/*
 * RT-Thread Application for RV32E SoC
 */

#include <rtthread.h>
#include <rthw.h>
#include "board.h"

/* Thread stack sizes */
#define THREAD_STACK_SIZE   512
#define THREAD_PRIORITY     20
#define THREAD_TIMESLICE    5

/* GPIO base address */
#define GPIO_BASE           0x20010000
#define GPIO_DATA_OUT       (GPIO_BASE + 0x04)
#define GPIO_DIR            (GPIO_BASE + 0x08)
#define GPIO_OE             (GPIO_BASE + 0x0C)

/* LED thread entry */
static void led_thread_entry(void* parameter)
{
    volatile rt_uint32_t *gpio_out = (rt_uint32_t *)GPIO_DATA_OUT;
    volatile rt_uint32_t *gpio_dir = (rt_uint32_t *)GPIO_DIR;
    volatile rt_uint32_t *gpio_oe  = (rt_uint32_t *)GPIO_OE;
    rt_uint8_t led_state = 0;

    /* Configure GPIO[0] as output */
    *gpio_dir = 0x01;
    *gpio_oe  = 0x01;

    rt_kprintf("LED thread started\n");

    while (1)
    {
        /* Toggle LED */
        led_state = !led_state;
        *gpio_out = led_state ? 0x01 : 0x00;

        rt_kprintf("LED: %s\n", led_state ? "ON" : "OFF");

        /* Delay 500ms */
        rt_thread_mdelay(500);
    }
}

/* Hello thread entry */
static void hello_thread_entry(void* parameter)
{
    rt_uint32_t count = 0;

    rt_kprintf("Hello thread started\n");

    while (1)
    {
        rt_kprintf("Hello RT-Thread! Count: %d\n", count++);

        /* Delay 1 second */
        rt_thread_mdelay(1000);
    }
}

/* System information thread */
static void sysinfo_thread_entry(void* parameter)
{
    rt_thread_mdelay(2000);  /* Wait for system to stabilize */

    while (1)
    {
        /* Print system information */
        rt_kprintf("\n========== System Info ==========\n");
        rt_kprintf("CPU: RISC-V RV32E @ %d MHz\n", SYSTEM_CLOCK / 1000000);
        rt_kprintf("RAM: %d KB\n", RAM_SIZE / 1024);
        rt_kprintf("Tick: %d\n", rt_tick_get());

        /* Print thread information */
        extern struct rt_thread *rt_current_thread;
        rt_kprintf("Current thread: %s\n", rt_current_thread->name);

        /* Memory usage */
        rt_uint32_t total, used, max_used;
        rt_memory_info(&total, &used, &max_used);
        rt_kprintf("Memory: Total=%d, Used=%d, Max=%d\n", total, used, max_used);

        rt_kprintf("=================================\n\n");

        /* Delay 5 seconds */
        rt_thread_mdelay(5000);
    }
}

/**
 * Application initialization
 */
int rt_application_init(void)
{
    rt_thread_t tid;

    /* Create LED thread */
    tid = rt_thread_create("led",
                          led_thread_entry,
                          RT_NULL,
                          THREAD_STACK_SIZE,
                          THREAD_PRIORITY,
                          THREAD_TIMESLICE);
    if (tid != RT_NULL)
        rt_thread_startup(tid);

    /* Create hello thread */
    tid = rt_thread_create("hello",
                          hello_thread_entry,
                          RT_NULL,
                          THREAD_STACK_SIZE,
                          THREAD_PRIORITY + 1,
                          THREAD_TIMESLICE);
    if (tid != RT_NULL)
        rt_thread_startup(tid);

    /* Create system info thread */
    tid = rt_thread_create("sysinfo",
                          sysinfo_thread_entry,
                          RT_NULL,
                          THREAD_STACK_SIZE,
                          THREAD_PRIORITY + 2,
                          THREAD_TIMESLICE);
    if (tid != RT_NULL)
        rt_thread_startup(tid);

    return 0;
}

/**
 * Main function (if using RT_USING_USER_MAIN)
 */
#ifdef RT_USING_USER_MAIN
int main(void)
{
    rt_kprintf("\n");
    rt_kprintf("  ____  _____    _____ _                        _ \n");
    rt_kprintf(" |  _ \\|_   _|  |_   _| |__  _ __ ___  __ _  __| |\n");
    rt_kprintf(" | |_) | | |______| | | '_ \\| '__/ _ \\/ _` |/ _` |\n");
    rt_kprintf(" |  _ <  | |______| | | | | | | |  __/ (_| | (_| |\n");
    rt_kprintf(" |_| \\_\\|_|      |_| |_| |_|_|  \\___|\\__,_|\\__,_|\n");
    rt_kprintf("\n");
    rt_kprintf(" RT-Thread on RV32E SoC\n");
    rt_kprintf(" Version: 4.1.0\n");
    rt_kprintf(" Built: %s %s\n", __DATE__, __TIME__);
    rt_kprintf("\n");

    /* Initialize application threads */
    rt_application_init();

    return 0;
}
#endif

/* Export to MSH command */
MSH_CMD_EXPORT(rt_application_init, Initialize RT-Thread application);
