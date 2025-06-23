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
 * Source of class FormalGenerics
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * FormalGenerics represents a list for formal generics argument.
 *
 * e.g. For {@code Function(public R type, public A type...) ref is}
 * the formal generics are {@code R} and {@code A}. (With {@code A} being an open type parameter.)
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FormalGenerics extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Convenience constant for an empty formal generics instance.
   */
  public static final FormalGenerics NONE = new FormalGenerics(new List<>());


  /*----------------------------  variables  ----------------------------*/


  /**
   * Field with type from this.type created in case fieldName != null.
   *
   * This is private to prevent direct access that does not take care about
   * isOpen.
   */
  public final List<AbstractFeature> list;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a FormalGenerics instance
   *
   * @param l the list of formal generics. May not be empty.
   */
  public FormalGenerics(List<AbstractFeature> l)
  {
    list = l;
    list.freeze();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * true if the last formal generic in an open formal generics list.
   */
  public boolean isOpen()
  {
    return !list.isEmpty() && list.getLast().isOpenTypeParameter();
  }


  /**
   * Check if the number of actual generics provided as an argument matches the
   * number of formal arguments required by this.  This takes into account that
   * the formal generics list might be open, i.e, the last argument can be
   * repeated zero or more times.
   *
   * @param actualGenerics the list of actual generics.
   *
   * @return true iff the number of actual arguments fits with the number of
   * expected arguments.
   */
  public boolean sizeMatches(List<AbstractType> actualGenerics)
  {
    if (_asActuals == actualGenerics)
      {
        return true;
      }
    else if (isOpen())
      {
        return (list.size()-1) <= actualGenerics.size();
      }
    else
      {
        return list.size() == actualGenerics.size();
      }
  }


  /**
   * Check if the number of actualGenerics match this FormalGenerics. If not,
   * create a compiler error.
   *
   * @param actualGenerics the actual generics to check
   *
   * @param pos the source code position at which the error should be reported
   *
   * @param detail1 part of the detail message to indicate where this happened,
   * i.e., "call" or "type".
   *
   * @param detail2 optional extra lines of detail message giving further
   * information, like {@code Calling feature: xyz.f\n" or "Type: Stack<bool,int>\n}.
   *
   * @return true iff size and type of actualGenerics does match
   */
  public boolean errorIfSizeDoesNotMatch(List<AbstractType> actualGenerics,
                                         SourcePosition pos,
                                         String detail1,
                                         String detail2)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !actualGenerics.contains(Types.t_ERROR));

    var result = sizeMatches(actualGenerics) || actualGenerics.contains(Types.t_ERROR);
    if (!result)
      {
        AstErrors.wrongNumberOfGenericArguments(this,
                                                actualGenerics,
                                                pos,
                                                detail1,
                                                detail2);
      }
    return result;
  }


  /**
   * Convenience function to resolve all types in a list of actual generic
   * arguments of a call or a type.
   *
   * @param generics the actual generic arguments that should be resolved
   *
   * @return a new array of the resolved generics
   */
  public static List<AbstractType> resolve(Resolution res, List<AbstractType> generics, AbstractFeature outer)
  {
    if (!(generics instanceof FormalGenerics.AsActuals))
      {
        generics = generics.map(t -> t.resolve(res, outer.context()));
      }
    return generics;
  }


  private List<AbstractType> _asActuals = null;


  /**
   * Wrapper class for result of asActuals(). This is used to quickly identify
   * the generics list of formals used as actuals, e.g., in an outer reference,
   * such that it can be replaced 1:1 by the actual generic arguments.
   */
  class AsActuals extends List<AbstractType>
  {
    /**
     * create AsActuals from FormalGenerics.this.list and freeze it.
     */
    AsActuals()
    {
      // NYI: This is a bit ugly, can we avoid adding all these types
      // here?  They should never be used since AsActuals is only a
      // placeholder for the actual generics.
      for (var g : list)
        {
          add(g.asGenericType());
        }
      freeze();
    }

    /**
     * Create non-frozen clone of from.
     */
    AsActuals(AsActuals from)
    {
      super(from);
    }

    /**
     * Create non-frozen clone of this.
     */
    public List<AbstractType> clone()
    {
      return new AsActuals(this);
    }

    /**
     * Check if this are the formal generics of f used as actuals.
     */
    boolean actualsOf(AbstractFeature f)
    {
      return f.generics() == FormalGenerics.this;
    }

    public boolean sizeMatches(List<AbstractType> actualGenerics)
    {
      return FormalGenerics.this.sizeMatches(actualGenerics);
    }
  };


  /**
   * Create the formal generics parameters for an outer reference for any inner
   * feature declared within this formal generic's feature.
   *
   * @return actual generics that match these formal generics.
   */
  public List<AbstractType> asActuals()
  {
    var result = _asActuals;
    if (result == null)
      {
        if (this == FormalGenerics.NONE)
          {
            result = UnresolvedType.NONE;
          }
        else
          {
            result = new AsActuals();
          }
        _asActuals = result;
      }

    if (POSTCONDITIONS) ensure
      (sizeMatches(result));

    return result;
  }


  /**
   * Add type parameter g to this list of formal generics.
   *
   * @param g the new type parameter
   */
  FormalGenerics addTypeParameter(AbstractFeature g)
  {
    return new FormalGenerics(list.addAfterUnfreeze(g));
  }


  /**
   * Number of generic arguments expected as a text to be used in error messages
   * about wrong number of actual generic arguments.
   *
   * @return
   */
  public String sizeText()
  {
    int sz = isOpen() ? list.size() - 1
                      : list.size();
    return
      isOpen()    && (sz == 0) ? "any number of generic arguments"
      :  isOpen() && (sz == 1) ? "at least one generic argument"
      :  isOpen() && (sz >  1) ? "at least " + sz + " generic arguments"
      : !isOpen() && (sz == 0) ? "no generic arguments"
      : !isOpen() && (sz == 1) ? "one generic argument"
      :                          "" + sz + " generic arguments" ;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return !isOpen() && list.isEmpty() ? ""
                                       : list + (isOpen() ? "..." : "");
  }

}
