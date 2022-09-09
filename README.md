## Simple Spring Boot application showing various methods of deploying to Openshift.

## Application information:

This is a simple Spring boot app for reading todos from a Postgresql database.  
There are two endpoints of interest: 
1. /todos, which will query for todos from the database. 
2. /demoenv, which will return a string specific to each deployed environment.

Also note that all of the Spring Actuator endpoints have been enabled for demo 
purposes. 

## Building, deploying, and running the application locally via Docker:

First, start a local postgres database, from the root of this project: 

```
docker run -d --rm --name tododb -p 5432:5432 \
-v $PWD/src/main/resources/initdb/:/docker-entrypoint-initdb.d/:Z \
-e POSTGRES_USER=todo \
-e POSTGRES_PASSWORD=demo123 \
-e POSTGRES_DB=todo postgres:10
```

The database should be automatically initialized with the .sql file in src/main/resources/initdb.

Now we can build a local docker image and run it.  

Build the app with

`mvn clean package`

Then build docker image:

`docker build -f Dockerfile.local -t openshift-java-demo .`

Do both at the same time: 

`mvn clean package && docker build -f Dockerfile.local -t openshift-java-demo .`

Now run the dockerized app.  We have to use the *--link* flag to allow demo app to communicate with the database container

`docker run -e SPRING_PROFILES_ACTIVE=docker --rm  -d --link tododb --name demo -p 8080:8080 openshift-java-demo`

Once we've verified the app works locally in Docker, it's time to run the app on openshift.

Tag the image, and push it to quay.io or another image repository.  This image will be
used in later steps to deploy the application. 

```
docker tag openshift-java-demo:latest quay.io/sholly/openshift-java-demo:latest  && \
     docker push quay.io/sholly/openshift-java-demo:latest`
```


## Deploy the application using new-app/s2i on source code
Create a new project: 

`oc new-project java-demo-s2i`

Set up the database: 

```
oc new-app postgresql-ephemeral -p POSTGRESQL_USER=todo \
   -p POSTGRESQL_PASSWORD=openshift123 \
   -p POSTGRESQL_DATABASE=todo \
   -p DATABASE_SERVICE_NAME=tododb
```

Wait for database pod to be running by checking the pod status

`oc get pods -w`

When the database is up and running, we should return something like this:
```shell
NAME              READY   STATUS    RESTARTS   AGE
tododb-1-deploy   1/1     Running   0          15s
tododb-1-nl429    1/1     Running   0          13s
```


Now load data into the database.  The easiest way to do so is to use
the `oc port-forward` command to set up a local port forward to the database pod: 

`oc port-forward $POD 5532:5432`

Load data:

```
psql -h localhost -p 5532 -U todo
todo=>\i openshift/todo.openshift.sql
``` 

For the first example, we can use Openshift's code repository introspection to deploy our app directly from the 
Github repository.  Openshift will find a pom.xml at the root, and build and deploy our app as Java: 

```shell
oc new-app --name java-demo \
   --as-deployment-config \
   java~https://github.com/sholly/openshift-java-demo.git
```

Wait for the app to be running `oc get pods`: 

```shell
NAME                 READY   STATUS             RESTARTS   AGE
java-demo-1-build    0/1     Completed          0          3m42s
java-demo-1-deploy   0/1     Completed          0          2m8s
java-demo-1-ldg74    0/1     CrashLoopBackOff   3          2m5s
tododb-1-deploy      0/1     Completed          0          15m
tododb-1-nl429       1/1     Running            0          15m

```
The application is running, but it is crashing because we need to configure the application.properties,
as well as the database credientials. We will now configure the application via a ConfigMap and Secret.


First, let's create the ConfigMap from an application.properties specific to Openshift: 

`oc create configmap java-demo --from-file openshift/application.properties`

Next we will add the ConfigMap to the DeploymentConfig as a volume.  By default, the java s2i image build will place our 
Spring Boot jar under /deployments.  Spring Boot, by default, will look for application.properties files in a config 
directory under the /deployments directory.  So we set the volume for the ConfigMap to be '/deployments/config': 

`oc set volume dc/java-demo --add -t configmap -m /deployments/config --name java-demo-volume --configmap-name java-demo`

Now, let's set up the secret containing the username and password for database access: 

```
oc create secret generic tododbsecret --from-literal SPRING_DATASOURCE_USER=todo \ 
  --from-literal SPRING_DATASOURCE_PASSWORD=openshift123
