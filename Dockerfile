FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
ENV MAVEN_OPTS="-Xmx768m"
COPY pom.xml .
COPY scripts ./scripts
RUN sh scripts/download-model.sh
COPY src ./src
RUN mvn -B package
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/nexus-backend-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/models ./models
COPY MODEL.md MODEL-LICENSE.txt ./
RUN apt-get update && apt-get install -y --no-install-recommends curl libgomp1 && rm -rf /var/lib/apt/lists/* && groupadd --system nexus && useradd --system --gid nexus nexus && mkdir -p /app/data && chown -R nexus:nexus /app
USER nexus
ENV BIND_ADDRESS=0.0.0.0
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx512m"
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=60s --retries=20 CMD curl --fail --silent http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
