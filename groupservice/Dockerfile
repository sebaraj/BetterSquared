# Use an official Maven image to build the application
#FROM maven:3.8.6-openjdk-17-slim AS build
FROM maven:3.8.6 AS build

# Set the working directory
WORKDIR /app

# Copy the pom.xml and download the dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application
RUN mvn clean install -DskipTests

# Debug: List the contents of the target directory to verify the JAR file
RUN ls -R /app/target


# Use an LTS amazon corretto runtime image to run the application
FROM amazoncorretto:21

# Set the working directory
WORKDIR /app

# Copy the built application from the previous stage
#COPY --from=build /app/target/authservice-*.jar app.jar
# RUN ls -l /app/target/classes/com/authservice/server/
COPY --from=build /app/target/Server-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

# Debug: List the contents of the working directory to verify the JAR file is copied
RUN ls -l /app

# Expose the port the application runs on
EXPOSE 6000

# Set the entry point to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
