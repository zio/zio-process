import BuildHelper._
import sbtwelcome._
import sbt.addSbtPlugin
import org.scalajs.linker.interface.ModuleInitializer

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-process/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
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

addCommandAlias("lint", "check")

ThisBuild / ciEnabledBranches          := Seq("series/2.x")
ThisBuild / ciTargetJavaVersions       := Seq("17")
ThisBuild / ciDefaultJavaVersion       := "17"
ThisBuild / ciTargetScalaVersions      := Map(
  "zioProcessJVM"    -> Seq(Scala212, Scala213, Scala3),
  "zioProcessNative" -> Seq(Scala212, Scala213, Scala3),
  "zioProcessJS"     -> Seq(Scala212, Scala213, Scala3)
)
ThisBuild / ciWorkflowEnv              := Map(
  "JDK_JAVA_OPTIONS" -> "-XX:+PrintCommandLineFlags -Xms2G -Xmx8G -Xss4M -XX:+UseG1GC -XX:ReservedCodeCacheSize=512M -XX:NonProfiledCodeHeapSize=256M",
  "SBT_OPTS"         -> "-XX:+PrintCommandLineFlags -Xms2G -Xmx8G -Xss4M -XX:+UseG1GC -XX:ReservedCodeCacheSize=512M -XX:NonProfiledCodeHeapSize=256M"
)
ThisBuild / ciCheckWebsiteBuildProcess := Seq(
  zio.sbt.githubactions.Step.SingleStep(name = "Check document generation", run = Some("sbt docs/compileDocs"))
)
inThisBuild(List(ciTestJobs := {
  import zio.json.ast.Json
  import zio.sbt.ZioSbtCiPlugin.SetupNodeJs
  import zio.sbt.githubactions.{ Step, Strategy }

  val setupNode = SetupNodeJs.copy(parameters = SetupNodeJs.parameters.updated("node-version", Json.Str("21.6.1")))
  val platforms =
    ciTestJobs.value.map(job => job.copy(steps = job.steps.init ++ Seq(setupNode) ++ job.steps.lastOption))
  val jvms      = ciTestJobs.value.map { job =>
    job.copy(
      id = "test-jvms",
      name = "Test JVMs",
      strategy = Some(Strategy(matrix = Map("java" -> List("21", "25")), failFast = false)),
      steps =
        job.steps.init :+ Step.SingleStep(name = "Test", run = Some(s"sbt --no-colors ++$Scala213 zioProcessJVM/test"))
    )
  }
  platforms ++ jvms
}))
ThisBuild / ciUpdateReadmeJobs         := Seq.empty
ThisBuild / ciPostReleaseJobs          := Seq.empty

logo        :=
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
logoColor   := scala.Console.RED
usefulTasks := Seq(
  UsefulTask("~compile", "Compile all modules with file-watch enabled"),
  UsefulTask("fmt", "Run scalafmt on the entire project"),
  UsefulTask("docs/docusaurusCreateSite", "Generates the microsite"),
  UsefulTask("testOnly *.YourSpec -- -t \"YourLabel\"", "Only runs tests with matching term").noAlias
)

val zioVersion = "2.1.26"

val scalaCollectionCompatVersion = "2.14.0"

val scalaJavaTimeVersion = "2.7.0"

lazy val root =
  project
    .in(file("."))
    .settings(
      name               := "zio-process",
      publish / skip     := true,
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
        "org.scala-lang.modules" %%% "scala-collection-compat" % scalaCollectionCompatVersion,
        "dev.zio"                %%% "zio-test"                % zioVersion % Test,
        "dev.zio"                %%% "zio-test-sbt"            % zioVersion % Test
      )
    )
    .enablePlugins(BuildInfoPlugin)
    .settings(dottySettings)
    .nativeSettings(publish / skip := true)
    .nativeSettings(Test / fork := false)
    .nativeSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test
      )
    )
    .jsSettings(Test / fork := false)
    .jsSettings(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % scalaJavaTimeVersion % Test
      )
    )
    .jsSettings(
      scalaJSUseMainModuleInitializer := true
    )

lazy val docs = project
  .in(file("zio-process-docs"))
  .settings(stdSettings("zio-process-docs"))
  .settings(
    moduleName                                 := "zio-process-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq("dev.zio" %% "zio" % zioVersion),
    projectName                                := "ZIO Process",
    mainModuleName                             := (zioProcess.jvm / moduleName).value,
    projectStage                               := ProjectStage.ProductionReady,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioProcess.jvm)
  )
  .dependsOn(zioProcess.jvm)
  .enablePlugins(WebsitePlugin)
