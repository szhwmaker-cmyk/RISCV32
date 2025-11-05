/*
 * RV32E SoC Board Support Package for RT-Thread
 */

#include <rthw.h>
#include <rtthread.h>
#include "board.h"

/* Memory Configuration */
#define HEAP_BEGIN  ((void*)&__heap_start)
#define HEAP_END    ((void*)&__heap_end)

/* External symbols from linker script */
extern int __heap_start;
extern int __heap_end;

/**
 * This function will initialize hardware board
 */
void rt_hw_board_init(void)
{
    /* Initialize UART for console */
    rt_hw_uart_init();

#ifdef RT_USING_CONSOLE
    /* Set console device */
    rt_console_set_device(RT_CONSOLE_DEVICE_NAME);
#endif

#ifdef RT_USING_HEAP
    /* Initialize system heap */
    rt_system_heap_init(HEAP_BEGIN, HEAP_END);
#endif

#ifdef RT_USING_COMPONENTS_INIT
    rt_components_board_init();
#endif
}

/**
 * System clock configuration
 */
void SystemCoreClockUpdate(void)
{
    /* RV32E SoC runs at 25MHz */
}

/**
 * Delay function (busy wait)
 */
void rt_hw_us_delay(rt_uint32_t us)
{
    rt_uint32_t ticks = us * (SYSTEM_CLOCK / 1000000);
    while(ticks--) {
        __asm__ volatile ("nop");
    }
}

/**
 * System timer interrupt handler
 * Called by timer interrupt (if interrupts are enabled)
 */
void rt_hw_systick_handler(void)
{
    /* Enter interrupt */
    rt_interrupt_enter();

    /* Increase tick */
    rt_tick_increase();

    /* Leave interrupt */
    rt_interrupt_leave();
}

/**
 * Early boot initialization
 * Called before main()
 */
void rt_hw_early_init(void)
{
    /* Disable interrupts during initialization */
    /* RV32E: Clear mstatus.MIE if available */

    /* Initialize BSS section */
    extern unsigned int __bss_start;
    extern unsigned int __bss_end;
    unsigned int *dst = &__bss_start;
    while (dst < &__bss_end) {
        *dst++ = 0;
    }

    /* Initialize data section */
    extern unsigned int __data_load;
    extern unsigned int __data_start;
    extern unsigned int __data_end;
    unsigned int *src = &__data_load;
    dst = &__data_start;
    while (dst < &__data_end) {
        *dst++ = *src++;
    }
}
