package core

import chisel3._
import chisel3.util._
import common.Config._
import bus.WishboneMaster

/**
 * MEM (Memory Access) 访存阶段
 *
 * 功能：
 * 1. 处理 LOAD/STORE 指令
 * 2. 通过 Wishbone 总线访问数据存储器
 * 3. 处理字节、半字、字访问
 * 4. 符号扩展/零扩展
 */
class MEM extends Module {
  val io = IO(new Bundle {
    // 来自 EX 阶段
    val alu_result = Input(UInt(XLEN.W))
    val rs2_data = Input(UInt(XLEN.W)) // STORE 数据
    val rd = Input(UInt(REG_ADDR_WIDTH.W))
    val ctrl = Input(new ControlSignals)
    val valid = Input(Bool())

    // 数据存储器接口 (Wishbone Master)
    val dmem = new WishboneMaster

    // 输出到 WB 阶段
    val wb_write_data = Output(UInt(XLEN.W))
    val wb_rd = Output(UInt(REG_ADDR_WIDTH.W))
    val wb_reg_write = Output(Bool())
    val wb_valid = Output(Bool())
  })

  // Wishbone 总线地址和数据
  val mem_addr = io.alu_result

  // 字节选择信号生成
  val byte_offset = mem_addr(1, 0)
  val sel = MuxLookup(io.ctrl.mem_width, "b1111".U)(
    Seq(
      0.U -> MuxLookup(byte_offset, "b0001".U)( // byte
        Seq(
          0.U -> "b0001".U,
          1.U -> "b0010".U,
          2.U -> "b0100".U,
          3.U -> "b1000".U
        )
      ),
      1.U -> MuxLookup(byte_offset(1), "b0011".U)( // half
        Seq(
          0.U -> "b0011".U,
          1.U -> "b1100".U
        )
      ),
      2.U -> "b1111".U // word
    )
  )

  // STORE 数据对齐
  val store_data = MuxLookup(io.ctrl.mem_width, io.rs2_data)(
    Seq(
      0.U -> MuxLookup(byte_offset, io.rs2_data(7, 0))( // byte
        Seq(
          0.U -> Cat(Fill(24, 0.U), io.rs2_data(7, 0)),
          1.U -> Cat(Fill(16, 0.U), io.rs2_data(7, 0), Fill(8, 0.U)),
          2.U -> Cat(Fill(8, 0.U), io.rs2_data(7, 0), Fill(16, 0.U)),
          3.U -> Cat(io.rs2_data(7, 0), Fill(24, 0.U))
        )
      ),
      1.U -> MuxLookup(byte_offset(1), io.rs2_data(15, 0))( // half
        Seq(
          0.U -> Cat(Fill(16, 0.U), io.rs2_data(15, 0)),
          1.U -> Cat(io.rs2_data(15, 0), Fill(16, 0.U))
        )
      ),
      2.U -> io.rs2_data // word
    )
  )

  // Wishbone 总线控制
  io.dmem.adr := mem_addr
  io.dmem.dat_w := store_data
  io.dmem.we := io.ctrl.mem_write
  io.dmem.sel := sel
  io.dmem.cyc := (io.ctrl.mem_read || io.ctrl.mem_write) && io.valid
  io.dmem.stb := (io.ctrl.mem_read || io.ctrl.mem_write) && io.valid

  // LOAD 数据处理
  val load_data_raw = io.dmem.dat_r
  val load_data = MuxLookup(io.ctrl.mem_width, load_data_raw)(
    Seq(
      0.U -> MuxLookup(byte_offset, 0.U)( // byte
        Seq(
          0.U -> Cat(Fill(24, Mux(io.ctrl.mem_signed, load_data_raw(7), 0.U)), load_data_raw(7, 0)),
          1.U -> Cat(Fill(24, Mux(io.ctrl.mem_signed, load_data_raw(15), 0.U)), load_data_raw(15, 8)),
          2.U -> Cat(Fill(24, Mux(io.ctrl.mem_signed, load_data_raw(23), 0.U)), load_data_raw(23, 16)),
          3.U -> Cat(Fill(24, Mux(io.ctrl.mem_signed, load_data_raw(31), 0.U)), load_data_raw(31, 24))
        )
      ),
      1.U -> MuxLookup(byte_offset(1), 0.U)( // half
        Seq(
          0.U -> Cat(Fill(16, Mux(io.ctrl.mem_signed, load_data_raw(15), 0.U)), load_data_raw(15, 0)),
          1.U -> Cat(Fill(16, Mux(io.ctrl.mem_signed, load_data_raw(31), 0.U)), load_data_raw(31, 16))
        )
      ),
      2.U -> load_data_raw // word
    )
  )

  // 写回数据选择
  val write_data = MuxLookup(io.ctrl.wb_sel, io.alu_result)(
    Seq(
      0.U -> io.alu_result, // ALU result
      1.U -> load_data, // Memory data
      2.U -> (io.alu_result + 4.U) // PC+4 (for JAL/JALR)
    )
  )

  // 输出到 WB 阶段
  io.wb_write_data := write_data
  io.wb_rd := io.rd
  io.wb_reg_write := io.ctrl.reg_write
  io.wb_valid := io.valid
}

/**
 * MEM/WB 流水线寄存器
 */
class MEM_WB_Reg extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())

    // 输入 (来自 MEM)
    val mem_write_data = Input(UInt(XLEN.W))
    val mem_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val mem_reg_write = Input(Bool())
    val mem_valid = Input(Bool())

    // 输出 (到 WB)
    val wb_write_data = Output(UInt(XLEN.W))
    val wb_rd = Output(UInt(REG_ADDR_WIDTH.W))
    val wb_reg_write = Output(Bool())
    val wb_valid = Output(Bool())
  })

  val write_data_reg = RegInit(0.U(XLEN.W))
  val rd_reg = RegInit(0.U(REG_ADDR_WIDTH.W))
  val reg_write_reg = RegInit(false.B)
  val valid_reg = RegInit(false.B)

  when(io.flush) {
    reg_write_reg := false.B
    valid_reg := false.B
  }.elsewhen(!io.stall) {
    write_data_reg := io.mem_write_data
    rd_reg := io.mem_rd
    reg_write_reg := io.mem_reg_write
    valid_reg := io.mem_valid
  }

  io.wb_write_data := write_data_reg
  io.wb_rd := rd_reg
  io.wb_reg_write := reg_write_reg
  io.wb_valid := valid_reg
}
