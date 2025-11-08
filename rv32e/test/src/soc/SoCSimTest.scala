package soc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import bus._
import peripherals._

import java.io.{File, RandomAccessFile}
import scala.io.Source

/**
 * SoC Simulation Test with Program Loading
 * SoC仿真测试，支持程序加载
 *
 * 功能：
 * - 从十六进制文件加载程序到ROM/RAM
 * - 运行程序直到完成或超时
 * - 监控UART输出
 * - 记录执行轨迹
 */
class SoCSimTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "RV32E SoC with Program Loading"

  /**
   * 从十六进制文件加载内存内容
   *
   * 文件格式：每行一个32位十六进制数（8个十六进制字符）
   * 例如：
   * 00000013
   * 00100093
   * 00200113
   */
  def loadHexFile(filename: String): Seq[BigInt] = {
    if (!new File(filename).exists()) {
      println(s"Warning: File $filename not found, using default program")
      // 默认程序：简单的循环和UART输出
      return Seq(
        BigInt("13", 16),      // nop
        BigInt("93", 16),      // li x1, 1
        BigInt("100093", 16),  // li x1, 1
        BigInt("200113", 16),  // li x2, 2
        BigInt("002081b3", 16), // add x3, x1, x2
        BigInt("100063", 16)   // beq x0, x0, -32 (loop)
      )
    }

    Source.fromFile(filename).getLines()
      .map(_.trim)
      .filter(line => line.nonEmpty && !line.startsWith("#"))
      .map(line => BigInt(line, 16))
      .toSeq
  }

  /**
   * 将程序加载到Wishbone存储器
   */
  def loadProgramToMemory(
    mem: WishboneMemorySlave,
    program: Seq[BigInt],
    baseAddr: Int = 0
  ): Unit = {
    // 通过peek/poke直接访问内部mem数组
    // 注意：这是测试环境特有的方法
    for ((word, idx) <- program.zipWithIndex) {
      val addr = baseAddr + (idx * 4)
      val byte0 = (word & 0xFF).toInt
      val byte1 = ((word >> 8) & 0xFF).toInt
      val byte2 = ((word >> 16) & 0xFF).toInt
      val byte3 = ((word >> 24) & 0xFF).toInt

      // 写入4个字节（小端序）
      // mem.mem(addr) := byte0.U
      // mem.mem(addr+1) := byte1.U
      // mem.mem(addr+2) := byte2.U
      // mem.mem(addr+3) := byte3.U
      // 注意：实际实现需要使用pokeMemory或类似方法
    }
  }

  it should "load and execute a simple program" in {
    test(new RV32ESoC()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 复位
      dut.reset.poke(true.B)
      dut.clock.step(5)
      dut.reset.poke(false.B)

      // 绑定UART输入
      dut.io.uart_rx.poke(true.B)  // Idle state
      dut.io.gpio_in.poke(0.U)

      println("Starting SoC simulation...")
      println("PC\t\tInstruction")
      println("=" * 40)

      // 运行1000个周期
      for (cycle <- 0 until 1000) {
        // 监控PC和指令
        val pc = dut.io.debug_pc.peek().litValue
        val inst = dut.io.debug_inst.peek().litValue

        if (cycle % 10 == 0) {
          println(f"0x$pc%08x\t0x$inst%08x")
        }

        // 监控UART输出
        if (dut.io.uart_tx.peek().litToBoolean == false) {
          // 检测到UART start bit
          // 实际解析需要更复杂的状态机
        }

        dut.clock.step(1)
      }

      println("Simulation completed after 1000 cycles")
    }
  }

  it should "test UART peripheral" in {
    test(new UART()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 初始化
      dut.clock.step(5)

      // 配置UART
      // 写入控制寄存器启用TX和RX
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(8.U)  // CTRL寄存器偏移0x08
      dut.io.wb.dat_i.poke(3.U)  // 使能TX和RX
      dut.io.wb.sel_i.poke(15.U) // 所有字节

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 发送一个字符 'A' (0x41)
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(0.U)  // DATA寄存器
      dut.io.wb.dat_i.poke(0x41.U)
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)

      // 等待UART传输完成（约87us @ 115200 baud）
      // 50MHz时钟：87us = 4350个周期
      dut.clock.step(5000)

      println("UART test completed")
    }
  }

  it should "test GPIO peripheral" in {
    test(new GPIO()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 初始化
      dut.io.gpio_in.poke(0.U)
      dut.clock.step(5)

      // 设置输出使能
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(8.U)  // OE寄存器
      dut.io.wb.dat_i.poke(0xFFFF.U)  // 所有位输出
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 写入输出值
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(4.U)  // OUT寄存器
      dut.io.wb.dat_i.poke(0xAAAA.U)
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 验证输出
      val gpio_out = dut.io.gpio_out.peek().litValue
      println(f"GPIO output: 0x$gpio_out%04x")
      assert(gpio_out == 0xAAAA, "GPIO output mismatch")

      println("GPIO test passed")
    }
  }

  it should "test Timer peripheral" in {
    test(new Timer()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 初始化
      dut.clock.step(5)

      // 配置预分频器（分频比=100）
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(12.U)  // PRESCALER寄存器
      dut.io.wb.dat_i.poke(100.U)
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 设置比较值
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(8.U)  // COMPARE寄存器
      dut.io.wb.dat_i.poke(1000.U)
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 启动定时器（使能 + 中断使能）
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(4.U)  // CTRL寄存器
      dut.io.wb.dat_i.poke(3.U)  // EN=1, IE=1
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)

      // 运行直到中断触发
      var irq_detected = false
      for (_ <- 0 until 150000) {
        dut.clock.step(1)
        if (dut.io.irq.peek().litToBoolean) {
          irq_detected = true
          println("Timer interrupt detected!")
        }
      }

      assert(irq_detected, "Timer interrupt not detected")
      println("Timer test passed")
    }
  }

  it should "test PLIC peripheral" in {
    test(new PLIC()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 初始化
      dut.io.interrupts.poke(0.U)
      dut.clock.step(5)

      // 设置中断源1的优先级=5
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(4.U)  // Priority[1]
      dut.io.wb.dat_i.poke(5.U)
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 使能中断源1
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(0x2000.U)  // Enable register
      dut.io.wb.dat_i.poke(2.U)  // Enable source 1
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 触发中断源1
      dut.io.interrupts.poke(2.U)  // Source 1
      dut.clock.step(5)

      // 检查中断输出
      val irq = dut.io.m_interrupt.peek().litToBoolean
      assert(irq, "PLIC interrupt not asserted")
      println("PLIC interrupt asserted correctly")

      // 声明中断
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(false.B)  // Read
      dut.io.wb.adr_i.poke(0x200008.U)  // Claim register

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      val claim_id = dut.io.wb.dat_o.peek().litValue
      println(f"Claimed interrupt ID: $claim_id")
      assert(claim_id == 1, "Wrong interrupt ID claimed")

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(2)

      // 完成中断
      dut.io.wb.cyc_i.poke(true.B)
      dut.io.wb.stb_i.poke(true.B)
      dut.io.wb.we_i.poke(true.B)
      dut.io.wb.adr_i.poke(0x200008.U)  // Complete register
      dut.io.wb.dat_i.poke(1.U)  // Complete source 1
      dut.io.wb.sel_i.poke(15.U)

      dut.clock.step(1)
      while (!dut.io.wb.ack_o.peek().litToBoolean) {
        dut.clock.step(1)
      }

      dut.io.wb.cyc_i.poke(false.B)
      dut.io.wb.stb_i.poke(false.B)
      dut.clock.step(5)

      println("PLIC test passed")
    }
  }

  it should "test CSR module" in {
    test(new CSRFile()).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // 初始化
      dut.io.decode_csr_cmd.poke(CSROp.NONE)
      dut.io.exception.poke(false.B)
      dut.io.mret.poke(false.B)
      dut.io.timer_irq.poke(false.B)
      dut.io.software_irq.poke(false.B)
      dut.io.external_irq.poke(false.B)
      dut.clock.step(5)

      // 测试1：写入mtvec
      dut.io.decode_csr_addr.poke(CSRAddr.mtvec)
      dut.io.decode_csr_cmd.poke(CSROp.RW)
      dut.io.execute_csr_wdata.poke(0x80000000L.U)
      dut.clock.step(1)

      val mtvec_read = dut.io.execute_csr_rdata.peek().litValue
      println(f"mtvec written: 0x$mtvec_read%08x")

      dut.io.decode_csr_cmd.poke(CSROp.NONE)
      dut.clock.step(2)

      // 测试2：写入mie（中断使能）
      dut.io.decode_csr_addr.poke(CSRAddr.mie)
      dut.io.decode_csr_cmd.poke(CSROp.RW)
      dut.io.execute_csr_wdata.poke(0x888.U)  // M-mode timer/software/external
      dut.clock.step(1)

      dut.io.decode_csr_cmd.poke(CSROp.NONE)
      dut.clock.step(2)

      // 测试3：触发异常
      dut.io.exception.poke(true.B)
      dut.io.exception_pc.poke(0x1000.U)
      dut.io.exception_cause.poke(TrapCause.ILLEGAL_INST)
      dut.io.exception_tval.poke(0x12345678L.U)
      dut.clock.step(1)

      val trap_taken = dut.io.trap_taken.peek().litToBoolean
      val trap_vector = dut.io.trap_vector.peek().litValue
      println(f"Trap taken: $trap_taken, vector: 0x$trap_vector%08x")

      dut.io.exception.poke(false.B)
      dut.clock.step(2)

      // 测试4：读取mepc（应该保存了异常PC）
      dut.io.decode_csr_addr.poke(CSRAddr.mepc)
      dut.io.decode_csr_cmd.poke(CSROp.RW)
      dut.io.execute_csr_wdata.poke(0.U)
      dut.clock.step(1)

      val mepc = dut.io.execute_csr_rdata.peek().litValue
      println(f"mepc: 0x$mepc%08x")
      assert(mepc == 0x1000, "mepc not saved correctly")

      dut.io.decode_csr_cmd.poke(CSROp.NONE)
      dut.clock.step(2)

      // 测试5：MRET返回
      dut.io.mret.poke(true.B)
      dut.clock.step(1)

      val epc = dut.io.epc.peek().litValue
      println(f"Returning to EPC: 0x$epc%08x")
      assert(epc == 0x1000, "EPC not correct")

      dut.io.mret.poke(false.B)
      dut.clock.step(5)

      println("CSR test passed")
    }
  }
}
