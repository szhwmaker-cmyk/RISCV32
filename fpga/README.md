# FPGA Deployment Directory

This directory contains all files needed for FPGA deployment of the RV32E SoC.

## 📁 Directory Structure

```
fpga/
├── rtl/
│   └── fpga_top.v              # FPGA top-level wrapper (clock, reset, I/O)
├── constraints/
│   └── arty_a7.xdc             # Pin assignments and timing constraints for Arty A7
├── scripts/
│   ├── create_pll.tcl          # Generate Clock Wizard IP (100 MHz → 25 MHz)
│   ├── create_project.tcl      # Automated Vivado project creation
│   ├── build_fpga.tcl          # Synthesis → Implementation → Bitstream
│   └── program_fpga.tcl        # Program FPGA device via JTAG
├── generated/                  # Generated Verilog from Chisel (auto-created)
│   ├── MinimalSoc.v
│   ├── Core.v
│   └── ... (all other modules)
├── vivado/                     # Vivado project directory (auto-created)
│   └── rv32e_soc_fpga.xpr
├── bitstreams/                 # Generated bitstream files (auto-created)
│   └── rv32e_soc_YYYYMMDD_HHMMSS.bit
├── build_and_program.sh        # Master build script (complete flow)
└── README.md                   # This file
```

## 🚀 Quick Start

### Option 1: Automated Build and Program (Recommended)

```bash
cd fpga
./build_and_program.sh
```

This runs the complete flow:
1. Generates Verilog from Chisel
2. Creates Vivado project
3. Runs synthesis and implementation
4. Generates bitstream
5. Programs FPGA

**Time**: ~30-40 minutes (first build)

### Option 2: Manual Step-by-Step

#### Step 1: Generate Verilog
```bash
cd ..  # Go to project root
mill rv32e.runMain circt.stage.ChiselMain \
  --module rv32e.soc.MinimalSoc \
  --target-dir fpga/generated \
  --split-verilog
```

#### Step 2: Create Vivado Project
```bash
cd fpga
vivado -mode batch -source scripts/create_project.tcl
```

#### Step 3: Build Bitstream
```bash
vivado -mode batch -source scripts/build_fpga.tcl
```

#### Step 4: Program FPGA
```bash
vivado -mode batch -source scripts/program_fpga.tcl
```

### Option 3: GUI Workflow

```bash
cd fpga
vivado vivado/rv32e_soc_fpga.xpr &
```

Then use Vivado GUI:
- Flow Navigator → Run Synthesis
- Flow Navigator → Run Implementation
- Flow Navigator → Generate Bitstream
- Flow Navigator → Open Hardware Manager → Program Device

## 🎯 Supported FPGA Boards

### Primary Target: Digilent Arty A7-35T
- **FPGA**: Xilinx Artix-7 XC7A35TICSG324-1L
- **Resources**: 20,800 LUTs, 41,600 FFs, 50 BRAMs
- **SoC Usage**: ~30% LUTs, ~7% FFs, ~72% BRAMs
- **Clock**: 100 MHz input → 25 MHz system clock (via PLL)
- **Price**: ~$129

Pin mapping (defined in `constraints/arty_a7.xdc`):
- UART TX: A9 (USB-UART bridge, no external adapter needed)
- UART RX: D10
- LED[0:3]: H5, J5, T9, T10
- Buttons[0:3]: D9, C9, B9, B8
- Clock: E3 (100 MHz oscillator)

### Alternative Boards

To target a different board:
1. Create new constraints file: `constraints/your_board.xdc`
2. Modify `scripts/create_project.tcl`:
   - Set correct part number: `set fpga_part "xc7aXXXXX"`
   - Change constraints file reference
3. Update `rtl/fpga_top.v` if pin names differ

## 📊 Resource Utilization

Expected utilization on Artix-7 35T:

| Resource | Used | Available | Percentage |
|----------|------|-----------|------------|
| LUTs     | ~6,150 | 20,800 | 29.6% |
| FFs      | ~2,712 | 41,600 | 6.5% |
| BRAMs    | 36 | 50 | 72.0% |
| DSPs     | 0 | 90 | 0% |

**Note**: BRAM usage is high (72%) due to 64 KB RAM. To reduce:
- Decrease RAM size in `src/main/scala/Config.scala`
- Or use a larger FPGA (e.g., Artix-7 50T)

## ⏱️ Timing

**Target Clock**: 25 MHz (40 ns period)
**Expected Slack**: +1 ns (WNS)
**Max Frequency**: ~28 MHz (with default constraints)

If timing fails:
- Lower clock frequency in `scripts/create_pll.tcl`
- Use faster speed grade (-2 instead of -1L)
- Add pipeline stages in critical paths

## 🧪 Hardware Testing

After programming:

### 1. Power and DONE LED
- DONE LED (green, near USB) should be solid ON
- If OFF: programming failed, reprogram

