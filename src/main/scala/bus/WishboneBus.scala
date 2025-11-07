package bus

import chisel3._
import chisel3.util._
import common.Config._

/**
 * Wishbone B4 总线主设备接口
 * 用于处理器核心访问外设和存储器
 */
class WishboneMaster extends Bundle {
  // 主设备输出信号
  val adr = Output(UInt(ADDR_WIDTH.W))      // 地址
  val dat_w = Output(UInt(BUS_WIDTH.W))     // 写数据
  val we = Output(Bool())                    // 写使能
  val sel = Output(UInt(SEL_WIDTH.W))       // 字节选择
  val stb = Output(Bool())                   // 选通信号
  val cyc = Output(Bool())                   // 周期有效

  // 从设备输入信号
  val dat_r = Input(UInt(BUS_WIDTH.W))      // 读数据
  val ack = Input(Bool())                    // 应答
  val err = Input(Bool())                    // 错误
  val rty = Input(Bool())                    // 重试
}

/**
 * Wishbone B4 总线从设备接口
 * 用于外设和存储器响应处理器访问
 */
class WishboneSlave extends Bundle {
  // 从设备输入信号
  val adr = Input(UInt(ADDR_WIDTH.W))       // 地址
  val dat_w = Input(UInt(BUS_WIDTH.W))      // 写数据
  val we = Input(Bool())                     // 写使能
  val sel = Input(UInt(SEL_WIDTH.W))        // 字节选择
  val stb = Input(Bool())                    // 选通信号
  val cyc = Input(Bool())                    // 周期有效

  // 从设备输出信号
  val dat_r = Output(UInt(BUS_WIDTH.W))     // 读数据
  val ack = Output(Bool())                   // 应答
  val err = Output(Bool())                   // 错误
  val rty = Output(Bool())                   // 重试
}

/**
 * Wishbone 总线连接辅助对象
 * 提供主从设备之间的连接方法
 */
object WishboneConnect {
  /**
   * 连接主设备和从设备
   */
  def apply(master: WishboneMaster, slave: WishboneSlave): Unit = {
    // 主设备 -> 从设备
    slave.adr := master.adr
    slave.dat_w := master.dat_w
    slave.we := master.we
    slave.sel := master.sel
    slave.stb := master.stb
    slave.cyc := master.cyc

    // 从设备 -> 主设备
    master.dat_r := slave.dat_r
    master.ack := slave.ack
    master.err := slave.err
    master.rty := slave.rty
  }
}

/**
 * Wishbone 总线仲裁器
 * 支持多主设备共享总线（简化版，当前只支持单主设备）
 */
class WishboneArbiter(numMasters: Int = 1) extends Module {
  val io = IO(new Bundle {
    val masters = Vec(numMasters, Flipped(new WishboneMaster))
    val slave = new WishboneMaster
  })

  if (numMasters == 1) {
    // 单主设备，直接连接
    io.slave.adr := io.masters(0).adr
    io.slave.dat_w := io.masters(0).dat_w
    io.slave.we := io.masters(0).we
    io.slave.sel := io.masters(0).sel
    io.slave.stb := io.masters(0).stb
    io.slave.cyc := io.masters(0).cyc

    io.masters(0).dat_r := io.slave.dat_r
    io.masters(0).ack := io.slave.ack
    io.masters(0).err := io.slave.err
    io.masters(0).rty := io.slave.rty
  } else {
    // 多主设备仲裁（轮询方式）
    val grant = RegInit(0.U(log2Ceil(numMasters).W))

    // 检查哪个主设备请求总线
    val requests = VecInit(io.masters.map(m => m.cyc && m.stb))

    // 轮询仲裁
    when(!io.slave.cyc) {
      for (i <- 0 until numMasters) {
        when(requests(i)) {
          grant := i.U
        }
      }
    }

    // 连接被授权的主设备
    io.slave.adr := io.masters(grant).adr
    io.slave.dat_w := io.masters(grant).dat_w
    io.slave.we := io.masters(grant).we
    io.slave.sel := io.masters(grant).sel
    io.slave.stb := io.masters(grant).stb
    io.slave.cyc := io.masters(grant).cyc

    // 响应信号分发
    for (i <- 0 until numMasters) {
      when(grant === i.U) {
        io.masters(i).dat_r := io.slave.dat_r
        io.masters(i).ack := io.slave.ack
        io.masters(i).err := io.slave.err
        io.masters(i).rty := io.slave.rty
      }.otherwise {
        io.masters(i).dat_r := 0.U
        io.masters(i).ack := false.B
        io.masters(i).err := false.B
        io.masters(i).rty := false.B
      }
    }
  }
}

/**
 * Wishbone 总线多路复用器
 * 将一个主设备连接到多个从设备
 */
class WishboneMux(numSlaves: Int) extends Module {
  val io = IO(new Bundle {
    val master = Flipped(new WishboneMaster)
    val slaves = Vec(numSlaves, new WishboneMaster)
    val sel = Input(UInt(log2Ceil(numSlaves).W))
  })

  // 默认值
  io.master.dat_r := 0.U
  io.master.ack := false.B
  io.master.err := false.B
  io.master.rty := false.B

  for (i <- 0 until numSlaves) {
    io.slaves(i).adr := io.master.adr
    io.slaves(i).dat_w := io.master.dat_w
    io.slaves(i).we := io.master.we
    io.slaves(i).sel := io.master.sel

    when(io.sel === i.U) {
      io.slaves(i).stb := io.master.stb
      io.slaves(i).cyc := io.master.cyc

      io.master.dat_r := io.slaves(i).dat_r
      io.master.ack := io.slaves(i).ack
      io.master.err := io.slaves(i).err
      io.master.rty := io.slaves(i).rty
    }.otherwise {
      io.slaves(i).stb := false.B
      io.slaves(i).cyc := false.B
    }
  }
}
