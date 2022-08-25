FROM gradle:7.5.0-jdk11-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle clean shadowJar --no-daemon

FROM flink:1.15.1-scala_2.12-java11
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/flinkToIcebergFatJar.jar