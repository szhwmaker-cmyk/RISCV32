/*
 * RV32E Context Switch for RT-Thread
 *
 * RV32E has only 16 registers (x0-x15):
 * x0: zero (hardwired to 0)
 * x1: ra (return address)
 * x2: sp (stack pointer)
 * x3: gp (global pointer)
 * x4: tp (thread pointer)
 * x5-x7: t0-t2 (temporaries)
 * x8: s0/fp (saved register / frame pointer)
 * x9: s1 (saved register)
 * x10-x11: a0-a1 (function arguments / return values)
 * x12-x15: a2-a5 (function arguments)
 */

    .section .text
    .align 2
    .global rt_hw_context_switch_to
    .global rt_hw_context_switch
    .global rt_hw_context_switch_interrupt

/**
 * void rt_hw_context_switch_to(rt_ubase_t to);
 * x10 (a0): to thread stack pointer pointer
 */
rt_hw_context_switch_to:
    lw sp, 0(a0)               /* Load new task's SP */

    /* Restore registers */
    lw x1,  0 * 4(sp)          /* ra */
    /* skip sp (x2) */
    lw x3,  2 * 4(sp)          /* gp */
    lw x4,  3 * 4(sp)          /* tp */
    lw x5,  4 * 4(sp)          /* t0 */
    lw x6,  5 * 4(sp)          /* t1 */
    lw x7,  6 * 4(sp)          /* t2 */
    lw x8,  7 * 4(sp)          /* s0/fp */
    lw x9,  8 * 4(sp)          /* s1 */
    lw x10, 9 * 4(sp)          /* a0 */
    lw x11, 10 * 4(sp)         /* a1 */
    lw x12, 11 * 4(sp)         /* a2 */
    lw x13, 12 * 4(sp)         /* a3 */
    lw x14, 13 * 4(sp)         /* a4 */
    lw x15, 14 * 4(sp)         /* a5 */

    lw x2,  1 * 4(sp)          /* Restore sp last */

    ret

/**
 * void rt_hw_context_switch(rt_ubase_t from, rt_ubase_t to);
 * x10 (a0): from thread stack pointer pointer
 * x11 (a1): to thread stack pointer pointer
 */
rt_hw_context_switch:
    /* Save current context */
    addi sp, sp, -15 * 4       /* Allocate stack space */

    sw x1,  0 * 4(sp)          /* ra */
    sw x2,  1 * 4(sp)          /* sp (will be updated) */
    sw x3,  2 * 4(sp)          /* gp */
    sw x4,  3 * 4(sp)          /* tp */
    sw x5,  4 * 4(sp)          /* t0 */
    sw x6,  5 * 4(sp)          /* t1 */
    sw x7,  6 * 4(sp)          /* t2 */
    sw x8,  7 * 4(sp)          /* s0/fp */
    sw x9,  8 * 4(sp)          /* s1 */
    sw x10, 9 * 4(sp)          /* a0 */
    sw x11, 10 * 4(sp)         /* a1 */
    sw x12, 11 * 4(sp)         /* a2 */
    sw x13, 12 * 4(sp)         /* a3 */
    sw x14, 13 * 4(sp)         /* a4 */
    sw x15, 14 * 4(sp)         /* a5 */

    /* Update saved sp */
    sw sp, 1 * 4(sp)

    /* Save current SP to from thread */
    sw sp, 0(a0)

    /* Load new task's SP */
    lw sp, 0(a1)

    /* Restore new context */
    lw x1,  0 * 4(sp)          /* ra */
    /* skip sp */
    lw x3,  2 * 4(sp)          /* gp */
    lw x4,  3 * 4(sp)          /* tp */
    lw x5,  4 * 4(sp)          /* t0 */
    lw x6,  5 * 4(sp)          /* t1 */
    lw x7,  6 * 4(sp)          /* t2 */
    lw x8,  7 * 4(sp)          /* s0/fp */
    lw x9,  8 * 4(sp)          /* s1 */
    lw x10, 9 * 4(sp)          /* a0 */
    lw x11, 10 * 4(sp)         /* a1 */
    lw x12, 11 * 4(sp)         /* a2 */
    lw x13, 12 * 4(sp)         /* a3 */
    lw x14, 13 * 4(sp)         /* a4 */
    lw x15, 14 * 4(sp)         /* a5 */

    lw x2,  1 * 4(sp)          /* Restore sp */

    ret

/**
 * void rt_hw_context_switch_interrupt(rt_ubase_t from, rt_ubase_t to);
 * For RV32E without interrupt, this is same as rt_hw_context_switch
 */
rt_hw_context_switch_interrupt:
    j rt_hw_context_switch

/**
 * rt_base_t rt_hw_interrupt_disable(void);
 * Dummy implementation (no interrupt support)
 */
    .global rt_hw_interrupt_disable
rt_hw_interrupt_disable:
    li a0, 0
    ret

/**
 * void rt_hw_interrupt_enable(rt_base_t level);
 * Dummy implementation (no interrupt support)
 */
    .global rt_hw_interrupt_enable
rt_hw_interrupt_enable:
    ret
