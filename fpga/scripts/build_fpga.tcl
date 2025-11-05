#
# Vivado TCL Script: Build FPGA Bitstream
# Runs: Synthesis → Implementation → Bitstream Generation
#

set project_name "rv32e_soc_fpga"
set project_dir "./vivado"
set project_file "$project_dir/$project_name.xpr"

puts "========================================="
puts "FPGA Build Script"
puts "========================================="
puts "Project: $project_name"
puts "Timestamp: [clock format [clock seconds] -format {%Y-%m-%d %H:%M:%S}]"
puts "========================================="

# Check if project exists
if {![file exists $project_file]} {
    puts "ERROR: Project file not found: $project_file"
    puts "Create project first: vivado -mode batch -source scripts/create_project.tcl"
    exit 1
}

# Open project
puts "\nOpening project..."
open_project $project_file

# Create reports directory if it doesn't exist
file mkdir "$project_dir/$project_name.runs/reports"
set reports_dir "$project_dir/$project_name.runs/reports"

puts "\n========================================="
puts "STAGE 1: SYNTHESIS"
puts "========================================="

# Reset synthesis run
reset_run synth_1

# Launch synthesis
puts "Starting synthesis..."
set synth_start [clock seconds]
launch_runs synth_1 -jobs 4
wait_on_run synth_1
set synth_end [clock seconds]
set synth_duration [expr $synth_end - $synth_start]

# Check synthesis status
if {[get_property PROGRESS [get_runs synth_1]] != "100%"} {
    puts "\nERROR: Synthesis failed!"
    puts "Check log: $project_dir/$project_name.runs/synth_1/runme.log"
    exit 1
}

if {[get_property STATUS [get_runs synth_1]] != "synth_design Complete!"} {
    puts "\nWARNING: Synthesis completed with warnings"
}

puts "\nSynthesis completed in $synth_duration seconds"

# Open synthesized design
puts "Opening synthesized design..."
open_run synth_1

# Generate synthesis reports
puts "\nGenerating synthesis reports..."

report_utilization -file "$reports_dir/post_synth_util.rpt"
report_timing_summary -max_paths 10 -file "$reports_dir/post_synth_timing.rpt"

# Parse and display utilization
puts "\n--- Synthesis Utilization Summary ---"
set util_report [report_utilization -return_string]
if {[regexp {Slice LUTs[\s\|]+(\d+)} $util_report -> luts]} {
    puts "LUTs: $luts"
}
if {[regexp {Slice Registers[\s\|]+(\d+)} $util_report -> ffs]} {
    puts "FFs: $ffs"
}
if {[regexp {Block RAM Tile[\s\|]+([\d\.]+)} $util_report -> brams]} {
    puts "BRAMs: $brams"
}

# Check timing
puts "\n--- Synthesis Timing Summary ---"
set timing_report [report_timing_summary -return_string]
if {[regexp {WNS\(ns\)[\s]+(-?[\d\.]+)} $timing_report -> wns]} {
    puts "WNS: $wns ns"
    if {$wns < 0} {
        puts "WARNING: Negative slack detected in synthesis!"
    }
}

puts "\n========================================="
puts "STAGE 2: IMPLEMENTATION"
puts "========================================="

# Reset implementation run
reset_run impl_1

# Launch implementation
puts "Starting implementation..."
set impl_start [clock seconds]
launch_runs impl_1 -jobs 4
wait_on_run impl_1
set impl_end [clock seconds]
set impl_duration [expr $impl_end - $impl_start]

# Check implementation status
if {[get_property PROGRESS [get_runs impl_1]] != "100%"} {
    puts "\nERROR: Implementation failed!"
    puts "Check log: $project_dir/$project_name.runs/impl_1/runme.log"
    exit 1
}

puts "\nImplementation completed in $impl_duration seconds"

# Open implemented design
puts "Opening implemented design..."
open_run impl_1

# Generate implementation reports
puts "\nGenerating implementation reports..."

report_utilization -file "$reports_dir/post_impl_util.rpt"
report_timing_summary -max_paths 10 -file "$reports_dir/post_impl_timing.rpt"
report_clock_utilization -file "$reports_dir/clock_util.rpt"
report_drc -file "$reports_dir/drc.rpt"
report_power -file "$reports_dir/power.rpt"

# Parse and display final utilization
puts "\n--- Implementation Utilization Summary ---"
set util_report [report_utilization -return_string]
if {[regexp {Slice LUTs[\s\|]+(\d+)} $util_report -> luts]} {
    puts "LUTs: $luts / 20800 (Artix-7 35T)"
}
if {[regexp {Slice Registers[\s\|]+(\d+)} $util_report -> ffs]} {
    puts "FFs: $ffs / 41600"
}
if {[regexp {Block RAM Tile[\s\|]+([\d\.]+)} $util_report -> brams]} {
    puts "BRAMs: $brams / 50"
}

