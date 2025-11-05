#
# TCL Script to create Clock Wizard IP
# Converts 100 MHz input to 25 MHz output
#

# Create IP
create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 \
    -module_name clk_wiz_0

# Configure IP
set_property -dict [list \
    CONFIG.PRIM_IN_FREQ {100.000} \
    CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {25.000} \
    CONFIG.USE_LOCKED {true} \
    CONFIG.USE_RESET {true} \
    CONFIG.RESET_TYPE {ACTIVE_HIGH} \
    CONFIG.RESET_PORT {reset} \
    CONFIG.CLKOUT1_DRIVES {BUFG} \
    CONFIG.CLKOUT2_DRIVES {BUFG} \
    CONFIG.CLKOUT3_DRIVES {BUFG} \
    CONFIG.CLKOUT4_DRIVES {BUFG} \
    CONFIG.CLKOUT5_DRIVES {BUFG} \
    CONFIG.CLKOUT6_DRIVES {BUFG} \
    CONFIG.CLKOUT7_DRIVES {BUFG} \
    CONFIG.FEEDBACK_SOURCE {FDBK_AUTO} \
    CONFIG.MMCM_CLKFBOUT_MULT_F {10.000} \
    CONFIG.MMCM_CLKIN1_PERIOD {10.000} \
    CONFIG.MMCM_CLKOUT0_DIVIDE_F {40.000} \
    CONFIG.CLKOUT1_JITTER {151.636} \
    CONFIG.CLKOUT1_PHASE_ERROR {98.575} \
] [get_ips clk_wiz_0]

# Generate IP
generate_target {instantiation_template} [get_files clk_wiz_0.xci]
generate_target all [get_files clk_wiz_0.xci]
create_ip_run [get_files clk_wiz_0.xci]
launch_runs clk_wiz_0_synth_1
wait_on_run clk_wiz_0_synth_1

puts "Clock Wizard IP created successfully"
