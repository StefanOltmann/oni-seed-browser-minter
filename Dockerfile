FROM gradle:9-jdk25 AS build_stage
WORKDIR /tmp
COPY .git .git
COPY gradle gradle
COPY build.gradle.kts gradle.properties settings.gradle.kts gradlew ./
COPY server/build.gradle.kts server/
COPY server/src server/src
RUN mkdir -p web
RUN chmod +x gradlew
RUN ./gradlew --no-daemon --info :server:test :server:buildFatJar

FROM eclipse-temurin:25-jre-alpine
EXPOSE 8080
RUN mkdir /app
COPY --from=build_stage /tmp/server/build/libs/*-all.jar /app/ktor-server.jar
ENTRYPOINT ["java","-Xlog:gc+init","-XX:+PrintCommandLineFlags","-jar","/app/ktor-server.jar"]
