package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * SPI Master 控制器 - 完整实现
 *
 * 寄存器映射：
 * 0x00: TXDATA - 发送数据寄存器（写触发传输）
 * 0x04: RXDATA - 接收数据寄存器
 * 0x08: CTRL   - 控制寄存器
 *       [0]: CPOL - 时钟极性 (0=空闲低, 1=空闲高)
 *       [1]: CPHA - 时钟相位 (0=第一边沿采样, 1=第二边沿采样)
 *       [7:4]: data_width - 数据位宽-1 (0=1bit, 7=8bit, 15=16bit)
 * 0x0C: DIV    - 时钟分频寄存器 (SPI_CLK = SYS_CLK / (2 * (DIV + 1)))
 * 0x10: STATUS - 状态寄存器
 *       [0]: busy - 传输忙
 *       [1]: done - 传输完成
 * 0x14: SS     - 片选寄存器 (低有效, bit[3:0]对应4个CS)
 *
 * SPI 模式：
 * - 模式0 (CPOL=0, CPHA=0): 空闲低电平，上升沿采样
 * - 模式1 (CPOL=0, CPHA=1): 空闲低电平，下降沿采样
 * - 模式2 (CPOL=1, CPHA=0): 空闲高电平，下降沿采样
 * - 模式3 (CPOL=1, CPHA=1): 空闲高电平，上升沿采样
 */
class SpiMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // SPI 物理接口
    val spi_clk  = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
    val spi_cs   = Output(UInt(4.W))
  })

  // ============================================================================
  // 寄存器定义
  // ============================================================================

  val ctrl_reg = RegInit(0.U(8.W))  // CPOL=0, CPHA=0, 8-bit
  val div_reg  = RegInit(4.U(16.W)) // 默认分频=4
  val ss_reg   = RegInit("b1111".U(4.W))  // 所有CS默认高（未选中）

  val cpol = ctrl_reg(0)
  val cpha = ctrl_reg(1)
  val data_width = ctrl_reg(7, 4) +& 1.U  // 实际位宽

  // ============================================================================
  // SPI 传输逻辑
  // ============================================================================

  // SPI 状态机
  val spi_idle :: spi_transfer :: spi_done :: Nil = Enum(3)
  val spi_state = RegInit(spi_idle)

  val tx_data_reg = RegInit(0.U(16.W))
  val rx_data_reg = RegInit(0.U(16.W))
  val bit_cnt = RegInit(0.U(5.W))
  val clk_cnt = RegInit(0.U(16.W))
  val spi_clk_reg = RegInit(false.B)
  val spi_mosi_reg = RegInit(false.B)

  // 发送FIFO（简化：单字节缓冲）
  val tx_fifo_valid = RegInit(false.B)
  val tx_fifo_data = RegInit(0.U(16.W))

  val busy = WireDefault(false.B)
  val done = RegInit(false.B)

  // 输出信号
  io.spi_clk  := Mux(cpol, !spi_clk_reg, spi_clk_reg)
  io.spi_mosi := spi_mosi_reg
  io.spi_cs   := ss_reg

  // MISO 输入同步（防止亚稳态）
  val miso_sync = RegInit(VecInit(Seq.fill(2)(false.B)))
  miso_sync(0) := io.spi_miso
  miso_sync(1) := miso_sync(0)
  val miso_in = miso_sync(1)

  // SPI 状态机
  switch(spi_state) {
    is(spi_idle) {
      spi_clk_reg := false.B
      done := false.B
      when(tx_fifo_valid) {
        spi_state := spi_transfer
        tx_data_reg := tx_fifo_data
        tx_fifo_valid := false.B
        bit_cnt := 0.U
        clk_cnt := 0.U
        rx_data_reg := 0.U
      }
    }

    is(spi_transfer) {
      busy := true.B

      // 时钟生成
      when(clk_cnt === div_reg) {
        clk_cnt := 0.U
        spi_clk_reg := !spi_clk_reg

        val is_sample_edge = (cpha === 0.U && spi_clk_reg === false.B) ||
                             (cpha === 1.U && spi_clk_reg === true.B)
        val is_shift_edge  = (cpha === 0.U && spi_clk_reg === true.B) ||
                             (cpha === 1.U && spi_clk_reg === false.B)

        // 在shift边沿输出数据
        when(is_shift_edge) {
          spi_mosi_reg := tx_data_reg(15)
          tx_data_reg := tx_data_reg << 1
        }

        // 在sample边沿采样数据
        when(is_sample_edge) {
          rx_data_reg := Cat(rx_data_reg(14, 0), miso_in)

          // 检查是否完成
          when(bit_cnt === data_width - 1.U) {
            spi_state := spi_done
          }.otherwise {
            bit_cnt := bit_cnt + 1.U
          }
        }

      }.otherwise {
        clk_cnt := clk_cnt + 1.U
      }
    }

    is(spi_done) {
      spi_clk_reg := false.B
      done := true.B
      // 等待一个时钟周期后回到idle
      spi_state := spi_idle
    }
  }

  // ============================================================================
  // Wishbone 总线接口
  // ============================================================================

  val addr_offset = io.wb.adr_o - SPI_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(SpiRegs.TXDATA.U) {
        // 写入发送FIFO并启动传输
        when(!tx_fifo_valid && spi_state === spi_idle) {
          tx_fifo_data := io.wb.dat_o(15, 0)
          tx_fifo_valid := true.B
        }
      }
      is(SpiRegs.CTRL.U) {
        ctrl_reg := io.wb.dat_o(7, 0)
      }
      is(SpiRegs.DIV.U) {
        div_reg := io.wb.dat_o(15, 0)
      }
      is(SpiRegs.SS.U) {
        ss_reg := io.wb.dat_o(3, 0)
      }
    }
  }

  // 读操作
  val status_reg = Cat(0.U(6.W), done, busy)

  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    SpiRegs.TXDATA.U -> tx_fifo_data,
    SpiRegs.RXDATA.U -> rx_data_reg,
    SpiRegs.CTRL.U   -> ctrl_reg,
    SpiRegs.DIV.U    -> div_reg,
    SpiRegs.STATUS.U -> status_reg,
    SpiRegs.SS.U     -> ss_reg
  ))

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
