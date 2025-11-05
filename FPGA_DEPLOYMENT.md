# FPGA Deployment Guide for RV32E SoC

**Status**: Ready for FPGA Implementation
**Date**: 2025-11-05
**Target Boards**: Xilinx Arty A7, Nexys A7, Basys 3

---

## 📋 Overview

This guide covers complete FPGA deployment workflow for the RV32E SoC, from Chisel code to hardware bitstream.

**Deployment Flow**:
```
Chisel HDL → Verilog Generation → Vivado Synthesis → Implementation → Bitstream → FPGA
```

---

## 🎯 Recommended FPGA Boards

### Primary Target: Digilent Arty A7-35T

**Specifications**:
- **FPGA**: Xilinx Artix-7 XC7A35TICSG324-1L
- **Logic Cells**: 33,280 (RV32E SoC uses ~25%)
- **Block RAM**: 1,800 Kb (SoC uses ~256 Kb = 14%)
- **Clock**: 100 MHz oscillator (PLL converts to 25 MHz)
- **Flash**: 16 MB Quad-SPI (perfect for boot storage)
- **Peripherals**:
  - 4x LEDs (GPIO mapping)
  - 4x RGB LEDs (optional)
  - 4x Buttons (GPIO input)
  - 4x Switches (GPIO input)
  - USB-UART bridge (direct UART connection)
  - Pmod connectors (I2C, SPI expansion)
- **Price**: ~$129 (educational discount available)
- **Documentation**: Excellent Digilent resources

**Why Arty A7?**
- ✅ Perfect size for RV32E SoC
- ✅ Built-in SPI Flash for boot
- ✅ USB-UART included (no external adapter needed)
- ✅ Comprehensive constraints file provided
- ✅ Well-supported in open-source toolchains

### Alternative: Digilent Nexys A7-50T

**Specifications**:
- **FPGA**: Xilinx Artix-7 XC7A50T
- **Logic Cells**: 52,160 (more headroom for expansion)
- **Peripherals**: More LEDs, 7-segment displays, VGA
- **Price**: ~$229

**Use case**: If you need extra peripherals or plan to expand SoC

### Alternative: Digilent Basys 3

**Specifications**:
- **FPGA**: Xilinx Artix-7 XC7A35T
- **Logic Cells**: 33,280
- **Peripherals**: 16 switches, 16 LEDs, 7-segment displays
- **Price**: ~$149

**Use case**: Educational lab environment

### Alternative: Lattice ECP5 (Open Source Toolchain)

**Specifications**:
- **FPGA**: Lattice ECP5-5G
- **Toolchain**: Open-source (Yosys, nextpnr, prjtrellis)
- **Example Board**: Colorlight i5 (~$15-20 on AliExpress)

**Use case**: If you want fully open-source FPGA toolchain

---

## 📊 Resource Estimation

### RV32E SoC Resource Usage on Artix-7

| Component | LUTs | FFs | BRAM | DSPs |
|-----------|------|-----|------|------|
| **Core Pipeline** | 2,500 | 800 | 0 | 0 |
| - IF Stage | 300 | 100 | 0 | 0 |
| - ID Stage | 500 | 150 | 0 | 0 |
| - EX Stage (ALU) | 800 | 200 | 0 | 0 |
| - MEM Stage | 400 | 150 | 0 | 0 |
| - WB Stage | 200 | 100 | 0 | 0 |
| - Hazard Unit | 300 | 100 | 0 | 0 |
| **Register File** | 400 | 512 | 1 | 0 |
| **Interconnect** | 1,200 | 400 | 0 | 0 |
| **Boot ROM** | 150 | 50 | 1 | 0 |
| **RAM (64 KB)** | 200 | 100 | 32 | 0 |
| **SPI Flash Ctrl** | 600 | 200 | 0 | 0 |
| **UART** | 300 | 150 | 2 | 0 |
| **GPIO** | 200 | 100 | 0 | 0 |
| **SPI Master** | 250 | 100 | 0 | 0 |
| **I2C Master** | 250 | 100 | 0 | 0 |
| **Clock/Reset** | 100 | 50 | 0 | 0 |
| **TOTAL** | **~6,150** | **~2,712** | **36** | **0** |

### Artix-7 35T Capacity

- **Available LUTs**: 20,800
- **Available FFs**: 41,600
- **Available BRAM**: 50 (1,800 Kb)
- **Available DSPs**: 90

