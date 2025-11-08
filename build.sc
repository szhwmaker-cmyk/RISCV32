// Mill build file for RV32E SoC
import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.ScalaTest

object rv32e_soc extends SbtModule with ScalafmtModule { m =>
  override def millSourcePath = os.pwd
  override def scalaVersion = "2.13.12"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ymacro-annotations"
  )

  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.0.0",
  )

  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.0.0",
  )

  object test extends SbtModuleTests with TestModule.ScalaTest {
    override def ivyDeps = m.ivyDeps() ++ Agg(
      ivy"edu.berkeley.cs::chiseltest:6.0.0"
    )
  }
}