```

Then, set environment variables containing the SPRING_DATASOURCE_USER and SPRING_DATASOURCE_PASSWORD from the tododbsecret: 

`oc set env dc/java-demo  --from secret/tododbsecret`

Each time we modify the DeploymentConfig, it will trigger a new deployment of the application. 

Our application pod should now be in a running state: 

```shell
java-demo-4-deploy   0/1     Completed   0          2m11s
java-demo-4-kx2xg    1/1     Running     0          2m6s
```

Finally, create a route for our application: 

`oc expose svc java-demo`

Test the application and verify correct operation.  

Get the route: 
`oc get route`

Test the /todos endpoint, verify that we have data: 

`curl java-demo-java-demo-s2i.apps.ocp4.lab.unixnerd.org/todos`

Test the /demoenv endpoint, verify we receive the message from the demo.env property in the ConfigMap:

`curl java-demo-java-demo-s2i.apps.ocp4.lab.unixnerd.org/demoenv`

## Deploying the application using the fabric8-maven-plugin:


Create a project: 

`oc new-project java-demo-fabric8`

Create the database:

```
oc new-app postgresql-ephemeral -p POSTGRESQL_USER=todo \
   -p POSTGRESQL_PASSWORD=openshift123 \
   -p POSTGRESQL_DATABASE=todo \
   -p DATABASE_SERVICE_NAME=tododb