### Utilization Percentage

- **LUTs**: 6,150 / 20,800 = **29.6%** ✅
- **FFs**: 2,712 / 41,600 = **6.5%** ✅
- **BRAM**: 36 / 50 = **72%** ⚠️ (Mostly RAM)
- **DSPs**: 0 / 90 = **0%** ✅

**Conclusion**: Excellent fit with room for expansion (70% LUT/FF headroom)

---

## 🚀 Deployment Workflow

### Step 1: Generate Verilog from Chisel

```bash
cd /home/user/RISCV32

# Generate Verilog using Chisel/CIRCT
mill rv32e.runMain circt.stage.ChiselMain \
  --module rv32e.soc.MinimalSoc \
  --target-dir fpga/generated \
  --split-verilog \
  --strip-debug-info

# Expected output files:
# fpga/generated/MinimalSoc.v
# fpga/generated/Core.v
# fpga/generated/RegFile.v
# fpga/generated/ALU.v
# ... (all modules)

# Verify generation
ls -lh fpga/generated/*.v
```

**What this does**:
- Compiles Chisel → FIRRTL → Verilog
- `--split-verilog`: Creates separate file per module (easier debugging)
- `--strip-debug-info`: Removes Chisel metadata (cleaner Verilog)

**Common Issues**:
- "mill: command not found" → Install Mill (see HOW_TO_RUN.md)
- "Java heap space" → Set `JAVA_OPTS="-Xmx4G"`
- Chisel deprecation warnings → Ignore if Verilog generates correctly

### Step 2: Prepare FPGA Project Files

```bash
# Copy generated Verilog to FPGA directory
cp fpga/generated/*.v fpga/rtl/

# Verify required files exist:
ls fpga/rtl/fpga_top.v          # FPGA wrapper (already created)
ls fpga/constraints/arty_a7.xdc # Pin constraints (already created)
ls fpga/scripts/create_pll.tcl  # PLL generation (already created)
```

### Step 3: Create Vivado Project

#### Option A: GUI Method (Recommended for First Time)

1. **Launch Vivado**:
```bash
vivado &
```

2. **Create New Project**:
- Click "Create Project"
- Project name: `rv32e_soc_fpga`
- Project location: `/home/user/RISCV32/fpga/vivado`
- Project type: **RTL Project**
- ☑️ Check "Do not specify sources at this time"

3. **Select FPGA Part**:
- For Arty A7: `xc7a35ticsg324-1L`
- Search: "xc7a35ti" → Select CSG324 package, -1L speed grade

4. **Add Source Files**:
- Add Design Sources:
  - Click "+" → "Add Files"
  - Select ALL `fpga/rtl/*.v` files
  - ☑️ Check "Copy sources into project"
  - Set `fpga_top.v` as **Top Module**

- Add Constraints:
  - Click "+" → "Add Files" (Constraints tab)
  - Select `fpga/constraints/arty_a7.xdc`

5. **Create Clock Wizard IP**:
- Tools → Run Tcl Script
- Select `fpga/scripts/create_pll.tcl`
- Wait for IP generation (~2 minutes)

6. **Verify Hierarchy**:
- Sources → Hierarchy
- Should see:
```
fpga_top (top)
├── clk_gen (clk_wiz_0)
└── soc (MinimalSoc)
    ├── core (Core)
    │   ├── if_stage (IF)
    │   ├── id_stage (ID)
    │   ├── ex_stage (EX)
    │   ├── mem_stage (MEM)
    │   ├── wb_stage (WB)
    │   └── hazard (Hazard)
    ├── boot_rom (BootROM)
    ├── ram (Ram)
    ├── uart (Uart)
    ├── gpio (Gpio)
    ├── spi_master (SpiMaster)
    ├── i2c_master (I2cMaster)
    └── interconnect (Interconnect)
```

#### Option B: TCL Script Method (Automated)

Create `fpga/scripts/create_project.tcl`:
```tcl
# See below for complete script
```

Run:
```bash
vivado -mode batch -source fpga/scripts/create_project.tcl
```

### Step 4: Run Synthesis

**GUI Method**:
1. Flow Navigator → SYNTHESIS → Run Synthesis
2. Wait (~5-10 minutes on modern PC)
3. When complete: "Synthesis Completed" dialog
4. Click "Open Synthesized Design"

