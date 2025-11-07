package core

import chisel3._
import chisel3.util._
import common.Config._

/**
 * 控制信号Bundle
 */
class ControlSignals extends Bundle {
  val alu_op = UInt(4.W)        // ALU 操作码
  val alu_src1 = UInt(2.W)      // ALU 源1选择 (0:rs1, 1:PC)
  val alu_src2 = UInt(2.W)      // ALU 源2选择 (0:rs2, 1:imm, 2:4)
  val mem_read = Bool()          // 内存读使能
  val mem_write = Bool()         // 内存写使能
  val mem_width = UInt(2.W)      // 内存访问宽度 (0:byte, 1:half, 2:word)
  val mem_unsigned = Bool()      // 无符号加载
  val branch = Bool()            // 分支指令
  val jump = Bool()              // 跳转指令
  val reg_write = Bool()         // 寄存器写使能
  val wb_sel = UInt(2.W)         // 写回数据选择 (0:alu, 1:mem, 2:pc+4)
  val csr_cmd = UInt(3.W)        // CSR 命令
  val illegal = Bool()           // 非法指令
}

/**
 * RV32E 指令译码器
 * 将 32 位指令译码为控制信号和立即数
 */
class Decoder extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))
    val ctrl = Output(new ControlSignals)
    val rs1 = Output(UInt(REG_ADDR_WIDTH.W))
    val rs2 = Output(UInt(REG_ADDR_WIDTH.W))
    val rd = Output(UInt(REG_ADDR_WIDTH.W))
    val imm = Output(UInt(XLEN.W))
  })

  // ============================================================================
  // 指令字段提取
  // ============================================================================

  val opcode = io.inst(6, 0)
  val rd = io.inst(11, 7)
  val funct3 = io.inst(14, 12)
  val rs1 = io.inst(19, 15)
  val rs2 = io.inst(24, 20)
  val funct7 = io.inst(31, 25)

  // ============================================================================
  // 立即数生成
  // ============================================================================

  val imm_i = io.inst(31, 20).asSInt.pad(XLEN).asUInt  // I-type
  val imm_s = Cat(io.inst(31, 25), io.inst(11, 7)).asSInt.pad(XLEN).asUInt  // S-type
  val imm_b = Cat(io.inst(31), io.inst(7), io.inst(30, 25), io.inst(11, 8), 0.U(1.W)).asSInt.pad(XLEN).asUInt  // B-type
  val imm_u = Cat(io.inst(31, 12), 0.U(12.W))  // U-type
  val imm_j = Cat(io.inst(31), io.inst(19, 12), io.inst(20), io.inst(30, 21), 0.U(1.W)).asSInt.pad(XLEN).asUInt  // J-type

  // ============================================================================
  // 默认控制信号
  // ============================================================================

  val ctrl = Wire(new ControlSignals)
  ctrl.alu_op := AluOp.ADD
  ctrl.alu_src1 := 0.U
  ctrl.alu_src2 := 0.U
  ctrl.mem_read := false.B
  ctrl.mem_write := false.B
  ctrl.mem_width := 2.U
  ctrl.mem_unsigned := false.B
  ctrl.branch := false.B
  ctrl.jump := false.B
  ctrl.reg_write := false.B
  ctrl.wb_sel := 0.U
  ctrl.csr_cmd := 0.U
  ctrl.illegal := false.B

  io.rs1 := rs1(REG_ADDR_WIDTH - 1, 0)
  io.rs2 := rs2(REG_ADDR_WIDTH - 1, 0)
  io.rd := rd(REG_ADDR_WIDTH - 1, 0)
  io.imm := 0.U

  // ============================================================================
  // 指令译码逻辑
  // ============================================================================

  switch(opcode) {
    // ========================================================================
    // OP-IMM (0010011): 立即数算术/逻辑指令
    // ========================================================================
    is("b0010011".U) {
      ctrl.alu_src2 := 1.U  // 使用立即数
      ctrl.reg_write := true.B
      ctrl.wb_sel := 0.U  // 写回ALU结果
      io.imm := imm_i

      switch(funct3) {
        is(0.U) { ctrl.alu_op := AluOp.ADD }  // ADDI
        is(2.U) { ctrl.alu_op := AluOp.SLT }  // SLTI
        is(3.U) { ctrl.alu_op := AluOp.SLTU } // SLTIU
        is(4.U) { ctrl.alu_op := AluOp.XOR }  // XORI
        is(6.U) { ctrl.alu_op := AluOp.OR }   // ORI
        is(7.U) { ctrl.alu_op := AluOp.AND }  // ANDI
        is(1.U) { ctrl.alu_op := AluOp.SLL }  // SLLI
        is(5.U) {
          when(funct7 === 0.U) {
            ctrl.alu_op := AluOp.SRL  // SRLI
          }.otherwise {
            ctrl.alu_op := AluOp.SRA  // SRAI
          }
        }
      }
    }

    // ========================================================================
    // OP (0110011): 寄存器算术/逻辑指令
    // ========================================================================
    is("b0110011".U) {
      ctrl.alu_src2 := 0.U  // 使用rs2
      ctrl.reg_write := true.B
      ctrl.wb_sel := 0.U

      when(funct7 === 1.U) {
        // M 扩展：乘除法指令
        switch(funct3) {
          is(0.U) { ctrl.alu_op := AluOp.MUL }  // MUL
          is(4.U) { ctrl.alu_op := AluOp.DIV }  // DIV
          is(6.U) { ctrl.alu_op := AluOp.REM }  // REM
        }
      }.otherwise {
        switch(funct3) {
          is(0.U) {
            when(funct7 === 0.U) {
              ctrl.alu_op := AluOp.ADD  // ADD
            }.otherwise {
              ctrl.alu_op := AluOp.SUB  // SUB
            }
          }
          is(1.U) { ctrl.alu_op := AluOp.SLL }  // SLL
          is(2.U) { ctrl.alu_op := AluOp.SLT }  // SLT
          is(3.U) { ctrl.alu_op := AluOp.SLTU } // SLTU
          is(4.U) { ctrl.alu_op := AluOp.XOR }  // XOR
          is(5.U) {
            when(funct7 === 0.U) {
              ctrl.alu_op := AluOp.SRL  // SRL
            }.otherwise {
              ctrl.alu_op := AluOp.SRA  // SRA
            }
          }
          is(6.U) { ctrl.alu_op := AluOp.OR }   // OR
          is(7.U) { ctrl.alu_op := AluOp.AND }  // AND
        }
      }
    }

    // ========================================================================
    // LOAD (0000011): 加载指令
    // ========================================================================
    is("b0000011".U) {
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src2 := 1.U  // 使用立即数
      ctrl.mem_read := true.B
      ctrl.reg_write := true.B
      ctrl.wb_sel := 1.U  // 写回内存数据
      io.imm := imm_i

      switch(funct3) {
        is(0.U) { ctrl.mem_width := 0.U; ctrl.mem_unsigned := false.B }  // LB
        is(1.U) { ctrl.mem_width := 1.U; ctrl.mem_unsigned := false.B }  // LH
        is(2.U) { ctrl.mem_width := 2.U; ctrl.mem_unsigned := false.B }  // LW
        is(4.U) { ctrl.mem_width := 0.U; ctrl.mem_unsigned := true.B }   // LBU
        is(5.U) { ctrl.mem_width := 1.U; ctrl.mem_unsigned := true.B }   // LHU
      }
    }

    // ========================================================================
    // STORE (0100011): 存储指令
    // ========================================================================
    is("b0100011".U) {
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src2 := 1.U  // 使用立即数
      ctrl.mem_write := true.B
      io.imm := imm_s

      switch(funct3) {
        is(0.U) { ctrl.mem_width := 0.U }  // SB
        is(1.U) { ctrl.mem_width := 1.U }  // SH
        is(2.U) { ctrl.mem_width := 2.U }  // SW
      }
    }

    // ========================================================================
    // BRANCH (1100011): 分支指令
    // ========================================================================
    is("b1100011".U) {
      ctrl.branch := true.B
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := AluOp.ADD
      io.imm := imm_b
    }

    // ========================================================================
    // JAL (1101111): 无条件跳转并链接
    // ========================================================================
    is("b1101111".U) {
      ctrl.jump := true.B
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := AluOp.ADD
      ctrl.reg_write := true.B
      ctrl.wb_sel := 2.U  // PC+4
      io.imm := imm_j
    }

    // ========================================================================
    // JALR (1100111): 间接跳转并链接
    // ========================================================================
    is("b1100111".U) {
      ctrl.jump := true.B
      ctrl.alu_src1 := 0.U  // rs1
      ctrl.alu_src2 := 1.U  // imm
      ctrl.alu_op := AluOp.ADD
      ctrl.reg_write := true.B
      ctrl.wb_sel := 2.U  // PC+4
      io.imm := imm_i
    }

    // ========================================================================
    // LUI (0110111): 加载高位立即数
    // ========================================================================
    is("b0110111".U) {
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 0.U
      ctrl.alu_src2 := 1.U  // imm
      ctrl.reg_write := true.B
      ctrl.wb_sel := 0.U
      io.imm := imm_u
      io.rs1 := 0.U  // 强制 rs1 = x0
    }

    // ========================================================================
    // AUIPC (0010111): PC + 高位立即数
    // ========================================================================
    is("b0010111".U) {
      ctrl.alu_op := AluOp.ADD
      ctrl.alu_src1 := 1.U  // PC
      ctrl.alu_src2 := 1.U  // imm
      ctrl.reg_write := true.B
      ctrl.wb_sel := 0.U
      io.imm := imm_u
    }

    // ========================================================================
    // SYSTEM (1110011): 系统指令和CSR
    // ========================================================================
    is("b1110011".U) {
      when(funct3 === 0.U) {
        // ECALL, EBREAK, MRET, WFI
        when(io.inst === "h00000073".U) {
          // ECALL
          ctrl.illegal := false.B
        }.elsewhen(io.inst === "h00100073".U) {
          // EBREAK
          ctrl.illegal := false.B
        }.elsewhen(io.inst === "h30200073".U) {
          // MRET
          ctrl.illegal := false.B
        }.otherwise {
          ctrl.illegal := true.B
        }
      }.otherwise {
        // CSR 指令
        ctrl.csr_cmd := funct3
        ctrl.reg_write := true.B
        io.imm := imm_i
      }
    }

    // ========================================================================
    // FENCE (0001111): 内存栅栏 (暂时NOP)
    // ========================================================================
    is("b0001111".U) {
      // 简化处理：当作 NOP
      ctrl.alu_op := AluOp.NOP
    }
  }

  io.ctrl := ctrl
}
