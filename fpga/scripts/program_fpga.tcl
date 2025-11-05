#
# Vivado TCL Script: Program FPGA Device
# Connects to hardware and programs bitstream
#

set project_name "rv32e_soc_fpga"
set project_dir "./vivado"
set project_file "$project_dir/$project_name.xpr"

puts "========================================="
puts "FPGA Programming Script"
puts "========================================="
puts "Timestamp: [clock format [clock seconds] -format {%Y-%m-%d %H:%M:%S}]"
puts ""

# Find the most recent bitstream in bitstreams directory
if {[file exists "./bitstreams"]} {
    set bitstream_files [glob -nocomplain "./bitstreams/*.bit"]
    if {[llength $bitstream_files] > 0} {
        # Sort by modification time and get most recent
        set bitstream_files [lsort -decreasing -command {
            lambda {a b} {expr {[file mtime $a] - [file mtime $b]}}
        } $bitstream_files]
        set bitstream_file [lindex $bitstream_files 0]
        puts "Using bitstream: [file tail $bitstream_file]"
        puts "Modified: [clock format [file mtime $bitstream_file] -format {%Y-%m-%d %H:%M:%S}]"
    } else {
        puts "ERROR: No bitstream files found in ./bitstreams/"
        puts "Build FPGA first: vivado -mode batch -source scripts/build_fpga.tcl"
        exit 1
    }
} else {
    # Try default location
    set bitstream_file "$project_dir/$project_name.runs/impl_1/fpga_top.bit"
    if {![file exists $bitstream_file]} {
        puts "ERROR: Bitstream file not found"
        puts "Tried: $bitstream_file"
        puts "Build FPGA first: vivado -mode batch -source scripts/build_fpga.tcl"
        exit 1
    }
    puts "Using default bitstream: $bitstream_file"
}

puts ""

# Bitstream size info
set bitstream_size [file size $bitstream_file]
set bitstream_size_kb [expr $bitstream_size / 1024]
puts "Bitstream size: $bitstream_size_kb KB"

puts "\n========================================="
puts "STEP 1: Connect to Hardware"
puts "========================================="

# Open hardware manager
puts "Opening hardware manager..."
open_hw_manager

# Try to connect to local hardware server
puts "Connecting to hardware server..."
if {[catch {connect_hw_server -allow_non_jtag} result]} {
    puts "ERROR: Failed to connect to hardware server"
    puts "Error: $result"
    puts ""
    puts "Troubleshooting:"
    puts "  1. Check if FPGA board is connected via USB"
    puts "  2. Check if hw_server is running: ps aux | grep hw_server"
    puts "  3. Try manually: vivado → Open Hardware Manager → Open target"
    close_hw_manager
    exit 1
}

puts "Hardware server connected"

# Scan for hardware targets
puts "\nScanning for hardware targets..."
if {[catch {open_hw_target} result]} {
    puts "ERROR: Failed to open hardware target"
    puts "Error: $result"
    puts ""
    puts "Possible causes:"
    puts "  1. No FPGA board detected"
    puts "  2. USB cable not connected"
    puts "  3. Board power switch OFF"
    puts "  4. USB device permissions (Linux: check /dev/bus/usb permissions)"
    puts ""
    puts "For Linux permission issues, try:"
    puts "  sudo chmod 666 /dev/bus/usb/XXX/YYY"
    puts "  Or install Xilinx cable drivers:"
    puts "  \$XILINX_VIVADO/data/xicom/cable_drivers/lin64/install_script/install_drivers/install_drivers"
    close_hw_manager
    exit 1
}

# Get hardware devices
set hw_devices [get_hw_devices]
if {[llength $hw_devices] == 0} {
    puts "ERROR: No FPGA devices found"
    puts ""
    puts "Check:"
    puts "  1. Board is powered on"
    puts "  2. USB cable is connected properly"
    puts "  3. lsusb shows Xilinx device (ID 03fd:...)"
    close_hw_target
    close_hw_manager
    exit 1
}

puts "\nFound [llength $hw_devices] device(s):"
foreach dev $hw_devices {
    puts "  - [get_property PART $dev] ([get_property PROGRAM.FILE $dev])"
}

# Select first device (typically xc7a35ti for Arty A7)
set hw_device [lindex $hw_devices 0]
current_hw_device $hw_device

set device_part [get_property PART $hw_device]
puts "\nSelected device: $device_part"

# Verify it's the expected part for Arty A7
if {![string match "*xc7a35ti*" $device_part] && ![string match "*xc7a35t*" $device_part]} {
    puts "WARNING: Device is not Artix-7 35T"
    puts "Expected: xc7a35ti or xc7a35t"
    puts "Found: $device_part"
    puts "Continue anyway? (bitstream may be incompatible)"
}

puts "\n========================================="
puts "STEP 2: Program Device"
puts "========================================="

# Set bitstream file
puts "Loading bitstream: [file tail $bitstream_file]"
set_property PROGRAM.FILE $bitstream_file $hw_device

# Optionally set debug probes file if it exists
set probes_file "[file rootname $bitstream_file].ltx"
if {[file exists $probes_file]} {
    puts "Loading debug probes: [file tail $probes_file]"
    set_property PROBES.FILE $probes_file $hw_device
}

# Refresh device
refresh_hw_device $hw_device

# Program the device
puts "\nProgramming device..."
puts "This will take approximately 10-15 seconds..."

set prog_start [clock seconds]

if {[catch {program_hw_devices $hw_device} result]} {
    puts "\nERROR: Programming failed!"
    puts "Error: $result"
    close_hw_target
    close_hw_manager
    exit 1
}

set prog_end [clock seconds]
set prog_duration [expr $prog_end - $prog_start]

puts "\nDevice programmed successfully in $prog_duration seconds!"

puts "\n========================================="
puts "STEP 3: Verify Programming"
puts "========================================="

# Refresh and check status
refresh_hw_device $hw_device

# Check if device is programmed
set is_programmed [get_property PROGRAM.DONE $hw_device]
if {$is_programmed} {
    puts "Programming verification: PASS"
    puts "DONE LED should be lit on the board"
} else {
    puts "WARNING: Programming verification failed"
    puts "DONE LED may not be lit"
}

puts "\n========================================="
puts "PROGRAMMING COMPLETE!"
puts "========================================="
puts ""
puts "What to expect on Arty A7 board:"
puts "  1. DONE LED (green, near USB) should be ON"
puts "  2. Within 1 second, LED[0] should start blinking"
puts "  3. Connect UART to see RT-Thread output"
puts ""
puts "UART connection:"
puts "  Linux:   screen /dev/ttyUSB1 115200"
puts "  Windows: PuTTY → COM port → 115200 baud"
puts ""
puts "Expected UART output:"
puts "  RT-Thread Sim"
puts "  TICK"
puts "  TICK"
puts "  ..."
puts ""
puts "If LED not blinking:"
puts "  - Check that Boot ROM is executing"
puts "  - Verify clock PLL is locked"
puts "  - Use ILA to debug (add debug cores in Vivado)"
puts ""
puts "If no UART output:"
puts "  - Verify baud rate: 115200 8N1"
puts "  - Check correct USB port (usually ttyUSB1, not USB0)"
puts "  - Test with: ls -l /dev/ttyUSB*"
puts ""

# Close hardware connection
close_hw_target
close_hw_manager

puts "Hardware manager closed"
puts ""

exit 0
