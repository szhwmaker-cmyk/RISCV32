/* RT-Thread Configuration for RV32E SoC */

#ifndef RT_CONFIG_H__
#define RT_CONFIG_H__

/* Automatically generated file; DO NOT EDIT. */
/* RT-Thread Configuration for RV32E */

/* RT-Thread Kernel */
#define RT_NAME_MAX                     8
#define RT_ALIGN_SIZE                   4
#define RT_THREAD_PRIORITY_MAX          32
#define RT_TICK_PER_SECOND              100
#define RT_USING_OVERFLOW_CHECK
#define RT_USING_HOOK
#define RT_HOOK_USING_FUNC_PTR
#define RT_USING_IDLE_HOOK
#define RT_IDLE_HOOK_LIST_SIZE          4
#define IDLE_THREAD_STACK_SIZE          256

/* Inter-Thread communication */
#define RT_USING_SEMAPHORE
#define RT_USING_MUTEX
#define RT_USING_EVENT
#define RT_USING_MAILBOX
#define RT_USING_MESSAGEQUEUE

/* Memory Management */
#define RT_USING_MEMPOOL
#define RT_USING_SMALL_MEM
#define RT_USING_HEAP

/* Kernel Device Object */
#define RT_USING_DEVICE
#define RT_USING_CONSOLE
#define RT_CONSOLEBUF_SIZE              128
#define RT_CONSOLE_DEVICE_NAME          "uart0"

/* RT-Thread Components */
#define RT_USING_COMPONENTS_INIT
#define RT_USING_USER_MAIN
#define RT_MAIN_THREAD_STACK_SIZE       1024
#define RT_MAIN_THREAD_PRIORITY         10

/* Command shell */
#define RT_USING_FINSH
#define FINSH_USING_MSH
#define FINSH_THREAD_NAME               "tshell"
#define FINSH_THREAD_PRIORITY           20
#define FINSH_THREAD_STACK_SIZE         2048
#define FINSH_CMD_SIZE                  80
#define FINSH_USING_HISTORY
#define FINSH_HISTORY_LINES             5

/* Device Drivers */
#define RT_USING_DEVICE_IPC
#define RT_USING_SERIAL
#define RT_SERIAL_RB_BUFSZ              64

/* RV32E SoC Hardware Configuration */
#define SOC_RISCV_RV32E

/* Memory Configuration */
#define RAM_START                       0x80000000
#define RAM_SIZE                        (64 * 1024)     /* 64KB */
#define RAM_END                         (RAM_START + RAM_SIZE)

/* System Clock */
#define SYSTEM_CLOCK                    25000000        /* 25MHz */

/* Peripheral Base Addresses */
#define UART0_BASE                      0x20000000
#define GPIO_BASE                       0x20010000
#define SPI_BASE                        0x20020000
#define I2C_BASE                        0x20030000

/* Interrupt Configuration */
/* Note: RV32E basic implementation may not have interrupts yet */
/* #define RT_USING_INTERRUPT */

/* Disable features not needed for minimal system */
#undef RT_USING_DEVICE_OPS
#undef RT_USING_DFS
#undef RT_USING_LIBC
#undef RT_USING_PTHREADS
#undef RT_USING_MODULE

/* Optimization */
#define RT_DEBUG
#define RT_DEBUG_INIT                   1

#endif /* RT_CONFIG_H__ */
