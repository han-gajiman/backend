# 한 가지만 백엔드 이미지. 빌드: docker compose build (deploy/README.md 참고)

# 1) jar 빌드
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
# 의존성 먼저 받아 두면 코드만 바뀔 때 다시 받지 않습니다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY src src
# 테스트는 PostgreSQL이 필요해서 이미지 빌드에서는 건너뜁니다. (테스트는 로컬에서)
RUN ./gradlew --no-daemon -q bootJar -x test && cp build/libs/*-SNAPSHOT.jar app.jar

# 2) 실행
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# 응답이 없으면 unhealthy → autoheal이 컨테이너를 다시 띄웁니다. (Flyway·기동 시간 고려해 start-period 90초)
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
