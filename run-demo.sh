#!/bin/bash

#url="jdbc:cockroachdb://192.168.1.99:26257/jdbc_test?sslmode=disable"
url="jdbc:cockroachdb://localhost:26257/defaultdb?sslmode=disable"
#url="jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable"
usr=root
pwd=
jarfile=cockroachdb-jdbc-demo/target/cockroachdb-jdbc-demo.jar

if [ ! -f "$jarfile" ]; then
    chmod +x mvnw
    ./mvnw clean install -Pdemo-jar
fi

java -jar $jarfile --url "${url}" --user "${usr}" --password "${pwd}" $*