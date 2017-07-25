import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import sbt._

lazy val rootProj =
  (project in file("."))
    .settings(name := "zquery")
    .settings(
      version := "b" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("YMMddHH")),
      scalaVersion := "2.12.1"
    )
    .settings(
      libraryDependencies ++= {
        Seq(
          "com.squareup.okhttp" % "okhttp" % "2.7.5",
          "org.slf4j" % "slf4j-api" % "1.7.25",
          "org.apache.logging.log4j" % "log4j-api" % "2.8.1",
          "org.apache.logging.log4j" % "log4j-core" % "2.8.1",
          "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.1",
          "org.scalaz" %% "scalaz-concurrent" % "7.2.10",
          "io.circe" %% "circe-core" % "0.7.0",
          "io.circe" %% "circe-generic" % "0.7.0",
          "io.circe" %% "circe-parser" % "0.7.0"
        )
      }
    )