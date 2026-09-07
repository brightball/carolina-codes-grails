FROM eclipse-temurin:21-jdk-jammy
USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*
