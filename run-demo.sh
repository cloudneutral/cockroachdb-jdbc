#!/bin/bash

url="jdbc:postgresql://kai-odin-11257.8nj.cockroachlabs.cloud:26257/kai-odin-11257.defaultdb?sslmode=verify-full"
usr=guest
pwd=

java -jar cockroachdb-jdbc-demo/target/cockroachdb-jdbc-demo.jar \
--url ${url} --user ${usr} --password ${pwd} \
$*