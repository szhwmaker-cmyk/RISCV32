#!/bin/bash
#
# Complete FPGA Build and Programming Script
# Runs entire flow: Verilog generation → Vivado build → Programming
#

set -e  # Exit on any error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "RV32E SoC FPGA Build and Program"
echo "========================================="
echo "Project root: $PROJECT_ROOT"
echo "FPGA dir:     $SCRIPT_DIR"
echo "Timestamp:    $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="
echo ""

# Check if Vivado is available
if ! command -v vivado &> /dev/null; then
    echo "ERROR: Vivado not found in PATH"
    echo ""
    echo "Please source Vivado settings:"
    echo "  source /tools/Xilinx/Vivado/2021.2/settings64.sh"
    echo "  Or: export PATH=/tools/Xilinx/Vivado/2021.2/bin:\$PATH"
    exit 1
fi

VIVADO_VERSION=$(vivado -version | head -1)
echo "Vivado version: $VIVADO_VERSION"
echo ""

# Parse command line options
SKIP_VERILOG=false
SKIP_BUILD=false
SKIP_PROGRAM=false
CLEAN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-verilog)
            SKIP_VERILOG=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --skip-program)
            SKIP_PROGRAM=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-verilog   Skip Chisel Verilog generation"
            echo "  --skip-build     Skip Vivado synthesis/implementation"
            echo "  --skip-program   Skip FPGA programming"
            echo "  --clean          Clean previous build artifacts"
            echo "  --help           Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                          # Full build and program"
            echo "  $0 --skip-verilog           # Use existing Verilog"
            echo "  $0 --skip-build             # Just reprogram with existing bitstream"
            echo "  $0 --skip-program           # Build only, don't program"
            echo "  $0 --clean                  # Clean and full rebuild"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Clean if requested
if [ "$CLEAN" = true ]; then
    echo "========================================="
    echo "CLEANING PREVIOUS BUILD"
    echo "========================================="
    cd "$SCRIPT_DIR"

    if [ -d "vivado" ]; then
        echo "Removing vivado project directory..."
        rm -rf vivado
    fi

    if [ -d "generated" ]; then
        echo "Removing generated Verilog..."
        rm -rf generated
    fi

    if [ -d ".Xil" ]; then
        echo "Removing Xilinx temporary files..."
        rm -rf .Xil
    fi

    echo "Clean complete"
    echo ""
fi

# Step 1: Generate Verilog from Chisel
if [ "$SKIP_VERILOG" = false ]; then
    echo "========================================="
    echo "STEP 1: Generate Verilog from Chisel"
    echo "========================================="
    cd "$PROJECT_ROOT"

    # Check if Mill is available
    if ! command -v mill &> /dev/null; then
        echo "ERROR: Mill build tool not found"
        echo ""
        echo "Install Mill:"
        echo "  curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.0/0.11.0 > mill"
        echo "  chmod +x mill"
        echo "  sudo mv mill /usr/local/bin/"
        echo ""
        echo "Or download from: https://github.com/com-lihaoyi/mill/releases"
        exit 1
    fi

    echo "Generating Verilog using Chisel..."
    echo ""

    mill rv32e.runMain circt.stage.ChiselMain \
        --module rv32e.soc.MinimalSoc \
        --target-dir fpga/generated \
        --split-verilog \
        --strip-debug-info

    if [ $? -ne 0 ]; then
        echo ""
        echo "ERROR: Verilog generation failed"
        exit 1
    fi

    echo ""
    echo "Verilog generation complete"
    echo "Generated files:"
    ls -lh "$SCRIPT_DIR/generated/"*.v | head -10
    VERILOG_COUNT=$(ls -1 "$SCRIPT_DIR/generated/"*.v 2>/dev/null | wc -l)
    echo "Total: $VERILOG_COUNT Verilog files"
    echo ""
else
    echo "========================================="
    echo "STEP 1: SKIPPED (using existing Verilog)"
    echo "========================================="
    echo ""
fi

# Step 2: Create Vivado project (if doesn't exist)
cd "$SCRIPT_DIR"

if [ ! -f "vivado/rv32e_soc_fpga.xpr" ]; then
    echo "========================================="
    echo "STEP 2: Create Vivado Project"
    echo "========================================="
    echo ""

    vivado -mode batch -source scripts/create_project.tcl

    if [ $? -ne 0 ]; then
        echo ""
        echo "ERROR: Project creation failed"
        exit 1
    fi

    echo ""
    echo "Project created successfully"
    echo ""
else
    echo "========================================="
    echo "STEP 2: Vivado project already exists"
    echo "========================================="
    echo "Using existing project: vivado/rv32e_soc_fpga.xpr"
    echo ""
fi

