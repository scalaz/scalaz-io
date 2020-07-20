// shadow sbt-scalajs' crossProject from Scala.js 0.6.x
import BuildHelper._
import MimaSettings.mimaSettings
import com.typesafe.tools.mima.plugin.MimaKeys.mimaFailOnNoPrevious
import explicitdeps.ExplicitDepsPlugin.autoImport.moduleFilterRemoveValue
import sbtcrossproject.CrossPlugin.autoImport.crossProject

name := "zio"

Global / onChangedBuildSource := ReloadOnSourceChanges

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
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(url("https://github.com/zio/zio/"), "scm:git:git@github.com:zio/zio.git")
    )
  )
)

addCommandAlias("build", "prepare; testJVM")
addCommandAlias("prepare", "fix; fmt")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias(
  "fixCheck",
  "; compile:scalafix --check ; test:scalafix --check"
)
addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
addCommandAlias(
  "compileJVM",
  ";coreTestsJVM/test:compile;stacktracerJVM/test:compile;streamsTestsJVM/test:compile;testTestsJVM/test:compile;testMagnoliaTestsJVM/test:compile;testRunnerJVM/test:compile;examplesJVM/test:compile;macrosJVM/test:compile"
)
addCommandAlias("compileNative", ";coreNative/compile")
addCommandAlias(
  "testJVM",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testMagnoliaTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile;benchmarks/test:compile;macrosJVM/test"
)
addCommandAlias(
  "testJVMNoBenchmarks",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testMagnoliaTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile"
)
addCommandAlias(
  "testJVMDotty",
  ";coreTestsJVM/test;stacktracerJVM/test:compile;streamsTestsJVM/test;testTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile"
)
addCommandAlias(
  "testJVM211",
  ";coreTestsJVM/test;stacktracerJVM/test;streamsTestsJVM/test;testTestsJVM/test;testRunnerJVM/test:run;examplesJVM/test:compile;macrosJVM/test"
)
addCommandAlias(
  "testJS",
  ";coreTestsJS/test;stacktracerJS/test;streamsTestsJS/test;testTestsJS/test;testMagnoliaTestsJS/test;examplesJS/test:compile;macrosJS/test"
)
addCommandAlias(
  "testJS211",
  ";coreTestsJS/test;stacktracerJS/test;streamsTestsJS/test;testTestsJS/test;examplesJS/test:compile;macrosJS/test"
)
addCommandAlias(
  "mimaChecks",
  "all coreJVM/mimaReportBinaryIssues streamsJVM/mimaReportBinaryIssues testJVM/mimaReportBinaryIssues"
)

