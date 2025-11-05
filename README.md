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
mill rv32e_soc.runMain circt.stage.ChiselMain --module rv32e.MinimalSoc --target-dir generated

# Run tests
mill rv32e_soc.test

# Run specific test
mill rv32e_soc.test.testOnly rv32e.RegFileSpec
```

## Current Status

- [x] Phase 0: Architecture Design
- [ ] Phase 1: Processor Core
- [ ] Phase 2: Peripherals
- [ ] Phase 3: Boot System
- [ ] Phase 4: SoC Integration
- [ ] Phase 5: Verification

## Documentation

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed system architecture.

## License

MIT License
