/**
 * @file board.c
 * @brief RV32E SoC 板级支持包
 */

#include <rtthread.h>
#include <rthw.h>
#include "board.h"

/* 堆定义 */
#if defined(RT_USING_USER_MAIN) && defined(RT_USING_HEAP)
#define RT_HEAP_SIZE (16 * 1024)
static uint32_t rt_heap[RT_HEAP_SIZE / 4];
uint32_t __heap_start = (uint32_t)&rt_heap[0];
uint32_t __heap_end = (uint32_t)&rt_heap[RT_HEAP_SIZE / 4];
#endif

/**
 * @brief 寄存器访问宏
 */
#define REG32(addr) (*(volatile uint32_t *)(addr))

/**
 * @brief UART 初始化
 */
static void uart_init(void)
{
    /* 设置波特率 (115200 bps @ 50 MHz) */
    REG32(UART_BASE + UART_BAUD) = 434; /* 50000000 / 115200 ≈ 434 */

    /* 使能发送和接收 */
    REG32(UART_BASE + UART_CTRL) = 0x03; /* tx_en | rx_en */
}

/**
 * @brief UART 输出字符
 */
static void uart_putc(char c)
{
    /* 等待发送缓冲区空闲 */
    while (REG32(UART_BASE + UART_STATUS) & UART_TX_FULL);

    /* 发送字符 */
    REG32(UART_BASE + UART_TXDATA) = c;
}

/**
 * @brief UART 接收字符
 */
static char uart_getc(void)
{
    /* 等待接收数据有效 */
    while (!(REG32(UART_BASE + UART_STATUS) & UART_RX_VALID));

    /* 读取字符 */
    return (char)(REG32(UART_BASE + UART_RXDATA) & 0xFF);
}

/**
 * @brief RT-Thread 控制台输出
 */
void rt_hw_console_output(const char *str)
{
    while (*str)
    {
        if (*str == '\n')
        {
            uart_putc('\r');
        }
        uart_putc(*str++);
    }
}

/**
 * @brief RT-Thread 控制台输入
 */
char rt_hw_console_getchar(void)
{
    return uart_getc();
}

/**
 * @brief GPIO 初始化
 */
static void gpio_init(void)
{
    /* 设置 LED 引脚为输出 */
    uint32_t dir = REG32(GPIO_BASE + GPIO_DIR);
    dir |= (1 << LED_PIN);
    REG32(GPIO_BASE + GPIO_DIR) = dir;

    uint32_t oe = REG32(GPIO_BASE + GPIO_OE);
    oe |= (1 << LED_PIN);
    REG32(GPIO_BASE + GPIO_OE) = oe;

    /* 初始化 LED 为熄灭 */
    REG32(GPIO_BASE + GPIO_DATA_OUT) &= ~(1 << LED_PIN);
}

/**
 * @brief 系统 tick 初始化
 *
 * 注：这里使用简化的 tick 实现
 * 实际应该使用硬件定时器
 */
static void systick_init(void)
{
    /* RV32E 没有标准的 mtime/mtimecmp 寄存器 */
    /* 这里需要根据实际硬件实现 */
    /* 简化起见，暂时留空 */
}

/**
 * @brief 系统 tick 中断处理
 */
void systick_handler(void)
{
    /* 进入中断 */
    rt_interrupt_enter();

    /* 增加系统 tick */
    rt_tick_increase();

    /* 退出中断 */
    rt_interrupt_leave();
}

/**
 * @brief 板级初始化
 */
void rt_hw_board_init(void)
{
    /* 初始化 UART */
    uart_init();

    /* 初始化 GPIO */
    gpio_init();

    /* 初始化系统 tick */
    systick_init();

    /* 打印启动信息 */
    rt_kprintf("\n");
    rt_kprintf("========================================\n");
    rt_kprintf("  RT-Thread on RV32E SoC\n");
    rt_kprintf("========================================\n");
    rt_kprintf("CPU:     RV32E @ %d MHz\n", CPU_FREQ / 1000000);
    rt_kprintf("SRAM:    %d KB\n", SRAM_SIZE / 1024);
    rt_kprintf("Tick:    %d Hz\n", RT_TICK_PER_SECOND);
    rt_kprintf("========================================\n");
    rt_kprintf("\n");

#ifdef RT_USING_HEAP
    /* 初始化堆 */
    rt_system_heap_init((void*)&__heap_start, (void*)&__heap_end);
#endif

#ifdef RT_USING_COMPONENTS_INIT
    /* 初始化组件 */
    rt_components_board_init();
#endif

#ifdef RT_USING_CONSOLE
    /* 设置控制台设备 */
    rt_console_set_device(RT_CONSOLE_DEVICE_NAME);
#endif
}

/**
 * @brief 简单的延时函数（用于早期启动）
 */
void rt_hw_us_delay(rt_uint32_t us)
{
    rt_uint32_t cycles = (CPU_FREQ / 1000000) * us / 4;
    for (volatile rt_uint32_t i = 0; i < cycles; i++)
    {
        __asm__ volatile ("nop");
    }
}

/**
 * @brief LED 控制
 */
void rt_hw_led_on(void)
{
    REG32(GPIO_BASE + GPIO_DATA_OUT) |= (1 << LED_PIN);
}

void rt_hw_led_off(void)
{
    REG32(GPIO_BASE + GPIO_DATA_OUT) &= ~(1 << LED_PIN);
}

void rt_hw_led_toggle(void)
{
    REG32(GPIO_BASE + GPIO_DATA_OUT) ^= (1 << LED_PIN);
}

/* finsh 调试命令 */
#ifdef RT_USING_FINSH
#include <finsh.h>

static void led_on(void)
{
    rt_hw_led_on();
    rt_kprintf("LED ON\n");
}
MSH_CMD_EXPORT(led_on, Turn on LED);

static void led_off(void)
{
    rt_hw_led_off();
    rt_kprintf("LED OFF\n");
}
MSH_CMD_EXPORT(led_off, Turn off LED);

static void led_toggle(void)
{
    rt_hw_led_toggle();
    rt_kprintf("LED Toggle\n");
}
MSH_CMD_EXPORT(led_toggle, Toggle LED);

#endif /* RT_USING_FINSH */
