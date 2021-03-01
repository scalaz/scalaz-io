package zio.internal.macros

import zio._

import scala.reflect.macros.blackbox

class ProvideLayerAutoMacros(val c: blackbox.Context) extends AutoLayerMacroUtils {
  import c.universe._

  def provideLayerAutoImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[ZIO[Any, E, A]] = {
    assertProperVarArgs(layers)
    val expr = buildMemoizedLayer(generateExprGraph(layers), getRequirements[R])
    c.Expr[ZIO[Any, E, A]](q"${c.prefix}.provideLayer(${expr.tree})")
  }

  def provideCustomLayerAutoImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[ZIO[ZEnv, E, A]] = {
    assertProperVarArgs(layers)
    val ZEnvRequirements = getRequirements[ZEnv]
    val requirements     = getRequirements[R] diff ZEnvRequirements

    val zEnvLayer = Node(List.empty, ZEnvRequirements, reify(ZEnv.any))
    val nodes     = (zEnvLayer +: layers.map(getNode)).toList

    val layerExpr = buildMemoizedLayer(generateExprGraph(nodes), requirements)
    c.Expr[ZIO[ZEnv, E, A]](q"${c.prefix}.provideCustomLayer(${layerExpr.tree})")
  }
}