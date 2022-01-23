addSbtPlugin("org.scalameta"       % "sbt-scalafmt"   % "2.4.5")
addSbtPlugin("pl.project13.scala"  % "sbt-jmh"        % "0.4.3")
addSbtPlugin("com.eed3si9n"        % "sbt-buildinfo"  % "0.10.0")
addSbtPlugin("org.scoverage"       % "sbt-scoverage"  % "1.9.2")
addSbtPlugin("org.scalameta"       % "sbt-mdoc"       % "2.2.24")
addSbtPlugin("ch.epfl.scala"       % "sbt-bloop"      % "1.4.11")
addSbtPlugin("com.github.sbt"      % "sbt-unidoc"     % "0.5.0")
addSbtPlugin("com.github.sbt"      % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.github.reibitto" % "sbt-welcome"    % "0.2.2")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.3"
