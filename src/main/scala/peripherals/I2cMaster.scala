package peripherals

import chisel3._
import chisel3.util._
import bus._
import common.Config._

/**
 * I2C Master 控制器 - 完整实现
 *
 * 寄存器映射：
 * 0x00: DATA   - 数据寄存器（读/写）
 * 0x04: ADDR   - 从设备地址寄存器 [6:0]
 * 0x08: CTRL   - 控制寄存器
 *       [0]: start - 发送START条件
 *       [1]: stop  - 发送STOP条件
 *       [2]: read  - 读操作
 *       [3]: write - 写操作
 *       [4]: ack   - ACK位（读时发送）
 * 0x0C: DIV    - 时钟分频寄存器 (SCL = SYS_CLK / (4 * (DIV + 1)))
 * 0x10: STATUS - 状态寄存器
 *       [0]: busy        - 传输忙
 *       [1]: ack_received - 收到ACK
 *       [2]: nack_received - 收到NACK
 *
 * I2C 协议：
 * - START: SDA 在SCL高时下降
 * - STOP:  SDA 在SCL高时上升
 * - 数据传输: SCL低时改变SDA，SCL高时采样SDA
 * - ACK: 第9位，低电平表示ACK
 */
class I2cMaster extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneMaster)

    // I2C 物理接口（开漏输出）
    val scl_o = Output(Bool())
    val scl_i = Input(Bool())
    val sda_o = Output(Bool())
    val sda_i = Input(Bool())
  })

  // ============================================================================
  // 寄存器定义
  // ============================================================================

  val addr_reg = RegInit(0.U(7.W))
  val div_reg  = RegInit(125.U(16.W))  // 默认100kHz @ 50MHz
  val cmd_reg  = RegInit(0.U(5.W))

  // ============================================================================
  // I2C 传输逻辑
  // ============================================================================

  // I2C 状态机
  val i2c_idle :: i2c_start :: i2c_addr :: i2c_ack_addr ::
      i2c_data :: i2c_ack_data :: i2c_stop :: Nil = Enum(7)
  val i2c_state = RegInit(i2c_idle)

  val scl_reg = RegInit(true.B)
  val sda_reg = RegInit(true.B)
  val clk_cnt = RegInit(0.U(16.W))
  val bit_cnt = RegInit(0.U(3.W))
  val data_reg = RegInit(0.U(8.W))
  val tx_data = RegInit(0.U(8.W))
  val rx_data = RegInit(0.U(8.W))

  val busy = WireDefault(false.B)
  val ack_received = RegInit(false.B)
  val nack_received = RegInit(false.B)

  // 命令FIFO（简化：单命令缓冲）
  val cmd_fifo_valid = RegInit(false.B)
  val cmd_fifo = RegInit(0.U(5.W))
  val data_fifo = RegInit(0.U(8.W))

  // 输出（开漏逻辑：输出1表示高阻）
  io.scl_o := scl_reg
  io.sda_o := sda_reg

  // 输入同步
  val scl_sync = RegInit(VecInit(Seq.fill(2)(true.B)))
  val sda_sync = RegInit(VecInit(Seq.fill(2)(true.B)))
  scl_sync(0) := io.scl_i
  scl_sync(1) := scl_sync(0)
  sda_sync(0) := io.sda_i
  sda_sync(1) := sda_sync(0)
  val scl_in = scl_sync(1)
  val sda_in = sda_sync(1)

  // 时钟分频（4相：准备->SCL下降->采样->SCL上升）
  val quarter_period = div_reg
  val phase_cnt = RegInit(0.U(2.W))

  // I2C 状态机
  switch(i2c_state) {
    is(i2c_idle) {
      scl_reg := true.B
      sda_reg := true.B
      ack_received := false.B
      nack_received := false.B

      when(cmd_fifo_valid) {
        val cmd = cmd_fifo
        when(cmd(0)) {  // START
          i2c_state := i2c_start
          clk_cnt := 0.U
        }.elsewhen(cmd(1)) {  // STOP
          i2c_state := i2c_stop
          clk_cnt := 0.U
        }.elsewhen(cmd(3) || cmd(2)) {  // WRITE or READ
          i2c_state := i2c_data
          tx_data := data_fifo
          bit_cnt := 0.U
          clk_cnt := 0.U
          phase_cnt := 0.U
        }
        cmd_fifo_valid := false.B
      }
    }

    is(i2c_start) {
      // START条件：SDA在SCL高时下降
      busy := true.B
      when(clk_cnt < quarter_period) {
        // SCL高，SDA高
        scl_reg := true.B
        sda_reg := true.B
        clk_cnt := clk_cnt + 1.U
      }.elsewhen(clk_cnt < quarter_period * 2.U) {
        // SCL高，SDA下降
        scl_reg := true.B
        sda_reg := false.B
        clk_cnt := clk_cnt + 1.U
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        // SCL下降
        scl_reg := false.B
        sda_reg := false.B
        clk_cnt := clk_cnt + 1.U
      }.otherwise {
        // 完成START
        i2c_state := i2c_idle
      }
    }

    is(i2c_data) {
      busy := true.B

      when(clk_cnt === quarter_period) {
        clk_cnt := 0.U
        phase_cnt := phase_cnt + 1.U

        switch(phase_cnt) {
          is(0.U) {
            // 相位0：准备数据，SCL低
            scl_reg := false.B
            sda_reg := tx_data(7)
            tx_data := tx_data << 1
          }
          is(1.U) {
            // 相位1：SCL上升
            scl_reg := true.B
          }
          is(2.U) {
            // 相位2：采样数据（读操作）
            scl_reg := true.B
            rx_data := Cat(rx_data(6, 0), sda_in)
          }
          is(3.U) {
            // 相位3：SCL下降，准备下一位
            scl_reg := false.B
            phase_cnt := 0.U

            when(bit_cnt === 7.U) {
              // 8位传输完成，进入ACK阶段
              i2c_state := i2c_ack_data
              bit_cnt := 0.U
            }.otherwise {
              bit_cnt := bit_cnt + 1.U
            }
          }
        }
      }.otherwise {
        clk_cnt := clk_cnt + 1.U
      }
    }

    is(i2c_ack_data) {
      busy := true.B

      when(clk_cnt === quarter_period) {
        clk_cnt := 0.U
        phase_cnt := phase_cnt + 1.U

        switch(phase_cnt) {
          is(0.U) {
            // SCL低，释放SDA（读）或发送ACK（写）
            scl_reg := false.B
            sda_reg := true.B  // 释放总线等待ACK
          }
          is(1.U) {
            // SCL上升
            scl_reg := true.B
          }
          is(2.U) {
            // 采样ACK位
            scl_reg := true.B
            when(sda_in === false.B) {
              ack_received := true.B
            }.otherwise {
              nack_received := true.B
            }
          }
          is(3.U) {
            // SCL下降，完成
            scl_reg := false.B
            i2c_state := i2c_idle
          }
        }
      }.otherwise {
        clk_cnt := clk_cnt + 1.U
      }
    }

    is(i2c_stop) {
      // STOP条件：SDA在SCL高时上升
      busy := true.B
      when(clk_cnt < quarter_period) {
        // SCL低，SDA低
        scl_reg := false.B
        sda_reg := false.B
        clk_cnt := clk_cnt + 1.U
      }.elsewhen(clk_cnt < quarter_period * 2.U) {
        // SCL上升
        scl_reg := true.B
        sda_reg := false.B
        clk_cnt := clk_cnt + 1.U
      }.elsewhen(clk_cnt < quarter_period * 3.U) {
        // SDA上升（STOP）
        scl_reg := true.B
        sda_reg := true.B
        clk_cnt := clk_cnt + 1.U
      }.otherwise {
        // 完成STOP
        i2c_state := i2c_idle
      }
    }
  }

  // ============================================================================
  // Wishbone 总线接口
  // ============================================================================

  val addr_offset = io.wb.adr_o - I2C_BASE.U

  // 写操作
  when(io.wb.cyc_o && io.wb.stb_o && io.wb.we_o) {
    switch(addr_offset) {
      is(I2cRegs.DATA.U) {
        data_fifo := io.wb.dat_o(7, 0)
      }
      is(I2cRegs.ADDR.U) {
        addr_reg := io.wb.dat_o(6, 0)
      }
      is(I2cRegs.CTRL.U) {
        // 写入命令触发操作
        when(!cmd_fifo_valid && i2c_state === i2c_idle) {
          cmd_fifo := io.wb.dat_o(4, 0)
          cmd_fifo_valid := true.B
        }
      }
      is(I2cRegs.DIV.U) {
        div_reg := io.wb.dat_o(15, 0)
      }
    }
  }

  // 读操作
  val status_reg = Cat(
    0.U(5.W),
    nack_received,
    ack_received,
    busy
  )

  io.wb.dat_i := MuxLookup(addr_offset, 0.U)(Seq(
    I2cRegs.DATA.U   -> rx_data,
    I2cRegs.ADDR.U   -> addr_reg,
    I2cRegs.CTRL.U   -> cmd_fifo,
    I2cRegs.DIV.U    -> div_reg,
    I2cRegs.STATUS.U -> status_reg
  ))

  // 应答信号
  io.wb.ack_i := RegNext(io.wb.cyc_o && io.wb.stb_o, false.B)
}
