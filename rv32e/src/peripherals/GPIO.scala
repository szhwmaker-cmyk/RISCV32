package peripherals

import chisel3._
import chisel3.util._
import bus._

/**
 * GPIO (General Purpose Input/Output)
 * 通用输入输出端口
 *
 * 特性：
 * - 16位可配置I/O端口
 * - 独立的输入、输出、方向控制
 * - 中断支持（上升沿、下降沿、双沿）
 * - Wishbone B4从设备接口
 *
 * 寄存器映射：
 * 0x00: DATA_IN  - 输入数据寄存器（只读）
 * 0x04: DATA_OUT - 输出数据寄存器（读写）
 * 0x08: DIR      - 方向寄存器（0=输入, 1=输出）
 * 0x0C: IRQ_EN   - 中断使能寄存器
 * 0x10: IRQ_MODE - 中断模式（0=上升沿, 1=下降沿, 2=双沿）
 * 0x14: IRQ_STAT - 中断状态寄存器（写1清除）
 */

object GPIORegs {
  val DATA_IN  = 0x00.U(4.W)
  val DATA_OUT = 0x04.U(4.W)
  val DIR      = 0x08.U(4.W)
  val IRQ_EN   = 0x0C.U(4.W)
  val IRQ_MODE = 0x10.U(4.W)
  val IRQ_STAT = 0x14.U(4.W)
}

object IRQMode {
  val RISING  = 0.U(2.W)  // 上升沿
  val FALLING = 1.U(2.W)  // 下降沿
  val BOTH    = 2.U(2.W)  // 双沿
}

/**
 * GPIO Controller with Wishbone Interface
 */
class GPIO(width: Int = 16) extends Module {
  val io = IO(new Bundle {
    // Wishbone interface
    val wb = new WishboneSlave()

    // GPIO pins
    val gpio_in = Input(UInt(width.W))
    val gpio_out = Output(UInt(width.W))
    val gpio_oe = Output(UInt(width.W))  // Output enable (1=output, 0=input)

    // Interrupt
    val irq = Output(Bool())
  })

  // Registers
  val data_out_reg = RegInit(0.U(width.W))
  val dir_reg = RegInit(0.U(width.W))  // 0=input, 1=output
  val irq_en_reg = RegInit(0.U(width.W))
  val irq_mode_reg = RegInit(VecInit(Seq.fill(width)(0.U(2.W))))
  val irq_stat_reg = RegInit(0.U(width.W))

  // Input synchronization (防止亚稳态)
  val gpio_in_sync1 = RegNext(io.gpio_in)
  val gpio_in_sync2 = RegNext(gpio_in_sync1)
  val gpio_in_prev = RegNext(gpio_in_sync2)

  // Edge detection
  val rising_edge = Wire(UInt(width.W))
  val falling_edge = Wire(UInt(width.W))

  rising_edge := gpio_in_sync2 & ~gpio_in_prev
  falling_edge := ~gpio_in_sync2 & gpio_in_prev

  // Interrupt generation per pin
  val irq_triggers = Wire(Vec(width, Bool()))
  for (i <- 0 until width) {
    irq_triggers(i) := MuxLookup(irq_mode_reg(i), false.B)(Seq(
      IRQMode.RISING  -> rising_edge(i),
      IRQMode.FALLING -> falling_edge(i),
      IRQMode.BOTH    -> (rising_edge(i) || falling_edge(i))
    ))
  }

  // Update interrupt status register
  for (i <- 0 until width) {
    when(irq_triggers(i) && irq_en_reg(i)) {
      irq_stat_reg := irq_stat_reg | (1.U << i)
    }
  }

  // GPIO output
  io.gpio_out := data_out_reg
  io.gpio_oe := dir_reg

  // Wishbone interface
  val ack_reg = RegInit(false.B)
  ack_reg := io.wb.cyc_i && io.wb.stb_i && !ack_reg

  io.wb.ack_o := ack_reg
  io.wb.err_o := false.B
  io.wb.rty_o := false.B

  val reg_addr = io.wb.adr_i(5, 2)

  // Read logic
  io.wb.dat_o := MuxLookup(reg_addr, 0.U)(Seq(
    GPIORegs.DATA_IN  -> Cat(0.U((32 - width).W), gpio_in_sync2),
    GPIORegs.DATA_OUT -> Cat(0.U((32 - width).W), data_out_reg),
    GPIORegs.DIR      -> Cat(0.U((32 - width).W), dir_reg),
    GPIORegs.IRQ_EN   -> Cat(0.U((32 - width).W), irq_en_reg),
    GPIORegs.IRQ_STAT -> Cat(0.U((32 - width).W), irq_stat_reg)
  ))

  // Write logic
  when(io.wb.cyc_i && io.wb.stb_i && io.wb.we_i) {
    switch(reg_addr) {
      is(GPIORegs.DATA_OUT) {
        data_out_reg := io.wb.dat_i(width - 1, 0)
      }
      is(GPIORegs.DIR) {
        dir_reg := io.wb.dat_i(width - 1, 0)
      }
      is(GPIORegs.IRQ_EN) {
        irq_en_reg := io.wb.dat_i(width - 1, 0)
      }
      is(GPIORegs.IRQ_MODE) {
        // 每个引脚2位模式，打包在32位寄存器中
        for (i <- 0 until width.min(16)) {
          irq_mode_reg(i) := io.wb.dat_i(i * 2 + 1, i * 2)
        }
      }
      is(GPIORegs.IRQ_STAT) {
        // 写1清除
        irq_stat_reg := irq_stat_reg & ~io.wb.dat_i(width - 1, 0)
      }
    }
  }

  // Interrupt output (任何启用的引脚有中断即触发)
  io.irq := (irq_stat_reg & irq_en_reg).orR
}
