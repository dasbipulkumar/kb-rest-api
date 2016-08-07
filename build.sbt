name := """simple-rest-scala"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

lazy val root = project.in(file(".")).enablePlugins(PlayScala)

resolvers += Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns)

fork in run := true

libraryDependencies += "org.elasticsearch" % "elasticsearch" % "2.3.4"

