package rv32e.bus

import chisel3._
import chisel3.util._
import rv32e.Config

/**
 * Wishbone Interconnect
 *
 * A simple Wishbone crossbar that routes transactions from multiple masters
 * to multiple slaves based on address decoding.
 *
 * Address Map:
 * - 0x1000_0000 - 0x1FFF_FFFF : SPI Flash (XIP)
 * - 0x2000_0000 - 0x2000_0FFF : UART
 * - 0x2001_0000 - 0x2001_0FFF : GPIO
 * - 0x2002_0000 - 0x2002_0FFF : SPI Master
 * - 0x2003_0000 - 0x2003_0FFF : I2C Master
 * - 0x2004_0000 - 0x2004_0FFF : SPI Flash Ctrl Registers
 * - 0x8000_0000 - 0x8000_FFFF : RAM
 */

object SlaveSelect {
  val NONE       = 0.U(3.W)
  val SPI_FLASH  = 1.U(3.W)
  val UART       = 2.U(3.W)
  val GPIO       = 3.U(3.W)
  val SPI        = 4.U(3.W)
  val I2C        = 5.U(3.W)
  val FLASH_CTRL = 6.U(3.W)
  val RAM        = 7.U(3.W)
}

class InterconnectIO extends Bundle {
  // Master ports (from CPU)
  val imem_master = Flipped(new WishboneMasterIO)  // Instruction fetch
  val dmem_master = Flipped(new WishboneMasterIO)  // Data access

  // Slave ports (to peripherals/memory)
  val spi_flash  = new WishboneMasterIO
  val uart       = new WishboneMasterIO
  val gpio       = new WishboneMasterIO
  val spi        = new WishboneMasterIO
  val i2c        = new WishboneMasterIO
  val flash_ctrl = new WishboneMasterIO
  val ram        = new WishboneMasterIO
}

class Interconnect extends Module {
  val io = IO(new InterconnectIO)

  // ========== Address Decoder ==========
  def decodeAddress(addr: UInt): UInt = {
    val sel = WireDefault(SlaveSelect.NONE)

    when(Config.inRange(addr, Config.SPI_FLASH_BASE, Config.SPI_FLASH_END)) {
      sel := SlaveSelect.SPI_FLASH
    }.elsewhen(Config.inRange(addr, Config.UART_BASE, Config.UART_END)) {
      sel := SlaveSelect.UART
    }.elsewhen(Config.inRange(addr, Config.GPIO_BASE, Config.GPIO_END)) {
      sel := SlaveSelect.GPIO
    }.elsewhen(Config.inRange(addr, Config.SPI_BASE, Config.SPI_END)) {
      sel := SlaveSelect.SPI
    }.elsewhen(Config.inRange(addr, Config.I2C_BASE, Config.I2C_END)) {
      sel := SlaveSelect.I2C
    }.elsewhen(Config.inRange(addr, Config.SPI_FLASH_CTRL_BASE, Config.SPI_FLASH_CTRL_END)) {
      sel := SlaveSelect.FLASH_CTRL
    }.elsewhen(Config.inRange(addr, Config.RAM_BASE, Config.RAM_END)) {
      sel := SlaveSelect.RAM
    }

    sel
  }

  // ========== Arbiter State Machine ==========
  // Simple priority arbiter: instruction has priority over data
  val s_idle :: s_imem :: s_dmem :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val current_master = WireDefault(io.imem_master)
  val imem_grant = WireDefault(false.B)
  val dmem_grant = WireDefault(false.B)

  switch(state) {
    is(s_idle) {
      when(io.imem_master.cyc) {
        state := s_imem
        imem_grant := true.B
      }.elsewhen(io.dmem_master.cyc) {
        state := s_dmem
        dmem_grant := true.B
      }
    }

    is(s_imem) {
      imem_grant := true.B
      current_master := io.imem_master
      when(!io.imem_master.cyc) {
        state := s_idle
      }
    }

    is(s_dmem) {
      dmem_grant := true.B
      current_master := io.dmem_master
      when(!io.dmem_master.cyc) {
        state := s_idle
      }
    }
  }

  // ========== Slave Selection ==========
  val slave_sel = decodeAddress(current_master.adr)

