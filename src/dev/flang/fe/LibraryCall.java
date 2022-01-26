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
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Types;
import dev.flang.ast.Universe;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * A LibraryCall represents a Call loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class LibraryCall extends AbstractCall
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
  private final int _select;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryCall
   */
  LibraryCall(LibraryModule lib, int index, Stack<Expr> s)
  {
    _libModule = lib;
    _index = index;
    _type = lib.callType(index);
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
            var t = _libModule.type(tp);
            g.add(t);
            tp = _libModule.typeNextPos(tp);
          }
      }
    Collections.reverse(actuals);
    _actuals = actuals;
    _generics = g;
    Expr target = null;
    var feat = lib.callCalledFeature(index);
    var f = lib.libraryFeature(feat);
    if (f.outer().isUniverse())
      {
        target = new Universe();
      }
    else
      {
        target = s.pop();
      }
    _target = target;
    _calledFeature = f;
    _select = f.resultType().isOpenGeneric() ? lib.callSelect(index) : -1;
  }


  /*-----------------------------  methods  -----------------------------*/


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
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    var i = generics().listIterator();
    while (i.hasNext())
      {
        i.set(i.next().visit(v, outer));
      }
    var j = actuals().listIterator(); // _actuals can change during resolveTypes, so create iterator early
    while (j.hasNext())
      {
        j.set(j.next().visit(v, outer));
      };
    if (target() != null)
      {
        var t = target().visit(v, outer);
        if (CHECKS) check
          (target() == t);
      }
    v.action(this);
    return this;
  }



  public List<AbstractType> generics() { return _generics; }
  public AbstractFeature calledFeature() { return _calledFeature; }
  public Expr target() { return _target; }
  public List<Expr> actuals() { return _actuals; }
  public int select() { return _select; }
  public boolean isDynamic()
  {
    return calledFeature().isDynamic() && !(target() instanceof AbstractCurrent);
  }
  boolean _isInheritanceCall = false;
  public boolean isInheritanceCall()
  {
    return _isInheritanceCall;
  }
  public AbstractType typeOrNull()
  {
    return _type;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    var t = target();
    return (t == null //||
             //t instanceof Universe
            ? ""
            : t.toString() + ".")
      + calledFeature().featureName().baseName()
      + (generics().isEmpty() ? "" : "<" + generics() + ">")
      + (actuals().isEmpty() ? "" : "(" + actuals() +")")
      //+ (select() < 0        ? "" : "." + select())
      ;
  }


}

/* end of file */
