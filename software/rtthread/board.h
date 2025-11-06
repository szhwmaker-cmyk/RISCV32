/**
 * @file board.h
 * @brief RV32E SoC 板级定义
 */

#ifndef __BOARD_H__
#define __BOARD_H__

#include <stdint.h>

/* CPU 配置 */
#define CPU_FREQ                50000000    /* 50 MHz */
#define TICK_PER_SECOND         1000        /* 1 ms tick */

/* 内存配置 */
#define SRAM_BASE               0x80000000
#define SRAM_SIZE               (64 * 1024) /* 64 KB */

/* 堆配置 */
#define RT_HEAP_BEGIN           ((void*)&__heap_start)
#define RT_HEAP_END             ((void*)&__heap_end)

/* 外设基地址 */
#define UART_BASE               0x20000000
#define GPIO_BASE               0x20010000
#define SPI_BASE                0x20020000
#define I2C_BASE                0x20030000

/* UART 寄存器偏移 */
#define UART_TXDATA             0x00
#define UART_RXDATA             0x04
#define UART_STATUS             0x08
#define UART_BAUD               0x0C
#define UART_CTRL               0x10

/* UART 状态位 */
#define UART_RX_VALID           (1 << 0)
#define UART_TX_EMPTY           (1 << 1)
#define UART_TX_FULL            (1 << 2)

/* GPIO 寄存器偏移 */
#define GPIO_DATA_IN            0x00
#define GPIO_DATA_OUT           0x04
#define GPIO_DIR                0x08
#define GPIO_OE                 0x0C

/* 默认控制台 */
#define RT_CONSOLE_DEVICE_NAME  "uart0"

/* GPIO 引脚定义 */
#define LED_PIN                 0
#define BUTTON_PIN              1

/* 外部符号（由链接脚本定义） */
extern uint32_t __heap_start;
extern uint32_t __heap_end;
extern uint32_t __stack_top;

/* 函数声明 */
void rt_hw_board_init(void);
void rt_hw_console_output(const char *str);
char rt_hw_console_getchar(void);

#endif /* __BOARD_H__ */
