FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system backend && adduser --system --ingroup backend backend

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080

USER backend

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar"]
