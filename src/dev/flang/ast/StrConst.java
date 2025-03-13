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
 * Source of class StrConst
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import dev.flang.util.SourcePosition;


/**
 * StrConst
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class StrConst extends Constant
{

  /*----------------------------  constants  ----------------------------*/


  private final String _str;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param s
   */
  public StrConst(SourcePosition pos, String s)
  {
    super(pos);
    this._str = s;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    return isCodepointLiteral()
      ? Types.resolved.t_codepoint
      : Types.resolved.t_String;
  }


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   */
  @Override
  public AbstractType type()
  {
    return isCodepointLiteral()
      ? Types.resolved.t_codepoint
      : Types.resolved.t_String;
  }


  /**
   * Serialized form of the data of this constant.
   */
  public byte[] data()
  {
    byte[] result;

    if (isCodepointLiteral())
      {
        var nl = new NumLiteral(_str.codePointAt(0));
        nl.propagateExpectedType(Types.resolved.t_u32);
        result = nl.data();
      }
    else
      {
        var b = _str.getBytes(StandardCharsets.UTF_8);
        var r = ByteBuffer.allocate(4 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        r.putInt(b.length);
        r.put(b);
        result = r.array();
      }

    return result;
  }


  /**
   * Is this StrConst denoting a codepoint?
   */
  private boolean isCodepointLiteral()
  {
    return _str.codePoints().count() == 1;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "\"" + _str + "\"";
  }

}

/* end of file */
