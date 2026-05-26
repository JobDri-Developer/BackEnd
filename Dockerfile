FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S jobdri && adduser -S jobdri -G jobdri

COPY build/libs/*.jar app.jar

USER jobdri

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