# Step 3: Build FPGA (synthesis, implementation, bitstream)
if [ "$SKIP_BUILD" = false ]; then
    echo "========================================="
    echo "STEP 3: Build FPGA Bitstream"
    echo "========================================="
    echo "This will take 20-30 minutes..."
    echo ""

    BUILD_START=$(date +%s)

    vivado -mode batch -source scripts/build_fpga.tcl -log build_fpga.log -journal build_fpga.jou

    if [ $? -ne 0 ]; then
        echo ""
        echo "ERROR: FPGA build failed"
        echo "Check log: fpga/build_fpga.log"
        exit 1
    fi

    BUILD_END=$(date +%s)
    BUILD_DURATION=$((BUILD_END - BUILD_START))
    BUILD_MINUTES=$((BUILD_DURATION / 60))
    BUILD_SECONDS=$((BUILD_DURATION % 60))

    echo ""
    echo "FPGA build complete in ${BUILD_MINUTES}m ${BUILD_SECONDS}s"
    echo ""

    # Find the generated bitstream
    BITSTREAM=$(ls -t bitstreams/*.bit 2>/dev/null | head -1)
    if [ -n "$BITSTREAM" ]; then
        BITSTREAM_SIZE=$(du -h "$BITSTREAM" | cut -f1)
        echo "Bitstream: $BITSTREAM ($BITSTREAM_SIZE)"
    else
        echo "WARNING: Bitstream not found in bitstreams/"
    fi
    echo ""
else
    echo "========================================="
    echo "STEP 3: SKIPPED (using existing bitstream)"
    echo "========================================="
    echo ""
fi

# Step 4: Program FPGA
if [ "$SKIP_PROGRAM" = false ]; then
    echo "========================================="
    echo "STEP 4: Program FPGA Device"
    echo "========================================="
    echo ""

    # Check if hardware is connected
    echo "Checking for connected FPGA boards..."
    if lsusb | grep -q "03fd"; then
        echo "Xilinx USB device detected"
        lsusb | grep "03fd"
    else
        echo "WARNING: No Xilinx USB device detected"
        echo "Make sure FPGA board is:"
        echo "  1. Connected via USB"
        echo "  2. Powered on"
        echo "  3. USB cable is good (try different cable)"
        echo ""
        read -p "Continue anyway? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Programming cancelled"
            exit 1
        fi
    fi

    echo ""
    echo "Programming FPGA..."

    vivado -mode batch -source scripts/program_fpga.tcl -log program_fpga.log -journal program_fpga.jou

    if [ $? -ne 0 ]; then
        echo ""
        echo "ERROR: FPGA programming failed"
        echo "Check log: fpga/program_fpga.log"
        echo ""
        echo "Troubleshooting:"
        echo "  1. Check USB connection and board power"
        echo "  2. Try: lsusb | grep Xilinx"
        echo "  3. Check USB permissions (Linux):"
        echo "     ls -l /dev/bus/usb/*/*"
        echo "     sudo chmod 666 /dev/bus/usb/XXX/YYY"
        echo "  4. Install Xilinx cable drivers (Linux):"
        echo "     cd \$XILINX_VIVADO/data/xicom/cable_drivers/lin64/install_script/install_drivers"
        echo "     sudo ./install_drivers"
        exit 1
    fi

    echo ""
    echo "Programming complete!"
    echo ""
else
    echo "========================================="
    echo "STEP 4: SKIPPED (not programming FPGA)"
    echo "========================================="
    echo ""
fi

# Final summary and next steps
echo "========================================="
echo "BUILD AND PROGRAM COMPLETE!"
echo "========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Check DONE LED on board (should be lit)"
echo ""
echo "2. Observe LED[0] blinking (~2 Hz)"
echo "   - If blinking: CPU is running! ✓"
echo "   - If not blinking: Check clock PLL or use ILA debug"
echo ""
echo "3. Connect to UART for RT-Thread output:"
echo ""
echo "   Linux:"
echo "     # Find UART device"
echo "     ls -l /dev/ttyUSB*"
echo "     # Connect (usually ttyUSB1)"
echo "     screen /dev/ttyUSB1 115200"
echo ""
echo "   Windows:"
echo "     # Use PuTTY or Tera Term"
echo "     # Port: COMx, Baud: 115200"
echo ""
echo "   Expected output:"
echo "     RT-Thread Sim"
echo "     TICK"
echo "     TICK"
echo "     ..."
echo ""
echo "4. For debugging:"
echo "   - View Vivado reports: fpga/vivado/rv32e_soc_fpga.runs/reports/"
echo "   - Check timing: post_impl_timing.rpt"
echo "   - Check utilization: post_impl_util.rpt"
echo "   - Add ILA cores for signal monitoring"
echo ""
echo "5. Project files:"

TOTAL_SIZE=$(du -sh "$SCRIPT_DIR/vivado" 2>/dev/null | cut -f1)
echo "   - Vivado project: fpga/vivado/ ($TOTAL_SIZE)"
echo "   - Bitstreams:     fpga/bitstreams/"
echo "   - Reports:        fpga/vivado/rv32e_soc_fpga.runs/reports/"
echo "   - Logs:           fpga/*.log"
echo ""

echo "========================================="
echo "Happy debugging! 🚀"
echo "========================================="
echo ""

exit 0
