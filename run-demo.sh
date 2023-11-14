#!/bin/bash

url="jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable"
usr=root
pwd=
jarfile=cockroachdb-jdbc-demo/target/cockroachdb-jdbc-demo.jar

if [ ! -f "$jarfile" ]; then
    chmod +x mvnw
    ./mvnw clean install -Pdemo-jar
fi

java -jar $jarfile --url "${url}" --user "${usr}" --password "${pwd}" $*