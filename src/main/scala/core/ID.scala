package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * ID 译码阶段
 *
 * 功能：
 * 1. 指令解码（识别 R/I/S/B/U/J 型指令）
 * 2. 读取寄存器文件
 * 3. 立即数生成和符号扩展
 * 4. 生成控制信号
 */
class ID extends Module {
  val io = IO(new Bundle {
    // 来自 IF 阶段
    val pc_in       = Input(UInt(XLEN.W))
    val inst        = Input(UInt(INST_WIDTH.W))
    val flush       = Input(Bool())

    // 寄存器文件接口
    val rs1_addr    = Output(UInt(REG_ADDR_WIDTH.W))
    val rs2_addr    = Output(UInt(REG_ADDR_WIDTH.W))
    val rs1_data    = Input(UInt(XLEN.W))
    val rs2_data    = Input(UInt(XLEN.W))

    // 输出到 EX 阶段
    val id_ex_reg   = Output(new ID_EX_Reg)
  })

  // 指令字段解析
  val opcode = io.inst(6, 0)
  val rd     = io.inst(11, 7)
  val funct3 = io.inst(14, 12)
  val rs1    = io.inst(19, 15)
  val rs2    = io.inst(24, 20)
  val funct7 = io.inst(31, 25)

  // 立即数生成
  val imm_i = Cat(Fill(20, io.inst(31)), io.inst(31, 20))
  val imm_s = Cat(Fill(20, io.inst(31)), io.inst(31, 25), io.inst(11, 7))
  val imm_b = Cat(Fill(19, io.inst(31)), io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W))
  val imm_u = Cat(io.inst(31, 12), Fill(12, 0.U))
  val imm_j = Cat(Fill(11, io.inst(31)), io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W))

  // 控制信号默认值
  val ctrl = Wire(new Bundle {
    val alu_op       = UInt(4.W)
    val alu_src      = Bool()
    val mem_read     = Bool()
    val mem_write    = Bool()
    val mem_size     = UInt(2.W)
    val mem_unsigned = Bool()
    val reg_write    = Bool()
    val wb_src       = UInt(2.W)
    val branch       = Bool()
    val jump         = Bool()
  })

  // 默认值
  ctrl.alu_op       := AluOp.ADD
  ctrl.alu_src      := false.B
  ctrl.mem_read     := false.B
  ctrl.mem_write    := false.B
  ctrl.mem_size     := MemSize.WORD
  ctrl.mem_unsigned := false.B
  ctrl.reg_write    := false.B
  ctrl.wb_src       := WbSrc.ALU
  ctrl.branch       := false.B
  ctrl.jump         := false.B

  val imm = WireDefault(0.U(XLEN.W))

  // 指令解码
  switch(opcode) {
    // R-type: ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND
    is("b0110011".U) {
      ctrl.reg_write := true.B
      ctrl.alu_src   := false.B
      imm := 0.U

      switch(funct3) {
        is("b000".U) { ctrl.alu_op := Mux(funct7(5), AluOp.SUB, AluOp.ADD) }  // ADD/SUB
        is("b001".U) { ctrl.alu_op := AluOp.SLL }   // SLL
        is("b010".U) { ctrl.alu_op := AluOp.SLT }   // SLT
        is("b011".U) { ctrl.alu_op := AluOp.SLTU }  // SLTU
        is("b100".U) { ctrl.alu_op := AluOp.XOR }   // XOR
        is("b101".U) { ctrl.alu_op := Mux(funct7(5), AluOp.SRA, AluOp.SRL) }  // SRL/SRA
        is("b110".U) { ctrl.alu_op := AluOp.OR }    // OR
        is("b111".U) { ctrl.alu_op := AluOp.AND }   // AND
      }
    }

    // I-type: ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI
    is("b0010011".U) {
      ctrl.reg_write := true.B
      ctrl.alu_src   := true.B
      imm := imm_i

      switch(funct3) {
        is("b000".U) { ctrl.alu_op := AluOp.ADD }   // ADDI
        is("b010".U) { ctrl.alu_op := AluOp.SLT }   // SLTI
        is("b011".U) { ctrl.alu_op := AluOp.SLTU }  // SLTIU
        is("b100".U) { ctrl.alu_op := AluOp.XOR }   // XORI
        is("b110".U) { ctrl.alu_op := AluOp.OR }    // ORI
        is("b111".U) { ctrl.alu_op := AluOp.AND }   // ANDI
        is("b001".U) { ctrl.alu_op := AluOp.SLL }   // SLLI
        is("b101".U) { ctrl.alu_op := Mux(funct7(5), AluOp.SRA, AluOp.SRL) }  // SRLI/SRAI
      }
    }

    // LOAD: LB, LH, LW, LBU, LHU
    is("b0000011".U) {
      ctrl.reg_write    := true.B
      ctrl.alu_src      := true.B
      ctrl.alu_op       := AluOp.ADD
      ctrl.mem_read     := true.B
      ctrl.wb_src       := WbSrc.MEM
      imm := imm_i

      switch(funct3) {
        is("b000".U) { ctrl.mem_size := MemSize.BYTE; ctrl.mem_unsigned := false.B }  // LB
        is("b001".U) { ctrl.mem_size := MemSize.HALF; ctrl.mem_unsigned := false.B }  // LH
        is("b010".U) { ctrl.mem_size := MemSize.WORD; ctrl.mem_unsigned := false.B }  // LW
        is("b100".U) { ctrl.mem_size := MemSize.BYTE; ctrl.mem_unsigned := true.B }   // LBU
        is("b101".U) { ctrl.mem_size := MemSize.HALF; ctrl.mem_unsigned := true.B }   // LHU
      }
    }

    // STORE: SB, SH, SW
    is("b0100011".U) {
      ctrl.alu_src   := true.B
      ctrl.alu_op    := AluOp.ADD
      ctrl.mem_write := true.B
      imm := imm_s

      switch(funct3) {
        is("b000".U) { ctrl.mem_size := MemSize.BYTE }  // SB
        is("b001".U) { ctrl.mem_size := MemSize.HALF }  // SH
        is("b010".U) { ctrl.mem_size := MemSize.WORD }  // SW
      }
    }

    // BRANCH: BEQ, BNE, BLT, BGE, BLTU, BGEU
    is("b1100011".U) {
      ctrl.branch  := true.B
      ctrl.alu_src := false.B
      imm := imm_b

      switch(funct3) {
        is("b000".U) { ctrl.alu_op := AluOp.SUB }   // BEQ (compare via subtraction)
        is("b001".U) { ctrl.alu_op := AluOp.SUB }   // BNE
        is("b100".U) { ctrl.alu_op := AluOp.SLT }   // BLT
        is("b101".U) { ctrl.alu_op := AluOp.SLT }   // BGE
        is("b110".U) { ctrl.alu_op := AluOp.SLTU }  // BLTU
        is("b111".U) { ctrl.alu_op := AluOp.SLTU }  // BGEU
      }
    }

    // JAL
    is("b1101111".U) {
      ctrl.jump      := true.B
      ctrl.reg_write := true.B
      ctrl.wb_src    := WbSrc.PC4
      imm := imm_j
    }

    // JALR
    is("b1100111".U) {
      ctrl.jump      := true.B
      ctrl.reg_write := true.B
      ctrl.alu_src   := true.B
      ctrl.alu_op    := AluOp.ADD
      ctrl.wb_src    := WbSrc.PC4
      imm := imm_i
    }

    // LUI
    is("b0110111".U) {
      ctrl.reg_write := true.B
      ctrl.alu_op    := AluOp.ADD
      ctrl.alu_src   := true.B
      imm := imm_u
    }

    // AUIPC
    is("b0010111".U) {
      ctrl.reg_write := true.B
      ctrl.alu_op    := AluOp.ADD
      ctrl.alu_src   := true.B
      imm := imm_u
    }
  }

  // 输出寄存器地址
  io.rs1_addr := rs1
  io.rs2_addr := rs2

  // 构造输出到 EX 阶段的数据
  when(io.flush) {
    // Flush 时输出 NOP
    io.id_ex_reg.pc          := 0.U
    io.id_ex_reg.rs1_data    := 0.U
    io.id_ex_reg.rs2_data    := 0.U
    io.id_ex_reg.imm         := 0.U
    io.id_ex_reg.rs1_addr    := 0.U
    io.id_ex_reg.rs2_addr    := 0.U
    io.id_ex_reg.rd_addr     := 0.U
    io.id_ex_reg.alu_op      := AluOp.NOP
    io.id_ex_reg.alu_src     := false.B
    io.id_ex_reg.mem_read    := false.B
    io.id_ex_reg.mem_write   := false.B
    io.id_ex_reg.mem_size    := MemSize.WORD
    io.id_ex_reg.mem_unsigned := false.B
    io.id_ex_reg.reg_write   := false.B
    io.id_ex_reg.wb_src      := WbSrc.ALU
    io.id_ex_reg.branch      := false.B
    io.id_ex_reg.jump        := false.B
  }.otherwise {
    io.id_ex_reg.pc          := io.pc_in
    io.id_ex_reg.rs1_data    := io.rs1_data
    io.id_ex_reg.rs2_data    := io.rs2_data
    io.id_ex_reg.imm         := imm
    io.id_ex_reg.rs1_addr    := rs1
    io.id_ex_reg.rs2_addr    := rs2
    io.id_ex_reg.rd_addr     := rd
    io.id_ex_reg.alu_op      := ctrl.alu_op
    io.id_ex_reg.alu_src     := ctrl.alu_src
    io.id_ex_reg.mem_read    := ctrl.mem_read
    io.id_ex_reg.mem_write   := ctrl.mem_write
    io.id_ex_reg.mem_size    := ctrl.mem_size
    io.id_ex_reg.mem_unsigned := ctrl.mem_unsigned
    io.id_ex_reg.reg_write   := ctrl.reg_write
    io.id_ex_reg.wb_src      := ctrl.wb_src
    io.id_ex_reg.branch      := ctrl.branch
    io.id_ex_reg.jump        := ctrl.jump
  }
}
