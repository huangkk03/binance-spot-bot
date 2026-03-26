FROM alpine:3.19 AS builder

WORKDIR /app

RUN apk add --no-cache openjdk17-jdk maven

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM alpine:3.19

RUN apk add --no-cache openjdk17-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
