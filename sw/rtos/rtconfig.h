/*
 * RT-Thread Configuration for RV32E SoC
 */

#ifndef __RTCONFIG_H__
#define __RTCONFIG_H__

/* RT-Thread Kernel */
#define RT_NAME_MAX                     8
#define RT_ALIGN_SIZE                   4
#define RT_THREAD_PRIORITY_MAX          8
#define RT_TICK_PER_SECOND              100
#define RT_USING_OVERFLOW_CHECK
#define RT_USING_HOOK
#define RT_HOOK_USING_FUNC_PTR
#define RT_USING_IDLE_HOOK
#define RT_IDLE_HOOK_LIST_SIZE          4
#define RT_USING_TIMER_SOFT
#define RT_TIMER_THREAD_PRIO            4
#define RT_TIMER_THREAD_STACK_SIZE      512

/* kservice optimization */
#define RT_KSERVICE_USING_STDLIB
#define RT_DEBUG

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
#define RT_MAIN_THREAD_STACK_SIZE       2048
#define RT_MAIN_THREAD_PRIORITY         10

/* Command shell */
#define RT_USING_FINSH
#define FINSH_USING_MSH
#define FINSH_THREAD_NAME               "tshell"
#define FINSH_THREAD_PRIORITY           20
#define FINSH_THREAD_STACK_SIZE         2048
#define FINSH_USING_HISTORY
#define FINSH_HISTORY_LINES             5
#define FINSH_USING_SYMTAB
#define FINSH_CMD_SIZE                  80
#define MSH_USING_BUILT_IN_COMMANDS
#define FINSH_USING_DESCRIPTION
#define FINSH_ARG_MAX                   10

/* Device Drivers */
#define RT_USING_DEVICE_IPC
#define RT_USING_SERIAL
#define RT_SERIAL_USING_DMA
#define RT_SERIAL_RB_BUFSZ              64

/* RV32E Specific */
#define ARCH_RISCV
#define ARCH_RISCV_RV32E
#define RT_USING_CPU_FFS
#undef RT_USING_INTERRUPT           // 不使用中断
#define RT_WITHOUT_INTERRUPT            // 轮询模式

/* Board Configuration */
#define RT_BOARD_NAME                   "RV32E-SoC"
#define RT_CPUS_NR                      1

#endif /* __RTCONFIG_H__ */
