package core

import chisel3._
import chisel3.util._

// ============================================================================
// Hazard Detection and Forwarding Unit
// 冒险检测和数据转发单元
// ============================================================================

class HazardUnit extends Module {
  val io = IO(new Bundle {
    // ID 阶段信号
    val if_id_rs1 = Input(UInt(4.W))
    val if_id_rs2 = Input(UInt(4.W))

    // EX 阶段信号
    val id_ex_rs1 = Input(UInt(4.W))
    val id_ex_rs2 = Input(UInt(4.W))
    val id_ex_rd = Input(UInt(4.W))
    val id_ex_mem_read = Input(Bool())

    // MEM 阶段信号
    val ex_mem_rd = Input(UInt(4.W))
    val ex_mem_reg_write = Input(Bool())

    // WB 阶段信号
    val mem_wb_rd = Input(UInt(4.W))
    val mem_wb_reg_write = Input(Bool())

    // 分支控制
    val branch_taken = Input(Bool())

    // 控制输出
    val stall_if = Output(Bool())
    val stall_id = Output(Bool())
    val flush_ex = Output(Bool())
    val flush_if = Output(Bool())

    // 转发控制
    val forward_a = Output(UInt(2.W))  // 转发到 EX 阶段的 rs1
    val forward_b = Output(UInt(2.W))  // 转发到 EX 阶段的 rs2
  })

  // ========== 数据转发检测 ==========
  // Forward A (rs1)
  when(io.ex_mem_reg_write && io.ex_mem_rd =/= 0.U && io.ex_mem_rd === io.id_ex_rs1) {
    // EX/MEM 转发
    io.forward_a := 1.U
  }.elsewhen(io.mem_wb_reg_write && io.mem_wb_rd =/= 0.U && io.mem_wb_rd === io.id_ex_rs1) {
    // MEM/WB 转发
    io.forward_a := 2.U
  }.otherwise {
    // 不转发
    io.forward_a := 0.U
  }

  // Forward B (rs2)
  when(io.ex_mem_reg_write && io.ex_mem_rd =/= 0.U && io.ex_mem_rd === io.id_ex_rs2) {
    // EX/MEM 转发
    io.forward_b := 1.U
  }.elsewhen(io.mem_wb_reg_write && io.mem_wb_rd =/= 0.U && io.mem_wb_rd === io.id_ex_rs2) {
    // MEM/WB 转发
    io.forward_b := 2.U
  }.otherwise {
    // 不转发
    io.forward_b := 0.U
  }

  // ========== LOAD-USE 冒险检测 ==========
  val load_use_hazard = io.id_ex_mem_read && (
    (io.id_ex_rd =/= 0.U && io.id_ex_rd === io.if_id_rs1) ||
    (io.id_ex_rd =/= 0.U && io.id_ex_rd === io.if_id_rs2)
  )

  // ========== 控制冒险处理 ==========
  // 分支跳转时冲刷流水线
  io.flush_if := io.branch_taken
  io.flush_ex := io.branch_taken || load_use_hazard

  // LOAD-USE 冒险时暂停流水线
  io.stall_if := load_use_hazard
  io.stall_id := load_use_hazard
}
