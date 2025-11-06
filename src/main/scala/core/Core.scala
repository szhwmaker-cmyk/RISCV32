package core

import chisel3._
import chisel3.util._
import common.Config._
import bus.WishboneMaster

/**
 * RV32E 5-Stage Pipeline Processor Core
 *
 * 五级流水线处理器核心
 * - IF: Instruction Fetch
 * - ID: Instruction Decode
 * - EX: Execute
 * - MEM: Memory Access
 * - WB: Write Back
 *
 * 特性：
 * - 数据转发 (Forwarding)
 * - LOAD-USE 冒险检测和暂停
 * - 分支预测（静态不跳转）
 */
class Core extends Module {
  val io = IO(new Bundle {
    // 指令存储器接口
    val imem = new WishboneMaster

    // 数据存储器接口
    val dmem = new WishboneMaster

    // 调试接口（可选）
    val debug = if (DEBUG_ENABLE) Some(new Bundle {
      val pc = Output(UInt(XLEN.W))
      val inst = Output(UInt(INST_WIDTH.W))
      val regs = Output(Vec(REG_NUM, UInt(XLEN.W)))
    }) else None
  })

  // ============================================================================
  // 模块实例化
  // ============================================================================

  val regfile = Module(new RegFile)
  val if_stage = Module(new IF)
  val if_id_reg = Module(new IF_ID_Reg)
  val id_stage = Module(new ID)
  val id_ex_reg = Module(new ID_EX_Reg)
  val ex_stage = Module(new EX)
  val ex_mem_reg = Module(new EX_MEM_Reg)
  val mem_stage = Module(new MEM)
  val mem_wb_reg = Module(new MEM_WB_Reg)
  val hazard_unit = Module(new HazardUnit)
  val forwarding_unit = Module(new ForwardingUnit)

  // ============================================================================
  // IF 阶段连接
  // ============================================================================

  if_stage.io.stall := hazard_unit.io.stall_if
  if_stage.io.flush := hazard_unit.io.flush_if
  if_stage.io.branch_target := ex_stage.io.branch_target
  if_stage.io.branch_taken := ex_stage.io.branch_taken
  io.imem <> if_stage.io.imem

  // IF/ID 寄存器
  if_id_reg.io.stall := hazard_unit.io.stall_id
  if_id_reg.io.flush := hazard_unit.io.flush_id
  if_id_reg.io.if_pc := if_stage.io.pc
  if_id_reg.io.if_inst := if_stage.io.inst
  if_id_reg.io.if_valid := if_stage.io.valid

  // ============================================================================
  // ID 阶段连接
  // ============================================================================

  id_stage.io.pc := if_id_reg.io.id_pc
  id_stage.io.inst := if_id_reg.io.id_inst
  id_stage.io.valid := if_id_reg.io.id_valid

  // 寄存器文件读端口
  regfile.io.rs1_addr := id_stage.io.rs1_addr
  regfile.io.rs2_addr := id_stage.io.rs2_addr
  id_stage.io.rs1_data := regfile.io.rs1_data
  id_stage.io.rs2_data := regfile.io.rs2_data

  // ID/EX 寄存器
  id_ex_reg.io.stall := false.B // EX 阶段不暂停
  id_ex_reg.io.flush := hazard_unit.io.flush_ex
  id_ex_reg.io.id_pc := id_stage.io.ex_pc
  id_ex_reg.io.id_rs1_data := id_stage.io.ex_rs1_data
  id_ex_reg.io.id_rs2_data := id_stage.io.ex_rs2_data
  id_ex_reg.io.id_imm := id_stage.io.ex_imm
  id_ex_reg.io.id_rd := id_stage.io.ex_rd
  id_ex_reg.io.id_rs1 := id_stage.io.ex_rs1
  id_ex_reg.io.id_rs2 := id_stage.io.ex_rs2
  id_ex_reg.io.id_ctrl := id_stage.io.ex_ctrl
  id_ex_reg.io.id_valid := id_stage.io.ex_valid

  // ============================================================================
  // EX 阶段连接
  // ============================================================================

  ex_stage.io.pc := id_ex_reg.io.ex_pc
  ex_stage.io.rs1_data := id_ex_reg.io.ex_rs1_data
  ex_stage.io.rs2_data := id_ex_reg.io.ex_rs2_data
  ex_stage.io.imm := id_ex_reg.io.ex_imm
  ex_stage.io.rd := id_ex_reg.io.ex_rd
  ex_stage.io.ctrl := id_ex_reg.io.ex_ctrl
  ex_stage.io.valid := id_ex_reg.io.ex_valid

