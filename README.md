#Simple Spring Boot application showing various methods of deploying to Openshift.

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

Tag the image, and push it to Dockerhub or another image repository such as quay.io: 

`docker tag openshift-java-demo:latest docker.io/sholly/openshift-java-demo:latest  && docker push docker.io/sholly/openshift-java-demo:latest`


##Deploy the application using new-app/s2i on source code
Make the project: 

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

Now let's set liveness and readiness probes: 

`oc set probe dc/java-demo --readiness  --get-url=http://:8080/actuator/health --initial-delay-seconds=10 --timeout-seconds=2`

`oc set probe dc/java-demo --liveness --get-url=http://:8080/actuator/health --initial-delay-seconds=10 --timeout-seconds=2`





Deploy app from image:

docker login -u $USER quay.io
oc create secret generic quayio --from-file  .dockerconfigjson=/home/sholly/.docker/config.json --type kubernetes.io/dockerconfigjson
oc secrets link default quayio --for pull
oc new-app --docker-image=quay.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config
or
oc new-app --docker-image=docker.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config

oc create secret generic tododbsecret --from-literal SPRING_DATASOURCE_USER=todo --from-literal SPRING_DATASOURCE_PASSWORD=openshift123
oc set env dc/java-demo  --from secret/tododbsecret

oc create configmap java-demo --from-file openshift/application.properties
oc set volume dc/java-demo --add -t configmap -m /deployments/config --name java-demo-volume --configmap-name java-demo

oc expose svc java-demo 


