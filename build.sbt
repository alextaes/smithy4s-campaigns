import Dependencies.{Basic, SmithyLibs}

import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val autoImportSettingsCommon = Seq(
  "java.lang",
  "scala",
  "scala.Predef",
  "scala.util.chaining",
  "scala.concurrent",
  "scala.concurrent.duration",
  "scala.jdk.CollectionConverters",
  "scala.jdk.FunctionConverters",
  "cats.implicits",
  "cats",
  "cats.effect",
  "cats.effect.std",
)

lazy val autoImportSettingsFs2 =
  autoImportSettingsCommon ++ Seq(
    "fs2",
    "fs2.concurrent",
    "org.http4s",
  )

lazy val commonSettings = Seq(
  update / evictionWarningOptions := EvictionWarningOptions.empty,
  scalaVersion := "3.3.0",
  organization := "org",
  organizationName := "Demos",
  ThisBuild / evictionErrorLevel := Level.Info,
  dependencyOverrides ++= Seq(
  ),
  ThisBuild / resolvers += "Akka library repository".at("https://repo.akka.io/maven"),
  ThisBuild / resolvers += "Confluent Maven Repository".at("https://packages.confluent.io/maven/"),
  scalacOptions ++=
    Seq(
      "-explain",
      "-Ysafe-init",
      "-deprecation",
      "-feature",
      "-Yretain-trees",
      "-Xmax-inlines",
      "50"
))

lazy val root = (project in file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(commonSettings)
  .settings(
    name := "smithy4s-campaigns",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "com.disneystreaming.smithy"    % "smithytranslate-traits" % "0.3.14",
      "org.http4s" %% "http4s-ember-server" % "0.23.25"
    ),
    scalacOptions += autoImportSettingsFs2.mkString(start = "-Yimports:", sep = ",", end = ""),
    Compile / run / fork := true,
    Test / parallelExecution := false,
    Compile / run / connectInput := true,
    libraryDependencies ++= Basic.httpServiceDependencies,
    //libraryDependencies ++= SmithyLibs.interfaceLibsDependencies
  )
