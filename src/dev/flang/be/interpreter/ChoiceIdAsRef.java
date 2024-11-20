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

import dev.flang.fuir.FUIR;

import java.util.ArrayList;


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
  public static ChoiceIdAsRef get(int clazz, int id)
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(clazz),
       id >= 0,
       id <  fuir().clazzNumChoices(clazz));

    ChoiceIdAsRef result;
    if (fuir().clazzNumChoices(clazz) > 2)
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
  public static int tag(int clazz, Value idAsRef)
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(clazz));

    int result = -1;

    if (idAsRef == null)
      {
        // null stands for the first (and only) non-reference type
        result = 0;
        while (result < fuir().clazzNumChoices(clazz) && fuir().clazzIsRef(fuir().clazzChoice(clazz, result)))
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
        if (CHECKS) check
          (idAsRef instanceof ValueWithClazz ||
           idAsRef instanceof JavaRef        ||
           idAsRef instanceof ArrayData  );

        var cl = (idAsRef instanceof ValueWithClazz id) ? id._clazz
                                                        : fuir().clazz(FUIR.SpecialClazzes.c_sys_ptr);
        do
          {
            result++;
          }
        while (!inheritsFrom(cl, fuir().clazzChoice(clazz, result)));
      }

    if (POSTCONDITIONS) ensure
      (0 <= result && result < fuir().clazzNumChoices(clazz));

    return result;
  }


  /**
   * Does clazz child inherit from parent?
   */
  static boolean inheritsFrom(int child, int parent)
  {
    var result = false;
    for (var h : fuir().clazzInstantiatedHeirs(parent))
      {
        result = result || (child == h);
      }
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
