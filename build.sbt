import sbt._
import Keys._
import play.Play.autoImport._
import com.typesafe.sbt.coffeescript.SbtCoffeeScript.autoImport._
import com.typesafe.sbt.less.SbtLess.autoImport._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import play.PlayScala
import com.typesafe.sbt.web.SbtWeb

name := "AuctionHouse"

version := "0.7-SNAPSHOT"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  jdbc
  ,anorm
  ,"com.typesafe.play" %% "play-slick" % "0.8.1"
  ,"org.webjars" %% "webjars-play" % "2.3.0"
  ,"org.webjars" % "angularjs" % "1.3.15"
  ,"org.webjars" % "bootstrap" % "3.3.4"
  ,"com.typesafe.akka" %% "akka-actor" % "2.3.9"
  ,"org.postgresql" % "postgresql" % "9.2-1004-jdbc4"
)

CoffeeScriptKeys.bare := true

LessKeys.strictMath in Assets := true

includeFilter in (Assets, LessKeys.less) := "*.less"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)
