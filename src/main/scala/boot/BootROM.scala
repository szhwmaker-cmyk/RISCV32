package rv32e.boot

import chisel3._
import chisel3.util._
import rv32e.bus._
import rv32e.Config

/**
 * Boot ROM
 *
 * Contains a small boot program that:
 * 1. Copies code from SPI Flash to RAM
 * 2. Jumps to RAM to execute user program
 *
 * The Boot ROM is mapped at a special address (e.g., 0x0000_0000)
 * and the CPU starts execution from here.
 *
 * Boot Program (pseudo-assembly):
 * ```
 * _start:
 *   li   t0, SPI_FLASH_BASE    # Source address
 *   li   t1, RAM_BASE          # Destination address
 *   li   t2, COPY_SIZE         # Number of bytes to copy
 * copy_loop:
 *   lw   t3, 0(t0)             # Load word from Flash
 *   sw   t3, 0(t1)             # Store word to RAM
 *   addi t0, t0, 4             # Increment source
 *   addi t1, t1, 4             # Increment destination
 *   addi t2, t2, -4            # Decrement counter
 *   bnez t2, copy_loop         # Continue if not zero
 * jump_to_ram:
 *   li   t0, RAM_BASE          # Load RAM base address
 *   jalr x0, 0(t0)             # Jump to RAM
 * ```
 *
 * Boot ROM Content (encoded as machine code):
 */

class BootROMIO extends Bundle {
  val wb = Flipped(new WishboneMasterIO)
}

class BootROM extends Module {
  val io = IO(new BootROMIO)

  // Boot ROM size: 256 bytes (64 words)
  val rom_depth = 64
  val rom = VecInit(Seq(
    // ========== RV32E Boot Code (Machine Code) ==========
    // Generated from the assembly above using RISC-V assembler

    // li t0, 0x10000000  (lui + addi)
    "h10000537".U,  // lui  t0, 0x10000
    "h00000513".U,  // addi t0, t0, 0

    // li t1, 0x80000000  (lui + addi)
    "h800005b7".U,  // lui  t1, 0x80000
    "h00058593".U,  // addi t1, t1, 0

    // li t2, 0x4000  (16KB = 0x4000)
    "h00004637".U,  // lui  t2, 0x4
    "h00060613".U,  // addi t2, t2, 0

    // copy_loop:
    //   lw   t3, 0(t0)
    "h0002ae03".U,  // lw   t3, 0(t0)

    //   sw   t3, 0(t1)
    "h01c5a023".U,  // sw   t3, 0(t1)

    //   addi t0, t0, 4
    "h00450513".U,  // addi t0, t0, 4

    //   addi t1, t1, 4
    "h00458593".U,  // addi t1, t1, 4

    //   addi t2, t2, -4
    "hffc60613".U,  // addi t2, t2, -4

    //   bnez t2, copy_loop  (branch back to copy_loop)
    "hfe0612e3".U,  // bnez t2, -16 (copy_loop)

    // jump_to_ram:
    //   li   t0, 0x80000000
    "h800002b7".U,  // lui  t0, 0x80000
    "h00028293".U,  // addi t0, t0, 0

    //   jalr x0, 0(t0)  (jump to RAM)
    "h00028067".U,  // jalr x0, 0(t0)

    // Fill rest with NOPs (addi x0, x0, 0)
  ) ++ Seq.fill(64 - 17)("h00000013".U(32.W)))

  // Wishbone interface
  val wb_ack = RegInit(false.B)
  val read_data = RegInit(0.U(32.W))

  io.wb.ack := wb_ack
  io.wb.dat_i := read_data

  // Calculate word address (boot ROM is word-aligned)
  val word_addr = io.wb.adr(7, 2)  // 64 words = 6 bits

  when(io.wb.cyc && io.wb.stb && !wb_ack) {
    wb_ack := true.B

    when(!io.wb.we) {
      // Read operation only (Boot ROM is read-only)
      val addr_valid = word_addr < rom_depth.U
      read_data := Mux(addr_valid, rom(word_addr), 0.U)
    }
    // Writes are ignored (Boot ROM is read-only)
  }.otherwise {
    wb_ack := false.B
  }
}

/**
 * Helper object to generate boot ROM code
 */
object BootROMGenerator {
  /**
   * Generate RISC-V assembly for boot code
   */
  def generateBootAsm(): String = {
    s"""
    |# RV32E Boot ROM Code
    |# Copies ${Config.BOOT_COPY_SIZE} bytes from Flash to RAM
    |
    |.section .text
    |.globl _start
    |
    |_start:
    |    # Load source address (SPI Flash base)
    |    lui  t0, %hi(0x${Config.SPI_FLASH_BASE.toHexString})
    |    addi t0, t0, %lo(0x${Config.SPI_FLASH_BASE.toHexString})
    |
    |    # Load destination address (RAM base)
    |    lui  t1, %hi(0x${Config.RAM_BASE.toHexString})
    |    addi t1, t1, %lo(0x${Config.RAM_BASE.toHexString})
    |
    |    # Load copy size
    |    li   t2, ${Config.BOOT_COPY_SIZE}
    |
    |copy_loop:
    |    lw   t3, 0(t0)      # Load from Flash
    |    sw   t3, 0(t1)      # Store to RAM
    |    addi t0, t0, 4      # Increment source
    |    addi t1, t1, 4      # Increment dest
    |    addi t2, t2, -4     # Decrement counter
    |    bnez t2, copy_loop  # Loop if not zero
    |
    |jump_to_ram:
    |    lui  t0, %hi(0x${Config.RAM_BASE.toHexString})
    |    addi t0, t0, %lo(0x${Config.RAM_BASE.toHexString})
    |    jalr x0, 0(t0)      # Jump to RAM
    |
    |.end
    """.stripMargin
  }
}

object BootROM extends App {
  // Print boot assembly for reference
  println("Boot ROM Assembly Code:")
  println(BootROMGenerator.generateBootAsm())
  println("\nGenerating Verilog...")

  circt.stage.ChiselMain.main(args ++ Array("--module", "rv32e.boot.BootROM"))
}
