/*
 * Board Configuration Header for RV32E SoC
 */

#ifndef __BOARD_H__
#define __BOARD_H__

#include <rtconfig.h>

/* System clock frequency */
#ifndef SYSTEM_CLOCK
#define SYSTEM_CLOCK    25000000    /* 25MHz */
#endif

/* Memory configuration */
#ifndef RAM_START
#define RAM_START       0x80000000
#endif

#ifndef RAM_SIZE
#define RAM_SIZE        (64 * 1024) /* 64KB */
#endif

#ifndef RAM_END
#define RAM_END         (RAM_START + RAM_SIZE)
#endif

/* Peripheral base addresses */
#ifndef UART0_BASE
#define UART0_BASE      0x20000000
#endif

#ifndef GPIO_BASE
#define GPIO_BASE       0x20010000
#endif

#ifndef SPI_BASE
#define SPI_BASE        0x20020000
#endif

#ifndef I2C_BASE
#define I2C_BASE        0x20030000
#endif

/* Function declarations */
void rt_hw_board_init(void);
void rt_hw_uart_init(void);
void rt_hw_us_delay(rt_uint32_t us);

#endif /* __BOARD_H__ */
