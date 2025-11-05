package rv32e.core

import chisel3._
import chisel3.util._

/**
 * ALU Operations (4-bit encoding)
 */
object ALUOp {
  val ADD  = 0.U(4.W)  // Addition
  val SUB  = 1.U(4.W)  // Subtraction
  val SLL  = 2.U(4.W)  // Shift left logical
  val SLT  = 3.U(4.W)  // Set less than (signed)
  val SLTU = 4.U(4.W)  // Set less than unsigned
  val XOR  = 5.U(4.W)  // Bitwise XOR
  val SRL  = 6.U(4.W)  // Shift right logical
  val SRA  = 7.U(4.W)  // Shift right arithmetic
  val OR   = 8.U(4.W)  // Bitwise OR
  val AND  = 9.U(4.W)  // Bitwise AND
  val COPY1 = 10.U(4.W) // Copy operand 1 (for LUI)
  val COPY2 = 11.U(4.W) // Copy operand 2
}

/**
 * Arithmetic Logic Unit
 *
 * Supports all RV32I arithmetic and logical operations:
 * - Arithmetic: ADD, SUB
 * - Comparison: SLT, SLTU
 * - Logical: AND, OR, XOR
 * - Shift: SLL, SRL, SRA
 *
 * Interface:
 * - op: ALU operation selector
 * - src1, src2: 32-bit operands
 * - out: 32-bit result
 * - zero: flag indicating result is zero (for branch conditions)
 */
class ALUIO extends Bundle {
  val op   = Input(UInt(4.W))   // ALU operation
  val src1 = Input(UInt(32.W))  // Operand 1
  val src2 = Input(UInt(32.W))  // Operand 2
  val out  = Output(UInt(32.W)) // Result
  val zero = Output(Bool())      // Result is zero flag
}

class ALU extends Module {
  val io = IO(new ALUIO)

  // Default output
  val result = WireDefault(0.U(32.W))

  // Shift amount (lower 5 bits of src2)
  val shamt = io.src2(4, 0)

  // Signed comparison
  val lt_signed = io.src1.asSInt < io.src2.asSInt

  // Unsigned comparison
  val lt_unsigned = io.src1 < io.src2

  // ========== ALU Operation Selection ==========
  switch(io.op) {
    is(ALUOp.ADD) {
      result := io.src1 + io.src2
    }
    is(ALUOp.SUB) {
      result := io.src1 - io.src2
    }
    is(ALUOp.SLL) {
      result := io.src1 << shamt
    }
    is(ALUOp.SLT) {
      result := Mux(lt_signed, 1.U, 0.U)
    }
    is(ALUOp.SLTU) {
      result := Mux(lt_unsigned, 1.U, 0.U)
    }
    is(ALUOp.XOR) {
      result := io.src1 ^ io.src2
    }
    is(ALUOp.SRL) {
      result := io.src1 >> shamt
    }
    is(ALUOp.SRA) {
      result := (io.src1.asSInt >> shamt).asUInt
    }
    is(ALUOp.OR) {
      result := io.src1 | io.src2
    }
    is(ALUOp.AND) {
      result := io.src1 & io.src2
    }
    is(ALUOp.COPY1) {
      result := io.src1
    }
    is(ALUOp.COPY2) {
      result := io.src2
    }
  }

  // Output assignment
  io.out := result
  io.zero := result === 0.U
}

object ALU extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.core.ALU"))
}