# Check final timing
puts "\n--- Implementation Timing Summary ---"
set timing_report [report_timing_summary -return_string]
if {[regexp {WNS\(ns\)[\s]+(-?[\d\.]+)} $timing_report -> wns]} {
    puts "WNS (Setup): $wns ns"
    if {$wns < 0} {
        puts "ERROR: Setup timing violation! WNS = $wns ns"
        puts "Consider:"
        puts "  1. Lowering clock frequency"
        puts "  2. Adding pipeline stages"
        puts "  3. Using faster speed grade"
        exit 1
    } else {
        puts "  Status: PASS (positive slack)"
    }
}

if {[regexp {WHS\(ns\)[\s]+(-?[\d\.]+)} $timing_report -> whs]} {
    puts "WHS (Hold): $whs ns"
    if {$whs < 0} {
        puts "ERROR: Hold timing violation! WHS = $whs ns"
        exit 1
    } else {
        puts "  Status: PASS (positive slack)"
    }
}

# Check power
puts "\n--- Power Estimate ---"
set power_report [report_power -return_string]
if {[regexp {Total On-Chip Power \(W\)[\s\|]+([\d\.]+)} $power_report -> power]} {
    puts "Total Power: $power W"
    if {$power > 2.0} {
        puts "WARNING: Power consumption high (>2W)"
    }
}

# Check DRC
puts "\n--- Design Rule Check ---"
set drc_report [report_drc -return_string]
if {[regexp {critical warning} $drc_report]} {
    puts "WARNING: Critical DRC warnings found"
    puts "Check: $reports_dir/drc.rpt"
} else {
    puts "DRC: PASS (no critical warnings)"
}

puts "\n========================================="
puts "STAGE 3: BITSTREAM GENERATION"
puts "========================================="

# Launch bitstream generation
puts "Generating bitstream..."
set bitstream_start [clock seconds]
launch_runs impl_1 -to_step write_bitstream -jobs 4
wait_on_run impl_1
set bitstream_end [clock seconds]
set bitstream_duration [expr $bitstream_end - $bitstream_start]

# Check bitstream status
set bitstream_file "$project_dir/$project_name.runs/impl_1/fpga_top.bit"
if {![file exists $bitstream_file]} {
    puts "\nERROR: Bitstream generation failed!"
    puts "Check log: $project_dir/$project_name.runs/impl_1/runme.log"
    exit 1
}

puts "\nBitstream generated in $bitstream_duration seconds"

# Get bitstream size
set bitstream_size [file size $bitstream_file]
set bitstream_size_kb [expr $bitstream_size / 1024]
puts "Bitstream size: $bitstream_size_kb KB"

# Create bitstreams directory and copy with timestamp
file mkdir "./bitstreams"
set timestamp [clock format [clock seconds] -format {%Y%m%d_%H%M%S}]
set output_bitstream "./bitstreams/rv32e_soc_$timestamp.bit"
file copy -force $bitstream_file $output_bitstream
puts "Bitstream copied to: $output_bitstream"

puts "\n========================================="
puts "BUILD SUMMARY"
puts "========================================="
set total_duration [expr $synth_duration + $impl_duration + $bitstream_duration]
set total_minutes [expr $total_duration / 60]
puts "Synthesis:    $synth_duration seconds"
puts "Implementation: $impl_duration seconds"
puts "Bitstream:    $bitstream_duration seconds"
puts "Total:        $total_duration seconds ($total_minutes minutes)"
puts ""
puts "Output files:"
puts "  Bitstream:  $output_bitstream"
puts "  Reports:    $reports_dir/"
puts ""

# Parse final metrics for summary
set util_report [report_utilization -return_string]
set timing_report [report_timing_summary -return_string]

puts "Final Metrics:"
if {[regexp {Slice LUTs[\s\|]+(\d+)} $util_report -> luts]} {
    set lut_pct [expr ($luts * 100.0) / 20800]
    puts "  LUTs:  $luts / 20800 ([format %.1f $lut_pct]%)"
}
if {[regexp {Slice Registers[\s\|]+(\d+)} $util_report -> ffs]} {
    set ff_pct [expr ($ffs * 100.0) / 41600]
    puts "  FFs:   $ffs / 41600 ([format %.1f $ff_pct]%)"
}
if {[regexp {Block RAM Tile[\s\|]+([\d\.]+)} $util_report -> brams]} {
    set bram_pct [expr ($brams * 100.0) / 50]
    puts "  BRAMs: $brams / 50 ([format %.1f $bram_pct]%)"
}
if {[regexp {WNS\(ns\)[\s]+(-?[\d\.]+)} $timing_report -> wns]} {
    puts "  WNS:   $wns ns"
}

# Calculate max frequency
if {[regexp {WNS\(ns\)[\s]+(-?[\d\.]+)} $timing_report -> wns]} {
    set target_period 40.0  ;# 25 MHz = 40 ns
    set max_period [expr $target_period - $wns]
    set max_freq [expr 1000.0 / $max_period]
    puts "  Max Frequency: [format %.1f $max_freq] MHz (target: 25 MHz)"
}

puts "\n========================================="
puts "BUILD COMPLETE!"
puts "========================================="
puts "\nNext step: Program FPGA"
puts "  GUI: Open Hardware Manager → Program Device"
puts "  CLI: vivado -mode batch -source scripts/program_fpga.tcl"
puts ""

# Close project
close_project

exit 0