### 2. LED Blink Test
- LED[0] should blink at ~2 Hz within 1 second of programming
- This indicates CPU is running and executing RT-Thread

### 3. UART Output
```bash
# Linux
screen /dev/ttyUSB1 115200

# Expected output:
RT-Thread Sim
TICK
TICK
TICK
...
```

**Note**: UART is typically `/dev/ttyUSB1` (USB0 is JTAG debugger)

## 🔧 Build Options

### Incremental Builds

Skip steps if only some files changed:

```bash
# Skip Verilog generation (use existing)
./build_and_program.sh --skip-verilog

# Skip synthesis/implementation (reprogram only)
./build_and_program.sh --skip-build

# Build but don't program
./build_and_program.sh --skip-program
```

### Clean Build

Remove all generated files:
```bash
./build_and_program.sh --clean
```

This deletes:
- `vivado/` directory
- `generated/` Verilog
- `.Xil/` temporary files

## 🐛 Troubleshooting

### "Vivado not found"
```bash
source /tools/Xilinx/Vivado/2021.2/settings64.sh
# Or add to ~/.bashrc
```

### "No FPGA device detected"
```bash
# Check USB connection
lsusb | grep Xilinx  # Should see "03fd:..."

# Check permissions (Linux)
ls -l /dev/bus/usb/*/*
sudo chmod 666 /dev/bus/usb/XXX/YYY

# Install cable drivers (first time only)
cd $XILINX_VIVADO/data/xicom/cable_drivers/lin64/install_script/install_drivers
sudo ./install_drivers
```

### "Timing not met"
Check `vivado/rv32e_soc_fpga.runs/reports/post_impl_timing.rpt`

Solutions:
- Lower clock frequency (25 MHz → 20 MHz)
- Use faster speed grade
- Add pipeline stages

### "No UART output"
- Verify baud rate: 115200 8N1
- Check correct port: `/dev/ttyUSB1` (not USB0)
- Test: `echo "test" > /dev/ttyUSB1` (should not error)

### "LED not blinking"
- Check DONE LED is ON (bitstream loaded)
- Verify clock PLL locked
- Add ILA debug cores to monitor PC and signals

## 📈 Performance Optimization

### Increase Clock Frequency

Edit `scripts/create_pll.tcl`:
```tcl
CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {30.000}  # 25 → 30 MHz
```

Rebuild and check timing. Safe range: 20-30 MHz.

### Reduce Resource Usage

Edit `src/main/scala/Config.scala`:
```scala
val RAM_SIZE_KB = 32  // 64 → 32 KB (halves BRAM usage)
```

Regenerate Verilog and rebuild.

## 📚 Additional Documentation

- **Complete deployment guide**: `/FPGA_DEPLOYMENT.md`
- **Simulation guide**: `/HOW_TO_RUN.md`
- **RT-Thread info**: `/RTTHREAD_SIMULATION.md`
- **Architecture**: `/ARCHITECTURE.md`

## 🔍 Debug with ILA

To add Integrated Logic Analyzer (ILA) for debugging:

1. Open synthesized design in Vivado GUI
2. Tools → Set Up Debug
3. Select signals to probe:
   - `soc/core/if_stage/pc` (Program Counter)
   - `soc/core/mem_stage/mem_write` (Memory writes)
   - `soc/uart/io_tx` (UART transmission)
4. Set trigger condition (e.g., PC = 0x80000000)
5. Re-run implementation
6. In Hardware Manager: capture waveform

## 📊 Reports

After building, check reports in:
```
vivado/rv32e_soc_fpga.runs/reports/
├── post_synth_util.rpt       # Synthesis utilization
├── post_synth_timing.rpt     # Synthesis timing
├── post_impl_util.rpt        # Final utilization
├── post_impl_timing.rpt      # Final timing (WNS/WHS)
├── clock_util.rpt            # Clock resource usage
├── drc.rpt                   # Design rule check
└── power.rpt                 # Power estimation
```

## ⚡ Power Consumption

Expected power on Artix-7:
- **Static**: ~0.1 W
- **Dynamic**: ~0.3-0.5 W (at 25 MHz)
- **Total**: ~0.4-0.6 W

Use external 5V adapter for best stability (USB power is marginal).

## 🎓 Learning Resources

- **Vivado User Guide**: UG973
- **Artix-7 Datasheet**: DS181
- **Arty A7 Reference Manual**: https://digilent.com/reference/arty-a7
- **Chisel to Verilog**: https://www.chisel-lang.org/

## 📋 Pre-Flight Checklist

Before running build:
- [ ] Mill installed and working
- [ ] Vivado 2021.2+ installed
- [ ] Artix-7 device files installed in Vivado
- [ ] FPGA board connected via USB
- [ ] Board powered on
- [ ] All Chisel code compiles (`mill rv32e.compile`)
- [ ] Tests pass (`mill rv32e.test`)

---

**Status**: Ready for FPGA deployment ✅
**Last Updated**: 2025-11-05
