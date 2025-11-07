package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * I2C Master 简化版本
 * 支持基本的I2C主模式传输
 */
class I2CMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val scl_out = Output(Bool())
    val scl_in = Input(Bool())
    val sda_out = Output(Bool())
    val sda_in = Input(Bool())
  })

  // 寄存器
  val data = RegInit(0.U(32.W))
  val addr = RegInit(0.U(8.W))
  val ctrl = RegInit(0.U(32.W))
  val divisor = RegInit(250.U(16.W))
  val status = RegInit(0.U(32.W))

  // Wishbone 接口（简化）
  io.wb.dat_r := 0.U
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B

  // I2C 信号（简化）
  io.scl_out := true.B
  io.sda_out := true.B
}
