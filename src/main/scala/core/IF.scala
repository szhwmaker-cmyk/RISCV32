package rv32e.core

import chisel3._
import chisel3.util._
import rv32e.Config
import rv32e.bus._

/**
 * Instruction Fetch (IF) Stage
 *
 * Responsibilities:
 * - Maintain and update Program Counter (PC)
 * - Fetch instructions from memory via Wishbone bus
 * - Handle PC updates (sequential, branch, jump)
 * - Support pipeline stalls and flushes
 *
 * PC Update Logic:
 * - Reset: PC = PC_RESET (SPI Flash base)
 * - Stall: PC unchanged
 * - Branch/Jump taken: PC = branch_target
 * - Normal: PC = PC + 4
 */
class IFIO extends Bundle {
  // Instruction memory interface (Wishbone master)
  val imem = new WishboneMasterIO

  // Output to ID stage
  val if_id = Output(new IF_ID_Reg)

  // Control inputs
  val stall        = Input(Bool())   // Stall signal (from hazard unit)
  val flush        = Input(Bool())   // Flush signal (from branch misprediction)
  val branch_taken = Input(Bool())   // Branch/Jump taken
  val branch_target = Input(UInt(32.W)) // Branch/Jump target address
}

class IF extends Module {
  val io = IO(new IFIO)

  // Program Counter Register
  val pc_reg = RegInit(Config.PC_RESET.U(32.W))

  // State machine for instruction fetch
  val s_idle :: s_fetch :: s_wait_ack :: Nil = Enum(3)
  val state = RegInit(s_idle)

  // Fetched instruction buffer
  val inst_buffer = RegInit(0.U(32.W))
  val inst_valid = RegInit(false.B)

  // ========== Wishbone Timeout Protection (FIXED: Problem #6) ==========
  // Prevent infinite stalls if slave doesn't respond
  val timeout_counter = RegInit(0.U(8.W))
  val timeout_limit = 255.U  // 255 cycles timeout
  val timeout = timeout_counter === timeout_limit

  // ========== PC Update Logic ==========
  val pc_next = WireDefault(pc_reg)

  when(!io.stall) {
    when(io.branch_taken) {
      // Branch or jump taken
      pc_next := io.branch_target
    }.elsewhen(inst_valid) {
      // Normal sequential execution (PC + 4)
      pc_next := pc_reg + 4.U
    }
  }

  // ========== PC Validation (FIXED: Problem #5) ==========
  // Check for PC overflow and misalignment
  assert(pc_next(1, 0) === 0.U, "PC misalignment detected: PC must be 4-byte aligned")
  assert(pc_next < Config.MAX_PC.U, cf"PC overflow detected: PC = 0x${Hexadecimal(pc_next)} >= MAX_PC")

  // Check branch target validity when branching
  when(io.branch_taken) {
    assert(io.branch_target(1, 0) === 0.U, "Branch target misalignment: must be 4-byte aligned")
    assert(io.branch_target < Config.MAX_PC.U,
           cf"Branch target overflow: target = 0x${Hexadecimal(io.branch_target)} >= MAX_PC")
  }

  pc_reg := pc_next

  // ========== Instruction Memory Access (Wishbone) ==========
  // Default Wishbone signals
  io.imem.adr := pc_reg
  io.imem.dat_o := 0.U
  io.imem.we := false.B
  io.imem.sel := "b1111".U  // Always fetch full word
  io.imem.stb := false.B
  io.imem.cyc := false.B

  // Fetch state machine
  switch(state) {
    is(s_idle) {
      when(!io.stall) {
        state := s_fetch
      }
    }

    is(s_fetch) {
      // Initiate fetch
      io.imem.stb := true.B
      io.imem.cyc := true.B
      state := s_wait_ack
    }

    is(s_wait_ack) {
      io.imem.stb := true.B
      io.imem.cyc := true.B

      // Increment timeout counter
      timeout_counter := timeout_counter + 1.U

      when(io.imem.ack) {
        // Instruction received
        inst_buffer := io.imem.dat_i
        inst_valid := true.B
        timeout_counter := 0.U
        state := s_idle

        // Immediately start next fetch if not stalling
        when(!io.stall && !io.branch_taken) {
          state := s_fetch
        }
      }.elsewhen(timeout) {
        // FIXED: Timeout occurred - insert NOP and continue
        assert(false.B, cf"Wishbone IMEM timeout at PC=0x${Hexadecimal(pc_reg)}")
        inst_buffer := 0x00000013.U  // NOP (ADDI x0, x0, 0)
        inst_valid := true.B
        timeout_counter := 0.U
        state := s_idle
      }
    }
  }

  // Reset timeout counter when not waiting for ACK
  when(state =/= s_wait_ack) {
    timeout_counter := 0.U
  }

  // ========== IF/ID Pipeline Register ==========
  val if_id_reg = RegInit(PipelineRegs.invalidIFID())

  when(io.flush) {
    // Flush the pipeline (insert bubble)
    if_id_reg := PipelineRegs.invalidIFID()
    inst_valid := false.B
  }.elsewhen(!io.stall && inst_valid) {
    // Normal operation: pass instruction to ID stage
    if_id_reg.pc := pc_reg
    if_id_reg.inst := inst_buffer
    if_id_reg.valid := true.B
    inst_valid := false.B  // Consumed
  }.elsewhen(io.stall) {
    // Keep current instruction in pipeline register
    if_id_reg := if_id_reg
  }

  io.if_id := if_id_reg
}

object IF extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.IF"))
}
