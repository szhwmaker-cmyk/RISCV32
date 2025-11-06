package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * SPI 主控制器
 *
 * 支持：
 * - 4 种 SPI 模式（CPOL, CPHA）
 * - 可配置时钟分频
 * - 8/16/32 位数据传输
 * - 多个片选信号
 *
 * 寄存器映射：
 * 0x00: TXDATA - 发送数据寄存器（写）
 * 0x04: RXDATA - 接收数据寄存器（读）
 * 0x08: CTRL - 控制寄存器（读写）
 *       [0]: CPOL - 时钟极性
 *       [1]: CPHA - 时钟相位
 *       [3:2]: data_width - 00: 8-bit, 01: 16-bit, 10: 32-bit
 *       [4]: start - 开始传输
 * 0x0C: DIV - 时钟分频寄存器（读写）
 * 0x10: STATUS - 状态寄存器（读）
 *       [0]: busy - 传输进行中
 *       [1]: done - 传输完成
 * 0x14: SS - 片选寄存器（读写）
 *       [3:0]: 片选信号（低电平有效）
 */
class SpiMasterIO extends Bundle {
  val sck = Output(Bool())
  val mosi = Output(Bool())
  val miso = Input(Bool())
  val ss_n = Output(UInt(4.W)) // 支持 4 个片选
}

class SpiMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val spi = new SpiMasterIO
  })

  // 寄存器
  val cpol = RegInit(false.B) // 时钟极性
  val cpha = RegInit(false.B) // 时钟相位
  val data_width = RegInit(0.U(2.W)) // 0: 8-bit, 1: 16-bit, 2: 32-bit
  val clk_div = RegInit(4.U(16.W)) // 默认 4 分频
  val ss_n_reg = RegInit(15.U(4.W)) // 所有片选默认为高（未选中）

  // 传输状态
  val busy = RegInit(false.B)
  val done = RegInit(false.B)

  // 状态机
  val sIdle :: sTransfer :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // 时钟分频计数器
  val clk_counter = RegInit(0.U(16.W))
  val clk_tick = (clk_counter === 0.U)

  when(state === sTransfer) {
    when(clk_counter === 0.U) {
      clk_counter := clk_div - 1.U
    }.otherwise {
      clk_counter := clk_counter - 1.U
    }
  }

  // SPI 时钟
  val sck_reg = RegInit(false.B)

  // 移位寄存器
  val shift_out = RegInit(0.U(32.W))
  val shift_in = RegInit(0.U(32.W))
  val bit_counter = RegInit(0.U(6.W))

  // 传输位数
  val transfer_bits = MuxLookup(data_width, 8.U)(
    Seq(
      0.U -> 8.U,
      1.U -> 16.U,
      2.U -> 32.U
    )
  )

  // SPI 输出
  io.spi.sck := sck_reg ^ cpol // 应用 CPOL
  io.spi.mosi := shift_out(31)
  io.spi.ss_n := ss_n_reg

  // Wishbone 总线处理
  val wb_addr = io.wb.adr(7, 0)
  val wb_write = io.wb.cyc && io.wb.stb && io.wb.we
  val wb_read = io.wb.cyc && io.wb.stb && !io.wb.we

  // 命令触发
  val cmd_start = WireDefault(false.B)

  // 写操作
  when(wb_write) {
    switch(wb_addr) {
      is(SpiRegs.TXDATA.U) {
        shift_out := io.wb.dat_w
      }
      is(SpiRegs.CTRL.U) {
        cpol := io.wb.dat_w(0)
        cpha := io.wb.dat_w(1)
        data_width := io.wb.dat_w(3, 2)
        cmd_start := io.wb.dat_w(4)
      }
      is(SpiRegs.DIV.U) {
        clk_div := io.wb.dat_w(15, 0)
      }
      is(SpiRegs.SS.U) {
        ss_n_reg := io.wb.dat_w(3, 0)
      }
    }
  }

  // 状态机
  switch(state) {
    is(sIdle) {
      busy := false.B
      done := false.B
      sck_reg := false.B
      bit_counter := 0.U

      when(cmd_start) {
        busy := true.B
        clk_counter := clk_div - 1.U
        state := sTransfer
      }
    }

    is(sTransfer) {
      when(clk_tick) {
        // SPI 模式处理
        when(cpha === 0.U) {
          // CPHA = 0: 数据在第一个时钟边沿采样
          when(!sck_reg) {
            // 时钟低电平：改变数据
            shift_out := Cat(shift_out(30, 0), 0.U(1.W))
            sck_reg := true.B
          }.otherwise {
            // 时钟高电平：采样数据
            shift_in := Cat(shift_in(30, 0), io.spi.miso)
            sck_reg := false.B
            bit_counter := bit_counter + 1.U

            when(bit_counter === (transfer_bits - 1.U)) {
              state := sDone
            }
          }
        }.otherwise {
          // CPHA = 1: 数据在第二个时钟边沿采样
          when(!sck_reg) {
            // 时钟低电平：采样数据
            shift_in := Cat(shift_in(30, 0), io.spi.miso)
            sck_reg := true.B
          }.otherwise {
            // 时钟高电平：改变数据
            shift_out := Cat(shift_out(30, 0), 0.U(1.W))
            sck_reg := false.B
            bit_counter := bit_counter + 1.U

            when(bit_counter === (transfer_bits - 1.U)) {
              state := sDone
            }
          }
        }
      }
    }

    is(sDone) {
      busy := false.B
      done := true.B
      sck_reg := false.B
      state := sIdle
    }
  }

  // 读操作
  val read_data = MuxLookup(wb_addr, 0.U)(
    Seq(
      SpiRegs.RXDATA.U -> shift_in,
      SpiRegs.CTRL.U -> Cat(Fill(27, 0.U), false.B, data_width, cpha, cpol),
      SpiRegs.DIV.U -> Cat(Fill(16, 0.U), clk_div),
      SpiRegs.STATUS.U -> Cat(Fill(30, 0.U), done, busy),
      SpiRegs.SS.U -> Cat(Fill(28, 0.U), ss_n_reg)
    )
  )

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B
}
