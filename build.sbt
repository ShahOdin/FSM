name := "FSM"

version := "0.1"

scalaVersion := "2.13.5"

addCompilerPlugin(
  "org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full
)

// if your project uses both 2.10 and polymorphic lambdas
libraryDependencies ++= (scalaBinaryVersion.value match {
  case "2.10" =>
    compilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
    ) :: Nil
  case _ =>
    Nil
})

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.6.0",
  "org.typelevel" %% "cats-effect" % "3.1.0"
)
