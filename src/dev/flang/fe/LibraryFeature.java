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
import java.util.Stack;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Assign;
import dev.flang.ast.Block;
import dev.flang.ast.BoolConst;
import dev.flang.ast.Box;
import dev.flang.ast.Contract;
import dev.flang.ast.Current;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.NumLiteral;
import dev.flang.ast.ReturnType;
import dev.flang.ast.SrcModule;
import dev.flang.ast.Stmnt;
import dev.flang.ast.StrConst;
import dev.flang.ast.Tag;
import dev.flang.ast.Type;
import dev.flang.ast.Types;

import dev.flang.util.Errors;
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
   * cached result of generics():
   */
  private FormalGenerics _generics;


  /**
   * cached result of outer()
   */
  AbstractFeature _outer = null;


  /**
   * cached result of arguments()
   */
  List<AbstractFeature> _arguments = null;

  /**
   * cached result of thisType()
   */
  private AbstractType _thisType;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryFeature
   */
  LibraryFeature(LibraryModule lib, int index, AbstractFeature from)
  {
    _libModule = lib;
    _index = index;
    _from = from;
    if (from != null)
      {
        check
          (from._libraryFeature == null);
        from._libraryFeature = this;
      }

    _kind = lib.featureKind(index) & FuzionConstants.MIR_FILE_KIND_MASK;
    var bytes = lib.featureName(index);
    var ac = lib.featureArgCount(index);
    var id = lib.featureId(index);
    var bn = new String(bytes, StandardCharsets.UTF_8);
    _featureName = FeatureName.get(bn, ac, id);
    if (from != null)
      {
        check(_featureName == _from.featureName());
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind()
  {
    return _libModule.featureKindEnum(_index);
  }

  /**
   * Is this a routine that returns the current instance as its result?
   */
  public boolean isConstructor()
  {
    return _libModule.featureIsConstructor(_index);
  }


  /**
   * Is this a constructor returning a reference result?
   */
  public boolean isThisRef()
  {
    return _libModule.featureIsThisRef(_index);
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
      (_libModule.USE_FUM || result.astFeature() == _from.outer().astFeature());

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
    var sz = _libModule.innerFeaturesSize(at);
    check
      (at+4+sz <= _libModule.data().limit());
    var i = _libModule.innerFeaturesFeaturesPos(at);
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
                var inner = _libModule.featureInnerFeaturesPos(i);
                result = findOuter(o, inner);
                i = _libModule.featureNextPos(i);
              }
          }
      }
    return result;
  }


  /**
   * The features declared within this feature.
   */
  List<AbstractFeature> declaredFeatures()
  {
    var i = _libModule.featureInnerFeaturesPos(_index);
    return _libModule.innerFeatures(i);
  }


  /**
   * The formal arguments of this feature
   */
  public List<AbstractFeature> arguments()
  {
    if (_arguments == null)
      {
        _arguments = new List<AbstractFeature>();
        if (LibraryModule.USE_FUM)
          {
            var i = _libModule.innerFeatures(_libModule.featureInnerFeaturesPos(_index));
            var n = _libModule.featureArgCount(_index);
            while (_arguments.size() < n)
              {
                var a = i.get(_arguments.size());
                _arguments.add(a);
              }
          }
        else
          {
            var i = _libModule.featureInnerPos(_index);
            var n = _libModule.featureArgCount(_index);
            while (_arguments.size() < n)
              {
                var a = _libModule.libraryFeature(i, (Feature) _from.arguments().get(_arguments.size()).astFeature());
                _arguments.add(a);
                i = _libModule.featureNextPos(i);
              }
          }
      }
    return _arguments;
  }


  /**
   * The result field declared automatically in case hasResultField().
   *
   * @return the result or null if this does not have a result field.
   */
  public AbstractFeature resultField()
  {
    AbstractFeature result = null;
    if (hasResultField())
      {
        if (LibraryModule.USE_FUM)
          {
            var i = _libModule.innerFeatures(_libModule.featureInnerFeaturesPos(_index));
            var n = _libModule.featureArgCount(_index);
            result = i.get(n);
          }
        else
          {
            var i = _libModule.featureInnerPos(_index);
            var n = _libModule.featureArgCount(_index);
            var c = 0;
            while (c < n)
              {
                c++;
                i = _libModule.featureNextPos(i);
              }
            result = _libModule.libraryFeature(i, (Feature) _from.resultField());
          }
      }

    check
      (hasResultField() == (result != null));

    return result;
  }


  /**
   * The outer ref field field in case hasOuterRef().
   *
   * @return the outer ref or null if this does not have an outer ref.
   */
  public AbstractFeature outerRef()
  {
    AbstractFeature result = null;
    if (hasOuterRef())
      {
        if (LibraryModule.USE_FUM)
          {
            var i = _libModule.innerFeatures(_libModule.featureInnerFeaturesPos(_index));
            var n = _libModule.featureArgCount(_index) + (hasResultField() ? 1 : 0);
            result = i.get(n);
          }
        else
          {
            var i = _libModule.featureInnerPos(_index);
            var n = _libModule.featureArgCount(_index) + (hasResultField() ? 1 : 0);
            var c = 0;
            while (c < n)
              {
                c++;
                i = _libModule.featureNextPos(i);
              }
            result = _libModule.libraryFeature(i, (Feature) _from.outerRef());
          }
      }

    check
      (hasOuterRef() == (result != null));

    return result;
  }


  /**
   * For choice feature (i.e., isChoice() holds): The tag field that holds in
   * i32 that identifies the index of the actual generic argument to choice that
   * is represented.
   *
   * @return the choice tag or null if this !isChoice().
   */
  public AbstractFeature choiceTag()
  {
    AbstractFeature result = null;
    if (isChoice())
      {
        if (LibraryModule.USE_FUM)
          {
            var i = _libModule.innerFeatures(_libModule.featureInnerFeaturesPos(_index));
            result = i.get(0);
          }
        else
          {
            var i = _libModule.featureInnerPos(_index);
            result = _libModule.libraryFeature(i, (Feature) _from.choiceTag());
          }
      }

    check
      (isChoice() == (result != null));

    return result;
  }


  /**
   * Get inner feature with given name, ignoring the argument count.
   *
   * @param name the name of the feature within this.
   *
   * @return the found feature or null in case of an error.
   */
  public AbstractFeature get(String name)
  {
    AbstractFeature result = null;
    if (LibraryModule.USE_FUM)
      {
        var i = _libModule.innerFeatures(_libModule.featureInnerFeaturesPos(_index));
        for (var r : i)
          {
            var rn = r.featureName();
            if (rn.baseName().equals(name))
              {
                if (result == null)
                  {
                    result = r;
                  }
                else
                  {
                Errors.fatal("Ambiguous inner feature '" + name + "': found '" + result.featureName() + "' and '" + r.featureName() + "'.");
                  }
              }
          }
      }
    else
      {
        var sz = _libModule.featureInnerSize(_index);
        var i = _libModule.featureInnerPos(_index);
        var e = i + sz;
        while  (i < e)
          {
            var r = _libModule.libraryFeature(i, _libModule._srcModule.featureFromOffset(i));
            var rn = r.featureName();
            if (rn.baseName().equals(name))
              {
                if (result == null)
                  {
                    result = r;
                  }
                else
                  {
                    Errors.fatal("Ambiguous inner feature '" + name + "': found '" + result.featureName() + "' and '" + r.featureName() + "'.");
                  }
              }
            i = _libModule.featureNextPos(i);
          }
      }
    if (result == null)
      {
        Errors.fatal("Could not find feature '"+name+"' in '" + qualifiedName() + "'.");
      }
    return result;
  }


  /**
   * thisType returns the type of this feature's frame object.  This type exists
   * even for features the do not have a frame such as abstracts, intrinsics,
   * choice or field.
   *
   * @return this feature's frame object
   */
  public AbstractType thisType()
  {
    if (PRECONDITIONS) require
      (isRoutine() || isAbstract() || isIntrinsic() || isChoice() || isField());

    AbstractType result = _thisType;
    if (result == null)
      {
        // NYI: Remove creation of ast.Type here:
        result = new Type(pos(), featureName().baseName(), generics().asActuals(), null, this, Type.RefOrVal.LikeUnderlyingFeature);

        result = new NormalType(_libModule, -1, pos(), this, Type.RefOrVal.LikeUnderlyingFeature, generics().asActuals(), result.outer(), result);
        _thisType = result;
      }

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.count() > 0 || result.isRef() == isThisRef(),
       // does not hold if feature is declared repeatedly
       Errors.count() > 0 || result.featureOfType().sameAs(this),
       true || // this condition is very expensive to check and obviously true:
       result == Types.intern(result)
       );

    return result;
  }


  /**
   * resultType returns the result type of this feature using.
   *
   * @return the result type. Never null.
   */
  public AbstractType resultType()
  {
    if (isConstructor())
      {
        return thisType();
      }
    else if (isChoice())
      {
        return Types.resolved.t_void;
      }
    else
      {
        var from = _from != null ? _from.resultType() : null;
        var fromPos = from != null ? from.pos() : LibraryModule.DUMMY_POS;
        return _libModule.type(_libModule.featureResultTypePos(_index), fromPos, from);
      }
  }


  /**
   * The formal generic arguments of this feature
   */
  public FormalGenerics generics()
  {
    var result = _generics;
    if (result == null)
      {
        if ((_libModule.featureKind(_index) & FuzionConstants.MIR_FILE_KIND_HAS_TYPE_PAREMETERS) == 0)
          {
            result = FormalGenerics.NONE;
          }
        else if (_libModule.USE_FUM)
          {
            var tai = _libModule.featureTypeArgsPos(_index);
            var list = new List<Generic>();
            var n = _libModule.typeArgsCount(tai);
            if (n > 0)
              {
                var isOpen = _libModule.typeArgsOpen(tai);
                var tali = _libModule.typeArgsListPos(tai);
                var i = 0;
                while (i < n)
                  {
                    var gn = _libModule.typeArgName(tali);
                    var gp = LibraryModule.USE_FUM ? LibraryModule.DUMMY_POS : _from.generics().list.get(i)._pos; // NYI: pos of generic
                    var tali0 = tali;
                    var i0 = i;
                    var g = new Generic(gp, i, gn, null)
                      {
                        public AbstractType constraint()
                        {
                          return _libModule.typeArgConstraint(tali0, gp, _from instanceof LibraryFeature ? null : _from.generics().list.get(i0).constraint());
                        }
                      };
                    list.add(g);
                    tali = _libModule.typeArgNextPos(tali);
                    i++;
                  }
                result = new FormalGenerics(list, isOpen, this);
              }
            else
              {
                result = FormalGenerics.NONE;
              }
          }
        else
          {
            result = _from.generics();
          }
        _generics = result;
      }
    return result;
  }


  public FeatureName featureName()
  {
    return _featureName;
  }
  public SourcePosition pos() { return _from == null ? LibraryModule.DUMMY_POS : _from.pos(); }
  List<AbstractCall> _inherits = null;
  public List<AbstractCall> inherits()
  {
    if (_inherits == null)
      {
        _inherits = new List<>();
        var n = _libModule.featureInheritsCount(_index);
        var ip = _libModule.featureInheritsPos(_index);
        for (var i = 0; i < n; i++)
          {
            var p = (AbstractCall) code1(ip);
            _inherits.add(p);
            ip = _libModule.codeNextPos(ip);
          }
      }
    if (_libModule.USE_FUM)
      {
        return _inherits;
      }
    else
      {
        return _from.inherits();
      }
  }

  // following are used in IR/Clazzes middle end or later only:
  public Impl.Kind implKind() { if (_libModule.USE_FUM) { check(false); return _from.implKind(); } else { return _from.implKind(); } }      // NYI: remove, used only in Clazz.java for some obscure case
  public Expr initialValue() { if (_libModule.USE_FUM) { check(false); return null; } else { return _from.initialValue(); } }   // NYI: remove, used only in Clazz.java for some obscure case

  // following used in MIR or later
  public Expr code()
  {
    if (PRECONDITIONS) require
      (isRoutine());
    if (true)
      {
        var c = _libModule.featureCodePos(_index);
        var result = code(c);
        if (_libModule.USE_FUM && result != null)
          {
            return result;
          }
      }
    if (_libModule.USE_FUM)
      {
        check(false); return null;
      }
    else
      {
        return _from.code();
      }
  }


  /**
   * Convert code at given offset in _libModule to an ast.Expr
   */
  Expr code1(int at)
  {
    var s = new Stack<Expr>();
    code(at, s);
    check
      (s.size() == 1);
    return s.pop();
  }


  /**
   * Convert code at given offset in _libModule to an ast.Expr
   */
  Expr code(int at)
  {
    var s = new Stack<Expr>();
    var result = code(at, s);
    check
      (s.size() == 0);
    return result;
  }


  /**
   * Convert code at given offset in _libModule to an ast.Expr
   */
  Block code(int at, Stack<Expr> s)
  {
    var l = new List<Stmnt>();
    var sz = _libModule.codeSize(at);
    var eat = _libModule.codeExpressionsPos(at);
    var e = eat;
    while (e < eat+sz)
      {
        var k = _libModule.expressionKind(e);
        var iat = e + 1;
        Expr ex = null;
        Stmnt c;
        switch (k)
          {
          case Assign:
            {
              var field = _libModule.assignField(iat);
              var f = _libModule.libraryFeature(field, null);
              var target = s.pop();
              var val = s.pop();
              c = new Assign(LibraryModule.DUMMY_POS, f, target, val);
              break;
            }
          case Unbox:
            {
              var val = s.pop();
              // NYI: type
              if (_libModule.USE_FUM) System.out.println("NYI: Unbox");
              c = null;
              s.push(null);
              break;
            }
          case Box:
            {
              var val = s.pop();
              // NYI: type
              c = null;
              s.push(new Box(val));
              break;
            }
          case Const:
            {
              var t = _libModule.constType(iat);
              var d = _libModule.constData(iat);
              Expr r;
              if (t.compareTo(Types.resolved.t_bool) == 0)
                {
                  r = d[0] == 0 ? BoolConst. FALSE : BoolConst.TRUE;
                }
              else if (t.compareTo(Types.resolved.t_string) == 0)
                {
                  var str = new String(d, StandardCharsets.UTF_8);
                  r = new StrConst(pos(), str, false);
                }
              else
                { // NYI: Numeric
                  r = new NumLiteral(4711); // NYI!
                }
              c = null;
              s.push(r);
              break;
            }
          case Current:
            {
              var r = new Current(LibraryModule.DUMMY_POS, thisType());
              c = null;
              s.push(r);
              break;
            }
          case Match:
            {
              var subj = s.pop();
              var n = _libModule.matchNumberOfCases(iat);
              var cat = _libModule.matchCasesPos(iat);
              for (var i = 0; i < n; i++)
                {
                  code(cat);
                  cat = _libModule.matchCaseNextPos(cat);
                }
              if (_libModule.USE_FUM) System.out.println("NYI: Match");
              c = null;
              break;
            }
          case Call:
            {
              var r = new LibraryCall(_libModule, iat, s);
              c = null;
              s.push(r);
              break;
            }
          case Pop:
            {
              c = s.pop();
              break;
            }
          case Tag:
            {
              var val = s.pop();
              // NYI: tag
              var taggedType = Types.resolved.t_unit; // NYI!
              c = null;
              s.push(new Tag(val, taggedType));
              break;
            }
          case Unit:
            {
              c = null;
              s.push(new Block(LibraryModule.DUMMY_POS, new List<>()));
              break;
            }
          default: throw new Error("Unexpected expression kind: " + k);
          }
        if (c != null)
          {
            l.add(c);
          }
        e = _libModule.expressionNextPos(e);
      }
    return new Block(LibraryModule.DUMMY_POS, l);
  }


  // in FUIR or later
  public Contract contract()
  {
    if (_libModule.USE_FUM)
      {
        if (true)
          // NYI: return a dummy contract until contracts are saved to the module file
          return new dev.flang.ast.Contract(null, null, null);
        check(false); return null;
      }
    else
      {
        return _from.contract();
      }
  }

  public AbstractFeature astFeature() { return _libModule.USE_FUM ? this : _from; }

}

/* end of file */
