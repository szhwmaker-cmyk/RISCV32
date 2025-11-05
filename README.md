# RV32E SoC - Five-Stage Pipelined Processor

A minimal System-on-Chip featuring a RISC-V RV32E processor core with SPI Flash boot capability.

## Project Structure

```
rv32e_soc/
├── src/main/scala/
│   ├── Config.scala          # System-wide configuration
│   ├── core/                 # Processor core modules
│   ├── bus/                  # Wishbone bus system
│   ├── peripherals/          # UART, GPIO, SPI, I2C
│   ├── boot/                 # Boot controller
│   └── soc/                  # Top-level SoC integration
├── src/test/scala/           # ChiselTest test suites
├── build.sc                  # Mill build configuration
└── ARCHITECTURE.md           # Detailed architecture document

```

## Features

- **RV32E ISA**: 16 integer registers (x0-x15)
- **5-Stage Pipeline**: IF → ID → EX → MEM → WB
- **Hazard Handling**: Data forwarding + pipeline stalls
- **SPI Flash Boot**: Boot from external Flash, copy to RAM
- **Peripherals**: UART, GPIO, SPI Master, I2C Master
- **Bus**: Wishbone B4 Pipelined interconnect

## Memory Map

| Device          | Base Address | Size  | Purpose |
|----------------|--------------|-------|---------|
| **Boot ROM**   | 0x0000_0000  | 256B  | Boot code (CPU starts here) |
| SPI Flash (XIP)| 0x1000_0000  | 256MB | Program storage |
| UART           | 0x2000_0000  | 4KB   | Serial communication |
| GPIO           | 0x2001_0000  | 4KB   | General I/O |
| SPI Master     | 0x2002_0000  | 4KB   | SPI peripherals |
| I2C Master     | 0x2003_0000  | 4KB   | I2C peripherals |
| Flash Ctrl     | 0x2004_0000  | 4KB   | Flash controller regs |
| **RAM**        | 0x8000_0000  | 64KB  | Main memory (execution) |

## Build & Test

```bash
# Compile Chisel to Verilog
mill rv32e_soc.runMain circt.stage.ChiselMain --module rv32e.soc.MinimalSoc --target-dir generated

# Run tests
mill rv32e_soc.test

# Run specific test
mill rv32e_soc.test.testOnly rv32e.core.RegFileSpec
```

## Current Status

- [x] Phase 0: Architecture Design ✅
- [x] Phase 1: Processor Core ✅ (See [STAGE1_COMPLETE.md](STAGE1_COMPLETE.md))
- [x] Phase 2: Peripherals ✅ (See [STAGE2_COMPLETE.md](STAGE2_COMPLETE.md))
- [x] Phase 3: Boot System ✅ (See [STAGE3_COMPLETE.md](STAGE3_COMPLETE.md))
- [x] Phase 4: SoC Integration ✅
- [ ] Phase 5: Verification 🔄 (In progress)

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed system architecture
- [STAGE1_COMPLETE.md](STAGE1_COMPLETE.md) - Processor core implementation
- [STAGE2_COMPLETE.md](STAGE2_COMPLETE.md) - Peripherals and bus system
- [STAGE3_COMPLETE.md](STAGE3_COMPLETE.md) - Boot ROM and boot system
- [PROJECT_STATUS.md](PROJECT_STATUS.md) - Current project status

## Statistics

- **Total Files**: 27 Scala source files
- **Total Lines**: ~4600 lines (with comments)
- **Core Modules**: 11 files
- **Peripherals**: 6 modules
- **Boot System**: 2 files (BootController + BootROM)
- **Test Files**: 3 test suites

## Boot Process

1. **Power-On**: CPU resets to PC = 0x00000000 (Boot ROM)
2. **Boot ROM Execution**: Copies 16KB from Flash (0x10000000) to RAM (0x80000000)
3. **Jump to RAM**: Boot ROM jumps to 0x80000000
4. **User Program**: Application executes from RAM

## License

MIT License
