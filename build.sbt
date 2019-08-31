// shadow sbt-scalajs' crossProject from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import BuildHelper._
import xerial.sbt.Sonatype._
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue

name := "zio"

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://zio.dev")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    ),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher,
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio/"), "scm:git:git@github.com:zio/zio.git")
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
addCommandAlias("compileJVM", ";coreJVM/test:compile;stacktracerJVM/test:compile")
addCommandAlias(
  "testJVM",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testJVM/test:run;testRunnerJVM/test:run;examplesJVM/test:compile"
)
addCommandAlias(
  "testJS",
  ";coreTestsJS/test;stacktracerJS/test;streamsTestsJS/test;testJS/test:run;examplesJS/test:compile"
)

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true,
    console := (console in Compile in coreJVM).value,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library")
  )
  .aggregate(
    coreJVM,
    coreJS,
    coreTestsJVM,
    coreTestsJS,
    docs,
    streamsJVM,
    streamsJS,
    streamsTestsJVM,
    streamsTestsJS,
    benchmarks,
    testJVM,
    testJS,
    stacktracerJS,
    stacktracerJVM,
    testRunnerJS,
    testRunnerJVM
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .dependsOn(stacktracer)
  .settings(stdSettings("zio"))
  .settings(buildInfoSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val coreJVM = core.jvm
  .settings(dottySettings)
  .settings(replSettings)

lazy val coreJS = core.js

lazy val coreTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-tests"))
  .dependsOn(core)
  .dependsOn(test % "test->test;compile->compile")
  .settings(stdSettings("core-tests"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner % "test->test;compile->compile")
  .settings(buildInfoSettings)
  .settings(publishArtifact in (Test, packageBin) := true)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core"          % "4.7.0" % Test,
      "org.specs2" %%% "specs2-scalacheck"    % "4.7.0" % Test,
      "org.specs2" %%% "specs2-matcher-extra" % "4.7.0" % Test
    )
  )
  .enablePlugins(BuildInfoPlugin)

lazy val coreTestsJVM = coreTests.jvm
  .configure(_.enablePlugins(JCStressPlugin))
  .settings(replSettings)

lazy val coreTestsJS = coreTests.js
  .settings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test
  )

lazy val streams = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams"))
  .dependsOn(core)
  .settings(stdSettings("zio-streams"))
  .settings(buildInfoSettings)
  .settings(streamReplSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val streamsJVM = streams.jvm
lazy val streamsJS  = streams.js

lazy val streamsTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams-tests"))
  .dependsOn(streams)
  .dependsOn(coreTests % "test->test;compile->compile")
  .settings(stdSettings("zio-streams-tests"))
  .settings(buildInfoSettings)
  .settings(streamReplSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val streamsTestsJVM = streamsTests.jvm.dependsOn(coreTestsJVM % "test->compile")
lazy val streamsTestsJS  = streamsTests.js

lazy val test = crossProject(JSPlatform, JVMPlatform)
  .in(file("test"))
  .dependsOn(core, streams)
  .settings(stdSettings("zio-test"))
  .settings(
    libraryDependencies ++= Seq(
      "org.portable-scala" %%% "portable-scala-reflect" % "0.1.0"
    )
  )

lazy val testJVM = test.jvm
lazy val testJS = test.js.settings(
  libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3" % Test,
  scalaJSUseMainModuleInitializer in Test := true
)

lazy val stacktracer = crossProject(JSPlatform, JVMPlatform)
  .in(file("stacktracer"))
  .settings(stdSettings("zio-stacktracer"))
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %%% "specs2-core"          % "4.7.0" % Test,
      "org.specs2" %%% "specs2-scalacheck"    % "4.7.0" % Test,
      "org.specs2" %%% "specs2-matcher-extra" % "4.7.0" % Test
    )
  )

lazy val stacktracerJS = stacktracer.js
lazy val stacktracerJVM = stacktracer.jvm
  .settings(dottySettings)
  .settings(replSettings)

