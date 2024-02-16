import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo._
import sbtcrossproject.CrossPlugin.autoImport._

object BuildHelper {

  val Scala212 = "2.12.18"
  val Scala213 = "2.13.11"
  val Scala3   = "3.3.1"

  private val stdOptions = Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-deprecation"
  )

  private val stdOpts213 = Seq(
    "-Wunused:imports",
    "-Wvalue-discard",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:params",
    "-Wvalue-discard"
  )

  private val stdOptsUpto212 = Seq(
    "-Xfuture",
    "-Ypartial-unification",
    "-Ywarn-nullary-override",
    "-Yno-adapted-args",
    "-Ywarn-infer-any",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused-import",
    "-Xfatal-warnings"
  )

  private val stdOptsUpto211 = Seq(
    "-explaintypes",
    "-Yrangepos",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-target:jvm-1.8"
  )

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        stdOpts213 ++ stdOptsUpto211
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        ) ++ stdOptsUpto212 ++ stdOptsUpto211
      case Some((3, _))  =>
        Seq("-noindent")
      case _             =>
        Seq("-Xexperimental") ++ stdOptsUpto212 ++ stdOptsUpto211
    }

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName,
      buildInfoObject := "BuildInfo"
    )

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    fork := true,
    crossScalaVersions := Seq(Scala212, Scala213),
    ThisBuild / scalaVersion := Scala213,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )

  def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*) =
    for {
      platform <- List("shared", platform)
      version  <- "scala" :: versions.toList.map("scala-" + _)
      result    = baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
      if result.exists
    } yield result

  def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File) = {
    val versions = CrossVersion.partialVersion(scalaVer) match {
      case Some((2, 11)) =>
        List("2.11", "2.11+", "2.11-2.12", "2.x")
      case Some((2, 12)) =>
        List("2.12", "2.11+", "2.12+", "2.11-2.12", "2.12-2.13", "2.x")
      case Some((2, 13)) =>
        List("2.13", "2.11+", "2.12+", "2.13+", "2.12-2.13", "2.x")
      case Some((3, 0))  =>
        List("dotty", "2.11+", "2.12+", "2.13+", "3.x")
      case _             =>
        List()
    }
    platformSpecificSources(platform, conf, baseDir)(versions: _*)
  }

  lazy val crossProjectSettings = Seq(
    Compile / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "main",
        baseDirectory.value
      )
    },
    Test / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "test",
        baseDirectory.value
      )
    }
  )

  val dottySettings = Seq(
    crossScalaVersions += Scala3,
    scalacOptions ++= {
      (scalaVersion.value, crossProjectPlatform.value.identifier) match {
        case (Scala3, "js") => Seq("-noindent", "-scalajs")
        case (Scala3, _)    => Seq("-noindent")
        case _              => Seq()
      }
    },
    scalacOptions --= {
      if (scalaVersion.value == Scala3)
        Seq("-Xfatal-warnings")
      else
        Seq()
    },
    Compile / doc / sources := {
      val old = (Compile / doc / sources).value
      if (scalaVersion.value == Scala3) {
        Nil
      } else {
        old
      }
    },
    Test / parallelExecution := {
      val old = (Test / parallelExecution).value
      if (scalaVersion.value == Scala3) {
        false
      } else {
        old
      }
    }
  )

}
