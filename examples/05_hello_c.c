/**
 * =====================================================
 * 示例程序 5: Hello World (C语言版本)
 * =====================================================
 * 功能：C语言版本的Hello World程序
 * 外设：UART, GPIO
 * 难度：⭐⭐ 初级
 * =====================================================
 */

#include <stdint.h>

// ========== 外设基地址定义 ==========
#define UART_BASE  0x20000000
#define GPIO_BASE  0x20001000

// ========== UART寄存器定义 ==========
#define UART_TXDATA  (*(volatile uint32_t*)(UART_BASE + 0x00))
#define UART_RXDATA  (*(volatile uint32_t*)(UART_BASE + 0x04))
#define UART_STATUS  (*(volatile uint32_t*)(UART_BASE + 0x08))
#define UART_CTRL    (*(volatile uint32_t*)(UART_BASE + 0x0C))

// ========== GPIO寄存器定义 ==========
#define GPIO_DATA    (*(volatile uint32_t*)(GPIO_BASE + 0x00))
#define GPIO_DIR     (*(volatile uint32_t*)(GPIO_BASE + 0x04))
#define GPIO_OE      (*(volatile uint32_t*)(GPIO_BASE + 0x08))

// ========== UART状态位定义 ==========
#define UART_RX_EMPTY  (1 << 0)
#define UART_TX_FULL   (1 << 1)

// ========== 函数声明 ==========
void uart_init(void);
void uart_putc(char c);
void uart_puts(const char* s);
void gpio_init(void);
void gpio_set_led(uint8_t value);
void delay_ms(uint32_t ms);

/**
 * UART初始化
 */
void uart_init(void) {
    // UART已由硬件配置为115200波特率
    // 这里只需要确保使能即可
    UART_CTRL = 0x03;  // 使能TX和RX
}

/**
 * 发送一个字符
 */
void uart_putc(char c) {
    // 等待发送FIFO不满
    while (UART_STATUS & UART_TX_FULL);

    // 发送字符
    UART_TXDATA = c;
}

/**
 * 发送字符串
 */
void uart_puts(const char* s) {
    while (*s) {
        uart_putc(*s++);
    }
}

/**
 * GPIO初始化
 */
void gpio_init(void) {
    // 设置GPIO[3:0]为输出（LED）
    GPIO_DIR = 0x0F;
    GPIO_OE  = 0x0F;
}

/**
 * 设置LED状态
 */
void gpio_set_led(uint8_t value) {
    GPIO_DATA = value & 0x0F;
}

/**
 * 延时函数（软件延时）
 * @param ms 延时毫秒数
 */
void delay_ms(uint32_t ms) {
    // 假设CPU时钟为25MHz
    // 每毫秒约25000个周期
    // 考虑循环开销，实际约10000次循环
    const uint32_t loops_per_ms = 10000;

    for (uint32_t i = 0; i < ms; i++) {
        for (uint32_t j = 0; j < loops_per_ms; j++) {
            // 空循环
            asm volatile("nop");
        }
    }
}

/**
 * 主函数
 */
int main(void) {
    // 初始化外设
    uart_init();
    gpio_init();

    // 发送启动消息
    uart_puts("\r\n");
    uart_puts("========================================\r\n");
    uart_puts("  RV32E SoC - Hello World (C)  \r\n");
    uart_puts("========================================\r\n");
    uart_puts("System initializing...\r\n");

    // LED开机自检序列
    for (int i = 0; i < 4; i++) {
        gpio_set_led(1 << i);  // 依次点亮LED
        delay_ms(200);
    }
    gpio_set_led(0x00);  // 全灭

    uart_puts("System ready!\r\n");
    uart_puts("\r\n");

    // 主循环
    uint32_t counter = 0;
    while (1) {
        // LED闪烁模式
        gpio_set_led(0x0F);  // 全亮
        uart_puts("LED ON  - Count: ");
        uart_putc('0' + (counter % 10));
        uart_puts("\r\n");
        delay_ms(500);

        gpio_set_led(0x00);  // 全灭
        uart_puts("LED OFF - Count: ");
        uart_putc('0' + (counter % 10));
        uart_puts("\r\n");
        delay_ms(500);

        counter++;
    }

    return 0;  // 永远不会到达这里
}

/**
 * 启动代码入口
 * 设置栈指针并调用main函数
 */
void _start(void) {
    // 设置栈指针到RAM顶部 (0x80010000)
    asm volatile("lui sp, 0x80010");

    // 调用main函数
    main();

    // 如果main返回，进入无限循环
    while (1);
}

/**
 * =====================================================
 * 预期UART输出：
 * ========================================
 *   RV32E SoC - Hello World (C)
 * ========================================
 * System initializing...
 * System ready!
 *
 * LED ON  - Count: 0
 * LED OFF - Count: 0
 * LED ON  - Count: 1
 * LED OFF - Count: 1
 * ...
 *
 * 预期LED行为：
 * - 开机时LED[0-3]依次点亮（自检）
 * - 然后所有LED以1Hz频率同步闪烁
 * =====================================================
 */
