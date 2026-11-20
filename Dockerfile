# =====================================================================
# CropDisease AI Pro: container image for cloud hosting (Render / Railway / any Docker host)
# =====================================================================

# ---- 1. Build the Spring Boot jar ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Gradle wrapper + build files first (cached between builds)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon --version

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- 2. Small runtime image ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# The platform sets PORT; 8080 is the default in the cloud
ENV PORT=8080
EXPOSE 8080

# Database: cloud MySQL when DB_URL / DB_USER / DB_PASSWORD are set on the host.
# Without them the app still works using a built-in in-memory database
# (disease library is loaded on start; accounts & scans reset when the server restarts).
ENV DB_URL="jdbc:h2:mem:cropdisease;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,USER;DB_CLOSE_DELAY=-1" \
    DB_USER="sa"

# Keep memory use within free-tier limits
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["java", "-jar", "app.jar"]
