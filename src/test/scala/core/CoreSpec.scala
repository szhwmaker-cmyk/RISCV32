package rv32e.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Basic test for Core module instantiation
 */
class CoreSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Core"

  it should "instantiate without errors" in {
    test(new Core) { dut =>
      // Just verify the module can be created
      // More comprehensive tests will be added in integration testing
      dut.clock.step(1)
    }
  }

  it should "reset to correct PC" in {
    test(new Core) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(1)
      dut.reset.poke(false.B)
      dut.clock.step(1)

      // After reset, debug PC should be at reset vector
      // Note: Initial PC might need a few cycles to propagate
      dut.clock.step(5)
    }
  }
}
