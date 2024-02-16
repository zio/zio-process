package zio.process

import ProcessPlatformSpecific._

private[process] trait ProcessInterface {

  def waitForUnsafe: Int

  def isAliveUnsafe: Boolean
  def destroyUnsafe(): Unit
  def destroyForciblyUnsafe: JProcess

  def pidUnsafe: Long

}
