package zio.process

import ProcessPlatformSpecific._

private[process] trait ProcessInterface {

  protected def waitForUnsafe: Int

  protected def isAliveUnsafe: Boolean
  protected def destroyUnsafe(): Unit
  protected def destroyForciblyUnsafe: JProcess

  protected def pidUnsafe: Long

}
