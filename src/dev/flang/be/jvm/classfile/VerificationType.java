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

import java.util.function.Supplier;

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
  private Supplier<Integer> _s = null;

  // used for comparing VerificationTypes
  // even before we add an entry to the
  // constant pool
  private String className = null;


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
   * Constructor for verification type object.
   *
   * @param className the className, used for comparing and unioning verification types.
   * @param s () => cpool_index;
   */
  public VerificationType(String className, Supplier<Integer> s)
  {
    if (PRECONDITIONS) require
     (className != null,
      s != null);
    this._type = VerificationType.type.Object;
    this.className = className;
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
      case Uninitialized :
        o.writeU2(_s.get());
        break;
      default:
        break;
      }
  }


  @Override
  public String toString()
  {
    return _type.label + (className != null ? "(" + className + ")" : ")");
  }


  @Override
  public int compareTo(VerificationType other)
  {
    if (PRECONDITIONS) require
      (_s == null // does not have constant pool index
      || className != null);

    return this._type.ordinal() < other._type.ordinal()
      ? -1
      : this._type.ordinal() > other._type.ordinal()
      ? 1
      : _type != type.Object
      ? 0
      : className.compareTo(other.className);
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
