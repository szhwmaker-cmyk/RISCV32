package sim

import chisel3._
import chisel3.util._

// ============================================================================
// SPI Flash 仿真模型
// 模拟外部 SPI Flash 芯片行为
// ============================================================================

class FlashModel(capacity: Int = 1024 * 1024) extends Module {  // 默认 1MB
  val io = IO(new Bundle {
    val sclk = Input(Bool())
    val mosi = Input(Bool())
    val miso = Output(Bool())
    val cs_n = Input(Bool())
  })

  // Flash 存储器
  val flash_mem = SyncReadMem(capacity / 4, UInt(32.W))

  // 状态机
  val idle :: cmd :: addr :: dummy :: data :: Nil = Enum(5)
  val state = RegInit(idle)

  val sclk_prev = RegNext(io.sclk, false.B)
  val pos_edge = !sclk_prev && io.sclk
  val neg_edge = sclk_prev && !io.sclk

  val cmd_reg = RegInit(0.U(8.W))
  val addr_reg = RegInit(0.U(24.W))
  val data_reg = RegInit(0.U(32.W))
  val bit_cnt = RegInit(0.U(6.W))
  val miso_reg = RegInit(false.B)

  io.miso := miso_reg

  // 片选无效时复位
  when(io.cs_n) {
    state := idle
    bit_cnt := 0.U
    miso_reg := false.B
  }.otherwise {
    // SPI 时序 (Mode 0: CPOL=0, CPHA=0)
    switch(state) {
      is(idle) {
        when(pos_edge) {
          state := cmd
          bit_cnt := 0.U
        }
      }

      is(cmd) {  // 接收命令
        when(pos_edge) {
          cmd_reg := Cat(cmd_reg(6, 0), io.mosi)
          bit_cnt := bit_cnt + 1.U
          when(bit_cnt === 7.U) {
            state := addr
            bit_cnt := 0.U
          }
        }
      }

      is(addr) {  // 接收地址
        when(pos_edge) {
          addr_reg := Cat(addr_reg(22, 0), io.mosi)
          bit_cnt := bit_cnt + 1.U
          when(bit_cnt === 23.U) {
            when(cmd_reg === 0x0B.U) {  // FAST_READ
              state := dummy
              bit_cnt := 0.U
            }.otherwise {
              state := data
              bit_cnt := 0.U
            }
          }
        }
      }

      is(dummy) {  // Dummy 字节
        when(pos_edge) {
          bit_cnt := bit_cnt + 1.U
          when(bit_cnt === 7.U) {
            state := data
            bit_cnt := 0.U
            // 从Flash读取数据
            data_reg := flash_mem.read(addr_reg(23, 2))
          }
        }
      }

      is(data) {  // 输出数据
        when(neg_edge) {
          miso_reg := data_reg(31)
          data_reg := data_reg << 1
          bit_cnt := bit_cnt + 1.U
          when(bit_cnt === 31.U) {
            // 读取下一个字
            addr_reg := addr_reg + 4.U
            data_reg := flash_mem.read((addr_reg + 4.U)(23, 2))
            bit_cnt := 0.U
          }
        }
      }
    }
  }

  // 提供写入接口用于测试初始化
  def loadProgram(addr: Int, data: Seq[Int]): Unit = {
    // 这个方法在测试中通过peek/poke实现
  }
}
