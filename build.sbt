organization in ThisBuild := "none"
scalaVersion in ThisBuild := "2.13.1"


lazy val `aggregation-server` = project
  .in(file("."))
  .settings(
    name := "AggregationServer",
    version := "0.1",
    mainClass in(Compile, run) := Some("aggregation.app.App"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.26",
      "com.typesafe.akka" %% "akka-stream" % "2.5.26",

      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.26",

      "com.typesafe" % "config" % "1.4.0",
      "com.iheart" %% "ficus" % "1.4.7",

      "com.tethys-json" %% "tethys" % "0.10.0"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % "2.5.26" % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
      "org.scalamock" %% "scalamock" % "4.4.0" % Test
    )
  )
