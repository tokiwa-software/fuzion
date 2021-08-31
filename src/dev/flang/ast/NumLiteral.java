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
 * Source of class NumLiteral
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;

import java.math.BigInteger;

/**
 * NumLiteral <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class NumLiteral extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  static enum ConstantType
  {
    ct_i8  (true, 1),
    ct_i16 (true, 2),
    ct_i32 (true, 4),
    ct_i64 (true, 8),
    ct_u8  (false, 1),
    ct_u16 (false, 2),
    ct_u32 (false, 4),
    ct_u64 (false, 8),
    ct_f32 (4),
    ct_f64 (8);
    final BigInteger _min, _max;
    final int _bytes;
    final boolean _signed;
    final boolean _float;
    ConstantType(boolean signed, int bytes)
    {
      _signed = signed;
      _bytes = bytes;
      var minb = new byte[bytes];
      var maxb = new byte[signed ? bytes : bytes + 1];
      if (signed)
        {
          for (var i = 0; i < bytes; i++)
            {
              minb[i] = (byte) (i == 0 ? 0x80 : 0x00);
              maxb[i] = (byte) (i == 0 ? 0x7f : 0xff);
            }
        }
      else
        {
          for (var i = 0; i <= bytes; i++)
            {
              maxb[i] = (byte) (i == 0 ? 0x00 : 0xff);
            }
        }
      _min = new BigInteger(minb);
      _max = new BigInteger(maxb);
      _float = false;
    }
    ConstantType(int bytes)
    {
      _bytes = bytes;
      _signed = true;
      _float = true;
      _min = new BigInteger("-1000000");  // NYI
      _max = new BigInteger( "1000000");  // NYI
    }
    boolean canHold(BigInteger value)
    {
      return
        _min.compareTo(value) <= 0 &&
        _max.compareTo(value) >= 0;
    }
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The constant as it appeared in the source code
   */
  private final String _originalString;



  /**
   * The radix _originalString is represented in, one of 2, 8, 10, 16.
   */
  private final int _radix;


  /**
   * The constant value, converted to BigInteger
   */
  public final BigInteger _value;


  /**
   * The type of this constant.  This can be set by the user of this type
   * depending on what this is assigned to.
   */
  private Type type_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param s
   */
  public NumLiteral(SourcePosition pos, String s, int base, BigInteger v)
  {
    super(pos);

    this._originalString = s;
    this._radix = base;
    this._value = v;
  }


  /**
   * Constructor
   *
   * @param i
   */
  public NumLiteral(int i)
  {
    this(SourcePosition.builtIn, Integer.toString(i), 10, BigInteger.valueOf(i));

    if (PRECONDITIONS) require
      (i >= 0);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create new constant by flipping the sign.
   */
  public NumLiteral neg(SourcePosition pos)
  {
    var o = _originalString;
    var s = o.startsWith("-") ? o.substring(1) : "-" + o;
    return new NumLiteral(pos, s, _radix, _value.negate());
  }


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    if (type_ == null)
      {
        if (ConstantType.ct_i32.canHold(_value))
          {
            type_ = Types.resolved.t_i32;
          }
        else
          {
            type_ = Types.resolved.t_i64;
          }
        checkRange();
      }
    return type_;
  }


  /**
   * Check that this constant is in the range allowed for its type_.
   */
  void checkRange()
  {
    if (PRECONDITIONS) require
      (findConstantType(type_) != null);

    ConstantType ct = findConstantType(type_);
    if (!ct.canHold(_value))
      {
        FeErrors.integerConstantOutOfLegalRange(pos(),
                                                _originalString,
                                                type_,
                                                toString(ct._min),
                                                toString(ct._max));
      }
  }


  /*
   * Convert given value to a string using _radix. Prepend "0x" or "0b" for hex
   * / binary representation.s
   *
   * @param v a constant
   *
   * @return the string representation of v.
   */
  String toString(BigInteger v)
  {
    String sign = "";
    if (v.signum() < 0)
      {
        sign = "-";
        v = v.negate();
      }
    String prefix =
      switch (_radix)
        {
        case  2 -> "0b";
        case 10 -> "";
        case 16 -> "0x";
        default -> throw new Error("unexpected radix: " + _radix);
        };
    return sign + prefix + v.toString(_radix);
  }


  /**
   * Check if type t is one of the known integer types i8, i16, i32, i64, u8,
   * u16, u32, u64 and return the corresponding ConstantType constant.
   *
   * @param t an interned type
   *
   * @return the corresponding ConstantType or nul if none.
   */
  ConstantType findConstantType(Type t)
  {
    if      (t == Types.resolved.t_i8 ) { return ConstantType.ct_i8 ; }
    else if (t == Types.resolved.t_i16) { return ConstantType.ct_i16; }
    else if (t == Types.resolved.t_i32) { return ConstantType.ct_i32; }
    else if (t == Types.resolved.t_i64) { return ConstantType.ct_i64; }
    else if (t == Types.resolved.t_u8 ) { return ConstantType.ct_u8 ; }
    else if (t == Types.resolved.t_u16) { return ConstantType.ct_u16; }
    else if (t == Types.resolved.t_u32) { return ConstantType.ct_u32; }
    else if (t == Types.resolved.t_u64) { return ConstantType.ct_u64; }
    else if (t == Types.resolved.t_f32) { return ConstantType.ct_f32; }
    else if (t == Types.resolved.t_f64) { return ConstantType.ct_f64; }
    else                                { return null;             }
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Feature outer, Type t)
  {
    if (type_ == null && findConstantType(t) != null)
      {
        type_ = t;
        checkRange();
      }
    return this;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public NumLiteral visit(FeatureVisitor v, Feature outer)
  {
    // nothing to be done for a constant
    return this;
  }


  /**
   * Get the little-endian representation of this constant.
   *
   * @return an array with length findConstantType(type_)._bytes containing the
   * constant as a little-endian unsigned or two's complement value.
   */
  public byte[] data()
  {
    var b = _value.toByteArray();
    var bytes = findConstantType(type_)._bytes;
    var result = new byte[bytes];
    for (var i = 0; i < bytes; i++)
      {
        if (i >= b.length)
          {
            result[i] = (byte) (_value.signum() < 0 ? 0xff : 0x00);
          }
        else
          {
            result[i] = b[b.length - 1 - i];
          }
      }
    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return _originalString;
  }

}

/* end of file */
