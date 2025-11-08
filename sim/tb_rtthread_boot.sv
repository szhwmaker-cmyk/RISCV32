//=============================================================================
// SystemVerilog Testbench for RT-Thread Boot Test
//
// 本测试平台用于外部仿真器（Verilator, VCS, ModelSim 等）
// 功能：
// 1. 实例化 RV32E SoC
// 2. 提供 Flash 模型并加载启动程序
// 3. 监控 UART 输出
// 4. 验证系统启动成功
//=============================================================================

`timescale 1ns/1ps

module tb_rtthread_boot;

  //===========================================================================
  // 参数定义
  //===========================================================================
  parameter CLOCK_PERIOD = 20;        // 50 MHz = 20ns period
  parameter SIM_TIME = 100_000_000;   // 100ms simulation time
  parameter UART_BAUDRATE = 115200;
  parameter UART_BIT_PERIOD = 1_000_000_000 / UART_BAUDRATE; // ns

  //===========================================================================
  // 信号定义
  //===========================================================================
  logic clock;
  logic reset;

  // UART
  logic uart_tx;
  logic uart_rx;

  // GPIO
  logic [15:0] gpio_in;
  logic [15:0] gpio_out;
  logic [15:0] gpio_oe;

  // SPI
  logic spi_sclk;
  logic spi_mosi;
  logic spi_miso;
  logic spi_cs_n;

  // I2C
  logic i2c_scl_i, i2c_scl_o, i2c_scl_oe;
  logic i2c_sda_i, i2c_sda_o, i2c_sda_oe;

  // Flash SPI
  logic flash_sclk;
  logic flash_mosi;
  logic flash_miso;
  logic flash_cs_n;

  //===========================================================================
  // 时钟生成
  //===========================================================================
  initial begin
    clock = 0;
    forever #(CLOCK_PERIOD/2) clock = ~clock;
  end

  //===========================================================================
  // 复位生成
  //===========================================================================
  initial begin
    reset = 1;
    repeat(10) @(posedge clock);
    reset = 0;
    $display("[%0t] Reset released", $time);
  end

  //===========================================================================
  // DUT 实例化 (从 Chisel 生成的 Verilog)
  //===========================================================================
  // 注意：需要先使用 Chisel 生成 Verilog
  // 命令：mill rv32esoc.runMain soc.RV32E_SoCMain

  RV32E_SoC dut (
    .clock(clock),
    .reset(reset),
    .io_uart_tx(uart_tx),
    .io_uart_rx(uart_rx),
    .io_gpio_in(gpio_in),
    .io_gpio_out(gpio_out),
    .io_gpio_oe(gpio_oe),
    .io_spi_sclk(spi_sclk),
    .io_spi_mosi(spi_mosi),
    .io_spi_miso(spi_miso),
    .io_spi_cs_n(spi_cs_n),
    .io_i2c_scl_i(i2c_scl_i),
    .io_i2c_scl_o(i2c_scl_o),
    .io_i2c_scl_oe(i2c_scl_oe),
    .io_i2c_sda_i(i2c_sda_i),
    .io_i2c_sda_o(i2c_sda_o),
    .io_i2c_sda_oe(i2c_sda_oe),
    .io_flash_sclk(flash_sclk),
    .io_flash_mosi(flash_mosi),
    .io_flash_miso(flash_miso),
    .io_flash_cs_n(flash_cs_n)
  );

  //===========================================================================
  // Flash 模型
  //===========================================================================
  spi_flash_model #(
    .CAPACITY(1024 * 1024),           // 1MB
    .FLASH_IMAGE("flash_boot.hex")    // 启动镜像文件
  ) flash_model (
    .sclk(flash_sclk),
    .mosi(flash_mosi),
    .miso(flash_miso),
    .cs_n(flash_cs_n)
  );

  //===========================================================================
  // UART 监控器
  //===========================================================================
  reg [7:0] uart_rx_data;
  reg uart_rx_valid;
  string uart_output = "";

  // UART 接收任务
  task automatic uart_receive();
    integer bit_count;
    reg [9:0] rx_shift;

    forever begin
      // 等待起始位
      @(negedge uart_tx);

      // 等待半个位周期（采样在位中心）
      #(UART_BIT_PERIOD / 2);

      // 接收 8 个数据位
      for (bit_count = 0; bit_count < 8; bit_count++) begin
        #UART_BIT_PERIOD;
        rx_shift[bit_count] = uart_tx;
      end

      // 接收停止位
      #UART_BIT_PERIOD;

      // 输出接收到的字符
      uart_rx_data = rx_shift[7:0];
      uart_rx_valid = 1;

      $write("%c", uart_rx_data);
      uart_output = {uart_output, string'(uart_rx_data)};

      @(posedge clock);
      uart_rx_valid = 0;
    end
  endtask

  //===========================================================================
  // 测试主程序
  //===========================================================================
  initial begin
    // 初始化信号
    uart_rx = 1;
    gpio_in = 0;
    spi_miso = 0;
    i2c_scl_i = 1;
    i2c_sda_i = 1;

    // 启动 UART 监控
    fork
      uart_receive();
    join_none

    // 等待复位完成
    @(negedge reset);
    $display("\n" , "=".repeat(60));
    $display("RT-Thread Boot Test - RV32E SoC");
    $display("=".repeat(60));
    $display("Clock frequency: 50 MHz");
    $display("UART baudrate: %0d", UART_BAUDRATE);
    $display("Flash image: flash_boot.hex");
    $display("=".repeat(60), "\n");

    // 运行仿真
    $display("[%0t] Starting simulation...", $time);

    // 等待一段时间或检测到启动消息
    fork
      begin
        // 超时检测
        #SIM_TIME;
        $display("\n[%0t] Simulation timeout", $time);
      end
      begin
        // 等待 RT-Thread 启动消息
        wait(uart_output.len() > 10 ||
             (uart_output.substr("RT-Thread") != "" ||
              uart_output.substr("HI") != ""));
        #10000; // 等待更多输出
        $display("\n[%0t] Boot message detected!", $time);
      end
    join_any
    disable fork;

    // 测试结果
    $display("\n" , "=".repeat(60));
    $display("Simulation Results");
    $display("=".repeat(60));
    $display("Simulation time: %0t", $time);
    $display("UART output (%0d chars):", uart_output.len());
    $display("%s", uart_output);
    $display("=".repeat(60));

    if (uart_output.len() > 0) begin
      $display("✓ TEST PASSED: UART output detected");
      if (uart_output.substr("RT-Thread") != "" ||
          uart_output.substr("HI") != "") begin
        $display("✓ Boot message verified");
      end
    end else begin
      $display("✗ TEST FAILED: No UART output");
    end

    $display("=".repeat(60), "\n");
    $finish;
  end

  //===========================================================================
  // 波形导出
  //===========================================================================
  initial begin
    $dumpfile("rtthread_boot.vcd");
    $dumpvars(0, tb_rtthread_boot);
  end

  //===========================================================================
  // 超时保护
  //===========================================================================
  initial begin
    #(SIM_TIME * 2);
    $display("\n[ERROR] Maximum simulation time exceeded!");
    $finish;
  end

endmodule

//=============================================================================
// SPI Flash 模型（简化版）
//=============================================================================
module spi_flash_model #(
  parameter CAPACITY = 1024 * 1024,
  parameter FLASH_IMAGE = "flash.hex"
)(
  input  logic sclk,
  input  logic mosi,
  output logic miso,
  input  logic cs_n
);

  // Flash 存储器
  logic [7:0] flash_mem [0:CAPACITY-1];

  // 状态机
  typedef enum {IDLE, CMD, ADDR, DUMMY, DATA} state_t;
  state_t state = IDLE;

  logic [7:0] cmd_reg;
  logic [23:0] addr_reg;
  integer bit_count;
  logic [7:0] data_byte;

  // 加载 Flash 镜像
  initial begin
    // 初始化为 0xFF（空白 Flash）
    for (int i = 0; i < CAPACITY; i++)
      flash_mem[i] = 8'hFF;

    // 从文件加载
    if (FLASH_IMAGE != "") begin
      $readmemh(FLASH_IMAGE, flash_mem);
      $display("[Flash] Loaded image from %s", FLASH_IMAGE);
    end
  end

  // SPI 接口逻辑
  always @(posedge sclk or posedge cs_n) begin
    if (cs_n) begin
      state <= IDLE;
      bit_count <= 0;
      miso <= 1'b0;
    end else begin
      case (state)
        IDLE: begin
          state <= CMD;
          bit_count <= 0;
        end

        CMD: begin
          cmd_reg <= {cmd_reg[6:0], mosi};
          bit_count <= bit_count + 1;
          if (bit_count == 7) begin
            state <= ADDR;
            bit_count <= 0;
          end
        end

        ADDR: begin
          addr_reg <= {addr_reg[22:0], mosi};
          bit_count <= bit_count + 1;
          if (bit_count == 23) begin
            if (cmd_reg == 8'h0B)  // FAST_READ
              state <= DUMMY;
            else
              state <= DATA;
            bit_count <= 0;
          end
        end

        DUMMY: begin
          bit_count <= bit_count + 1;
          if (bit_count == 7) begin
            state <= DATA;
            bit_count <= 0;
            data_byte <= flash_mem[addr_reg];
          end
        end

        DATA: begin
          miso <= data_byte[7];
          data_byte <= {data_byte[6:0], 1'b0};
          bit_count <= bit_count + 1;
          if (bit_count == 7) begin
            addr_reg <= addr_reg + 1;
            data_byte <= flash_mem[addr_reg + 1];
            bit_count <= 0;
          end
        end
      endcase
    end
  end

endmodule
