import com.typesafe.sbt.coffeescript.Import.CoffeeScriptKeys
import com.typesafe.sbt.less.Import.LessKeys
import com.typesafe.sbt.web.SbtWeb
import play.PlayScala

name := "AuctionHouse"

version := "0.4-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  jdbc,
  "org.webjars" %% "webjars-play" % "2.3.0",
  "org.webjars" % "angularjs" % "1.2.25",
  "org.webjars" % "bootstrap" % "3.2.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.4",
  "com.typesafe.play" %% "play-slick" % "0.8.0",
  "org.postgresql" % "postgresql" % "9.2-1004-jdbc4",
  "org.specs2" %% "specs2" % "2.4.2" % "test",
  "commons-codec" % "commons-codec" % "1.9"
)

CoffeeScriptKeys.bare := true

includeFilter in (Assets, LessKeys.less) := "*.less"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)
