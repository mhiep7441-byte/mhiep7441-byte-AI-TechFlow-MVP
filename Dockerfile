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
RUN apk add --no-cache python3 py3-pip ffmpeg font-dejavu \
    && addgroup -S techflow && adduser -S techflow -G techflow
COPY worker-requirements.txt ./worker-requirements.txt
RUN pip3 install --no-cache-dir --break-system-packages -r worker-requirements.txt
COPY --from=backend /workspace/web-manager/target/web-manager-1.1.0.jar ./app.jar
COPY video_worker.py ./video_worker.py
COPY inky_worker.py ./inky_worker.py
COPY research_agent.py ./research_agent.py
COPY content_guard.py ./content_guard.py
COPY series_planner.py ./series_planner.py
COPY topics.txt ./topics.txt
USER techflow
EXPOSE 8080
CMD ["sh", "-c", "if [ -n \"$DATABASE_URL_RAW\" ]; then db_target=\"${DATABASE_URL_RAW#*://}\"; export DATABASE_URL=\"jdbc:postgresql://${db_target#*@}\"; fi; if [ -n \"$PORT\" ]; then export SERVER_PORT=\"$PORT\"; fi; exec java -Xms96m -Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -jar /app/app.jar"]
