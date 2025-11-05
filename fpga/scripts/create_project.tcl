#
# Vivado TCL Script: Create RV32E SoC FPGA Project
# Target: Xilinx Arty A7-35T
#

set project_name "rv32e_soc_fpga"
set project_dir "./vivado"
set rtl_dir "./rtl"
set constraints_dir "./constraints"
set generated_dir "./generated"

# FPGA part number for Arty A7-35T
set fpga_part "xc7a35ticsg324-1L"

puts "========================================="
puts "Creating Vivado Project: $project_name"
puts "========================================="

# Create project directory if it doesn't exist
file mkdir $project_dir

# Create new project
create_project $project_name $project_dir -part $fpga_part -force

# Set project properties
set_property target_language Verilog [current_project]
set_property simulator_language Mixed [current_project]
set_property default_lib work [current_project]

puts "\n========================================="
puts "Adding Design Sources"
puts "========================================="

# Add FPGA top-level wrapper
if {[file exists "$rtl_dir/fpga_top.v"]} {
    add_files -fileset sources_1 "$rtl_dir/fpga_top.v"
    puts "Added: fpga_top.v"
} else {
    puts "ERROR: fpga_top.v not found!"
    exit 1
}

# Add generated Chisel Verilog files
if {[file exists $generated_dir]} {
    set verilog_files [glob -nocomplain "$generated_dir/*.v"]
    if {[llength $verilog_files] > 0} {
        add_files -fileset sources_1 $verilog_files
        puts "Added [llength $verilog_files] Verilog files from $generated_dir"
        foreach f $verilog_files {
            puts "  - [file tail $f]"
        }
    } else {
        puts "WARNING: No Verilog files found in $generated_dir"
        puts "Run Chisel generation first: mill rv32e.runMain circt.stage.ChiselMain ..."
    }
} else {
    puts "WARNING: $generated_dir directory not found"
    puts "Run Chisel generation first"
}

# Set top module
set_property top fpga_top [current_fileset]

puts "\n========================================="
puts "Adding Constraints"
puts "========================================="

# Add constraints file
if {[file exists "$constraints_dir/arty_a7.xdc"]} {
    add_files -fileset constrs_1 "$constraints_dir/arty_a7.xdc"
    puts "Added: arty_a7.xdc"
} else {
    puts "ERROR: arty_a7.xdc not found!"
    exit 1
}

puts "\n========================================="
puts "Creating Clock Wizard IP"
puts "========================================="

# Source PLL creation script
if {[file exists "./scripts/create_pll.tcl"]} {
    source ./scripts/create_pll.tcl
    puts "Clock Wizard IP created successfully"
} else {
    puts "WARNING: create_pll.tcl not found"
    puts "You'll need to create Clock Wizard IP manually"
}

puts "\n========================================="
puts "Setting Synthesis Options"
puts "========================================="

# Synthesis settings
set_property strategy "Vivado Synthesis Defaults" [get_runs synth_1]
set_property steps.synth_design.args.flatten_hierarchy "rebuilt" [get_runs synth_1]
set_property steps.synth_design.args.directive "Default" [get_runs synth_1]
set_property steps.synth_design.args.retiming "false" [get_runs synth_1]

# Enable resource sharing for better area utilization
set_property steps.synth_design.args.resource_sharing "auto" [get_runs synth_1]

# Set synthesis to use 4 threads
set_property steps.synth_design.args.jobs "4" [get_runs synth_1]

puts "\n========================================="
puts "Setting Implementation Options"
puts "========================================="

# Implementation settings
set_property strategy "Vivado Implementation Defaults" [get_runs impl_1]

# Placement settings
set_property steps.opt_design.args.directive "Default" [get_runs impl_1]
set_property steps.place_design.args.directive "Default" [get_runs impl_1]

# Routing settings
set_property steps.route_design.args.directive "Default" [get_runs impl_1]

# Physical optimization
set_property steps.phys_opt_design.is_enabled true [get_runs impl_1]
set_property steps.phys_opt_design.args.directive "Default" [get_runs impl_1]

# Post-route physical optimization
set_property steps.post_route_phys_opt_design.is_enabled true [get_runs impl_1]
set_property steps.post_route_phys_opt_design.args.directive "Default" [get_runs impl_1]

# Set implementation to use 4 threads
set_property steps.place_design.args.jobs "4" [get_runs impl_1]
set_property steps.route_design.args.jobs "4" [get_runs impl_1]

puts "\n========================================="
puts "Setting Bitstream Options"
puts "========================================="

# Bitstream settings
set_property steps.write_bitstream.args.bin_file false [get_runs impl_1]
set_property steps.write_bitstream.args.readback_file false [get_runs impl_1]
set_property steps.write_bitstream.args.verbose false [get_runs impl_1]

# Add timestamp to bitstream
set timestamp [clock format [clock seconds] -format {%Y%m%d_%H%M%S}]
set_property steps.write_bitstream.args.logic_location_file false [get_runs impl_1]

puts "\n========================================="
puts "Creating Reports Directory"
puts "========================================="

# Create reports directory
file mkdir "$project_dir/$project_name.runs/reports"

puts "\n========================================="
puts "Project Creation Summary"
puts "========================================="
puts "Project name:    $project_name"
puts "Project dir:     $project_dir"
puts "FPGA part:       $fpga_part"
puts "Top module:      fpga_top"
puts "Design files:    [llength [get_files -filter {FILE_TYPE == Verilog}]]"
puts "Constraint files:[llength [get_files -filter {FILE_TYPE == XDC}]]"
puts "\n========================================="
puts "Project Created Successfully!"
puts "========================================="
puts "\nNext steps:"
puts "1. Open project: vivado $project_dir/$project_name.xpr"
puts "2. Or run synthesis: vivado -mode batch -source scripts/build_fpga.tcl"
puts ""

# Save and close project
update_compile_order -fileset sources_1
save_project

puts "Project saved to: $project_dir/$project_name.xpr"
puts ""
