name := "cats-effect-playground"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies += "org.typelevel" %% "cats-effect" % "3.2.0" withSources() withJavadoc()
libraryDependencies += "org.typelevel" %% "munit-cats-effect-3" % "1.0.3" % Test
libraryDependencies += "org.typelevel" %% "cats-effect-testing-specs2" % "1.1.1" % Test

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps"
)