# RV32E SoC Architecture Document

## 1. System Overview

This document describes a minimal System-on-Chip (SoC) built around a 5-stage pipelined RV32E processor core.

### Key Features
- **Processor**: RV32E ISA (16 integer registers)
- **Pipeline**: 5-stage classical RISC pipeline (IF, ID, EX, MEM, WB)
- **Boot**: SPI Flash boot with code copy to RAM
- **Peripherals**: UART, GPIO, SPI Master, I2C Master
- **Bus**: Wishbone B4 Pipelined

---

## 2. System Block Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         RV32E SoC                           │
│                                                             │
│  ┌─────────────────┐                                       │
│  │   RV32E Core    │                                       │
│  │  (5-stage pipe) │                                       │
│  └────────┬────────┘                                       │
│           │                                                 │
│           │ Wishbone Master                                │
│           │                                                 │
│  ┌────────┴─────────────────────────────────────────────┐  │
│  │         Wishbone Interconnect (Crossbar)            │  │
│  │              (Address Decoder + Arbiter)            │  │
│  └────┬─────┬──────┬──────┬──────┬──────┬──────────┬───┘  │
│       │     │      │      │      │      │          │       │
│   ┌───┴─┐ ┌─┴──┐ ┌─┴──┐ ┌─┴──┐ ┌─┴──┐ ┌─┴───────┐ ┌─┴──┐  │
│   │Flash│ │UART│ │GPIO│ │SPI │ │I2C │ │FlashCtrl│ │RAM │  │
│   │(XIP)│ │    │ │    │ │Mstr│ │Mstr│ │         │ │64KB│  │
│   └─────┘ └────┘ └────┘ └────┘ └────┘ └─────────┘ └────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Bus Architecture

### 3.1 Bus Protocol Selection: **Wishbone B4 Pipelined**

**Rationale:**
- Simple, well-documented open-source protocol
- Supports pipelined transfers for better throughput
- Easy to implement and debug
- Wide adoption in open-source hardware community

### 3.2 Wishbone Signals

| Signal  | Direction | Width | Description              |
|---------|-----------|-------|--------------------------|
| adr     | Master→   | 32    | Address                  |
| dat_o   | Master→   | 32    | Write data               |
| dat_i   | ←Slave    | 32    | Read data                |
| we      | Master→   | 1     | Write enable             |
| sel     | Master→   | 4     | Byte select (active high)|
| stb     | Master→   | 1     | Strobe (valid cycle)     |
| cyc     | Master→   | 1     | Cycle valid              |
| ack     | ←Slave    | 1     | Acknowledge              |

### 3.3 Address Decoding

The interconnect decodes addresses to route transactions:

| Device          | Base Address | End Address  | Size  |
|----------------|--------------|--------------|-------|
| SPI Flash (XIP)| 0x1000_0000  | 0x1FFF_FFFF  | 256MB |
| UART           | 0x2000_0000  | 0x2000_0FFF  | 4KB   |
| GPIO           | 0x2001_0000  | 0x2001_0FFF  | 4KB   |
| SPI Master     | 0x2002_0000  | 0x2002_0FFF  | 4KB   |
| I2C Master     | 0x2003_0000  | 0x2003_0FFF  | 4KB   |
| Flash Ctrl Reg | 0x2004_0000  | 0x2004_0FFF  | 4KB   |
| RAM            | 0x8000_0000  | 0x8000_FFFF  | 64KB  |

---

## 4. Processor Core Architecture

### 4.1 Five-Stage Pipeline

```
┌────┐    ┌────┐    ┌────┐    ┌─────┐    ┌────┐
│ IF │───→│ ID │───→│ EX │───→│ MEM │───→│ WB │
└────┘    └────┘    └────┘    └─────┘    └────┘
  ↑         ↑         ↑          ↑         ↑
  │         │         │          │         │
 PC      RegFile    ALU      Data Bus   Write
Update   Read                 Access     Back
```

**Stage Description:**