```

Initialize the database:

```
oc port-forward $POD 5532:5432
psql -h localhost -p 5532 -U todo
todo=>\i todo.openshift.sql
``` 

Now build and deploy the application via the fabric8-maven-plugin.  It will compile the app, build an image via s2i, 
craft Openshift configuration objects, and apply them in the current namespace.  

`mvn clean fabric8:deploy`

The fabric8 plugin knows nothing about the needed ConfigMap or Secret, so the app will again be crash looping on first deploy. 

The fabric8 plugin, however, will apply snippets of openshift configuration to the default configuration it creates if 
we create the necessary snippets.  Examine the partial files in fabric8/.  We see our ConfigMap, Secret, and a partial 
DeploymentConfig snippet that will be merged into the generated DeploymentConfig.

Copy the files in fabric8/ to /src/main/fabric8/:

`cp fabric8/* src/main/fabric8/`

Now we can run the 'resource-apply' goal, which will generate our application configuration, 
applying and merging the snippets we copied. 

`mvn clean fabric8:resource-apply`

Now our application should be running properly. Examine the /todos and /demoenv endpoints to verify a correct 
application deployment.

## Deploying the application from a Docker image:

If we pass `oc new-app` an existing Docker image, Openshift will pull and introspect the image, then create an 
application based

Create the project:

`oc new-project java-demo-image`

Set up and initialize the database:

```shell
oc new-app postgresql-ephemeral -p POSTGRESQL_USER=todo \
   -p POSTGRESQL_PASSWORD=openshift123 \
   -p POSTGRESQL_DATABASE=todo \
   -p DATABASE_SERVICE_NAME=tododb
```
```
oc port-forward $POD 5532:5432
psql -h localhost -p 5532 -U todo
todo=>\i todo.openshift.sql
``` 

Deploy the application using the Docker image we pushed to docker.io: 

For docker.io:

`oc new-app --image=docker.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config`

Since every change to a DeploymentConfig results in a new deployment, this time let's pause the DeploymentConfig 
rollouts until we've configured the application: 

`oc rollout pause dc/java-demo`

Create the configmap and set the volume: 

`oc create configmap java-demo --from-file openshift/application.properties`

```shell
oc set volume dc/java-demo --add -t configmap \
   -m /deployments/config --name java-demo-volume \
   --configmap-name java-demo
```

Create the secret and set the environment variables on the DeploymentConfig: 
```shell
oc create secret generic tododbsecret \
   --from-literal SPRING_DATASOURCE_USER=todo\
  --from-literal SPRING_DATASOURCE_PASSWORD=openshift123
  ```
  
`oc set env dc/java-demo  --from secret/tododbsecret`

Now resume DeploymentConfig rollouts, and expose the application.

`oc rollout resume dc/java-demo`

`oc expose svc java-demo` 

Test that things are working as expected: 

`curl java-demo-java-demo-image.apps.ocp4.lab.unixnerd.org/todos`

`curl java-demo-java-demo-image.apps.ocp4.lab.unixnerd.org/demoenv`

To make sure our application is both running and ready to accept traffic, 
let's set liveness and readiness probes:

The liveness probe is how Openshift determines if the application is up:

`oc set probe dc/java-demo --liveness --get-url=http://:8080/actuator/health --initial-delay-seconds=10 --timeout-seconds=2`

The readiness probe is how Openshift determines if the application is ready to accept traffic.  We're using the 
/todos endpoint to make sure that the application's database connection is properly configured and the endpoint 
is working normally:

`oc set probe dc/java-demo --readiness --get-url=http://:8080/todos --initial-delay-seconds=10 --timeout-seconds=2`


If our application is experiencing increased load, we can manually scale up or down the number of pods we're 
running like so: 

`oc scale dc/java-demo --replicas=3`

## Deploying the application from yaml files. 

Once we get an application deployed and properly configured, it is desirable to save the Openshift configuration so we 
reuse them for other purposes such as deployments to higher environments.  I've taken the step of 
exporting the DeploymentConfigs, Services, Route, Secrets, and the Configmap from the previous
app deployment, cleaned the yaml files up, and placed them in openshift/deploy-yaml. 
Now we can simply deploy our app like from the project root like so: 

First create the project: 
`oc new-project java-demo-deploy-yaml`

Now simply apply the yaml files: 

`oc apply -f openshift/deploy-yaml/`

Run `oc get all` to see all of the Kubernetes objects:

```shell
NAME                     READY   STATUS      RESTARTS   AGE
pod/java-demo-1-deploy   0/1     Completed   0          100s
pod/java-demo-1-qscmx    1/1     Running     0          98s
pod/tododb-1-deploy      0/1     Completed   0          101s
pod/tododb-1-lnd8x       1/1     Running     0          98s

NAME                                DESIRED   CURRENT   READY   AGE
replicationcontroller/java-demo-1   1         1         1       101s
replicationcontroller/tododb-1      1         1         1       101s

NAME                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)    AGE
service/java-demo   ClusterIP   172.30.147.241   <none>        8080/TCP   102s
service/tododb      ClusterIP   172.30.167.66    <none>        5432/TCP   102s

NAME                                           REVISION   DESIRED   CURRENT   TRIGGERED BY
deploymentconfig.apps.openshift.io/java-demo   1          1         1         config,image(java-demo:latest)
deploymentconfig.apps.openshift.io/tododb      1          1         1         config,image(postgresql:10-el8)

NAME                                       IMAGE REPOSITORY                                                                   TAGS     UPDATED
imagestream.image.openshift.io/java-demo   image-registry.openshift-image-registry.svc:5000/java-demo-deploy-yaml/java-demo   latest   About a minute ago

NAME                                 HOST/PORT                                                    PATH   SERVICES    PORT       TERMINATION   WILDCARD
route.route.openshift.io/java-demo   java-demo-java-demo-deploy-yaml.apps.ocp4.lab.unixnerd.org          java-demo   8080-tcp                 None
```

Again, test that things are working as expected:

`curl java-demo-java-demo-deploy-yaml.apps.ocp4.lab.unixnerd.org/todos`

`curl java-demo-java-demo-deploy-yaml.apps.ocp4.lab.unixnerd.org/demoenv`




## Build and deploy with Tekton

Note: To run this portion of the demo, you will need the tkn client. 
The tkn client can be downloaded here: 

https://tekton.dev/docs/cli/


Create a new project: 

```
oc new-project java-demo-tekton
```

Set up the database.  This time, use the deployment found in tekton/db/

```
oc apply -f tekton/db/
```

As before, wait for the database pod to be up, then initialize: 

```
tododb-1-deploy   0/1     Completed   0          97s
tododb-1-rr2tt    1/1     Running     0          90s
```

```
$ oc port-forward tododb-1-rr2tt 

$ psql -h localhost -p 5532 -U todo 
Handling connection for 5532
psql (14.3, server 10.21)
Type "help" for help.

todo=> \i openshift/todo.openshift.sql 
CREATE TABLE
INSERT 0 1
INSERT 0 1
INSERT 0 1
todo=> 

```

Now create the pipeline and the PVC neded for pipeline runs: 

```
oc apply -f tekton/pipeline/build-deploy-pipeline.yaml
oc apply -f tekton/pipeline/build-deploy-workspace-pvc.yaml
```

With everything set up, let's run our pipeline: 
