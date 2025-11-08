# RV32E SoC Vivado Implementation Script

# Set the reference directory
set origin_dir [file dirname [info script]]
set project_dir "${origin_dir}/../vivado_project"

# Open synthesized checkpoint
open_checkpoint ${project_dir}/post_synth.dcp

# Run optimization
puts "Running Optimization..."
opt_design

# Run placement
puts "Running Placement..."
place_design
write_checkpoint -force ${project_dir}/post_place.dcp
report_timing_summary -file ${project_dir}/post_place_timing_summary.rpt

# Run routing
puts "Running Routing..."
route_design
write_checkpoint -force ${project_dir}/post_route.dcp

# Generate reports
report_timing_summary -file ${project_dir}/post_route_timing_summary.rpt
report_timing -sort_by group -max_paths 100 -path_type summary -file ${project_dir}/post_route_timing.rpt
report_clock_utilization -file ${project_dir}/clock_utilization.rpt
report_utilization -file ${project_dir}/post_route_utilization.rpt
report_power -file ${project_dir}/post_route_power.rpt
report_drc -file ${project_dir}/post_route_drc.rpt

# Write bitstream
puts "Writing Bitstream..."
write_bitstream -force ${project_dir}/rv32e_soc.bit

puts "Implementation Complete!"
puts "Bitstream: ${project_dir}/rv32e_soc.bit"
