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
import dev.flang.ast.AbstractType;
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

import dev.flang.util.FuzionConstants;
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


  /**
   * cached result of kind()
   */
  private final int _kind;


  /**
   * cached result of featureName()
   */
  private final FeatureName _featureName;


  /**
   * cached result of outer()
   */
  AbstractFeature _outer = null;


  /**
   * cached result of arguments()
   */
  List<AbstractFeature> _arguments = null;


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

    _kind = lib.featureKind(index);
    var bytes = lib.featureName(index);
    var ac = lib.featureArgCount(index);
    var id = lib.featureId(index);
    var bn = new String(bytes, StandardCharsets.UTF_8);
    _featureName = FeatureName.get(bn, ac, id);
    check(_featureName == _from.featureName());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind()
  {
    return isConstructor() ? AbstractFeature.Kind.Routine
                           : AbstractFeature.Kind.from(_kind);
  }

  /**
   * Is this a routine that returns the current instance as its result?
   */
  public boolean isConstructor()
  {
    return
      _kind == FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_VALUE ||
      _kind == FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF;
  }


  /**
   * Is this a constructor returning a reference result?
   */
  public boolean isThisRef()
  {
    return _kind == FuzionConstants.MIR_FILE_KIND_CONSTRUCTOR_REF;
  }


  /**
   * Find the outer feature of this festure.
   */
  public AbstractFeature outer()
  {
    var result = _outer;
    if (result == null)
      {
        result = findOuter(_libModule._mir.universe(), FuzionConstants.MIR_FILE_FIRST_FEATURE_OFFSET);
        _outer = result;
      }

    check
      (result.astFeature() == _from.outer().astFeature());

    return result;
  }


  /**
   * Helper method for outer() to find the outer feature of this festure
   * starting with outer which is defined in _libModule.data() at offset 'at'.
   *
   * @param outer the 'current' outer feature that is declared at 'at'
   *
   * @param at the position of outer's inner feature declarations within
   * _libModule.data()
   *
   * @return the outer feature found or null if outer is not an outer feature of
   * this.
   */
  private AbstractFeature findOuter(AbstractFeature outer, int at)
  {
    if (PRECONDITIONS) require
      (outer != null,
       at >= 0,
       at < _libModule.data().limit());

    AbstractFeature result = null;
    var sz = _libModule.data().getInt(at);
    check
      (at+4+sz <= _libModule.data().limit());
    var i = at+4;
    if (i <= _index && _index < i+sz)
      {
        while (result == null)
          {
            if (i == _index)
              {
                result = outer;
              }
            else
              {
                var o = _libModule.libraryFeature(i, _libModule._srcModule.featureFromOffset(i));
                check
                  (o != null);
                var inner = _libModule.featureInnerSizePos(i);
                result = findOuter(o, inner);
                i = _libModule.nextFeaturePos(i);
              }
          }
      }
    return result;
  }

  /**
   * The formal arguments of this feature
   */
  public List<AbstractFeature> arguments()
  {
    if (_arguments == null)
      {
        _arguments = new List<AbstractFeature>();
        var i = _libModule.featureInnerPos(_index);
        var n = _libModule.featureArgCount(_index);
        while (_arguments.size() < n)
          {
            var a = _libModule.libraryFeature(i, (Feature) _from.arguments().get(_arguments.size()).astFeature());
            _arguments.add(a);
            i = _libModule.nextFeaturePos(i);
          }
      }
    return _arguments;
  }

  public FeatureName featureName()
  {
    return _featureName;
  }
  public SourcePosition pos() { return _from.pos(); }
  public List<AbstractType> choiceGenerics() { return _from.choiceGenerics(); }
  public FormalGenerics generics() { return _from.generics(); }
  public Generic getGeneric(String name) { return _from.getGeneric(name); }
  public List<Call> inherits() { return _from.inherits(); }
  public AbstractType thisType() { return _from.thisType(); }
  public FeatureName handDown(Resolution res, AbstractFeature f, FeatureName fn, Call p, AbstractFeature heir) { return _from.handDown(res, f, fn, p, heir); }
  public AbstractType[] handDown(Resolution res, AbstractType[] a, AbstractFeature heir) { return _from.handDown(res, a, heir); }
  public AbstractType resultType() { return _from.resultType(); }
  public boolean inheritsFrom(AbstractFeature parent) { return _from.inheritsFrom(parent); }
  public List<Call> tryFindInheritanceChain(AbstractFeature ancestor) { return _from.tryFindInheritanceChain(ancestor); }
  public List<Call> findInheritanceChain(AbstractFeature ancestor) { return _from.findInheritanceChain(ancestor); }
  public AbstractFeature resultField() { return _from.resultField(); }
  public Collection<AbstractFeature> allInnerAndInheritedFeatures(Resolution res) { return _from.allInnerAndInheritedFeatures(res); }
  public AbstractFeature outerRef() { return _from.outerRef(); }
  public AbstractFeature get(Resolution res, String qname) { return _from.get(res, qname); }
  public AbstractType[] argTypes() { return _from.argTypes(); }

  // following are used in IR/Clazzes middle end or later only:
  public AbstractFeature outerRefOrNull() { return _from.outerRefOrNull(); }
  public void visit(FeatureVisitor v) { _from.visit(v); }
  public boolean isOpenGenericField() { return _from.isOpenGenericField(); }
  public int depth() { return _from.depth(); }
  public Feature choiceTag() { return _from.choiceTag(); }

  public Impl.Kind implKind() { return _from.implKind(); }      // NYI: remove, used only in Clazz.java for some obscure case
  public Expr initialValue() { return _from.initialValue(); }   // NYI: remove, used only in Clazz.java for some obscure case

  // following used in MIR or later
  public Expr code() { return _from.code(); }

  // in FUIR or later
  public Contract contract() { return _from.contract(); }

  public AbstractFeature astFeature() { return _from; }

}

/* end of file */
