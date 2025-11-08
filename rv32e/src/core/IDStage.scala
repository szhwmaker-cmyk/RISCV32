package core

import chisel3._
import chisel3.util._

/**
 * Control Unit
 * 控制单元 - 生成控制信号
 */
class ControlUnit extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(7.W))
    val funct3 = Input(UInt(3.W))
    val funct7 = Input(UInt(7.W))

    val ctrl = Output(new ControlSignals())
    val imm_type = Output(UInt(ImmType.width.W))
  })

  // 默认值
  val ctrl = Wire(new ControlSignals())
  ctrl.alu_op := ALUOp.ADD
  ctrl.alu_src1 := 0.U  // rs1
  ctrl.alu_src2 := 0.U  // rs2
  ctrl.branch := false.B
  ctrl.branch_type := BranchType.BEQ
  ctrl.mem_read := false.B
  ctrl.mem_write := false.B
  ctrl.mem_size := 2.U  // word
  ctrl.mem_unsigned := false.B
  ctrl.reg_write := false.B
  ctrl.wb_src := 0.U  // ALU

  val imm_type = Wire(UInt(ImmType.width.W))
  imm_type := ImmType.I_TYPE

  // 根据opcode解码
  switch(io.opcode) {
    // OP-IMM (立即数运算)
    is(Opcode.OP_IMM) {
      ctrl.reg_write := true.B
      ctrl.alu_src2 := 1.U  // imm
      imm_type := ImmType.I_TYPE

      switch(io.funct3) {
        is(Funct3.ADD_SUB) { ctrl.alu_op := ALUOp.ADD }
        is(Funct3.SLT) { ctrl.alu_op := ALUOp.SLT }
        is(Funct3.SLTU) { ctrl.alu_op := ALUOp.SLTU }
        is(Funct3.XOR) { ctrl.alu_op := ALUOp.XOR }
        is(Funct3.OR) { ctrl.alu_op := ALUOp.OR }
        is(Funct3.AND) { ctrl.alu_op := ALUOp.AND }
        is(Funct3.SLL) { ctrl.alu_op := ALUOp.SLL }
        is(Funct3.SRL_SRA) {
          ctrl.alu_op := Mux(io.funct7(5), ALUOp.SRA, ALUOp.SRL)
        }
      }
    }

    // OP (寄存器运算)
    is(Opcode.OP) {
      ctrl.reg_write := true.B
      imm_type := ImmType.ZERO

      switch(io.funct3) {
        is(Funct3.ADD_SUB) {
          ctrl.alu_op := Mux(io.funct7(5), ALUOp.SUB, ALUOp.ADD)
        }
        is(Funct3.SLL) { ctrl.alu_op := ALUOp.SLL }
        is(Funct3.SLT) { ctrl.alu_op := ALUOp.SLT }
        is(Funct3.SLTU) { ctrl.alu_op := ALUOp.SLTU }
        is(Funct3.XOR) { ctrl.alu_op := ALUOp.XOR }
        is(Funct3.SRL_SRA) {
          ctrl.alu_op := Mux(io.funct7(5), ALUOp.SRA, ALUOp.SRL)
        }
        is(Funct3.OR) { ctrl.alu_op := ALUOp.OR }
        is(Funct3.AND) { ctrl.alu_op := ALUOp.AND }
      }
    }

    // LOAD
    is(Opcode.LOAD) {
      ctrl.reg_write := true.B
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := ALUOp.ADD
      ctrl.mem_read := true.B
      ctrl.wb_src := 1.U  // MEM
      imm_type := ImmType.I_TYPE

      switch(io.funct3) {
        is(Funct3.LB) {
          ctrl.mem_size := 0.U
          ctrl.mem_unsigned := false.B
        }
        is(Funct3.LH) {
          ctrl.mem_size := 1.U
          ctrl.mem_unsigned := false.B
        }
        is(Funct3.LW) {
          ctrl.mem_size := 2.U
          ctrl.mem_unsigned := false.B
        }
        is(Funct3.LBU) {
          ctrl.mem_size := 0.U
          ctrl.mem_unsigned := true.B
        }
        is(Funct3.LHU) {
          ctrl.mem_size := 1.U
          ctrl.mem_unsigned := true.B
        }
      }
    }

    // STORE
    is(Opcode.STORE) {
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := ALUOp.ADD
      ctrl.mem_write := true.B
      imm_type := ImmType.S_TYPE

      switch(io.funct3) {
        is(Funct3.SB) { ctrl.mem_size := 0.U }
        is(Funct3.SH) { ctrl.mem_size := 1.U }
        is(Funct3.SW) { ctrl.mem_size := 2.U }
      }
    }

    // BRANCH
    is(Opcode.BRANCH) {
      ctrl.branch := true.B
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := ALUOp.ADD  // 计算分支目标地址
      imm_type := ImmType.B_TYPE

      switch(io.funct3) {
        is(Funct3.BEQ) { ctrl.branch_type := BranchType.BEQ }
        is(Funct3.BNE) { ctrl.branch_type := BranchType.BNE }
        is(Funct3.BLT) { ctrl.branch_type := BranchType.BLT }
        is(Funct3.BGE) { ctrl.branch_type := BranchType.BGE }
        is(Funct3.BLTU) { ctrl.branch_type := BranchType.BLTU }
        is(Funct3.BGEU) { ctrl.branch_type := BranchType.BGEU }
      }
    }

    // JAL
    is(Opcode.JAL) {
      ctrl.branch := true.B
      ctrl.reg_write := true.B
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := ALUOp.ADD
      ctrl.wb_src := 2.U  // PC+4
      ctrl.branch_type := BranchType.JAL
      imm_type := ImmType.J_TYPE
    }

    // JALR
    is(Opcode.JALR) {
      ctrl.branch := true.B
      ctrl.reg_write := true.B
      ctrl.wb_src := 2.U  // PC+4
      ctrl.branch_type := BranchType.JALR
      imm_type := ImmType.I_TYPE
    }

    // LUI
    is(Opcode.LUI) {
      ctrl.reg_write := true.B
      ctrl.alu_src1 := 2.U  // 0
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := ALUOp.ADD
      imm_type := ImmType.U_TYPE
    }

    // AUIPC
    is(Opcode.AUIPC) {
      ctrl.reg_write := true.B
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := ALUOp.ADD
      imm_type := ImmType.U_TYPE
    }
  }

  io.ctrl := ctrl
  io.imm_type := imm_type
}

