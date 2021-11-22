addSbtPlugin("org.scalameta"       % "sbt-scalafmt"   % "2.4.4")
addSbtPlugin("pl.project13.scala"  % "sbt-jmh"        % "0.4.3")
addSbtPlugin("com.eed3si9n"        % "sbt-buildinfo"  % "0.10.0")
addSbtPlugin("org.scoverage"       % "sbt-scoverage"  % "1.9.0")
addSbtPlugin("org.scalameta"       % "sbt-mdoc"       % "2.2.24")
addSbtPlugin("ch.epfl.scala"       % "sbt-bloop"      % "1.4.11")
addSbtPlugin("com.eed3si9n"        % "sbt-unidoc"     % "0.4.3")
addSbtPlugin("com.geirsson"        % "sbt-ci-release" % "1.5.7")
addSbtPlugin("com.github.reibitto" % "sbt-welcome"    % "0.2.1")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.3"
