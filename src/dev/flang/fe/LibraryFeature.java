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
import java.nio.file.Path;

import java.util.Collection;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCase;
import dev.flang.ast.AbstractConstant;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.BoolConst;
import dev.flang.ast.Box;
import dev.flang.ast.Cond;
import dev.flang.ast.Contract;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.FormalGenerics;
import dev.flang.ast.Generic;
import dev.flang.ast.Impl;
import dev.flang.ast.Stmnt;
import dev.flang.ast.Tag;
import dev.flang.ast.Type;
import dev.flang.ast.Types;
import dev.flang.ast.Unbox;

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
   * Unique index of this feature.
   */
  final int _index;


  /**
   * cached result of kind()
   */
  private final Kind _kind;


  /**
   * cached result of featureName()
   */
  private FeatureName _featureName;


  /**
   * cached result of generics():
   */
  private FormalGenerics _generics;


  /**
   * cached result of contract()
   */
  Contract _contract;


  /**
   * cached result of innerFeatures()
   */
  List<AbstractFeature> _innerFeatures;


  /**
   * cached result of outer()
   */
  AbstractFeature _outer = null;


  /**
   * cached result of arguments()
   */
  List<AbstractFeature> _arguments = null;


  /**
   * Cached result of pos()
   */
  private SourcePosition _pos = null;


  /**
   * cached result of code()
   */
  private Expr _code;


  /**
   * cached result of resultField()
   */
  private AbstractFeature _resultField;


  /**
   * cached result of outerRef()
   */
  private AbstractFeature _outerRef;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryFeature
   */
  LibraryFeature(LibraryModule lib, int index)
  {
    _libModule = lib;
    _index = index;
    _kind = lib.featureKindEnum(index);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * What is this Feature's kind?
   *
   * @return Routine, Field, Intrinsic, Abstract or Choice.
   */
  public Kind kind()
  {
    return _kind;
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
        result = _libModule.featureOuter(_index);
        _outer = result;
      }

    return result;
  }


  /**
   * The inner features declared within this feature's module file.
   */
  List<AbstractFeature> innerFeatures()
  {
    var result = _innerFeatures;
    if (result == null)
      {
        var i = _libModule.featureInnerFeaturesPos(_index);
        result = _libModule.innerFeatures(i);
        _innerFeatures = result;
      }
    return result;
  }


  /**
   * The features declared within this feature.
   */
  List<AbstractFeature> declaredFeatures()
  {
    return innerFeatures();
  }


  /**
   * The formal arguments of this feature
   */
  public List<AbstractFeature> arguments()
  {
    if (_arguments == null)
      {
        _arguments = new List<AbstractFeature>();
        var i = innerFeatures();
        var n = _libModule.featureArgCount(_index);
        while (_arguments.size() < n)
          {
            var a = i.get(_arguments.size());
            _arguments.add(a);
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
    AbstractFeature result = _resultField;
    if (result == null && hasResultField())
      {
        var i = innerFeatures();
        var n = _libModule.featureArgCount(_index);
        result = i.get(n);
        _resultField = result;
      }

    if (CHECKS) check
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
    AbstractFeature result = _outerRef;
    if (result == null && hasOuterRef())
      {
        var i = innerFeatures();
        var n = _libModule.featureArgCount(_index) + (hasResultField() ? 1 : 0);
        result = i.get(n);
        _outerRef = result;
      }

    if (CHECKS) check
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
        var i = innerFeatures();
        result = i.get(0);  // first entry is outer ref.
      }

    if (CHECKS) check
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
    var i = innerFeatures();
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
    if (result == null)
      {
        Errors.fatal("Could not find feature '"+name+"' in '" + qualifiedName() + "'.");
      }
    return result;
  }


  /**
   * createThisType returns a new instance of the type of this feature's frame
   * object.  This can be called even if !hasThisType() since thisClazz() is
   * used also for abstract or intrinsic feature to determine the resultClazz().
   *
   * @return this feature's frame object
   */
  public AbstractType createThisType()
  {
    if (PRECONDITIONS) require
      (isRoutine() || isAbstract() || isIntrinsic() || isChoice() || isField());

    var o = outer();
    var ot = o == null ? null : o.thisType();
    AbstractType result = new NormalType(_libModule, -1, this, this, Type.RefOrVal.LikeUnderlyingFeature, generics().asActuals(), ot);

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.count() > 0 || result.isRef() == isThisRef(),
       // does not hold if feature is declared repeatedly
       Errors.count() > 0 || result.featureOfType() == this,
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
        return _libModule.type(_libModule.featureResultTypePos(_index));
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
        else
          {
            var tai = _libModule.featureTypeArgsPos(_index);
            var n = _libModule.typeArgsCount(tai);
            if (n > 0)
              {
                var list = new List<Generic>();
                var isOpen = _libModule.typeArgsOpen(tai);
                var tali = _libModule.typeArgsListPos(tai);
                var i = 0;
                while (i < n)
                  {
                    var gn = _libModule.typeArgName(tali);
                    var gp = LibraryModule.DUMMY_POS;
                    var tali0 = tali;
                    var i0 = i;
                    var g = new Generic(gp, i, gn, null)
                      {
                        public AbstractType constraint()
                        {
                          return _libModule.typeArgConstraint(tali0);
                        }
                        public String toString()
                        {
                          return gn;
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
        _generics = result;
      }
    return result;
  }


  public FeatureName featureName()
  {
    var result = _featureName;
    if (result == null)
      {
        var bytes = _libModule.featureName(_index);
        var ac = _libModule.featureArgCount(_index);
        var id = _libModule.featureId(_index);
        if (bytes.length == 0)
          {
            var gi = _libModule.globalIndex(_index);
            result = FeatureName.get(gi, ac, id);
          }
        else
          {
            var bn = new String(bytes, StandardCharsets.UTF_8);
            result = FeatureName.get(bn, ac, id);
          }
        _featureName = result;
      }
    return result;
  }


  /**
   * Source code position where this feature was declared.
   */
  public SourcePosition pos()
  {
    if (_pos == null)
      {
        _pos = pos(_libModule.featurePosition(_index));
      }
    return _pos;
  }


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
            ((LibraryCall) p)._isInheritanceCall = true;
            _inherits.add(p);
            ip = _libModule.codeNextPos(ip);
          }
      }
    return _inherits;
  }


  // following used in MIR or later
  public Expr code()
  {
    if (PRECONDITIONS) require
      (isRoutine());

    var result = _code;
    if (result == null)
      {
        var c = _libModule.featureCodePos(_index);
        result = code(c);
        _code = result;
      }
    return result;
  }


  /**
   * Convert code at given offset in _libModule to an ast.Expr
   */
  Expr code1(int at)
  {
    var res = _libModule._code1.get(at);
    if (res == null)
      {
        var s = new Stack<Expr>();
        code(at, s, -1);
        if (CHECKS) check
          (s.size() == 1);
        res = s.pop();
        _libModule._code1.put(at, res);
      }
    return res;
  }


  /**
   * Convert code at given offset in _libModule to an ast.Expr
   */
  Expr code(int at)
  {
    var res = _libModule._code.get(at);
    if (res == null)
      {
        var s = new Stack<Expr>();
        res = code(at, s, -1);
        if (CHECKS) check
          (s.size() == 0);
        _libModule._code.put(at, res);
      }
    return res;
  }


  /**
   * Convert code at given offset in _libModule to an ast.Expr
   *
   * @param at position of this code section
   *
   * @param s the stack of expressions
   *
   * @param pos the current source code position from earlier code, -1 if none.
   */
  AbstractBlock code(int at, Stack<Expr> s, int pos)
  {
    var l = new List<Stmnt>();
    var sz = _libModule.codeSize(at);
    var eat = _libModule.codeExpressionsPos(at);
    var e = eat;
    while (e < eat+sz)
      {
        var k = _libModule.expressionKind(e);
        var iat = _libModule.expressionExprPos(e);
        pos = _libModule.expressionHasPosition(e) ? _libModule.expressionPosition(e) : pos;
        var fpos = pos;
        Expr ex = null;
        Stmnt c = null;
        Expr x = null;
        switch (k)
          {
          case Assign:
            {
              var field = _libModule.assignField(iat);
              var f = _libModule.libraryFeature(field);
              var target = s.pop();
              var val = s.pop();
              c = new AbstractAssign(f, target, val)
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
              break;
            }
          case Unbox:
            {
              var fx = s.pop();
              x = new Unbox(fx, _libModule.unboxType(iat))
                { public SourcePosition pos() { return fx.pos(); } };
              ((Unbox)x)._needed = _libModule.unboxNeeded(iat);
              break;
            }
          case Box:
            {
              x = new Box(s.pop())
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
              break;
            }
          case Const:
            {
              var t = _libModule.constType(iat);
              var d = _libModule.constData(iat);
              x = new AbstractConstant()
                {
                  public AbstractType typeOrNull() { return t; }
                  public byte[] data() { return d; }
                  public Expr visit(FeatureVisitor v, AbstractFeature af) { return this; };
                  public String toString() { return "LibraryFeature.Constant of type "+type(); }
                  public SourcePosition pos() { return LibraryFeature.this.pos(fpos); }
                };
              break;
            }
          case Current:
            {
              x = new AbstractCurrent(thisType())
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
              break;
            }
          case Match:
            {
              var subj = s.pop();
              var n = _libModule.matchNumberOfCases(iat);
              var cat = _libModule.matchCasesPos(iat);
              var cases = new List<AbstractCase>();
              for (var i = 0; i < n; i++)
                {
                  var cn = _libModule.caseNumTypes(cat);
                  var cf = (cn == -1) ? _libModule.libraryFeature(_libModule.caseField(cat)) : null;
                  List<AbstractType> ts = null;
                  if (cn != -1)
                    {
                      ts = new List<>();
                      var tat = _libModule.caseTypePos(cat);
                      for (var ci = 0; ci < cn; ci++)
                        {
                          ts.add(_libModule.type(tat));
                          tat = _libModule.typeNextPos(tat);
                        }
                    }
                  var cc = code(_libModule.caseCodePos(cat));
                  var fts = ts;
                  var lc = new AbstractCase(null)
                    {
                      public SourcePosition pos() { return LibraryModule.DUMMY_POS; /* NYI: Need proper position */ }
                      public AbstractFeature field() { return cf; }
                      public List<AbstractType> types() { return fts; }
                      public AbstractBlock code() { return (AbstractBlock) cc; }
                      public String toString() { return "LibraryFeature.AbstractCase"; }
                    };
                  cases.add(lc);
                  cat = _libModule.caseNextPos(cat);
                }
              c = new AbstractMatch()
                {
                  public Expr subject() { return subj; }
                  public List<AbstractCase> cases() { return cases; }
                  public String toString() { return "LibraryFeature.AbstractMatch"; }
                  public SourcePosition pos() { return LibraryFeature.this.pos(fpos); }
                };
              break;
            }
          case Call:
            {
              x = new LibraryCall(_libModule, iat, s)
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
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
              var taggedType = _libModule.tagType(iat);
              x = new Tag(val, taggedType);
              break;
            }
          case Unit:
            {
              x = new AbstractBlock(new List<>())
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
              break;
            }
          default: throw new Error("Unexpected expression kind: " + k);
          }
        if (x != null)
          {
            if (CHECKS) check
              (c == null);
            if (!l.isEmpty())
              {
                l.add(x);
                x = new AbstractBlock(l)
                  { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
                l = new List<>();
              }
            s.push(x);
          }
        else if (c != null)
          {
            l.add(c);
          }
        e = _libModule.expressionNextPos(e);
      }
    var fpos = pos;
    return new AbstractBlock(l)
      { public SourcePosition pos() { return LibraryFeature.this.pos(fpos); } };
  }


  /**
   * Create a Source position instance for the given position in this library file.
   *
   * @param pos the position, may be -1 for undefined or 0 for
   * SourcePosition.builtIn, otherwise a valid index into a source file in this
   * module.
   */
  private SourcePosition pos(int pos)
  {
    return _libModule.pos(pos);
  }


  /**
   * Read a list a n conditions at given position in _libModule.
   */
  private List<Cond> condList(int n, int at)
  {
    var result = new List<Cond>();
    for (var i = 0; i < n; i++)
      {
        var x = code1(at);
        result.add(new Cond(x));
        at = _libModule.codeNextPos(at);
      }
    return result;
  }


  public Contract contract()
  {
    if (_contract == null)
      {
        var pre_n  = _libModule.featurePreCondCount (_index);
        var post_n = _libModule.featurePostCondCount(_index);
        var inv_n  = _libModule.featureInvCondCount (_index);
        if (pre_n == 0 && post_n == 0 && inv_n == 0)
          {
            _contract = Contract.EMPTY_CONTRACT;
          }
        else
          {
            _contract = new Contract(condList(pre_n , _libModule.featurePreCondPos (_index)),
                                     condList(post_n, _libModule.featurePostCondPos(_index)),
                                     condList(inv_n , _libModule.featureInvCondPos (_index)));
          }
      }
    return _contract;
  }


  /**
   * All features that have been found to be directly redefined by this feature.
   * This does not include redefintions of redefinitions.  Four Features loaded
   * from source code, this set is collected during RESOLVING_DECLARATIONS.  For
   * LibraryFeature, this will be loaded from the library module file.
   */
  private Set<AbstractFeature> _redefines;
  public Set<AbstractFeature> redefines()
  {
    if (_redefines == null)
      {
        _redefines = new TreeSet<>();
        var n = _libModule.featureRedefinesCount(_index);
        var ip = _libModule.featureRedefinesPos(_index);
        for (var i = 0; i < n; i++)
          {
            var r = _libModule.libraryFeature(_libModule.featureRedefine(_index, i));
            _redefines.add(r);
          }
      }
    return _redefines;
  }


  /**
   * Compare this to other for sorting Features
   */
  public int compareTo(AbstractFeature other)
  {
    return (other instanceof Feature)
      ? -1
      : _index - ((LibraryFeature) other)._index;  // there are only two subclasses: Feature and LibraryFeature.
  }


}

/* end of file */
