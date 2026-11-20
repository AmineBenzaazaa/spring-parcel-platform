# syntax=docker/dockerfile:1.7
# Build context: repository root (the module needs its siblings: common-events, service-support).

# ---- stage 1: build the fat jar and split it into layers ------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
# BuildKit cache mount: the Maven repository is reused across builds and across services
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    MAVEN_OPTS="-Xmx512m" ./mvnw -B -q -pl order-service -am package -DskipTests \
 && cp order-service/target/order-service-*.jar app.jar \
 && java -Djarmode=layertools -jar app.jar extract --destination extracted

# ---- stage 2: slim JRE runtime, non-root ----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
# least-changing layers first so a code change only rebuilds the last (small) layer
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8082
# container-aware heap sizing; small, laptop-friendly footprint (override in compose/k8s)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss256k -XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
