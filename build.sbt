name := "AuctionHouse"

version := "0.8-CCCAuction2016"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  ws
  ,cache
  ,evolutions
  ,"com.typesafe.play" %% "play-slick" % "1.1.1"
  ,"com.typesafe.play" %% "play-slick-evolutions" % "1.1.1"
  ,"org.webjars" %% "webjars-play" % "2.4.0-2"
  ,"org.webjars" % "angularjs" % "1.4.9"
  ,"org.webjars" % "bootstrap" % "3.3.5"
  ,"com.typesafe.akka" %% "akka-actor" % "2.3.9"
  ,"org.postgresql" % "postgresql" % "9.4.1207"
)

routesGenerator := InjectedRoutesGenerator

CoffeeScriptKeys.bare := true

LessKeys.strictMath in Assets := true

includeFilter in (Assets, LessKeys.less) := "*.less"

lazy val root = (project in file(".")).enablePlugins(PlayScala).enablePlugins(SbtWeb)

herokuAppName in Compile := "cccauction2016"

pipelineStages := Seq(rjs, digest, gzip)

RjsKeys.mainModule := "app"
