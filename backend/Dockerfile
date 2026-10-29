FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 javac && useradd --uid 10001 --gid javac --create-home javac
WORKDIR /app
COPY --from=build /app/target/campus-1.0.0.jar app.jar
USER javac
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
