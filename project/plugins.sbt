addSbtPlugin("org.scalameta"       % "sbt-scalafmt"                  % "2.5.0")
addSbtPlugin("pl.project13.scala"  % "sbt-jmh"                       % "0.4.5")
addSbtPlugin("com.eed3si9n"        % "sbt-buildinfo"                 % "0.11.0")
addSbtPlugin("org.scoverage"       % "sbt-scoverage"                 % "2.0.7")
addSbtPlugin("org.scalameta"       % "sbt-mdoc"                      % "2.3.7")
addSbtPlugin("ch.epfl.scala"       % "sbt-bloop"                     % "1.5.9")
addSbtPlugin("com.github.sbt"      % "sbt-unidoc"                    % "0.5.0")
addSbtPlugin("com.github.sbt"      % "sbt-ci-release"                % "1.5.11")
addSbtPlugin("com.github.reibitto" % "sbt-welcome"                   % "0.2.2")
addSbtPlugin("dev.zio"             % "zio-sbt-website"               % "0.3.4")
addSbtPlugin("org.portable-scala"  % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scala-native"    % "sbt-scala-native"              % "0.4.17")
addSbtPlugin("org.portable-scala"  % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-js"        % "sbt-scalajs"                   % "1.16.0")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.6"

resolvers += Resolver.sonatypeRepo("public")
