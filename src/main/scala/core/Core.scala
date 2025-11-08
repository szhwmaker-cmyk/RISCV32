package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * RV32E 处理器核心顶层模块
 *
 * 集成五级流水线的所有阶段：
 * - IF: 取指
 * - ID: 译码
 * - EX: 执行
 * - MEM: 访存
 * - WB: 写回
 *
 * 以及冒险检测和转发单元
 */
class Core extends Module {
  val io = IO(new Bundle {
    // 指令存储器接口
    val imem_addr  = Output(UInt(XLEN.W))
    val imem_rdata = Input(UInt(INST_WIDTH.W))

    // 数据存储器接口
    val dmem_addr  = Output(UInt(XLEN.W))
    val dmem_wdata = Output(UInt(XLEN.W))
    val dmem_wen   = Output(Bool())
    val dmem_ren   = Output(Bool())
    val dmem_size  = Output(UInt(2.W))
    val dmem_rdata = Input(UInt(XLEN.W))
  })

  // ============================================================================
  // 实例化各个模块
  // ============================================================================

  val regfile = Module(new RegFile)
  val if_stage = Module(new IF)
  val id_stage = Module(new ID)
  val ex_stage = Module(new EX)
  val mem_stage = Module(new MEM)
  val wb_stage = Module(new WB)
  val hazard = Module(new HazardUnit)

  // ============================================================================
  // 流水线寄存器
  // ============================================================================

  val if_id_reg = RegInit(0.U.asTypeOf(new IF_ID_Reg))
  val id_ex_reg = RegInit(0.U.asTypeOf(new ID_EX_Reg))
  val ex_mem_reg = RegInit(0.U.asTypeOf(new EX_MEM_Reg))
  val mem_wb_reg = RegInit(0.U.asTypeOf(new MEM_WB_Reg))

  // ============================================================================
  // IF 阶段连接
  // ============================================================================

  if_stage.io.stall := hazard.io.stall_if
  if_stage.io.flush := hazard.io.flush_if
  if_stage.io.branch_taken := ex_mem_reg.branch_taken
  if_stage.io.branch_target := ex_mem_reg.branch_target
  if_stage.io.inst_data := io.imem_rdata

  io.imem_addr := if_stage.io.inst_addr

  // IF/ID 流水线寄存器更新
  when(!hazard.io.stall_id) {
    if_id_reg.pc := if_stage.io.pc_out
    if_id_reg.inst := if_stage.io.inst_out
  }

  // ============================================================================
  // ID 阶段连接
  // ============================================================================

  id_stage.io.pc_in := if_id_reg.pc
  id_stage.io.inst := if_id_reg.inst
  id_stage.io.flush := hazard.io.flush_id
  id_stage.io.rs1_data := regfile.io.rs1_data
  id_stage.io.rs2_data := regfile.io.rs2_data

  regfile.io.rs1_addr := id_stage.io.rs1_addr
  regfile.io.rs2_addr := id_stage.io.rs2_addr

  // ID/EX 流水线寄存器更新
  when(!hazard.io.stall_id) {
    id_ex_reg := id_stage.io.id_ex_reg
  }.elsewhen(hazard.io.flush_ex) {
    // 插入 NOP
    id_ex_reg := 0.U.asTypeOf(new ID_EX_Reg)
  }

  // ============================================================================
  // EX 阶段连接
  // ============================================================================

  ex_stage.io.id_ex_reg := id_ex_reg
  ex_stage.io.flush := hazard.io.flush_ex
  ex_stage.io.forward_a := hazard.io.forward_a
  ex_stage.io.forward_b := hazard.io.forward_b
  ex_stage.io.ex_mem_data := ex_mem_reg.alu_result
  ex_stage.io.mem_wb_data := wb_stage.io.reg_wdata

  // EX/MEM 流水线寄存器更新
  ex_mem_reg := ex_stage.io.ex_mem_reg

  // ============================================================================
  // MEM 阶段连接
  // ============================================================================

  mem_stage.io.ex_mem_reg := ex_mem_reg
  mem_stage.io.flush := false.B  // MEM 阶段通常不需要 flush
  mem_stage.io.mem_rdata := io.dmem_rdata

  io.dmem_addr := mem_stage.io.mem_addr
  io.dmem_wdata := mem_stage.io.mem_wdata
  io.dmem_wen := mem_stage.io.mem_wen
  io.dmem_ren := mem_stage.io.mem_ren
  io.dmem_size := mem_stage.io.mem_size

  // MEM/WB 流水线寄存器更新
  mem_wb_reg := mem_stage.io.mem_wb_reg

  // ============================================================================
  // WB 阶段连接
  // ============================================================================

  wb_stage.io.mem_wb_reg := mem_wb_reg

  regfile.io.wen := wb_stage.io.reg_wen
  regfile.io.rd_addr := wb_stage.io.reg_waddr
  regfile.io.rd_data := wb_stage.io.reg_wdata

  // ============================================================================
  // Hazard 单元连接
  // ============================================================================

  hazard.io.id_ex_rs1 := id_ex_reg.rs1_addr
  hazard.io.id_ex_rs2 := id_ex_reg.rs2_addr
  hazard.io.id_ex_mem_read := id_ex_reg.mem_read
  hazard.io.id_ex_rd := id_ex_reg.rd_addr

  hazard.io.ex_mem_rd := ex_mem_reg.rd_addr
  hazard.io.ex_mem_reg_write := ex_mem_reg.reg_write
  hazard.io.ex_mem_alu_result := ex_mem_reg.alu_result

  hazard.io.mem_wb_rd := mem_wb_reg.rd_addr
  hazard.io.mem_wb_reg_write := mem_wb_reg.reg_write
  hazard.io.mem_wb_wdata := wb_stage.io.reg_wdata

  hazard.io.if_id_rs1 := id_stage.io.rs1_addr
  hazard.io.if_id_rs2 := id_stage.io.rs2_addr

  hazard.io.branch_taken := ex_mem_reg.branch_taken
}
