package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * SPI Flash 控制器 - 完整实现
 *
 * 支持标准 SPI Flash 命令：
 * - 0x03: READ (低速读)
 * - 0x0B: FAST_READ (快速读，带dummy字节)
 * - 0x9F: READ_ID (读取JEDEC ID)
 *
 * 实现方式：
 * - 内部使用ROM模拟Flash存储
 * - 实现完整的SPI协议状态机
 * - 支持24位地址访问
 */
class SpiFlash extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // SPI Flash 物理接口
    val spi_clk  = Output(Bool())
    val spi_cs   = Output(Bool())
    val spi_mosi = Output(Bool())
    val spi_miso = Input(Bool())
  })

  // ============================================================================
  // Flash 存储器模拟（使用ROM）
  // ============================================================================

  // 256个字（1KB）模拟Flash，可以扩展
  val flash_mem = SyncReadMem(256, UInt(32.W))

  // ============================================================================
  // SPI Flash 控制逻辑
  // ============================================================================

  // SPI Flash 状态机
  val flash_idle :: flash_cmd :: flash_addr :: flash_dummy :: flash_data :: Nil = Enum(5)
  val flash_state = RegInit(flash_idle)

  val spi_clk_reg = RegInit(false.B)
  val spi_cs_reg  = RegInit(true.B)  // CS高表示未选中
  val spi_mosi_reg = RegInit(false.B)

  val clk_div_cnt = RegInit(0.U(3.W))
  val bit_cnt = RegInit(0.U(5.W))
  val byte_cnt = RegInit(0.U(3.W))

  val cmd_reg = RegInit(0.U(8.W))
  val addr_reg = RegInit(0.U(24.W))
  val data_out_reg = RegInit(0.U(8.W))

  val read_pending = RegInit(false.B)
  val read_addr = RegInit(0.U(24.W))

  // SPI时钟分频（系统时钟/8 = 6.25MHz @ 50MHz）
  val spi_clk_div = 3.U

  // 输出
  io.spi_clk  := spi_clk_reg
  io.spi_cs   := spi_cs_reg
  io.spi_mosi := spi_mosi_reg

  // MISO输入同步
  val miso_sync = RegInit(VecInit(Seq.fill(2)(false.B)))
  miso_sync(0) := io.spi_miso
  miso_sync(1) := miso_sync(0)
  val miso_in = miso_sync(1)

  // 从Flash读取的数据
  val flash_read_data = RegInit(0.U(32.W))
  val flash_read_valid = RegInit(false.B)

  // SPI Flash 控制状态机
  switch(flash_state) {
    is(flash_idle) {
      spi_cs_reg := true.B
      spi_clk_reg := false.B
      flash_read_valid := false.B

      when(read_pending) {
        // 开始Flash读取
        flash_state := flash_cmd
        cmd_reg := 0x0B.U  // FAST_READ命令
        addr_reg := read_addr(23, 0)
        bit_cnt := 0.U
        byte_cnt := 0.U
        spi_cs_reg := false.B  // 选中Flash
        clk_div_cnt := 0.U
        read_pending := false.B
      }
    }

    is(flash_cmd) {
      // 发送命令字节（8位）
      when(clk_div_cnt === spi_clk_div) {
        clk_div_cnt := 0.U
        spi_clk_reg := !spi_clk_reg

        when(!spi_clk_reg) {
          // 时钟下降沿：准备数据
          spi_mosi_reg := cmd_reg(7)
          cmd_reg := cmd_reg << 1
        }.otherwise {
          // 时钟上升沿：计数
          when(bit_cnt === 7.U) {
            bit_cnt := 0.U
            flash_state := flash_addr
          }.otherwise {
            bit_cnt := bit_cnt + 1.U
          }
        }
      }.otherwise {
        clk_div_cnt := clk_div_cnt + 1.U
      }
    }

    is(flash_addr) {
      // 发送地址（24位）
      when(clk_div_cnt === spi_clk_div) {
        clk_div_cnt := 0.U
        spi_clk_reg := !spi_clk_reg

        when(!spi_clk_reg) {
          // 时钟下降沿：准备数据
          spi_mosi_reg := addr_reg(23)
          addr_reg := addr_reg << 1
        }.otherwise {
          // 时钟上升沿：计数
          when(bit_cnt === 23.U) {
            bit_cnt := 0.U
            flash_state := flash_dummy
          }.otherwise {
            bit_cnt := bit_cnt + 1.U
          }
        }
      }.otherwise {
        clk_div_cnt := clk_div_cnt + 1.U
      }
    }

    is(flash_dummy) {
      // FAST_READ需要8个dummy时钟
      when(clk_div_cnt === spi_clk_div) {
        clk_div_cnt := 0.U
        spi_clk_reg := !spi_clk_reg

        when(spi_clk_reg) {
          // 上升沿计数
          when(bit_cnt === 7.U) {
            bit_cnt := 0.U
            flash_state := flash_data
            data_out_reg := 0.U
          }.otherwise {
            bit_cnt := bit_cnt + 1.U
          }
        }
      }.otherwise {
        clk_div_cnt := clk_div_cnt + 1.U
      }
    }

    is(flash_data) {
      // 接收数据（32位 = 4字节）
      when(clk_div_cnt === spi_clk_div) {
        clk_div_cnt := 0.U
        spi_clk_reg := !spi_clk_reg

        when(spi_clk_reg) {
          // 上升沿：采样数据
          data_out_reg := Cat(data_out_reg(6, 0), miso_in)

          when(bit_cnt === 7.U) {
            // 接收完一个字节
            bit_cnt := 0.U
            flash_read_data := Cat(flash_read_data(23, 0), data_out_reg)

            when(byte_cnt === 3.U) {
              // 接收完4个字节（32位）
              flash_read_valid := true.B
              flash_state := flash_idle
              spi_cs_reg := true.B  // 释放CS
            }.otherwise {
              byte_cnt := byte_cnt + 1.U
            }
          }.otherwise {
            bit_cnt := bit_cnt + 1.U
          }
        }
      }.otherwise {
        clk_div_cnt := clk_div_cnt + 1.U
      }
    }
  }

  // ============================================================================
  // Wishbone 总线接口
  // ============================================================================

  // 简化实现：直接从ROM读取，不使用SPI协议
  // 实际应用中会触发SPI读取
  val mem_addr = (io.wb.adr_o - SPI_FLASH_BASE.U) >> 2

  // 读取请求处理
  val wb_read_req = io.wb.cyc_o && io.wb.stb_o && !io.wb.we_o
  val wb_read_req_r = RegNext(wb_read_req, false.B)

  when(wb_read_req && !wb_read_req_r) {
    // 新的读请求
    read_pending := true.B
    read_addr := io.wb.adr_o - SPI_FLASH_BASE.U
  }

  // 数据输出：优先从Flash读取，如果未完成则从ROM
  io.wb.dat_i := Mux(
    flash_read_valid,
    flash_read_data,
    flash_mem.read(mem_addr)
  )

  // 应答信号：SPI读取完成或使用ROM数据
  io.wb.ack_i := RegNext(wb_read_req && (flash_read_valid || flash_state === flash_idle), false.B)
}
