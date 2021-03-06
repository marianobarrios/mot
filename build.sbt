organization := "com.github.marianobarrios"
name := "mot"
version := "0.8.0"
description := "Message-Oriented Transport"
homepage := Some(url("https://github.com/marianobarrios/mot"))
licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))
	
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

scalaVersion := "2.11.6"
crossScalaVersions := Seq("2.10.5", "2.11.6")

scalacOptions := Seq(
	"-feature", 
	"-deprecation", 
	"-optimize",
	"-unchecked",
	"-language:postfixOps", 
	"-language:reflectiveCalls", 
	"-language:implicitConversions", 
	"-Ywarn-dead-code",
	"-Ywarn-inaccessible",
	"-Ywarn-nullary-unit",
	"-Ywarn-nullary-override")
	
libraryDependencies ++=
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" ::
  "com.github.marianobarrios" % "lbmq" % "0.2.0" ::
  "ch.qos.logback" % "logback-classic" % "1.1.3" % Test ::
  "io.netty" % "netty-common" % "4.0.26.Final" ::
  "org.scalatest" %% "scalatest" % "2.2.4" % "test" ::
  "org.hdrhistogram" % "HdrHistogram" % "2.1.4" % "test" ::
  Nil
  
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, major)) if major >= 11 =>
      libraryDependencies.value ++ Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3")
    case _ =>
      libraryDependencies.value
  }
}

// Do not include src/{main,test}/java in the configuration, to avoid having sbt-eclipse generate them empty
unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil
unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil
  
scalacOptions in (Compile, doc) ++= Seq("-doc-root-content", baseDirectory.value + "/root-doc.txt")
 
fork := true
connectInput := true
outputStrategy := Some(StdoutOutput)
javaOptions  ++= Seq("-Xmx6200m", "-Xms6200m", "-XX:NewSize=5000m")

autoAPIMappings := true
publishMavenStyle := true

pomExtra := (
  <scm>
    <url>git@github.com:marianobarrios/mot.git</url>
    <connection>scm:git:git@github.com:marianobarrios/mot.git</connection>
    <developerConnection>scm:git:git@github.com:marianobarrios/mot.git</developerConnection>
  </scm>
  <developers>
    <developer>
      <name>Mariano Barrios</name>
      <url>https://github.com/marianobarrios/</url>
    </developer>
  </developers>)
