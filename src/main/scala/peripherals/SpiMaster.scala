package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

// ============================================================================
// SPI Master 外设 - 完整协议实现
// ============================================================================

class SpiMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // SPI 外部引脚
    val sclk = Output(Bool())
    val mosi = Output(Bool())
    val miso = Input(Bool())
    val cs_n = Output(UInt(4.W))  // 4个片选
  })

  // ========== 寄存器 ==========
  val ctrl_reg   = RegInit(0.U(8.W))   // [7:4]=data_width-1, [3:2]=cs_sel, [1]=cpha, [0]=cpol
  val div_reg    = RegInit(4.U(16.W))  // 时钟分频
  val data_reg   = RegInit(0.U(32.W))
  val status_reg = Wire(UInt(8.W))

  // ========== SPI 状态机 ==========
  val spi_idle :: spi_transfer :: spi_done :: Nil = Enum(3)
  val spi_state = RegInit(spi_idle)

  val cpol = ctrl_reg(0)
  val cpha = ctrl_reg(1)
  val cs_sel = ctrl_reg(3, 2)
  val data_width = ctrl_reg(7, 4) +& 1.U  // 1-16 bits

  val spi_clk_cnt = RegInit(0.U(16.W))
  val spi_bit_cnt = RegInit(0.U(5.W))
  val spi_clk_reg = RegInit(false.B)
  val spi_busy = RegInit(false.B)

  val tx_shift_reg = RegInit(0.U(32.W))
  val rx_shift_reg = RegInit(0.U(32.W))
  val miso_sync = RegNext(io.miso, false.B)

  // 时钟生成
  when(spi_state === spi_transfer) {
    when(spi_clk_cnt >= div_reg) {
      spi_clk_reg := !spi_clk_reg
      spi_clk_cnt := 0.U
    }.otherwise {
      spi_clk_cnt := spi_clk_cnt + 1.U
    }
  }.otherwise {
    spi_clk_reg := cpol
    spi_clk_cnt := 0.U
  }

  // SPI 状态机
  val is_sample_edge = (cpha === 0.U && spi_clk_reg === false.B) ||
                        (cpha === 1.U && spi_clk_reg === true.B)
  val is_shift_edge  = (cpha === 0.U && spi_clk_reg === true.B) ||
                        (cpha === 1.U && spi_clk_reg === false.B)

  switch(spi_state) {
    is(spi_idle) {
      spi_busy := false.B
      spi_bit_cnt := 0.U
    }
    is(spi_transfer) {
      spi_busy := true.B
      when(spi_clk_cnt === 0.U) {
        when(is_sample_edge) {
          // 采样MISO
          rx_shift_reg := Cat(rx_shift_reg(30, 0), miso_sync)
        }.elsewhen(is_shift_edge) {
          // 移出MOSI
          tx_shift_reg := tx_shift_reg << 1
          spi_bit_cnt := spi_bit_cnt + 1.U
          when(spi_bit_cnt === data_width) {
            spi_state := spi_done
          }
        }
      }
    }
    is(spi_done) {
      data_reg := rx_shift_reg
      spi_state := spi_idle
    }
  }

  // SPI 输出
  io.sclk := spi_clk_reg
  io.mosi := tx_shift_reg(31)
  io.cs_n := ~(1.U << cs_sel) | Mux(spi_busy, 0.U, "b1111".U)

  // 状态寄存器
  val spi_done_flag = (spi_state === spi_done)
  status_reg := Cat(Fill(6, 0.U), spi_done_flag, spi_busy)

  // ========== Wishbone 接口 ==========
  val ack_reg = RegNext(io.wb.stb_o && io.wb.cyc_o, false.B)
  io.wb.ack_o := ack_reg

  val reg_addr = io.wb.adr_o(7, 0)

  // 读操作
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    SpiReg.CTRL.U   -> ctrl_reg,
    SpiReg.DIV.U    -> div_reg,
    SpiReg.DATA.U   -> data_reg,
    SpiReg.STATUS.U -> status_reg
  ))

  // 写操作
  when(io.wb.stb_o && io.wb.cyc_o && io.wb.we_o) {
    when(reg_addr === SpiReg.CTRL.U) {
      ctrl_reg := io.wb.dat_o(7, 0)
    }.elsewhen(reg_addr === SpiReg.DIV.U) {
      div_reg := io.wb.dat_o(15, 0)
    }.elsewhen(reg_addr === SpiReg.DATA.U && !spi_busy) {
      data_reg := io.wb.dat_o
      tx_shift_reg := io.wb.dat_o
      spi_state := spi_transfer
    }
  }
}
