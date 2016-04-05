name := "AuctionHouse"

version := "0.9-SNAPSHOT"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  jdbc
  ,evolutions
  ,"com.typesafe.play" %% "play-slick" % "1.0.1"
  ,"com.typesafe.play" %% "play-slick-evolutions" % "1.0.1"
  ,"org.postgresql" % "postgresql" % "9.4-1201-jdbc41"
  ,"org.webjars" %% "webjars-play" % "2.4.0-1"
  ,"org.webjars" % "angularjs" % "1.3.15"
  ,"org.webjars" % "bootstrap" % "3.3.5"
  ,"com.typesafe.akka" %% "akka-actor" % "2.3.11"
  ,"com.mohiva" %% "play-silhouette" % "3.0.4"
  ,"net.ceedubs" %% "ficus" % "1.1.2"
  ,specs2 % Test
)

CoffeeScriptKeys.bare := true

LessKeys.strictMath in Assets := true

includeFilter in (Assets, LessKeys.less) := "*.less"

herokuAppName in Compile := "auction-house"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)

routesGenerator := InjectedRoutesGenerator

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen" // Warn when numerics are widened.
)
