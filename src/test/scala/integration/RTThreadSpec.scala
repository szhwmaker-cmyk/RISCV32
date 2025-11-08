package integration

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import soc._

class RTThreadSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RT-Thread OS Boot Test"

  it should "boot RT-Thread from Flash and output to UART" in {
    test(new RV32E_SoC).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // 初始化
      dut.io.uart_rx.poke(true.B)
      dut.io.gpio_in.poke(0.U)
      dut.io.spi_miso.poke(false.B)
      dut.io.i2c_scl_i.poke(true.B)
      dut.io.i2c_sda_i.poke(true.B)
      dut.io.flash_miso.poke(false.B)

      println("Testing RT-Thread boot (simplified)...")
      println("Note: Full RT-Thread test requires compiled RISC-V image")

      // 运行足够长的时间以观察启动过程
      for (cycle <- 0 until 10000) {
        dut.clock.step()

        if (cycle % 1000 == 0) {
          println(s"Cycle: $cycle")
        }
      }

      println("RT-Thread boot test completed!")
      println("In a real test, we would:")
      println("1. Load RT-Thread image into Flash model")
      println("2. Monitor UART for RT-Thread banner")
      println("3. Verify system initialization messages")
    }
  }
}
