/*
 * RT-Thread Startup Code for RV32E
 */

    .section .text.entry
    .globl _start
    .type _start, @function

_start:
    /* Disable interrupts */
    csrci mstatus, 8  /* Clear MIE bit if CSR available */

    /* Initialize global pointer (if used) */
    /* RV32E: May not use gp register */

    /* Set stack pointer */
    la   sp, __stack_top

    /* Clear BSS section */
    la   t0, __bss_start
    la   t1, __bss_end
bss_clear:
    bge  t0, t1, bss_done
    sw   zero, 0(t0)
    addi t0, t0, 4
    j    bss_clear

bss_done:
    /* Copy data section from Flash to RAM */
    la   t0, __data_load     /* Source in Flash */
    la   t1, __data_start    /* Dest in RAM */
    la   t2, __data_end
data_copy:
    bge  t1, t2, data_done
    lw   t3, 0(t0)
    sw   t3, 0(t1)
    addi t0, t0, 4
    addi t1, t1, 4
    j    data_copy

data_done:
    /* Call RT-Thread entry */
    call rtthread_startup

    /* Should never return */
halt:
    j    halt

    .size _start, . - _start
