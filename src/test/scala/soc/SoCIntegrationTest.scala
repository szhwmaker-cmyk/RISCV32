package soc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * SoC 集成测试
 *
 * 测试完整系统的基本功能
 */
class SoCIntegrationTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MinimalSoC"

  it should "execute a simple program" in {
    test(new MinimalSoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // 复位
      dut.reset.poke(true.B)
      dut.clock.step(10)
      dut.reset.poke(false.B)

      // 运行一些时钟周期
      dut.clock.step(100)

      // 基本的健全性检查 - 确保系统没有卡死
      // 在实际测试中，我们会加载程序并验证输出
    }
  }

  it should "handle UART transmission" in {
    test(new MinimalSoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(10)
      dut.reset.poke(false.B)

      // 这里需要加载一个测试程序来测试 UART
      // 运行足够长的时间来完成 UART 传输
      dut.clock.step(10000)

      // 检查 UART 输出（这需要监控 io.uart.tx）
    }
  }
}

/**
 * 指令级测试
 *
 * 测试各种 RISC-V 指令
 */
class InstructionTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Core Instruction Execution"

  // 辅助函数：创建 RISC-V 指令
  def makeRType(opcode: Int, rd: Int, funct3: Int, rs1: Int, rs2: Int, funct7: Int): Int = {
    (funct7 << 25) | (rs2 << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode
  }

  def makeIType(opcode: Int, rd: Int, funct3: Int, rs1: Int, imm: Int): Int = {
    (imm << 20) | (rs1 << 15) | (funct3 << 12) | (rd << 7) | opcode
  }

  it should "execute ADDI instruction" in {
    test(new core.Core).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // 这个测试需要更完整的设置
      // 包括初始化内存等
      dut.reset.poke(true.B)
      dut.clock.step(10)
      dut.reset.poke(false.B)

      // 运行几个周期
      dut.clock.step(50)
    }
  }
}
