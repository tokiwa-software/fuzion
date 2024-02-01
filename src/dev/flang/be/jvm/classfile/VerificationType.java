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
 * Source of class VerificationType
 *
 *---------------------------------------------------------------------*/


package dev.flang.be.jvm.classfile;

import dev.flang.util.ANY;
import dev.flang.util.List;

/*
 * A verification type specifies a local variable or a stack entry.
 *
 * See also section 4.7.4 in https://docs.oracle.com/javase/specs/jvms/se21/jvms21.pdf
 */
public class VerificationType extends ANY implements Comparable<VerificationType>
{

  /*
   * Predefined verification types that do not have a constant pool index or offset.
   */

  public static final VerificationType Top = new VerificationType(VerificationType.type.Top);
  public static final VerificationType Integer = new VerificationType(VerificationType.type.Integer);
  public static final VerificationType Float = new VerificationType(VerificationType.type.Float);
  public static final VerificationType Long = new VerificationType(VerificationType.type.Long);
  public static final VerificationType Double = new VerificationType(VerificationType.type.Double);
  public static final VerificationType Null = new VerificationType(VerificationType.type.Null);
  public static final VerificationType UninitializedThis = new VerificationType(VerificationType.type.UninitializedThis);


  private VerificationType.type _type;

  /**
   * if _t == Uninitialized then offset,
   * if _t == Object        then cpool_index;
   */
  private int _s = -1;


  public enum type
  {

    Top(0, "Top"),
    Integer(1, "Integer"),
    Float(2, "Float"),
    Long(4, "Long"),
    Double(3, "Double"),
    Null(5, "Null"),
    UninitializedThis(6, "UninitializedThis"),
    Object(7, "Object"),
    Uninitialized(8, "Uninitialized");

    int num;
    String label;


    type(int num, String l)
    {
      this.num = num;
      this.label = l;
    };
  }


  /*
   * Constructor for a verification type that does
   * not need are reference in the constant pool index.
   */
  private VerificationType(type t)
  {
    if (PRECONDITIONS)
      require(t != type.Object,
              t != type.Uninitialized);

    this._type = t;
  }


  /**
   * @param t
   * @param s if t == Uninitialized then offset,
   *          if t == Object        then cpool_index;
   */
  public VerificationType(type t, int s)
  {
    if (PRECONDITIONS)
      require(t == type.Object || t == type.Uninitialized,
              s >= 0);
    this._type = t;
    this._s = s;
  }


  /*
   * The verification_type_info structure consists of a one-byte tag followed
   * by zero or more bytes, giving more information about the tag.
   */
  public void write(ClassFile.Kaku o)
  {
    o.writeU1(_type.num);
    switch (_type)
      {
      case Object :
        o.writeU2(_s);
        break;
      case Uninitialized :
        o.writeU2(_s);
        break;
      default:
        break;
      }
  }


  @Override
  public String toString()
  {
    return _type.label + " at " + _s;
  }


  @Override
  public int compareTo(VerificationType other)
  {
    return this._type.ordinal() < other._type.ordinal()
      ? -1
      : this._type.ordinal() > other._type.ordinal()
      ? 1
      : this._s - other._s;
  }


  /**
   * Would a local variable or stack item with
   * this verification type need two slots?
   */
  public boolean needsTwoSlots()
  {
    return this == VerificationType.Double
      || this == VerificationType.Long;
  }


  /**
   * The union of two verification types.
   */
  private VerificationType union(VerificationType other)
  {
    return compareTo(other) == 0
      ? this
      : VerificationType.Top;
  }


  /**
   * Create a union of two lists of verification types.
   * If the lists are of unequal length, end is filled with
   * verification type Top.
   *
   * @param a
   * @param b
   * @return
   */
  static List<VerificationType> union(List<VerificationType> a, List<VerificationType> b)
  {
    var r = new List<VerificationType>();
    for (int index = 0; index < Math.max(a.size(), b.size()); index++)
      {
        r.add(
          a.size() > index && b.size() > index
           ? a.get(index).union(b.get(index))
           : a.size() > index
              ? VerificationType.Top
           : VerificationType.Top);
      }
    return r;
  }



}