  // Default slave outputs (all inactive)
  val default_slave = Wire(new WishboneMasterIO)
  default_slave.adr := 0.U
  default_slave.dat_o := 0.U
  default_slave.we := false.B
  default_slave.sel := 0.U
  default_slave.stb := false.B
  default_slave.cyc := false.B

  io.spi_flash  := default_slave
  io.uart       := default_slave
  io.gpio       := default_slave
  io.spi        := default_slave
  io.i2c        := default_slave
  io.flash_ctrl := default_slave
  io.ram        := default_slave

  // Master acknowledgement multiplexer
  val master_ack = WireDefault(false.B)
  val master_dat_i = WireDefault(0.U(32.W))

  // ========== Route Master to Selected Slave ==========
  when(imem_grant || dmem_grant) {
    switch(slave_sel) {
      is(SlaveSelect.SPI_FLASH) {
        io.spi_flash.adr := current_master.adr
        io.spi_flash.dat_o := current_master.dat_o
        io.spi_flash.we := current_master.we
        io.spi_flash.sel := current_master.sel
        io.spi_flash.stb := current_master.stb
        io.spi_flash.cyc := current_master.cyc
        master_ack := io.spi_flash.ack
        master_dat_i := io.spi_flash.dat_i
      }

      is(SlaveSelect.UART) {
        io.uart.adr := current_master.adr
        io.uart.dat_o := current_master.dat_o
        io.uart.we := current_master.we
        io.uart.sel := current_master.sel
        io.uart.stb := current_master.stb
        io.uart.cyc := current_master.cyc
        master_ack := io.uart.ack
        master_dat_i := io.uart.dat_i
      }

      is(SlaveSelect.GPIO) {
        io.gpio.adr := current_master.adr
        io.gpio.dat_o := current_master.dat_o
        io.gpio.we := current_master.we
        io.gpio.sel := current_master.sel
        io.gpio.stb := current_master.stb
        io.gpio.cyc := current_master.cyc
        master_ack := io.gpio.ack
        master_dat_i := io.gpio.dat_i
      }

      is(SlaveSelect.SPI) {
        io.spi.adr := current_master.adr
        io.spi.dat_o := current_master.dat_o
        io.spi.we := current_master.we
        io.spi.sel := current_master.sel
        io.spi.stb := current_master.stb
        io.spi.cyc := current_master.cyc
        master_ack := io.spi.ack
        master_dat_i := io.spi.dat_i
      }

      is(SlaveSelect.I2C) {
        io.i2c.adr := current_master.adr
        io.i2c.dat_o := current_master.dat_o
        io.i2c.we := current_master.we
        io.i2c.sel := current_master.sel
        io.i2c.stb := current_master.stb
        io.i2c.cyc := current_master.cyc
        master_ack := io.i2c.ack
        master_dat_i := io.i2c.dat_i
      }

      is(SlaveSelect.FLASH_CTRL) {
        io.flash_ctrl.adr := current_master.adr
        io.flash_ctrl.dat_o := current_master.dat_o
        io.flash_ctrl.we := current_master.we
        io.flash_ctrl.sel := current_master.sel
        io.flash_ctrl.stb := current_master.stb
        io.flash_ctrl.cyc := current_master.cyc
        master_ack := io.flash_ctrl.ack
        master_dat_i := io.flash_ctrl.dat_i
      }

      is(SlaveSelect.RAM) {
        io.ram.adr := current_master.adr
        io.ram.dat_o := current_master.dat_o
        io.ram.we := current_master.we
        io.ram.sel := current_master.sel
        io.ram.stb := current_master.stb
        io.ram.cyc := current_master.cyc
        master_ack := io.ram.ack
        master_dat_i := io.ram.dat_i
      }
    }
  }

  // ========== Connect Acknowledgements to Masters ==========
  io.imem_master.ack := Mux(imem_grant, master_ack, false.B)
  io.imem_master.dat_i := Mux(imem_grant, master_dat_i, 0.U)

  io.dmem_master.ack := Mux(dmem_grant, master_ack, false.B)
  io.dmem_master.dat_i := Mux(dmem_grant, master_dat_i, 0.U)
}

object Interconnect extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.bus.Interconnect"))
}
