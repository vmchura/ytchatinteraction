import Dependencies.*
import play.core.PlayVersion.pekkoVersion


Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / organization := "your.organization"


lazy val server = (project in file("server")).settings(
  scalaJSProjects := Seq(client),
  Assets / pipelineStages  := Seq(scalaJSPipeline),
  pipelineStages := Seq(digest, gzip),
  // triggers scalaJSPipeline when using compile or continuous compilation
  Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
  libraryDependencies ++= Seq(
    guice,
    ws,
    "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusPlayVersion % Test,
    "org.playframework" %% "play-slick" % slickVersion,
    "org.playframework" %% "play-slick-evolutions" % slickVersion,

    // PostgreSQL driver for production
    "org.postgresql" % "postgresql" % postgresql,

    // Silhouette dependencies
    "org.playframework.silhouette" %% "play-silhouette" % silhouetteVersion,
    "org.playframework.silhouette" %% "play-silhouette-password-bcrypt" % silhouetteVersion,
    "org.playframework.silhouette" %% "play-silhouette-persistence" % silhouetteVersion,
    "org.playframework.silhouette" %% "play-silhouette-crypto-jca" % silhouetteVersion,
    "org.playframework.silhouette" %% "play-silhouette-totp" % silhouetteVersion,
    "org.playframework.silhouette" %% "play-silhouette-testkit" % silhouetteVersion % Test,

    // Utilities for Silhouette
    "com.iheart" %% "ficus" % "1.5.2", // Typesafe config utilities
    "net.codingwell" %% "scala-guice" % "6.0.0", // For Dependency Injection

    // Keep existing dependencies
    "com.h2database" % "h2" % "2.3.232",
    "org.webjars" %% "webjars-play" % "3.0.2",
    "org.webjars.npm" % "picocss__pico" % "2.1.1",
    "net.logstash.logback" % "logstash-logback-encoder" % "7.3",
    "org.jsoup" % "jsoup" % "1.18.1",
    "ch.qos.logback" % "logback-classic" % "1.5.8",
    "org.apache.pekko" %% "pekko-slf4j" % pekkoVersion,
    "org.apache.pekko" %% "pekko-testkit" % pekkoVersion % Test,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
    specs2 % Test,
    "edu.stanford.nlp" % "stanford-corenlp" % "4.5.9",
    "edu.stanford.nlp" % "stanford-corenlp" % "4.5.9" classifier "models-spanish",
    "edu.stanford.nlp" % "stanford-corenlp" % "4.5.9" classifier "models",
    "com.github.tminglei" %% "slick-pg" % "0.23.0",
    "com.github.tminglei" %% "slick-pg_play-json" % "0.23.0",
    "io.cequence" %% "openai-scala-client" % "1.2.0",
    "com.vmunier" %% "scalajs-scripts" % "1.3.0"
  ),
  Test / javaOptions += "-Dslick.dbs.default.connectionTimeout=30 seconds",
  Test / javaOptions += "-Dconfig.file=conf/test.conf",
  // Fix for Java 23 compatibility with Byte Buddy (used by Mockito)
  Test / javaOptions += "-Dnet.bytebuddy.experimental=true",
  // Additional JVM options for Java 23
  Test / javaOptions += "-XX:+EnableDynamicAgentLoading"
).enablePlugins(PlayScala).dependsOn(shared.jvm)

lazy val client =  (project in file("client")).settings(
  scalaJSUseMainModuleInitializer := true,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.2.0",
    "com.lihaoyi" %%% "utest" % "0.9.0" % "test",
    "com.softwaremill.sttp.client4" %%% "core" % "4.0.9",
    "com.yang-bo" %%% "html" % "3.0.3",
    "com.thoughtworks.binding" %%% "binding" % "12.2.0",
    "com.thoughtworks.binding" %%% "bindable" % "3.0.0",
    "com.thoughtworks.binding" %%% "latestevent" % "2.0.0",
    "com.thoughtworks.binding" %%% "futurebinding" % "12.1.1"
  )
).enablePlugins(ScalaJSPlugin, ScalaJSWeb).dependsOn(shared.js)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("shared")).settings(
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "4.2.1")
  .jsConfigure(_.enablePlugins(ScalaJSWeb))