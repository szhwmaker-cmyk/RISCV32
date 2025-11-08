# RT-Thread OS 移植说明

## 概述

本目录包含 RT-Thread OS 在 RV32E SoC 上的移植文件。

## RT-Thread 特性

RV32E SoC 支持 RT-Thread 的简化版本：
- **不需要中断支持**：采用轮询方式
- **不需要系统指令**：简化的任务调度
- **最小配置**：仅包含核心内核和基础组件

## 移植步骤

### 1. 获取 RT-Thread 源码

```bash
git clone https://github.com/RT-Thread/rt-thread.git
cd rt-thread
```

### 2. 配置 RV32E 支持

在 `rtconfig.h` 中配置：

```c
#define RT_USING_RV32E          1
#define RT_CPUS_NR              1
#define RT_ALIGN_SIZE           4
#define RT_THREAD_PRIORITY_MAX  8
#define RT_TICK_PER_SECOND      100
#define RT_NAME_MAX             8
#define RT_USING_HEAP           1
#define RT_USING_SMALL_MEM      1
```

### 3. 移植文件

需要创建以下文件：

- `board.c` - 板级初始化
- `board.h` - 板级配置
- `uart.c` - UART驱动（用于console）
- `context_gcc.S` - 上下文切换（RV32E汇编）

### 4. 关键实现

#### UART 驱动

```c
#define UART_BASE 0x20000000

void rt_hw_console_output(const char *str) {
    while (*str) {
        // 等待TX空闲
        while (UART_STATUS & 0x01);
        UART_TXDATA = *str++;
    }
}
```

#### 简化的调度器

由于不使用中断，采用协作式调度：

```c
void rt_hw_context_switch(rt_uint32 from, rt_uint32 to) {
    // 简化的上下文切换
    // RV32E只有x0-x15，保存更少的寄存器
}
```

## 编译

```bash
cd rt-thread/bsp/rv32e-soc
scons
```

生成的 `rtthread.bin` 可以烧录到 SPI Flash。

## 测试

启动后应该看到：

```
 \\ | /
- RT -     Thread Operating System
 / | \\     4.1.0 build Dec  1 2024
 2006 - 2022 Copyright by rt-thread team
msh >
```

## 示例应用

```c
#include <rtthread.h>

static void led_thread_entry(void *parameter) {
    rt_uint32_t count = 0;

    while (1) {
        rt_kprintf("LED thread: %d\\n", count++);
        GPIO_DATA_OUT = count & 0xFF;
        rt_thread_delay(RT_TICK_PER_SECOND);
    }
}

int main(void) {
    rt_thread_t tid;

    tid = rt_thread_create("led",
                          led_thread_entry,
                          RT_NULL,
                          512,
                          3,
                          20);

    if (tid != RT_NULL)
        rt_thread_startup(tid);

    return 0;
}
```

## 注意事项

1. RV32E 只有 16 个寄存器，需要特别优化上下文切换
2. 没有中断支持，需要轮询方式处理外设
3. 堆栈大小要严格控制，RAM 只有 256KB
4. 编译时使用 `-march=rv32e -mabi=ilp32e`