  // 数据转发
  forwarding_unit.io.ex_rs1 := id_ex_reg.io.ex_rs1
  forwarding_unit.io.ex_rs2 := id_ex_reg.io.ex_rs2
  forwarding_unit.io.mem_rd := ex_mem_reg.io.mem_rd
  forwarding_unit.io.mem_reg_write := ex_mem_reg.io.mem_ctrl.reg_write
  forwarding_unit.io.wb_rd := mem_wb_reg.io.wb_rd
  forwarding_unit.io.wb_reg_write := mem_wb_reg.io.wb_reg_write

  ex_stage.io.forward_a := forwarding_unit.io.forward_a
  ex_stage.io.forward_b := forwarding_unit.io.forward_b
  ex_stage.io.ex_mem_alu_result := ex_mem_reg.io.mem_alu_result
  ex_stage.io.mem_wb_write_data := mem_wb_reg.io.wb_write_data

  // EX/MEM 寄存器
  ex_mem_reg.io.stall := false.B
  ex_mem_reg.io.flush := false.B
  ex_mem_reg.io.ex_alu_result := ex_stage.io.mem_alu_result
  ex_mem_reg.io.ex_rs2_data := ex_stage.io.mem_rs2_data
  ex_mem_reg.io.ex_rd := ex_stage.io.mem_rd
  ex_mem_reg.io.ex_ctrl := ex_stage.io.mem_ctrl
  ex_mem_reg.io.ex_valid := ex_stage.io.mem_valid

  // ============================================================================
  // MEM 阶段连接
  // ============================================================================

  mem_stage.io.alu_result := ex_mem_reg.io.mem_alu_result
  mem_stage.io.rs2_data := ex_mem_reg.io.mem_rs2_data
  mem_stage.io.rd := ex_mem_reg.io.mem_rd
  mem_stage.io.ctrl := ex_mem_reg.io.mem_ctrl
  mem_stage.io.valid := ex_mem_reg.io.mem_valid
  io.dmem <> mem_stage.io.dmem

  // MEM/WB 寄存器
  mem_wb_reg.io.stall := false.B
  mem_wb_reg.io.flush := false.B
  mem_wb_reg.io.mem_write_data := mem_stage.io.wb_write_data
  mem_wb_reg.io.mem_rd := mem_stage.io.wb_rd
  mem_wb_reg.io.mem_reg_write := mem_stage.io.wb_reg_write
  mem_wb_reg.io.mem_valid := mem_stage.io.wb_valid

  // ============================================================================
  // WB 阶段连接
  // ============================================================================

  // 寄存器文件写端口
  regfile.io.wen := mem_wb_reg.io.wb_reg_write && mem_wb_reg.io.wb_valid
  regfile.io.waddr := mem_wb_reg.io.wb_rd
  regfile.io.wdata := mem_wb_reg.io.wb_write_data

  // ============================================================================
  // 冒险检测单元连接
  // ============================================================================

  hazard_unit.io.id_rs1 := id_stage.io.ex_rs1
  hazard_unit.io.id_rs2 := id_stage.io.ex_rs2
  hazard_unit.io.ex_rd := id_ex_reg.io.ex_rd
  hazard_unit.io.ex_reg_write := id_ex_reg.io.ex_ctrl.reg_write
  hazard_unit.io.ex_mem_read := id_ex_reg.io.ex_ctrl.mem_read
  hazard_unit.io.mem_rd := ex_mem_reg.io.mem_rd
  hazard_unit.io.mem_reg_write := ex_mem_reg.io.mem_ctrl.reg_write
  hazard_unit.io.wb_rd := mem_wb_reg.io.wb_rd
  hazard_unit.io.wb_reg_write := mem_wb_reg.io.wb_reg_write
  hazard_unit.io.branch_taken := ex_stage.io.branch_taken

  // ============================================================================
  // 调试接口
  // ============================================================================

  if (DEBUG_ENABLE) {
    io.debug.get.pc := if_stage.io.pc
    io.debug.get.inst := if_stage.io.inst
    io.debug.get.regs := regfile.io.debug_reg.get
  }
}
