// *****************************************************************************
// Projects
// *****************************************************************************
lazy val scalaVersion213 = "2.13.3"

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
        library.zio          % Provided,
        library.zioTest      % Test,
        library.zioTestJunit % Test,
        library.zioTestSbt   % Test
      ),
      publishArtifact := true,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      scalaVersion := scalaVersion213,
      crossScalaVersions := Seq("2.12.14", scalaVersion213)
    )

lazy val examples =
  project
    .in(file("examples"))
    .settings(settings)
    .settings(
      name := "examples",
      libraryDependencies ++= Seq(
        library.testContainers % Test,
        library.zio            % Provided,
        library.zioTest        % Test,
        library.zioTestJunit   % Test,
        library.zioTestSbt     % Test
      ),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      Test / fork := true,
      scalaVersion := scalaVersion213,
      crossScalaVersions := Seq("2.12.14", scalaVersion213)
    )
    .dependsOn(root)

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {

    object Version {
      val zio                 = "1.0.12"
      val sttp                = "2.2.8"
      val typesafeConfig      = "1.4.0"
      val promqlClient        = "0.5.0"
      val timeseries          = "1.7.0"
      val testContainersScala = "0.38.8"
    }

    val promqlClient   = "io.sqooba.oss"                %% "scala-promql-client"            % Version.promqlClient
    val timeseries     = "io.sqooba.oss"                %% "scala-timeseries-lib"           % Version.timeseries
    val zio            = "dev.zio"                      %% "zio"                            % Version.zio
    val zioTest        = "dev.zio"                      %% "zio-test"                       % Version.zio
    val zioTestSbt     = "dev.zio"                      %% "zio-test-sbt"                   % Version.zio
    val zioTestJunit   = "dev.zio"                      %% "zio-test-junit"                 % Version.zio
    val sttpZioClient  = "com.softwaremill.sttp.client" %% "async-http-client-backend-zio"  % Version.sttp
    val typesafeConfig = "com.typesafe"                  % "config"                         % Version.typesafeConfig
    val testContainers = "com.dimafeng"                 %% "testcontainers-scala-scalatest" % Version.testContainersScala

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
    pomIncludeRepository := { _ =>
      false
    },
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
