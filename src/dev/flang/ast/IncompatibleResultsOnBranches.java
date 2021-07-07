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
 * Source of class IncompatibleResultTypeError
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * IncompatibleResultsOnBranches creates informative error messages for branching
 * statements like "if" or "match" in case the different branches produce
 * incompatible results.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class IncompatibleResultsOnBranches extends ANY
{


  /*----------------------------  variables  ----------------------------*/

  /**
   * The different types in source code order.
   */
  private List<Type> types_ = new List<>();

  /**
   * For each type, a list of expressions from different branches that produce
   * this type.
   */
  private TreeMap<Type, List<SourcePosition>> positions_ = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to create error message for incompatible types in branches.
   *
   * @param pos the source position
   *
   * @param msg the main, one line error message
   *
   * @param it an iterator over the expressions that produce the results in the
   * different branches.  Should be in source-code order.
   */
  public IncompatibleResultsOnBranches(SourcePosition pos,
                                       String msg,
                                       Iterator<Expr> it)
  {
    while (it.hasNext())
      {
        add(it.next());
      }
    check
      (types_.size() > 1);
    StringBuilder typesMsg = new StringBuilder();
    for (Type t : types_)
      {
        List<SourcePosition> l = positions_.get(t);
        typesMsg.append(( l.size() == 1 ? "block returns" : "blocks return") + " value of type " + t + " at ");
        boolean first = true;
        for (SourcePosition p : l)
          {
            typesMsg.append((first ? "" : "and at ")+ p.show() + "\n");
            first = false;
          }
      }
    Errors.error(pos,
                 msg,
                 "Incompatible result types in different branches:\n" +
                 typesMsg);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add the given expression to types_ and positions_.
   */
  private void add(Expr e)
  {
    Type t = e.type();
    List<SourcePosition> l = positions_.get(t);
    if (l == null)
      {
        types_.add(t);
        l = new List<>();
        positions_.put(t, l);
      }
    l.add(e.posOfLast());
  }

}

/* end of file */
