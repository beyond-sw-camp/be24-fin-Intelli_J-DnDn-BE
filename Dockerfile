 #빌드 단계
 FROM gradle:8.14.4-jdk17 AS builder

 WORKDIR /app

 COPY build.gradle   ./
 COPY settings.gradle    ./

 RUN gradle dependencies --no-daemon # 의존성 주입하는 코드

 COPY ./src  ./src
 RUN gradle bootjar --no-daemon # 컨테이너 안에서 jar파일이 만들어짐



# 실행 단계
FROM openjdk:17-ea-17-slim
# COPY ./build/libs/*.jar    /app.jar
COPY --from=builder /app/build/libs/*SNAPSHOT.jar  /app.jar
# --from=builder : builder라는 이름으로 지정된 이전 도커 빌드 단계(Stage)에서 파일을 가져오겠다
EXPOSE 8080
CMD ["java", "-jar", "/app.jar"]