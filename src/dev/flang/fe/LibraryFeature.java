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
 * Source of class LibraryFeature
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.nio.charset.StandardCharsets;

import java.util.Collection;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.ast.Contract;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.Resolution;
import dev.flang.ast.ReturnType;
import dev.flang.ast.Type;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * A LibraryFeature represents a Fuzion feature loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LibraryFeature extends AbstractFeature
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


  /**
   * NYI: For now, this is just a wrapper around an AST feature. This should be
   * removed once all data is obtained from _libModule;
   */
  public final AbstractFeature _from;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryFeature
   */
  LibraryFeature(LibraryModule lib, int index, AbstractFeature from)
  {
    _libModule = lib;
    _index = index;
    _from = from;
    check
      (from._libraryFeature == null);
    from._libraryFeature = this;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind() { return _from.kind(); }

  public boolean isOuterRef() { return _from.isOuterRef(); }
  public boolean isThisRef() { return _from.isThisRef(); }
  public boolean isChoiceTag() { return _from.isChoiceTag(); }
  public boolean isDynamic() { return _from.isDynamic(); }
  public boolean isAnonymousInnerFeature() { return _from.isAnonymousInnerFeature(); /* NYI: remove? */ }
  protected boolean isIndexVarUpdatedByLoop() { throw new Error(); }
  public boolean isBuiltInPrimitive() { return _from.isBuiltInPrimitive(); }
  public boolean hasResult() { return _from.hasResult(); }
  public FeatureName featureName()
  {
    var i = _index;
    var d = _libModule.data();
    var l  = d.getInt(i); i = i + 4; var bytes = new byte[l];
    d.get(i, bytes);      i = i + l;
    var ac = d.getInt(i); i = i + 4;
    var id = d.getInt(i); i = i + 4;
    var bn = new String(bytes, 0, l, StandardCharsets.UTF_8);
    var result = FeatureName.get(bn, ac, id);
    check(result == _from.featureName());
    return FeatureName.get(bn, ac, id);
  }
  public SourcePosition pos() { return _from.pos(); }
  public ReturnType returnType() { return _from.returnType(); }
  public List<Type> choiceGenerics() { return _from.choiceGenerics(); }
  public FormalGenerics generics() { return _from.generics(); }
  public Generic getGeneric(String name) { return _from.getGeneric(name); }
  public List<Call> inherits() { return _from.inherits(); }
  public boolean isLastArgType(Type t) { return _from.isLastArgType(t); }
  public AbstractFeature outer() { return _from.outer(); }
  public Type thisType() { return _from.thisType(); }
  public boolean hasOpenGenericsArgList() { return _from.hasOpenGenericsArgList(); }
  public List<AbstractFeature> arguments() { return _from.arguments(); }
  public FeatureName handDown(Resolution res, AbstractFeature f, FeatureName fn, Call p, AbstractFeature heir) { return _from.handDown(res, f, fn, p, heir); }
  public Type[] handDown(Resolution res, Type[] a, AbstractFeature heir) { return _from.handDown(res, a, heir); }
  public AbstractFeature select(Resolution res, int i) { return _from.select(res, i); }
  protected Type resultTypeIfPresent(Resolution res, List<Type> generics) { return resultType(); }
  public Type resultType() { return _from.resultType(); }
  public void checkNoClosureAccesses(Resolution res, SourcePosition errorPos) { _from.checkNoClosureAccesses(res, errorPos); }
  public boolean inheritsFrom(AbstractFeature parent) { return _from.inheritsFrom(parent); }
  public List<Call> tryFindInheritanceChain(AbstractFeature ancestor) { return _from.tryFindInheritanceChain(ancestor); }
  public List<Call> findInheritanceChain(AbstractFeature ancestor) { return _from.findInheritanceChain(ancestor); }
  public AbstractFeature resultField() { return _from.resultField(); }
  public Collection<AbstractFeature> allInnerAndInheritedFeatures(Resolution res) { return _from.allInnerAndInheritedFeatures(res); }
  public AbstractFeature outerRef() { return _from.outerRef(); }
  public boolean isOuterRefAdrOfValue() { return _from.isOuterRefAdrOfValue(); }
  public AbstractFeature get(Resolution res, String qname) { return _from.get(res, qname); }
  public Type[] argTypes() { return _from.argTypes(); }

  // following are used in IR/Clazzes middle end or later only:
  public boolean isOuterRefCopyOfValue() { return _from.isOuterRefCopyOfValue(); }
  public AbstractFeature outerRefOrNull() { return _from.outerRefOrNull(); }
  public void visit(FeatureVisitor v) { _from.visit(v); }
  public boolean isOpenGenericField() { return _from.isOpenGenericField(); }
  public int depth() { return _from.depth(); }
  public int selectSize() { return _from.selectSize(); }
  public Feature select(int i) { return _from.select(i); }
  public Feature choiceTag() { return _from.choiceTag(); }

  public Impl.Kind implKind() { return _from.implKind(); }      // NYI: remove, used only in Clazz.java for some obscure case
  public Expr initialValue() { return _from.initialValue(); }   // NYI: remove, used only in Clazz.java for some obscure case

  // following used in MIR or later
  public Expr code() { return _from.code(); }

  // in FE or later
  public boolean isArtificialField() { return _from.isArtificialField(); }

  // in FUIR or later
  public Contract contract() { return _from.contract(); }

  public AbstractFeature astFeature() { return _from; }

}

/* end of file */
