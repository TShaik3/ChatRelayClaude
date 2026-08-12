# syntax=docker/dockerfile:1
#
# Single-deployable build: the Svelte frontend's static output is baked into the Spring Boot
# jar's classpath (src/main/resources/static), so one container serves both the REST/WebSocket
# API and the UI on the same origin -- no nginx, no CORS, matching how the Vite dev proxy already
# treats them as same-origin in development (Migration Plan Phase 3/4).

# ---- Stage 1: build the Svelte frontend ----
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: build the Spring Boot backend, embedding the frontend's static build ----
# This is a Gradle multi-project build rooted one level up from backend/ (see settings.gradle.kts),
# so the wrapper and settings file have to come along too, not just the backend/ subproject.
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts ./
COPY gradle/ ./gradle/
COPY backend/ ./backend/
COPY --from=frontend-build /frontend/dist ./backend/src/main/resources/static
RUN ./gradlew :backend:bootJar -x test --no-daemon

# ---- Stage 3: runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-build /workspace/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
