package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * I2C 主控制器
 *
 * 支持：
 * - 标准模式 (100 kHz)
 * - 快速模式 (400 kHz)
 * - 7 位地址
 * - 读写操作
 *
 * 寄存器映射：
 * 0x00: DATA - 数据寄存器（读写）
 * 0x04: ADDR - 从设备地址寄存器（写）[7:1]: 地址, [0]: R/W (0=写, 1=读)
 * 0x08: CTRL - 控制寄存器（写）
 *       [0]: start - 发送起始条件
 *       [1]: stop - 发送停止条件
 *       [2]: read - 读操作
 *       [3]: write - 写操作
 *       [4]: ack - 应答位
 * 0x0C: DIV - 时钟分频寄存器（读写）
 * 0x10: STATUS - 状态寄存器（读）
 *       [0]: busy - 总线忙
 *       [1]: ack_received - 收到应答
 *       [2]: arb_lost - 仲裁丢失
 */
class I2cIO extends Bundle {
  val scl_out = Output(Bool())
  val scl_in = Input(Bool())
  val sda_out = Output(Bool())
  val sda_in = Input(Bool())
}

class I2c extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val i2c = new I2cIO
  })

  // 寄存器
  val data_reg = RegInit(0.U(8.W))
  val addr_reg = RegInit(0.U(8.W))
  val clk_div = RegInit((SYS_CLK_FREQ / (100000 * 4)).U(16.W)) // 默认 100 kHz
  val busy = RegInit(false.B)
  val ack_received = RegInit(false.B)

  // 状态机
  val sIdle :: sStart :: sAddress :: sData :: sAck :: sStop :: Nil = Enum(6)
  val state = RegInit(sIdle)

  // 时钟分频计数器
  val clk_counter = RegInit(0.U(16.W))
  val clk_tick = (clk_counter === 0.U)

  when(state =/= sIdle) {
    when(clk_counter === 0.U) {
      clk_counter := clk_div - 1.U
    }.otherwise {
      clk_counter := clk_counter - 1.U
    }
  }

  // I2C 时钟和数据线控制
  val scl_reg = RegInit(true.B)
  val sda_reg = RegInit(true.B)
  val bit_counter = RegInit(0.U(4.W))
  val shift_reg = RegInit(0.U(8.W))

  io.i2c.scl_out := scl_reg
  io.i2c.sda_out := sda_reg

  // Wishbone 总线处理
  val wb_addr = io.wb.adr(7, 0)
  val wb_write = io.wb.cyc && io.wb.stb && io.wb.we
  val wb_read = io.wb.cyc && io.wb.stb && !io.wb.we

  // 命令触发
  val cmd_start = WireDefault(false.B)
  val cmd_stop = WireDefault(false.B)
  val cmd_write = WireDefault(false.B)
  val cmd_read = WireDefault(false.B)

  // 写操作
  when(wb_write) {
    switch(wb_addr) {
      is(I2cRegs.DATA.U) {
        data_reg := io.wb.dat_w(7, 0)
      }
      is(I2cRegs.ADDR.U) {
        addr_reg := io.wb.dat_w(7, 0)
      }
      is(I2cRegs.CTRL.U) {
        cmd_start := io.wb.dat_w(0)
        cmd_stop := io.wb.dat_w(1)
        cmd_read := io.wb.dat_w(2)
        cmd_write := io.wb.dat_w(3)
      }
      is(I2cRegs.DIV.U) {
        clk_div := io.wb.dat_w(15, 0)
      }
    }
  }

  // 状态机
  switch(state) {
    is(sIdle) {
      scl_reg := true.B
      sda_reg := true.B
      busy := false.B

      when(cmd_start) {
        busy := true.B
        state := sStart
        clk_counter := clk_div - 1.U
      }
    }

    is(sStart) {
      // 起始条件：SCL 高时，SDA 从高到低
      when(clk_tick) {
        scl_reg := true.B
        sda_reg := false.B
        bit_counter := 0.U
        shift_reg := addr_reg
        state := sAddress
      }
    }

    is(sAddress) {
      // 发送地址（7 位地址 + R/W 位）
      when(clk_tick) {
        when(scl_reg) {
          // SCL 高电平期间保持数据稳定
          scl_reg := false.B
        }.otherwise {
          // SCL 低电平期间改变数据
          sda_reg := shift_reg(7)
          shift_reg := Cat(shift_reg(6, 0), 0.U(1.W))
          scl_reg := true.B
          bit_counter := bit_counter + 1.U

          when(bit_counter === 7.U) {
            state := sAck
            bit_counter := 0.U
          }
        }
      }
    }

    is(sData) {
      // 发送或接收数据
      when(clk_tick) {
        when(scl_reg) {
          scl_reg := false.B
          // SCL 低电平时采样 SDA（读操作）
          when(addr_reg(0) === 1.U) {
            shift_reg := Cat(shift_reg(6, 0), io.i2c.sda_in)
          }
        }.otherwise {
          // SCL 高电平时改变 SDA（写操作）
          when(addr_reg(0) === 0.U) {
            sda_reg := shift_reg(7)
            shift_reg := Cat(shift_reg(6, 0), 0.U(1.W))
          }
          scl_reg := true.B
          bit_counter := bit_counter + 1.U

          when(bit_counter === 7.U) {
            state := sAck
            bit_counter := 0.U
            when(addr_reg(0) === 1.U) {
              data_reg := Cat(shift_reg(6, 0), io.i2c.sda_in)
            }
          }
        }
      }
    }

    is(sAck) {
      // 应答位
      when(clk_tick) {
        when(scl_reg) {
          scl_reg := false.B
          // 采样应答位
          ack_received := !io.i2c.sda_in
        }.otherwise {
          // 主机接收时需要发送应答
          when(addr_reg(0) === 1.U) {
            sda_reg := false.B // ACK
          }.otherwise {
            sda_reg := true.B // Release SDA
          }
          scl_reg := true.B

          // 检查是否有后续操作
          when(cmd_write) {
            shift_reg := data_reg
            state := sData
          }.elsewhen(cmd_read) {
            state := sData
          }.elsewhen(cmd_stop) {
            state := sStop
          }.otherwise {
            state := sIdle
          }
        }
      }
    }

    is(sStop) {
      // 停止条件：SCL 高时，SDA 从低到高
      when(clk_tick) {
        when(!scl_reg) {
          scl_reg := true.B
          sda_reg := false.B
        }.otherwise {
          sda_reg := true.B
          state := sIdle
        }
      }
    }
  }

  // 读操作
  val read_data = MuxLookup(wb_addr, 0.U)(
    Seq(
      I2cRegs.DATA.U -> Cat(Fill(24, 0.U), data_reg),
      I2cRegs.DIV.U -> Cat(Fill(16, 0.U), clk_div),
      I2cRegs.STATUS.U -> Cat(Fill(29, 0.U), false.B, ack_received, busy)
    )
  )

  io.wb.dat_r := read_data
  io.wb.ack := io.wb.cyc && io.wb.stb
  io.wb.err := false.B
  io.wb.rty := false.B
}
