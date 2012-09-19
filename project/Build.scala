import sbt._
import Keys._

object Build extends sbt.Build {
  import Dependencies._

  lazy val myProject = Project("eas-account-loader", file("."))
    .settings(
      organization  := "com.timetrade",
      version       := "0.1.0",
      scalaVersion  := "2.9.2",
      scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
      resolvers     ++= Dependencies.resolutionRepos,
      libraryDependencies ++= Seq(
        Compile.akkaActor,
        Compile.akkaSlf4j,
        Compile.commonsCodec,
        Compile.sprayClient,
        Compile.sprayJson
      )
    )
}

object Dependencies {
  val resolutionRepos = Seq(
    // On my laptop: where to find eas-connector jar:
    //
    "Local Ivy Repository" at "file://"+Path.userHome.absolutePath+"/.ivy2.local",

    // This took bloody hours to stumble upon. Ivy sucks bowling balls through garden hoses.
    // Where to find other timetrade jars:
    //
    Resolver.url("Timetrade Nexus repo",
                  url("http://artifactrepo/nexus/content/repositories/releases"))
                  (Patterns(Seq("[organisation]/[module]/[revision]/ivy-[revision].xml"),
                            Seq("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"),
                            true)),

    // Pelops:
    //
    "S7 repo on Github" at "https://github.com/s7/mvnrepo/raw/master/org",

    // Typesafe stuff:
    //
    ScalaToolsSnapshots,
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",

    // Spray repo
    "spray repo" at "http://repo.spray.cc/"
  )

  object V {
    val akka         = "2.0.3"
    val spray        = "1.0-M2"
  }

  object Compile {
    val sprayClient     = "cc.spray"                  %  "spray-client"      % V.spray   % "compile" withSources()
    val sprayJson       = "cc.spray"                  %  "spray-json_2.9.2"  % "1.1.1"   % "compile" withSources()
    val akkaActor       = "com.typesafe.akka"         %  "akka-actor"        % V.akka    % "compile" withSources()
    val akkaSlf4j       = "com.typesafe.akka"         %  "akka-slf4j"        % V.akka    % "compile" withSources()
    val commonsCodec    = "commons-codec"             %  "commons-codec"     % "1.6"    % "compile" withSources()
  }
}
