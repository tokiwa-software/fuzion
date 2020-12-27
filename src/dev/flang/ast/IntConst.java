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
 * Tokiwa GmbH, Berlin
 *
 * Source of class IntConst
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * IntConst <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class IntConst extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public long l;


  /**
   * The constant as it appeared in the source code.
   */
  private String fromSource;


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
  public IntConst(SourcePosition pos, String s)
  {
    super(pos);
    this.fromSource = s;
    try
      {
        this.l = Long.parseUnsignedLong(s);
      }
    catch (NumberFormatException e)
      {
        e.printStackTrace();
      }
  }


  /**
   * Constructor
   *
   * @param i
   */
  public IntConst(long l)
  {
    super(SourcePosition.builtIn);
    this.fromSource = "" + l; // NYI: needed?
    this.l = l;
  }


  /*-----------------------------  methods  -----------------------------*/


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
        type_ = (l == (int) l) ? Types.resolved.t_i32
                               : Types.resolved.t_i64;
      }
    return type_;
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
    if (type_ == null)
      {
        if      (t == Types.resolved.t_i32) { Integer.parseInt         (fromSource); type_ = t; }
        else if (t == Types.resolved.t_u32) { Integer.parseUnsignedInt (fromSource); type_ = t; }
        else if (t == Types.resolved.t_i64) { Long   .parseLong        (fromSource); type_ = t; }
        else if (t == Types.resolved.t_u64) { Long   .parseUnsignedLong(fromSource); type_ = t; }
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
  public IntConst visit(FeatureVisitor v, Feature outer)
  {
    // nothing to be done for a constant
    return this;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return Long.toUnsignedString(l);
  }

}

/* end of file */
