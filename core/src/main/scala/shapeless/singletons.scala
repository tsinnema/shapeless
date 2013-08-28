/*
 * Copyright (c) 2013 Miles Sabin 
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

package shapeless

import scala.language.existentials
import scala.language.experimental.macros

import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

trait Witness {
  type T
  val value: T {}
}

object Witness {
  type Aux[T0] = Witness { type T = T0 }
  type Lt[Lub] = Witness { type T <: Lub }

  implicit def apply[T] = macro SingletonTypeMacros.materializeImpl[T]

  implicit def apply[T](t: T) = macro SingletonTypeMacros.convertImpl[T]
}

trait WitnessWith[TC[_]] extends Witness {
  val instance: TC[T]
  type Out
}

trait LowPriorityWitnessWith {
  implicit def apply2[H, TC2[_ <: H, _], S <: H, T](t: T) = macro SingletonTypeMacros.convertInstanceImpl2[H, TC2, S, T]
}

object WitnessWith extends LowPriorityWitnessWith {
  type Aux[TC[_], T0] = WitnessWith[TC] { type T = T0  }
  type Lt[TC[_], Lub] = WitnessWith[TC] { type T <: Lub }

  implicit def apply1[TC[_], T](t: T) = macro SingletonTypeMacros.convertInstanceImpl1[TC, T]
}

class Function1Body[I,O](f: I => O) extends StaticAnnotation

trait SingletonTypeMacros[C <: Context] {
  import syntax.SingletonOps
  type SingletonOpsLt[Lub] = SingletonOps { type T <: Lub }

  val c: C

  import c.universe._
  import Flag._

  def mkWitnessT[W](sTpe: Type, s: Any) = mkWitness[W](TypeTree(sTpe), Literal(Constant(s)))

  //  'sTpt: Tree' instead of 'sTpt: TypeTree' in order to accept 
  //  an 'Annotated(annotation, typetree)'
  def mkWitness[W](sTpt: Tree, s: Tree) = {
  //def mkWitness[W](sTpt: TypTree, s: Tree) = {
    val witnessTpt = Ident(typeOf[Witness].typeSymbol)
    val T = TypeDef(Modifiers(), newTypeName("T"), List(), sTpt)
    val value = ValDef(Modifiers(), newTermName("value"), sTpt, s)
    c.Expr[W] {
      mkImplClass(witnessTpt, List(T, value), List())
    }
  }

  def materializeImpl[T: c.WeakTypeTag]: c.Expr[Witness.Aux[T]] = {
    weakTypeOf[T] match {
      case t @ ConstantType(Constant(s)) => mkWitnessT(t, s)

      case t @ SingleType(p, v) if !v.isParameter =>
        mkWitness(TypeTree(t), TypeApply(Select(Ident(v), newTermName("asInstanceOf")), List(TypeTree(t))))

      case t =>
        c.abort(c.enclosingPosition, s"Type argument $t is not a singleton type")
    }
  }

  def convertImpl[T: c.WeakTypeTag](t: c.Expr[T]): c.Expr[Witness.Lt[T]] = {
    import scala.collection.immutable.ListMap

    val f1sym = typeOf[Function1[_,_]].typeSymbol

    (weakTypeOf[T], t.tree) match {
      case (tpe @ ConstantType(const: Constant), _) =>
        mkWitness(TypeTree(tpe), Literal(const))

      case (tpe @ SingleType(p, v), tree) if !v.isParameter =>
        mkWitness(TypeTree(tpe), tree)

      case (tpe: TypeRef, Literal(const: Constant)) =>
        mkWitness(TypeTree(ConstantType(const)), Literal(const))

      //  FIXME, CRITICAL: Before trying to publish anything, make sure you 
      //  are avoiding ghastly bugs relating to free variables and evaluation 
      //  of this witness in a different context from the one in which it's 
      //  constructed!
      case (tpe @ TypeRef(_, `f1sym`, List(iType, oType)), func @ Function(_,_)) => {

        //  HAD I BEEN TEMPORARILY LOOKING TO DEFINE/CONSTRUCT THE WRONG THING?
        //  - Function body representation (in mkFnWitness) vs. the annotation?
        //  - Take a closer look at what xeno-by posted
        //    - Yes indeed I guess I was wrong.
        //  - Ok I think I can have a plan now.
        //    - TODO: Have a plan.
        //  
        //  - Do I need any alternative to plain mkWitness after all?
        //    - Take a look at the case code in convertImpl. (Moved this note here now.)
        //    - What I might need to do is move back again to mkWitness taking Tree
        //      instead of TypeTree

        val annTypeTree =
          Annotated(
            Apply(
              Select(
                New(
                  AppliedTypeTree(
                    Ident(newTypeName("Function1Body")),
                    List(Ident(iType.typeSymbol.name), Ident(oType.typeSymbol.name))
                )),
                nme.CONSTRUCTOR
              ),
              List(func)
            ),
            TypeTree(tpe)
          )
        mkWitness(annTypeTree, func)

        // val annTypeTree = 
        //   Annotated(
        //     Apply(
        //       TypeApply(
        //         Select(Ident(newTermName("FunctionBody1")), newTermName("apply")),
        //         List(Ident(iType.typeSymbol.name), Ident(oType.typeSymbol.name))
        //       ),
        //       List(func)
        //     ),
        //     TypeTree(tpe)
        //   )
        // mkWitness(annTypeTree, func)
          
        /*
        PackageDef(
          Ident(newTermName("<empty>")),
          List(
            ClassDef(
              Modifiers(
                NoFlags, tpnme.EMPTY,
                List(
                  Apply(
                    Select(
                      New(
                        AppliedTypeTree(
                          Ident(newTypeName("foo")),
                          List(Ident(newTypeName("Int")))
                      )),
                      nme.CONSTRUCTOR
                    ),
                    List(Literal(Constant(2)))
              ))),
              newTypeName("C"), List(),
              Template(
                List(Select(Ident(scala), newTypeName("AnyRef"))),
                emptyValDef,
                List(
                  DefDef(
                    Modifiers(), nme.CONSTRUCTOR, List(), List(List()), TypeTree(),
                    Block(
                      List(
                        Apply(
                          Select(Super(This(tpnme.EMPTY), tpnme.EMPTY), nme.CONSTRUCTOR),
                          List()
                      )),
                      Literal(Constant(()))
        )))))))

        New(AppliedTypeTree(...))

        package <empty> {
          @new foo[Int](2) class C extends scala.AnyRef {
            def <init>() = {
              super.<init>();
              ()
            }
          }
        }
        */

        //mkFnWitness(tpe, func)
        //mkFnWitness(TypeTree(tpe), func)

        // val annTypeTree = 
        //   Annotated(
        //     Annotation(
        //       TypeRef(NoPrefix, typeOf[Function1Body[_,_]].typeSymbol, List(iType,oType)),
        //       List(func), ListMap()
        //     ),
        //     TypeTree(tpe)
        //   )
        // mkWitness(annTypeTree, func)

        // val typeTree = // FIXME wrap this with Annotated?
        //   TypeTree(AnnotatedType(
        //     List(Annotation(
        //       TypeRef(NoPrefix, typeOf[Function1Body[_,_]].typeSymbol, List(iType,oType)),
        //       List(func), ListMap()
        //     )),
        //     TypeRef(NoPrefix, f1sym, List(iType,oType)),
        //     NoSymbol
        //   ))
        // mkWitness(typeTree, func)
      }

      case _ =>
        c.abort(c.enclosingPosition, s"Expression ${t.tree} does not evaluate to a constant or a stable value")
    }
  }
  // def mkFnWitness(fnTpt: TypeTree, func: Function) = {
  //
  //   val fnApplyDefDef =
  //     DefDef // STUFF
  //
  //   val fnClassDef =
  //     ClassDef(
  //       Modifiers(FINAL),
  //       newTypeName(c.fresh()),
  //       List(),
  //       Template(
  //         List(fnTpt),
  //         emptyValDef,
  //         constructor(false) // number of arguments not > 0
  //         // STUFF
  //
  //   // STUFF

  // }
    // from mkImplClass:
    //
    // val classDef =
    //   ClassDef(
    //     Modifiers(FINAL),
    //     name,
    //     List(),
    //     Template(
    //       List(parent),
    //       emptyValDef,
    //       constructor(args.size > 0) :: defns
    //     )
    //   )

    // def mkWitness[W](sTpt: TypTree, s: Tree) = {
    //   val witnessTpt = Ident(typeOf[Witness].typeSymbol)
    //   val T = TypeDef(Modifiers(), newTypeName("T"), List(), sTpt)
    //   val value = ValDef(Modifiers(), newTermName("value"), sTpt, s)
    //   c.Expr[W] {
    //     mkImplClass(witnessTpt, List(T, value), List())
    //   }
    // }


  def mkWitnessWith[W](singletonInstanceTpt: TypTree, sTpt: TypTree, s: Tree, i: Tree) = {
    val iTpe =
      (i.tpe match {
        case NullaryMethodType(resTpe) => resTpe
        case other => other
      }).normalize

    val iOut = iTpe.member(newTypeName("Out")) match {
      case NoSymbol => definitions.NothingClass
      case other => other
    }

    val niTpt = TypeTree(iTpe)

    val T = TypeDef(Modifiers(), newTypeName("T"), List(), sTpt)
    val value = ValDef(Modifiers(), newTermName("value"), sTpt, s)
    val instance = ValDef(Modifiers(), newTermName("instance"), niTpt, i)
    val Out = TypeDef(Modifiers(), newTypeName("Out"), List(), Ident(iOut))
    c.Expr[W] {
      mkImplClass(singletonInstanceTpt, List(T, value, instance, Out), List())
    }
  }

  def convertInstanceImpl[TC[_], T](t: c.Expr[T])
    (implicit tcTag: c.WeakTypeTag[TC[_]], tTag: c.WeakTypeTag[T]): c.Expr[WitnessWith.Lt[TC, T]] = {
      val tc = tcTag.tpe.typeConstructor
      val siTpt =
        AppliedTypeTree(
          Select(Ident(newTermName("shapeless")), newTypeName("WitnessWith")),
          List(TypeTree(tc))
        )

      (weakTypeOf[T], t.tree) match {
        case (tpe @ ConstantType(const: Constant), _) =>
          val tci = appliedType(tc, List(tpe))
          val i = c.inferImplicitValue(tci, silent = false)
          mkWitnessWith(siTpt, TypeTree(tpe), Literal(const), i)

        case (tpe @ SingleType(p, v), tree) if !v.isParameter =>
          val tci = appliedType(tc, List(tpe))
          val i = c.inferImplicitValue(tci, silent = false)
          mkWitnessWith(siTpt, TypeTree(tpe), tree, i)

        case (tpe: TypeRef, Literal(const: Constant)) =>
          val tci = appliedType(tc, List(ConstantType(const)))
          val i = c.inferImplicitValue(tci, silent = false)
          mkWitnessWith(siTpt, TypeTree(ConstantType(const)), Literal(const), i)

        case _ =>
          c.abort(c.enclosingPosition, s"Expression ${t.tree} does not evaluate to a constant or a stable value")
      }
  }

  def convertInstanceImpl2[H, TC2[_ <: H, _], S <: H, T](t: c.Expr[T])
    (implicit tc2Tag: c.WeakTypeTag[TC2[_, _]], sTag: c.WeakTypeTag[S], tTag: c.WeakTypeTag[T]):
      c.Expr[WitnessWith.Lt[({ type λ[X] = TC2[S, X] })#λ, T]] = {
      val tc2 = tc2Tag.tpe.typeConstructor
      val s = sTag.tpe

      val pre = weakTypeOf[WitnessWith[({ type λ[X] = TC2[S, X] })#λ]]
      val pre2 = pre.map { _ match {
        case TypeRef(prefix, sym, args) if sym.isFreeType =>
          TypeRef(NoPrefix, tc2.typeSymbol, args)
        case tpe => tpe
      }}
      val tc = pre2.normalize

      (weakTypeOf[T], t.tree) match {
        case (tpe @ ConstantType(const: Constant), _) =>
          val tci = appliedType(tc2, List(s, tpe))
          val i = c.inferImplicitValue(tci, silent = false)
          mkWitnessWith(TypeTree(tc), TypeTree(tpe), Literal(const), i)

        case (tpe @ SingleType(p, v), tree) if !v.isParameter =>
          val tci = appliedType(tc2, List(s, tpe))
          val i = c.inferImplicitValue(tci, silent = false)
          mkWitnessWith(TypeTree(tc), TypeTree(tpe), tree, i)

        case (tpe: TypeRef, Literal(const: Constant)) =>
          val tci = appliedType(tc2, List(s, ConstantType(const)))
          val i = c.inferImplicitValue(tci, silent = false)
          mkWitnessWith(TypeTree(tc), TypeTree(ConstantType(const)), Literal(const), i)

        case _ =>
          c.abort(c.enclosingPosition, s"Expression ${t.tree} does not evaluate to a constant or a stable value")
      }
  }

  def mkOps[O](sTpt: TypTree, w: Tree) = {
    val opsTpt = Ident(typeOf[SingletonOps].typeSymbol)
    val T = TypeDef(Modifiers(), newTypeName("T"), List(), sTpt)
    val value = ValDef(Modifiers(), newTermName("witness"), TypeTree(), w)
    c.Expr[O] {
      mkImplClass(opsTpt, List(T, value), List())
    }
  }

  def mkSingletonOps[T](t: c.Expr[T]): c.Expr[SingletonOpsLt[T]] = {
    (weakTypeOf[T], t.tree) match {
      case (tpe @ ConstantType(const: Constant), _) =>
        val sTpt = TypeTree(tpe)
        mkOps(sTpt, mkWitness(sTpt, Literal(const)).tree)

      case (tpe @ SingleType(p, v), tree) if !v.isParameter =>
        val sTpt = TypeTree(tpe)
        mkOps(sTpt, mkWitness(sTpt, tree).tree)

      case (tpe: TypeRef, Literal(const: Constant)) =>
        val sTpt = TypeTree(ConstantType(const))
        mkOps(sTpt, mkWitness(sTpt, Literal(const)).tree)

      case (tpe @ TypeRef(pre, sym, args), tree) =>
        val sTpt = SingletonTypeTree(tree)
        mkOps(sTpt, mkWitness(sTpt, tree).tree)

      case (tpe, tree) =>
        c.abort(c.enclosingPosition, s"Expression ${t.tree} does not evaluate to a constant or a stable value")
    }
  }

  def constructor(prop: Boolean) =
    DefDef(
      Modifiers(),
      nme.CONSTRUCTOR,
      List(),
      List(
        if(prop)
          List(
            ValDef(Modifiers(PARAM), newTermName("i"), Ident(typeOf[Int].typeSymbol), EmptyTree)
          )
        else
          Nil
      ),
      TypeTree(),
      Block(
        List(
          Apply(
            Select(
              Super(This(tpnme.EMPTY), tpnme.EMPTY), nme.CONSTRUCTOR
            ),
            if(prop)
              List(Ident(newTermName("i")))
            else
              Nil
          )
        ),
        Literal(Constant(()))
      )
    )

  def mkImplClass(parent: Tree, defns: List[Tree], args: List[Tree]): Tree = {
    val name = newTypeName(c.fresh())

    val classDef =
      ClassDef(
        Modifiers(FINAL),
        name,
        List(),
        Template(
          List(parent),
          emptyValDef,
          constructor(args.size > 0) :: defns
        )
      )

    Block(
      List(classDef),
      Apply(Select(New(Ident(name)), nme.CONSTRUCTOR), args)
    )
  }
}

object SingletonTypeMacros {
  import syntax.SingletonOps
  type SingletonOpsLt[Lub] = SingletonOps { type T <: Lub }

  def inst(c0: Context) = new SingletonTypeMacros[c0.type] { val c: c0.type = c0 }

  def materializeImpl[T: c.WeakTypeTag](c: Context): c.Expr[Witness.Aux[T]] = inst(c).materializeImpl[T]

  def convertImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Witness.Lt[T]] = inst(c).convertImpl[T](t)

  def convertInstanceImpl1[TC[_], T](c: Context)(t: c.Expr[T])
    (implicit tcTag: c.WeakTypeTag[TC[_]], tTag: c.WeakTypeTag[T]):
      c.Expr[WitnessWith.Lt[TC, T]] = inst(c).convertInstanceImpl[TC, T](t)

  def convertInstanceImpl2[H, TC2[_ <: H, _], S <: H, T](c: Context)(t: c.Expr[T])
    (implicit tcTag: c.WeakTypeTag[TC2[_, _]], sTag: c.WeakTypeTag[S], tTag: c.WeakTypeTag[T]):
      c.Expr[WitnessWith.Lt[({ type λ[X] = TC2[S, X] })#λ, T]] = inst(c).convertInstanceImpl2[H, TC2, S, T](t)

  def mkSingletonOps[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[SingletonOpsLt[T]] = inst(c).mkSingletonOps[T](t)
}
