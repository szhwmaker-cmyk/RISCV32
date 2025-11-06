package core

import chisel3._
import chisel3.util._
import common.Config._
import bus.WishboneMaster

/**
 * IF (Instruction Fetch) 取指阶段
 *
 * 功能：
 * 1. 维护程序计数器 (PC)
 * 2. 通过 Wishbone 总线从指令存储器读取指令
 * 3. 处理 PC 更新（顺序、跳转、分支）
 * 4. 处理流水线暂停和清空
 */
class IF extends Module {
  val io = IO(new Bundle {
    // 控制信号
    val stall = Input(Bool()) // 暂停信号
    val flush = Input(Bool()) // 清空信号
    val branch_target = Input(UInt(XLEN.W)) // 分支目标地址
    val branch_taken = Input(Bool()) // 分支是否发生

    // 指令存储器接口 (Wishbone Master)
    val imem = new WishboneMaster

    // 输出到 ID 阶段
    val pc = Output(UInt(XLEN.W))
    val inst = Output(UInt(INST_WIDTH.W))
    val valid = Output(Bool()) // 指令是否有效
  })

  // PC 寄存器
  val pc_reg = RegInit(PC_RESET.U(XLEN.W))

  // 下一个 PC 值
  val pc_next = Wire(UInt(XLEN.W))

  // PC 更新逻辑
  when(!io.stall) {
    when(io.branch_taken) {
      // 分支跳转
      pc_reg := io.branch_target
    }.otherwise {
      // 顺序执行
      pc_reg := pc_next
    }
  }

  // PC+4 (下一条指令)
  val pc_plus4 = pc_reg + 4.U
  pc_next := pc_plus4

  // Wishbone 总线请求
  io.imem.adr := pc_reg
  io.imem.dat_w := 0.U
  io.imem.we := false.B
  io.imem.sel := "b1111".U
  io.imem.cyc := true.B
  io.imem.stb := !io.stall && !io.flush

  // 指令输出
  val inst_reg = RegInit(0.U(INST_WIDTH.W))
  val valid_reg = RegInit(false.B)

  when(io.flush) {
    // 清空时插入 NOP
    inst_reg := 0x00000013.U // NOP (addi x0, x0, 0)
    valid_reg := false.B
  }.elsewhen(!io.stall && io.imem.ack) {
    // 正常取指
    inst_reg := io.imem.dat_r
    valid_reg := true.B
  }.elsewhen(io.stall) {
    // 暂停时保持
    valid_reg := valid_reg
  }.otherwise {
    valid_reg := false.B
  }

  io.pc := pc_reg
  io.inst := inst_reg
  io.valid := valid_reg
}

/**
 * IF/ID 流水线寄存器
 */
class IF_ID_Reg extends Module {
  val io = IO(new Bundle {
    val stall = Input(Bool())
    val flush = Input(Bool())

    // 输入 (来自 IF)
    val if_pc = Input(UInt(XLEN.W))
    val if_inst = Input(UInt(INST_WIDTH.W))
    val if_valid = Input(Bool())

    // 输出 (到 ID)
    val id_pc = Output(UInt(XLEN.W))
    val id_inst = Output(UInt(INST_WIDTH.W))
    val id_valid = Output(Bool())
  })

  val pc_reg = RegInit(0.U(XLEN.W))
  val inst_reg = RegInit(0x00000013.U(INST_WIDTH.W)) // NOP
  val valid_reg = RegInit(false.B)

  when(io.flush) {
    inst_reg := 0x00000013.U
    valid_reg := false.B
  }.elsewhen(!io.stall) {
    pc_reg := io.if_pc
    inst_reg := io.if_inst
    valid_reg := io.if_valid
  }

  io.id_pc := pc_reg
  io.id_inst := inst_reg
  io.id_valid := valid_reg
}
