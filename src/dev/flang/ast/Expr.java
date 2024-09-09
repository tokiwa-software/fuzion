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
 * Tokiwa Software GmbH, Germany
 *
 * Source of class Expr
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.stream.Collectors;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;
import dev.flang.util.StringHelpers;


/**
 * Expr <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Expr extends HasGlobalIndex implements HasSourcePosition
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Empty Expr list to be used for empty actual arguments lists.
   */
  public static final List<Expr> NO_EXPRS = new List<Expr>();


  /**
   * Dummy Expr value. Used e.g. in 'Actual' to represent non-existing value version
   * of the actual.
   */
  public static Call NO_VALUE;


  /**
   * Dummy Expr value. Used to represent error values.
   */
  public static final Expr ERROR_VALUE = new Expr()
    {
      public SourcePosition pos()
      {
        return SourcePosition.builtIn;
      }
      public void setSourceRange(SourceRange r)
      { // do not change the source position if there was an error.
      }
      public Expr visit(FeatureVisitor v, AbstractFeature outer)
      {
        return this;
      }
      @Override
      AbstractType typeForInferencing()
      {
        return Types.t_ERROR;
      }
      public String toString()
      {
        return Errors.ERROR_STRING;
      }
    };


  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for expression result vars
   */
  static private long _id_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * Source code position range of this Expression. null if not known.
   */
  protected SourceRange _range;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for an Expression.
   */
  public Expr()
  {
  }

  /*-----------------------------  methods  -----------------------------*/


  /**
   * Source range of this Expr.  Note that this might be longer than the Expr
   * itself, e.g., in a call
   *
   *    f (x.q y)
   *
   * The argument to f is the call `x.q y` whose position is
   *
   *    f (x.q y)
   * --------^
   *
   * but the source range is
   *
   *    f (x.q y)
   * -----^^^^^^^
   *
   */
  public SourcePosition sourceRange()
  {
    return _range == null ? pos() : _range;
  }


  public void setSourceRange(SourceRange r)
  {
    if (PRECONDITIONS) require
      (/* make sure we do not accidentally set this repeatedly, as for special
        * Exprs like ERROR_VALUE, but we might extend it as in adding
        * parentheses around the Expr:
        */
       _range == null ||
       _range.bytePos()    >= r.bytePos() &&
       _range.byteEndPos() <= r.byteEndPos());

    _range = r;
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known
   * yet. t_UNDEFINED in case Expr depends on the inferred result type of a
   * feature that is not available yet (or never will due to circular
   * inference).
   */
  public AbstractType type()
  {
    var result = typeForInferencing();
    if (result == null)
      {
        result = Types.t_ERROR;
        // NYI: This should try to find the reason for the missing type and
        // print the problem
        AstErrors.failedToInferType(this);
      }
    return result;
  }


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForInferencing()
  {
    return null;
  }


  /**
   * typeForUnion returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForUnion()
  {
    return typeForInferencing();
  }


  /**
   * Get the result type of a union of all results of exprs.
   *
   * @param exprs the expression to unionize
   *
   * @return the union of exprs result type, defaulting to Types.resolved.t_void if
   * no expression can be inferred yet.
   */
  public static AbstractType union(List<Expr> exprs, Context context)
  {
    AbstractType t = Types.resolved.t_void;

    // First pass:
    // Union of the types of the expressions
    // that are sure about their types.
    var foundType = false;
    for (var e : exprs)
      {
        var et = e.typeForUnion();
        if (et != null)
          {
            foundType = true;
            t = t.union(et, context);
          }
      }

    if (foundType)
      {
        // Propagate the found type to all expression
        for (var e : exprs)
          {
            e.propagateExpectedType(t);
          }
      }

    // Second pass:
    // Union of the types of the expressions
    AbstractType result = Types.resolved.t_void;
    foundType = false;
    for (var e : exprs)
      {
        var et = e.typeForInferencing();
        if (et != null)
          {
            foundType = true;
            result = result.union(et, context);
          }
      }

    return foundType
      ? result
      : null;
  }


  /**
   * During type inference: Inform this expression that it is
   * expected to result in the given type.
   *
   * @param t the expected type.
   */
  protected void propagateExpectedType(AbstractType t)
  {

  }


  /**
   * The source code position of this expression that produces the result value
   * of this Expression. This is usually equal to this Expression's position,
   * unless we have a block of the form
   *
   *   {
   *     x;
   *     y
   *   }
   *
   * where this is the position of y.
   */
  SourcePosition posOfLast()
  {
    return pos();
  }


  /**
   * Load all features that are called by this expression.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   */
  void loadCalledFeature(Resolution res, Context context)
  {
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  public abstract Expr visit(FeatureVisitor v, AbstractFeature outer);


  /**
   * visit all the expressions within this Expr.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    v.action(this);
  }


  /**
   * Convert this Expression into an assignment to the given field.  In case
   * this is a expression with several branches such as an "if" or a "match"
   * expression, add corresponding assignments in each branch and convert this
   * into a expression that does not produce a value.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param r the field this should be assigned to.
   *
   * @return the Expr this Expr is to be replaced with, typically an Assign
   * that performs the assignment to r.
   */
  Expr assignToField(Resolution res, Context context, Feature r)
  {
    return new Assign(res, pos(), r, this, context);
  }


  /**
   * A lazy value v (one of type `Lazy T`) will automatically be replaced by
   * `v.call` during type resolution, such that it behaves as if it was of type
   * `T`.
   *
   * However, when `v` is passed to a value of type `Lazy T`, it does not make
   * sense to wrap this call into `Lazy` again. Instead. we would like to pass
   * the lazy value `v` directly.  So this method gives the original value for a
   * lazy value `v` t was replaced by `v.call`.
   *
   * @return `this` in case this was not replaced by a `call` to a `Lazy` value,
   * the original lazy value if it was.
   */
  Expr originalLazyValue()
  {
    return this;
  }


  /**
   * Check that this Expr does not contain any inner declarations of
   * features. Produce an error otherwise.
   *
   * An expression used as a lazy value or a partially applied call must not
   * contain any inner declarations.  There is is no fundamental problem, it
   * just requires that the front end would not add the feature declarations
   * found in this expression to the outer feature eagerly, but only after
   * processing of lazy values and partial application was done.
   *
   * @param what the reason why we are checking this, "a lazy value" or "a
   * partially applied function call".
   *
   * @param outer the outer feature, currently unused.
   *
   * @return true iff no declarations were found and, consequently, no error was
   * produced.
   */
  boolean mustNotContainDeclarations(String what, AbstractFeature outer)
  {
    var result = true;
    var declarations = new List<Feature>();
    visit(new FeatureVisitor()
      {
        public Expr action (Feature f, AbstractFeature outer)
        {
          declarations.add(f);
          return f;
        }
      },
      outer);

    if (!declarations.isEmpty())
      {
        /*
         * NYI: Instead of producing an error here, we could instead remove what
         * was done during SourceModule.findDeclarations() performed in this
         * expression, or, alternatively, create a new parse tree for this
         * expression and use that instead.
         *
         * Examples that cause this problem are
         *
         *     l(t Lazy i32) is
         *     _ := l ({
         *               x is
         *               y => 4711
         *               c := 0815
         *               c+y
         *             })
         *
         * or using implicit declarations created for a loop:
         *
         *     l(t Lazy l) is
         *       n => t
         *
         *     f l is l (do)
         *     _ := f.n
         */
        AstErrors.declarationsInLazy(what, this, declarations);
        result = false;
      }
    return result;
  }


  /**
   * After propagateExpectedType: if type inference up until now has figured
   * out that a Lazy feature is expected, but the current expression is not
   * a Lazy feature, then wrap this expression in a Lazy feature.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the type this expression is assigned to.
   */
  public Expr wrapInLazy(Resolution res, Context context, AbstractType t)
  {
    var result = this;

    result = t.isLazyType() ? result.originalLazyValue() : result;

    if (t.isLazyType() && !result.type().isLazyType())
      {
        if (mustNotContainDeclarations("a lazy value", context.outerFeature()))
          {
            var fn = new Function(pos(),
                                  new List<>(),
                                  result);

            result = fn.propagateExpectedType(res, context, t);
            fn.resolveTypes(res, context);
            fn.updateTarget(res);
          }
        else
          {
            result = ERROR_VALUE;
          }
      }
    return result;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Context context, AbstractType t)
  {
    return this;
  }


  /**
   * Try to perform partial application such that this expression matches
   * `expectedType`.  Note that this may happen twice:
   *
   * 1. during RESOLVING_DECLARATIONS phase of outer when resolving arguments to
   *    a call such as `l.map +1`. In this case, expectedType may be a function
   *    type `Function R A` with generic arguments not yet replaced by actual
   *    arguments, in particular the result type `R` is unknown since it is the
   *    result type of this expression.
   *
   * 2. during TYPES_INFERENCING phase when the target variable's type is fully
   *    resolved and this gets propagated to this expression.
   *
   * Note that this does not perform resolveTypes on the results since that
   * would be too early during 1. but it is required in 2.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param expectedType the expected type.
   */
  Expr propagateExpectedTypeForPartial(Resolution res, Context context, AbstractType expectedType)
  {
    return this;
  }


  /**
   * Return this expression as an (unresolved) type.
   * This is null by default except for calls/this-expressions
   * that can be used as the left hand side in a dot-type-call.
   *
   * The type is returned as produced by the parser and needs
   * to be resolved with the context it is used in to be of
   * any use.
   */
  public UnresolvedType asParsedType()
  {
    return null;
  }


  /**
   * Return this expression as a simple name.  This is null by default except
   * for calls that without an explicit target and without any actual arguments
   * or select clause.
   */
  public ParsedName asParsedName()
  {
    return null;
  }


  /**
   * Return this expression as a simple qualifier. This is null by default
   * except for calls without explicit target or one whose asQualifier() is not
   * null and without any actual arguments or select clause.
   */
  public List<ParsedName> asQualifier()
  {
    return null;
  }


  protected Expr addFieldForResult(Resolution res, Context context, AbstractType t)
  {
    var result = this;
    if (!t.isVoid())
      {
        var pos = pos();
        Feature r = new Feature(res,
                                pos,
                                Visi.PRIV,
                                t,
                                FuzionConstants.EXPRESSION_RESULT_PREFIX + (_id_++),
                                context.outerFeature());
        r.scheduleForResolution(res);
        res.resolveTypes();
        result = new Block(new List<>(assignToField(res, context, r),
                                      new Call(pos, new Current(pos, context.outerFeature()), r).resolveTypes(res, context)));
      }
    return result;
  }


  /**
   * Does this expression consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    return false;
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  public boolean isCallToOuterRef()
  {
    return false;
  }


  /**
   * Check if this value might need boxing, unboxing or tagging and wrap this
   * into Box()/Tag() if this is the case.
   *
   * @param frmlT the formal type this value is assigned to
   *
   * @param context the source code context where this Expr is used
   *
   * @return this or an instance of Box wrapping this.
   */
  Expr box(AbstractType frmlT, Context context)
  {
    if (PRECONDITIONS) require
      (frmlT != null);

    var result = this;
    var t = type();

    if (!t.isVoid() && (frmlT.isAssignableFrom(t, context) || frmlT.isAssignableFrom(t.asRef(), context)))
      {
        if (needsBoxing(frmlT, context))
          {
            result = new Box(result, frmlT);
            t = result.type();
          }
        if (frmlT.isChoice() && frmlT.isAssignableFrom(t, context))
          {
            result = tag(frmlT, result, context);
            if (CHECKS) check
              (!result.needsBoxing(frmlT, context));
          }
      }

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0
        || t.isVoid()
        || frmlT.isGenericArgument()
        || frmlT.isThisType()
        || !result.needsBoxing(frmlT, context)
        || !(frmlT.isAssignableFrom(t, context) || frmlT.isAssignableFrom(t.asRef(), context)));

    return result;
  }


  /**
   * handle tagging when assigning value to choice frmlT
   * @param frmlT
   * @param value
   * @return
   */
  private Expr tag(AbstractType frmlT, Expr value, Context context)
  {
    if(PRECONDITIONS) require
      (frmlT.isChoice() || frmlT == Types.t_ERROR);

    // Case 1: types are equal, no tagging necessary
    if (frmlT.compareTo(value.type()) == 0)
      {
        return value;
      }
    // Case 2.1: ambiguous assignment via subtype
    //
    // example:
    //
    //  A ref is
    //  B ref is
    //  C ref : B, A is
    //  t choice A B := C
    //
    else if (frmlT
             .choiceGenerics(context)
              .stream()
             .filter(cg -> cg.isDirectlyAssignableFrom(value.type(), context))
              .count() > 1)
      {
        AstErrors.ambiguousAssignmentToChoice(frmlT, value);
        return Expr.ERROR_VALUE;
      }
    // Case 2.2: no nested tagging necessary:
    // there is a choice generic in this choice
    // that this value is "directly" assignable to
    else if (frmlT
              .choiceGenerics(context)
              .stream()
             .anyMatch(cg -> cg.isDirectlyAssignableFrom(value.type(), context)))
      {
        return new Tag(value, frmlT, context);
      }
    // Case 3: nested tagging necessary
    // value is only assignable to choice element
    // that itself is a choice
    else
      {
        // we assign to the choice generic
        // that expr is assignable to
        var cgs = frmlT
          .choiceGenerics(context)
          .stream()
          .filter(cg -> cg.isChoice() && cg.isAssignableFrom(value.type(), context))
          .collect(Collectors.toList());

        if (cgs.size() > 1)
          {
            AstErrors.ambiguousAssignmentToChoice(frmlT, value);
          }

        if (CHECKS) check
          (Errors.any() || cgs.size() == 1);

        return cgs.size() == 1 ? tag(frmlT, tag(cgs.get(0), value, context), context)
                               : Expr.ERROR_VALUE;
      }
  }


  /**
   * Is boxing needed when we assign to frmlT since frmlT is generic (so it
   * could be a ref) or frmlT is this type and the underlying feature is by
   * default a ref?
   *
   * @param frmlT the formal type we are assigning to.
   */
  boolean needsBoxingForGenericOrThis(AbstractType frmlT)
  {
    return
      frmlT.isGenericArgument() || frmlT.isThisType();
  }


  /**
   * Is boxing needed when we assign to frmlT?
   * @param frmlT the formal type we are assigning to.
   */
  private boolean needsBoxing(AbstractType frmlT, Context context)
  {
    var t = type();
    if (needsBoxingForGenericOrThis(frmlT))
      {
        return true;
      }
    else if (t.isRef() && !isCallToOuterRef())
      {
        return false;
      }
    else if (frmlT.isRef())
      {
        return true;
      }
    else
      {
        return frmlT.isChoice() &&
          !frmlT.isAssignableFrom(t, context) &&
          frmlT.isAssignableFrom(t.asRef(), context);
      }
  }


  /**
   * Do automatic unwrapping of features inheriting `unwrap`
   * if the expected type fits the unwrapped type.
   *
   * @param res the resolution instance
   *
   * @param context the source code context where this Expr is used
   *
   * @param expectedType the expected type
   *
   * @return the unwrapped expression
   */
  public Expr unwrap(Resolution res, Context context, AbstractType expectedType)
  {
    var t = type();
    return this != ERROR_VALUE && t != Types.t_ERROR
      && !expectedType.isAssignableFrom(t, context)
      && expectedType.compareTo(Types.resolved.t_Any) != 0
      && !t.isGenericArgument()
      && t.feature()
          .inherits()
          .stream()
          .anyMatch(c ->
            c.calledFeature().equals(Types.resolved.f_auto_unwrap)
            && !c.actualTypeParameters().isEmpty()
                    && expectedType.isAssignableFrom(c.actualTypeParameters().get(0).applyTypePars(t), context))
      ? new ParsedCall(this, new ParsedName(pos(), "unwrap")).resolveTypes(res, context)
      : this;
  }


  /**
   * This expression as a compile time constant.
   */
  public Constant asCompileTimeConstant()
  {
    throw new Error("This expr is not a compile time constant: " + this.getClass());
  }


  /**
   * Some Expressions do not produce a result, e.g., a Block that is empty or
   * whose last expression is not an expression that produces a result.
   */
  public boolean producesResult()
  {
    return true;
  }


  /**
   * Reset static fields
   */
  public static void reset()
  {
    NO_VALUE = new Call(SourcePosition.builtIn, FuzionConstants.NO_VALUE_STRING)
    {
      { _type = Types.t_ERROR; }
      @Override
      Expr box(AbstractType frmlT, Context context)
      {
        return this;
      }
    };
  }


  /**
   * wrap the result of toString in parentheses if necessary
   */
  public String toStringWrapped()
  {
    return StringHelpers.wrapInParentheses(toString());
  }


}

/* end of file */
