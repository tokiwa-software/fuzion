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
 * Source of class LibraryCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.util.Collections;
import java.util.Stack;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * A LibraryCall represents a Call loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LibraryCall extends AbstractCall
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The library this come from.
   */
  public final LibraryModule _libModule;


  /**
   * index of this feature within _libModule.
   */
  private final int _index;


  private final AbstractType _type;
  private final Expr _target;
  private final List<Expr> _actuals;
  private final List<AbstractType> _generics;
  private final AbstractFeature _calledFeature;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryCall
   */
  LibraryCall(LibraryModule lib, int index, Stack<Expr> s)
  {
    super(LibraryModule.DUMMY_POS);
    _libModule = lib;
    _index = index;
    _type = _libModule.USE_FUM ? lib.callType(index) : null;
    var na = _libModule.callNumArgs(index);
    var actuals = new List<Expr>();
    for (var i = 0; i < na; i++)
      {
        actuals.add(s.pop());
      }
    var ng = _libModule.callNumTypeParameters(index);
    var g = new List<AbstractType>();
    if (ng > 0)
      {
        var tp = _libModule.callTypeParametersPos(index);
        for (var i = 0; i < ng; i++)
          {
            var t = _libModule.type(tp, LibraryModule.DUMMY_POS, null);
            g.add(t);
            tp = _libModule.typeNextPos(tp);
          }
      }
    Collections.reverse(actuals);
    _actuals = actuals;
    _generics = g;
    Expr target = null;
    var feat = lib.callCalledFeature(index);
    var f = lib.libraryFeature(feat, null);
    if (!f.outer().isUniverse())
      {
        target = s.pop();
      }
    _target = target;
    _calledFeature = f;
  }


  /*-----------------------------  methods  -----------------------------*/


  public List<AbstractType> generics() { return _generics; }
  public AbstractFeature calledFeature() { return _calledFeature; }
  public Expr target() { return _target; }
  public List<Expr> actuals() { return _actuals; }
  public int select() {
    if (type().isOpenGeneric())
      {
        return -1;
      }
    else
      {
        throw new Error("NYI");
      }
  }
  public boolean isDynamic() { throw new Error("NYI"); }
  public boolean isInheritanceCall()  { throw new Error("NYI"); }
  public AbstractType typeOrNull()
  {
    return _type;
  }

}

/* end of file */
