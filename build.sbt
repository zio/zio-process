import BuildHelper._
import sbtwelcome._
import sbt.addSbtPlugin
import org.scalajs.linker.interface.ModuleInitializer

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

val zioVersion = "2.0.21"

lazy val root =
  project
    .in(file("."))
    .settings(
      publish / skip := true,
      crossScalaVersions := Nil
    )
    .aggregate(zioProcess.jvm, zioProcess.native, zioProcess.js, docs)

lazy val zioProcess =
  crossProject(JVMPlatform, NativePlatform, JSPlatform)
    .in(file("zio-process"))
    .settings(stdSettings("zio-process"))
    .settings(crossProjectSettings)
    .settings(buildInfoSettings("zio.process"))
    .settings(testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")))
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio"                %%% "zio"                     % zioVersion,
        "dev.zio"                %%% "zio-streams"             % zioVersion,
        "org.scala-lang.modules" %%% "scala-collection-compat" % "2.9.0",
        "dev.zio"                %%% "zio-test"                % zioVersion % Test,
        "dev.zio"                %%% "zio-test-sbt"            % zioVersion % Test
      )
    )
    .enablePlugins(BuildInfoPlugin)
    .settings(dottySettings)
    .nativeSettings(Test / fork := false)
    .nativeSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.5.0" % Test
      )
    )
    .jsSettings(Test / fork := false)
    .jsSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.5.0" % Test
      )
    )
    .jsSettings(
      scalaJSUseMainModuleInitializer := true
    )

lazy val docs = project
  .in(file("zio-process-docs"))
  .settings(stdSettings("zio-process-docs"))
  .settings(
    moduleName := "zio-process-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq("dev.zio" %% "zio" % zioVersion),
    projectName := "ZIO Process",
    mainModuleName := (zioProcess.jvm / moduleName).value,
    projectStage := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioProcess.jvm),
    docsPublishBranch := "series/2.x"
  )
  .dependsOn(zioProcess.jvm)
  .enablePlugins(WebsitePlugin)
