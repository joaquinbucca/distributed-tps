FROM java:8

EXPOSE 65111

COPY target/distributed-1.0-SNAPSHOT-jar-with-dependencies.jar /usr/src/myapp/server.jar

WORKDIR /usr/src/myapp
