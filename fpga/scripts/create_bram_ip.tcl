#!/usr/bin/tclsh
# Xilinx Block Memory Generator IP Configuration Script
#
# This script creates a Block RAM IP core optimized for the RV32E SoC.
# Run in Vivado TCL console or batch mode.
#
# Usage:
#   vivado -mode batch -source create_bram_ip.tcl
#
# Generated IP:
#   - xilinx_bram_64kb: Single-port 64KB RAM with byte enables

# Project settings
set ip_name "xilinx_bram_64kb"
set ip_dir "../ip_repo"

# Create IP repository directory if it doesn't exist
file mkdir $ip_dir

puts "========================================="
puts "Creating Xilinx Block Memory Generator IP"
puts "========================================="

# Create IP project (if needed)
create_project managed_ip_project $ip_dir/managed_ip_project -part xc7a35ticsg324-1L -ip -force

# Create Block Memory Generator IP
create_ip -name blk_mem_gen -vendor xilinx.com -library ip -version 8.4 \
    -module_name $ip_name -dir $ip_dir

# Configure IP properties
set_property -dict [list \
    CONFIG.Memory_Type {Single_Port_RAM} \
    CONFIG.Use_Byte_Write_Enable {true} \
    CONFIG.Byte_Size {8} \
    CONFIG.Write_Width_A {32} \
    CONFIG.Read_Width_A {32} \
    CONFIG.Write_Depth_A {16384} \
    CONFIG.Read_Latency_A {1} \
    CONFIG.Enable_A {Always_Enabled} \
    CONFIG.Register_PortA_Output_of_Memory_Primitives {true} \
    CONFIG.Use_RSTA_Pin {false} \
    CONFIG.Port_A_Write_Rate {50} \
    CONFIG.Port_A_Enable_Rate {100} \
    CONFIG.EN_SAFETY_CKT {false} \
    CONFIG.Write_Width_B {32} \
    CONFIG.Read_Width_B {32} \
    CONFIG.Operating_Mode_A {WRITE_FIRST} \
    CONFIG.Enable_32bit_Address {false} \
    CONFIG.Use_REGCEA_Pin {false} \
    CONFIG.Coe_File {none} \
    CONFIG.Load_Init_File {false} \
    CONFIG.Fill_Remaining_Memory_Locations {false} \
    CONFIG.Remaining_Memory_Locations {0} \
    CONFIG.Assume_Synchronous_Clk {true} \
] [get_ips $ip_name]

# Generate IP
generate_target {instantiation_template} [get_files $ip_dir/$ip_name/$ip_name.xci]
generate_target all [get_files $ip_dir/$ip_name/$ip_name.xci]

puts "IP Generation Complete!"
puts "IP Location: $ip_dir/$ip_name"
puts ""
puts "To use this IP in your project:"
puts "1. Add IP repository to project:"
puts "   Settings -> IP -> Repository -> Add: $ip_dir"
puts "2. Instantiate in Verilog/VHDL:"
puts "   xilinx_bram_64kb ram_inst ("
puts "     .clka(clk),"
puts "     .ena(en),"
puts "     .wea(we),"
puts "     .addra(addr),"
puts "     .dina(din),"
puts "     .douta(dout)"
puts "   );"
puts ""
puts "========================================="

# Close project
close_project

# ========================================
# Alternative: Simpler 32KB version (uses less BRAM)
# ========================================

puts "Creating 32KB version..."
set ip_name_32kb "xilinx_bram_32kb"

create_project managed_ip_project_32kb $ip_dir/managed_ip_project_32kb -part xc7a35ticsg324-1L -ip -force

create_ip -name blk_mem_gen -vendor xilinx.com -library ip -version 8.4 \
    -module_name $ip_name_32kb -dir $ip_dir

set_property -dict [list \
    CONFIG.Memory_Type {Single_Port_RAM} \
    CONFIG.Use_Byte_Write_Enable {true} \
    CONFIG.Byte_Size {8} \
    CONFIG.Write_Width_A {32} \
    CONFIG.Read_Width_A {32} \
    CONFIG.Write_Depth_A {8192} \
    CONFIG.Read_Latency_A {1} \
    CONFIG.Register_PortA_Output_of_Memory_Primitives {true} \
    CONFIG.Operating_Mode_A {WRITE_FIRST} \
] [get_ips $ip_name_32kb]

generate_target all [get_files $ip_dir/$ip_name_32kb/$ip_name_32kb.xci]

puts "32KB IP Generation Complete!"
close_project

puts ""
puts "========================================="
puts "Summary:"
puts "- 64KB RAM: $ip_dir/$ip_name"
puts "- 32KB RAM: $ip_dir/$ip_name_32kb"
puts "========================================="

exit
