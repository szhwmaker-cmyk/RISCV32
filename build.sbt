// RV32E SoC Project Build Configuration
// 可以使用 sbt 或 mill 构建，推荐使用 mill

name := "rv32e_soc"

version := "1.0.0"

scalaVersion := "2.13.12"

// Chisel 依赖
libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % "5.1.0",
  "edu.berkeley.cs" %% "chiseltest" % "5.0.2" % "test",
  "org.scalatest" %% "scalatest" % "3.2.16" % "test"
)

// Scala 编译选项
scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-Ymacro-annotations"
)

// Chisel 编译插件
addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "5.1.0" cross CrossVersion.full)
