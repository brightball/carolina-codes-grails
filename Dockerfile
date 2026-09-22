# eclipse-temurin:27-* is unpublished on JDK 27 GA day; openjdk:27-rc reports feature 27 (27+35).
FROM openjdk:27-rc-bookworm AS build
WORKDIR /app
COPY gradlew gradle.properties build.gradle settings.gradle ./
COPY gradle ./gradle
COPY grails-app ./grails-app
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon assemble \
    && find build/libs -maxdepth 1 -type f -name '*.war' ! -name '*-plain.war' -exec cp {} /tmp/app.war \;

FROM openjdk:27-rc-slim
WORKDIR /app
COPY --from=build /tmp/app.war app.war
ENV PORT=8080
# JDK 27 dropped -XX:MaxRAM. -Xmx plus metaspace and code cache stay under fly.toml memory.
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport -XX:TieredStopAtLevel=1 -Xss512k -Dfile.encoding=UTF-8"
EXPOSE 8080
CMD ["java", "-Dgrails.env=prod", "-jar", "app.war"]
