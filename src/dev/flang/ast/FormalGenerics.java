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
 * Source of class Case
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;
import java.util.ListIterator;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * FormalGenerics represents a list for formal generics argument.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FormalGenerics extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Convenience constant for an empty formal generics instance.
   */
  public static final FormalGenerics NONE = new FormalGenerics();


  /*----------------------------  variables  ----------------------------*/


  /**
   * Field with type from this.type created in case fieldName != null.
   *
   * This is private to prevent direct access that does not take care about
   * isOpen.
   */
  public final List<Generic> list;


  /**
   * true iff this is an open list that with an arbitrary number of actual
   * formal generic arguments.  This means that the last formal generic can be
   * repeated 0.. times, the actual arguments must hence have at least
   * list.size()-1 elements.
   */
  final boolean isOpen;


  /**
   * The Feature that contains this formal generics declaration.
   */
  private Feature _feature = null;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a FormalGenerics.NONE instance, the only instance with an
   * empty list.
   */
  private FormalGenerics()
  {
    list = new List<Generic>();
    isOpen = false;
  }


  /**
   * Constructor for a FormalGenerics instance
   *
   * @param l the list of formal generics. May not be empty.
   *
   * @param open true iff the list is open, i.e., followed by an ellipsis.
   */
  public FormalGenerics(List<Generic> l,
                        boolean open)
  {
    if (PRECONDITIONS) require
      (l.size() > 0);

    list = l;
    isOpen = open;
    for (Generic g: l)
      {
        g.setFormalGenerics(this, open && g == l.getLast());
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Stores a reference to the surrounding feature in this  FormalGenerics instance.
   */
  void setFeature(Feature feature)
  {
    if (PRECONDITIONS) require
      (this._feature == null);

    if (this != NONE)
      {
        this._feature = feature;
      }

    if (POSTCONDITIONS) ensure
      ((this == NONE || this._feature == feature),
       (this != NONE || this._feature == null   ));
  }


  /**
   * feature
   *
   * @return
   */
  public Feature feature()
  {
    return _feature;
  }


  /**
   * true if the last formal generic in an open formal generics list.
   */
  public boolean isOpen()
  {
    return isOpen;
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
    if (asActuals_ == actualGenerics)
      {
        return true;
      }
    else if (isOpen)
      {
        return (list.size()-1) <= actualGenerics.size();
      }
    else
      {
        return list.size() == actualGenerics.size();
      }
  }


  /**
   * Check if the given actualGenerics match the formal generics in this. IF
   * not, create a compiler error.
   *
   * @param actualGenerics the actual generics to check
   *
   * @param pos the source code position at which the error should be reported
   *
   * @param detail1 part of the detail message to indicate where this happened,
   * i.e., "call" or "type".
   *
   * @param detail2 optional extra lines of detail message giving further
   * information, like "Calling feature: xyz.f\n" or "Type: Stack<bool,int>\n".
   *
   * @return true iff size and type of actualGenerics does match
   */
  public boolean errorIfSizeOrTypeDoesNotMatch(List<AbstractType> actualGenerics,
                                               SourcePosition pos,
                                               String detail1,
                                               String detail2)
  {
    boolean result = true;
    if (!sizeMatches(actualGenerics))
      {
        result = false;
        AstErrors.wrongNumberOfGenericArguments(this,
                                               actualGenerics,
                                               pos,
                                               detail1,
                                               detail2);
      }
    // NYI: check that generics match the generic constraints
    return result;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, Feature outer)
  {
    for (Generic gen : list)
      {
        gen.visit(v, outer);
      }
  }


  /**
   * Find formal generic argument with given name.
   *
   * @param name the name of a formal generic argument.
   *
   * @return null if name is not the name of a formal generic argument
   * in this.  Otherwise, a reference to the formal generic argument.
   */
  public Generic get(String name)
  {
    Generic result = null;
    Iterator it = list.iterator();
    while ((result == null) && it.hasNext())
      {
        Generic g = (Generic) it.next();
        if (g._name.equals(name))
          {
            result = g;
          }
      }

    if (POSTCONDITIONS) ensure
      ((result == null) || (result._name.equals(name)));
    // result == null ==> for all g in generics: !g.name.equals(name)

    return result;
  }


  /**
   * Convenience function to resolve all types in a list of actual generic
   * arguments of a call or a type.
   *
   * @param generics the actual generic arguments that should be resolved
   */
  public static void resolve(Resolution res, List<AbstractType> generics, AbstractFeature outer)
  {
    if (!generics.isEmpty())
      {
        if (!(generics instanceof FormalGenerics.AsActuals))
          {
            ListIterator<AbstractType> i = generics.listIterator();
            while (i.hasNext())
              {
                var t = i.next();
                i.set(t.resolve(res, outer));
              }
          }
      }
  }


  private List<AbstractType> asActuals_ = null;


  /**
   * Wrapper class for result of asActuals(). This is used to quickly identify
   * the generics list of formals used as actuals, e.g., in an outer reference,
   * such that it can be replaced 1:1 by the actual generic arguments.
   */
  class AsActuals extends List<AbstractType>
  {
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
    List<AbstractType> result = asActuals_;
    if (result == null)
      {
        if (this == FormalGenerics.NONE)
          {
            result = Call.NO_GENERICS;
          }
        else
          {
            result = new AsActuals();// new List<Type>();
            // NYI: This is a bit ugly, can we avoid adding all these types
            // here?  They should never be used since AsActuals is only a
            // placeholder for the actual generics.
            for (Generic g : list)
              {
                result.add(new Type(_feature.pos(), g));
              }
          }
        asActuals_ = result;
      }

    if (POSTCONDITIONS) ensure
      (sizeMatches(result));

    return result;
  }


  /**
   * Number of generic arguments expected as a text to be used in error messages
   * about wrong number of actual generic arguments.
   *
   * @return
   */
  public String sizeText()
  {
    int sz = isOpen ? list.size() - 1
                    : list.size();
    return
      isOpen    && (sz == 0) ? "any number of generic arguments"
      :  isOpen && (sz == 1) ? "at least one generic argument"
      :  isOpen && (sz >  1) ? "at least " + sz + " generic arguments"
      : !isOpen && (sz == 0) ? "no generic arguments"
      : !isOpen && (sz == 1) ? "one generic argument"
      :                        "" + sz + " generic arguments" ;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return !isOpen && list.isEmpty() ? ""
                                     : "<" + list + (isOpen ? "..." : "") + ">";
  }

}
