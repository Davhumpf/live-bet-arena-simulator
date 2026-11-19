FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN --mount=type=cache,id=arena-maven,target=/root/.m2,sharing=locked mvn -B package
ARG SERVICE
RUN cp ${SERVICE}/target/${SERVICE}-1.0.0.jar /app.jar
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system arena && useradd --system --gid arena arena && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder --chown=arena:arena /app.jar app.jar
USER arena
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","app.jar"]