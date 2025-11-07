# RT-Thread 启动代码
# 最小化的 C 运行时环境

.section .text.start
.globl _start

_start:
    # 栈指针已由 BootROM 设置
    # 直接调用 main
    call main

    # 如果 main 返回，进入死循环
1:
    j 1b