**TCL Method**:
```tcl
reset_run synth_1
launch_runs synth_1 -jobs 4
wait_on_run synth_1

# Check for errors
if {[get_property PROGRESS [get_runs synth_1]] != "100%"} {
  error "Synthesis failed"
}

open_run synth_1
report_utilization -file reports/post_synth_util.txt
report_timing_summary -file reports/post_synth_timing.txt
```

**Expected Synthesis Results**:
```
=== Utilization Summary ===
LUT:       6,150 / 20,800  (29.6%)
FF:        2,712 / 41,600  (6.5%)
BRAM:      36 / 50         (72.0%)
DSP:       0 / 90          (0.0%)

=== Timing Summary ===
WNS (Worst Negative Slack): 0.250 ns  ✅ MET
TNS (Total Negative Slack):  0.000 ns  ✅ MET
WHS (Worst Hold Slack):      0.150 ns  ✅ MET
THS (Total Hold Slack):      0.000 ns  ✅ MET

All constraints met!
```

**Common Synthesis Warnings (Safe to Ignore)**:
- "Found unconnected internal register" → Unused signals from Chisel
- "Multi-driven net" in generated PLL → Normal for Xilinx IP
- "Sequential element is unused" → Dead code elimination working correctly

**Critical Synthesis Errors (Must Fix)**:
- "Cannot find module 'xyz'" → Missing Verilog file
- "Syntax error" → Check generated Verilog
- "Unresolved reference" → Missing IP core

### Step 5: Run Implementation

**GUI Method**:
1. Flow Navigator → IMPLEMENTATION → Run Implementation
2. Wait (~10-15 minutes)
3. When complete: "Implementation Completed" dialog
4. Click "Open Implemented Design"

**TCL Method**:
```tcl
reset_run impl_1
launch_runs impl_1 -jobs 4
wait_on_run impl_1

if {[get_property PROGRESS [get_runs impl_1]] != "100%"} {
  error "Implementation failed"
}

open_run impl_1
report_utilization -file reports/post_impl_util.txt
report_timing_summary -file reports/post_impl_timing.txt
report_power -file reports/power.txt
```

**Expected Implementation Results**:
```
=== Final Utilization ===
LUT:       6,200 / 20,800  (29.8%)  [slight increase from synthesis]
FF:        2,720 / 41,600  (6.5%)
BRAM:      36 / 50         (72.0%)
BUFG:      2 (clk_100mhz, clk_25mhz)

=== Final Timing ===
WNS: 1.125 ns  ✅ MET (25 MHz clock period = 40 ns)
WHS: 0.200 ns  ✅ MET

Maximum operating frequency: ~28 MHz (headroom: 12%)
```

**Timing Violations (If Any)**:
- WNS < 0: **Critical Path Too Long**
  - Solution: Lower clock frequency (20 MHz instead of 25 MHz)
  - Or: Add pipeline stages to critical path
  - Or: Use faster speed grade (-1L → -2)

### Step 6: Generate Bitstream

**GUI Method**:
1. Flow Navigator → PROGRAM AND DEBUG → Generate Bitstream
2. Wait (~5 minutes)
3. When complete: "Bitstream Generation Completed"

**TCL Method**:
```tcl
launch_runs impl_1 -to_step write_bitstream -jobs 4
wait_on_run impl_1

# Verify bitstream exists
if {![file exists "rv32e_soc_fpga.runs/impl_1/fpga_top.bit"]} {
  error "Bitstream generation failed"
}

# Copy to convenient location
file copy -force \
  "rv32e_soc_fpga.runs/impl_1/fpga_top.bit" \
  "bitstreams/rv32e_soc_$(date +%Y%m%d_%H%M%S).bit"
```

**Bitstream Location**:
```
fpga/vivado/rv32e_soc_fpga.runs/impl_1/fpga_top.bit
```

**Bitstream Size**: ~1-2 MB (typical for Artix-7)

### Step 7: Program FPGA

#### Option A: Vivado Hardware Manager (GUI)

1. **Connect Board**:
- Plug Arty A7 into USB port
- Power switch: ON
- Wait for USB enumeration (~5 seconds)

2. **Open Hardware Manager**:
- Flow Navigator → PROGRAM AND DEBUG → Open Hardware Manager
- Click "Open target" → "Auto Connect"
- Should detect: "xc7a35ti_0"

