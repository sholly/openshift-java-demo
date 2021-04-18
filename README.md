start local postgres database: 

```
docker run -d --rm --name tododb -p 5432:5432 \
-v $PWD/src/main/resources/initdb/:/docker-entrypoint-initdb.d/:Z \
-e POSTGRES_USER=todo \
-e POSTGRES_PASSWORD=demo123 \
-e POSTGRES_DB=todo postgres:10
```

run locally in docker: 

first, build app with
`mvn clean package`

Then build docker image:

`docker build -f Dockerfile.local -t openshift-java-demo .`
Now run the dockerized app.  We have to use the *--link* flag to allow demo app to communicate with the database container

`docker run -e SPRING_PROFILES_ACTIVE=local --rm  -d --link tododb --name demo -p 8080:8080 openshift-java-demo`

Running on openshift:
oc new-app --name java-demo --as-deployment-config java~https://github.com/sholly/openshift-java-demo.git

oc create configmap java-demo --from-file resources/application-openshiftdev.properties
oc set volume dc/java-demo --add -t configmap -m /deployments/config --name java-demo-volume --configmap-name java-demo
oc set env dc/java-demo --env SPRING_PROFILES_ACTIVE=openshiftdev
oc create secret generic tododbsecret --from-literal SPRING_DATASOURCE_USER=todo --from-literal SPRING_DATASOURCE_PASSWORD=demo123
oc set env dc/java-demo  --from secret/tododbsecret

