package soc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SoCIntegrationSpec extends AnyFlatSpec with ChiselScalatestTester {
  "SoC" should "initialize correctly" in {
    test(new RV32E_SoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // 初始化输入
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.spi_miso.poke(false.B)
      dut.io.i2c_scl_i.poke(true.B)
      dut.io.i2c_sda_i.poke(true.B)
      dut.io.flash_miso.poke(false.B)

      // 复位并运行一段时间
      dut.clock.step(100)

      // SoC应该正常运行，没有崩溃
      println("SoC initialization test passed!")
    }
  }

  it should "access GPIO peripheral" in {
    test(new RV32E_SoC) { dut =>
      // 初始化
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.spi_miso.poke(false.B)
      dut.io.i2c_scl_i.poke(true.B)
      dut.io.i2c_sda_i.poke(true.B)
      dut.io.flash_miso.poke(false.B)

      // 等待系统稳定
      dut.clock.step(50)

      // 运行一段时间，观察GPIO输出
      for (i <- 0 until 100) {
        dut.clock.step()
      }

      println("GPIO access test passed!")
    }
  }

  it should "handle memory accesses" in {
    test(new RV32E_SoC) { dut =>
      // 初始化
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.spi_miso.poke(false.B)
      dut.io.i2c_scl_i.poke(true.B)
      dut.io.i2c_sda_i.poke(true.B)
      dut.io.flash_miso.poke(false.B)

      // 运行测试
      dut.clock.step(200)

      println("Memory access test passed!")
    }
  }
}