1. **IF (Instruction Fetch)**
   - Fetch instruction from memory via Wishbone
   - Update PC (sequential or branch target)
   - Output: PC, Instruction

2. **ID (Instruction Decode)**
   - Decode instruction format (R/I/S/B/U/J)
   - Read register file (rs1, rs2)
   - Generate immediate values
   - Generate control signals
   - Output: Control signals, operands, immediate

3. **EX (Execute)**
   - ALU operations
   - Branch condition evaluation
   - Branch/Jump target calculation
   - Output: ALU result, branch decision

4. **MEM (Memory Access)**
   - Load/Store operations via Wishbone
   - Byte/Half-word/Word access
   - Output: Memory data (for loads)

5. **WB (Write Back)**
   - Write result to register file
   - Select source: ALU result / Memory data / PC+4

### 4.2 Hazard Handling

**Data Hazards (RAW - Read After Write):**

- **Detection**: Compare EX/MEM/WB stage rd with ID stage rs1/rs2
- **Resolution**:
  - **Forwarding**: EX→EX, MEM→EX, WB→EX
  - **Stall**: LOAD-USE hazard requires 1-cycle stall

**Control Hazards (Branches):**

- **Strategy**: Predict not-taken (continue sequential fetch)
- **Resolution**:
  - Branch resolved in EX stage
  - On misprediction: Flush IF and ID stages
  - 2-cycle penalty for taken branches

**Structural Hazards:**

- Avoided by design (separate instruction/data paths via Wishbone arbiter)

### 4.3 Register File

- **Count**: 16 registers (x0-x15) per RV32E spec
- **Width**: 32 bits
- **x0**: Hardwired to zero
- **Ports**: 2 read (async), 1 write (sync)

---

## 5. Boot System Architecture

### 5.1 Boot Flow State Machine

```
┌────────┐
│ RESET  │
└───┬────┘
    │
    ↓
┌────────────┐
│  INIT_SPI  │ ← Configure SPI Flash Controller
└─────┬──────┘
      │
      ↓
┌─────────────┐
│ READ_FLASH  │ ← Read 16KB from Flash @ 0x1000_0000
└──────┬──────┘
       │
       ↓
┌──────────────┐
│  LOAD_MEM    │ ← Copy to RAM @ 0x8000_0000
└──────┬───────┘
       │
       ↓
┌───────────────┐
│  BOOT_DONE    │ ← Set PC = 0x8000_0000
└───────┬───────┘
        │
        ↓
┌───────────────┐
│   RUN         │ ← Normal execution
└───────────────┘
```

### 5.2 Boot Modes

**Mode 1: XIP (Execute-In-Place)** - *Not implemented initially*
- CPU fetches directly from SPI Flash
- Slower but simpler

**Mode 2: Boot Loader (Implemented)**
- Small Boot ROM copies Flash → RAM
- Jump to RAM for execution
- Faster execution

### 5.3 Boot ROM Implementation

Located at a small ROM or implemented as FSM logic:

```
Pseudo-code:
1. src = SPI_FLASH_BASE (0x1000_0000)
2. dst = RAM_BASE (0x8000_0000)
3. count = BOOT_COPY_SIZE (16KB)
4. while count > 0:
     *dst = *src
     dst += 4
     src += 4
     count -= 4
5. PC = RAM_BASE
```

---

## 6. Peripheral Specifications

### 6.1 UART

**Features:**
- 8N1 format (8 data bits, no parity, 1 stop bit)
- Configurable baud rate (default 115200)
- 16-byte TX/RX FIFOs

**Register Map:**

| Offset | Name   | Access | Description              |
|--------|--------|--------|--------------------------|
| 0x00   | TXDATA | W      | Transmit data register   |
| 0x04   | RXDATA | R      | Receive data register    |
| 0x08   | STATUS | R      | Status [tx_full, rx_valid] |
| 0x0C   | BAUD   | RW     | Baud rate divisor        |

### 6.2 GPIO

**Features:**
- 16-bit parallel I/O
- Configurable direction per pin
- Output enable control

**Register Map:**

