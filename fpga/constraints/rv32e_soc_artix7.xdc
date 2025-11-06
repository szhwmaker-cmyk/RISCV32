# RV32E SoC - Xilinx Artix-7 约束文件
# 适用于 Digilent Basys3/Arty A7 等开发板

##############################################################################
# 时钟和复位
##############################################################################

# 系统时钟 - 100 MHz 输入
set_property -dict {PACKAGE_PIN W5 IOSTANDARD LVCMOS33} [get_ports clk]
create_clock -period 10.000 -name sys_clk_pin -waveform {0.000 5.000} -add [get_ports clk]

# 复位按钮 (低有效)
set_property -dict {PACKAGE_PIN U18 IOSTANDARD LVCMOS33} [get_ports rst_n]

# 时钟不确定性
set_clock_uncertainty 0.500 [get_clocks sys_clk_pin]

##############################################################################
# UART 接口
##############################################################################

# UART TX (FPGA -> PC)
set_property -dict {PACKAGE_PIN A18 IOSTANDARD LVCMOS33} [get_ports {uart_tx}]

# UART RX (PC -> FPGA)
set_property -dict {PACKAGE_PIN B18 IOSTANDARD LVCMOS33} [get_ports {uart_rx}]

##############################################################################
# SPI Flash 接口
##############################################################################

# SPI Flash SCK
set_property -dict {PACKAGE_PIN E9 IOSTANDARD LVCMOS33} [get_ports {spi_flash_sck}]

# SPI Flash CS_N
set_property -dict {PACKAGE_PIN K9 IOSTANDARD LVCMOS33} [get_ports {spi_flash_cs_n}]

# SPI Flash MOSI
set_property -dict {PACKAGE_PIN D10 IOSTANDARD LVCMOS33} [get_ports {spi_flash_mosi}]

# SPI Flash MISO
set_property -dict {PACKAGE_PIN D9 IOSTANDARD LVCMOS33} [get_ports {spi_flash_miso}]

##############################################################################
# GPIO - LED
##############################################################################

set_property -dict {PACKAGE_PIN U16 IOSTANDARD LVCMOS33} [get_ports {gpio_out[0]}]
set_property -dict {PACKAGE_PIN E19 IOSTANDARD LVCMOS33} [get_ports {gpio_out[1]}]
set_property -dict {PACKAGE_PIN U19 IOSTANDARD LVCMOS33} [get_ports {gpio_out[2]}]
set_property -dict {PACKAGE_PIN V19 IOSTANDARD LVCMOS33} [get_ports {gpio_out[3]}]
set_property -dict {PACKAGE_PIN W18 IOSTANDARD LVCMOS33} [get_ports {gpio_out[4]}]
set_property -dict {PACKAGE_PIN U15 IOSTANDARD LVCMOS33} [get_ports {gpio_out[5]}]
set_property -dict {PACKAGE_PIN U14 IOSTANDARD LVCMOS33} [get_ports {gpio_out[6]}]
set_property -dict {PACKAGE_PIN V14 IOSTANDARD LVCMOS33} [get_ports {gpio_out[7]}]

##############################################################################
# GPIO - 开关/按钮
##############################################################################

set_property -dict {PACKAGE_PIN V17 IOSTANDARD LVCMOS33} [get_ports {gpio_in[0]}]
set_property -dict {PACKAGE_PIN V16 IOSTANDARD LVCMOS33} [get_ports {gpio_in[1]}]
set_property -dict {PACKAGE_PIN W16 IOSTANDARD LVCMOS33} [get_ports {gpio_in[2]}]
set_property -dict {PACKAGE_PIN W17 IOSTANDARD LVCMOS33} [get_ports {gpio_in[3]}]
set_property -dict {PACKAGE_PIN W15 IOSTANDARD LVCMOS33} [get_ports {gpio_in[4]}]
set_property -dict {PACKAGE_PIN V15 IOSTANDARD LVCMOS33} [get_ports {gpio_in[5]}]
set_property -dict {PACKAGE_PIN W14 IOSTANDARD LVCMOS33} [get_ports {gpio_in[6]}]
set_property -dict {PACKAGE_PIN W13 IOSTANDARD LVCMOS33} [get_ports {gpio_in[7]}]

