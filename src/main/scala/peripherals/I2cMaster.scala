package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * I2C Master 控制器
 *
 * 简化实现
 */
class I2cMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // I2C 物理接口
    val scl_o = Output(Bool())
    val scl_i = Input(Bool())
    val sda_o = Output(Bool())
    val sda_i = Input(Bool())
  })

  // 寄存器
  val data_reg   = RegInit(0.U(8.W))
  val addr_reg   = RegInit(0.U(7.W))
  val ctrl_reg   = RegInit(0.U(8.W))
  val div_reg    = RegInit(500.U(16.W))  // 100kHz @ 50MHz
  val status_reg = RegInit(0.U(8.W))

  // 地址译码
  val addr_offset = io.wb.adr_o - I2C_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(I2cRegs.DATA.U)   { data_reg   := io.wb.dat_o(7, 0) }
      is(I2cRegs.ADDR.U)   { addr_reg   := io.wb.dat_o(6, 0) }
      is(I2cRegs.CTRL.U)   { ctrl_reg   := io.wb.dat_o(7, 0) }
      is(I2cRegs.DIV.U)    { div_reg    := io.wb.dat_o(15, 0) }
    }
  }

  // 读操作
  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    I2cRegs.DATA.U   -> data_reg,
    I2cRegs.ADDR.U   -> addr_reg,
    I2cRegs.CTRL.U   -> ctrl_reg,
    I2cRegs.DIV.U    -> div_reg,
    I2cRegs.STATUS.U -> status_reg
  ))

  // 简化：I2C 信号默认值（空闲状态）
  io.scl_o := true.B
  io.sda_o := true.B

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
