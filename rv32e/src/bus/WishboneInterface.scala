package bus

import chisel3._
import chisel3.util._

/**
 * Wishbone B4 Bus Interface Definitions
 * Wishbone B4 总线接口定义
 *
 * 基于 Wishbone B4 规范:
 * - 32位地址总线
 * - 32位数据总线
 * - 支持单周期和流水线传输
 * - 支持字节选择
 * - 支持突发传输
 *
 * 参考: https://cdn.opencores.org/downloads/wbspec_b4.pdf
 */

/**
 * Wishbone Master Interface
 * Master端口（发起总线事务）
 */
class WishboneMaster extends Bundle {
  // Address and Data
  val adr_o = Output(UInt(32.W))      // Address output
  val dat_o = Output(UInt(32.W))      // Data output (for writes)
  val dat_i = Input(UInt(32.W))       // Data input (for reads)

  // Control signals
  val we_o = Output(Bool())           // Write enable
  val sel_o = Output(UInt(4.W))       // Byte select
  val stb_o = Output(Bool())          // Strobe (valid cycle)
  val cyc_o = Output(Bool())          // Cycle (bus cycle in progress)

  // Response signals
  val ack_i = Input(Bool())           // Acknowledge
  val err_i = Input(Bool())           // Error
  val rty_i = Input(Bool())           // Retry

  // Optional signals
  val lock_o = Output(Bool())         // Lock (for atomic operations)
  val cti_o = Output(UInt(3.W))       // Cycle Type Identifier
  val bte_o = Output(UInt(2.W))       // Burst Type Extension
}

/**
 * Wishbone Slave Interface
 * Slave端口（响应总线事务）
 */
class WishboneSlave extends Bundle {
  // Address and Data
  val adr_i = Input(UInt(32.W))       // Address input
  val dat_i = Input(UInt(32.W))       // Data input (for writes)
  val dat_o = Output(UInt(32.W))      // Data output (for reads)

  // Control signals
  val we_i = Input(Bool())            // Write enable
  val sel_i = Input(UInt(4.W))        // Byte select
  val stb_i = Input(Bool())           // Strobe
  val cyc_i = Input(Bool())           // Cycle

  // Response signals
  val ack_o = Output(Bool())          // Acknowledge
  val err_o = Output(Bool())          // Error
  val rty_o = Output(Bool())          // Retry

  // Optional signals
  val lock_i = Input(Bool())          // Lock
  val cti_i = Input(UInt(3.W))        // Cycle Type Identifier
  val bte_i = Input(UInt(2.W))        // Burst Type Extension
}

/**
 * Wishbone Cycle Type Identifier (CTI)
 */
object WB_CTI {
  val CLASSIC      = "b000".U(3.W)  // Classic cycle
  val CONST_BURST  = "b001".U(3.W)  // Constant address burst
  val INCR_BURST   = "b010".U(3.W)  // Incrementing burst
  val END_OF_BURST = "b111".U(3.W)  // End of burst
}

/**
 * Wishbone Burst Type Extension (BTE)
 */
object WB_BTE {
  val LINEAR  = "b00".U(2.W)  // Linear burst
  val WRAP_4  = "b01".U(2.W)  // 4-beat wrap burst
  val WRAP_8  = "b10".U(2.W)  // 8-beat wrap burst
  val WRAP_16 = "b11".U(2.W)  // 16-beat wrap burst
}

/**
 * Wishbone Master Adapter
 * 将简单的存储器接口转换为Wishbone Master接口
 */
class WishboneMasterAdapter extends Module {
  val io = IO(new Bundle {
    // Simple memory interface
    val mem_addr = Input(UInt(32.W))
    val mem_wdata = Input(UInt(32.W))
    val mem_rdata = Output(UInt(32.W))
    val mem_wen = Input(Bool())
    val mem_ren = Input(Bool())
    val mem_size = Input(UInt(2.W))   // 0: byte, 1: half, 2: word
    val mem_valid = Output(Bool())     // Transaction complete

    // Wishbone Master interface
    val wb = new WishboneMaster()
  })

  // State machine for bus transactions
  val sIdle :: sBusy :: Nil = Enum(2)
  val state = RegInit(sIdle)

  // Generate byte select based on size and address
  val byte_sel = Wire(UInt(4.W))
  val addr_offset = io.mem_addr(1, 0)

  byte_sel := MuxLookup(io.mem_size, "b1111".U)(Seq(
    0.U -> MuxLookup(addr_offset, "b0001".U)(Seq(  // Byte
      0.U -> "b0001".U,
      1.U -> "b0010".U,
      2.U -> "b0100".U,
      3.U -> "b1000".U
    )),
    1.U -> MuxLookup(addr_offset(1), "b0011".U)(Seq(  // Half word
      0.U -> "b0011".U,
      1.U -> "b1100".U
    )),
    2.U -> "b1111".U  // Word
  ))

  // Wishbone signals
  io.wb.adr_o := io.mem_addr
  io.wb.dat_o := io.mem_wdata
  io.wb.we_o := io.mem_wen
  io.wb.sel_o := byte_sel
  io.wb.lock_o := false.B
  io.wb.cti_o := WB_CTI.CLASSIC
  io.wb.bte_o := WB_BTE.LINEAR

  // State machine
  switch(state) {
    is(sIdle) {
      io.wb.cyc_o := false.B
      io.wb.stb_o := false.B

      when(io.mem_wen || io.mem_ren) {
        state := sBusy
      }
    }

    is(sBusy) {
      io.wb.cyc_o := true.B
      io.wb.stb_o := true.B

      when(io.wb.ack_i || io.wb.err_i) {
        state := sIdle
      }
    }
  }

  // Output
  io.mem_rdata := io.wb.dat_i
  io.mem_valid := io.wb.ack_i

  // Tie off unused response signals
  when(io.wb.err_i || io.wb.rty_i) {
    // Error handling can be added here
  }
}

/**
 * Simple Wishbone Slave (Memory)
 * 简单的Wishbone从设备（存储器）
 */
class WishboneMemorySlave(size: Int = 1024) extends Module {
  val io = IO(new Bundle {
    val wb = new WishboneSlave()
  })

  // Memory array (byte-addressable)
  val mem = Mem(size, UInt(8.W))

  // Acknowledge logic
  val ack_reg = RegInit(false.B)
  ack_reg := io.wb.cyc_i && io.wb.stb_i && !ack_reg

  io.wb.ack_o := ack_reg
  io.wb.err_o := false.B
  io.wb.rty_o := false.B

  // Address calculation
  val word_addr = io.wb.adr_i(31, 2)
  val base_addr = word_addr << 2.U

  // Read logic
  val read_data = Wire(UInt(32.W))
  read_data := Cat(
    mem(base_addr + 3.U),
    mem(base_addr + 2.U),
    mem(base_addr + 1.U),
    mem(base_addr)
  )

  io.wb.dat_o := read_data

  // Write logic
  when(io.wb.cyc_i && io.wb.stb_i && io.wb.we_i) {
    when(io.wb.sel_i(0)) { mem(base_addr) := io.wb.dat_i(7, 0) }
    when(io.wb.sel_i(1)) { mem(base_addr + 1.U) := io.wb.dat_i(15, 8) }
    when(io.wb.sel_i(2)) { mem(base_addr + 2.U) := io.wb.dat_i(23, 16) }
    when(io.wb.sel_i(3)) { mem(base_addr + 3.U) := io.wb.dat_i(31, 24) }
  }
}
