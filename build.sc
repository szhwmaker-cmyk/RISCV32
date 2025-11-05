import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.ScalaTest

object rv32e_soc extends SbtModule with ScalafmtModule {
  def scalaVersion = "2.13.12"

  def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.0.0",
  )

  def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.0.0",
  )

  object test extends SbtModuleTests with TestModule.ScalaTest with ScalafmtModule {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.scalatest::scalatest:3.2.16",
      ivy"edu.berkeley.cs::chiseltest:6.0.0"
    )
  }
}
