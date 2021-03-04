/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa GmbH, Berlin
 *
 * Source of class Function
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Function <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Function extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  static final List<Feature> NO_FEATURES = new List<Feature>();
  static final List<Call> NO_CALLS = new List<Call>();


  /*-------------------------  static variables -------------------------*/

  /**
   * quick-and-dirty way to make unique names for function wrappers
   */
  static private long id = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * For function declaration of the kind "fun a.b.f", this is the call to f
   *
   * For a function that declares a new anonymous feature, this will be the
   * resulting call that creates an instance of a subclass for Function/Routine
   * whose call() function implements the function.
   */
  Call call_;


  /**
   * For function declaration of the kind
   *
   *  fun int (Object o, int i) { result =* o.hashCode + i; }
   *
   * this is the declared feature
   *
   *  int (Object o, int i) { result =* o.hashCode + i; }
   *
   */
  Feature feature_;


  Type type_;


  /**
   * For a function that declares a new anonymous feature, these are the generic
   * arguments to Function/Routine the anonymous feature inherits from. This
   * will be used put the correct return type in case of a fun declaration using
   * => that requires type inference.
   */
  Call inheritsCall_;


  /**
   * For a function defined inline, this is the wrapper that calls the inline
   * feature.
   */
  final Feature _wrapper;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor defining a function by referring to a feature, e.g.,
   *
   *   x(fun a.b.f);
   *
   * where f is defined in a.b as
   *
   *   f(o Object, i int) int { result = o.hashCode + i; };
   *
   * then the "fun f" is syntactic surgar for
   *
   *  a.b._anonymous<#>_f_: Function<int,Object,int>
   *   {
   *     redefine call(o Object, i int) int
   *     {
   *       result = f(o,i);
   *     };
   *   }
   *  x(a.b._anonymous<#>_f_());
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param c the call after the fun keyword
   */
  public Function(SourcePosition pos,
                  Call c)
  {
    super(pos);

    if (PRECONDITIONS) require
      (c._actuals.size() == 0);

    this.call_ = c;
    c.forFun = true;
    this._wrapper = null;
  }


  /**
   * Constructor defining a function inline, e.g.,
   *
   *  x(fun (o Object, i int) int { result = o.hashCode + i; });
   *
   * which is equivalent to
   *
   *  _anonymous<#>_f_: Function<int,Object,int>
   *   {
   *     redefine int call(Object o, int i)
   *     {
   *       result = o.hashCode + i;
   *     };
   *   }
   *  x(_anonymous<#>_f_());
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the return type or null for a routine
   *
   * @param a the arguments list
   *
   * @param i the inheritance clause
   *
   * @param c the contract
   *
   * @param b the code
   */
  public Function(SourcePosition pos,
                  ReturnType r,
                  List<Feature> a,
                  List<Call> i,
                  Contract c,
                  Block b)
  {
    this(pos, r, a, i, c, new Impl(b.pos, b, Impl.Kind.Routine));
  }


  /**
   * Constructor defining a function inline, e.g.,
   *
   *  x(fun (o Object, i int) => o.hashCode + i);
   *
   * which is equivalent to
   *
   *  _anonymous<#>_f_: Function<int,Object,int>
   *   {
   *     redefine call(o Object, i int) => o.hashCode + i;
   *   }
   *  x(_anonymous<#>_f_());
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the return type or null for a routine
   *
   * @param a the arguments list
   *
   * @param i the inheritance clause
   *
   * @param c the contract
   *
   * @param b the code
   */
  public Function(SourcePosition pos,
                  ReturnType r,
                  List<Feature> a,
                  List<Call> i,
                  Contract c,
                  Expr e)
  {
    // NYI: This currently does not work to define a function without a result as in
    //  fun () => {}
    this(pos, r, a, i, c, new Impl(e.pos, e, Impl.Kind.RoutineDef));
  }


  /**
   * Constructor defining a function inline, e.g.,
   *
   *  x(fun int (Object o, int i) { result = o.hashCode + i; });
   *
   * which is equivalent to
   *
   *  _anonymous<#>_f_: Function<int,Object,int>
   *   {
   *     redefine int call(Object o, int i)
   *     {
   *       result = o.hashCode + i;
   *     };
   *   }
   *  x(_anonymous<#>_f_());
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the return type or null for a routine
   *
   * @param a the arguments list
   *
   * @param i the inheritance clause
   *
   * @param c the contract
   *
   * @param p the code
   */
  private Function(SourcePosition pos,
                   ReturnType r,
                   List<Feature> a,
                   List<Call> i,
                   Contract c,
                   Impl p)
  {
    super(pos);

    /* We have an expression of the form
     *
     *   fun (o Object, i int) int { result = o.hashCode + i; });
     *
     * so we replace it by
     *
     * --Fun<id>-- : Function<R,A1,A2,...>
     * {
     *   public redefine R call(A1 a1, A2 a2, ...)
     *   {
     *     result = o.hashCode + i;
     *   }
     * }
     * [..]
     *         --Fun<id>--()
     * [..]
     */

    Feature f = new Feature(pos, r, new List<String>("call"), a, i, c, p);
    this.feature_ = f;
    f.modifiers |= Consts.MODIFIER_REDEFINE;

    List<Type> generics = new List<Type>();
    generics.add(f.hasResult() ? Types.t_UNDEFINED : new Type("unit"));
    for (int j = 0; j < a.size(); j++)
      {
        generics.add(Types.t_UNDEFINED);
      }
    // inherits clause for wrapper feature: Function<R,A,B,C,...>
    inheritsCall_ = new Call(pos,
                             f.hasResult() ? "Function" : "Routine",
                             generics,
                             Expr.NO_EXPRS);
    List<Stmnt> statements = new List<Stmnt>(f);
    String wrapperName = "#fun"+ id++;
    _wrapper = new Feature(pos,
                           Consts.VISIBILITY_INVISIBLE,
                           Consts.MODIFIER_FINAL,
                           RefType.INSTANCE,
                           new List<String>(wrapperName),
                           FormalGenerics.NONE,
                           NO_FEATURES,
                           new List<>(inheritsCall_),
                           new Contract(null,null,null),
                           new Impl(pos, new Block(pos, statements), Impl.Kind.Routine));
    call_ = new Call(pos, _wrapper);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Load all features that are called by this expression.
   *
   * @param res the resolution instance.
   *
   * @param thiz the class that contains this expression.
   */
  void loadCalledFeature(Resolution res, Feature thiz)
  {
    this.call_.loadCalledFeature(res, thiz);
    if (this.feature_ == null)
      {
        Feature f = this.call_.calledFeature();
        check
          (Errors.count() > 0 || f != null);

        if (f != null)
          {
            if (f.returnType != RefType.INSTANCE && f.returnType.isConstructorType())
              {
                System.err.println("NYI: fun for returnType >>"+f.returnType+"<< not allowed");
                System.exit(1);
              }
          }
      }
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  public Expr visit(FeatureVisitor v, Feature outer)
  {
    var e = this.call_.visit(v, outer);
    check
      (e == this.call_); // NYI: This will fail e.g. if call_ is a call to bool.infix &&, need to handle explicitly
    this.call_ = (Call) e;
    return v.action(this, outer);
  }


  /**
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param outer the root feature that contains this statement.
   */
  public void findGenerics(FeatureVisitor v, Feature outer)
  {
    if (this.feature_ != null)
      { /* NYI: Neeed? The following comment seems wrong: */
        // directly process generics in feature_'s arguments and return type,
        // while visit() skips the feature_.
        Feature f = this.feature_;
        for (Feature a : f.arguments)
          {
            a.returnType.visit(v, outer);
          }
        f.returnType.visit(v, outer);
      }
  }


  private Feature functionOrRoutine()
  {
    Feature f = this.feature_ == null ? this.call_.calledFeature()
                                      : this.feature_;
    check
      (Errors.count() > 0 || f != null);

    if (f != null)
      {
        f = Types.resolved.f_function;
      }
    return f;
  }

  /**
   * Produce the list of actual generic arguments to be passed to
   * functionOrRoutine.
   */
  List<Type> generics(Resolution res)
  {
    List<Type> generics = new List<Type>();

    Feature f = this.feature_ == null ? this.call_.calledFeature()
                                      : this.feature_;
    check
      (Errors.count() > 0 || f != null);

    if (f != null)
      {
        generics.add(f.hasResult()
                     ? f.resultTypeForTypeInference(pos, res, Type.NONE)
                     : new Type("unit"));
        for (Feature a : f.arguments)
          {
            a.resolveTypes(res);
            generics.add(a.resultType());
          }
      }
    return generics;
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public void resolveTypes(Resolution res, Feature outer)
  {
    if (this.feature_ == null)
      {
        Feature fr = functionOrRoutine();
        List<Type> generics = generics(res);
        FormalGenerics.resolve(generics, outer);
        type_ = fr != null ? new Type(pos, fr._featureName.baseName(), generics, null, fr, Type.RefOrVal.LikeUnderlyingFeature).resolve(outer)
                           : Types.t_ERROR;
      }
    else
      {
        inheritsCall_.generics = generics(res);
        Call inheritsCall2 = inheritsCall_.resolveTypes(res, outer);
        // Call.resolveType returns something differnt than this only for an
        // immediate function call, which is never the case in an inherits
        // clause.
        check
          (inheritsCall_ == inheritsCall2);
        type_ = call_.type();
      }
  }


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    return type_;
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Expr resolveSyntacticSugar2(Resolution res, Feature outer)
  {
    Expr result = this;
    if (Errors.count() == 0)  // avoid null pointer hdlg in case calledFeature not found etc.
      {
        if (this.feature_ == null)
          { /* We have an expression of the form
             *
             *   fun a.b.f
             *
             * so we replace it by
             *
             * --Fun<id>-- : Function<R,A1,A2,...>
             * {
             *   public redefine R call(A1 a1, A2 a2, ...)
             *   {
             *     result = a.b.f(a1, a2, ...);
             *   }
             * }
             * [..]
             *         --Fun<id>--()
             * [..]
             */
            Call call = this.call_;
            call.forFun = false;  // the call is no longer for fun (i.e., ignored in Call.resolveTypes)
            Feature calledFeature = call.calledFeature();
            /* NYI: "fun a.b" special cases: check what can go wrong with
             * calledTarget and flag an error. Possible errors aor special case
             * are
             *
             *  - calling an abstract feature
             *  - calling a redefined feature
             *  - calling a single feature
             *  - calling a feature in a different module
             */
            List<Expr> actual_args = new List<Expr>();
            List<Feature> formal_args = new List<Feature>();
            int argnum = 1;
            for (Feature f : calledFeature.arguments)
              {
                String name = "a"+argnum;
                actual_args.add(new Call(pos, null, name, Call.NO_GENERICS, Expr.NO_EXPRS));
                formal_args.add(new Feature(pos, Consts.VISIBILITY_LOCAL, 0, f.resultType(), name, new Contract(null,null,null)));
                argnum++;
              }
            Call callWithArgs = new Call(pos, null, call.name, Call.NO_GENERICS, actual_args);
            Feature fcall = new Feature(pos, Consts.VISIBILITY_PUBLIC,
                                        Consts.MODIFIER_REDEFINE,
                                        NoType.INSTANCE, // calledFeature.returnType,
                                        new List<String>("call"),
                                        FormalGenerics.NONE,
                                        formal_args,
                                        NO_CALLS,
                                        new Contract(null,null,null),
                                        new Impl(pos, callWithArgs, Impl.Kind.RoutineDef));

            // inherits clause for wrapper feature: Function<R,A,B,C,...>
            Feature fr = functionOrRoutine();
            List<Call> inherits = new List<>(new Call(pos, fr._featureName.baseName(), type_._generics, Expr.NO_EXPRS));

            List<Stmnt> statements = new List<Stmnt>(fcall);

            String wrapperName = "#fun"+ id++;
            Feature function = new Feature(pos,
                                           Consts.VISIBILITY_INVISIBLE,
                                           Consts.MODIFIER_FINAL,
                                           RefType.INSTANCE,
                                           new List<String>(wrapperName),
                                           FormalGenerics.NONE,
                                           NO_FEATURES,
                                           inherits,
                                           new Contract(null,null,null),
                                           new Impl(pos, new Block(pos, statements), Impl.Kind.Routine));
            function.findDeclarations(call.target.type().featureOfType());
            result = new Call(pos,
                              call.target,
                              function)
              .resolveTypes(res, outer);
          }
        else
          {
            result = call_;
          }
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return (feature_ == null)
      ? "function CALL: " + call_
      : "function FEATURE: " + feature_;
  }

}

/* end of file */
