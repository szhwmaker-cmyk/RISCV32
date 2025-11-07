package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * 冒险检测和数据转发单元
 *
 * 处理三种冒险：
 * 1. 数据冒险（通过数据转发解决）
 * 2. 控制冒险（通过分支预测和流水线清空解决）
 * 3. LOAD-USE 冒险（需要暂停流水线）
 */
class HazardUnit extends Module {
  val io = IO(new Bundle {
    // 流水线寄存器信息
    val id_rs1 = Input(UInt(REG_ADDR_WIDTH.W))
    val id_rs2 = Input(UInt(REG_ADDR_WIDTH.W))
    val ex_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val ex_reg_write = Input(Bool())
    val ex_mem_read = Input(Bool())
    val mem_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val mem_reg_write = Input(Bool())
    val wb_rd = Input(UInt(REG_ADDR_WIDTH.W))
    val wb_reg_write = Input(Bool())

    // 分支/跳转信号
    val branch_taken = Input(Bool())
    val jump_taken = Input(Bool())

    // 控制信号
    val stall_if = Output(Bool())    // 暂停取指
    val stall_id = Output(Bool())    // 暂停译码
    val flush_if = Output(Bool())    // 清空取指
    val flush_id = Output(Bool())    // 清空译码
    val flush_ex = Output(Bool())    // 清空执行

    // 数据转发控制
    val forward_a = Output(UInt(2.W))  // rs1转发选择 (0:无, 1:EX, 2:MEM)
    val forward_b = Output(UInt(2.W))  // rs2转发选择 (0:无, 1:EX, 2:MEM)
  })

  // ============================================================================
  // LOAD-USE 冒险检测
  // ============================================================================

  val load_use_hazard = io.ex_mem_read && (
    (io.ex_rd =/= 0.U && io.ex_rd === io.id_rs1) ||
    (io.ex_rd =/= 0.U && io.ex_rd === io.id_rs2)
  )

  // ============================================================================
  // 控制冒险处理
  // ============================================================================

  val control_hazard = io.branch_taken || io.jump_taken

  // ============================================================================
  // 暂停信号生成
  // ============================================================================

  io.stall_if := load_use_hazard
  io.stall_id := load_use_hazard

  // ============================================================================
  // 清空信号生成
  // ============================================================================

  io.flush_if := control_hazard
  io.flush_id := control_hazard
  io.flush_ex := control_hazard || load_use_hazard

  // ============================================================================
  // 数据转发逻辑
  // ============================================================================

  // rs1 转发
  io.forward_a := 0.U
  when(io.mem_reg_write && io.mem_rd =/= 0.U && io.mem_rd === io.id_rs1) {
    // 从 MEM 阶段转发
    io.forward_a := 2.U
  }.elsewhen(io.ex_reg_write && io.ex_rd =/= 0.U && io.ex_rd === io.id_rs1) {
    // 从 EX 阶段转发
    io.forward_a := 1.U
  }

  // rs2 转发
  io.forward_b := 0.U
  when(io.mem_reg_write && io.mem_rd =/= 0.U && io.mem_rd === io.id_rs2) {
    // 从 MEM 阶段转发
    io.forward_b := 2.U
  }.elsewhen(io.ex_reg_write && io.ex_rd =/= 0.U && io.ex_rd === io.id_rs2) {
    // 从 EX 阶段转发
    io.forward_b := 1.U
  }
}

/**
 * 分支判断单元
 * 根据分支类型和操作数判断是否跳转
 */
class BranchUnit extends Module {
  val io = IO(new Bundle {
    val funct3 = Input(UInt(3.W))
    val rs1_data = Input(UInt(XLEN.W))
    val rs2_data = Input(UInt(XLEN.W))
    val branch = Input(Bool())
    val taken = Output(Bool())
  })

  val eq = io.rs1_data === io.rs2_data
  val lt = io.rs1_data.asSInt < io.rs2_data.asSInt
  val ltu = io.rs1_data < io.rs2_data

  val branch_taken = WireDefault(false.B)

  when(io.branch) {
    switch(io.funct3) {
      is(0.U) { branch_taken := eq }       // BEQ
      is(1.U) { branch_taken := !eq }      // BNE
      is(4.U) { branch_taken := lt }       // BLT
      is(5.U) { branch_taken := !lt }      // BGE
      is(6.U) { branch_taken := ltu }      // BLTU
      is(7.U) { branch_taken := !ltu }     // BGEU
    }
  }

  io.taken := branch_taken
}
