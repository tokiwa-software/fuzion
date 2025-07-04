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

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;

import java.math.BigInteger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * NumLiteral
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class NumLiteral extends Constant
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
    ct_f64 (53, 11),
    ct_numeric(false, 4);
    // ct_f128 (113, 15),   -- NYI: support for f128
    // ct_f256 (237, 19),   -- NYI: support for f256

    /**
     * bytes in memory occupied by this type
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
     * # of bytes occupied by this type
     */
    int bytes()
    {
      return _bytes;
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
   * The expected type of this constant that was propagated into this Expr from
   * what this is assigned to.
   */
  private AbstractType _propagatedType;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
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
    if (CHECKS) check
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
    if (CHECKS) check
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
   * Create new constant by adding the given sign to a NumLiteral that so far
   * did not have a sign.
   *
   * @param sign the new sign
   *
   * @param signPos the source code position of the sign, must be directly
   * before this NumLiterals position.
   */
  public NumLiteral addSign(String sign, SourcePosition signPos)
  {
    if (PRECONDITIONS) require
      (sign.equals("+") || sign.equals("-"),
       !_originalString.startsWith("+") && !_originalString.startsWith("-"));

    var s = sign + _originalString;
    var newPos = new SourceRange(signPos._sourceFile, signPos.bytePos(), pos().byteEndPos());
    return new NumLiteral(newPos, s, _base, _mantissa, _exponent2, _exponent5);
  }


  /**
   * Create new constant by removing the added sign.
   *
   * @return a NumLiteral equal to the original one {@code addSign} was called on.
   */
  public NumLiteral stripSign()
  {
    if (PRECONDITIONS) require
      (explicitSign() != null);

    var s = _originalString.substring(1);
    var newPos = new SourceRange(pos()._sourceFile, pos().bytePos()+1, pos().byteEndPos());
    return new NumLiteral(newPos, s, _base, _mantissa, _exponent2, _exponent5);
  }


  /**
   * If this NumLiteral has an explicit sign as in {@code +127} or {@code -128}, return that
   * sign as a String, return null otherwise.
   *
   * @return "+", "-", or null,
   */
  public String explicitSign()
  {
    return
      _originalString.startsWith("+") ? "+" :
      _originalString.startsWith("-") ? "-"
                                     : null;
  }


  /**
   * Get the source code position of the explicit sign.
   */
  public SourceRange signPos()
  {
    if (PRECONDITIONS) require
      (explicitSign() != null);

    return new SourceRange(pos()._sourceFile, pos().bytePos(), pos().bytePos()+1);
  }


  /**
   * Is this negative?
   */
  private boolean signBit()
  {
    return _originalString.startsWith("-");
  }


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
    var result = _propagatedType;
    if (result == null)
      {
        var i = hasDot() ? null : intValue();
        result = i == null
          ? Types.resolved.t_f64
          : Types.resolved.t_i32;
      }
    return result;
  }


  /**
   * typeForUnion returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForUnion()
  {
    return null;
  }


  /**
   * During type inference: Inform this expression that it is
   * expected to result in the given type.
   *
   * @param t the expected type.
   */
  @Override
  protected void propagateExpectedType(AbstractType t)
  {
    if (PRECONDITIONS) require
      (_propagatedType == null || extractNumericType(t) == null || _propagatedType.compareTo(extractNumericType(t)) == 0);

    _propagatedType = extractNumericType(t);
  }


  private AbstractType extractNumericType(AbstractType t)
  {
    var result = t
      .choices(Context.NONE)
      .filter(x -> Types.resolved.numericTypes.contains(x))
      .collect(Collectors.toList());

    return result.size() == 1
      ? result.get(0)
      : null;
  }


  /**
   * Get this value if it is an integer. In case the value is above/below
   * +/-2^max, the result might get replaced by 2^max.
   *
   * @return the integer represented by this,
   */
  public BigInteger intValue()
  {
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
        return signBit() ? v.negate() : v;
      }
  }


  /**
   * Get the data in little endian order for a float
   */
  private byte[] floatBits()
  {
    ConstantType ct = findConstantType(type());

    if (CHECKS) check
      (ct._isFloat);

    var res = new byte[ct._bytes];
    var m = _mantissa;
    var f = B0;
    if (m.signum() != 0)
      {
        var e5 = _exponent5;
        var e2 = _exponent2;

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
        // rounding changed mantissa length => shift again
        if((m.bitLength() == ct._mBits + 1))
          {
            m = shiftWithRounding(m, -1);
            e2 = e2 + 1;
          }
        if (CHECKS) check
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
            AstErrors.floatConstantTooLarge(pos(),
                                           _originalString,
                                           type(),
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
                AstErrors.floatConstantTooSmall(pos(),
                                               _originalString,
                                               type(),
                                               min, minH);
              }
          }
        else
          {
            var high1 = B1.shiftLeft(ct._mBits-1);
            if (CHECKS) check
              (m.and(high1).signum() != 0);
            m = m.andNot(high1);
          }
        f = (signBit() ? B1 : B0)    .shiftLeft(ct._eBits  )
          .or(BigInteger.valueOf(e2)).shiftLeft(ct._mBits-1)
          .or(m);
      }
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
    return res;
  }


  /**
   * Helper routine to shift BigInteger v left (sh &gt; 0) or right (sh &lt; 0) and
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
        var result = v.add(roundingBit).shiftRight(-sh);
        if (_exponent5 == 0 && !result.shiftLeft(-sh).equals(v))
          {
            AstErrors.lossOfPrecision(pos(), _originalString, _base, type());
          }
        return result;
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
      (type().compareTo(Types.resolved.t_f32) == 0);

    return ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN).getFloat();
  }

  /**
   * Get the value of this f64 constant
   */
  public double f64Value()
  {
    if (PRECONDITIONS) require
      (type().compareTo(Types.resolved.t_f64) == 0);

    return ByteBuffer.wrap(data()).order(ByteOrder.LITTLE_ENDIAN).getDouble();
  }


  /**
   * Check that this constant is in the range allowed for its type_.
   */
  @Override
  void checkRange()
  {
    if (PRECONDITIONS) require
      (findConstantType(type()) != null);

    ConstantType ct = findConstantType(type());
    if (ct._isFloat)
      {
        floatBits();
      }
    else
      {
        var i = intValue();
        if (i == null)
          {
            AstErrors.nonWholeNumberUsedAsIntegerConstant(pos(),
                                                         _originalString,
                                                         type());
          }
        else if (!ct.canHold(i))
          {
            AstErrors.integerConstantOutOfLegalRange(pos(),
                                                    _originalString,
                                                    type(),
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
   * Check if type t is one of the known numeric types i8, i16, i32, i64, u8,
   * u16, u32, u64, f32, f64 and return the corresponding ConstantType constant.
   *
   * @param t an interned type
   *
   * @return the corresponding ConstantType or null if none.
   */
  private static ConstantType findConstantType(AbstractType t)
  {
    if      (t.compareTo(Types.resolved.t_i8 ) == 0) { return ConstantType.ct_i8 ; }
    else if (t.compareTo(Types.resolved.t_i16) == 0) { return ConstantType.ct_i16; }
    else if (t.compareTo(Types.resolved.t_i32) == 0) { return ConstantType.ct_i32; }
    else if (t.compareTo(Types.resolved.t_i64) == 0) { return ConstantType.ct_i64; }
    else if (t.compareTo(Types.resolved.t_u8 ) == 0) { return ConstantType.ct_u8 ; }
    else if (t.compareTo(Types.resolved.t_u16) == 0) { return ConstantType.ct_u16; }
    else if (t.compareTo(Types.resolved.t_u32) == 0) { return ConstantType.ct_u32; }
    else if (t.compareTo(Types.resolved.t_u64) == 0) { return ConstantType.ct_u64; }
    else if (t.compareTo(Types.resolved.t_f32) == 0) { return ConstantType.ct_f32; }
    else if (t.compareTo(Types.resolved.t_f64) == 0) { return ConstantType.ct_f64; }
    else                                             { return t.isGenericArgument() ? ConstantType.ct_numeric : null; }
  }


  /**
   * Perform partial application for a NumLiteral. In particular, this converts
   * a literal with a sign such as {@code -2} into a lambda of the form {@code x -> x - 2}.
   *
   * @see Expr#propagateExpectedTypeForPartial for details.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   */
  @Override
  Expr propagateExpectedTypeForPartial(Resolution res, Context context, AbstractType t)
  {
    Expr result;
    if (t.isFunctionTypeExcludingLazy() && t.arity() == 1 && explicitSign() != null)
      { // convert `map -1` into `map x->x-1`
        var pns = new List<Expr>();
        pns.add(Partial.argName(pos()));
        result = new Function(pos(),
                              pns,
                              new ParsedCall(pns.get(0),                                  // target #p<n>
                                             new ParsedName(signPos(),
                                                            FuzionConstants.INFIX_OPERATOR_PREFIX +
                                                            explicitSign()),              // `infix +` or `infix -`
                                             new List<>(stripSign())));                   // constant w/o sign
      }
    else
      {
        result = super.propagateExpectedTypeForPartial(res, context, t);
      }
    return result;
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
   * @param context the source code context where this Expr is used
   *
   * @param t the expected type.
   *
   * @param from for error output: if non-null, produces a String describing
   * where the expected type came from.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the expression that reads the field.
   */
  Expr propagateExpectedType(Resolution res, Context context, AbstractType t, Supplier<String> from)
  {
    // if expected type is choice, examine if there is exactly one numeric
    // constant type in choice generics, if so use that for further type
    // propagation.
    t = t.findInChoice(cg -> !cg.isGenericArgument() && findConstantType(cg) != null, context);
    if (_propagatedType == null && findConstantType(t) != null)
      {
        _propagatedType = t;
      }
    return super.propagateExpectedType(res, context, t, from);
  }


  /**
   * After propagateExpectedType: if type inference up until now has figured
   * out that a Lazy feature is expected, but the current expression is not
   * a Lazy feature, then wrap this expression in a Lazy feature.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Expr is used
   *
   * @param t the type this expression is assigned to.
   */
  @Override
  Expr wrapInLazy(Resolution res, Context context, AbstractType t)
  {
    if (t.isLazyType())
      {
        propagateExpectedType(res, context, t.generics().get(0), null);
      }
    return super.wrapInLazy(res, context, t);
  }


  /**
   * Get the little-endian representation of this constant.
   *
   * @return an array with length findConstantType(type_)._bytes containing the
   * constant as a little-endian unsigned or two's complement value.
   */
  public byte[] data()
  {
    var ct = findConstantType(type());
    byte[] result;
    if (ct._isFloat)
      {
        result = floatBits();
      }
    else
      {
        var i = intValue();
        var b = i.toByteArray();
        var bytes = ct._bytes;
        result = new byte[bytes];
        for (var ix = 0; ix < bytes; ix++)
          {
            if (ix >= b.length)
              {
                result[ix] = (byte) (i.signum() < 0 ? 0xff : 0x00);
              }
            else
              {
                result[ix] = b[b.length - 1 - ix];
              }
          }
      }
    var bb = ByteBuffer.wrap(new byte[4+result.length]).order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(ct._bytes);
    bb.put(result);
    return bb.array();
  }


  @Override
  protected Expr resolveSyntacticSugar2(Resolution res, Context _context)
  {
    if (_propagatedType != null && _propagatedType.isGenericArgument())
      {
        Call result;

        if (_originalString.equals("0"))
          {
            result = new ParsedCall(
                          new ParsedCall(new ParsedName(pos(), _propagatedType.genericArgument().featureName().baseName())),
                          new ParsedName(pos(), "zero"))
                  .resolveTypes(res, _context);
          }
        else if (_originalString.equals("1"))
          {
            result = new ParsedCall(
                          new ParsedCall(new ParsedName(pos(), _propagatedType.genericArgument().featureName().baseName())),
                          new ParsedName(pos(), "one"))
                  .resolveTypes(res, _context);
          }
        else
          {
            result = new ParsedCall(
                      new ParsedCall(new ParsedName(pos(), _propagatedType.genericArgument().featureName().baseName())),
                      new ParsedName(pos(), "from_u32"),
                      new List<>(this))
                  .resolveTypes(res, _context);
          }

        _propagatedType = Types.resolved.t_u32;
        return result;
      }
    else
      {
        return this;
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
