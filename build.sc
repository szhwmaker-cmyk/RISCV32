import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.ScalaTest

/**
 * RV32E SoC Mill 构建配置
 * 使用 Chisel 6.5 和 Scala 2.13
 */
object rv32e_soc extends ScalaModule with ScalafmtModule {

  // Scala 版本
  def scalaVersion = "2.13.14"

  // 编译选项
  def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-release:11"
  )

  // Chisel 6.5 依赖
  def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.5.0",
  )

  // Chisel 6.5 编译插件
  def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.5.0",
  )

  // 测试模块
  object test extends ScalaTests with TestModule.ScalaTest {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.18",
      ivy"edu.berkeley.cs::chiseltest:6.0.0"
    )
  }

  // 生成 Verilog 的辅助任务
  def verilog() = T.command {
    val classpath = runClasspath().map(_.path)
    os.proc(
      "java",
      "-cp", classpath.mkString(":"),
      "circt.stage.ChiselMain",
      "--module", "soc.WishboneSoc",
      "--target-dir", "generated"
    ).call()
  }
}
