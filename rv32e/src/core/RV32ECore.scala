package core

import chisel3._
import chisel3.util._

/**
 * RV32E 5-Stage Pipelined Processor Core
 * RV32E 五级流水线处理器核心
 *
 * 流水线阶段：
 * 1. IF (Instruction Fetch)     - 取指
 * 2. ID (Instruction Decode)    - 译码
 * 3. EX (Execute)               - 执行
 * 4. MEM (Memory Access)        - 访存
 * 5. WB (Write Back)            - 写回
 *
 * 特性：
 * - 数据前递 (Forwarding)
 * - 冒险检测 (Hazard Detection)
 * - 流水线暂停 (Stall)
 * - 流水线冲刷 (Flush)
 * - 分支预测 (Static Prediction)
 */
class RV32ECore extends Module {
  val io = IO(new Bundle {
    // 指令存储器接口
    val imem_addr = Output(UInt(32.W))
    val imem_data = Input(UInt(32.W))
    val imem_valid = Input(Bool())

    // 数据存储器接口
    val dmem_addr = Output(UInt(32.W))
    val dmem_wdata = Output(UInt(32.W))
    val dmem_rdata = Input(UInt(32.W))
    val dmem_wen = Output(Bool())
    val dmem_ren = Output(Bool())
    val dmem_size = Output(UInt(2.W))

    // 调试接口
    val debug_pc = Output(UInt(32.W))
    val debug_inst = Output(UInt(32.W))
  })

  // ========== 模块实例化 ==========

  // 寄存器堆
  val regfile = Module(new RegFile())

  // 流水线阶段
  val if_stage = Module(new IFStage())
  val id_stage = Module(new IDStage())
  val ex_stage = Module(new EXStage())
  val mem_stage = Module(new MEMStage())
  val wb_stage = Module(new WBStage())

  // 冒险检测和前递单元
  val hazard_unit = Module(new HazardUnit())

  // ========== 冒险检测和前递单元连接 ==========

  hazard_unit.io.id_rs1 := id_stage.io.id_ex.rs1_addr
  hazard_unit.io.id_rs2 := id_stage.io.id_ex.rs2_addr

  hazard_unit.io.ex_rd := ex_stage.io.ex_mem.rd_addr
  hazard_unit.io.ex_mem_read := ex_stage.io.ex_mem.mem_read
  hazard_unit.io.ex_reg_write := ex_stage.io.ex_mem.reg_write

  hazard_unit.io.mem_rd := mem_stage.io.mem_wb.rd_addr
  hazard_unit.io.mem_reg_write := mem_stage.io.mem_wb.reg_write

  hazard_unit.io.wb_rd := wb_stage.io.wb_rd_addr
  hazard_unit.io.wb_reg_write := wb_stage.io.wb_reg_write

  hazard_unit.io.branch_taken := ex_stage.io.branch_taken

  // ========== IF Stage ==========

  if_stage.io.stall := hazard_unit.io.stall
  if_stage.io.flush := hazard_unit.io.flush_if
  if_stage.io.branch_taken := ex_stage.io.branch_taken
  if_stage.io.branch_target := ex_stage.io.branch_target
  if_stage.io.imem_data := io.imem_data
  if_stage.io.imem_valid := io.imem_valid

  io.imem_addr := if_stage.io.imem_addr

  // ========== ID Stage ==========

  id_stage.io.if_id := if_stage.io.if_id
  id_stage.io.stall := hazard_unit.io.stall
  id_stage.io.flush := hazard_unit.io.flush_id

  // 寄存器读取
  id_stage.io.rs1_data := regfile.io.rs1_data
  id_stage.io.rs2_data := regfile.io.rs2_data
  regfile.io.rs1_addr := id_stage.io.rs1_addr
  regfile.io.rs2_addr := id_stage.io.rs2_addr

  // 写回到寄存器
  id_stage.io.wb_rd_addr := wb_stage.io.wb_rd_addr
  id_stage.io.wb_rd_data := wb_stage.io.wb_rd_data
  id_stage.io.wb_reg_write := wb_stage.io.wb_reg_write

  // ========== EX Stage ==========

  ex_stage.io.id_ex := id_stage.io.id_ex
  ex_stage.io.flush := hazard_unit.io.flush_ex

  // 前递控制
  ex_stage.io.forward_a := hazard_unit.io.forward_a
  ex_stage.io.forward_b := hazard_unit.io.forward_b

  // 前递数据源
  ex_stage.io.ex_mem_alu_result := mem_stage.io.mem_wb.alu_result
  ex_stage.io.mem_wb_result := wb_stage.io.wb_forward_data

  // ========== MEM Stage ==========

  mem_stage.io.ex_mem := ex_stage.io.ex_mem

  // 数据存储器接口
  io.dmem_addr := mem_stage.io.dmem_addr
  io.dmem_wdata := mem_stage.io.dmem_wdata
  mem_stage.io.dmem_rdata := io.dmem_rdata
  io.dmem_wen := mem_stage.io.dmem_wen
  io.dmem_ren := mem_stage.io.dmem_ren
  io.dmem_size := mem_stage.io.dmem_size

  // ========== WB Stage ==========

  wb_stage.io.mem_wb := mem_stage.io.mem_wb

  // 写回寄存器堆
  regfile.io.rd_addr := wb_stage.io.wb_rd_addr
  regfile.io.rd_data := wb_stage.io.wb_rd_data
  regfile.io.rd_wen := wb_stage.io.wb_reg_write

  // ========== 调试输出 ==========

  io.debug_pc := if_stage.io.if_id.pc
  io.debug_inst := if_stage.io.if_id.inst
}

/**
 * RV32E Core with Memories (用于仿真测试)
 * 包含指令和数据存储器的完整核心
 */
class RV32ECoreWithMem(
  imem_size: Int = 1024,
  dmem_size: Int = 1024
) extends Module {
  val io = IO(new Bundle {
    // 调试接口
    val debug_pc = Output(UInt(32.W))
    val debug_inst = Output(UInt(32.W))

    // 存储器初始化接口（用于测试）
    val imem_init_wen = Input(Bool())
    val imem_init_addr = Input(UInt(32.W))
    val imem_init_data = Input(UInt(32.W))
  })

  // 核心实例
  val core = Module(new RV32ECore())

  // 指令存储器
  val imem = Module(new InstructionMemory(imem_size))

  // 数据存储器
  val dmem = Module(new DataMemory(dmem_size))

  // 连接核心和存储器
  imem.io.addr := core.io.imem_addr
  core.io.imem_data := imem.io.data
  core.io.imem_valid := imem.io.valid

  dmem.io.addr := core.io.dmem_addr
  dmem.io.wdata := core.io.dmem_wdata
  core.io.dmem_rdata := dmem.io.rdata
  dmem.io.wen := core.io.dmem_wen
  dmem.io.ren := core.io.dmem_ren
  dmem.io.size := core.io.dmem_size

  // 调试输出
  io.debug_pc := core.io.debug_pc
  io.debug_inst := core.io.debug_inst
}
