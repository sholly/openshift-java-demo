start local postgres database: 

```
docker run -d --rm --name tododb -p 5432:5432 \
-v $PWD/src/main/resources/initdb/:/docker-entrypoint-initdb.d/:Z  
-e POSTGRES_USER=todo \
-e POSTGRES_PASSWORD=demo123\
-e POSTGRES_DB=todo postgres:10
```

run locally in docker: 

first, build app with
`mvn clean package`

Then build docker image:

`docker build -f Dockerfile.local -t openshift-java-demo .`
Now run the dockerized app.  We have to use the *--link* flag to allow demo app to communicate with the database container

`docker run --rm  -d --link tododb --name demo -p 8080:8080 openshift-java-demo`


