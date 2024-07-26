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
 * Source of class EmbeddedValue
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;

import java.util.function.Function;


/**
 * EmbeddedValue represents the value of a value type field that was read
 * from an instance. It is essentially a tuple of the actual value of the field
 * and the instance that contains this field.
 *
 * EmbeddedValue is used to track references into this instance: If the
 * address of this value escapes, this means that the lifespan of the
 * instance containing this value has to be extended accordingly.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class EmbeddedValue extends Val
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The instance that contains this embedded value, null if embedded value
   * is a temporary result.
   */
  final Instance _instance;

  /**
   * If this is a temporary result, the called feature, code block and index of
   * the call that produced this result. -1/-1 otherwise.
   */
  final int _site;


  /**
   * The value contained in this field.
   */
  final Value _value;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create EmbeddedValue for given instance or code/index and value.
   *
   * @param instance the instance containing this embedded value
   *
   * @param code code block index
   *
   * @param index expr index in code block
   *
   * @param value the value of the embedded field
   */
  EmbeddedValue(Instance instance,
                int site,
                Value value)
  {
    if (PRECONDITIONS) require
      ((instance != null) != (site != -1),
       value != null,
       instance == null || value._clazz == -1 || !instance._dfa._fuir.clazzIsRef(value._clazz));

    this._instance = instance;
    this._site = site;
    this._value = value;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * apply f to the unwrapped value and re-wrap
   *
   * @param f function to apply to unwrapped value.
   */
  public Val rewrap(DFA dfa, Function<Value,Val> f)
  {
    var res = f.apply(value());
    var rv = res.value();
    var cl = rv._clazz;
    if (cl != -1 && dfa._fuir.clazzIsRef(cl))
      {
        return res;
      }
    return new EmbeddedValue(_instance, _site, rv);
  }


  /**
   * In case this value is wrapped in an instance that contains additional
   * information unrelated to the actual value (e.g. EmbeddedValue), get the
   * actual value.
   */
  Value value()
  {
    return _value;
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return
      _value +
      (_instance != null ? " embedded in " + _instance.toString()
                         : " EMBEDDED in " + _site);
  }


}

/* end of file */