lazy val root = project
  .in(file("."))
  .settings(
    skip in publish := true,
    console := (console in Compile in coreJVM).value,
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-js", "scalajs-library"),
    welcomeMessage
  )
  .aggregate(
    coreJVM,
    coreJS,
    coreTestsJVM,
    coreTestsJS,
    macrosJVM,
    macrosJS,
    docs,
    streamsJVM,
    streamsJS,
    streamsTestsJVM,
    streamsTestsJS,
    benchmarks,
    testJVM,
    testJS,
    testTestsJVM,
    testTestsJS,
    stacktracerJS,
    stacktracerJVM,
    testRunnerJS,
    testRunnerJVM,
    testJunitRunnerJVM,
    testMagnoliaJVM,
    testMagnoliaJS
  )
  .enablePlugins(ScalaJSPlugin)

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("core"))
  .dependsOn(stacktracer)
  .settings(stdSettings("zio"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio"))
  .settings(scalaReflectSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val coreJVM = core.jvm
  .settings(dottySettings)
  .settings(replSettings)
  // Failures will be enabled after 1.0.0
  .settings(mimaSettings(failOnProblem = false))

lazy val coreJS = core.js

lazy val coreNative = core.native
  .settings(scalaVersion := "2.11.12")
  .settings(skip in Test := true)
  .settings(skip in doc := true)
  .settings( // Exclude from Intellij because Scala Native projects break it - https://github.com/scala-native/scala-native/issues/1007#issuecomment-370402092
    SettingKey[Boolean]("ide-skip-project") := true
  )
  .settings(sources in (Compile, doc) := Seq.empty)
  .settings(
    libraryDependencies ++= Seq(
      "dev.whaling" %%% "native-loop-core"      % "0.1.1",
      "dev.whaling" %%% "native-loop-js-compat" % "0.1.1"
    )
  )

lazy val coreTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("core-tests"))
  .dependsOn(core)
  .dependsOn(test)
  .settings(stdSettings("core-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio"))
  .settings(skip in publish := true)
  .settings(Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat)
  .enablePlugins(BuildInfoPlugin)

lazy val coreTestsJVM = coreTests.jvm
  .settings(dottySettings)
  .configure(_.enablePlugins(JCStressPlugin))
  .settings(replSettings)

lazy val coreTestsJS = coreTests.js
  .settings(testJsSettings)

lazy val macros = crossProject(JSPlatform, JVMPlatform)
  .in(file("macros"))
  .dependsOn(core)
  .settings(stdSettings("zio-macros"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)

lazy val macrosJVM = macros.jvm.settings(dottySettings)
lazy val macrosJS  = macros.js.settings(testJsSettings)

lazy val streams = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams"))
  .dependsOn(core)
  .settings(stdSettings("zio-streams"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.stream"))
  .settings(streamReplSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val streamsJVM = streams.jvm
  .settings(dottySettings)
  // No bincompat on streams yet
  .settings(mimaSettings(failOnProblem = false))

lazy val streamsJS = streams.js

lazy val streamsTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("streams-tests"))
  .dependsOn(streams)
  .dependsOn(coreTests % "test->test;compile->compile")
  .settings(stdSettings("streams-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.stream"))
  .settings(skip in publish := true)
  .settings(Compile / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars)
  .enablePlugins(BuildInfoPlugin)

lazy val streamsTestsJVM = streamsTests.jvm
  .dependsOn(coreTestsJVM % "test->compile")
  .settings(dottySettings)

lazy val streamsTestsJS = streamsTests.js
  .settings(testJsSettings)

lazy val test = crossProject(JSPlatform, JVMPlatform)
  .in(file("test"))
  .dependsOn(core, streams)
  .settings(stdSettings("zio-test"))
  .settings(crossProjectSettings)
  .settings(macroDefinitionSettings)
  .settings(macroExpansionSettings)
  .settings(
    libraryDependencies ++= Seq(
      ("org.portable-scala" %%% "portable-scala-reflect" % "1.0.0").withDottyCompat(scalaVersion.value)
    )
  )

lazy val testJVM = test.jvm
  .settings(dottySettings)
  // No bincompat on zio-test yet
  .settings(mimaSettings(failOnProblem = false))
lazy val testJS = test.js

lazy val testTests = crossProject(JSPlatform, JVMPlatform)
  .in(file("test-tests"))
  .dependsOn(test)
  .settings(stdSettings("test-tests"))
  .settings(crossProjectSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.test"))
  .settings(skip in publish := true)
  .settings(macroExpansionSettings)
  .enablePlugins(BuildInfoPlugin)

lazy val testTestsJVM = testTests.jvm.settings(dottySettings)
lazy val testTestsJS  = testTests.js.settings(testJsSettings)

lazy val testMagnolia = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-magnolia"))
  .dependsOn(test)
  .settings(stdSettings("zio-test-magnolia"))
  .settings(macroDefinitionSettings)
  .settings(
    crossScalaVersions --= Seq("2.11.12", dottyVersion),
    scalacOptions += "-language:experimental.macros",
    libraryDependencies += ("com.propensive" %%% "magnolia" % "0.16.0").exclude("org.scala-lang", "scala-compiler")
  )

lazy val testMagnoliaJVM = testMagnolia.jvm
lazy val testMagnoliaJS  = testMagnolia.js

lazy val testMagnoliaTests = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-magnolia-tests"))
  .dependsOn(testMagnolia)
  .dependsOn(testTests % "test->test;compile->compile")
  .settings(stdSettings("test-magnolia-tests"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(testRunner)
  .settings(buildInfoSettings("zio.test"))
  .settings(skip in publish := true)
  .enablePlugins(BuildInfoPlugin)

lazy val testMagnoliaTestsJVM = testMagnoliaTests.jvm
lazy val testMagnoliaTestsJS  = testMagnoliaTests.js.settings(testJsSettings)

lazy val stacktracer = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("stacktracer"))
  .settings(stdSettings("zio-stacktracer"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.internal.stacktracer"))

lazy val stacktracerJS = stacktracer.js
lazy val stacktracerJVM = stacktracer.jvm
  .settings(dottySettings)
  .settings(replSettings)

lazy val stacktracerNative = stacktracer.native
  .settings(scalaVersion := "2.11.12")
  .settings(scalacOptions -= "-Xfatal-warnings") // Issue 3112
  .settings(skip in Test := true)
  .settings(skip in doc := true)

lazy val testRunner = crossProject(JVMPlatform, JSPlatform)
  .in(file("test-sbt"))
  .settings(stdSettings("zio-test-sbt"))
  .settings(crossProjectSettings)
  .settings(mainClass in (Test, run) := Some("zio.test.sbt.TestMain"))
  .jsSettings(libraryDependencies ++= Seq("org.scala-js" %% "scalajs-test-interface" % "1.1.1"))
  .jvmSettings(libraryDependencies ++= Seq("org.scala-sbt" % "test-interface" % "1.0"))
  .dependsOn(core)
  .dependsOn(test)

lazy val testJunitRunner = crossProject(JVMPlatform)
  .in(file("test-junit"))
  .settings(stdSettings("zio-test-junit"))
  .settings(libraryDependencies ++= Seq("junit" % "junit" % "4.13"))
  .dependsOn(test)

lazy val testJunitRunnerJVM = testJunitRunner.jvm.settings(dottySettings)

lazy val testRunnerJVM = testRunner.jvm.settings(dottySettings)
lazy val testRunnerJS  = testRunner.js.settings(testJsSettings)

/**
 * Examples sub-project that is not included in the root project.
 * To run tests :
 * `sbt "examplesJVM/test"`
 */
lazy val examples = crossProject(JVMPlatform, JSPlatform)
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(crossProjectSettings)
  .settings(macroExpansionSettings)
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .dependsOn(macros, testRunner)

lazy val examplesJS = examples.js.settings(testJsSettings)
lazy val examplesJVM = examples.jvm
  .settings(dottySettings)
  .dependsOn(testJunitRunnerJVM)

lazy val benchmarks = project.module
  .dependsOn(coreJVM, streamsJVM, testJVM)
  .enablePlugins(JmhPlugin)
  .settings(replSettings)
  .settings(
    // skip 2.11 benchmarks because akka stop supporting scala 2.11 in 2.6.x
    crossScalaVersions -= "2.11.12",
    //
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "co.fs2"                    %% "fs2-core"      % "2.4.2",
        "com.google.code.findbugs"  % "jsr305"         % "3.0.2",
        "com.twitter"               %% "util-core"     % "20.6.0",
        "com.typesafe.akka"         %% "akka-stream"   % "2.6.7",
        "io.monix"                  %% "monix"         % "3.2.2",
        "io.projectreactor"         % "reactor-core"   % "3.3.7.RELEASE",
        "io.reactivex.rxjava2"      % "rxjava"         % "2.2.19",
        "org.ow2.asm"               % "asm"            % "8.0.1",
        "org.scala-lang"            % "scala-compiler" % scalaVersion.value % Provided,
        "org.scala-lang"            % "scala-reflect"  % scalaVersion.value,
        "org.typelevel"             %% "cats-effect"   % "2.1.4",
        "org.scalacheck"            %% "scalacheck"    % "1.14.3",
        "hedgehog"                  %% "hedgehog-core" % "0.1.0",
        "com.github.japgolly.nyaya" %% "nyaya-gen"     % "0.9.2"
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
    ),
    resolvers += Resolver.url("bintray-scala-hedgehog", url("https://dl.bintray.com/hedgehogqa/scala-hedgehog"))(
      Resolver.ivyStylePatterns
    )
  )

lazy val docs = project.module
  .in(file("zio-docs"))
  .settings(
    // skip 2.13 mdoc until mdoc is available for 2.13
    crossScalaVersions -= "2.13.1",
    //
    skip.in(publish) := true,
    moduleName := "zio-docs",
    unusedCompileDependenciesFilter -= moduleFilter("org.scalameta", "mdoc"),
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    scalacOptions ~= { _ filterNot (_ startsWith "-Ywarn") },
    scalacOptions ~= { _ filterNot (_ startsWith "-Xlint") },
    libraryDependencies ++= Seq(
      "com.github.ghik"     % "silencer-lib"                 % "1.4.4" % Provided cross CrossVersion.full,
      "commons-io"          % "commons-io"                   % "2.7" % "provided",
      "org.jsoup"           % "jsoup"                        % "1.13.1" % "provided",
      "org.reactivestreams" % "reactive-streams-examples"    % "1.0.3" % "provided",
      "dev.zio"             %% "zio-interop-cats"            % "2.0.0.0-RC13",
      "dev.zio"             %% "zio-interop-future"          % "2.12.8.0-RC6",
      "dev.zio"             %% "zio-interop-monix"           % "3.0.0.0-RC7",
      "dev.zio"             %% "zio-interop-scalaz7x"        % "7.2.27.0-RC9",
      "dev.zio"             %% "zio-interop-java"            % "1.1.0.0-RC6",
      "dev.zio"             %% "zio-interop-reactivestreams" % "1.0.3.5-RC12",
      "dev.zio"             %% "zio-interop-twitter"         % "19.7.0.0-RC2"
    )
  )
  .settings(macroExpansionSettings)
  .dependsOn(
    coreJVM,
    streamsJVM,
    testJVM,
    testMagnoliaJVM
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

scalafixDependencies in ThisBuild += "com.nequissimus" %% "sort-imports" % "0.5.0"
