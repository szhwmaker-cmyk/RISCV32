/*
 * UART Driver for RV32E SoC
 */

#include <rthw.h>
#include <rtthread.h>
#include <rtdevice.h>
#include "board.h"

/* UART Register Definitions */
struct rv32e_uart_regs {
    volatile rt_uint32_t txdata;    /* 0x00: Transmit data */
    volatile rt_uint32_t rxdata;    /* 0x04: Receive data */
    volatile rt_uint32_t status;    /* 0x08: Status register */
    volatile rt_uint32_t baud;      /* 0x0C: Baud rate divisor */
};

/* Status register bits */
#define UART_STATUS_TX_EMPTY    (1 << 0)
#define UART_STATUS_TX_FULL     (1 << 1)
#define UART_STATUS_RX_VALID    (1 << 2)
#define UART_STATUS_RX_EMPTY    (1 << 3)

/* UART device structure */
struct rv32e_uart {
    struct rv32e_uart_regs *regs;
    rt_uint32_t baud_rate;
};

static struct rv32e_uart uart0 = {
    .regs = (struct rv32e_uart_regs *)UART0_BASE,
    .baud_rate = 115200
};

/**
 * UART initialization
 */
static rt_err_t rv32e_uart_configure(struct rt_serial_device *serial,
                                     struct serial_configure *cfg)
{
    struct rv32e_uart *uart = (struct rv32e_uart *)serial->parent.user_data;
    rt_uint32_t divisor;

    /* Calculate baud rate divisor */
    /* divisor = SYSTEM_CLOCK / baud_rate */
    divisor = SYSTEM_CLOCK / cfg->baud_rate;

    /* Set baud rate */
    uart->regs->baud = divisor;

    return RT_EOK;
}

/**
 * UART control
 */
static rt_err_t rv32e_uart_control(struct rt_serial_device *serial,
                                   int cmd, void *arg)
{
    switch (cmd) {
        case RT_DEVICE_CTRL_CLR_INT:
            /* Disable RX interrupt */
            break;

        case RT_DEVICE_CTRL_SET_INT:
            /* Enable RX interrupt */
            break;
    }

    return RT_EOK;
}

/**
 * UART put character
 */
static int rv32e_uart_putc(struct rt_serial_device *serial, char c)
{
    struct rv32e_uart *uart = (struct rv32e_uart *)serial->parent.user_data;

    /* Wait for TX FIFO not full */
    while (uart->regs->status & UART_STATUS_TX_FULL);

    /* Send character */
    uart->regs->txdata = c;

    return 1;
}

/**
 * UART get character
 */
static int rv32e_uart_getc(struct rt_serial_device *serial)
{
    struct rv32e_uart *uart = (struct rv32e_uart *)serial->parent.user_data;

    /* Check if RX data is available */
    if (uart->regs->status & UART_STATUS_RX_VALID) {
        return (int)(uart->regs->rxdata & 0xFF);
    }

    return -1;
}

static const struct rt_uart_ops rv32e_uart_ops = {
    .configure = rv32e_uart_configure,
    .control   = rv32e_uart_control,
    .putc      = rv32e_uart_putc,
    .getc      = rv32e_uart_getc,
};

static struct rt_serial_device serial0;

/**
 * UART hardware initialization
 */
int rt_hw_uart_init(void)
{
    struct serial_configure config = RT_SERIAL_CONFIG_DEFAULT;

    /* Configure UART parameters */
    config.baud_rate = BAUD_RATE_115200;
    config.data_bits = DATA_BITS_8;
    config.stop_bits = STOP_BITS_1;
    config.parity    = PARITY_NONE;

    /* Register UART0 device */
    serial0.ops    = &rv32e_uart_ops;
    serial0.config = config;

    rt_hw_serial_register(&serial0,
                         "uart0",
                         RT_DEVICE_FLAG_RDWR | RT_DEVICE_FLAG_INT_RX,
                         &uart0);

    return 0;
}
