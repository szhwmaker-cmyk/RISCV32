package peripherals

import chisel3._
import chisel3.util._
import bus._

/**
 * UART (Universal Asynchronous Receiver/Transmitter)
 * 通用异步收发器
 *
 * 特性：
 * - 可配置波特率
 * - 8-N-1 格式 (8 data bits, No parity, 1 stop bit)
 * - TX/RX FIFO缓冲
 * - 中断支持
 * - Wishbone B4从设备接口
 *
 * 寄存器映射：
 * 0x00: DATA   - 数据寄存器 (读=RX, 写=TX)
 * 0x04: STATUS - 状态寄存器
 * 0x08: CTRL   - 控制寄存器
 * 0x0C: DIV    - 波特率分频器
 */

/**
 * UART Registers
 */
object UARTRegs {
  val DATA_REG   = 0x00.U(4.W)
  val STATUS_REG = 0x04.U(4.W)
  val CTRL_REG   = 0x08.U(4.W)
  val DIV_REG    = 0x0C.U(4.W)
}

/**
 * UART Status Register Bits
 */
object UARTStatus {
  val TX_READY = 0  // TX FIFO has space
  val RX_VALID = 1  // RX FIFO has data
  val TX_EMPTY = 2  // TX FIFO is empty
  val RX_FULL  = 3  // RX FIFO is full
}

/**
 * UART Control Register Bits
 */
object UARTCtrl {
  val TX_EN    = 0  // Transmit enable
  val RX_EN    = 1  // Receive enable
  val TX_IRQ   = 2  // TX interrupt enable
  val RX_IRQ   = 3  // RX interrupt enable
}

/**
 * Simple UART Transmitter
 */
class UARTTx(clockFreq: Int = 50000000, baudRate: Int = 115200) extends Module {
  val io = IO(new Bundle {
    val din = Input(UInt(8.W))
    val din_valid = Input(Bool())
    val din_ready = Output(Bool())
    val tx = Output(Bool())
  })

  val cyclesPerBit = (clockFreq / baudRate).U

  // State machine
  val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val counter = RegInit(0.U(16.W))
  val bit_index = RegInit(0.U(3.W))
  val data_reg = RegInit(0.U(8.W))

  io.din_ready := (state === sIdle)
  io.tx := true.B  // Default idle high

  switch(state) {
    is(sIdle) {
      io.tx := true.B
      when(io.din_valid) {
        data_reg := io.din
        state := sStart
        counter := 0.U
      }
    }

    is(sStart) {
      io.tx := false.B  // Start bit (low)
      when(counter === cyclesPerBit - 1.U) {
        counter := 0.U
        bit_index := 0.U
        state := sData
      }.otherwise {
        counter := counter + 1.U
      }
    }

    is(sData) {
      io.tx := data_reg(bit_index)
      when(counter === cyclesPerBit - 1.U) {
        counter := 0.U
        when(bit_index === 7.U) {
          state := sStop
        }.otherwise {
          bit_index := bit_index + 1.U
        }
      }.otherwise {
        counter := counter + 1.U
      }
    }

    is(sStop) {
      io.tx := true.B  // Stop bit (high)
      when(counter === cyclesPerBit - 1.U) {
        counter := 0.U
        state := sIdle
      }.otherwise {
        counter := counter + 1.U
      }
    }
  }
}

/**
 * Simple UART Receiver
 */
class UARTRx(clockFreq: Int = 50000000, baudRate: Int = 115200) extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val dout = Output(UInt(8.W))
    val dout_valid = Output(Bool())
  })

  val cyclesPerBit = (clockFreq / baudRate).U
  val cyclesPerHalfBit = cyclesPerBit >> 1.U

  // State machine
  val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val counter = RegInit(0.U(16.W))
  val bit_index = RegInit(0.U(3.W))
  val data_reg = RegInit(0.U(8.W))

  // Synchronize RX input
  val rx_sync = RegNext(RegNext(io.rx, true.B), true.B)

  io.dout := data_reg
  io.dout_valid := false.B

  switch(state) {
    is(sIdle) {
      counter := 0.U
      bit_index := 0.U
      when(!rx_sync) {  // Start bit detected
        state := sStart
      }
    }

    is(sStart) {
      when(counter === cyclesPerHalfBit) {
        when(!rx_sync) {  // Verify start bit
          counter := 0.U
          state := sData
        }.otherwise {
          state := sIdle  // False start bit
        }
      }.otherwise {
        counter := counter + 1.U
      }
    }

    is(sData) {
      when(counter === cyclesPerBit - 1.U) {
        data_reg := Cat(rx_sync, data_reg(7, 1))
        counter := 0.U
        when(bit_index === 7.U) {
          state := sStop
        }.otherwise {
          bit_index := bit_index + 1.U
        }
      }.otherwise {
        counter := counter + 1.U
      }
    }

    is(sStop) {
      when(counter === cyclesPerBit - 1.U) {
        io.dout_valid := true.B
        state := sIdle
      }.otherwise {
        counter := counter + 1.U
      }
    }
  }
}

