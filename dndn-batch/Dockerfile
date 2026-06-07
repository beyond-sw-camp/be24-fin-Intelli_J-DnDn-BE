FROM eclipse-temurin:17-jre-alpine
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata
WORKDIR /app
COPY build/libs/dndn-batch-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
