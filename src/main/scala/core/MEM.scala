package rv32e.core

import chisel3._
import chisel3.util._
import rv32e.bus._

/**
 * Memory Access (MEM) Stage
 *
 * Responsibilities:
 * - Perform load/store operations via Wishbone bus
 * - Handle byte/half-word/word accesses
 * - Sign/zero extension for loads
 * - Pass data to WB stage
 */
class MEMIO extends Bundle {
  // Input from EX stage
  val ex_mem = Input(new EX_MEM_Reg)

  // Data memory interface (Wishbone master)
  val dmem = new WishboneMasterIO

  // Output to WB stage
  val mem_wb = Output(new MEM_WB_Reg)

  // Control inputs
  val stall = Input(Bool())
  val flush = Input(Bool())
}

class MEM extends Module {
  val io = IO(new MEMIO)

  // ========== Memory Access State Machine ==========
  val s_idle :: s_read :: s_write :: s_wait_ack :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val mem_data_buffer = RegInit(0.U(32.W))
  val mem_done = RegInit(false.B)

  // ========== Wishbone Timeout Protection (FIXED: Problem #6) ==========
  val timeout_counter = RegInit(0.U(8.W))
  val timeout_limit = 255.U
  val timeout = timeout_counter === timeout_limit

  // ========== Memory Alignment Check (FIXED: Problem #11) ==========
  // Check if memory access is properly aligned
  val mem_addr = io.ex_mem.alu_result
  val addr_aligned = MuxLookup(io.ex_mem.mem_size, true.B)(Seq(
    0.U -> true.B,                      // Byte - always aligned
    1.U -> (mem_addr(0) === 0.U),      // Halfword - 2-byte aligned
    2.U -> (mem_addr(1, 0) === 0.U)    // Word - 4-byte aligned
  ))

  // Assert on misaligned access
  when(io.ex_mem.valid && (io.ex_mem.mem_read || io.ex_mem.mem_write)) {
    assert(addr_aligned,
           cf"Misaligned memory access: addr=0x${Hexadecimal(mem_addr)}, size=${io.ex_mem.mem_size}")
  }

  // ========== Wishbone Bus Control ==========
  io.dmem.adr := io.ex_mem.alu_result
  io.dmem.dat_o := Wishbone.alignWriteData(
    io.ex_mem.rs2_data,
    io.ex_mem.alu_result,
    io.ex_mem.mem_size
  )
  io.dmem.we := false.B
  io.dmem.sel := Wishbone.genByteSelect(io.ex_mem.alu_result, io.ex_mem.mem_size)
  io.dmem.stb := false.B
  io.dmem.cyc := false.B

  // State machine
  switch(state) {
    is(s_idle) {
      when(io.ex_mem.valid && !io.stall) {
        when(io.ex_mem.mem_read) {
          state := s_read
        }.elsewhen(io.ex_mem.mem_write) {
          state := s_write
        }.otherwise {
          mem_done := true.B
        }
      }
    }

    is(s_read) {
      io.dmem.stb := true.B
      io.dmem.cyc := true.B
      io.dmem.we := false.B
      state := s_wait_ack
    }

    is(s_write) {
      io.dmem.stb := true.B
      io.dmem.cyc := true.B
      io.dmem.we := true.B
      state := s_wait_ack
    }

    is(s_wait_ack) {
      io.dmem.stb := true.B
      io.dmem.cyc := true.B

      // Increment timeout counter
      timeout_counter := timeout_counter + 1.U

      when(io.dmem.ack) {
        when(io.ex_mem.mem_read) {
          mem_data_buffer := io.dmem.dat_i
        }
        mem_done := true.B
        timeout_counter := 0.U
        state := s_idle
      }.elsewhen(timeout) {
        // FIXED: Timeout occurred - return zero for loads, ignore stores
        assert(false.B, cf"Wishbone DMEM timeout at addr=0x${Hexadecimal(io.ex_mem.alu_result)}")
        when(io.ex_mem.mem_read) {
          mem_data_buffer := 0.U  // Return zero on timeout
        }
        mem_done := true.B
        timeout_counter := 0.U
        state := s_idle
      }
    }
  }

  // Reset timeout counter when not waiting for ACK
  when(state =/= s_wait_ack) {
    timeout_counter := 0.U
  }

  // ========== Load Data Processing ==========
  val load_data = Wishbone.extractLoadData(
    mem_data_buffer,
    io.ex_mem.alu_result,
    io.ex_mem.mem_size,
    io.ex_mem.mem_unsigned
  )

  // ========== MEM/WB Pipeline Register ==========
  val mem_wb_reg = RegInit(PipelineRegs.invalidMEMWB())

  when(io.flush) {
    mem_wb_reg := PipelineRegs.invalidMEMWB()
    mem_done := false.B
  }.elsewhen(!io.stall && (mem_done || (!io.ex_mem.mem_read && !io.ex_mem.mem_write))) {
    mem_wb_reg.pc := io.ex_mem.pc
    mem_wb_reg.alu_result := io.ex_mem.alu_result
    mem_wb_reg.mem_data := load_data
    mem_wb_reg.rd_addr := io.ex_mem.rd_addr
    mem_wb_reg.reg_write := io.ex_mem.reg_write
    mem_wb_reg.wb_sel := io.ex_mem.wb_sel
    mem_wb_reg.valid := io.ex_mem.valid
    mem_done := false.B
  }

  io.mem_wb := mem_wb_reg
}

object MEM extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.MEM"))
}
