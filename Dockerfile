FROM maven:latest as builder
WORKDIR /app
COPY . .
RUN mvn package -f pom.xml

FROM openjdk:11-jre-slim as runner
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends xorg mesa-utils libgl1-mesa-glx openjfx \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/ProxyChecker.jar ./ProxyChecker.jar

CMD ["java", "-jar", "ProxyChecker.jar"]
