import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "AuctionHouse"
  val appVersion = "0.1-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    "com.typesafe.slick" %% "slick" % "2.0.0",
    "org.postgresql" % "postgresql" % "9.2-1004-jdbc4",
    "org.specs2" %% "specs2" % "1.14" % "test",
    "commons-codec" % "commons-codec" % "1.7",
    "com.typesafe.akka" %% "akka-testkit" % "2.2.4",

    "org.webjars" %% "webjars-play" % "2.2.1-2",
    "org.webjars" % "angularjs" % "1.2.14",
    "org.webjars" % "bootstrap" % "3.1.1"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    scalaVersion := "2.10.3",

    coffeescriptOptions := Seq("bare"),

    resolvers += "typesafe" at "http://repo.typesafe.com/typesafe/repo"
  ).settings()

}
