// RV32E SoC Project Build Configuration
// 使用 Chisel 6.5 构建 RV32E 5级流水线处理器

name := "rv32e_soc"

version := "1.0.0"

scalaVersion := "2.13.14"

// Chisel 6.5 依赖
libraryDependencies ++= Seq(
  "org.chipsalliance" %% "chisel" % "6.5.0",
  "edu.berkeley.cs" %% "chiseltest" % "6.0.0" % "test",
  "org.scalatest" %% "scalatest" % "3.2.18" % "test"
)

// Scala 编译选项
scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-Xcheckinit",
  "-release:11"
)

// Chisel 6.5 插件
addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "6.5.0" cross CrossVersion.full)
