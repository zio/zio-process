import BuildHelper._
import sbtwelcome._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.dev/zio-process/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

logo :=
  s"""
     |      _
     |     (_)
     |  _____  ___    _ __  _ __ ___   ___ ___  ___ ___
     | |_  / |/ _ \\  | '_ \\| '__/ _ \\ / __/ _ \\/ __/ __|
     |  / /| | (_) | | |_) | | | (_) | (_|  __/\\__ \\__ \\
     | /___|_|\\___/  | .__/|_|  \\___/ \\___\\___||___/___/
     |               | |
     |               |_|
     |
     | ${version.value}
     |
     | Scala ${scalaVersion.value}
     |
     |""".stripMargin
logoColor := scala.Console.RED
usefulTasks := Seq(
  UsefulTask("a", "~compile", "Compile all modules with file-watch enabled"),
  UsefulTask("b", "fmt", "Run scalafmt on the entire project"),
  UsefulTask("c", "docs/docusaurusCreateSite", "Generates the microsite"),
  UsefulTask("", "testOnly *.YourSpec -- -t \"YourLabel\"", "Only runs tests with matching term")
)

val zioVersion = "2.0.10"

lazy val root =
  project
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(zioProcess, docs)

lazy val zioProcess =
  project
    .in(file("zio-process"))
    .settings(stdSettings("zio-process"))
    .settings(buildInfoSettings("zio.process"))
    .settings(
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      libraryDependencies ++= Seq(
        "dev.zio"                %% "zio"                     % zioVersion,
        "dev.zio"                %% "zio-streams"             % zioVersion,
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0",
        "dev.zio"                %% "zio-test"                % zioVersion % "test",
        "dev.zio"                %% "zio-test-sbt"            % zioVersion % "test"
      )
    )
    .enablePlugins(BuildInfoPlugin)

lazy val docs = project
  .in(file("zio-process-docs"))
  .settings(stdSettings("zio-process-docs"))
  .settings(
    moduleName := "zio-process-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    crossScalaVersions -= Scala211,
    libraryDependencies ++= Seq("dev.zio" %% "zio" % zioVersion),
    projectName := "ZIO Process",
    mainModuleName := (zioProcess / moduleName).value,
    projectStage := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioProcess),
    docsPublishBranch := "series/2.x"
  )
  .dependsOn(zioProcess)
  .enablePlugins(WebsitePlugin)