/**
 * ID (Instruction Decode) Stage
 * 译码阶段
 *
 * 功能：
 * - 解码指令
 * - 生成控制信号
 * - 读取寄存器
 * - 生成立即数
 * - 将所有信息传递到ID/EX流水线寄存器
 */
class IDStage extends Module {
  val io = IO(new Bundle {
    // 来自IF阶段
    val if_id = Input(new IF_ID_Reg())

    // 控制信号
    val stall = Input(Bool())
    val flush = Input(Bool())

    // 寄存器堆接口
    val rs1_addr = Output(UInt(4.W))
    val rs1_data = Input(UInt(32.W))
    val rs2_addr = Output(UInt(4.W))
    val rs2_data = Input(UInt(32.W))

    // 写回接口（来自WB阶段）
    val wb_rd_addr = Input(UInt(4.W))
    val wb_rd_data = Input(UInt(32.W))
    val wb_reg_write = Input(Bool())

    // 输出到EX阶段
    val id_ex = Output(new ID_EX_Reg())
  })

  // 指令字段提取
  val inst = io.if_id.inst
  val opcode = InstructionDecoder.getOpcode(inst)
  val rd = InstructionDecoder.getRd(inst)
  val funct3 = InstructionDecoder.getFunct3(inst)
  val rs1 = InstructionDecoder.getRs1(inst)
  val rs2 = InstructionDecoder.getRs2(inst)
  val funct7 = InstructionDecoder.getFunct7(inst)

  // 控制单元
  val control = Module(new ControlUnit())
  control.io.opcode := opcode
  control.io.funct3 := funct3
  control.io.funct7 := funct7

  // 立即数生成器
  val immgen = Module(new ImmGen())
  immgen.io.inst := inst
  immgen.io.imm_type := control.io.imm_type

  // 寄存器读取
  io.rs1_addr := rs1
  io.rs2_addr := rs2

  // ID/EX Pipeline Register
  val id_ex_reg = RegInit(0.U.asTypeOf(new ID_EX_Reg()))

  when(io.flush) {
    // 冲刷：插入NOP
    id_ex_reg := 0.U.asTypeOf(new ID_EX_Reg())
    id_ex_reg.valid := false.B
  }.elsewhen(!io.stall) {
    // 正常更新
    id_ex_reg.pc := io.if_id.pc
    id_ex_reg.inst := inst
    id_ex_reg.ctrl := control.io.ctrl
    id_ex_reg.rs1_data := io.rs1_data
    id_ex_reg.rs2_data := io.rs2_data
    id_ex_reg.rs1_addr := rs1
    id_ex_reg.rs2_addr := rs2
    id_ex_reg.rd_addr := rd
    id_ex_reg.imm := immgen.io.imm
    id_ex_reg.valid := io.if_id.valid
  }
  // stall时保持不变

  io.id_ex := id_ex_reg
}
