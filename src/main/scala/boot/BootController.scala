package boot

import chisel3._
import chisel3.util._
import common.Config._

/**
 * Boot 控制器
 *
 * 负责从 SPI Flash 加载启动代码到 RAM
 * 简化实现：直接允许从 Flash 执行（XIP 模式）
 *
 * 状态机：
 * IDLE -> LOADING -> DONE
 */
class BootController extends Module {
  val io = IO(new Bundle {
    val boot_done = Output(Bool())
  })

  // 状态定义
  val sIdle :: sLoading :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // 简化实现：直接进入 DONE 状态
  // 实际实现需要完整的 SPI Flash 读取逻辑
  when(state === sIdle) {
    state := sDone
  }

  io.boot_done := state === sDone
}
