FROM maven:3.8.6 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean install -DskipTests

FROM amazoncorretto:21

WORKDIR /app

COPY --from=build /app/target/Consumer-1.0-SNAPSHOT-jar-with-dependencies.jar email.jar

ENTRYPOINT ["java", "-jar", "email.jar"]
