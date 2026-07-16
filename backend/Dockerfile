FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runner

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system docpilot \
    && useradd --system --gid docpilot --home-dir /app --shell /usr/sbin/nologin docpilot \
    && mkdir -p /app/data/uploads /app/data/upload-chunks \
    && chown -R docpilot:docpilot /app

COPY --from=builder --chown=docpilot:docpilot /workspace/target/*.jar /app/app.jar

USER docpilot

ENV SERVER_PORT=8081
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
