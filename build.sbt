name := "shopping-cart-service"

organization := "com.lightbend.akka.samples"
organizationHomepage := Some(url("https://akka.io"))
licenses := Seq(("CC0", url("https://creativecommons.org/publicdomain/zero/1.0")))

scalaVersion := "2.13.17"

Compile / scalacOptions ++= Seq(
  "-target:11",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")

Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false

run / fork := true
// pass along config selection to forked jvm
run / javaOptions ++= sys.props
  .get("config.resource")
  .fold(Seq.empty[String])(res => Seq(s"-Dconfig.resource=$res"))

Global / cancelable := false // ctrl-c

val AkkaVersion = "2.10.12"
val AkkaHttpVersion = "10.7.3"
val AkkaManagementVersion = "1.6.4"
val AkkaPersistenceR2dbcVersion = "1.3.9"
val AkkaProjectionVersion =
  sys.props.getOrElse("akka-projection.version", "1.6.16")
val AkkaDiagnosticsVersion = "2.2.2"

enablePlugins(AkkaGrpcPlugin, JavaAppPackaging, DockerPlugin, Cinnamon)

dockerBaseImage := "docker.io/library/eclipse-temurin:21.0.1_12-jre-jammy"
dockerUsername := sys.props.get("docker.username")
dockerRepository := sys.props.get("docker.registry")
dockerUpdateLatest := true
// Add the Cinnamon Agent for run and test
run / cinnamon := true
test / cinnamon := true
// Set the Cinnamon Agent log level
cinnamonLogLevel := "INFO"

ThisBuild / dynverSeparator := "-"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

libraryDependencies ++= Seq(
  // 1. Basic dependencies for a clustered application
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  // Akka Management powers Health Checks, Akka Cluster Bootstrapping, and Akka Diagnostics
  "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
  "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion,
  "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
  "com.lightbend.akka" %% "akka-diagnostics" % AkkaDiagnosticsVersion,
  // Common dependencies for logging and testing
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "org.scalatest" %% "scalatest" % "3.1.2" % Test,
  // 2. Using Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-r2dbc" % AkkaPersistenceR2dbcVersion,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  // 3. Querying or projecting data from Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-r2dbc" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-grpc" % AkkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-testkit" % AkkaProjectionVersion % Test,
  // Use Coda Hale Metrics
  Cinnamon.library.cinnamonCHMetrics,
  // Use Akka instrumentation
  Cinnamon.library.cinnamonAkka,
  Cinnamon.library.cinnamonAkkaTyped,
  Cinnamon.library.cinnamonAkkaPersistence,
  Cinnamon.library.cinnamonAkkaStream,
  Cinnamon.library.cinnamonAkkaProjection,
  // Use Akka HTTP instrumentation
  Cinnamon.library.cinnamonAkkaHttp,
  // Use Akka gRPC instrumentation
  Cinnamon.library.cinnamonAkkaGrpc,
  // Use Akka Cluster instrumentation
  Cinnamon.library.cinnamonAkkaCluster,
  // OpenTelemetry
  Cinnamon.library.cinnamonOpenTelemetry)
