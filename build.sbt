import Dependencies.*
import play.core.PlayVersion.pekkoVersion
scalaVersion := "3.3.4"
name := "ytchatinteraction"
version := "1.0-SNAPSHOT"
lazy val root = (project in file(".")).enablePlugins(PlayScala)

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
  "com.iheart" %% "ficus" % "1.5.2",   // Typesafe config utilities
  "net.codingwell" %% "scala-guice" % "6.0.0",  // For Dependency Injection
  
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
  "io.cequence" %% "openai-scala-client" % "1.2.0"
)

Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)
Test / javaOptions += "-Dslick.dbs.default.connectionTimeout=30 seconds"
Test / javaOptions += "-Dconfig.file=conf/test.conf"