lazy val testRunner = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-sbt"))
  .settings(stdSettings("zio-test-sbt"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang"     % "scala-reflect"            % scalaVersion.value,
      "org.portable-scala" %%% "portable-scala-reflect" % "0.1.0"
    ),
    mainClass in (Test, run) := Some("zio.test.sbt.TestMain")
  )
  .jsSettings(libraryDependencies ++= Seq("org.scala-js" %% "scalajs-test-interface" % "0.6.28"))
  .jvmSettings(libraryDependencies ++= Seq("org.scala-sbt" % "test-interface" % "1.0"))
  .dependsOn(core % "test->test;compile->compile")
  .dependsOn(test % "test->test;compile->compile")

lazy val testRunnerJVM = testRunner.jvm
lazy val testRunnerJS  = testRunner.js

/**
 * Examples sub-project that is not included in the root project.
 * To run tests :
 * `sbt "examplesJVM/test"`
 */
lazy val examples = crossProject(JVMPlatform, JSPlatform)
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner % "test->test;compile->compile")

lazy val examplesJS  = examples.js
lazy val examplesJVM = examples.jvm

lazy val benchmarks = project.module
  .dependsOn(coreJVM, streamsJVM)
  .enablePlugins(JmhPlugin)
  .settings(replSettings)
  .settings(
    // skip 2.13 benchmarks until monix & twitter-util publish for 2.13
    crossScalaVersions -= "2.13.0",
    //
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "co.fs2"                   %% "fs2-core"        % "1.1.0-M1",
        "com.google.code.findbugs" % "jsr305"           % "3.0.2",
        "com.twitter"              %% "util-collection" % "19.1.0",
        "com.typesafe.akka"        %% "akka-stream"     % "2.5.25",
        "io.monix"                 %% "monix"           % "3.0.0-RC2",
        "io.projectreactor"        % "reactor-core"     % "3.2.11.RELEASE",
        "io.reactivex.rxjava2"     % "rxjava"           % "2.2.12",
        "org.ow2.asm"              % "asm"              % "7.1",
        "org.scala-lang"           % "scala-compiler"   % scalaVersion.value % Provided,
        "org.scala-lang"           % "scala-reflect"    % scalaVersion.value,
        "org.typelevel"            %% "cats-effect"     % "2.0.0-RC2"
      ),
    unusedCompileDependenciesFilter -= libraryDependencies.value
      .map(moduleid => moduleFilter(organization = moduleid.organization, name = moduleid.name))
      .reduce(_ | _),
    scalacOptions in Compile in console := Seq(
      "-Ypartial-unification",
      "-language:higherKinds",
      "-language:existentials",
      "-Yno-adapted-args",
      "-Xsource:2.13",
      "-Yrepl-class-based"
    )
  )

lazy val docs = project.module
  .in(file("zio-docs"))
  .settings(
    // skip 2.13 mdoc until mdoc is available for 2.13
    crossScalaVersions -= "2.13.0",
    //
    skip.in(publish) := true,
    moduleName := "zio-docs",
    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    libraryDependencies ++= Seq(
      "com.github.ghik"     %% "silencer-lib"                % "1.4.2" % "provided",
      "commons-io"          % "commons-io"                   % "2.6" % "provided",
      "org.jsoup"           % "jsoup"                        % "1.12.1" % "provided",
      "org.reactivestreams" % "reactive-streams-examples"    % "1.0.3" % "provided",
      "dev.zio"             %% "zio-interop-cats"            % "2.0.0.0-RC2",
      "dev.zio"             %% "zio-interop-future"          % "2.12.8.0-RC3",
      "dev.zio"             %% "zio-interop-monix"           % "3.0.0.0-RC4",
      "dev.zio"             %% "zio-interop-scalaz7x"        % "7.2.27.0-RC1",
      "dev.zio"             %% "zio-interop-java"            % "1.1.0.0-RC3",
      "dev.zio"             %% "zio-interop-reactivestreams" % "1.0.3.0-RC1",
      "dev.zio"             %% "zio-interop-twitter"         % "19.7.0.0-RC1"
    )
  )
  .dependsOn(
    coreJVM,
    streamsJVM
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
