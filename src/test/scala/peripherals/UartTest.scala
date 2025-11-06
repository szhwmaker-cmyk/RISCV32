package peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import common.Config._

/**
 * UART 单元测试
 */
class UartTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "UartTx"

  it should "transmit a byte correctly" in {
    test(new UartTx) { dut =>
      // 设置波特率分频（使用较小的值加快仿真）
      dut.io.baud_div.poke(4.U)
      dut.io.enable.poke(true.B)

      // 发送数据 0x55 (01010101)
      dut.io.din.poke(0x55.U)
      dut.io.wr_en.poke(true.B)
      dut.clock.step()
      dut.io.wr_en.poke(false.B)

      // 空闲状态应该是高电平
      dut.io.tx.expect(true.B)

      // 等待起始位
      dut.clock.step(5)
      dut.io.tx.expect(false.B) // Start bit

      // 数据位 (LSB first)
      for (bit <- Seq(1, 0, 1, 0, 1, 0, 1, 0)) {
        dut.clock.step(4) // 波特率周期
        dut.io.tx.expect(bit.B)
      }

      // 停止位
      dut.clock.step(4)
      dut.io.tx.expect(true.B)

      // 返回空闲
      dut.clock.step(4)
      dut.io.tx.expect(true.B)
    }
  }

  behavior of "UartRx"

  it should "receive a byte correctly" in {
    test(new UartRx) { dut =>
      dut.io.baud_div.poke(4.U)
      dut.io.enable.poke(true.B)
      dut.io.rd_en.poke(false.B)

      // 模拟接收 0xA5 (10100101)
      dut.io.rx.poke(true.B) // Idle
      dut.clock.step(10)

      // Start bit
      dut.io.rx.poke(false.B)
      dut.clock.step(4)

      // Data bits (LSB first: 1,0,1,0,0,1,0,1)
      val data = Seq(1, 0, 1, 0, 0, 1, 0, 1)
      for (bit <- data) {
        dut.io.rx.poke(bit.B)
        dut.clock.step(4)
      }

      // Stop bit
      dut.io.rx.poke(true.B)
      dut.clock.step(4)

      // 检查接收到的数据
      dut.io.rx_valid.expect(true.B)

      // 读取数据
      dut.io.rd_en.poke(true.B)
      dut.io.dout.expect(0xA5.U)
      dut.clock.step()
      dut.io.rd_en.poke(false.B)
    }
  }

  behavior of "Uart"

  it should "handle register read/write" in {
    test(new Uart) { dut =>
      // 写入波特率寄存器
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(UartRegs.BAUD.U)
      dut.io.wb.dat_w.poke(100.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step()

      // 读取波特率寄存器
      dut.io.wb.we.poke(false.B)
      dut.clock.step()
      dut.io.wb.dat_r.expect(100.U)
      dut.io.wb.ack.expect(true.B)

      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
    }
  }
}
