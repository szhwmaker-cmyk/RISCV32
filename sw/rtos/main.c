/*
 * RT-Thread Application Entry for RV32E SoC
 */

#include <rtthread.h>
#include "board.h"

/* Thread stacks */
#define THREAD_STACK_SIZE   512
#define THREAD_PRIORITY     20
#define THREAD_TIMESLICE    5

static rt_uint8_t led_thread_stack[THREAD_STACK_SIZE];
static struct rt_thread led_thread;

static rt_uint8_t uart_thread_stack[THREAD_STACK_SIZE];
static struct rt_thread uart_thread;

/**
 * LED blink thread
 */
static void led_thread_entry(void *parameter) {
    rt_uint16_t count = 0;

    rt_kprintf("LED thread started\n");

    while (1) {
        // Toggle LED pattern
        GPIO_DATA_OUT = count & 0xFF;
        count++;

        // Print status every 10 iterations
        if (count % 10 == 0) {
            rt_kprintf("LED: %d (GPIO: 0x%04x)\n", count, GPIO_DATA_OUT);
        }

        // Sleep for 500ms (50 ticks @ 100Hz)
        rt_thread_mdelay(500);
    }
}

/**
 * UART echo thread
 */
static void uart_thread_entry(void *parameter) {
    char ch;

    rt_kprintf("UART thread started\n");
    rt_kprintf("Type characters, they will be echoed back\n");

    while (1) {
        // Check if RX data available
        if (UART_STATUS & UART_STATUS_RX_VALID) {
            ch = UART_RXDATA & 0xFF;

            // Echo back
            rt_kprintf("Echo: %c (0x%02x)\n", ch, ch);
        }

        // Yield to other threads
        rt_thread_mdelay(10);
    }
}

/**
 * Main function (RT-Thread entry)
 */
int main(void) {
    rt_kprintf("\n\n");
    rt_kprintf("========================================\n");
    rt_kprintf("  RT-Thread on RV32E SoC\n");
    rt_kprintf("========================================\n");
    rt_kprintf("CPU:       RV32E 5-Stage Pipeline\n");
    rt_kprintf("Frequency: 50 MHz\n");
    rt_kprintf("RT-Thread: %d.%d.%d\n", RT_VERSION, RT_SUBVERSION, RT_REVISION);
    rt_kprintf("Heap:      %d bytes\n", (rt_uint32_t)RT_HW_HEAP_END - (rt_uint32_t)RT_HW_HEAP_BEGIN);
    rt_kprintf("========================================\n\n");

    // Create LED thread
    rt_thread_init(&led_thread,
                   "led",
                   led_thread_entry,
                   RT_NULL,
                   &led_thread_stack[0],
                   sizeof(led_thread_stack),
                   THREAD_PRIORITY,
                   THREAD_TIMESLICE);
    rt_thread_startup(&led_thread);

    // Create UART thread
    rt_thread_init(&uart_thread,
                   "uart",
                   uart_thread_entry,
                   RT_NULL,
                   &uart_thread_stack[0],
                   sizeof(uart_thread_stack),
                   THREAD_PRIORITY + 1,
                   THREAD_TIMESLICE);
    rt_thread_startup(&uart_thread);

    rt_kprintf("All threads created and started\n\n");

    return 0;
}

/**
 * Idle hook - called when system is idle
 */
void rt_hw_idle_hook(void) {
    // Simple delay
    for (volatile int i = 0; i < 100; i++);
}
