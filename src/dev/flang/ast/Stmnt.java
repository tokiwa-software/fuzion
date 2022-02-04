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
 * Source of interface Stmnt
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.SourcePosition;


/**
 * Stmnt <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface Stmnt
{


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this statment, used for error messages.
   */
  public SourcePosition pos();


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Stmnt if the action performed during the
   * visit replaces this by the alternative.
   */
  public Stmnt visit(FeatureVisitor v, AbstractFeature outer);


  /**
   * visit all the statements within this Stmnt.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  default void visitStatements(StatementVisitor v)
  {
    v.action(this);
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  boolean containsOnlyDeclarations();


}

/* end of file */
