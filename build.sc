import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.ScalaTest

/**
 * RV32E SoC Mill 构建配置
 * 使用 Chisel 3.6 和 Scala 2.13
 */
object rv32e_soc extends ScalaModule with ScalafmtModule {

  // Scala 版本
  def scalaVersion = "2.13.12"

  // 编译选项
  def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ymacro-annotations"
  )

  // Ivy 依赖
  def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:5.1.0",
  )

  // 编译插件
  def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:5.1.0",
  )

  // 测试模块
  object test extends ScalaTests with TestModule.ScalaTest {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.16",
      ivy"edu.berkeley.cs::chiseltest:5.0.2"
    )
  }

  // 生成 Verilog 的辅助任务
  def verilog() = T.command {
    val classpath = runClasspath().map(_.path)
    os.proc(
      "java",
      "-cp", classpath.mkString(":"),
      "circt.stage.ChiselMain",
      "--module", "soc.MinimalSoc",
      "--target-dir", "generated"
    ).call()
  }
}
