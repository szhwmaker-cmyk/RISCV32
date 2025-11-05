package rv32e.bus

import chisel3._
import chisel3.util._
import rv32e.Config

/**
 * Wishbone B4 Pipelined Interface
 *
 * Classic Wishbone signals for master and slave devices
 */
class WishboneMasterIO extends Bundle {
  val adr   = Output(UInt(Config.ADDR_WIDTH.W))  // Address
  val dat_o = Output(UInt(Config.BUS_WIDTH.W))   // Data output (master to slave)
  val dat_i = Input(UInt(Config.BUS_WIDTH.W))    // Data input (slave to master)
  val we    = Output(Bool())                      // Write enable
  val sel   = Output(UInt(4.W))                   // Byte select
  val stb   = Output(Bool())                      // Strobe (valid transaction)
  val cyc   = Output(Bool())                      // Cycle valid
  val ack   = Input(Bool())                       // Acknowledge
}

class WishboneSlaveIO extends Bundle {
  val adr   = Input(UInt(Config.ADDR_WIDTH.W))
  val dat_i = Input(UInt(Config.BUS_WIDTH.W))    // Data input to slave
  val dat_o = Output(UInt(Config.BUS_WIDTH.W))   // Data output from slave
  val we    = Input(Bool())
  val sel   = Input(UInt(4.W))
  val stb   = Input(Bool())
  val cyc   = Input(Bool())
  val ack   = Output(Bool())
}

/**
 * Helper functions for Wishbone transactions
 */
object Wishbone {
  /**
   * Generate byte select mask from address and access size
   * @param addr Lower 2 bits of address
   * @param size Access size: 0=byte, 1=half, 2=word
   * @return 4-bit select mask
   */
  def genByteSelect(addr: UInt, size: UInt): UInt = {
    val sel = WireDefault(0.U(4.W))
    switch(size) {
      is(0.U) { // Byte access
        sel := MuxLookup(addr(1, 0), 0.U)(Seq(
          0.U -> "b0001".U,
          1.U -> "b0010".U,
          2.U -> "b0100".U,
          3.U -> "b1000".U
        ))
      }
      is(1.U) { // Half-word access
        sel := Mux(addr(1) === 0.U, "b0011".U, "b1100".U)
      }
      is(2.U) { // Word access
        sel := "b1111".U
      }
    }
    sel
  }

  /**
   * Align data for writing based on address
   */
  def alignWriteData(data: UInt, addr: UInt, size: UInt): UInt = {
    val aligned = WireDefault(0.U(32.W))
    switch(size) {
      is(0.U) { // Byte
        aligned := MuxLookup(addr(1, 0), 0.U)(Seq(
          0.U -> (data(7, 0) << 0),
          1.U -> (data(7, 0) << 8),
          2.U -> (data(7, 0) << 16),
          3.U -> (data(7, 0) << 24)
        ))
      }
      is(1.U) { // Half-word
        aligned := Mux(addr(1) === 0.U,
          data(15, 0) << 0,
          data(15, 0) << 16
        )
      }
      is(2.U) { // Word
        aligned := data
      }
    }
    aligned
  }

  /**
   * Extract and sign-extend loaded data
   */
  def extractLoadData(data: UInt, addr: UInt, size: UInt, unsigned: Bool): UInt = {
    val extracted = WireDefault(0.U(32.W))

    switch(size) {
      is(0.U) { // Byte
        val byte_data = MuxLookup(addr(1, 0), 0.U)(Seq(
          0.U -> data(7, 0),
          1.U -> data(15, 8),
          2.U -> data(23, 16),
          3.U -> data(31, 24)
        ))
        extracted := Mux(unsigned,
          byte_data,  // Zero extend
          Cat(Fill(24, byte_data(7)), byte_data) // Sign extend
        )
      }
      is(1.U) { // Half-word
        val half_data = Mux(addr(1) === 0.U,
          data(15, 0),
          data(31, 16)
        )
        extracted := Mux(unsigned,
          half_data,  // Zero extend
          Cat(Fill(16, half_data(15)), half_data) // Sign extend
        )
      }
      is(2.U) { // Word
        extracted := data
      }
    }
    extracted
  }
}
