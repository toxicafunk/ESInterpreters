name := "ESInterpreters"

version := "0.1"

scalaVersion := "2.12.5"
scalaVersion in ThisBuild := "2.12.5"

val circeVersion = "0.9.3"
val http4sVersion = "0.18.7"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-free" % "1.0.1",
  "org.typelevel" %% "cats-mtl-core" % "0.0.2",
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,

  "org.apache.kafka" % "kafka-clients" % "1.1.0",

  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,

  "org.http4s"     %% "http4s-blaze-server"  % http4sVersion,
  "org.http4s"     %% "http4s-dsl"           % http4sVersion,

  "io.monix" %% "monix" % "3.0.0-RC1"
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-literal"
).map(_ % circeVersion)

scalacOptions ++= Seq(
  //"-Xfatal-warnings",
  "-Ypartial-unification",
  "-deprecation",
  "-feature",
  "-language:reflectiveCalls",
  "-language:higherKinds"
)
