package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

class UartSpec extends AnyFlatSpec with ChiselScalatestTester {
  "UART" should "transmit a byte correctly" in {
    test(new Uart) { dut =>
      // 配置波特率（快速测试）
      dut.io.wb.adr_o.poke(UartReg.BAUD.U)
      dut.io.wb.dat_o.poke(10.U)  // 快速波特率用于测试
      dut.io.wb.we_o.poke(true.B)
      dut.io.wb.stb_o.poke(true.B)
      dut.io.wb.cyc_o.poke(true.B)
      dut.clock.step()
      dut.io.wb.stb_o.poke(false.B)
      dut.io.wb.cyc_o.poke(false.B)
      dut.clock.step()

      // 检查TX初始状态为高
      dut.io.tx.expect(true.B)

      // 发送字节 0x55 (0b01010101)
      dut.io.wb.adr_o.poke(UartReg.TXDATA.U)
      dut.io.wb.dat_o.poke(0x55.U)
      dut.io.wb.we_o.poke(true.B)
      dut.io.wb.stb_o.poke(true.B)
      dut.io.wb.cyc_o.poke(true.B)
      dut.clock.step()
      dut.io.wb.stb_o.poke(false.B)
      dut.io.wb.cyc_o.poke(false.B)

      // 等待传输完成（start + 8 data + stop = 10 bits）
      dut.clock.step(120)

      // TX应该回到高电平
      dut.io.tx.expect(true.B)
    }
  }

  it should "set status flags correctly" in {
    test(new Uart) { dut =>
      // 读取状态寄存器
      dut.io.wb.adr_o.poke(UartReg.STATUS.U)
      dut.io.wb.we_o.poke(false.B)
      dut.io.wb.stb_o.poke(true.B)
      dut.io.wb.cyc_o.poke(true.B)
      dut.clock.step(2)

      // TX应该是空闲的（bit 1 = tx_empty应该为1）
      val status = dut.io.wb.dat_o.peek().litValue
      assert((status & 0x02) != 0, "TX should be empty initially")

      dut.io.wb.stb_o.poke(false.B)
      dut.io.wb.cyc_o.poke(false.B)
    }
  }
}
