##
## Xilinx Design Constraints (XDC) for Arty A7
## RV32E SoC Project
##

## Clock Signal (100 MHz)
set_property -dict {PACKAGE_PIN E3 IOSTANDARD LVCMOS33} [get_ports clk_100mhz]
create_clock -period 10.000 -name sys_clk_pin -waveform {0.000 5.000} -add [get_ports clk_100mhz]

## Reset Button (BTN0, active low)
set_property -dict {PACKAGE_PIN C2 IOSTANDARD LVCMOS33} [get_ports btn_reset_n]

## ========================================================================
## UART (USB-UART Bridge)
## ========================================================================
set_property -dict {PACKAGE_PIN D10 IOSTANDARD LVCMOS33} [get_ports uart_txd]
set_property -dict {PACKAGE_PIN A9  IOSTANDARD LVCMOS33} [get_ports uart_rxd]

## ========================================================================
## LEDs
## ========================================================================
set_property -dict {PACKAGE_PIN H5  IOSTANDARD LVCMOS33} [get_ports {led[0]}]
set_property -dict {PACKAGE_PIN J5  IOSTANDARD LVCMOS33} [get_ports {led[1]}]
set_property -dict {PACKAGE_PIN T9  IOSTANDARD LVCMOS33} [get_ports {led[2]}]
set_property -dict {PACKAGE_PIN T10 IOSTANDARD LVCMOS33} [get_ports {led[3]}]

## RGB LED 0
set_property -dict {PACKAGE_PIN G6  IOSTANDARD LVCMOS33} [get_ports {led0_rgb[0]}]  # Red
set_property -dict {PACKAGE_PIN F6  IOSTANDARD LVCMOS33} [get_ports {led0_rgb[1]}]  # Green
set_property -dict {PACKAGE_PIN E1  IOSTANDARD LVCMOS33} [get_ports {led0_rgb[2]}]  # Blue

## RGB LED 1
set_property -dict {PACKAGE_PIN G3  IOSTANDARD LVCMOS33} [get_ports {led1_rgb[0]}]  # Red
set_property -dict {PACKAGE_PIN J4  IOSTANDARD LVCMOS33} [get_ports {led1_rgb[1]}]  # Green
set_property -dict {PACKAGE_PIN G4  IOSTANDARD LVCMOS33} [get_ports {led1_rgb[2]}]  # Blue

## ========================================================================
## Buttons (BTN1-3, active high)
## ========================================================================
set_property -dict {PACKAGE_PIN D9  IOSTANDARD LVCMOS33} [get_ports {btn[0]}]
set_property -dict {PACKAGE_PIN C9  IOSTANDARD LVCMOS33} [get_ports {btn[1]}]
set_property -dict {PACKAGE_PIN B9  IOSTANDARD LVCMOS33} [get_ports {btn[2]}]
set_property -dict {PACKAGE_PIN B8  IOSTANDARD LVCMOS33} [get_ports {btn[3]}]

## ========================================================================
## Switches
## ========================================================================
set_property -dict {PACKAGE_PIN A8  IOSTANDARD LVCMOS33} [get_ports {sw[0]}]
set_property -dict {PACKAGE_PIN C11 IOSTANDARD LVCMOS33} [get_ports {sw[1]}]
set_property -dict {PACKAGE_PIN C10 IOSTANDARD LVCMOS33} [get_ports {sw[2]}]
set_property -dict {PACKAGE_PIN A10 IOSTANDARD LVCMOS33} [get_ports {sw[3]}]

## ========================================================================
## Quad SPI Flash
## ========================================================================
set_property -dict {PACKAGE_PIN L13 IOSTANDARD LVCMOS33} [get_ports qspi_cs]
set_property -dict {PACKAGE_PIN L16 IOSTANDARD LVCMOS33} [get_ports {qspi_dq[0]}]  # MOSI
set_property -dict {PACKAGE_PIN K17 IOSTANDARD LVCMOS33} [get_ports {qspi_dq[1]}]  # MISO
set_property -dict {PACKAGE_PIN K18 IOSTANDARD LVCMOS33} [get_ports {qspi_dq[2]}]  # WP
set_property -dict {PACKAGE_PIN L14 IOSTANDARD LVCMOS33} [get_ports {qspi_dq[3]}]  # HOLD
set_property -dict {PACKAGE_PIN E2  IOSTANDARD LVCMOS33} [get_ports qspi_sck]

## ========================================================================
## Timing Constraints
## ========================================================================

## Generated 25 MHz clock from PLL
create_generated_clock -name clk_25mhz -source [get_pins clk_gen/clk_in1] \
    -divide_by 4 [get_pins clk_gen/clk_out1]

## Input delays (relative to 25 MHz clock)
set_input_delay -clock clk_25mhz -min 0.000 [get_ports uart_rxd]
set_input_delay -clock clk_25mhz -max 5.000 [get_ports uart_rxd]
set_input_delay -clock clk_25mhz -min 0.000 [get_ports {btn[*]}]
set_input_delay -clock clk_25mhz -max 5.000 [get_ports {btn[*]}]
set_input_delay -clock clk_25mhz -min 0.000 [get_ports {sw[*]}]
set_input_delay -clock clk_25mhz -max 5.000 [get_ports {sw[*]}]
set_input_delay -clock clk_25mhz -min 0.000 [get_ports {qspi_dq[*]}]
set_input_delay -clock clk_25mhz -max 8.000 [get_ports {qspi_dq[*]}]

## Output delays
set_output_delay -clock clk_25mhz -min -1.000 [get_ports uart_txd]
set_output_delay -clock clk_25mhz -max 3.000 [get_ports uart_txd]
set_output_delay -clock clk_25mhz -min -1.000 [get_ports {led[*]}]
set_output_delay -clock clk_25mhz -max 3.000 [get_ports {led[*]}]
set_output_delay -clock clk_25mhz -min -1.000 [get_ports qspi_cs]
set_output_delay -clock clk_25mhz -max 8.000 [get_ports qspi_cs]
set_output_delay -clock clk_25mhz -min -1.000 [get_ports qspi_sck]
set_output_delay -clock clk_25mhz -max 8.000 [get_ports qspi_sck]
set_output_delay -clock clk_25mhz -min -1.000 [get_ports {qspi_dq[*]}]
set_output_delay -clock clk_25mhz -max 8.000 [get_ports {qspi_dq[*]}]

## False paths for asynchronous inputs
set_false_path -from [get_ports btn_reset_n]
set_false_path -from [get_ports {btn[*]}]
set_false_path -from [get_ports {sw[*]}]

## ========================================================================
## Configuration
## ========================================================================
set_property CONFIG_VOLTAGE 3.3 [current_design]
set_property CFGBVS VCCO [current_design]
set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]
set_property BITSTREAM.CONFIG.CONFIGRATE 33 [current_design]
set_property CONFIG_MODE SPIx4 [current_design]
