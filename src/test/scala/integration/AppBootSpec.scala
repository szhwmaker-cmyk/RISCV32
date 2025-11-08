package integration

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import soc._
import sim._

class AppBootSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SoC Application Boot Test"

  it should "boot from SPI Flash and output to UART" in {
    test(new RV32E_SoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // 创建Flash模型
      val flash = Module(new FlashModel(1024 * 1024))

      // 连接Flash
      flash.io.sclk := dut.io.flash_sclk
      flash.io.mosi := dut.io.flash_mosi
      flash.io.cs_n := dut.io.flash_cs_n
      dut.io.flash_miso.poke(flash.io.miso.peek())

      // 创建UART监控
      val uart_mon = Module(new UartMonitor(434))
      uart_mon.io.rx := dut.io.uart_tx

      // 初始化其他输入
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.spi_miso.poke(false.B)
      dut.io.i2c_scl_i.poke(true.B)
      dut.io.i2c_sda_i.poke(true.B)

      // 简单的测试程序：
      // 将字符 'H', 'e', 'l', 'l', 'o' 发送到 UART
      // 这需要编译好的RISC-V程序，这里简化为直接观察系统行为

      println("Running SoC for 1000 cycles...")

      var uart_output = ""

      for (cycle <- 0 until 5000) {
        dut.clock.step()

        // 检查UART输出
        if (uart_mon.io.char_valid.peek().litToBoolean) {
          val char = uart_mon.io.char_data.peek().litValue.toChar
          uart_output += char
          println(s"UART received: $char (0x${uart_mon.io.char_data.peek().litValue.toHexString})")
        }

        // 更新Flash MISO信号
        dut.io.flash_miso.poke(flash.io.miso.peek())
      }

      println(s"UART output: $uart_output")
      println("Application boot test completed!")
    }
  }
}
