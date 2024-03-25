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
 * Source of class InlineArray
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.nio.ByteBuffer;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * InlineArray represents syntactic sugar for array initialization: '[1, 2, 3]' or
 * '[point x, y; point sin alpha, cos alpha]'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class InlineArray extends ExprWithPos
{


  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for temporary variables needed for
   * array initialization.
   */
  static private long _id_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The elements to be stored in the array
   */
  public final List<Expr> _elements;


  /**
   * The type of this array.
   */
  private AbstractType _type;


  /**
   * The code for initializing this array.
   */
  private Expr _code;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for Parser
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param elements the elements of this array
   */
  public InlineArray(SourcePosition pos, List<Expr> elements)
  {
    super(pos);
    this._elements = elements;
  }


  /**
   * Constructor for loading InlineArray from fum-file
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param elements the elements of this array
   *
   * @param code the code to initialize this array
   */
  public InlineArray(SourcePosition pos, List<Expr> elements, Expr code)
  {
    super(pos);
    this._elements = elements;
    this._code = code;
    this._type = code.type();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForInferencing()
  {
    if (_type == null && !_elements.isEmpty())
      {
        var t = Expr.union(_elements);
        if (t == Types.t_ERROR)
          {
            new IncompatibleResultsOnBranches(pos(),
                                              "Incompatible types in array elements",
                                              _elements.iterator());
            _type = Types.t_ERROR;
          }
        else
          {
            _type = t == null
              ? null
              : ResolvedNormalType.create(new List<>(t),
                                          new List<>(t),
                                          null,
                                          Types.resolved.f_array);
          }
      }
    return _type;
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
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, AbstractFeature outer, AbstractType t)
  {
    if (_type == null)
      {
        // if expected type is choice, examine if there is exactly one
        // array in choice generics, if so use this for further type propagation.
        t = t.findInChoice(cg -> !cg.isGenericArgument() && cg.featureOfType() == Types.resolved.f_array);

        var elementType = elementType(t);
        if (elementType != Types.t_ERROR)
          {
            for (var e : _elements)
              {
                var e2 = e.propagateExpectedType(res, outer, elementType);
                if (CHECKS) check
                  (e == e2);
              }
            _type = Types.resolved.f_array.resultTypeIfPresent(res, new List<>(elementType));
          }
      }
    return this;
  }


  /**
   * For a given array type, return the type t of its elements.
   *
   * @param t any type
   *
   * @return if t is Array<T>; the element type T. Types.t_ERROR otherwise.
   */
  private AbstractType elementType(AbstractType t)
  {
    if (PRECONDITIONS) require
      (t != null);

    // NYI see issue: #1817
    if (Types.resolved.f_array.inheritsFrom(t.featureOfType()) &&
        t.generics().size() == 1)
      {
        return t.generics().get(0);
      }
    else
      {
        return Types.t_ERROR;
      }
  }


  /**
   * For this array's type(), return the element type
   *
   * @return if type() is Array<T>; the element type T. Types.t_ERROR otherwise.
   */
  public AbstractType elementType()
  {
    return elementType(type());
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    var li = _elements.listIterator();
    while (li.hasNext())
      {
        var e = li.next();
        li.set(e.visit(v, outer));
      }
    return v.action(this, outer);
  }


  /**
   * visit all the expressions within this InlineArray.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    super.visitExpressions(v);
    for (var e : _elements)
      {
        e.visitExpressions(v);
      }
    if (_code != null)
      {
        _code.visitExpressions(v);
      }
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(AbstractFeature outer)
  {
    var li = _elements.listIterator();
    while (li.hasNext())
      {
        var e = li.next();
        li.set(e.box(elementType()));
      }
  }


  /**
   * check the types in this InlineArray
   */
  public void checkTypes()
  {
    if (PRECONDITIONS) require
      (Errors.any() || _type != null);

    var elementType = elementType();

    if (CHECKS) check
      (Errors.any() || elementType != Types.t_ERROR);

    for (var e : _elements)
      {
        if (!elementType.isDirectlyAssignableFrom(e.type()))
          {
            AstErrors.incompatibleTypeInArrayInitialization(e.pos(), _type, elementType, e);
          }
      }
  }


  /**
   * the code generated by syntaxsugar2 to instantiate the array.
   *
   * used if this array is not found to be compile-time constant.
   */
  public Expr code()
  {
    return _code;
  }


  /**
   * This expression as a compile time constant.
   */
  @Override
  public AbstractConstant asCompileTimeConstant()
  {
    var result = new AbstractConstant() {

      @Override
      public SourcePosition pos()
      {
        throw new UnsupportedOperationException("Unimplemented method 'pos'");
      }

      /**
       * first four bytes are the elementCount
       * remaining bytes is the serialized data of the elements
       */
      @Override
      public byte[] data()
      {
        var result = ByteBuffer.allocate(4 + InlineArray.this
          ._elements
          .stream()
          .mapToInt(e -> e.asCompileTimeConstant().data().length)
          .sum());

        result.putInt(_elements.size());

        InlineArray.this
          ._elements
          .stream()
          .forEach(e -> result.put(e.asCompileTimeConstant().data()));

        return result.array();
      }

      @Override
      public Expr visit(FeatureVisitor v, AbstractFeature outer)
      {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
      }

      @Override
      public AbstractType type()
      {
        var r = InlineArray.this.type();

        if (POSTCONDITIONS) ensure
          (!r.dependsOnGenerics(),
           !r.containsThisType());

        return r;
      }

      @Override
      public Expr origin() { return InlineArray.this; }

    };

    return result;
  }


  /**
   * Resolve syntactic sugar, e.g., by replacing anonymous inner functions by
   * declaration of corresponding inner features. Add (f,<>) to the list of
   * features to be searched for runtime types to be layouted.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this expression.
   */
  public Expr resolveSyntacticSugar2(Resolution res, AbstractFeature outer)
  {
    var et = elementType();
    var eT           = new List<AbstractType>(et);
    var args         = new List<Actual>(new Actual(et),
                                        new Actual(new NumLiteral(_elements.size())));
    var fuzion       = new Call(SourcePosition.builtIn, null, "fuzion"                     ).resolveTypes(res, outer);
    var sys          = new Call(SourcePosition.builtIn, fuzion, "sys"                      ).resolveTypes(res, outer);
    var sysArrayCall = new Call(SourcePosition.builtIn, sys , "internal_array_init", args  ).resolveTypes(res, outer);
    var fuzionT      = new ParsedType(SourcePosition.builtIn, "fuzion", UnresolvedType.NONE, null);
    var sysT         = new ParsedType(SourcePosition.builtIn, "sys"   , UnresolvedType.NONE, fuzionT);
    var sysArrayT    = new ParsedType(SourcePosition.builtIn, "internal_array", eT, sysT);
    var sysArrayName = FuzionConstants.INLINE_SYS_ARRAY_PREFIX + (_id_++);
    var sysArrayVar  = new Feature(SourcePosition.builtIn, Visi.PRIV, sysArrayT, sysArrayName, Impl.FIELD);
    res._module.findDeclarations(sysArrayVar, outer);
    res.resolveDeclarations(sysArrayVar);
    res.resolveTypes();
    var sysArrayAssign = new Assign(res, SourcePosition.builtIn, sysArrayVar, sysArrayCall, outer);
    var exprs = new List<Expr>(sysArrayAssign);
    for (var i = 0; i < _elements.size(); i++)
      {
        var e = _elements.get(i);
        var setArgs         = new List<Actual>(new Actual(new NumLiteral(i)),
                                                new Actual(e));
        var readSysArrayVar = new Call(SourcePosition.builtIn, null           , sysArrayName     ).resolveTypes(res, outer);
        var setElement      = new Call(SourcePosition.builtIn, readSysArrayVar,
                                        FuzionConstants.FEATURE_NAME_INDEX_ASSIGN,
                                        setArgs                                    ).resolveTypes(res, outer);
        exprs.add(setElement);
      }
    var readSysArrayVar = new Call(SourcePosition.builtIn, null, sysArrayName                      ).resolveTypes(res, outer);
    var unit1           = new Call(SourcePosition.builtIn, null, "unit"                            ).resolveTypes(res, outer);
    var unit2           = new Call(SourcePosition.builtIn, null, "unit"                            ).resolveTypes(res, outer);
    var unit3           = new Call(SourcePosition.builtIn, null, "unit"                            ).resolveTypes(res, outer);
    var sysArrArgs      = new List<Actual>(new Actual(et),
                                            new Actual(readSysArrayVar),
                                            new Actual(unit1),
                                            new Actual(unit2),
                                            new Actual(unit3));
    var arrayCall       = new Call(SourcePosition.builtIn, null, "array"     , sysArrArgs).resolveTypes(res, outer);
    exprs.add(arrayCall);

    // we do not "replace" this inline array by instantiation code
    // instead we save away the code and decide in fuir
    // if we have to use it or can turn this array into a
    // compile-time constant.
    _code = new Block(exprs)
    {
      @Override
      public SourcePosition pos()
      {
        return InlineArray.this.pos();
      }
    };
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _elements.toString("[", "; ", "]");
  }

}

/* end of file */
