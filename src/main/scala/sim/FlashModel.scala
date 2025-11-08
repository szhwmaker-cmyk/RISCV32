package sim

import chisel3._
import chisel3.util._

// ============================================================================
// SPI Flash 仿真模型
// 模拟外部 SPI Flash 芯片行为
// ============================================================================

class FlashModel(capacity: Int = 1024 * 1024, initData: Option[Seq[Int]] = None) extends Module {  // 默认 1MB
  val io = IO(new Bundle {
    val sclk = Input(Bool())
    val mosi = Input(Bool())
    val miso = Output(Bool())
    val cs_n = Input(Bool())
  })

  // Flash 存储器
  val flash_mem = SyncReadMem(capacity / 4, UInt(32.W))

  // 初始化存储器（如果提供了初始数据）
  if (initData.isDefined) {
    val data = initData.get
    // 注意：SyncReadMem 的初始化在仿真时通过 loadMemoryFromFileInline 实现
    // 这里我们将数据存储为常量，在测试中使用 poke
  }

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

}

// ============================================================================
// FlashModel 辅助对象
// ============================================================================
object FlashModel {
  /**
   * 生成简单的启动程序
   * 该程序会：
   * 1. 设置栈指针到 RAM 顶部
   * 2. 向 UART 发送启动消息
   * 3. 进入无限循环
   */
  def generateBootProgram(): Seq[BigInt] = {
    // RV32E 启动代码（机器码）
    Seq(
      // 0x10000000: lui sp, 0x80040      # sp = 0x80040000 (RAM 顶部 256KB)
      BigInt("80040137", 16),

      // 0x10000004: lui a0, 0x20000      # a0 = 0x20000000 (UART 基地址)
      BigInt("20000537", 16),

      // 0x10000008: addi a1, zero, 72    # a1 = 'H' (0x48)
      BigInt("04800593", 16),

      // 0x1000000C: sw a1, 0(a0)         # UART_TXDATA = 'H'
      BigInt("00b52023", 16),

      // 0x10000010: addi a1, zero, 73    # a1 = 'I' (0x49)
      BigInt("04900593", 16),

      // 0x10000014: sw a1, 0(a0)         # UART_TXDATA = 'I'
      BigInt("00b52023", 16),

      // 0x10000018: addi a1, zero, 10    # a1 = '\n' (0x0A)
      BigInt("00a00593", 16),

      // 0x1000001C: sw a1, 0(a0)         # UART_TXDATA = '\n'
      BigInt("00b52023", 16),

      // 0x10000020: addi a1, zero, 82    # a1 = 'R' (0x52)
      BigInt("05200593", 16),

      // 0x10000024: sw a1, 0(a0)         # UART_TXDATA = 'R'
      BigInt("00b52023", 16),

      // 0x10000028: addi a1, zero, 84    # a1 = 'T' (0x54)
      BigInt("05400593", 16),

      // 0x1000002C: sw a1, 0(a0)         # UART_TXDATA = 'T'
      BigInt("00b52023", 16),

      // 0x10000030: addi a1, zero, 10    # a1 = '\n' (0x0A)
      BigInt("00a00593", 16),

      // 0x10000034: sw a1, 0(a0)         # UART_TXDATA = '\n'
      BigInt("00b52023", 16),

      // 0x10000038: jal zero, 0          # 无限循环
      BigInt("0000006f", 16)
    )
  }

  /**
   * 生成完整的 RT-Thread 启动程序
   * 输出: "RT-Thread Starting...\n"
   */
  def generateRTThreadBootProgram(): Seq[BigInt] = {
    val message = "RT-Thread Boot\n"
    val instructions = scala.collection.mutable.ArrayBuffer[BigInt]()

    // 设置栈指针: lui sp, 0x80040
    instructions += BigInt("80040137", 16)

    // 设置 UART 基地址: lui a0, 0x20000
    instructions += BigInt("20000537", 16)

    // 为每个字符生成 UART 输出代码
    for (ch <- message) {
      val ascii = ch.toInt
      // addi a1, zero, ascii
      val addi_inst = (ascii << 20) | (0 << 15) | (0 << 12) | (11 << 7) | 0x13
      instructions += BigInt(addi_inst)

      // sw a1, 0(a0)
      instructions += BigInt("00b52023", 16)
    }

    // 无限循环: jal zero, 0
    instructions += BigInt("0000006f", 16)

    instructions.toSeq
  }
}
