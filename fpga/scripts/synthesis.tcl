# RV32E SoC Vivado Synthesis Script
# Target: Xilinx 7-Series

# Set the reference directory for source file relative paths
set origin_dir [file dirname [info script]]
set project_dir "${origin_dir}/../vivado_project"

# Create project
create_project rv32e_soc ${project_dir} -part xc7a35ticsg324-1L -force

# Set project properties
set_property target_language Verilog [current_project]
set_property simulator_language Mixed [current_project]

# Add Chisel-generated Verilog files
# Note: These need to be generated first using Chisel
# add_files -norecurse ${origin_dir}/../../generated/RV32ESoC.v

# Add constraints
add_files -fileset constrs_1 -norecurse ${origin_dir}/../constraints/rv32e_soc_xc7.xdc

# Set top module
# set_property top RV32ESoC [current_fileset]

# Run synthesis
puts "Running Synthesis..."
synth_design -top RV32ESoC -part xc7a35ticsg324-1L

# Write checkpoint
write_checkpoint -force ${project_dir}/post_synth.dcp

# Generate reports
report_timing_summary -file ${project_dir}/post_synth_timing_summary.rpt
report_utilization -file ${project_dir}/post_synth_utilization.rpt
report_power -file ${project_dir}/post_synth_power.rpt

puts "Synthesis Complete!"
puts "Check reports in: ${project_dir}"
