FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY api/build.gradle api/
COPY judge/build.gradle judge/
RUN ./gradlew --no-daemon dependencies -q > /dev/null
COPY api api
COPY judge judge
RUN ./gradlew --no-daemon :api:bootJar -x test

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /src/api/build/libs/api.jar api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
