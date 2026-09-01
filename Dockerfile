FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package


FROM mcr.microsoft.com/playwright/java:v1.55.0-noble

WORKDIR /app

COPY --from=build /app/target/naukri-auto-updater-0.0.1-SNAPSHOT.jar app.jar

# Resume
RUN mkdir -p /app/data /app/assets
COPY src/main/resources/Honey_SDE_Resume_1.pdf /app/assets/Honey_SDE_Resume.pdf

# Virtual display for headed Chromium
ENV DISPLAY=:99
ENV TZ=Asia/Kolkata

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "mkdir -p /app/data && if [ ! -f /app/data/Honey_SDE_Resume.pdf ]; then cp -f /app/assets/Honey_SDE_Resume.pdf /app/data/Honey_SDE_Resume.pdf; fi; Xvfb :99 -screen 0 1920x1080x24 -ac & java -jar /app/app.jar"]
