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
 * Source of class VerificationTypeInfo
 *
 *---------------------------------------------------------------------*/


package dev.flang.be.jvm.classfile;

import dev.flang.util.ANY;
import dev.flang.util.List;

public class VerificationTypeInfo extends ANY implements Comparable<VerificationTypeInfo>
{

  private VerificationTypeInfo.type _t;

  public VerificationTypeInfo.type type()
  {
    return _t;
  }

  public static final VerificationTypeInfo Top = new VerificationTypeInfo(VerificationTypeInfo.type.Top);
  public static final VerificationTypeInfo Integer = new VerificationTypeInfo(VerificationTypeInfo.type.Integer);
  public static final VerificationTypeInfo Float = new VerificationTypeInfo(VerificationTypeInfo.type.Float);
  public static final VerificationTypeInfo Long = new VerificationTypeInfo(VerificationTypeInfo.type.Long);
  public static final VerificationTypeInfo Double = new VerificationTypeInfo(VerificationTypeInfo.type.Double);
  public static final VerificationTypeInfo Null = new VerificationTypeInfo(VerificationTypeInfo.type.Null);
  public static final VerificationTypeInfo UninitializedThis = new VerificationTypeInfo(VerificationTypeInfo.type.UninitializedThis);

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
    String l;


    type(int num, String l)
    {
      this.num = num;
      this.l = l;
    };
  }


  private VerificationTypeInfo(type t)
  {
    if (PRECONDITIONS)
      require(t != type.Object,
              t != type.Uninitialized);

    this._t = t;
  }


  /**
   * @param t
   * @param s if t == Uninitialized then offset,
   *          if t == Object        then cpool_index;
   */
  public VerificationTypeInfo(type t, int s)
  {
    if (PRECONDITIONS)
      require(t == type.Object || t == type.Uninitialized,
              s >= 0);
    this._t = t;
    this._s = s;
  }


  /*
   * The verification_type_info structure consists of a one-byte tag followed
   * by zero or more bytes, giving more information about the tag.
   */
  public void write(ClassFile.Kaku o)
  {
    o.writeU1(_t.num);
    switch (_t)
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
    return _t.l + " at " + _s;
  }


  @Override
  public int compareTo(VerificationTypeInfo other)
  {
    return this._t.ordinal() < other._t.ordinal()
      ? -1
      : this._t.ordinal() > other._t.ordinal()
      ? 1
      : this._s - other._s;
  }


  public VerificationTypeInfo union(VerificationTypeInfo other)
  {
    return compareTo(other) == 0
      ? this
      : VerificationTypeInfo.Top;
  }


  static List<VerificationTypeInfo> union(List<VerificationTypeInfo> a, List<VerificationTypeInfo> b)
  {
    var r = new List<VerificationTypeInfo>();
    for (int index = 0; index < Math.max(a.size(), b.size()); index++)
      {
        r.add(
          a.size() > index && b.size() > index
           ? a.get(index).union(b.get(index))
           : a.size() > index
              ? VerificationTypeInfo.Top
           : VerificationTypeInfo.Top);
      }
    return r;
  }


}
