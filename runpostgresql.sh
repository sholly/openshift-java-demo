#!/bin/sh

docker run -d --rm --name tododb -p 5432:5432 -v $PWD/src/main/resources/initdb/:/docker-entrypoint-initdb.d/:Z -e POSTGRES_USER=todo -e POSTGRES_PASSWORD=demo123 -e POSTGRES_DB=todo postgres:10
