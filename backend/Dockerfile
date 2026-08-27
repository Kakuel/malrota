# Railway 서비스의 Root Directory가 backend일 때 Java 17로 백엔드를 빌드·실행한다.
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

# 의존성 정의를 먼저 복사해 Docker 빌드 캐시를 활용한다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# 실제 수신 포트는 Spring의 server.port=${PORT:8081} 설정이 Railway PORT 값으로 결정한다.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
