package peripherals

import chisel3._
import chisel3.util._
import common.Config._
import bus._

/**
 * SPI Flash 控制器
 *
 * 支持：
 * 1. 标准 SPI 读取 (Read: 0x03)
 * 2. 快速读取 (Fast Read: 0x0B)
 * 3. XIP 模式（Execute In Place）
 * 4. 用于启动加载
 *
 * 接口：
 * - Wishbone 从设备接口
 * - SPI Flash 物理接口
 */
class SpiFlashIO extends Bundle {
  val sck = Output(Bool()) // SPI Clock
  val cs_n = Output(Bool()) // Chip Select (active low)
  val mosi = Output(Bool()) // Master Out Slave In
  val miso = Input(Bool()) // Master In Slave Out
}

class SpiFlashCtrl extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)
    val spi = new SpiFlashIO
  })

  // 状态机
  val sIdle :: sCommand :: sAddress :: sDummy :: sData :: sDone :: Nil = Enum(6)
  val state = RegInit(sIdle)

  // 寄存器
  val clk_div = RegInit(4.U(8.W)) // 默认 4 分频
  val clk_counter = RegInit(0.U(8.W))
  val bit_counter = RegInit(0.U(6.W))
  val cmd_reg = RegInit(0x0B.U(8.W)) // Fast Read 命令
  val addr_reg = RegInit(0.U(24.W))
  val data_reg = RegInit(0.U(32.W))
  val shift_reg = RegInit(0.U(32.W))

  // SPI 时钟生成
  val spi_clk_en = RegInit(false.B)
  val spi_clk = RegInit(false.B)

  when(clk_counter === 0.U) {
    clk_counter := clk_div
    when(spi_clk_en) {
      spi_clk := !spi_clk
    }
  }.otherwise {
    clk_counter := clk_counter - 1.U
  }

  // SPI 接口默认值
  io.spi.sck := spi_clk
  io.spi.cs_n := (state === sIdle)
  io.spi.mosi := shift_reg(31)

  // Wishbone 总线处理
  val wb_req = io.wb.cyc && io.wb.stb
  val wb_addr = io.wb.adr - SPI_FLASH_BASE.U

  // 状态机
  switch(state) {
    is(sIdle) {
      spi_clk_en := false.B
      spi_clk := false.B

      when(wb_req && !io.wb.we) {
        // 开始读取操作
        addr_reg := wb_addr(23, 0)
        shift_reg := Cat(cmd_reg, 0.U(24.W))
        bit_counter := 0.U
        state := sCommand
        spi_clk_en := true.B
      }
    }

    is(sCommand) {
      // 发送命令字节 (8 bits)
      when(spi_clk && (clk_counter === 0.U)) {
        // 时钟上升沿，移位
        shift_reg := Cat(shift_reg(30, 0), 0.U(1.W))
        bit_counter := bit_counter + 1.U

        when(bit_counter === 7.U) {
          // 命令发送完成，准备发送地址
          shift_reg := Cat(addr_reg, 0.U(8.W))
          bit_counter := 0.U
          state := sAddress
        }
      }
    }

    is(sAddress) {
      // 发送地址 (24 bits)
      when(spi_clk && (clk_counter === 0.U)) {
        shift_reg := Cat(shift_reg(30, 0), 0.U(1.W))
        bit_counter := bit_counter + 1.U

        when(bit_counter === 23.U) {
          // 地址发送完成
          bit_counter := 0.U
          // Fast Read 需要 dummy byte
          when(cmd_reg === 0x0B.U) {
            shift_reg := 0.U
            state := sDummy
          }.otherwise {
            state := sData
          }
        }
      }
    }

    is(sDummy) {
      // Dummy 字节 (8 bits for Fast Read)
      when(spi_clk && (clk_counter === 0.U)) {
        bit_counter := bit_counter + 1.U

        when(bit_counter === 7.U) {
          bit_counter := 0.U
          shift_reg := 0.U
          state := sData
        }
      }
    }

    is(sData) {
      // 接收数据 (32 bits = 4 bytes)
      when(!spi_clk && (clk_counter === 0.U)) {
        // 时钟下降沿，采样数据
        shift_reg := Cat(shift_reg(30, 0), io.spi.miso)
        bit_counter := bit_counter + 1.U

        when(bit_counter === 31.U) {
          // 数据接收完成
          data_reg := Cat(shift_reg(30, 0), io.spi.miso)
          state := sDone
        }
      }
    }

    is(sDone) {
      spi_clk_en := false.B
      state := sIdle
    }
  }

  // Wishbone 响应
  io.wb.dat_r := data_reg
  io.wb.ack := (state === sDone)
  io.wb.err := false.B
  io.wb.rty := false.B
}

