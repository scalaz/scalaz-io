/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.macros

import scala.reflect.macros.whitebox.Context

import com.github.ghik.silencer.silent

/**
 * Generates method accessors for a service into annotated object.
 */
private[macros] class AccessibleMacro(val c: Context) {
  import c.universe._

  protected case class ModuleInfo(
    module: ModuleDef,
    service: ClassDef
  )

  def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  @silent("pattern var [^\\s]+ in method unapply is never used")
  def apply(annottees: c.Tree*): c.Tree = {

    val any: Tree       = tq"_root_.scala.Any"
    val nothing: Tree   = tq"_root_.scala.Nothing"
    val throwable: Tree = tq"_root_.java.lang.Throwable"

    val moduleInfo = (annottees match {
      case (module: ModuleDef) :: Nil =>
        module.impl.body.collectFirst {
          case service @ ClassDef(_, name, _, _) if name.toTermName.toString == "Service" =>
            ModuleInfo(module, service)
        }
      case _ => None
    }).getOrElse(abort("@accessible macro can only be applied to objects containing `Service` trait."))

    sealed trait Capability
    object Capability {
      case class Effect(r: Tree, e: Tree, a: Tree)                  extends Capability
      case class Method(a: Tree)                                    extends Capability
      case class Sink(r: Tree, e: Tree, a0: Tree, a: Tree, b: Tree) extends Capability
      case class Stream(r: Tree, e: Tree, a: Tree)                  extends Capability
    }

    case class TypeInfo(capability: Capability) {

      val r: Tree = capability match {
        case Capability.Effect(r, _, _)     => r
        case Capability.Sink(r, _, _, _, _) => r
        case Capability.Stream(r, _, _)     => r
        case Capability.Method(_)           => any
      }

      val e: Tree = capability match {
        case Capability.Effect(_, e, _)     => e
        case Capability.Sink(_, e, _, _, _) => e
        case Capability.Stream(_, e, _)     => e
        case Capability.Method(_)           => throwable
      }

      val a: Tree = capability match {
        case Capability.Effect(_, _, a)      => a
        case Capability.Sink(_, e, a0, a, b) => tq"_root_.zio.stream.ZSink[$any, $e, $a0, $a, $b]"
        case Capability.Stream(_, e, a)      => tq"_root_.zio.stream.ZStream[$any, $e, $a]"
        case Capability.Method(a)            => a
      }
    }

    def typeInfo(tree: Tree): TypeInfo =
      tree match {
        case tq"$typeName[..$typeParams]" =>
          val typeArgs  = typeParams.map(t => c.typecheck(tq"$t", c.TYPEmode, silent = true).tpe)
          val tpe       = c.typecheck(tq"$typeName[..$typeArgs]", c.TYPEmode).tpe
          val dealiased = tpe.dealias
          val replacements: List[Tree] = (tpe.typeArgs zip typeParams).collect {
            case (NoType, t) => q"$t"
          }

          val (typeArgTrees, _) = dealiased.typeArgs.foldLeft(List.empty[Tree] -> replacements) {
            case ((acc, x :: xs), NoType) => (acc :+ x)     -> xs
            case ((acc, xs), t)           => (acc :+ q"$t") -> xs
          }

          (dealiased.typeSymbol.fullName, typeArgTrees) match {
            case ("zio.ZIO", r :: e :: a :: Nil)                     => TypeInfo(Capability.Effect(r, e, a))
            case ("zio.stream.ZSink", r :: e :: a0 :: a :: b :: Nil) => TypeInfo(Capability.Sink(r, e, a0, a, b))
            case ("zio.stream.ZStream", r :: e :: a :: Nil)          => TypeInfo(Capability.Stream(r, e, a))
            case _                                                   => TypeInfo(Capability.Method(tree))
          }
      }

    def makeAccessor(
      name: TermName,
      info: TypeInfo,
      impl: Tree,
      typeParams: List[TypeDef],
      paramLists: List[List[ValDef]],
      isVal: Boolean
    ): Tree = {
      val mods =
        if (impl == EmptyTree) Modifiers()
        else Modifiers(Flag.OVERRIDE)

      val returnType = info.capability match {
        case Capability.Effect(r, e, a) =>
          if (r != any) tq"_root_.zio.ZIO[_root_.zio.Has[Service] with $r, $e, $a]"
          else tq"_root_.zio.ZIO[_root_.zio.Has[Service], $e, $a]"
        case Capability.Stream(r, e, a) =>
          val value = tq"_root_.zio.stream.ZStream[$r, $e, $a]"
          if (r != any) tq"_root_.zio.ZIO[_root_.zio.Has[Service] with $r, $nothing, $value]"
          else tq"_root_.zio.ZIO[_root_.zio.Has[Service], $nothing, $value]"
        case Capability.Sink(r, e, a0, a, b) =>
          val value = tq"_root_.zio.stream.ZSink[$r, $e, $a0, $a, $b]"
          if (r != any) tq"_root_.zio.ZIO[_root_.zio.Has[Service] with $r, $e, $value]"
          else tq"_root_.zio.ZIO[_root_.zio.Has[Service], $e, $value]"
        case Capability.Method(a) =>
          tq"_root_.zio.ZIO[_root_.zio.Has[Service], $throwable, $a]"
      }

      val typeArgs = typeParams.map(_.name)

      def isRepeatedParamType(vd: ValDef) = vd.tpt match {
        case AppliedTypeTree(Select(_, nme), _) if nme == definitions.RepeatedParamClass.name => true
        case _                                                                                => false
      }

      val returnValue = (info.capability, paramLists) match {
        case (_: Capability.Effect, argLists) if argLists.flatten.nonEmpty =>
          val argNames = argLists.map(_.map { arg =>
            if (isRepeatedParamType(arg)) q"${arg.name}: _*"
            else q"${arg.name}"
          })
          q"_root_.zio.ZIO.accessM(_.get[Service].$name[..$typeArgs](...$argNames))"
        case (_: Capability.Effect, _) =>
          q"_root_.zio.ZIO.accessM(_.get[Service].$name)"
        case (_, argLists) if argLists.flatten.nonEmpty =>
          val argNames = argLists.map(_.map(_.name))
          q"_root_.zio.ZIO.access(_.get[Service].$name[..$typeArgs](...$argNames))"
        case (_, _) =>
          q"_root_.zio.ZIO.access(_.get[Service].$name)"
      }

      if (isVal) q"$mods val $name: $returnType = $returnValue"
      else
        paramLists match {
          case Nil       => q"$mods def $name[..$typeParams]: $returnType = $returnValue"
          case List(Nil) => q"$mods def $name[..$typeParams](): $returnType = $returnValue"
          case _         => q"$mods def $name[..$typeParams](...$paramLists): $returnType = $returnValue"
        }
    }

    val accessors =
      moduleInfo.service.impl.body.collect {
        case DefDef(_, termName, tparams, argLists, tree: Tree, impl) =>
          makeAccessor(termName, typeInfo(tree), impl, tparams, argLists, false)

        case ValDef(_, termName, tree: Tree, impl) =>
          makeAccessor(termName, typeInfo(tree), impl, Nil, Nil, true)
      }

    moduleInfo.module match {
      case q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }" =>
        q"""
           $mods object $tname extends { ..$earlydefns } with ..$parents { $self =>
             ..$body
             ..$accessors
           }
         """
      case _ => abort("@accesible macro failure - could not unquote annotated object.")
    }
  }
}
