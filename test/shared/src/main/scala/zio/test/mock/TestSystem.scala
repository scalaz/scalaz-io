package zio.test.mock

import zio.{ Ref, UIO, ZIO }
import zio.system.System

case class TestSystem(systemState: Ref[TestSystem.Data]) extends System.Service[Any] {

  override def env(variable: String): ZIO[Any, SecurityException, Option[String]] =
    systemState.get.map(_.envs.get(variable))

  override def property(prop: String): ZIO[Any, Throwable, Option[String]] =
    systemState.get.map(_.properties.get(prop))

  override val lineSeparator: ZIO[Any, Nothing, String] =
    systemState.get.map(_.lineSeparator)

  def putEnv(name: String, value: String): UIO[Unit] =
    systemState.update(data => data.copy(envs = data.envs.updated(name, value))).unit

  def putProperty(name: String, value: String): UIO[Unit] =
    systemState.update(data => data.copy(properties = data.properties.updated(name, value))).unit

  def setLineSeparator(lineSep: String): UIO[Unit] =
    systemState.update(_.copy(lineSeparator = lineSep)).unit

  def clearEnv(variable: String): UIO[Unit] =
    systemState.update(data => data.copy(envs = data.envs - variable)).unit

  def clearProperty(prop: String): UIO[Unit] =
    systemState.update(data => data.copy(properties = data.properties - prop)).unit
}

object TestSystem {
  val DefaultData: Data = Data(Map(), Map(), "\n")

  def make(data: Data): UIO[TestSystem] =
    Ref.make(data).map(TestSystem(_))

  case class Data(
    properties: Map[String, String] = Map.empty,
    envs: Map[String, String] = Map.empty,
    lineSeparator: String = "\n"
  )
}
