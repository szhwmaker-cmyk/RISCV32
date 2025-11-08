package bus

import chisel3._
import chisel3.util._
import common.Config._

/**
 * Wishbone B4 Pipeline 总线接口定义
 */
class WishboneMaster extends Bundle {
  // Master → Slave 信号
  val adr_o = Output(UInt(ADDR_WIDTH.W))  // 地址线
  val dat_o = Output(UInt(BUS_WIDTH.W))   // 写数据
  val we_o  = Output(Bool())              // 写使能 (1=写, 0=读)
  val sel_o = Output(UInt(SEL_WIDTH.W))   // 字节选择 (4 bits for 32-bit bus)
  val stb_o = Output(Bool())              // Strobe 信号 (传输有效)
  val cyc_o = Output(Bool())              // Cycle 信号 (总线周期有效)

  // Slave → Master 信号
  val dat_i = Input(UInt(BUS_WIDTH.W))    // 读数据
  val ack_i = Input(Bool())               // 应答信号 (传输完成)
}

class WishboneSlave extends Bundle {
  // Master → Slave 信号
  val adr_i = Input(UInt(ADDR_WIDTH.W))
  val dat_i = Input(UInt(BUS_WIDTH.W))
  val we_i  = Input(Bool())
  val sel_i = Input(UInt(SEL_WIDTH.W))
  val stb_i = Input(Bool())
  val cyc_i = Input(Bool())

  // Slave → Master 信号
  val dat_o = Output(UInt(BUS_WIDTH.W))
  val ack_o = Output(Bool())
}

/**
 * Wishbone 主设备适配器
 * 将处理器的简单存储器接口转换为 Wishbone 总线
 */
class WishboneMasterAdapter extends Module {
  val io = IO(new Bundle {
    // 处理器存储器接口
    val mem_addr  = Input(UInt(XLEN.W))
    val mem_wdata = Input(UInt(XLEN.W))
    val mem_wen   = Input(Bool())
    val mem_ren   = Input(Bool())
    val mem_size  = Input(UInt(2.W))
    val mem_rdata = Output(UInt(XLEN.W))
    val mem_ready = Output(Bool())

    // Wishbone 主设备接口
    val wb = new WishboneMaster
  })

  // 总线请求
  val req = io.mem_wen || io.mem_ren

  // 地址
  io.wb.adr_o := io.mem_addr

  // 写数据
  io.wb.dat_o := io.mem_wdata

  // 写使能
  io.wb.we_o := io.mem_wen

  // 字节选择信号（根据 mem_size 和地址低位）
  val addr_offset = io.mem_addr(1, 0)
  io.wb.sel_o := MuxLookup(io.mem_size, "b1111".U)(Seq(
    MemSize.BYTE -> MuxLookup(addr_offset, "b0001".U)(Seq(
      0.U -> "b0001".U,
      1.U -> "b0010".U,
      2.U -> "b0100".U,
      3.U -> "b1000".U
    )),
    MemSize.HALF -> Mux(addr_offset(1), "b1100".U, "b0011".U),
    MemSize.WORD -> "b1111".U
  ))

  // Strobe 和 Cycle 信号
  io.wb.stb_o := req
  io.wb.cyc_o := req

  // 读数据
  io.mem_rdata := io.wb.dat_i

  // 准备信号（应答）
  io.mem_ready := io.wb.ack_i
}

/**
 * 内存访问大小定义（与 core/PipelineRegs.scala 一致）
 */
object MemSize {
  val BYTE = 0.U(2.W)
  val HALF = 1.U(2.W)
  val WORD = 2.U(2.W)
}