| Offset | Name     | Access | Description                |
|--------|----------|--------|----------------------------|
| 0x00   | DATA_IN  | R      | Input data                 |
| 0x04   | DATA_OUT | RW     | Output data                |
| 0x08   | DIR      | RW     | Direction (0=in, 1=out)    |
| 0x0C   | OE       | RW     | Output enable              |

### 6.3 SPI Master

**Features:**
- Standard SPI protocol
- Configurable CPOL/CPHA
- 8/16/32-bit transfers
- Clock divider

**Register Map:**

| Offset | Name   | Access | Description                |
|--------|--------|--------|----------------------------|
| 0x00   | CTRL   | RW     | Control [start, busy, cpol, cpha] |
| 0x04   | DIV    | RW     | Clock divider              |
| 0x08   | TXDATA | W      | Transmit data              |
| 0x0C   | RXDATA | R      | Receive data               |

### 6.4 I2C Master

**Features:**
- Standard (100kHz) and Fast (400kHz) modes
- 7-bit addressing
- START/STOP condition generation

**Register Map:**

| Offset | Name   | Access | Description                |
|--------|--------|--------|----------------------------|
| 0x00   | CTRL   | RW     | Control [start, stop, ack] |
| 0x04   | DIV    | RW     | Clock divider              |
| 0x08   | TXDATA | W      | Transmit data/address      |
| 0x0C   | RXDATA | R      | Receive data               |
| 0x10   | STATUS | R      | Status [busy, ack_recv]    |

### 6.5 SPI Flash Controller

**Features:**
- Read command support (0x03, 0x0B Fast Read)
- Direct memory-mapped access for XIP
- Register-based access for boot loader

**Register Map:**

| Offset | Name   | Access | Description                |
|--------|--------|--------|----------------------------|
| 0x00   | CTRL   | RW     | Control [start, busy, done]|
| 0x04   | DIV    | RW     | Clock divider              |
| 0x08   | ADDR   | RW     | Flash address (24-bit)     |
| 0x0C   | DATA   | R      | Read data                  |

---

## 7. Design Decisions Summary

| Aspect              | Decision                | Rationale                                      |
|---------------------|-------------------------|------------------------------------------------|
| **ISA**             | RV32E                   | Reduced registers for smaller area            |
| **Pipeline**        | 5-stage classical       | Balance performance and simplicity             |
| **Bus**             | Wishbone B4 Pipelined   | Simple, open-source, well-documented           |
| **Boot**            | SPI Flash → RAM copy    | Faster execution than XIP                      |
| **Branch Predict**  | Static not-taken        | Simple, low penalty with early resolution      |
| **Data Hazard**     | Forwarding + Stall      | Minimize stalls, forward when possible         |
| **Reset PC**        | 0x1000_0000             | Boot from SPI Flash address                    |
| **Main Memory**     | 64KB RAM @ 0x8000_0000  | Sufficient for embedded applications           |

---

## 8. Verification Strategy

### 8.1 Unit Testing
- Each module tested in isolation
- ChiselTest framework
- Cover all instruction types, hazards, peripheral operations

### 8.2 Integration Testing
- Full SoC simulation
- Test programs: Fibonacci, memory copy, UART echo, GPIO toggle

### 8.3 Formal Verification (Future)
- Instruction correctness proofs
- Pipeline invariant checking

---

## 9. Implementation Phases

**Phase 0**: Architecture & Config ✓
**Phase 1**: Processor Core (Stages 1.1-1.3)
**Phase 2**: Peripherals (Stages 2.1-2.2)
**Phase 3**: Boot System (Stage 3)
**Phase 4**: SoC Integration (Stage 4)
**Phase 5**: Verification (Stage 5)

---

## 10. References

- RISC-V Instruction Set Manual (Volume I: User-Level ISA)
- Wishbone B4 Specification
- Chisel/FIRRTL Documentation

---

**Document Version**: 1.0
**Last Updated**: 2025-11-05
**Status**: Architecture Approved, Ready for Implementation
