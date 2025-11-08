/*
 * Board Support Package for RV32E SoC
 */

#include <rtthread.h>
#include <rthw.h>
#include "board.h"

/* Simple delay function */
static void delay(int count) {
    for (volatile int i = 0; i < count; i++);
}

/**
 * UART initialization
 */
static void uart_init(void) {
    // 配置波特率 115200 @ 50MHz
    UART_BAUD = 434;
    // 使能 TX 和 RX
    UART_CTRL = 0x03;
}

/**
 * Console output (used by RT-Thread)
 */
void rt_hw_console_output(const char *str) {
    while (*str) {
        // 等待 TX 不忙
        while (UART_STATUS & UART_STATUS_TX_BUSY);
        UART_TXDATA = *str++;
    }
}

/**
 * Board initialization
 */
void rt_hw_board_init(void) {
    // 初始化 UART
    uart_init();

    // 初始化 GPIO (全部配置为输出)
    GPIO_DIR = 0xFFFF;
    GPIO_OE = 0xFFFF;
    GPIO_DATA_OUT = 0x0000;

    // 初始化系统堆
#if defined(RT_USING_HEAP)
    rt_system_heap_init((void *)RT_HW_HEAP_BEGIN, (void *)RT_HW_HEAP_END);
#endif

    // 初始化板级设备（如果有）
#ifdef RT_USING_COMPONENTS_INIT
    rt_components_board_init();
#endif

#ifdef RT_USING_CONSOLE
    rt_console_set_device(RT_CONSOLE_DEVICE_NAME);
#endif
}

/**
 * Simple tick implementation (without interrupt)
 * This should be called periodically from main loop
 */
void rt_hw_tick_increase(void) {
    rt_tick_increase();
}

/**
 * CPU usage idle hook
 */
void rt_hw_cpu_idle(void) {
    // 简单的空闲循环
    __asm__ volatile ("nop");
}
