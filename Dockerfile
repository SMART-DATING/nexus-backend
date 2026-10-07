FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
ENV MAVEN_OPTS="-Xmx768m"
COPY pom.xml .
COPY src ./src
RUN mvn -B package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/nexus-backend-0.0.1-SNAPSHOT.jar app.jar
RUN addgroup -S nexus && adduser -S nexus -G nexus && mkdir -p /app/data && chown -R nexus:nexus /app
USER nexus
ENV BIND_ADDRESS=0.0.0.0
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx512m"
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=40s --retries=20 CMD wget -q -O /dev/null http://127.0.0.1:8080/api/v1/health || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
