import Dependencies._

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.stoufexis"
ThisBuild / organizationName := "stoufexis"
ThisBuild / scalacOptions    := Seq(
  "-deprecation",                  // Emit warning and location for usages of deprecated APIs.
  "-encoding",
  "utf-8",                         // Specify character encoding used by source files.
  "-explaintypes",                 // Explain type errors in more detail.
  "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros", // Allow macro definition (besides implementation and application). Disabled, as this will significantly change in Scala 3
  "-language:higherKinds",         // Allow higher-kinded types
  "-language:implicitConversions", // for newtypes
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
  "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
  "-Xlint:unused",                 // TODO check if we still need -Wunused below
  "-Xlint:deprecation",            // Enable linted deprecations.
  "-Wdead-code",                   // Warn when dead code is identified.
  "-Wmacros:both",                 // Lints code before and after applying a macro
  "-Wnumeric-widen",               // Warn when numerics are widened.
  "-Xlint:implicit-recursion",     // Warn when an implicit resolves to an enclosing self-definition.
  "-Wunused:imports",              // Warn if an import selector is not referenced.
  "-Wunused:patvars",              // Warn if a variable bound in a pattern is unused.
  "-Wunused:privates",             // Warn if a private member is unused.
  "-Wunused:locals",               // Warn if a local definition is unused.
  "-Wunused:explicits",            // Warn if an explicit parameter is unused.
  "-Wunused:implicits",            // Warn if an implicit parameter is unused.
  "-Wunused:params",               // Enable -Wunused:explicits,implicits.
  "-Wunused:linted",
  "-Wvalue-discard",               // Warn when non-Unit expression results are unused.
  "-Ymacro-annotations"
)

lazy val kafka =
  Seq(
    "com.github.fd4s" %% "fs2-kafka"     % "3.5.1",
    "org.apache.kafka" % "kafka-clients" % "3.7.0"
  )

lazy val effect =
  Seq(
    "co.fs2"        %% "fs2-core"    % "3.10.2",
    "org.typelevel" %% "cats-core"   % "2.10.0",
    "org.typelevel" %% "cats-effect" % "3.5.4"
  )

lazy val log =
  Seq(
    "org.typelevel" %% "log4cats-core"  % "2.6.0",
    "org.typelevel" %% "log4cats-slf4j" % "2.6.0"
  )

lazy val newtype = Seq("io.estatico" %% "newtype" % "0.4.4")

lazy val fuuid = Seq("io.chrisdavenport" %% "fuuid" % "0.8.0-M1")

lazy val testdeps =
  Seq(
    "com.disneystreaming" %% "weaver-cats"       % "0.8.4",
    "com.disneystreaming" %% "weaver-scalacheck" % "0.8.4"
  ).map(_ % Test)

lazy val config =
  Seq(
    "com.github.pureconfig" %% "pureconfig"             % "0.17.6",
    "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.6"
  )

lazy val circe =
  Seq(
    "io.circe" %% "circe-core"    % "0.14.7",
    "io.circe" %% "circe-generic" % "0.14.7",
    "io.circe" %% "circe-parser"  % "0.14.7"
  )

lazy val kittens = Seq("org.typelevel" %% "kittens" % "3.3.0")

lazy val lib =
  (project in file("lib"))
    .settings(
      libraryDependencies ++=
        kafka ++
          effect ++
          log ++
          newtype ++
          fuuid ++
          config ++
          circe ++
          kittens ++
          testdeps
    )

lazy val root =
  (project in file("."))
    .aggregate(lib)
