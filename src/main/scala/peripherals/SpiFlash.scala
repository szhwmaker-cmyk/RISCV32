package peripherals

import chisel3._
import chisel3.util._
import bus._

// ============================================================================
// SPI Flash 控制器 - 完整协议实现
// 支持 FAST_READ (0x0B) 命令
// ============================================================================

class SpiFlash extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // SPI Flash 外部引脚
    val sclk = Output(Bool())
    val mosi = Output(Bool())
    val miso = Input(Bool())
    val cs_n = Output(Bool())
  })

  // ========== SPI Flash 状态机 ==========
  val flash_idle :: flash_cmd :: flash_addr :: flash_dummy :: flash_data :: Nil = Enum(5)
  val flash_state = RegInit(flash_idle)

  val spi_clk_cnt = RegInit(0.U(3.W))
  val spi_clk_reg = RegInit(false.B)
  val bit_cnt = RegInit(0.U(6.W))

  val cmd_reg = RegInit(0.U(8.W))
  val addr_reg = RegInit(0.U(24.W))
  val data_out_reg = RegInit(0.U(32.W))

  val miso_sync = RegNext(io.miso, false.B)
  val cs_n_reg = RegInit(true.B)
  val mosi_reg = RegInit(false.B)

  // SPI 时钟生成 (50MHz / 8 = 6.25MHz)
  val spi_div = 4.U
  when(flash_state =/= flash_idle) {
    when(spi_clk_cnt >= spi_div) {
      spi_clk_reg := !spi_clk_reg
      spi_clk_cnt := 0.U
    }.otherwise {
      spi_clk_cnt := spi_clk_cnt + 1.U
    }
  }.otherwise {
    spi_clk_reg := false.B
    spi_clk_cnt := 0.U
  }

  // SPI Flash 状态机
  switch(flash_state) {
    is(flash_idle) {
      cs_n_reg := true.B
      bit_cnt := 0.U
    }

    is(flash_cmd) {  // 发送 FAST_READ 命令 (0x0B)
      cs_n_reg := false.B
      when(spi_clk_cnt === 0.U && spi_clk_reg === false.B) {
        mosi_reg := cmd_reg(7)
        cmd_reg := cmd_reg << 1
      }.elsewhen(spi_clk_cnt === 0.U && spi_clk_reg === true.B) {
        bit_cnt := bit_cnt + 1.U
        when(bit_cnt === 7.U) {
          flash_state := flash_addr
          bit_cnt := 0.U
        }
      }
    }

    is(flash_addr) {  // 发送 24-bit 地址
      when(spi_clk_cnt === 0.U && spi_clk_reg === false.B) {
        mosi_reg := addr_reg(23)
        addr_reg := addr_reg << 1
      }.elsewhen(spi_clk_cnt === 0.U && spi_clk_reg === true.B) {
        bit_cnt := bit_cnt + 1.U
        when(bit_cnt === 23.U) {
          flash_state := flash_dummy
          bit_cnt := 0.U
        }
      }
    }

    is(flash_dummy) {  // 8 个 dummy 时钟
      mosi_reg := false.B
      when(spi_clk_cnt === 0.U && spi_clk_reg === true.B) {
        bit_cnt := bit_cnt + 1.U
        when(bit_cnt === 7.U) {
          flash_state := flash_data
          bit_cnt := 0.U
        }
      }
    }

    is(flash_data) {  // 读取 32-bit 数据
      when(spi_clk_cnt === 0.U && spi_clk_reg === true.B) {
        data_out_reg := Cat(data_out_reg(30, 0), miso_sync)
        bit_cnt := bit_cnt + 1.U
        when(bit_cnt === 31.U) {
          flash_state := flash_idle
        }
      }
    }
  }

  io.sclk := spi_clk_reg
  io.mosi := mosi_reg
  io.cs_n := cs_n_reg

  // ========== Wishbone 接口 ==========
  val wb_req = io.wb.stb_o && io.wb.cyc_o
  val wb_addr = io.wb.adr_o(25, 0)  // 26-bit地址 (64MB)

  // 启动Flash读取
  val start_read = RegNext(wb_req && flash_state === flash_idle, false.B)
  when(start_read) {
    cmd_reg := 0x0B.U  // FAST_READ
    addr_reg := Cat(wb_addr, 0.U(2.W))(23, 0)  // 字地址转字节地址
    flash_state := flash_cmd
  }

  // Wishbone 应答
  val ack_reg = RegNext(flash_state === flash_idle && !start_read && wb_req, false.B)
  io.wb.ack_o := ack_reg
  io.wb.dat_o := data_out_reg
}
