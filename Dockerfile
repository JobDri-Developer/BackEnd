FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S jobdri && adduser -S jobdri -G jobdri
RUN mkdir -p /var/log/spring-boot/audit && chown -R jobdri:jobdri /var/log/spring-boot /app

COPY build/libs/*.jar app.jar

USER jobdri

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
