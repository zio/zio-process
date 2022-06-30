import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo._

object BuildHelper {
  private val versions: Map[String, String] = {
    import org.snakeyaml.engine.v2.api.{ Load, LoadSettings }

    import java.util.{ List => JList, Map => JMap }
    import scala.jdk.CollectionConverters._
    val doc  = new Load(LoadSettings.builder().build())
      .loadFromReader(scala.io.Source.fromFile(".github/workflows/ci.yml").bufferedReader())
    val yaml = doc.asInstanceOf[JMap[String, JMap[String, JMap[String, JMap[String, JMap[String, JList[String]]]]]]]
    val list = yaml.get("jobs").get("test").get("strategy").get("matrix").get("scala").asScala
    list.map { v =>
      val vs = v.split('.'); val init = vs.take(vs(0) match { case "2" => 2; case _ => 1 }); (init.mkString("."), v)
    }.toMap
  }

  private val Scala211: String = versions("2.11")
  private val Scala212: String = versions("2.12")
  private val Scala213: String = versions("2.13")
  private val Scala3: String   = versions("3")

  private val stdOptions = Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings"
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
    "-Ywarn-unused-import"
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
    crossScalaVersions := Seq(Scala211, Scala212, Scala213, Scala3),
    ThisBuild / scalaVersion := Scala213,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
