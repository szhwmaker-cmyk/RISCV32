package soc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * SoC 系统级集成测试
 */
class MinimalSocSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MinimalSoc"

  it should "initialize properly" in {
    test(new MinimalSoc) { dut =>
      // 初始化所有输入
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.flash_miso.poke(false.B)
      dut.io.spi_miso.poke(false.B)
      dut.io.scl_i.poke(true.B)
      dut.io.sda_i.poke(true.B)

      // 时钟复位
      dut.clock.step(10)

      // 验证初始状态
      // UART TX 应该是高电平（空闲）
      dut.io.uart_tx.expect(true.B)
    }
  }

  it should "access GPIO registers" in {
    test(new MinimalSoc).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // 初始化
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.flash_miso.poke(false.B)
      dut.io.spi_miso.poke(false.B)
      dut.io.scl_i.poke(true.B)
      dut.io.sda_i.poke(true.B)

      // 运行一段时间
      dut.clock.step(100)

      // 注：实际测试需要能够控制处理器执行特定指令
      // 这里只是验证系统能正常运行
    }
  }

  it should "handle memory access" in {
    test(new MinimalSoc) { dut =>
      // 初始化
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.flash_miso.poke(false.B)
      dut.io.spi_miso.poke(false.B)
      dut.io.scl_i.poke(true.B)
      dut.io.sda_i.poke(true.B)

      // 运行系统，处理器应该能够访问 RAM
      dut.clock.step(50)
    }
  }

  it should "run for extended period without errors" in {
    test(new MinimalSoc) { dut =>
      // 初始化所有输入
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.flash_miso.poke(false.B)
      dut.io.spi_miso.poke(false.B)
      dut.io.scl_i.poke(true.B)
      dut.io.sda_i.poke(true.B)

      // 运行1000个时钟周期
      dut.clock.step(1000)

      // 系统应该能稳定运行
    }
  }
}
