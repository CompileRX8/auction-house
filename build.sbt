name := "AuctionHouse"

version := "0.7.1-SNAPSHOT"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  jdbc
  ,"com.typesafe.play" %% "play-slick" % "2.0.0"
  ,"org.webjars" %% "webjars-play" % "2.5.0-4"
  ,"org.webjars" % "angularjs" % "1.3.15"
  ,"org.webjars" % "bootstrap" % "3.3.4"
  ,"com.typesafe.akka" %% "akka-actor" % "2.4.17"
  ,"org.postgresql" % "postgresql" % "42.0.0"
)

herokuAppName in Compile := "cccauction2017"

CoffeeScriptKeys.bare := true

LessKeys.strictMath in Assets := true

includeFilter in (Assets, LessKeys.less) := "*.less"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)
