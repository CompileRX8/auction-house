import sbt._
import Keys._
import com.typesafe.sbt.coffeescript.SbtCoffeeScript.autoImport._
import com.typesafe.sbt.less.SbtLess.autoImport._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web.SbtWeb

name := "AuctionHouse"

version := "0.9-SNAPSHOT"

scalaVersion := "2.11.6"

//resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  jdbc
  ,evolutions
  ,"com.typesafe.play" %% "play-slick" % "1.0.1"
  ,"com.typesafe.play" %% "play-slick-evolutions" % "1.0.1"
  ,"org.postgresql" % "postgresql" % "9.4-1201-jdbc41"
//  ,"com.typesafe.play" %% "anorm" % "2.4.0"
//  ,"com.h2database" % "h2" % "1.4.187"
  ,"org.webjars" %% "webjars-play" % "2.4.0-1"
  ,"org.webjars" % "angularjs" % "1.3.15"
  ,"org.webjars" % "bootstrap" % "3.3.5"
  ,"com.typesafe.akka" %% "akka-actor" % "2.3.11"
  ,specs2 % Test
)

CoffeeScriptKeys.bare := true

LessKeys.strictMath in Assets := true

includeFilter in (Assets, LessKeys.less) := "*.less"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)
