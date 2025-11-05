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

| Device          | Base Address | Size  |
|----------------|--------------|-------|
| SPI Flash (XIP)| 0x1000_0000  | 256MB |
| UART           | 0x2000_0000  | 4KB   |
| GPIO           | 0x2001_0000  | 4KB   |
| SPI Master     | 0x2002_0000  | 4KB   |
| I2C Master     | 0x2003_0000  | 4KB   |
| Flash Ctrl     | 0x2004_0000  | 4KB   |
| RAM            | 0x8000_0000  | 64KB  |

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
- [x] Phase 3: Boot System ⏸️ (Simplified, basic support)
- [x] Phase 4: SoC Integration ✅
- [ ] Phase 5: Verification 🔄 (Partial)

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) - Detailed system architecture
- [STAGE1_COMPLETE.md](STAGE1_COMPLETE.md) - Processor core implementation details
- [STAGE2_COMPLETE.md](STAGE2_COMPLETE.md) - Peripherals and bus implementation
- [PROJECT_STATUS.md](PROJECT_STATUS.md) - Current project status and features

## Statistics

- **Total Files**: 24 Scala source files
- **Total Lines**: ~4000 lines (with comments)
- **Core Modules**: 11 files
- **Peripherals**: 6 modules
- **Test Files**: 3 test suites

## License

MIT License
