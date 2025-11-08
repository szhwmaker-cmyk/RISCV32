package core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/**
 * 冒险处理单元测试
 */
class HazardSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "HazardUnit"

  it should "detect EX/MEM forwarding" in {
    test(new HazardUnit) { dut =>
      // 设置 EX/MEM 阶段有写回到 x1
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_rd.poke(1.U)

      // ID/EX 阶段需要读取 x1
      dut.io.id_ex_rs1.poke(1.U)

      dut.clock.step()

      // 应该触发 EX/MEM 转发
      dut.io.forward_a.expect(1.U)
    }
  }

  it should "detect MEM/WB forwarding" in {
    test(new HazardUnit) { dut =>
      // 设置 MEM/WB 阶段有写回到 x2
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.mem_wb_rd.poke(2.U)

      // ID/EX 阶段需要读取 x2
      dut.io.id_ex_rs2.poke(2.U)

      dut.clock.step()

      // 应该触发 MEM/WB 转发
      dut.io.forward_b.expect(2.U)
    }
  }

  it should "prioritize EX/MEM forwarding over MEM/WB" in {
    test(new HazardUnit) { dut =>
      // 同时设置 EX/MEM 和 MEM/WB 阶段写回到 x1
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_rd.poke(1.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.mem_wb_rd.poke(1.U)

      // ID/EX 阶段需要读取 x1
      dut.io.id_ex_rs1.poke(1.U)

      dut.clock.step()

      // 应该优先使用 EX/MEM 转发（更新的数据）
      dut.io.forward_a.expect(1.U)
    }
  }

  it should "detect LOAD-USE hazard and stall pipeline" in {
    test(new HazardUnit) { dut =>
      // ID/EX 阶段是 LOAD 指令，目标寄存器是 x1
      dut.io.id_ex_mem_read.poke(true.B)
      dut.io.id_ex_rd.poke(1.U)

      // IF/ID 阶段的指令需要读取 x1
      dut.io.if_id_rs1.poke(1.U)

      dut.clock.step()

      // 应该触发流水线暂停
      dut.io.stall_if.expect(true.B)
      dut.io.stall_id.expect(true.B)
      dut.io.flush_ex.expect(true.B)
    }
  }

  it should "flush pipeline on branch taken" in {
    test(new HazardUnit) { dut =>
      // 分支跳转
      dut.io.branch_taken.poke(true.B)

      dut.clock.step()

      // 应该 flush IF 和 ID 阶段
      dut.io.flush_if.expect(true.B)
      dut.io.flush_id.expect(true.B)
    }
  }

  it should "not forward from x0" in {
    test(new HazardUnit) { dut =>
      // EX/MEM 写回到 x0
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_rd.poke(0.U)

      // ID/EX 读取 x0
      dut.io.id_ex_rs1.poke(0.U)

      dut.clock.step()

      // 不应该转发（x0 永远是 0）
      dut.io.forward_a.expect(0.U)
    }
  }
}
