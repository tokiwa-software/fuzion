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
 * Source of class FList
 *
 *---------------------------------------------------------------------*/

package dev.flang.parser;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.Contract;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Impl;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Type;
import dev.flang.ast.Visi;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * FList is a list of declared features with the same name that exists during
 * parsing only.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FList extends ANY implements Stmnt
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The features in this list
   */
  final List<Feature> _list = new List<Feature>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param v
   *
   * @param m
   *
   * @param r
   *
   * @param qnames
   *
   * @param g
   *
   * @param a
   *
   * @param i
   *
   * @param c
   *
   * @param p
   */
  public FList(SourcePosition pos,
               Visi v,
               int m,
               ReturnType r,
               List<List<String>> qnames,
               FormalGenerics g,
               List<Feature> a,
               List<AbstractCall> i,
               Contract c,
               Impl p) {
    for (List<String> n : qnames)
      {
        _list.add(new Feature(pos, v,m,r,n,g,a,i,c,p));
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The soucecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return _list.getFirst().pos();
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return nothing.
   *
   * @throws Error
   */
  public FList visit(FeatureVisitor v, Feature outer)
  {
    throw new Error("FList must be used only during parsing");
  }

  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    throw new Error("FList must be used only during parsing");
  }


  /**
   * feature
   *
   * @param u
   *
   * @return
   */
  public Feature feature()
  {
    Feature result;
    if (_list.size() != 1)
      {
        result = null;
        Errors.error(_list.get(1).pos(),
                     "Source file must define exactly one feature, not " + _list.size()+".",
                     "Additional features must be declared as inner features or in separate files.");
      }
    else
      {
        result = _list.removeFirst();
        result.setDefinedInOwnFile();
      }
    return result;
  }


}

/* end of file */
