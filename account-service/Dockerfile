FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.10_7_1.9.8_3.3.1 AS builder

WORKDIR /app

COPY build.sbt .
COPY project/ project/
RUN sbt update

COPY . .

RUN sbt assembly

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/scala-3.3.7/account-service-assembly-0.1.0-SNAPSHOT.jar app.jar

RUN mkdir -p data
COPY --from=builder /app/src/main/resources/accounts.json data/accounts.json

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]