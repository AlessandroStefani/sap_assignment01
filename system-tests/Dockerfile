FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.10_7_1.9.8_3.3.1

WORKDIR /tests

COPY build.sbt .
COPY project/ project/

RUN sbt update

COPY src/ src/

RUN sbt test:compile

CMD ["sbt", "test"]