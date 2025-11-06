# RT-Thread for RV32E SoC

本目录包含 RT-Thread 实时操作系统在 RV32E SoC 上的移植。

## 目录结构

```
rtthread/
├── README.md           # 本文件
├── rtconfig.h          # RT-Thread 配置头文件
├── board.c             # 板级支持包
├── board.h             # 板级定义
├── SConstruct          # SCons 构建脚本
├── rtthread.ld         # RT-Thread 链接脚本
└── rtthread_startup.S  # RT-Thread 启动代码
```

## RT-Thread 版本

- RT-Thread 版本：4.1.1
- 内核：nano 版本（适合嵌入式系统）

## 移植特性

1. **RV32E 支持**
   - 16 个通用寄存器 (x0-x15)
   - 自定义 ABI: ilp32e
   - 优化的上下文切换

2. **硬件支持**
   - 50 MHz 系统时钟
   - 64 KB SRAM
   - UART 控制台 (115200 bps)
   - GPIO 支持

3. **OS 特性**
   - 多线程调度
   - 信号量、互斥锁、邮箱
   - 软件定时器
   - 内存管理

## 构建 RT-Thread

### 前提条件

1. 安装 RISC-V 工具链：
```bash
# Ubuntu/Debian
sudo apt-get install gcc-riscv64-unknown-elf

# 或下载预编译工具链
wget https://github.com/xpack-dev-tools/riscv-none-embed-gcc-xpack/releases/download/v10.2.0-1.2/xpack-riscv-none-embed-gcc-10.2.0-1.2-linux-x64.tar.gz
```

2. 安装 SCons（RT-Thread 构建系统）：
```bash
pip install scons
```

3. 下载 RT-Thread 源码：
```bash
git clone https://github.com/RT-Thread/rt-thread.git
cd rt-thread
```

### 编译步骤

1. 配置环境变量：
```bash
export RTT_ROOT=/path/to/rt-thread
export RTT_CC=gcc
export RTT_EXEC_PATH=/path/to/riscv-toolchain/bin
export RTT_CC_PREFIX=riscv32-unknown-elf-
```

2. 编译：
```bash
cd /path/to/RISCV32/software/rtthread
scons
```

3. 生成二进制文件：
```bash
riscv32-unknown-elf-objcopy -O binary rtthread.elf rtthread.bin
```

## 快速开始示例

```c
#include <rtthread.h>
#include <board.h>

static void led_thread_entry(void *parameter)
{
    while (1)
    {
        rt_pin_write(LED_PIN, PIN_HIGH);
        rt_thread_mdelay(500);
        rt_pin_write(LED_PIN, PIN_LOW);
        rt_thread_mdelay(500);
    }
}

int main(void)
{
    rt_thread_t tid;

    rt_kprintf("RT-Thread on RV32E!\n");

    tid = rt_thread_create("led",
                           led_thread_entry,
                           RT_NULL,
                           512,
                           25,
                           10);
    if (tid != RT_NULL)
        rt_thread_startup(tid);

    return 0;
}
```

## 仿真测试

使用 Verilator 仿真测试 RT-Thread：

```bash
# 构建 SoC 仿真器
cd /path/to/RISCV32
mill rv32e_soc.test.runMain sim.SocSimulator

# 加载 RT-Thread 二进制
./obj_dir/VSocTop rtthread.bin
```

## FPGA 部署

参见 [FPGA 部署文档](../../fpga/README.md)。

## 性能指标

| 指标 | 值 |
|------|-----|
| 上下文切换时间 | ~200 cycles |
| 最大线程数 | 32 |
| 系统 tick | 1 ms |
| RAM 使用 | ~8 KB (kernel) |

## 已知限制

1. **RV32E 限制**
   - 只有 16 个寄存器，函数调用开销略高
   - 某些 GCC 优化可能不适用

2. **硬件限制**
   - 无 FPU（浮点运算通过软件仿真）
   - 无 MMU（内存保护）
   - 无硬件中断控制器（使用简化的中断模型）

## 技术支持

- RT-Thread 官方文档：https://www.rt-thread.org/document/site/
- RT-Thread 社区：https://club.rt-thread.org/
- 项目 Issue：https://github.com/szhwmaker-cmyk/RISCV32/issues

## 许可证

RT-Thread 采用 Apache License 2.0 许可证。
