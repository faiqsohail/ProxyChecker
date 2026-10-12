FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre-noble AS runner
WORKDIR /app

# Native libraries required by the JavaFX runtime bundled inside ProxyChecker.jar (X11/GTK/OpenGL).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgtk-3-0t64 libgl1 libxtst6 libxxf86vm1 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/ProxyChecker.jar ./ProxyChecker.jar

CMD ["java", "-jar", "ProxyChecker.jar"]
