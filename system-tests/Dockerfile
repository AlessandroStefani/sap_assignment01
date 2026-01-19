FROM eclipse-temurin:21-jdk

ENV SBT_VERSION=1.9.9
RUN apt-get update && \
    apt-get install -y curl && \
    curl -L -o sbt-$SBT_VERSION.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-$SBT_VERSION.deb && \
    dpkg -i sbt-$SBT_VERSION.deb && \
    rm sbt-$SBT_VERSION.deb && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /tests

COPY build.sbt .
COPY project ./project

RUN sbt update

COPY src ./src

CMD ["sbt", "test"]