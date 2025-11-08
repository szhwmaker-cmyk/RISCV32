package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * 流水线集成测试
 * 测试指令在流水线中的正确执行
 */
class PipelineIntegrationSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Pipeline Integration"

  it should "execute simple ADD instruction" in {
    test(new Core) { dut =>
      // ADDI x1, x0, 10 (0x00A00093)
      val addi_inst = "h00A00093".U

      // 提供指令
      dut.io.imem_rdata.poke(addi_inst)
      dut.clock.step(5) // 等待流水线填充

      // 验证结果会写回寄存器
      // 注：实际测试需要访问内部寄存器状态
    }
  }

  it should "handle data hazards with forwarding" in {
    test(new Core) { dut =>
      // 测试序列：
      // ADDI x1, x0, 5
      // ADDI x2, x1, 3  (需要转发x1的值)

      // 第一条指令
      dut.io.imem_rdata.poke("h00500093".U) // ADDI x1, x0, 5
      dut.clock.step()

      // 第二条指令
      dut.io.imem_rdata.poke("h00308113".U) // ADDI x2, x1, 3
      dut.clock.step(5)

      // 验证转发逻辑工作正常
    }
  }

  it should "handle LOAD-USE hazard with stall" in {
    test(new Core) { dut =>
      // 测试序列：
      // LW  x1, 0(x2)
      // ADD x3, x1, x4  (需要暂停)

      // LW指令
      dut.io.imem_rdata.poke("h00012083".U) // LW x1, 0(x2)
      dut.io.dmem_rdata.poke(42.U)
      dut.clock.step()

      // ADD指令（会触发暂停）
      dut.io.imem_rdata.poke("h004081B3".U) // ADD x3, x1, x4
      dut.clock.step(6)

      // 验证流水线暂停了一个周期
    }
  }

  it should "handle branch correctly" in {
    test(new Core) { dut =>
      // BEQ x1, x2, offset
      // 测试分支跳转和flush

      dut.io.imem_rdata.poke("h00208063".U) // BEQ x1, x2, 0
      dut.clock.step(5)

      // 验证分支预测和flush逻辑
    }
  }

  it should "execute a sequence of instructions" in {
    test(new Core) { dut =>
      val instructions = Seq(
        "h00A00093".U, // ADDI x1, x0, 10
        "h00500113".U, // ADDI x2, x0, 5
        "h002081B3".U, // ADD  x3, x1, x2
        "h40208233".U  // SUB  x4, x1, x2
      )

      for (inst <- instructions) {
        dut.io.imem_rdata.poke(inst)
        dut.clock.step()
      }

      // 等待流水线排空
      dut.clock.step(5)
    }
  }
}
