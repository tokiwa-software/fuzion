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
    int bits()
    {
      return _bytes*8;
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
   * The base the main part of _originalString is represented in, one of 2, 8,
   * 10, 16 (the exponent might use a different base).
   */
  private final int _base;


  /**
   * The mantissa of the constant value, converted to BigInteger and with '.'
   * removed.  The value represented by this instance is
   *
   *   _mantissa * 2 ^ _exponent2 * 5 ^ _exponent5.
   *
   */
  private final BigInteger _mantissa;


  /**
   * Exponent to the base of 2 that has to be multiplied with _mantissa to
   * obtain the value represented by this.
   */
  private final int _exponent2;


  /**
   * Exponent to the base of 2 that has to be multiplied with _mantissa to
   * obtain the value represented by this.
   */
  private final int _exponent5;

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
  public NumLiteral(SourcePosition pos,
                    String s,
                    int base,
                    BigInteger v,
                    int dotAt,
                    BigInteger exponent,
                    int exponentBase)
  {
    super(pos);

    this._base = base;
    this._originalString = s;
    var b = base;
    var e2 = 0;
    var e5 = 0;
    var min = BigInteger.valueOf(-1000000);
    var max = BigInteger.valueOf( 1000000);
    var e = exponent.min(max).max(min).intValue();   // force within range min..max

    while (exponentBase % 2 == 0)
      {
        exponentBase = exponentBase / 2;
        e2 += e;
      }
    while (exponentBase % 5 == 0)
      {
        exponentBase = exponentBase / 5;
        e5 += e;
      }
    check
      (exponentBase == 1); // we do not support exponentBases other that 2^n*5^m

    while (b % 2 == 0)
      {
        b = b / 2;
        e2 -= dotAt;
      }
    while (b % 5 == 0)
      {
        b = b / 5;
        e5 -= dotAt;
      }
    check
      (b == 1); // we do not support bases other that 2^n*5^m

    while (v.signum() != 0 && v.mod(BigInteger.valueOf(2)).signum() == 0)
      {
        v = v.divide(BigInteger.valueOf(2));
        e2 = e2+1;
      }
    while (v.signum() != 0 && v.mod(BigInteger.valueOf(5)).signum() == 0)
      {
        v = v.divide(BigInteger.valueOf(5));
        e5 = e5 + 1;
      }
    this._mantissa = v;
    this._exponent2 = e2;
    this._exponent5 = e5;
  }



  /**
   * Constructor for literal with given values its fields.
   */
  private NumLiteral(SourcePosition pos,
                     String s,
                     int base,
                     BigInteger m,
                     int e2,
                     int e5)
  {
    super(pos);

    this._base = base;
    this._originalString = s;
    this._mantissa = m;
    this._exponent2 = e2;
    this._exponent5 = e5;
  }


  /**
   * Constructor for an artificial literal of given value.
   *
   * @param i
   */
  public NumLiteral(int i)
  {
    this(SourcePosition.builtIn, Integer.toString(i), 10, BigInteger.valueOf(i), 0, 0);

    if (PRECONDITIONS) require
      (i >= 0);

  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * Was this declared with a '.'?  If so, the preferred type is f64, not
   * i32/i64.
   */
  private boolean hasDot()
  {
    return _originalString.contains(".");
  }


  /**
   * Create new constant by flipping the sign.
   */
  public NumLiteral neg(SourcePosition pos)
  {
    var o = _originalString;
    var s = o.startsWith("-") ? o.substring(1) : "-" + o;
    return new NumLiteral(pos, s, _base, _mantissa.negate(), _exponent2, _exponent5);
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
        var i = intValue(ConstantType.ct_i32);
        if (i == null)
          {
            type_ = Types.resolved.t_f64;
          }
        else if (ConstantType.ct_i32.canHold(i))
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
   * Get this value if it is an integer. In case the value is above/below
   * +/-2^max, the result might get replaced by 2^max.
   *
   * @return the integer represented by this,
   */
  public BigInteger intValue()
  {
    return intValue(findConstantType(typeOrNull()));
  }


  /**
   * Get this value if it is an integer. In case the value is above/below
   * +/-2^max, the result might get replaced by 2^max.
   *
   * @return the integer represented by this,
   */
  private BigInteger intValue(ConstantType ct)
  {
    var max = ct.bits();
    var v = _mantissa;
    var e2 = _exponent2;
    var e5 = _exponent5;
    if (e2 < 0 || e5 < 0)
      {
        return null;
      }
    else if (v.signum() == 0)
      {
        return v;
      }
    else if (e2 > 256 || e5 > 128)
      {
        return BigInteger.valueOf(2).pow(256);
      }
    else
      {
        while (e2 > 0)
          {
            v = v.multiply(BigInteger.valueOf(2));
            e2 = e2 - 1;
          }
        while (e5 > 0)
          {
            v = v.multiply(BigInteger.valueOf(5));
            e5 = e5 - 1;
          }
        return v;
      }
  }


  /**
   * Check that this constant is in the range allowed for its type_.
   */
  void checkRange()
  {
    if (PRECONDITIONS) require
      (findConstantType(type_) != null);

    ConstantType ct = findConstantType(type_);
    var i = intValue(ct);
    if (i == null)
      {
        FeErrors.nonWholeNumberUsedAsIntegerConstant(pos(),
                                                     _originalString,
                                                     type_);
      }
    else if (!ct.canHold(i))
      {
        FeErrors.integerConstantOutOfLegalRange(pos(),
                                                _originalString,
                                                type_,
                                                toString(ct._min),
                                                toString(ct._max));
      }
  }


  /*
   * Convert given value to a string using _base. Prepend "0x" or "0b" for hex
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
      switch (_base)
        {
        case  2 -> "0b";
        case 10 -> "";
        case 16 -> "0x";
        default -> throw new Error("unexpected base: " + _base);
        };
    return sign + prefix + v.toString(_base);
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
    var ct = findConstantType(type_);
    var i = intValue(ct);
    var b = i.toByteArray();
    var bytes = ct._bytes;
    var result = new byte[bytes];
    for (var ix = 0; ix < bytes; ix++)
      {
        if (ix >= b.length)
          {
            result[ix] = (byte) (_mantissa.signum() < 0 ? 0xff : 0x00);
          }
        else
          {
            result[ix] = b[b.length - 1 - ix];
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