/**
 * SPI Flash 模拟器（用于仿真）
 *
 * 模拟一个简单的 SPI Flash 存储器
 */
class SpiFlashSim(size: Int = 1024 * 1024) extends Module {
  val io = IO(new Bundle {
    val spi = Flipped(new SpiFlashIO)
  })

  // 存储器
  val mem = SyncReadMem(size / 4, UInt(32.W))

  // 状态机
  val sIdle :: sCommand :: sAddress :: sDummy :: sData :: Nil = Enum(5)
  val state = RegInit(sIdle)

  val shift_in = RegInit(0.U(32.W))
  val shift_out = RegInit(0.U(32.W))
  val bit_counter = RegInit(0.U(8.W))
  val cmd = RegInit(0.U(8.W))
  val addr = RegInit(0.U(24.W))

  // 检测 CS 下降沿
  val cs_n_prev = RegNext(io.spi.cs_n, true.B)
  val cs_falling = cs_n_prev && !io.spi.cs_n
  val cs_rising = !cs_n_prev && io.spi.cs_n

  // 检测 SCK 上升沿
  val sck_prev = RegNext(io.spi.sck, false.B)
  val sck_rising = !sck_prev && io.spi.sck
  val sck_falling = sck_prev && !io.spi.sck

  when(cs_falling) {
    state := sCommand
    bit_counter := 0.U
    shift_in := 0.U
  }

  when(cs_rising) {
    state := sIdle
  }

  when(!io.spi.cs_n) {
    switch(state) {
      is(sCommand) {
        when(sck_rising) {
          shift_in := Cat(shift_in(30, 0), io.spi.mosi)
          bit_counter := bit_counter + 1.U

          when(bit_counter === 7.U) {
            cmd := Cat(shift_in(6, 0), io.spi.mosi)
            bit_counter := 0.U
            state := sAddress
          }
        }
      }

      is(sAddress) {
        when(sck_rising) {
          shift_in := Cat(shift_in(30, 0), io.spi.mosi)
          bit_counter := bit_counter + 1.U

          when(bit_counter === 23.U) {
            addr := Cat(shift_in(22, 0), io.spi.mosi)
            bit_counter := 0.U
            // 判断是否需要 dummy cycle
            when(cmd === 0x0B.U) {
              state := sDummy
            }.otherwise {
              state := sData
              shift_out := mem.read((Cat(shift_in(22, 0), io.spi.mosi)) >> 2)
            }
          }
        }
      }

      is(sDummy) {
        when(sck_rising) {
          bit_counter := bit_counter + 1.U

          when(bit_counter === 7.U) {
            bit_counter := 0.U
            state := sData
            shift_out := mem.read(addr >> 2)
          }
        }
      }

      is(sData) {
        when(sck_falling) {
          shift_out := Cat(shift_out(30, 0), 0.U(1.W))
          bit_counter := bit_counter + 1.U

          when(bit_counter === 31.U) {
            bit_counter := 0.U
            // 连续读取下一个字
            addr := addr + 4.U
            shift_out := mem.read((addr + 4.U) >> 2)
          }
        }
      }
    }
  }

  io.spi.miso := shift_out(31)
}
