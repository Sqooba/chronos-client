// *****************************************************************************
// Projects
// *****************************************************************************

lazy val root =
  project
    .in(file("."))
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.promqlClient,
        library.timeseries,
        library.sttpZioClient,
        library.typesafeConfig,
        library.zio        % Provided,
        library.zioTest    % Test,
        library.zioTestSbt % Test
      ),
      publishArtifact := true,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      scalaVersion := "2.13.3",
      crossScalaVersions := Seq("2.12.12", "2.13.3")
    )

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {

    object Version {
      val zio            = "1.0.3"
      val sttp           = "2.2.8"
      val typesafeConfig = "1.4.0"
      val promqlClient   = "HEAD-SNAPSHOT"
      // FIXME: set to newest version when released
      val timeseries = "HEAD-SNAPSHOT"
    }

    val promqlClient   = "io.sqooba.oss"                %% "scala-promql-client"           % Version.promqlClient
    val timeseries     = "io.sqooba.oss"                %% "scala-timeseries-lib"          % Version.timeseries
    val zio            = "dev.zio"                      %% "zio"                           % Version.zio
    val zioTest        = "dev.zio"                      %% "zio-test"                      % Version.zio
    val zioTestSbt     = "dev.zio"                      %% "zio-test-sbt"                  % Version.zio
    val sttpZioClient  = "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % Version.sttp
    val typesafeConfig = "com.typesafe"                  % "config"                        % Version.typesafeConfig

  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
    scalafmtSettings ++
    commandAliases

lazy val commonSettings =
  Seq(
    name := "chronos-client",
    organization := "io.sqooba.oss",
    organizationName := "Sqooba",
    homepage := Some(url("https://github.com/Sqooba/chronos-client/")),
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/Sqooba/chronos-client.git"),
        "scm:git:git@github.com:Sqooba/chronos-client.git"
      )
    ),
    developers := List(
      Developer("ex0ns", "Ex0ns", "", url("https://github.com/ex0ns")),
      Developer("yannbolliger", "Yann Bolliger", "", url("https://github.com/yannbolliger"))
    ),
    scalacOptions --= Seq(
      "-Xlint:nullary-override"
    )
  )

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true
  )

lazy val commandAliases =
  addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt") ++
    addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
