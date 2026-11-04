FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY database/ database/
COPY backend/src/ backend/src/
COPY --from=frontend /build/frontend/dist/ backend/src/main/resources/static/
RUN mvn -q -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system javac && useradd --system --gid javac javac
COPY --from=backend /build/backend/target/campus-1.0.0.jar /app/app.jar
USER javac
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV COOKIE_SECURE=true
ENV DB_SCHEMA=javac_secure
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
