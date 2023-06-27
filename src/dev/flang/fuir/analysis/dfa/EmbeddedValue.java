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

import java.util.TreeMap;
import java.util.stream.Collectors;

import dev.flang.util.Errors;


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
public class EmbeddedValue extends Value
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
  final int _cl;
  final int _code;
  final int _index;


  /**
   * The value contained in this field.
   */
  final Value _value;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create EmbeddedValue for given instance and value.
   *
   * @param instance the instance containing this embedded value
   *
   * @param value the value of the embedded field
   */
  public EmbeddedValue(Instance instance,
                       Value value)
  {
    this(instance, -1, -1, -1, value);

    if (PRECONDITIONS) require
      (instance != null,
       value != null,
       value._clazz == -1 || !instance._dfa._fuir.clazzIsRef(value._clazz));
  }


  /**
   * Create EmbeddedValue for given call/code/index and value.
   *
   * @param cl the call that contains the code
   *
   * @param code code block index
   *
   * @param index expr index in code block
   *
   * @param value the value of the embedded field
   */
  public EmbeddedValue(int cl,
                       int code,
                       int index,
                       Value value)
  {
    this(null, cl, code, index, value);

    if (PRECONDITIONS) require
      (code != -1 && index != -1,
       value != null);
  }


  /**
   * Create EmbeddedValue for given insatnce or code/index and value.
   *
   * @param cl the call that contains the code
   *
   * @param instance the instance containing this embedded value
   *
   * @param code code block index
   *
   * @param index expr index in code block
   *
   * @param value the value of the embedded field
   */
  private EmbeddedValue(Instance instance,
                        int cl,
                        int code,
                        int index,
                        Value value)
  {
    super(value._clazz);

    try {
    if (PRECONDITIONS) require
      ((instance != null) != (code != -1 && index != -1),
       value != null,
       instance == null || value._clazz == -1 || !instance._dfa._fuir.clazzIsRef(value._clazz));
    } catch (Error e)
      {
        System.out.println("value._clazz: "+value._clazz+" "+value);
        throw e;
      }


    this._instance = instance;
    this._cl = cl;
    this._code = code;
    this._index = index;
    this._value = value;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get set of values of given field within this instance.
   */
  Value readFieldFromInstance(DFA dfa, int field)
  {
    if (PRECONDITIONS) require
      (_clazz == dfa._fuir.clazzAsValue(dfa._fuir.clazzOuterClazz(field)));

    var res = _value.readFieldFromInstance(dfa, field);
    return new EmbeddedValue(_instance, _cl, _code, _index, res);
  }



  /**
   * Get set of values of given field within this value.  This works for unit
   * type results even if this is not an instance (but a unit type itself).
   */
  public Value readField(DFA dfa, int field)
  {
    var res = _value.readField(dfa, field);
    return new EmbeddedValue(_instance, _cl, _code, _index, res);
  }



  /**
   * Create a call to a field
   *
   * @param cc the inner value of the field that is called.
   */
  Value rewrap(DFA dfa, Value res)
  {
    return res == null || res._clazz == -1 || dfa._fuir.clazzIsRef(res._clazz) ? res : new EmbeddedValue(_instance, _cl, _code, _index, res);
  }


  /**
   * Create a call to a field
   *
   * @param cc the inner value of the field that is called.
   */
  Value callField(DFA dfa, int cc)
  {
    var res = _value.callField(dfa, cc);
    try {
    return res == null || res._clazz == -1 || dfa._fuir.clazzIsRef(res._clazz) ? res : new EmbeddedValue(_instance, _cl, _code, _index, res);
    } catch (Error | RuntimeException e)
      {
        //        System.out.println("res._clazz: "+res._clazz+" "+res);
        throw e;
      }
  }

  /**
   * Create the union of the values 'this' and 'v'. This is called by join()
   * after common cases (same instance, UNDEFINED) have been handled.
   */
  public Value joinInstances(Value v)
  {
    return _value.joinInstances(v);
  }


  /**
   * Perform c.accept on this and, if this is a set, on all values contained in
   * the set.
   *
   * @param c a consumer to apply to the values.
   */
  public void forAll(ValueConsumer c)
  {
    _value.forAll(c);
  }


  /**
   * In case this value is wrapped in an instance that contains additional
   * information unrelated to the actual value (e.g. EmbeddedValue), get the
   * actual value.
   */
  Value unwrap()
  {
    return _value.unwrap();
  }


  /**
   * Create human-readable string from this instance.
   */
  public String toString()
  {
    return
      (_instance != null ? _instance._dfa._fuir.clazzAsString(_clazz) + " embedded in " + _instance.toString()
                         : "EMBEDDED in " + _code + "@" + _index)  ;
  }

}

/* end of file */
