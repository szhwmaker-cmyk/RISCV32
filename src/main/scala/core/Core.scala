package core

import chisel3._
import chisel3.util._
import common._

// ============================================================================
// RV32E Processor Core
// 五级流水线处理器核心顶层
// ============================================================================

class Core extends Module {
  val io = IO(new Bundle {
    // 指令内存接口
    val imem = new MemPortIO

    // 数据内存接口
    val dmem = new MemPortIO
  })

  // ========== 模块实例化 ==========
  val regfile = Module(new RegFile)
  val if_stage = Module(new IF)
  val id_stage = Module(new ID)
  val ex_stage = Module(new EX)
  val mem_stage = Module(new MEM)
  val wb_stage = Module(new WB)
  val hazard = Module(new HazardUnit)

  // ========== 流水线寄存器 ==========
  val if_id_reg = RegInit(0.U.asTypeOf(new IF_ID_Reg))
  val id_ex_reg = RegInit(0.U.asTypeOf(new ID_EX_Reg))
  val ex_mem_reg = RegInit(0.U.asTypeOf(new EX_MEM_Reg))
  val mem_wb_reg = RegInit(0.U.asTypeOf(new MEM_WB_Reg))

  // ========== IF Stage ==========
  if_stage.io.stall := hazard.io.stall_if
  if_stage.io.flush := hazard.io.flush_if
  if_stage.io.branch_taken := ex_mem_reg.branch_taken
  if_stage.io.branch_target := ex_mem_reg.branch_target
  io.imem <> if_stage.io.imem

  // IF/ID 流水线寄存器更新
  when(hazard.io.stall_id) {
    // 保持不变
  }.elsewhen(hazard.io.flush_if) {
    if_id_reg.valid := false.B
    if_id_reg.inst := 0x00000013.U  // NOP
  }.otherwise {
    if_id_reg.pc := if_stage.io.pc_out
    if_id_reg.inst := if_stage.io.inst_out
    if_id_reg.valid := true.B
  }

  // ========== ID Stage ==========
  id_stage.io.inst := if_id_reg.inst
  id_stage.io.pc := if_id_reg.pc

  // 寄存器文件读
  regfile.io.rs1_addr := id_stage.io.rs1_addr
  regfile.io.rs2_addr := id_stage.io.rs2_addr
  id_stage.io.rs1_data := regfile.io.rs1_data
  id_stage.io.rs2_data := regfile.io.rs2_data

  // ID/EX 流水线寄存器更新
  when(hazard.io.flush_ex) {
    id_ex_reg.valid := false.B
    id_ex_reg.ctrl.reg_write := false.B
    id_ex_reg.ctrl.mem_read := false.B
    id_ex_reg.ctrl.mem_write := false.B
  }.otherwise {
    id_ex_reg.pc := if_id_reg.pc
    id_ex_reg.rs1_data := regfile.io.rs1_data
    id_ex_reg.rs2_data := regfile.io.rs2_data
    id_ex_reg.imm := id_stage.io.imm
    id_ex_reg.rs1 := id_stage.io.rs1_addr
    id_ex_reg.rs2 := id_stage.io.rs2_addr
    id_ex_reg.rd := id_stage.io.rd_addr
    id_ex_reg.ctrl := id_stage.io.ctrl
    id_ex_reg.valid := if_id_reg.valid
  }

  // ========== EX Stage ==========
  ex_stage.io.pc := id_ex_reg.pc
  ex_stage.io.rs1_data := id_ex_reg.rs1_data
  ex_stage.io.rs2_data := id_ex_reg.rs2_data
  ex_stage.io.imm := id_ex_reg.imm
  ex_stage.io.ctrl := id_ex_reg.ctrl

  // 数据转发
  ex_stage.io.forward_a := hazard.io.forward_a
  ex_stage.io.forward_b := hazard.io.forward_b
  ex_stage.io.ex_mem_alu_result := ex_mem_reg.alu_result
  ex_stage.io.mem_wb_write_data := wb_stage.io.write_data

  // EX/MEM 流水线寄存器更新
  ex_mem_reg.alu_result := ex_stage.io.alu_result
  ex_mem_reg.rs2_data := ex_stage.io.rs2_data_out
  ex_mem_reg.rd := id_ex_reg.rd
  ex_mem_reg.ctrl := id_ex_reg.ctrl
  ex_mem_reg.valid := id_ex_reg.valid
  ex_mem_reg.branch_taken := ex_stage.io.branch_taken
  ex_mem_reg.branch_target := ex_stage.io.branch_target

  // ========== MEM Stage ==========
  mem_stage.io.alu_result := ex_mem_reg.alu_result
  mem_stage.io.rs2_data := ex_mem_reg.rs2_data
  mem_stage.io.ctrl := ex_mem_reg.ctrl
  io.dmem <> mem_stage.io.dmem

  // MEM/WB 流水线寄存器更新
  mem_wb_reg.alu_result := ex_mem_reg.alu_result
  mem_wb_reg.mem_data := mem_stage.io.mem_data
  mem_wb_reg.rd := ex_mem_reg.rd
  mem_wb_reg.ctrl := ex_mem_reg.ctrl
  mem_wb_reg.valid := ex_mem_reg.valid

  // ========== WB Stage ==========
  wb_stage.io.alu_result := mem_wb_reg.alu_result
  wb_stage.io.mem_data := mem_wb_reg.mem_data
  wb_stage.io.pc_plus_4 := mem_wb_reg.alu_result  // PC+4 在JAL/JALR时已经在ALU计算
  wb_stage.io.ctrl := mem_wb_reg.ctrl

  // 寄存器文件写
  regfile.io.wen := mem_wb_reg.ctrl.reg_write && mem_wb_reg.valid
  regfile.io.rd_addr := mem_wb_reg.rd
  regfile.io.rd_data := wb_stage.io.write_data

  // ========== Hazard Unit Connections ==========
  hazard.io.if_id_rs1 := id_stage.io.rs1_addr
  hazard.io.if_id_rs2 := id_stage.io.rs2_addr
  hazard.io.id_ex_rs1 := id_ex_reg.rs1
  hazard.io.id_ex_rs2 := id_ex_reg.rs2
  hazard.io.id_ex_rd := id_ex_reg.rd
  hazard.io.id_ex_mem_read := id_ex_reg.ctrl.mem_read
  hazard.io.ex_mem_rd := ex_mem_reg.rd
  hazard.io.ex_mem_reg_write := ex_mem_reg.ctrl.reg_write
  hazard.io.mem_wb_rd := mem_wb_reg.rd
  hazard.io.mem_wb_reg_write := mem_wb_reg.ctrl.reg_write
  hazard.io.branch_taken := ex_mem_reg.branch_taken
}
