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
 * Source of class ChoiceIdAsRef
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.util.ArrayList;

import dev.flang.air.Clazz;


/**
 * ChoiceIdAsRef represents the id stored in the tag of a choice type as a
 * reference.  This can be used to avoid the need for a tag and use the
 * _choiceValRef field instead.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ChoiceIdAsRef extends Value
{


  /*-------------------------  static variables  ------------------------*/


  /**
   *
   */
  public static ArrayList<ChoiceIdAsRef> _preallocated = new ArrayList<>();


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  final int _id;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param id the id stored in this value
   */
  public ChoiceIdAsRef(int id)
  {
    if (PRECONDITIONS) require
      (id >= 0);

    this._id = id;

    _preallocated.add(this);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * get returns the ChoiceIdAsRef for the given id.
   *
   * @param clazz a the choice clazz
   *
   * @param id the id of a choice in clazz
   *
   * @return the ChoiceIdAsRef value for id, may be null in case there is only
   * one such id.
   */
  public static ChoiceIdAsRef get(Clazz clazz, int id)
  {
    if (PRECONDITIONS) require
      (clazz.isChoice(),
       id >= 0,
       id <  clazz._choiceGenerics.size());

    ChoiceIdAsRef result;
    if (clazz._choiceGenerics.size() > 2)
      {
        // make sure all values are preallocated
        while (_preallocated.size() <= id)
          {
            new ChoiceIdAsRef(_preallocated.size());
          }
        result = _preallocated.get(id);
      }
    else
      {
        result = null;
      }

    if (POSTCONDITIONS) ensure
      (tag(clazz, result) == id);

    return result;
  }


  /**
   * tag returns the id corresponding to a result of get(clazz,int).
   *
   * @param idAsRef the id stored in the returned value
   *
   * @return the id stored in idAsRef.
   */
  public static int tag(Clazz clazz, Value idAsRef)
  {
    if (PRECONDITIONS) require
      (clazz.isChoice());

    int result = -1;

    if (idAsRef == null)
      {
        // null stands for the first (and only) non-reference type
        result = 0;
        while (clazz.choiceGenerics().get(result).isRef())
          {
            result++;
          }
      }
    else if (idAsRef instanceof ChoiceIdAsRef)
      {
        result = ((ChoiceIdAsRef) idAsRef)._id;
      }
    else
      {
        result = 0;
        while (!clazz.choiceGenerics().get(result)._type.isAssignableFrom(((ValueWithClazz)idAsRef)._clazz._type))
          {
            result++;
          }
      }

    if (POSTCONDITIONS) ensure
      (0 <= result && result < clazz.choiceGenerics().size());

    return result;
  }


  /**
   * toString, for debugging
   *
   * @return
   */
  public String toString()
  {
    return "choiceIdAsRef["+_id+"]";
  }

}

/* end of file */
