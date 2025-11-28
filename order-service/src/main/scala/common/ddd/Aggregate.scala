package common.ddd

trait Aggregate[T] extends Entity[T] :
  override def getId: T
