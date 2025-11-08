package peripherals

import chisel3._
import chisel3.util._
import bus._

/**
 * Timer/Counter Module
 * 定时器/计数器模块
 *
 * 特性：
 * - 32位向下计数器
 * - 可配置预分频器
 * - 自动重载模式
 * - 单次模式
 * - 中断支持
 * - Wishbone B4从设备接口
 *
 * 寄存器映射：
 * 0x00: CTRL    - 控制寄存器
 * 0x04: STATUS  - 状态寄存器
 * 0x08: LOAD    - 重载值寄存器
 * 0x0C: COUNT   - 当前计数值（只读）
 * 0x10: PRESCAL - 预分频器值
 */

object TimerRegs {
  val CTRL    = 0x00.U(4.W)
  val STATUS  = 0x04.U(4.W)
  val LOAD    = 0x08.U(4.W)
  val COUNT   = 0x0C.U(4.W)
  val PRESCAL = 0x10.U(4.W)
}

/**
 * Timer Control Register Bits
 */
object TimerCtrl {
  val ENABLE    = 0  // Timer enable
  val IRQ_EN    = 1  // Interrupt enable
  val AUTO_RELOAD = 2  // Auto-reload mode (continuous)
  val ONE_SHOT  = 3  // One-shot mode
}

/**
 * Timer Status Register Bits
 */
object TimerStatus {
  val IRQ_FLAG = 0  // Interrupt flag (write 1 to clear)
}

/**
 * Timer/Counter with Wishbone Interface
 */
class Timer extends Module {
  val io = IO(new Bundle {
    // Wishbone interface
    val wb = new WishboneSlave()

    // Interrupt output
    val irq = Output(Bool())
  })

  // Registers
  val ctrl_reg = RegInit(0.U(32.W))
  val status_reg = RegInit(0.U(32.W))
  val load_reg = RegInit(0.U(32.W))
  val count_reg = RegInit(0.U(32.W))
  val prescal_reg = RegInit(0.U(32.W))
  val prescal_counter = RegInit(0.U(32.W))

  // Extract control bits
  val timer_enable = ctrl_reg(TimerCtrl.ENABLE)
  val irq_enable = ctrl_reg(TimerCtrl.IRQ_EN)
  val auto_reload = ctrl_reg(TimerCtrl.AUTO_RELOAD)
  val one_shot = ctrl_reg(TimerCtrl.ONE_SHOT)

  // Timer logic
  val tick = Wire(Bool())
  tick := false.B

  when(timer_enable) {
    when(prescal_counter === prescal_reg) {
      prescal_counter := 0.U
      tick := true.B
    }.otherwise {
      prescal_counter := prescal_counter + 1.U
    }
  }.otherwise {
    prescal_counter := 0.U
  }

  // Counter logic
  when(timer_enable && tick) {
    when(count_reg === 0.U) {
      // Counter reached zero
      status_reg := status_reg | (1.U << TimerStatus.IRQ_FLAG)

      when(auto_reload) {
        // Auto-reload mode: reload counter
        count_reg := load_reg
      }.elsewhen(one_shot) {
        // One-shot mode: stop timer
        ctrl_reg := ctrl_reg & ~(1.U << TimerCtrl.ENABLE)
        count_reg := 0.U
      }.otherwise {
        // Default: stop at zero
        count_reg := 0.U
      }
    }.otherwise {
      // Decrement counter
      count_reg := count_reg - 1.U
    }
  }

  // Wishbone interface
  val ack_reg = RegInit(false.B)
  ack_reg := io.wb.cyc_i && io.wb.stb_i && !ack_reg

  io.wb.ack_o := ack_reg
  io.wb.err_o := false.B
  io.wb.rty_o := false.B

  val reg_addr = io.wb.adr_i(5, 2)

  // Read logic
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    TimerRegs.CTRL    -> ctrl_reg,
    TimerRegs.STATUS  -> status_reg,
    TimerRegs.LOAD    -> load_reg,
    TimerRegs.COUNT   -> count_reg,
    TimerRegs.PRESCAL -> prescal_reg
  ))

  // Write logic
  when(io.wb.cyc_i && io.wb.stb_i && io.wb.we_i) {
    switch(reg_addr) {
      is(TimerRegs.CTRL) {
        ctrl_reg := io.wb.dat_i
      }
      is(TimerRegs.STATUS) {
        // Write 1 to clear
        status_reg := status_reg & ~io.wb.dat_i
      }
      is(TimerRegs.LOAD) {
        load_reg := io.wb.dat_i
        // Also load into counter if timer is disabled
        when(!timer_enable) {
          count_reg := io.wb.dat_i
        }
      }
      is(TimerRegs.PRESCAL) {
        prescal_reg := io.wb.dat_i
      }
    }
  }

  // Interrupt output
  io.irq := irq_enable && status_reg(TimerStatus.IRQ_FLAG)
}

/**
 * Multi-Timer Module
 * 多定时器模块（4个独立定时器）
 */
class MultiTimer(numTimers: Int = 4) extends Module {
  val io = IO(new Bundle {
    // Wishbone interface
    val wb = new WishboneSlave()

    // Interrupt outputs (one per timer)
    val irq = Output(Vec(numTimers, Bool()))
  })

  // Instantiate timers
  val timers = Seq.fill(numTimers)(Module(new Timer()))

  // Address decode: bits [11:6] select timer, bits [5:2] select register
  val timer_sel = io.wb.adr_i(11, 6)

  // Default outputs
  io.wb.ack_o := false.B
  io.wb.err_o := false.B
  io.wb.rty_o := false.B
  io.wb.dat_o := 0.U

  // Connect timers
  for (i <- 0 until numTimers) {
    // Default inputs
    timers(i).io.wb.cyc_i := false.B
    timers(i).io.wb.stb_i := false.B
    timers(i).io.wb.we_i := false.B
    timers(i).io.wb.adr_i := 0.U
    timers(i).io.wb.dat_i := 0.U
    timers(i).io.wb.sel_i := 0.U
    timers(i).io.wb.lock_i := false.B
    timers(i).io.wb.cti_i := 0.U
    timers(i).io.wb.bte_i := 0.U

    when(timer_sel === i.U) {
      // Forward signals to selected timer
      timers(i).io.wb.cyc_i := io.wb.cyc_i
      timers(i).io.wb.stb_i := io.wb.stb_i
      timers(i).io.wb.we_i := io.wb.we_i
      timers(i).io.wb.adr_i := io.wb.adr_i
      timers(i).io.wb.dat_i := io.wb.dat_i
      timers(i).io.wb.sel_i := io.wb.sel_i
      timers(i).io.wb.lock_i := io.wb.lock_i
      timers(i).io.wb.cti_i := io.wb.cti_i
      timers(i).io.wb.bte_i := io.wb.bte_i

      // Forward responses
      io.wb.ack_o := timers(i).io.wb.ack_o
      io.wb.err_o := timers(i).io.wb.err_o
      io.wb.rty_o := timers(i).io.wb.rty_o
      io.wb.dat_o := timers(i).io.wb.dat_o
    }

    // Connect interrupt outputs
    io.irq(i) := timers(i).io.irq
  }
}
