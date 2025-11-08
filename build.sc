// RV32E Processor Project - Mill Build Configuration
// Chisel 6.5 with ChiselTest

import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.ScalaTest

/**
 * RV32E 5-Stage Pipeline Processor
 * - Chisel 6.5
 * - Wishbone B4 Bus
 * - Mill Build System
 */
object Versions {
  val scala = "2.13.12"
  val chisel = "6.5.0"
  val chiselTest = "6.0.0"
  val scalatest = "3.2.17"
}

object rv32e extends ScalaModule with ScalafmtModule {
  def scalaVersion = Versions.scala

  def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ymacro-annotations",
    "-P:chiselplugin:genBundleElements"
  )

  def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:${Versions.chisel}"
  )

  def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:${Versions.chisel}"
  )

  object test extends ScalaTests with ScalafmtModule {
    def ivyDeps = Agg(
      ivy"edu.berkeley.cs::chiseltest:${Versions.chiselTest}",
      ivy"org.scalatest::scalatest:${Versions.scalatest}"
    )

    def testFramework = "org.scalatest.tools.Framework"
  }
}
