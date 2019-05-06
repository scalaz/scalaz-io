package scalaz.zio.stacktracer.impl

//import java.util.concurrent.ConcurrentHashMap
import java.util

import scalaz.zio.stacktracer.{SourceLocation, SourceLocationCache}

final class ConcurrentHashMapCache extends SourceLocationCache {
//  val cache = new ConcurrentHashMap[Class[_], SourceLocation]
  val cache = new util.HashMap[Class[_], SourceLocation]

  override def getOrElseUpdate(lambda: AnyRef, f: AnyRef => SourceLocation): SourceLocation = {
    // not using atomic ops to avoid needless spin-lock because `f` is repeatable – if the value is there it must be the same
    val clazz = lambda.getClass

    val res = cache.get(clazz)
    if (res eq null) {
      val v = f(lambda)
      cache.putIfAbsent(clazz, v)
      v
    } else {
      res
    }
  }
}

object ConcurrentHashMapCache {
  val globalMutableSharedSourceLocationCache = new ConcurrentHashMapCache
}
