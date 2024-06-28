FROM maven:3.8.6 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean install -DskipTests

FROM amazoncorretto:21

WORKDIR /app

COPY --from=build /app/target/Server-1.0-SNAPSHOT-jar-with-dependencies.jar bet.jar

EXPOSE 7000

ENTRYPOINT ["java", "-jar", "bet.jar"]
