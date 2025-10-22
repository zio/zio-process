addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.1")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.0")

addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.7.1")

addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.10")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.3")

addSbtPlugin("com.github.reibitto" % "sbt-welcome" % "0.5.0")

addSbtPlugin("dev.zio" % "zio-sbt-website" % "0.4.0-alpha.31")

addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")

addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.17")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.19.0")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.9"

resolvers += Resolver.sonatypeRepo("public")
