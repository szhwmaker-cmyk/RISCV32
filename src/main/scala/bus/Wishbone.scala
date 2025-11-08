package bus

import chisel3._
import chisel3.util._

// ============================================================================
// Wishbone B4 接口定义
// ============================================================================

// Master 输出信号
class WishboneMaster extends Bundle {
  val adr_o = Output(UInt(32.W))    // 地址
  val dat_o = Output(UInt(32.W))    // 写数据
  val we_o  = Output(Bool())        // 写使能 (1=写, 0=读)
  val sel_o = Output(UInt(4.W))     // 字节选择
  val stb_o = Output(Bool())        // Strobe (传输有效)
  val cyc_o = Output(Bool())        // Cycle (总线周期有效)
  val dat_i = Input(UInt(32.W))     // 读数据
  val ack_i = Input(Bool())         // 应答
}

// Slave 接口 (从Master角度看)
class WishboneSlave extends Bundle {
  val adr_i = Input(UInt(32.W))
  val dat_i = Input(UInt(32.W))
  val we_i  = Input(Bool())
  val sel_i = Input(UInt(4.W))
  val stb_i = Input(Bool())
  val cyc_i = Input(Bool())
  val dat_o = Output(UInt(32.W))
  val ack_o = Output(Bool())
}

// ============================================================================
// Wishbone Master Adapter
// 将处理器的 MemPortIO 转换为 Wishbone Master
// ============================================================================

class WishboneMasterAdapter extends Module {
  val io = IO(new Bundle {
    val mem = Flipped(new common.MemPortIO)  // 来自处理器
    val wb  = new WishboneMaster             // 输出到总线
  })

  // Wishbone 信号
  io.wb.adr_o := io.mem.addr
  io.wb.dat_o := io.mem.wdata
  io.wb.we_o  := io.mem.wen
  io.wb.sel_o := io.mem.mask
  io.wb.stb_o := io.mem.valid
  io.wb.cyc_o := io.mem.valid

  // 反馈到处理器
  io.mem.rdata := io.wb.dat_i
  io.mem.ready := io.wb.ack_i
}
