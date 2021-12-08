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

import java.nio.charset.StandardCharsets;

import dev.flang.util.SourcePosition;


/**
 * StrConst <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class StrConst extends Constant
{

  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public String str;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param s
   */
  public StrConst(SourcePosition pos, String s)
  {
    super(pos);
    this.str = s.substring(1,s.length()-1);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeOrNull returns the type of this expression or null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public AbstractType typeOrNull()
  {
    return Types.resolved.t_string;
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
  public StrConst visit(FeatureVisitor v, Feature outer)
  {
    // nothing to be done for a constant
    return this;
  }


  /**
   * Serialized form of the data of this constant.
   */
  public byte[] data()
  {
    return str.getBytes(StandardCharsets.UTF_8);
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "\""+str+"\"";
  }

}

/* end of file */
