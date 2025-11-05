package rv32e.peripherals

import chisel3._
import chisel3.util._
import rv32e.bus._
import rv32e.Config

/**
 * Simple RAM Module
 *
 * Synchronous read/write RAM with Wishbone interface
 * Size: 64KB (configurable via Config.RAM_SIZE)
 *
 * Features:
 * - Single-port RAM
 * - Byte-addressable with byte-select support
 * - Synchronous read (1-cycle latency)
 * - Can be initialized with boot code
 */

class RamIO extends Bundle {
  // Wishbone slave interface
  val wb = Flipped(new WishboneMasterIO)
}

class Ram extends Module {
  val io = IO(new RamIO)

  // Calculate RAM depth (number of 32-bit words)
  val ram_depth = (Config.RAM_SIZE / 4).toInt
  val addr_bits = log2Ceil(ram_depth)

  // RAM storage
  val ram = SyncReadMem(ram_depth, UInt(32.W))

  // Wishbone interface logic
  val wb_ack = RegInit(false.B)
  val read_data = RegInit(0.U(32.W))

  io.wb.ack := wb_ack
  io.wb.dat_i := read_data

  // Address calculation (word-aligned)
  val word_addr = io.wb.adr(addr_bits + 1, 2)

  when(io.wb.cyc && io.wb.stb && !wb_ack) {
    wb_ack := true.B

    when(io.wb.we) {
      // Write operation with byte-select
      val current_data = ram.read(word_addr)
      val write_mask = Cat(
        Fill(8, io.wb.sel(3)),
        Fill(8, io.wb.sel(2)),
        Fill(8, io.wb.sel(1)),
        Fill(8, io.wb.sel(0))
      )
      val masked_write_data = (io.wb.dat_o & write_mask) | (current_data & ~write_mask)
      ram.write(word_addr, masked_write_data)
    }.otherwise {
      // Read operation
      read_data := ram.read(word_addr)
    }
  }.otherwise {
    wb_ack := false.B
  }
}

/**
 * RAM with Initialization
 *
 * Can be preloaded with program code for boot
 */
class InitRam(init_file: Option[String] = None) extends Module {
  val io = IO(new RamIO)

  val ram_depth = (Config.RAM_SIZE / 4).toInt
  val addr_bits = log2Ceil(ram_depth)

  // Initialize RAM from file if provided
  val ram = init_file match {
    case Some(file) => {
      val mem = SyncReadMem(ram_depth, UInt(32.W))
      // Note: Actual file loading would use loadMemoryFromFile in Chisel
      // For simulation: loadMemoryFromFileInline(mem, file)
      mem
    }
    case None => SyncReadMem(ram_depth, UInt(32.W))
  }

  val wb_ack = RegInit(false.B)
  val read_data = RegInit(0.U(32.W))

  io.wb.ack := wb_ack
  io.wb.dat_i := read_data

  val word_addr = io.wb.adr(addr_bits + 1, 2)

  when(io.wb.cyc && io.wb.stb && !wb_ack) {
    wb_ack := true.B

    when(io.wb.we) {
      val current_data = ram.read(word_addr)
      val write_mask = Cat(
        Fill(8, io.wb.sel(3)),
        Fill(8, io.wb.sel(2)),
        Fill(8, io.wb.sel(1)),
        Fill(8, io.wb.sel(0))
      )
      val masked_write_data = (io.wb.dat_o & write_mask) | (current_data & ~write_mask)
      ram.write(word_addr, masked_write_data)
    }.otherwise {
      read_data := ram.read(word_addr)
    }
  }.otherwise {
    wb_ack := false.B
  }
}

object Ram extends App {
  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.peripherals.Ram"))
}
