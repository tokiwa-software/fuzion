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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    // ct_f16 (11, 5),   -- NYI: support for f16
    ct_f32 (24, 8),
    ct_f64 (53, 11);
    // ct_f128 (113, 15),   -- NYI: support for f128
    // ct_f256 (237, 19),   -- NYI: support for f256

    /**
     * bytes in memory occuplied by this type
     */
    final int _bytes;

    /**
     * float or integer type?
     */
    final boolean _isFloat;

    /**
     * for integers: allowed range
     */
    final BigInteger _min, _max;

    /**
     * for float: length of mantissa (including leading '1' bit)
     */
    final int _mBits;

    /**
     * for float: length of exponent
     */
    final int _eBits;

    /**
     * Constructor for integer
     */
    ConstantType(boolean signed, int bytes)
    {
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
      _isFloat = false;
      _mBits = -1;
      _eBits = -1;
    }

    /**
     * Constructor for float
     */
    ConstantType(int mBits, int eBits)
    {
      _bytes = (mBits + eBits) / 8;
      _mBits = mBits;
      _eBits = eBits;
      _isFloat = true;
      _min = null;
      _max = null;
    }

    /**
     * # of bits occupied by this type
     */
    int bits()
    {
      return _bytes*8;
    }

    /**
     * check if this can hold an integer of the given value
     */
    boolean canHold(BigInteger value)
    {
      if (PRECONDITIONS) require
        (!_isFloat);

      return
        _min.compareTo(value) <= 0 &&
        _max.compareTo(value) >= 0;
    }
  }


  /**
   * Convenience BitInteger values:
   */
  static BigInteger B0 = BigInteger.valueOf(0);
  static BigInteger B1 = BigInteger.valueOf(1);
  static BigInteger B2 = BigInteger.valueOf(2);
  static BigInteger B5 = BigInteger.valueOf(5);
  static BigInteger B10 = BigInteger.valueOf(10);


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

    while (v.signum() != 0 && v.mod(B2).signum() == 0)
      {
        v = v.divide(B2);
        e2 = e2+1;
      }
    while (v.signum() != 0 && v.mod(B5).signum() == 0)
      {
        v = v.divide(B5);
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
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  {
    if (type_ == null)
      {
        var i = hasDot() ? null : intValue(ConstantType.ct_i32);
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
    return intValue(findConstantType(type()));
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
        return B2.pow(256);
      }
    else
      {
        while (e2 > 0)
          {
            v = v.multiply(B2);
            e2 = e2 - 1;
          }
        while (e5 > 0)
          {
            v = v.multiply(B5);
            e5 = e5 - 1;
          }
        return v;
      }
  }


  /**
   * Get the data in little endian order for a float
   */
  private byte[] floatBits()
  {
    ConstantType ct = findConstantType(type_);

    check
      (ct._isFloat);

    var res = new byte[ct._bytes];
    var m = _mantissa;
    var s = m.signum();
    if (s != 0)
      {
        var e5 = _exponent5;
        var e2 = _exponent2;
        m = s > 0 ? m : m.negate();

        // incorporate e5 into m/e2:
        if (e5 > 0)
          { // for positive e5, multiply m with 5^e5
            m = m.multiply(B5.pow(e5));
          }
        else  if (e5 < 0)
          { // for negative e5, divide m by 5^(-e5), but ensure we have enough result bits
            var div5 = B5.pow(-e5);
            // make sure m has at least mBits after division
            var extraBits = ct._mBits + 1 - (m.bitLength() - div5.bitLength());
            if (extraBits > 0)
              {
                m = m.shiftLeft(extraBits);
                e2 = e2 - extraBits;
              }
            m = m.divide(div5);
          }

        // make sure m's bitLength is exactly mBits
        var deltaBits = ct._mBits - m.bitLength();
        m = shiftWithRounding(m, deltaBits);
        e2 = e2 - deltaBits;
        check
          (m.bitLength() == ct._mBits);

        // add bias to e2 and handle overflow and denormalized numbers
        var eBias    = (1 << (ct._eBits-1))-1;
        var eSpecial = (1 << ct._eBits) -1;
        e2 = e2 + eBias + ct._mBits -1;
        if (e2 >= eSpecial)  // exponent too large
          {
            // NYI: Determination of max float value is a little clumsy:
            var mmax = B1.shiftLeft(ct._mBits).subtract(B1);
            var bmax = mmax.shiftLeft(eSpecial-eBias-ct._mBits);
            var ndigits = 0;
            while (B10.pow(ndigits).subtract(mmax).signum() < 0)
              {
                ndigits++;
              }
            ndigits += 1;
            var d = bmax.toString();
            var max = d.charAt(0) + "." + d.substring(1,ndigits) + "E" + (d.length()-1);
            var maxH = "0x1P" + (eBias + ct._mBits - 1);
            FeErrors.floatConstantTooLarge(pos(),
                                           _originalString,
                                           type_,
                                           max, maxH);
            e2 = eSpecial; // +/- infinity
            m = B0;
          }
        else if (e2 <= 0)
          { // denormalized
            m = shiftWithRounding(m, e2-1);
            e2 = 0;
            if (m.signum() == 0)
              {
                // NYI: Determination of min float value is a little clumsy:
                var minE2 = -eBias-ct._mBits+2;
                var b5min  = B5 .pow(-minE2).toString();
                var b10min = B10.pow(-minE2).toString();
                var exp = b5min.length() - b10min.length();
                var min = b5min.charAt(0) + "." + b5min.charAt(1) + "E" + exp;
                var minH = "0x1P" + minE2;
                FeErrors.floatConstantTooSmall(pos(),
                                               _originalString,
                                               type_,
                                               min, minH);
              }
          }
        else
          {
            var high1 = B1.shiftLeft(ct._mBits-1);
            check
              (m.and(high1).signum() != 0);
            m = m.andNot(high1);
          }
        var f = BigInteger.valueOf((2-s)/2) .shiftLeft(ct._eBits  )
          .or(  BigInteger.valueOf(e2     )).shiftLeft(ct._mBits-1)
          .or(m);
        var b = f.toByteArray();
        var l0 = 0L;
        var bl = Math.min(b.length, ct._bytes);
        var bs = Math.max(0, b.length - ct._bytes);
        for (var i = 0; i < bl; i++)
          {
            var bv = b[i+bs];
            l0 = l0 | (((long) bv & 0xff) << ((bl-1-i)*8));
            res[bl-1-i] = bv;
          }
      }
    return res;
  }


  /**
   * Helper routine to shift BigInteger v left (sh > 0) or right (sh < 0) and
   * perform rounding in case of a right shift.
   *
   * @param v a BigInteger value
   *
   * @param sh a shift distance
   *
   * @return v shifted, plus one in case of right shift with the highest lost
   * bit being 1.
   */
  private BigInteger shiftWithRounding(BigInteger v, int sh)
  {
    if (sh > 0)
      {
        return v.shiftLeft(sh);
      }
    else if (sh < 0)
      {
        var roundingBit = B1.shiftLeft(-sh-1);
        return v.add(roundingBit).shiftRight(-sh);
      }
    else
      {
        return v;
      }
  }


  /**
   * Get the value of this f32 constant
   */
  public float f32Value()
  {
    if (PRECONDITIONS) require
      (type() == Types.resolved.t_f32);

    return ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
  }

  /**
   * Get the value of this f64 constant
   */
  public double f64Value()
  {
    if (PRECONDITIONS) require
      (type() == Types.resolved.t_f64);

    return ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN).getDouble();
  }


  /**
   * Check that this constant is in the range allowed for its type_.
   */
  void checkRange()
  {
    if (PRECONDITIONS) require
      (findConstantType(type_) != null);

    ConstantType ct = findConstantType(type_);
    if (ct._isFloat)
      {
        floatBits();
      }
    else
      {
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
        case  8 -> "0o";
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
    if (ct._isFloat)
      {
        return floatBits();
      }
    else
      {
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
