# Railway가 저장소 최상위에서 빌드할 때 backend Gradle 프로젝트만 패키징한다.
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

# 의존성 정의를 먼저 복사해 Docker 캐시를 활용한다.
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
RUN chmod +x gradlew

COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# 실제 수신 포트는 Spring의 server.port=${PORT:8081} 설정이 Railway PORT 값으로 결정한다.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
