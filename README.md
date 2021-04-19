##Simple Spring Boot application showing various methods of deploying to Openshift.

## Application information:

This is a simple Spring boot app for reading todos from a Postgresql database.  
There are two endpoints of interest: 
1. /todos, which will query for todos from the database. 
2. /demoenv, which will return a string specific to each deployed environment.

Also note that all of the Spring Actuator endpoints have been enabled for demo 
purposes. 

##Building, deploying, and running the application locally via Docker:

First, start a local postgres database: 

```
docker run -d --rm --name tododb -p 5432:5432 \
-v $PWD/src/main/resources/initdb/:/docker-entrypoint-initdb.d/:Z \
-e POSTGRES_USER=todo \
-e POSTGRES_PASSWORD=demo123 \
-e POSTGRES_DB=todo postgres:10
```

Initialize the local database: 
```
psql -h localhost -p 5432 -U todo
todo=>\i todo.sql
```


Now we can build a local docker image and run it.  

first, build app with
`mvn clean package`

Then build docker image:

`docker build -f Dockerfile.local -t openshift-java-demo .`

Do both at the same time: 

`mvn clean package && docker build -f Dockerfile.local -t openshift-java-demo .`

Now run the dockerized app.  We have to use the *--link* flag to allow demo app to communicate with the database container

`docker run -e SPRING_PROFILES_ACTIVE=docker --rm  -d --link tododb --name demo -p 8080:8080 openshift-java-demo`

Once we've verified the app works locally in Docker, it's time to run the app on openshift.

Tag the image, and push it to Dockerhub or another image repository such as quay.io.  This image will be
used in later steps to deploy the application. 

`docker tag openshift-java-demo:latest docker.io/sholly/openshift-java-demo:latest  && docker push docker.io/sholly/openshift-java-demo:latest`


##Deploy the application using new-app/s2i on source code
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


Now initialize the database on Openshift.  The easiest way to do so is to use
the `oc port-forward` command to set up a local port forward to the pod running the 
Postgresql database: 

`oc port-forward $POD 5532:5432`

Then, loading data is almost exactly like loading data locally:

```
psql -h localhost -p 5532 -U todo
todo=>\i todo.openshift.sql
``` 



We can deploy our app directly from the git repo like so: 

```shell
oc new-app --name java-demo \
   --as-deployment-config \
   java~https://github.com/sholly/openshift-java-demo.git
```

Let's check the pods with `oc get pods`: 

```shell
NAME                 READY   STATUS             RESTARTS   AGE
java-demo-1-build    0/1     Completed          0          3m42s
java-demo-1-deploy   0/1     Completed          0          2m8s
java-demo-1-ldg74    0/1     CrashLoopBackOff   3          2m5s
tododb-1-deploy      0/1     Completed          0          15m
tododb-1-nl429       1/1     Running            0          15m

```
The application is running, but it doesn't have the proper application.properties,
nor does it know the database username and password. We will now configure the application properly.


First, let's create the configmap from an application.properties specific to openshift: 

`oc create configmap java-demo --from-file openshift/application.properties`

Next we will add the ConfigMap to the DeploymentConfig as a volume.  By default, the java s2i image build will place our 
Spring Boot jar under deployments.  Spring Boot, by default, will look for application.properties files in a config 
directory under the directory which contains our jar file.  So we set the volume for the ConfigMap to be 
'/deployments/config': 

`oc set volume dc/java-demo --add -t configmap -m /deployments/config --name java-demo-volume --configmap-name java-demo`

Now, let's set up the secret containing the username and password for database access: 

`oc create secret generic tododbsecret --from-literal SPRING_DATASOURCE_USER=todo --from-literal SPRING_DATASOURCE_PASSWORD=openshift123`

Then, set environment variables containing the names and values from the tododbsecret: 

`oc set env dc/java-demo  --from secret/tododbsecret`

Now our pod should be in the running state: 

```shell
java-demo-4-deploy   0/1     Completed   0          2m11s
java-demo-4-kx2xg    1/1     Running     0          2m6s
```

Finally, we need to create the route in order to access the application: 

`oc expose svc java-demo`

Let's test the application and verify correct operation.  

Get the route: 
`oc get route`

Test the /todos endpoint, verify that we receive 3 todos: 

`curl java-demo-java-demo-s2i.apps.ocp4.lab.unixnerd.org/todos`

Test the /demoenv endpoint, verify we receive the message from the demo.env property in the ConfigMap:

`curl java-demo-java-demo-s2i.apps.ocp4.lab.unixnerd.org/demoenv`


##Deploying the application from a previously-built image:

This process looks the same as letting Openshift build from source code, but we're
using the Docker image we built and pushed 

Create the project:

`oc new-project java-demo-image`

Set up the database:

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

Deploy the application using the Docker image we pushed to docker.io: 

For quay.io: 

`oc new-app --docker-image=quay.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config`

For docker.io:

`oc new-app --docker-image=docker.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config`

For this example, let's pause the DeploymentConfig rollouts until we've configured the app: 

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

Now let's set liveness and readiness probes so we can control:

`oc set probe dc/java-demo --liveness --get-url=http://:8080/actuator/health --initial-delay-seconds=10 --timeout-seconds=2`

`oc set probe dc/java-demo --readiness --get-url=http://:8080/todos --initial-delay-seconds=10 --timeout-seconds=2`


If we need to, we can manually scale the number of pods we're running like so: 

`oc scale dc/java-demo --replicas=3`

##Deploying the application from yaml files. 

The previous deployments were fine, but involved a lot of manual work.  I've taken the step of 
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

`curl java-demo-java-demo-image.apps.ocp4.lab.unixnerd.org/todos`

`curl java-demo-java-demo-image.apps.ocp4.lab.unixnerd.org/demoenv`
