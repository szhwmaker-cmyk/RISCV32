package rv32e.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for Hazard Detection and Forwarding Unit
 */
class HazardSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Hazard"

  // ========== No Hazard Tests ==========

  it should "not forward when there are no dependencies" in {
    test(new Hazard) { dut =>
      // Setup: Different registers, no dependencies
      dut.io.id_ex_rs1.poke(1.U)
      dut.io.id_ex_rs2.poke(2.U)
      dut.io.ex_mem_rd.poke(3.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.mem_wb_rd.poke(4.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should not forward
      dut.io.fwd_rs1_sel.expect(ForwardSel.NO_FWD)
      dut.io.fwd_rs2_sel.expect(ForwardSel.NO_FWD)

      // Should not stall
      dut.io.stall_if.expect(false.B)
      dut.io.stall_id.expect(false.B)

      // Should not flush
      dut.io.flush_if.expect(false.B)
      dut.io.flush_id.expect(false.B)
      dut.io.flush_ex.expect(false.B)
    }
  }

  it should "not forward when destination is x0" in {
    test(new Hazard) { dut =>
      // Setup: EX/MEM writing to x0, ID/EX reading x0
      dut.io.id_ex_rs1.poke(0.U)
      dut.io.id_ex_rs2.poke(0.U)
      dut.io.ex_mem_rd.poke(0.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should not forward (x0 is hardwired to 0)
      dut.io.fwd_rs1_sel.expect(ForwardSel.NO_FWD)
      dut.io.fwd_rs2_sel.expect(ForwardSel.NO_FWD)
    }
  }

  // ========== EX Forwarding Tests (EX/MEM → EX) ==========

  it should "forward rs1 from EX/MEM stage" in {
    test(new Hazard) { dut =>
      // Setup: EX/MEM writes to x5, ID/EX reads from x5
      dut.io.id_ex_rs1.poke(5.U)
      dut.io.id_ex_rs2.poke(2.U)
      dut.io.ex_mem_rd.poke(5.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(6.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward rs1 from EX/MEM
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_EX)
      dut.io.fwd_rs2_sel.expect(ForwardSel.NO_FWD)
    }
  }

  it should "forward rs2 from EX/MEM stage" in {
    test(new Hazard) { dut =>
      // Setup: EX/MEM writes to x7, ID/EX reads from x7 in rs2
      dut.io.id_ex_rs1.poke(2.U)
      dut.io.id_ex_rs2.poke(7.U)
      dut.io.ex_mem_rd.poke(7.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(6.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward rs2 from EX/MEM
      dut.io.fwd_rs1_sel.expect(ForwardSel.NO_FWD)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_EX)
    }
  }

  it should "forward both rs1 and rs2 from EX/MEM stage" in {
    test(new Hazard) { dut =>
      // Setup: EX/MEM writes to x3, ID/EX reads x3 in both rs1 and rs2
      dut.io.id_ex_rs1.poke(3.U)
      dut.io.id_ex_rs2.poke(3.U)
      dut.io.ex_mem_rd.poke(3.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward both from EX/MEM
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_EX)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_EX)
    }
  }

  it should "not forward from EX/MEM when reg_write is false" in {
    test(new Hazard) { dut =>
      // Setup: EX/MEM has matching rd but reg_write is disabled
      dut.io.id_ex_rs1.poke(5.U)
      dut.io.id_ex_rs2.poke(5.U)
      dut.io.ex_mem_rd.poke(5.U)
      dut.io.ex_mem_reg_write.poke(false.B)  // No write!
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(6.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should not forward
      dut.io.fwd_rs1_sel.expect(ForwardSel.NO_FWD)
      dut.io.fwd_rs2_sel.expect(ForwardSel.NO_FWD)
    }
  }

  // ========== MEM Forwarding Tests (MEM/WB → EX) ==========

  it should "forward rs1 from MEM/WB stage" in {
    test(new Hazard) { dut =>
      // Setup: MEM/WB writes to x4, ID/EX reads from x4
      // No EX hazard
      dut.io.id_ex_rs1.poke(4.U)
      dut.io.id_ex_rs2.poke(2.U)
      dut.io.ex_mem_rd.poke(6.U)  // Different register
      dut.io.ex_mem_reg_write.poke(false.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(4.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward rs1 from MEM/WB
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_MEM)
      dut.io.fwd_rs2_sel.expect(ForwardSel.NO_FWD)
    }
  }

  it should "forward rs2 from MEM/WB stage" in {
    test(new Hazard) { dut =>
      // Setup: MEM/WB writes to x8, ID/EX reads from x8 in rs2
      dut.io.id_ex_rs1.poke(2.U)
      dut.io.id_ex_rs2.poke(8.U)
      dut.io.ex_mem_rd.poke(6.U)  // Different register
      dut.io.ex_mem_reg_write.poke(false.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(8.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward rs2 from MEM/WB
      dut.io.fwd_rs1_sel.expect(ForwardSel.NO_FWD)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_MEM)
    }
  }

  it should "forward both rs1 and rs2 from MEM/WB stage" in {
    test(new Hazard) { dut =>
      // Setup: MEM/WB writes to x9, ID/EX reads x9 in both sources
      dut.io.id_ex_rs1.poke(9.U)
      dut.io.id_ex_rs2.poke(9.U)
      dut.io.ex_mem_rd.poke(0.U)
      dut.io.ex_mem_reg_write.poke(false.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(9.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward both from MEM/WB
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_MEM)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_MEM)
    }
  }

  // ========== Forwarding Priority Tests ==========

  it should "prioritize EX forwarding over MEM forwarding for rs1" in {
    test(new Hazard) { dut =>
      // Setup: Both EX/MEM and MEM/WB write to same register x5
      // EX should have priority
      dut.io.id_ex_rs1.poke(5.U)
      dut.io.id_ex_rs2.poke(1.U)
      dut.io.ex_mem_rd.poke(5.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(5.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward from EX/MEM (higher priority)
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_EX)
      dut.io.fwd_rs2_sel.expect(ForwardSel.NO_FWD)
    }
  }

  it should "prioritize EX forwarding over MEM forwarding for rs2" in {
    test(new Hazard) { dut =>
      // Setup: Both stages write to x7, ID/EX reads x7 in rs2
      dut.io.id_ex_rs1.poke(1.U)
      dut.io.id_ex_rs2.poke(7.U)
      dut.io.ex_mem_rd.poke(7.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(7.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward from EX/MEM (higher priority)
      dut.io.fwd_rs1_sel.expect(ForwardSel.NO_FWD)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_EX)
    }
  }

  it should "handle mixed forwarding: rs1 from EX, rs2 from MEM" in {
    test(new Hazard) { dut =>
      // Setup: EX/MEM writes to x3, MEM/WB writes to x4
      // ID/EX reads x3 in rs1 and x4 in rs2
      dut.io.id_ex_rs1.poke(3.U)
      dut.io.id_ex_rs2.poke(4.U)
      dut.io.ex_mem_rd.poke(3.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(4.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should forward rs1 from EX, rs2 from MEM
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_EX)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_MEM)
    }
  }

  // ========== LOAD-USE Hazard Tests ==========

  it should "stall on LOAD-USE hazard for rs1" in {
    test(new Hazard) { dut =>
      // Setup: ID/EX is executing LOAD to x5, EX/MEM will have x5
      // Next instruction needs x5 in rs1
      dut.io.id_ex_rs1.poke(5.U)
      dut.io.id_ex_rs2.poke(2.U)
      dut.io.id_ex_mem_read.poke(true.B)  // LOAD in ID/EX
      dut.io.ex_mem_rd.poke(5.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should stall IF and ID stages
      dut.io.stall_if.expect(true.B)
      dut.io.stall_id.expect(true.B)
      dut.io.stall_ex.expect(false.B)
      dut.io.stall_mem.expect(false.B)

      // Should flush EX to insert bubble
      dut.io.flush_ex.expect(true.B)
    }
  }

  it should "stall on LOAD-USE hazard for rs2" in {
    test(new Hazard) { dut =>
      // Setup: ID/EX is executing LOAD to x6, next instruction needs x6 in rs2
      dut.io.id_ex_rs1.poke(2.U)
      dut.io.id_ex_rs2.poke(6.U)
      dut.io.id_ex_mem_read.poke(true.B)  // LOAD in ID/EX
      dut.io.ex_mem_rd.poke(6.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should stall
      dut.io.stall_if.expect(true.B)
      dut.io.stall_id.expect(true.B)
      dut.io.flush_ex.expect(true.B)
    }
  }

  it should "not stall when LOAD destination is x0" in {
    test(new Hazard) { dut =>
      // Setup: LOAD to x0 (should not cause stall)
      dut.io.id_ex_rs1.poke(0.U)
      dut.io.id_ex_rs2.poke(0.U)
      dut.io.id_ex_mem_read.poke(true.B)
      dut.io.ex_mem_rd.poke(0.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should not stall (x0 is special)
      dut.io.stall_if.expect(false.B)
      dut.io.stall_id.expect(false.B)
      dut.io.flush_ex.expect(false.B)
    }
  }

  it should "not stall when LOAD result is not immediately used" in {
    test(new Hazard) { dut =>
      // Setup: LOAD to x7, but next instruction uses x8
      dut.io.id_ex_rs1.poke(8.U)
      dut.io.id_ex_rs2.poke(9.U)
      dut.io.id_ex_mem_read.poke(true.B)
      dut.io.ex_mem_rd.poke(7.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should not stall (no dependency)
      dut.io.stall_if.expect(false.B)
      dut.io.stall_id.expect(false.B)
      dut.io.flush_ex.expect(false.B)
    }
  }

  // ========== Control Hazard Tests ==========

  it should "flush IF and ID on branch taken" in {
    test(new Hazard) { dut =>
      // Setup: Branch is taken
      dut.io.id_ex_rs1.poke(1.U)
      dut.io.id_ex_rs2.poke(2.U)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.ex_mem_rd.poke(3.U)
      dut.io.ex_mem_reg_write.poke(false.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(true.B)  // Branch taken!
      dut.clock.step(1)

      // Should flush IF and ID stages
      dut.io.flush_if.expect(true.B)
      dut.io.flush_id.expect(true.B)
      dut.io.flush_ex.expect(false.B)

      // Should not stall
      dut.io.stall_if.expect(false.B)
      dut.io.stall_id.expect(false.B)
    }
  }

  it should "not flush when branch is not taken" in {
    test(new Hazard) { dut =>
      // Setup: Branch is not taken
      dut.io.id_ex_rs1.poke(1.U)
      dut.io.id_ex_rs2.poke(2.U)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.ex_mem_rd.poke(3.U)
      dut.io.ex_mem_reg_write.poke(false.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(false.B)  // Not taken
      dut.clock.step(1)

      // Should not flush
      dut.io.flush_if.expect(false.B)
      dut.io.flush_id.expect(false.B)
      dut.io.flush_ex.expect(false.B)
    }
  }

  // ========== Combined Scenario Tests ==========

  it should "handle forwarding with branch taken" in {
    test(new Hazard) { dut =>
      // Setup: EX forwarding + branch taken
      dut.io.id_ex_rs1.poke(5.U)
      dut.io.id_ex_rs2.poke(5.U)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.ex_mem_rd.poke(5.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(0.U)
      dut.io.mem_wb_reg_write.poke(false.B)
      dut.io.branch_taken.poke(true.B)
      dut.clock.step(1)

      // Should forward from EX
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_EX)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_EX)

      // Should flush due to branch
      dut.io.flush_if.expect(true.B)
      dut.io.flush_id.expect(true.B)
    }
  }

  it should "handle all signals in complex scenario" in {
    test(new Hazard) { dut =>
      // Complex scenario:
      // - EX/MEM writes to x3 (forward to rs1)
      // - MEM/WB writes to x4 (forward to rs2)
      // - No LOAD-USE hazard
      // - No branch
      dut.io.id_ex_rs1.poke(3.U)
      dut.io.id_ex_rs2.poke(4.U)
      dut.io.id_ex_mem_read.poke(false.B)
      dut.io.ex_mem_rd.poke(3.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(4.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Verify all outputs
      dut.io.fwd_rs1_sel.expect(ForwardSel.FWD_EX)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_MEM)
      dut.io.stall_if.expect(false.B)
      dut.io.stall_id.expect(false.B)
      dut.io.stall_ex.expect(false.B)
      dut.io.stall_mem.expect(false.B)
      dut.io.flush_if.expect(false.B)
      dut.io.flush_id.expect(false.B)
      dut.io.flush_ex.expect(false.B)
    }
  }

  it should "handle LOAD-USE with forwarding from MEM stage" in {
    test(new Hazard) { dut =>
      // LOAD-USE for rs1, but rs2 can be forwarded from MEM
      dut.io.id_ex_rs1.poke(5.U)
      dut.io.id_ex_rs2.poke(6.U)
      dut.io.id_ex_mem_read.poke(true.B)  // LOAD
      dut.io.ex_mem_rd.poke(5.U)
      dut.io.ex_mem_reg_write.poke(true.B)
      dut.io.ex_mem_mem_read.poke(false.B)
      dut.io.mem_wb_rd.poke(6.U)
      dut.io.mem_wb_reg_write.poke(true.B)
      dut.io.branch_taken.poke(false.B)
      dut.clock.step(1)

      // Should stall due to LOAD-USE on rs1
      dut.io.stall_if.expect(true.B)
      dut.io.stall_id.expect(true.B)
      dut.io.flush_ex.expect(true.B)

      // rs2 should still forward from MEM (even though stalling)
      dut.io.fwd_rs2_sel.expect(ForwardSel.FWD_MEM)
    }
  }
}
