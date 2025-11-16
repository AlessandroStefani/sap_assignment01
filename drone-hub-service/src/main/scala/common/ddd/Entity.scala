package common.ddd

trait Entity[T]:
  def getId: T;