/**
 * UART with Wishbone Interface
 */
class UART(
  clockFreq: Int = 50000000,
  baudRate: Int = 115200,
  fifoDepth: Int = 16
) extends Module {
  val io = IO(new Bundle {
    // Wishbone interface
    val wb = new WishboneSlave()

    // UART pins
    val tx = Output(Bool())
    val rx = Input(Bool())

    // Interrupt
    val irq = Output(Bool())
  })

  // TX and RX modules
  val tx_module = Module(new UARTTx(clockFreq, baudRate))
  val rx_module = Module(new UARTRx(clockFreq, baudRate))

  // TX and RX FIFOs
  val tx_fifo = Module(new Queue(UInt(8.W), fifoDepth))
  val rx_fifo = Module(new Queue(UInt(8.W), fifoDepth))

  // Connect UART pins
  io.tx := tx_module.io.tx
  rx_module.io.rx := io.rx

  // Connect FIFOs
  tx_fifo.io.deq <> tx_module.io
  tx_fifo.io.deq.ready := tx_module.io.din_ready
  tx_module.io.din_valid := tx_fifo.io.deq.valid
  tx_module.io.din := tx_fifo.io.deq.bits

  rx_fifo.io.enq.valid := rx_module.io.dout_valid
  rx_fifo.io.enq.bits := rx_module.io.dout

  // Registers
  val ctrl_reg = RegInit(0.U(32.W))
  val div_reg = RegInit((clockFreq / baudRate).U(32.W))

  // Wishbone interface
  val ack_reg = RegInit(false.B)
  ack_reg := io.wb.cyc_i && io.wb.stb_i && !ack_reg

  io.wb.ack_o := ack_reg
  io.wb.err_o := false.B
  io.wb.rty_o := false.B

  val reg_addr = io.wb.adr_i(3, 2)

  // Read logic
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    UARTRegs.DATA_REG -> Cat(0.U(24.W), rx_fifo.io.deq.bits),
    UARTRegs.STATUS_REG -> Cat(
      0.U(28.W),
      rx_fifo.io.count === fifoDepth.U,  // RX_FULL
      tx_fifo.io.count === 0.U,           // TX_EMPTY
      rx_fifo.io.deq.valid,               // RX_VALID
      tx_fifo.io.enq.ready                // TX_READY
    ),
    UARTRegs.CTRL_REG -> ctrl_reg,
    UARTRegs.DIV_REG -> div_reg
  ))

  // Write logic
  when(io.wb.cyc_i && io.wb.stb_i && io.wb.we_i) {
    switch(reg_addr) {
      is(UARTRegs.DATA_REG) {
        tx_fifo.io.enq.valid := true.B
        tx_fifo.io.enq.bits := io.wb.dat_i(7, 0)
      }
      is(UARTRegs.CTRL_REG) {
        ctrl_reg := io.wb.dat_i
      }
      is(UARTRegs.DIV_REG) {
        div_reg := io.wb.dat_i
      }
    }
  }.otherwise {
    tx_fifo.io.enq.valid := false.B
    tx_fifo.io.enq.bits := 0.U
  }

  // Read from RX FIFO
  rx_fifo.io.deq.ready := io.wb.cyc_i && io.wb.stb_i && !io.wb.we_i &&
                          (reg_addr === UARTRegs.DATA_REG) && ack_reg

  // Interrupt generation
  val tx_irq_en = ctrl_reg(UARTCtrl.TX_IRQ)
  val rx_irq_en = ctrl_reg(UARTCtrl.RX_IRQ)

  io.irq := (tx_irq_en && tx_fifo.io.enq.ready) ||
            (rx_irq_en && rx_fifo.io.deq.valid)
}
