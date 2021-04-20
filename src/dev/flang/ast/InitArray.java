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
 * Source of class InitArray
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * InitArray represents syntactic sugar for array initialization: '[1, 2, 3]' or
 * '[point x, y; point sin alpha, cos alpha]'.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class InitArray extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The elements to be stored in the array
   */
  List<Expr> _elements;


  /**
   * The type of this array.
   */
  private Type type_;


  /**
   * Clazz index for array clazz
   */
  public int _arrayClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param elements the elements of this array
   */
  public InitArray(SourcePosition pos, List<Expr> elements)
  {
    super(pos);
    this._elements = elements;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    if (type_ == null)
      {
        var t = Types.resolved.t_void;
        for (var e : _elements)
          {
            t = t.union(e.type());
          }
        type_ = Types.intern(new Type(pos(),
                                      "array",
                                      new List<>(t),
                                      null,
                                      Types.resolved.f_Array,
                                      Type.RefOrVal.LikeUnderlyingFeature));
      }
    return type_;
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
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Feature outer, Type t)
  {
    if (type_ == null)
      {
        var elementType = elementType(t);
        if (elementType != Types.t_ERROR)
          {
            for (var e : _elements)
              {
                e.propagateExpectedType(res, outer, elementType);
              }
            type_ = t;
          }
      }
    return this;
  }


  /**
   * For a given array type, return the type t of its elements.
   *
   * @param t any type
   *
   * @param if t is Array<T>; the element type T. Types.t_ERROR otherwise.
   */
  private Type elementType(Type t)
  {
    if (PRECONDITIONS) require
      (t != null);

    if (t.featureOfType() == Types.resolved.f_Array &&
        t._generics.size() == 1)
      {
        return t._generics.get(0);
      }
    else
      {
        return Types.t_ERROR;
      }
  }


  /**
   * For this array's type(), return the element type
   *
   * @param if type() is Array<T>; the element type T. Types.t_ERROR otherwise.
   */
  private Type elementType()
  {
    return elementType(type());
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public InitArray visit(FeatureVisitor v, Feature outer)
  {
    var li = _elements.listIterator();
    while (li.hasNext())
      {
        var e = li.next();
        li.set(e.visit(v, outer));
      }
    v.action(this, outer);
    return this;
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(Feature outer)
  {
    var elementType = elementType();
    var li = _elements.listIterator();
    while (li.hasNext())
      {
        var e = li.next();
        li.set(e.box(elementType));
      }
  }


  /**
   * check the types in this assignment
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes()
  {
    if (PRECONDITIONS) require
      (type_ != null);

    var elementType = elementType();

    for (var e : _elements)
      {
        Type actlT = e.type();

        check
          (actlT == Types.intern(actlT));

        check
          (Errors.count() > 0 || (elementType != Types.t_ERROR &&
                                  actlT != Types.t_ERROR    ));

        if (!elementType.isAssignableFromOrContainsError(actlT))
          {
            FeErrors.incompatibleTypeInArrayInitialization(e.pos(), type_, elementType, actlT, e);
          }
      }
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
