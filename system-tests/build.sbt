name := "system-tests"
version := "0.1.0"
scalaVersion := "3.3.7"

val http4sVersion = "0.23.23"
val circeVersion = "0.14.6"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "org.http4s"    %% "http4s-ember-client" % http4sVersion % Test,
  "org.http4s"    %% "http4s-circe" % http4sVersion % Test,

  // QUESTA È LA LIBRERIA MANCANTE PER USARE json"""..."""
  "io.circe"      %% "circe-literal" % circeVersion % Test,

  "io.circe"      %% "circe-generic" % circeVersion % Test,
  "org.slf4j"     % "slf4j-simple" % "2.0.9" % Test
)