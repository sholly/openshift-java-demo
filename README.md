start local postgres database: 

```
docker run -d --rm --name tododb -p 5432:5432 \
-v $PWD/src/main/resources/initdb/:/docker-entrypoint-initdb.d/:Z \
-e POSTGRES_USER=todo \
-e POSTGRES_PASSWORD=demo123 \
-e POSTGRES_DB=todo postgres:10
```
psql -h localhost -p 5432 -U todo
#\i todo.sql
run locally in docker: 

first, build app with
`mvn clean package`

Then build docker image:

`docker build -f Dockerfile.local -t openshift-java-demo .`
Now run the dockerized app.  We have to use the *--link* flag to allow demo app to communicate with the database container

`docker run -e SPRING_PROFILES_ACTIVE=docker --rm  -d --link tododb --name demo -p 8080:8080 openshift-java-demo`

Running on openshift:
Create database: 
`oc new-app postgresql-ephemeral -p POSTGRESQL_USER=todo -p POSTGRESQL_PASSWORD=openshift123 -p POSTGRESQL_DATABASE=todo  -p DATABASE_SERVICE_NAME=tododb`

Wait for database pod to be running


Initialize database:

```
oc port-forward $POD 5532:5432
psql -h localhost -p 5532 -U todo
#\i todo.openshift.sql
``` 

deploy app using new-app/s2i on source code
oc new-app --name java-demo --as-deployment-config java~https://github.com/sholly/openshift-java-demo.git

oc create configmap java-demo --from-file openshift/deploy/application.properties
oc set volume dc/java-demo --add -t configmap -m /deployments/config --name java-demo-volume --configmap-name java-demo

oc create secret generic tododbsecret --from-literal SPRING_DATASOURCE_USER=todo --from-literal SPRING_DATASOURCE_PASSWORD=openshift123
oc set env dc/java-demo  --from secret/tododbsecret

oc expose svc java-demo



Deploy app from image:

docker login -u $USER quay.io
oc create secret generic quayio --from-file  .dockerconfigjson=/home/sholly/.docker/config.json --type kubernetes.io/dockerconfigjson
oc secrets link default quayio --for pull
oc new-app --docker-image=quay.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config
or
oc new-app --docker-image=docker.io/sholly/openshift-java-demo:latest --name java-demo --as-deployment-config

oc create secret generic tododbsecret --from-literal SPRING_DATASOURCE_USER=todo --from-literal SPRING_DATASOURCE_PASSWORD=openshift123
oc set env dc/java-demo  --from secret/tododbsecret

oc create configmap java-demo --from-file openshift/deploy/application.properties
oc set volume dc/java-demo --add -t configmap -m /deployments/config --name java-demo-volume --configmap-name java-demo

oc expose svc java-demo 