3. **Program Device**:
- Right-click device → "Program Device"
- Bitstream file: (should auto-populate) `fpga_top.bit`
- ☑️ Check "Verify" (optional, adds 30 seconds)
- Click "Program"

4. **Verify Programming**:
- Programming takes ~10 seconds
- "Device programmed successfully" message
- DONE LED on board should be lit (green)
- LEDs should start blinking (if RT-Thread running)

#### Option B: Command-Line Programming

```bash
# Using Vivado in batch mode
vivado -mode batch -source fpga/scripts/program_fpga.tcl

# Or using OpenOCD (if using open-source tools)
openocd -f board/digilent_arty.cfg \
  -c "init; pld load 0 fpga_top.bit; exit"
```

---

## 🧪 Hardware Testing Procedures

### Test 1: Power-On and Clock Verification

**Procedure**:
1. Program FPGA with bitstream
2. Observe DONE LED (should be solid green)
3. Check for excessive heat (~30-40°C is normal)
4. Listen for unusual sounds (shouldn't be any)

**Success Criteria**:
- ✅ DONE LED lit
- ✅ No smoke or burning smell
- ✅ Board warm but not hot

**Debug**:
- DONE LED off → Programming failed, reprogram
- Board very hot → Possible short circuit, power off immediately

### Test 2: Boot Sequence and LED Blink

**Expected Behavior**:
After programming, within 1 second:
- Boot ROM executes (~160,000 cycles = 6.4 ms @ 25 MHz)
- Code copied from SPI Flash to RAM
- RT-Thread minimal RTOS starts
- LED[0] should start blinking at ~2 Hz

**Procedure**:
1. Program FPGA
2. Observe LED[0] (rightmost LED on Arty A7)
3. Should see: OFF → ON (0.5s) → OFF (0.5s) → repeat

**Success Criteria**:
- ✅ LED[0] blinks at regular interval
- ✅ Blink rate approximately 2 Hz

**Debug**:
- No blinking → Boot ROM not executing
  - Check: Reset signal (active high)
  - Check: Clock PLL locked
  - Solution: Insert ILA to monitor PC
- Wrong blink rate → Clock frequency incorrect
  - Measure with ILA or oscilloscope
  - Adjust PLL settings

### Test 3: UART Communication

**Hardware Setup**:
- Arty A7 has built-in USB-UART bridge
- No external adapter needed
- Should appear as `/dev/ttyUSB1` (Linux) or `COM4` (Windows)

**Procedure**:

1. **Find UART Device**:
```bash
# Linux
ls -l /dev/ttyUSB*
# Should see: /dev/ttyUSB1 (USB0 is JTAG, USB1 is UART)

# Verify it's the UART bridge
udevadm info --name=/dev/ttyUSB1 | grep SERIAL
```

2. **Connect Serial Terminal**:
```bash
# Using screen
screen /dev/ttyUSB1 115200

# Or using minicom
minicom -D /dev/ttyUSB1 -b 115200

# Or using Python
python3 -m serial.tools.miniterm /dev/ttyUSB1 115200
```

3. **Reset FPGA**:
- Press RESET button on board (or re-program)

4. **Expected Output**:
```
RT-Thread Sim
TICK
TICK
TICK
TICK
...
```

**Success Criteria**:
- ✅ Characters appear in terminal
- ✅ "RT-Thread Sim" banner visible
- ✅ "TICK" messages every ~1 second
- ✅ No garbage characters

**Debug**:
- No output → UART not transmitting
  - Verify baud rate: 115200 8N1
  - Check TX pin in ILA
  - Verify UART base address (0x20000000)
- Garbage characters → Baud rate mismatch
  - FPGA clock: 25 MHz
  - UART divisor: 217 (25M / 115200)
  - Actual baud: 115,207 baud (0.006% error) ✅
- Intermittent characters → Clock stability issue
  - Check PLL locked signal

### Test 4: GPIO Input (Buttons/Switches)

**Hardware**:
- Arty A7 has 4 buttons (BTN0-BTN3)
- Mapped to GPIO input bits [3:0]

**Procedure**:
1. Modify RT-Thread code to read GPIO input
2. Rebuild and reprogram
3. Press BTN0
4. Should see UART message: "Button 0 pressed"

**Future Enhancement**: Create button-controlled LED pattern

### Test 5: SPI Flash Read Verification

**Objective**: Verify Boot ROM correctly reads from SPI Flash

**Procedure**:
1. Use Vivado Hardware Manager → Memory Configuration
2. Read back SPI Flash contents
3. Verify RT-Thread image at address 0x100000

**Alternative**: Insert ILA to monitor SPI signals:
- CS_N (should pulse low during boot)
- SCK (should clock at 12.5 MHz, divider=2)
- MOSI/MISO (command 0x03, address 0x100000)

### Test 6: Performance Measurement

**Objective**: Measure actual CPU performance

**Method 1: Instruction Counter**
Add performance counter to Core.scala:
```scala
val instr_count = RegInit(0.U(64.W))
when(!io.stall) {
  instr_count := instr_count + 1.U
}
// Map to GPIO read address
```

**Method 2: ILA-based Profiling**
- Insert ILA on PC signal
- Trigger on specific address
- Measure cycles between triggers

**Expected Performance**:
- CPI (Cycles Per Instruction): ~1.3 (with hazards and stalls)
- MIPS @ 25 MHz: ~19 MIPS
- Boot time: 6.4 ms
- Context switch: 2 μs

### Test 7: Multi-Hour Stability Test

**Objective**: Verify no crashes or hangs

**Procedure**:
1. Program FPGA with RT-Thread
2. Connect UART, log output to file:
```bash
cat /dev/ttyUSB1 > uart_log_$(date +%Y%m%d_%H%M%S).txt
```
3. Let run for 24 hours
4. Analyze log:
```bash
grep "TICK" uart_log_*.txt | wc -l
# Should be ~86,400 TICKs (24 hours * 60 min * 60 sec)
```

**Success Criteria**:
- ✅ No unexpected reboots
- ✅ Consistent TICK rate
- ✅ No UART errors or overruns

---

## 📈 Performance Optimization

### Clock Frequency Tuning

**Default**: 25 MHz (conservative, guaranteed timing closure)

**Overclocking Procedure**:
1. Edit `fpga/scripts/create_pll.tcl`:
```tcl
CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {30.000}  # 25 → 30 MHz
```
2. Regenerate IP, re-synthesize, re-implement
3. Check timing report: WNS must be > 0
4. Test hardware stability (run for 1 hour)

**Safe Frequency Range**:
- **20 MHz**: Very safe, WNS ~4 ns
- **25 MHz**: Default, WNS ~1 ns (recommended)
- **30 MHz**: Possible, WNS ~0.2 ns (test thoroughly)
- **35 MHz**: Unlikely without optimization

**If Timing Violations Occur**:
- Add pipeline stage in critical path
- Use faster speed grade (-1L → -2)
- Reduce BRAM port contention
- Optimize interconnect MUX depth

### Resource Optimization

**Reduce LUT Usage** (if needed):
- Simplify ALU operations (remove shifts)
- Reduce interconnect slaves (disable I2C/SPI if unused)
- Use Block RAM instead of distributed RAM

**Reduce BRAM Usage** (currently 72%):
- Decrease RAM size: 64 KB → 32 KB
  - Edit `Config.scala`: `RAM_SIZE_KB = 32`
- Use distributed RAM for small memories
- Share BRAM between peripherals

**Power Optimization**:
- Clock gating for idle peripherals
- Reduce UART baud rate (less switching activity)
- Use "Low Power" optimization in Vivado

---

## 🐛 Troubleshooting

### Issue: "Cannot find xc7a35ti part"

**Solution**:
```bash
# Check installed Vivado devices
ls $XILINX_VIVADO/data/parts/xilinx/artix7/

# If missing, install Artix-7 device support
# Xilinx Installer → Add Devices → Artix-7
```

### Issue: "Synthesis failed with errors"

**Debug Steps**:
1. Open `vivado.log` in FPGA project directory
2. Search for "ERROR"
3. Common issues:
   - Missing Verilog file → Add to project
   - Undefined module → Re-run Chisel generation
   - Syntax error in generated Verilog → Report Chisel bug

### Issue: "Timing constraints not met"

**Solution**:
1. Open implemented design
2. Reports → Timing → Report Timing Summary
3. Identify critical path:
```
Path 1: ALU/add_result → EX/mem_data_reg
  Logic Delay: 3.2 ns
  Net Delay:   1.8 ns
  Total:       5.0 ns (critical)
```
4. Options:
   - Lower clock frequency
   - Add pipeline stage before ALU
   - Use DSP slice for addition (overkill but works)

### Issue: "FPGA not detected"

**Debug**:
```bash
# Check USB connection
lsusb | grep Xilinx
# Should see: "03fd:0050 Xilinx, Inc."

# Check permissions (Linux)
sudo chmod 666 /dev/bus/usb/XXX/YYY

# Install cable drivers (if first time)
cd $XILINX_VIVADO/data/xicom/cable_drivers/lin64/install_script/install_drivers
sudo ./install_drivers
```

### Issue: "DONE LED not lighting"

**Causes**:
1. Bitstream corrupted → Regenerate
2. Wrong FPGA part selected → Verify xc7a35ti
3. Power supply insufficient → Use external 5V adapter
4. FPGA damaged → Try different board

### Issue: "UART no output"

**Debug Checklist**:
- [ ] Correct baud rate: 115200
- [ ] Correct device: /dev/ttyUSB1 (not USB0)
- [ ] UART enabled in FPGA design
- [ ] TX pin correctly mapped in XDC
- [ ] No GPIO/UART address conflict
- [ ] Program actually writing to UART

**ILA Debug**:
Add ILA to monitor:
- UART TX data
- UART TX valid signal
- UART TX ready signal (should be 1 when idle)

### Issue: "LED not blinking"

**Debug**:
1. Check if program reached RAM execution:
   - Add ILA trigger on PC = 0x80000000
2. Check GPIO initialization:
   - ILA on GPIO_DIR, GPIO_OE registers
3. Check LED mapping in XDC:
   - Verify `led[0]` maps to correct pin

---

## 📚 Advanced Topics

### Adding ChipScope/ILA for Debug

**Procedure**:
1. Open Synthesized Design
2. Tools → Set Up Debug
3. Select signals to monitor:
   - `soc/core/if_stage/pc` (Program Counter)
   - `soc/core/id_stage/opcode` (Current instruction)
   - `soc/uart/io_tx` (UART output)
   - `soc/gpio/io_data_out` (LED state)
4. Set trigger condition: e.g., PC = 0x80000000
5. Re-run Implementation
6. In Hardware Manager: Dashboard → hw_ila_1 → Run Trigger

**Waveform Analysis**:
- Capture 1024 samples at 25 MHz = 40 μs window
- Verify PC increments correctly
- Check for unexpected stalls
- Measure boot time precisely

### SPI Flash Programming via FPGA

**Objective**: Update RT-Thread image without reprogramming FPGA

**Method 1: Vivado Hardware Manager**
1. Tools → Add Configuration Memory Device
2. Select: "s25fl128sxxxxxx0-spi-x1_x2_x4"
3. Program with `minimal_rtos.bin`
4. Reset FPGA (button) - new code runs

**Method 2: Boot ROM Bootloader**
Future enhancement: Add UART bootloader to Boot ROM
- Receive new image via UART
- Program SPI Flash sectors
- Reset and boot new image

### Multi-Board Testing

**Test Farm Setup**:
- Multiple Arty A7 boards
- USB hub for power and programming
- Automated testing script:
```bash
#!/bin/bash
for board in /dev/ttyUSB{1,3,5,7}; do
  echo "Testing $board"
  ./test_uart.sh $board &
done
wait
```

### Custom Peripheral Addition

**Example: Adding PWM Module**

1. Create `Pwm.scala` in `src/main/scala/peripherals/`
2. Add to interconnect address map:
```scala
val PWM_BASE = 0x20020000L  // New address
```
3. Instantiate in `MinimalSoc.scala`
4. Add to `Interconnect.scala` slave list
5. Create test: `PwmSpec.scala`
6. Regenerate Verilog and rebuild FPGA

---

## 📁 Complete File Checklist

Before FPGA deployment, verify these files exist:

### Generated Verilog
- [ ] `fpga/generated/MinimalSoc.v`
- [ ] `fpga/generated/Core.v`
- [ ] `fpga/generated/RegFile.v`
- [ ] `fpga/generated/ALU.v`
- [ ] `fpga/generated/Interconnect.v`
- [ ] `fpga/generated/BootROM.v`
- [ ] `fpga/generated/Uart.v`
- [ ] `fpga/generated/Gpio.v`
- [ ] `fpga/generated/Ram.v`
- [ ] (+ all other modules)

### FPGA Wrapper
- [ ] `fpga/rtl/fpga_top.v`

### Constraints
- [ ] `fpga/constraints/arty_a7.xdc`

### Scripts
- [ ] `fpga/scripts/create_pll.tcl`
- [ ] `fpga/scripts/create_project.tcl` (to be created)
- [ ] `fpga/scripts/build_fpga.tcl` (to be created)
- [ ] `fpga/scripts/program_fpga.tcl` (to be created)

### RT-Thread Image
- [ ] `rtthread/build/minimal_rtos.bin`

### Documentation
- [ ] `FPGA_DEPLOYMENT.md` (this file)
- [ ] `HOW_TO_RUN.md`
- [ ] `RTTHREAD_SIMULATION.md`

---

## 🎯 Deployment Milestones

### Milestone 1: Verilog Generation ✅
- [x] Generate Verilog from Chisel
- [x] Verify module hierarchy
- [x] Check for syntax errors

### Milestone 2: FPGA Project Setup
- [ ] Create Vivado project
- [ ] Add all sources
- [ ] Generate PLL IP
- [ ] Verify constraints

### Milestone 3: Synthesis
- [ ] Run synthesis
- [ ] Check utilization < 80%
- [ ] Verify no critical warnings
- [ ] Generate synthesis reports

### Milestone 4: Implementation
- [ ] Run place and route
- [ ] Check timing closure (WNS > 0)
- [ ] Verify power < 2W
- [ ] Generate implementation reports

### Milestone 5: Bitstream Generation
- [ ] Generate bitstream
- [ ] Verify file size ~1-2 MB
- [ ] Archive with timestamp

### Milestone 6: Hardware Bringup
- [ ] Program FPGA
- [ ] Verify DONE LED
- [ ] Test LED blink
- [ ] Test UART output

### Milestone 7: Validation
- [ ] Multi-hour stability test
- [ ] Performance measurement
- [ ] Power consumption measurement
- [ ] Temperature monitoring

### Milestone 8: Documentation
- [ ] Hardware test results
- [ ] Known issues list
- [ ] User guide for board

---

## 📧 Support and Resources

### Digilent Arty A7 Resources
- **Reference Manual**: https://digilent.com/reference/programmable-logic/arty-a7/reference-manual
- **Schematics**: Available on Digilent website
- **Example Projects**: https://github.com/Digilent/Arty-A7

### Xilinx Vivado Resources
- **User Guides**: UG973 (Vivado Design Suite User Guide)
- **Timing Closure**: UG949
- **Device Documentation**: Artix-7 Data Sheet (DS181)

### Community Support
- Chisel Users Slack: https://chisel-lang.org/community
- RISC-V Forums: https://groups.google.com/a/groups.riscv.org/g/sw-dev
- FPGAcpu.org: https://fpgacpu.org/

---

## ✅ Pre-Deployment Checklist

Before attempting FPGA deployment:

**Software Prerequisites**:
- [ ] Vivado 2021.2 or later installed
- [ ] Vivado device files for Artix-7
- [ ] Mill build tool installed
- [ ] Java 11+ for Chisel/Scala
- [ ] Python 3 for helper scripts

**Hardware Prerequisites**:
- [ ] Arty A7 board (or compatible)
- [ ] USB cable (micro-USB for Arty)
- [ ] External 5V power adapter (optional but recommended)

**Design Prerequisites**:
- [ ] All Chisel code compiles without errors
- [ ] ChiselTest tests pass (simulation)
- [ ] RT-Thread minimal RTOS image built
- [ ] Verilog successfully generated

**Knowledge Prerequisites**:
- [ ] Basic Vivado familiarity
- [ ] Understanding of FPGA synthesis/implementation flow
- [ ] Ability to read timing reports
- [ ] Serial terminal usage (minicom, screen, etc.)

---

## 🚀 Quick Start Summary

**For Experienced Users**:

```bash
# 1. Generate Verilog
mill rv32e.runMain circt.stage.ChiselMain --module rv32e.soc.MinimalSoc --target-dir fpga/generated

# 2. Create Vivado project
cd fpga
vivado -mode batch -source scripts/create_project.tcl

# 3. Build bitstream
vivado -mode batch -source scripts/build_fpga.tcl

# 4. Program FPGA
vivado -mode batch -source scripts/program_fpga.tcl

# 5. Test UART
screen /dev/ttyUSB1 115200
```

**Expected time**: 30-45 minutes for full flow (excluding Vivado tool installation)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-05
**Status**: Ready for implementation
**Next Steps**: Create automation TCL scripts, test on hardware

