/*
 * Board Configuration for RV32E SoC
 */

#ifndef __BOARD_H__
#define __BOARD_H__

#include <rtthread.h>
#include <stdint.h>

/* Memory Layout */
#define RT_HW_HEAP_BEGIN    ((void*)0x80010000)
#define RT_HW_HEAP_END      ((void*)0x80040000)  // 192KB heap

/* System Clock */
#define RT_SYSTEM_CLOCK     50000000  // 50MHz

/* UART Configuration */
#define UART_BASE           0x20000000
#define UART_TXDATA         (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_RXDATA         (*(volatile uint32_t*)(UART_BASE + 0x04))
#define UART_STATUS         (*(volatile uint32_t*)(UART_BASE + 0x08))
#define UART_BAUD           (*(volatile uint32_t*)(UART_BASE + 0x0C))
#define UART_CTRL           (*(volatile uint32_t*)(UART_BASE + 0x10))

#define UART_STATUS_TX_BUSY (1 << 0)
#define UART_STATUS_TX_EMPTY (1 << 1)
#define UART_STATUS_RX_VALID (1 << 4)

/* GPIO Configuration */
#define GPIO_BASE           0x20010000
#define GPIO_DATA_IN        (*(volatile uint32_t*)(GPIO_BASE + 0x00))
#define GPIO_DATA_OUT       (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_DIR            (*(volatile uint32_t*)(GPIO_BASE + 0x08))
#define GPIO_OE             (*(volatile uint32_t*)(GPIO_BASE + 0x0C))

/* Function Prototypes */
void rt_hw_board_init(void);
void rt_hw_console_output(const char *str);

#endif /* __BOARD_H__ */
