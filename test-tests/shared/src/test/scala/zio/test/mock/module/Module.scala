/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test.mock.module

import zio.{ IO, ZIO }

/**
 * Example module used for testing ZIO Mock framework.
 */
object Module {

  trait Service {
    val static: IO[String, String]
    def zeroParams: IO[String, String]
    def zeroParamsWithParens(): IO[String, String]
    def singleParam(a: Int): IO[String, String]
    def manyParams(a: Int, b: String, c: Long): IO[String, String]
    def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String]
    def command(a: Int): IO[Unit, Unit]
    def looped(a: Int): IO[Nothing, Nothing]
    def overloaded(n: Int): IO[String, String]
    def overloaded(n: Long): IO[String, String]
    def maxParams(
      a: Int,
      b: Int,
      c: Int,
      d: Int,
      e: Int,
      f: Int,
      g: Int,
      h: Int,
      i: Int,
      j: Int,
      k: Int,
      l: Int,
      m: Int,
      n: Int,
      o: Int,
      p: Int,
      q: Int,
      r: Int,
      s: Int,
      t: Int,
      u: Int,
      v: Int
    ): IO[String, String]
  }

  val static                                     = ZIO.accessM[Module](_.get.static)
  def zeroParams                                 = ZIO.accessM[Module](_.get.zeroParams)
  def zeroParamsWithParens()                     = ZIO.accessM[Module](_.get.zeroParamsWithParens())
  def singleParam(a: Int)                        = ZIO.accessM[Module](_.get.singleParam(a))
  def manyParams(a: Int, b: String, c: Long)     = ZIO.accessM[Module](_.get.manyParams(a, b, c))
  def manyParamLists(a: Int)(b: String)(c: Long) = ZIO.accessM[Module](_.get.manyParamLists(a)(b)(c))
  def command(a: Int)                            = ZIO.accessM[Module](_.get.command(a))
  def looped(a: Int)                             = ZIO.accessM[Module](_.get.looped(a))
  def overloaded(n: Int)                         = ZIO.accessM[Module](_.get.overloaded(n))
  def overloaded(n: Long)                        = ZIO.accessM[Module](_.get.overloaded(n))
  def maxParams(
    a: Int,
    b: Int,
    c: Int,
    d: Int,
    e: Int,
    f: Int,
    g: Int,
    h: Int,
    i: Int,
    j: Int,
    k: Int,
    l: Int,
    m: Int,
    n: Int,
    o: Int,
    p: Int,
    q: Int,
    r: Int,
    s: Int,
    t: Int,
    u: Int,
    v: Int
  ) = ZIO.accessM[Module](_.get.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
}
