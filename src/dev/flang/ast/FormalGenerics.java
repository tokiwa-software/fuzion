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


  private static final List<AbstractFeature> NO_FEATURES = new List<>();
  // NYI: UNDER DEVELOPMENT: { NO_FEATURES.freeze(); }

  private final AbstractFeature _feature;


  /**
   * Field with type from this.type created in case fieldName != null.
   *
   * This is private to prevent direct access that does not take care about
   * isOpen.
   */
  // NYI: CLEANUP: remove this method
  public final List<AbstractFeature> list()
  {
    return _feature == null
      ? NO_FEATURES
      : _feature.typeArguments();
  };


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a FormalGenerics instance
   *
   * @param af the features for which this is the generics.
   */
  public FormalGenerics(AbstractFeature af)
  {
    _feature = af;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * true if the last formal generic in an open formal generics list.
   */
  public boolean isOpen()
  {
    return !list().isEmpty() && list().getLast().isOpenTypeParameter();
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
    return isOpen()
      ? (list().size()-1) <= actualGenerics.size()
      : list().size() == actualGenerics.size();
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
      for (var g : list())
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
    if (PRECONDITIONS) require
      (_feature.state().atLeast(State.RESOLVING_DECLARATIONS));

    // NYI: UNDER DEVELOPMENT: re-add caching?
    return new AsActuals();
  }


  /**
   * Number of generic arguments expected as a text to be used in error messages
   * about wrong number of actual generic arguments.
   *
   * @return
   */
  public String sizeText()
  {
    int sz = isOpen() ? list().size() - 1
                      : list().size();
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
    return !isOpen() && list().isEmpty()
      ? ""
      : list().map2(f -> f.featureName().baseNameHuman()) + (isOpen() ? "..." : "");
  }

}
