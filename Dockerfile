# 한 가지만 백엔드 이미지 (Spring Boot, Java 17)
# 1단계: Gradle로 실행 파일(jar) 만들기 — 테스트는 DB가 필요해서 여기서는 건너뜁니다. (테스트는 배포 전에 로컬에서)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: 실행용 (JRE만)
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
