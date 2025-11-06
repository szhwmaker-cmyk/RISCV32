# Vivado 综合脚本
# RV32E SoC

# 设置项目参数
set project_name "rv32e_soc"
set top_module "RV32ESoC"
set part "xc7a35tcpg236-1"

# 设置路径
set project_dir "./vivado_project"
set rtl_dir "../../generated"
set constraints_dir "../constraints"

# 创建项目目录
file mkdir $project_dir

# 创建项目
create_project $project_name $project_dir -part $part -force

# 添加 RTL 源文件
add_files [glob -nocomplain $rtl_dir/*.v]

# 添加约束文件
add_files -fileset constrs_1 $constraints_dir/rv32e_soc_artix7.xdc

# 设置顶层模块
set_property top $top_module [current_fileset]

# 综合设置
set_property strategy Flow_PerfOptimized_high [get_runs synth_1]
set_property STEPS.SYNTH_DESIGN.ARGS.DIRECTIVE AlternateRoutability [get_runs synth_1]
set_property STEPS.SYNTH_DESIGN.ARGS.RETIMING true [get_runs synth_1]

# 实现设置
set_property strategy Performance_ExplorePostRoutePhysOpt [get_runs impl_1]
set_property STEPS.PLACE_DESIGN.ARGS.DIRECTIVE ExtraNetDelay_high [get_runs impl_1]
set_property STEPS.ROUTE_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]

# 运行综合
puts "Starting synthesis..."
reset_run synth_1
launch_runs synth_1 -jobs 4
wait_on_run synth_1

# 检查综合结果
if {[get_property PROGRESS [get_runs synth_1]] != "100%"} {
    error "ERROR: Synthesis failed"
}

puts "Synthesis completed successfully"

# 打开综合结果
open_run synth_1

# 报告资源使用
report_utilization -file $project_dir/utilization_synth.txt
report_timing_summary -file $project_dir/timing_synth.txt

# 运行实现
puts "Starting implementation..."
reset_run impl_1
launch_runs impl_1 -to_step write_bitstream -jobs 4
wait_on_run impl_1

# 检查实现结果
if {[get_property PROGRESS [get_runs impl_1]] != "100%"} {
    error "ERROR: Implementation failed"
}

puts "Implementation completed successfully"

# 打开实现结果
open_run impl_1

# 生成报告
report_utilization -file $project_dir/utilization_impl.txt
report_timing_summary -file $project_dir/timing_impl.txt -max_paths 10
report_power -file $project_dir/power.txt
report_clock_utilization -file $project_dir/clock_util.txt

# 检查时序
set WNS [get_property SLACK [get_timing_paths -max_paths 1 -nworst 1 -setup]]
puts "WNS: $WNS ns"

if {$WNS < 0} {
    puts "WARNING: Timing not met! WNS = $WNS ns"
} else {
    puts "SUCCESS: Timing met with WNS = $WNS ns"
}

# 生成比特流
puts "Generating bitstream..."
write_bitstream -force $project_dir/$project_name.bit

# 生成 MCS 文件（用于 Flash 烧录）
write_cfgmem -format mcs -size 16 -interface SPIx4 \
    -loadbit "up 0x00000000 $project_dir/$project_name.bit" \
    -file $project_dir/$project_name.mcs

puts "Build completed!"
puts "Bitstream: $project_dir/$project_name.bit"
puts "MCS file: $project_dir/$project_name.mcs"

# 关闭项目
close_project
