# RV32E SoC FPGA Constraints
# Target: Xilinx 7-Series (Artix-7, Kintex-7, etc.)
# Example board: Arty A7-35T

# Clock (100 MHz input)
set_property -dict {PACKAGE_PIN E3 IOSTANDARD LVCMOS33} [get_ports clk]
create_clock -period 10.000 -name sys_clk [get_ports clk]

# Reset button (active low)
set_property -dict {PACKAGE_PIN C2 IOSTANDARD LVCMOS33} [get_ports reset_n]
set_false_path -from [get_ports reset_n]

# UART
set_property -dict {PACKAGE_PIN D10 IOSTANDARD LVCMOS33} [get_ports uart_tx]
set_property -dict {PACKAGE_PIN A9 IOSTANDARD LVCMOS33} [get_ports uart_rx]

# GPIO / LEDs (16 bits)
# LEDs 0-3
set_property -dict {PACKAGE_PIN H5 IOSTANDARD LVCMOS33} [get_ports {gpio_out[0]}]
set_property -dict {PACKAGE_PIN J5 IOSTANDARD LVCMOS33} [get_ports {gpio_out[1]}]
set_property -dict {PACKAGE_PIN T9 IOSTANDARD LVCMOS33} [get_ports {gpio_out[2]}]
set_property -dict {PACKAGE_PIN T10 IOSTANDARD LVCMOS33} [get_ports {gpio_out[3]}]

# RGB LEDs as additional GPIO
set_property -dict {PACKAGE_PIN G6 IOSTANDARD LVCMOS33} [get_ports {gpio_out[4]}]
set_property -dict {PACKAGE_PIN F6 IOSTANDARD LVCMOS33} [get_ports {gpio_out[5]}]
set_property -dict {PACKAGE_PIN E1 IOSTANDARD LVCMOS33} [get_ports {gpio_out[6]}]
set_property -dict {PACKAGE_PIN G3 IOSTANDARD LVCMOS33} [get_ports {gpio_out[7]}]

# Switches as GPIO inputs (4 bits)
set_property -dict {PACKAGE_PIN A8 IOSTANDARD LVCMOS33} [get_ports {gpio_in[0]}]
set_property -dict {PACKAGE_PIN C11 IOSTANDARD LVCMOS33} [get_ports {gpio_in[1]}]
set_property -dict {PACKAGE_PIN C10 IOSTANDARD LVCMOS33} [get_ports {gpio_in[2]}]
set_property -dict {PACKAGE_PIN A10 IOSTANDARD LVCMOS33} [get_ports {gpio_in[3]}]

# Buttons as additional GPIO inputs
set_property -dict {PACKAGE_PIN D9 IOSTANDARD LVCMOS33} [get_ports {gpio_in[4]}]
set_property -dict {PACKAGE_PIN C9 IOSTANDARD LVCMOS33} [get_ports {gpio_in[5]}]
set_property -dict {PACKAGE_PIN B9 IOSTANDARD LVCMOS33} [get_ports {gpio_in[6]}]
set_property -dict {PACKAGE_PIN B8 IOSTANDARD LVCMOS33} [get_ports {gpio_in[7]}]

# Tie unused GPIO to ground
set_property -dict {PACKAGE_PIN NONE IOSTANDARD LVCMOS33} [get_ports {gpio_out[15:8]}]
set_property -dict {PACKAGE_PIN NONE IOSTANDARD LVCMOS33} [get_ports {gpio_in[15:8]}]

# SPI (Optional - for Flash or external devices)
# Commented out by default
# set_property -dict {PACKAGE_PIN L16 IOSTANDARD LVCMOS33} [get_ports spi_sclk]
# set_property -dict {PACKAGE_PIN K17 IOSTANDARD LVCMOS33} [get_ports spi_mosi]
# set_property -dict {PACKAGE_PIN K18 IOSTANDARD LVCMOS33} [get_ports spi_miso]
# set_property -dict {PACKAGE_PIN L14 IOSTANDARD LVCMOS33} [get_ports {spi_cs_n[0]}]

# I2C (Optional)
# set_property -dict {PACKAGE_PIN L18 IOSTANDARD LVCMOS33} [get_ports i2c_scl_out]
# set_property -dict {PACKAGE_PIN M18 IOSTANDARD LVCMOS33} [get_ports i2c_scl_in]
# set_property -dict {PACKAGE_PIN A14 IOSTANDARD LVCMOS33} [get_ports i2c_sda_out]
# set_property -dict {PACKAGE_PIN A13 IOSTANDARD LVCMOS33} [get_ports i2c_sda_in]

# Timing Constraints

# Input delay for asynchronous inputs
set_input_delay -clock sys_clk -min 0 [get_ports uart_rx]
set_input_delay -clock sys_clk -max 2 [get_ports uart_rx]
set_input_delay -clock sys_clk -min 0 [get_ports gpio_in*]
set_input_delay -clock sys_clk -max 2 [get_ports gpio_in*]

# Output delay
set_output_delay -clock sys_clk -min -1 [get_ports uart_tx]
set_output_delay -clock sys_clk -max 2 [get_ports uart_tx]
set_output_delay -clock sys_clk -min -1 [get_ports gpio_out*]
set_output_delay -clock sys_clk -max 2 [get_ports gpio_out*]

# False paths for reset
set_false_path -from [get_ports reset_n] -to [all_registers]

# Configuration options
set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]
set_property BITSTREAM.CONFIG.CONFIGRATE 33 [current_design]
set_property CONFIG_MODE SPIx4 [current_design]