##############################################################################
# I2C 接口
##############################################################################

# I2C SCL
set_property -dict {PACKAGE_PIN L18 IOSTANDARD LVCMOS33} [get_ports {i2c_scl}]

# I2C SDA
set_property -dict {PACKAGE_PIN M18 IOSTANDARD LVCMOS33} [get_ports {i2c_sda}]

##############################################################################
# SPI Master 接口
##############################################################################

# SPI SCK
set_property -dict {PACKAGE_PIN F4 IOSTANDARD LVCMOS33} [get_ports {spi_sck}]

# SPI MOSI
set_property -dict {PACKAGE_PIN D3 IOSTANDARD LVCMOS33} [get_ports {spi_mosi}]

# SPI MISO
set_property -dict {PACKAGE_PIN E2 IOSTANDARD LVCMOS33} [get_ports {spi_miso}]

# SPI CS[0]
set_property -dict {PACKAGE_PIN F3 IOSTANDARD LVCMOS33} [get_ports {spi_cs_n[0]}]

##############################################################################
# 时序约束
##############################################################################

# 输入延迟约束
set_input_delay -clock [get_clocks sys_clk_pin] -min 1.000 [get_ports {uart_rx}]
set_input_delay -clock [get_clocks sys_clk_pin] -max 5.000 [get_ports {uart_rx}]
set_input_delay -clock [get_clocks sys_clk_pin] -min 1.000 [get_ports {spi_flash_miso}]
set_input_delay -clock [get_clocks sys_clk_pin] -max 5.000 [get_ports {spi_flash_miso}]
set_input_delay -clock [get_clocks sys_clk_pin] -min 1.000 [get_ports {gpio_in[*]}]
set_input_delay -clock [get_clocks sys_clk_pin] -max 5.000 [get_ports {gpio_in[*]}]

# 输出延迟约束
set_output_delay -clock [get_clocks sys_clk_pin] -min 0.000 [get_ports {uart_tx}]
set_output_delay -clock [get_clocks sys_clk_pin] -max 3.000 [get_ports {uart_tx}]
set_output_delay -clock [get_clocks sys_clk_pin] -min 0.000 [get_ports {spi_flash_sck}]
set_output_delay -clock [get_clocks sys_clk_pin] -max 3.000 [get_ports {spi_flash_sck}]
set_output_delay -clock [get_clocks sys_clk_pin] -min 0.000 [get_ports {gpio_out[*]}]
set_output_delay -clock [get_clocks sys_clk_pin] -max 3.000 [get_ports {gpio_out[*]}]

# 伪路径 - 异步复位
set_false_path -from [get_ports rst_n]

# 多周期路径（如果需要）
# set_multicycle_path -setup 2 -from [get_pins ...] -to [get_pins ...]

##############################################################################
# 物理约束
##############################################################################

# 防止优化掉关键信号
set_property KEEP TRUE [get_nets {core/pc_reg[*]}]
set_property KEEP TRUE [get_nets {core/regfile/regs[*]}]

# 布局约束（可选，用于优化时序）
# create_pblock pblock_core
# add_cells_to_pblock pblock_core [get_cells [list core]]
# resize_pblock pblock_core -add {SLICE_X0Y0:SLICE_X50Y50}

##############################################################################
# 配置约束
##############################################################################

# 配置电压
set_property CFGBVS VCCO [current_design]
set_property CONFIG_VOLTAGE 3.3 [current_design]

# 比特流选项
set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]
set_property BITSTREAM.CONFIG.CONFIGRATE 33 [current_design]
set_property BITSTREAM.CONFIG.SPI_BUSWIDTH 4 [current_design]
set_property BITSTREAM.CONFIG.SPI_FALL_EDGE YES [current_design]

##############################################################################
# 调试约束
##############################################################################

# ILA 时钟
# set_property C_CLK_INPUT_FREQ_HZ 100000000 [get_debug_cores dbg_hub]
# set_property C_ENABLE_CLK_DIVIDER false [get_debug_cores dbg_hub]
# set_property C_USER_SCAN_CHAIN 1 [get_debug_cores dbg_hub]

# 注：根据实际开发板调整引脚分配
# 上述约束适用于 Basys3 开发板
# 对于其他开发板，请参考其原理图修改引脚号
