addSbtPlugin("org.scalameta"       % "sbt-scalafmt"                  % "2.5.6")
addSbtPlugin("com.eed3si9n"        % "sbt-buildinfo"                 % "0.13.1")
addSbtPlugin("org.scoverage"       % "sbt-scoverage"                 % "2.4.4")
addSbtPlugin("org.scalameta"       % "sbt-mdoc"                      % "2.8.2")
addSbtPlugin("com.github.sbt"      % "sbt-unidoc"                    % "0.6.0")
addSbtPlugin("com.github.sbt"      % "sbt-ci-release"                % "1.11.2")
addSbtPlugin("com.github.reibitto" % "sbt-welcome"                   % "0.5.0")
addSbtPlugin("dev.zio"             % "zio-sbt-website"               % "0.4.9")
addSbtPlugin("org.portable-scala"  % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scala-native"    % "sbt-scala-native"              % "0.5.9")
addSbtPlugin("org.portable-scala"  % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-js"        % "sbt-scalajs"                   % "1.22.0")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.0.1"

resolvers ++= Resolver.sonatypeOssRepos("public")
