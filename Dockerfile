FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew gradle.properties build.gradle settings.gradle ./
COPY gradle ./gradle
COPY grails-app ./grails-app
COPY src ./src
RUN ./gradlew --no-daemon assemble

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.war app.war
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-Dgrails.env=prod", "-jar", "app.war"]
