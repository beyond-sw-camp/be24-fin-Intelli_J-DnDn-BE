# 빌드 단계
FROM gradle:8.14.4-jdk17 AS builder

WORKDIR /app

COPY build.gradle ./
COPY settings.gradle ./
COPY dndn-core/build.gradle ./dndn-core/build.gradle

RUN gradle :dndn-core:dependencies --no-daemon

COPY ./dndn-core/src ./dndn-core/src

RUN gradle :dndn-core:bootJar --no-daemon

# 실행 단계
FROM openjdk:17-ea-17-slim
COPY --from=builder /app/dndn-core/build/libs/*SNAPSHOT.jar /app.jar
EXPOSE 8080

CMD ["java", "-jar", "/app.jar"]
