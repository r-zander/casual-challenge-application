FROM openjdk:21-slim
WORKDIR /opt/app
ARG JAR_FILE=build/libs/casual-challenge-application-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar", "-Dspring.profiles.active=prod","app.jar"]
