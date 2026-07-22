FROM node:22-alpine AS frontend
WORKDIR /workspace/web-manager/frontend
COPY web-manager/frontend/package*.json ./
RUN npm ci
COPY web-manager/frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /workspace/web-manager
COPY web-manager/pom.xml ./
RUN mvn dependency:go-offline -B
COPY web-manager/src ./src
COPY --from=frontend /workspace/web-manager/src/main/resources/static ./src/main/resources/static
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S techflow && adduser -S techflow -G techflow
COPY --from=backend /workspace/web-manager/target/web-manager-1.1.0.jar ./app.jar
USER techflow
EXPOSE 8080
CMD ["sh", "-c", "if [ -n \"$DATABASE_URL_RAW\" ]; then export DATABASE_URL=\"jdbc:$DATABASE_URL_RAW\"; fi; exec java -jar /app/app.jar"]
