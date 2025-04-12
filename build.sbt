scalaVersion := "3.3.4"
name := "ytchatinteraction"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "org.playframework" %% "play-slick" % "6.1.1",
  "org.playframework" %% "play-slick-evolutions" % "6.1.1",
  "com.h2database" % "h2" % "2.3.232",
  specs2 % Test,
)
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)
Test / javaOptions += "-Dslick.dbs.default.connectionTimeout=30 seconds"
Test / javaOptions ++= Seq("-Dconfig.file=conf/test.conf")

