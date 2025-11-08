package peripherals

import chisel3._
import chisel3.util._
import bus._

/**
 * SPI Master Controller
 * SPI主控制器
 *
 * 特性：
 * - 支持模式0-3（CPOL, CPHA配置）
 * - 可配置时钟分频
 * - 8/16/32位传输
 * - 多从设备片选
 * - Wishbone B4从设备接口
 *
 * 寄存器映射：
 * 0x00: CTRL   - 控制寄存器
 * 0x04: STATUS - 状态寄存器
 * 0x08: DATA   - 数据寄存器
 * 0x0C: DIV    - 时钟分频器
 * 0x10: CS     - 片选寄存器
 */

object SPIRegs {
  val CTRL   = 0x00.U(4.W)
  val STATUS = 0x04.U(4.W)
  val DATA   = 0x08.U(4.W)
  val DIV    = 0x0C.U(4.W)
  val CS     = 0x10.U(4.W)
}

object SPICtrl {
  val START  = 0  // Start transmission
  val CPOL   = 1  // Clock polarity
  val CPHA   = 2  // Clock phase
  val LSB    = 3  // LSB first (0=MSB first)
  val IE     = 4  // Interrupt enable
}

object SPIStatus {
  val BUSY = 0  // Transfer in progress
  val DONE = 1  // Transfer complete
}

/**
 * SPI Master Core
 */
class SPIMasterCore extends Module {
  val io = IO(new Bundle {
    // Control
    val start = Input(Bool())
    val cpol = Input(Bool())
    val cpha = Input(Bool())
    val lsb_first = Input(Bool())
    val clk_div = Input(UInt(16.W))
    val data_len = Input(UInt(6.W))  // 1-32 bits

    // Data
    val tx_data = Input(UInt(32.W))
    val rx_data = Output(UInt(32.W))
    val busy = Output(Bool())
    val done = Output(Bool())

    // SPI pins
    val sclk = Output(Bool())
    val mosi = Output(Bool())
    val miso = Input(Bool())
  })

  // State machine
  val sIdle :: sTransfer :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // Registers
  val clk_counter = RegInit(0.U(16.W))
  val bit_counter = RegInit(0.U(6.W))
  val shift_reg = RegInit(0.U(32.W))
  val rx_shift_reg = RegInit(0.U(32.W))
  val sclk_reg = RegInit(false.B)
  val done_reg = RegInit(false.B)

  // SPI clock generation
  val sclk_edge = Wire(Bool())
  sclk_edge := false.B

  when(state === sTransfer) {
    when(clk_counter === io.clk_div) {
      clk_counter := 0.U
      sclk_reg := ~sclk_reg
      sclk_edge := true.B
    }.otherwise {
      clk_counter := clk_counter + 1.U
    }
  }.otherwise {
    clk_counter := 0.U
    sclk_reg := io.cpol  // Idle state depends on CPOL
  }

  // Determine sample and shift edges based on CPHA
  val sample_edge = Wire(Bool())
  val shift_edge = Wire(Bool())

  when(io.cpha) {
    // CPHA=1: sample on second edge, shift on first edge
    sample_edge := sclk_edge && (sclk_reg === io.cpol)
    shift_edge := sclk_edge && (sclk_reg =/= io.cpol)
  }.otherwise {
    // CPHA=0: sample on first edge, shift on second edge
    sample_edge := sclk_edge && (sclk_reg =/= io.cpol)
    shift_edge := sclk_edge && (sclk_reg === io.cpol)
  }

  // State machine
  done_reg := false.B

  switch(state) {
    is(sIdle) {
      when(io.start) {
        shift_reg := io.tx_data
        bit_counter := 0.U
        state := sTransfer
      }
    }

    is(sTransfer) {
      // Shift out on shift edge
      when(shift_edge) {
        when(io.lsb_first) {
          shift_reg := Cat(0.U(1.W), shift_reg(31, 1))
        }.otherwise {
          shift_reg := Cat(shift_reg(30, 0), 0.U(1.W))
        }
      }

      // Sample in on sample edge
      when(sample_edge) {
        when(io.lsb_first) {
          rx_shift_reg := Cat(io.miso, rx_shift_reg(31, 1))
        }.otherwise {
          rx_shift_reg := Cat(rx_shift_reg(30, 0), io.miso)
        }

        bit_counter := bit_counter + 1.U

        when(bit_counter === io.data_len - 1.U) {
          state := sDone
        }
      }
    }

    is(sDone) {
      done_reg := true.B
      state := sIdle
    }
  }

  // Outputs
  io.sclk := sclk_reg
  io.mosi := Mux(io.lsb_first, shift_reg(0), shift_reg(31))
  io.rx_data := rx_shift_reg
  io.busy := state === sTransfer
  io.done := done_reg
}

/**
 * SPI Master with Wishbone Interface
 */
class SPIMaster(numCS: Int = 4) extends Module {
  val io = IO(new Bundle {
    // Wishbone interface
    val wb = new WishboneSlave()

    // SPI pins
    val sclk = Output(Bool())
    val mosi = Output(Bool())
    val miso = Input(Bool())
    val cs_n = Output(UInt(numCS.W))  // Chip select (active low)

    // Interrupt
    val irq = Output(Bool())
  })

  // SPI core
  val spi_core = Module(new SPIMasterCore())

  // Registers
  val ctrl_reg = RegInit(0.U(32.W))
  val status_reg = Wire(UInt(32.W))
  val data_reg = RegInit(0.U(32.W))
  val div_reg = RegInit(1.U(32.W))
  val cs_reg = RegInit(Fill(numCS, 1.U(1.W)))  // All CS inactive (high)

  // Extract control bits
  val start = ctrl_reg(SPICtrl.START)
  val cpol = ctrl_reg(SPICtrl.CPOL)
  val cpha = ctrl_reg(SPICtrl.CPHA)
  val lsb_first = ctrl_reg(SPICtrl.LSB)
  val irq_en = ctrl_reg(SPICtrl.IE)

  // Connect SPI core
  spi_core.io.start := start
  spi_core.io.cpol := cpol
  spi_core.io.cpha := cpha
  spi_core.io.lsb_first := lsb_first
  spi_core.io.clk_div := div_reg(15, 0)
  spi_core.io.data_len := 8.U  // Default 8-bit transfers
  spi_core.io.tx_data := data_reg
  spi_core.io.miso := io.miso

  // Clear START bit when transfer begins
  when(start && spi_core.io.busy) {
    ctrl_reg := ctrl_reg & ~(1.U << SPICtrl.START)
  }

  // Update data register when done
  when(spi_core.io.done) {
    data_reg := spi_core.io.rx_data
  }

  // SPI pins
  io.sclk := spi_core.io.sclk
  io.mosi := spi_core.io.mosi
  io.cs_n := cs_reg

  // Status register
  status_reg := Cat(
    0.U(30.W),
    spi_core.io.done,
    spi_core.io.busy
  )

  // Wishbone interface
  val ack_reg = RegInit(false.B)
  ack_reg := io.wb.cyc_i && io.wb.stb_i && !ack_reg

  io.wb.ack_o := ack_reg
  io.wb.err_o := false.B
  io.wb.rty_o := false.B

  val reg_addr = io.wb.adr_i(5, 2)

  // Read logic
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    SPIRegs.CTRL   -> ctrl_reg,
    SPIRegs.STATUS -> status_reg,
    SPIRegs.DATA   -> data_reg,
    SPIRegs.DIV    -> div_reg,
    SPIRegs.CS     -> Cat(0.U((32 - numCS).W), cs_reg)
  ))

  // Write logic
  when(io.wb.cyc_i && io.wb.stb_i && io.wb.we_i) {
    switch(reg_addr) {
      is(SPIRegs.CTRL) {
        ctrl_reg := io.wb.dat_i
      }
      is(SPIRegs.DATA) {
        data_reg := io.wb.dat_i
      }
      is(SPIRegs.DIV) {
        div_reg := io.wb.dat_i
      }
      is(SPIRegs.CS) {
        cs_reg := io.wb.dat_i(numCS - 1, 0)
      }
    }
  }

  // Interrupt
  io.irq := irq_en && spi_core.io.done
}
