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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCase;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Box;
import dev.flang.ast.Cond;
import dev.flang.ast.Constant;
import dev.flang.ast.Context;
import dev.flang.ast.Contract;
import dev.flang.ast.Env;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Tag;
import dev.flang.ast.Types;
import dev.flang.ast.Universe;
import dev.flang.ast.Visi;
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
   * Index of this feature within _libModule._data.
   *
   * This index is unique for features within _libModule, i.e., not unique
   * globally.
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

  /**
   * cached result of modulesOfInnerFeatures()
   */
  private Set<LibraryModule> _modulesOfInnerFeatures = null;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create LibraryFeature
   *
   * @param lib the module this was defined in
   *
   * @param index index within lib where this was defined.
   */
  LibraryFeature(LibraryModule lib, int index)
  {
    _libModule = lib;
    _index = index;
    _kind = lib.featureKindEnum(index);

    var tf = existingCotype();
    if (tf != null)
      {
        // NYI: HACK: This is somewhat ugly, would be nicer if the type feature
        // in the fum file would contain a reference to the origin such that we
        // do not need to patch this into the type feature's field.
        tf._cotypeOrigin = this;
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Unique global index of this feature.
   */
  public int globalIndex()
  {
    return _libModule.globalIndex(_index);
  }


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
  public boolean isRef()
  {
    return _libModule.featureIsThisRef(_index);
  }


  /**
   * Visibility of this feature
   */
  public Visi visibility()
  {
    return _libModule.featureVisibilityEnum(_index);
  }

  /**
   * the modifiers of this feature
   */
  public int modifiers()
  {
    return
      (_libModule.featureIsFixed       (_index)     ? FuzionConstants.MODIFIER_FIXED    : 0) |
      (_libModule.featureRedefinesCount(_index) > 0 ? FuzionConstants.MODIFIER_REDEFINE : 0);

  }


  @Override
  public boolean isUniverse()
  {
    return this == _libModule.libraryUniverse();
  }


  /**
   * Find the outer feature of this feature.
   */
  public AbstractFeature outer()
  {
    AbstractFeature result = _outer;
    if (result == null && !isUniverse())
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
        for (var j = 0; j < i.size() && j < n; j++)
          {
            _arguments.add(i.get(j));
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
        var n = arguments().size();
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
        var n = arguments().size() + (hasResultField() ? 1 : 0);
        result = i.get(n);
        _outerRef = result;
      }

    if (CHECKS) check
      (hasOuterRef() == (result != null));

    return result;
  }


  /**
   * If we have an existing type feature (store in a .fum library file), return that
   * type feature. return null otherwise.
   */
  public AbstractFeature existingCotype()
  {
    return _libModule.featureHasCotype(_index) ? _libModule.featureCotype(_index) : null;
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
      (isRoutine() || isAbstract() || isIntrinsic() || isChoice() || isField() || isTypeParameter());

    var o = outer();
    var ot = o == null ? null : o.selfType();
    AbstractType result = new NormalType(_libModule, -1, this,
                                         isRef() ? FuzionConstants.MIR_FILE_TYPE_IS_REF
                                                     : FuzionConstants.MIR_FILE_TYPE_IS_VALUE,
                                         generics().asActuals(), ot);

    if (POSTCONDITIONS) ensure
      (result != null,
       Errors.any() || result.isRef() == isRef(),
       // does not hold if feature is declared repeatedly
       Errors.any() || result.feature() == this);

    return result;
  }


  /**
   * type of this Expr. Since LibraryFature is no longer an expression, this
   * will cause an error.
   *
   * NYI: CLEANUP: AbstractFeature should best not inherit from Expr. Instead,
   * it should be sufficient if dev.flang.ast.Feature does.
   */
  @Override
  public AbstractType type()
  {
    throw new Error("LibraryFeature.type() called");
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
        return selfType();
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
   * The sourcecode position of this feature declaration's result type, null if
   * not available.
   */
  public SourcePosition resultTypePos()
  {
    return null; // NYI: UNDER DEVELOPMENT: resultTypePos currently missing in module file
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
            var gi = globalIndex();
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
        _pos = pos(_libModule.featurePosition(_index), _libModule.featurePositionEnd(_index));
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


  /**
   * The implementation of this feature.
   *
   * requires isRoutine() == true
   */
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
        code(at, s, -1, -1);
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
        res = code(at, s, -1, -1);
        if (CHECKS) check
          (s.size() == 0 || s.peek().type().isVoid());
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
  AbstractBlock code(int at, Stack<Expr> s, int pos, int posEnd)
  {
    var l = new List<Expr>();
    var sz = _libModule.codeSize(at);
    var eat = _libModule.codeExpressionsPos(at);
    var e = eat;
    while (e < eat+sz)
      {
        var k = _libModule.expressionKind(e);
        var iat = _libModule.expressionExprPos(e);
        pos = _libModule.expressionHasPosition(e) ? _libModule.expressionPosition(e) : pos;
        posEnd = _libModule.expressionHasPosition(e) ? _libModule.expressionPositionEnd(e) : posEnd;
        var fpos = pos;
        var fposEnd = posEnd;
        Expr c = null;
        Expr x = null;
        switch (k)
          {
          case Assign:
            {
              var field = _libModule.assignField(iat);
              var f = _libModule.libraryFeature(field);
              var target = f.outer().isUniverse() ? new Universe() : s.pop();
              var val = s.pop();
              c = new AbstractAssign(f, target, val)
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
              break;
            }
          case Box:
            {
              var t = _libModule.boxType(iat);
              x = new Box(s.pop(), t)
                {
                  public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); }
                };
              break;
            }
          case Const:
            {
              var t = _libModule.constType(iat);
              var d = _libModule.constData(iat);
              x = new Constant()
                {
                  @Override public AbstractType type() { return t; }
                  @Override public byte[] data() { return d; }
                  @Override public Expr visit(FeatureVisitor v, AbstractFeature af) { v.action(this); return this; };
                  @Override public String toString() { return "LibraryFeature.Constant of type "+type(); }
                  @Override public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); }
                };
              break;
            }
          case Current:
            {
              x = new AbstractCurrent(selfType().asThis())
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
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
                  public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); }
                };
              break;
            }
          case Call:
            {
              x = new LibraryCall(_libModule, iat, s)
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
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
              x = new Tag(val, taggedType, Context.NONE);
              break;
            }
          case Env:
            {
              var envType = _libModule.envType(iat);
              x = new Env(LibraryModule.DUMMY_POS, envType)
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
              break;
            }
          case Unit:
            {
              x = new AbstractBlock(new List<>())
                { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
              break;
            }
          case InlineArray:
            {
              var codePos   = _libModule.inlineArrayCodePos(iat);
              var elCount   = _libModule.inlineArrayCodeElementCount(iat);
              var elCodePos = _libModule.inlineArrayCodeElementCodePos(iat);

              var el = new List<Expr>();
              var code = code(codePos, new Stack<Expr>(), -1, -1);

              for (int index = 0; index < elCount; index++)
                {
                  var st = new Stack<Expr>();
                  var elCode = code(elCodePos, st, -1, -1);
                  el.add(st.isEmpty() ? elCode._expressions.get(0) : st.pop());
                  elCodePos = _libModule.codeNextPos(elCodePos);
                }

              x = new InlineArray(LibraryFeature.this.pos(fpos, fposEnd), el, code);
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
                  { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
                l = new List<>();
              }
            s.push(x);
          }
        else if (c != null)
          {
            l.add(c);
          }
        e = (c instanceof AbstractMatch m && m.type().isVoid())
          // stop, see #2345
          ? Integer.MAX_VALUE
          : _libModule.expressionNextPos(e);
      }
    var fpos = pos;
    var fposEnd = posEnd;
    return new AbstractBlock(l)
      { public SourcePosition pos() { return LibraryFeature.this.pos(fpos, fposEnd); } };
  }


  /**
   * Create a Source position instance for the given position in this library file.
   *
   * @param pos the position, may be -1 for undefined or 0 for
   * SourcePosition.builtIn, otherwise a valid index into a source file in this
   * module.
   */
  private SourcePosition pos(int pos, int posEnd)
  {
    return _libModule.pos(pos, posEnd);
  }


  public Contract contract()
  {
    return Contract.EMPTY_CONTRACT;
  }

  @Override
  public AbstractFeature preFeature()
  {
    return _libModule.featurePreFeature(_index);
  }
  @Override
  public AbstractFeature preBoolFeature()
  {
    return _libModule.featurePreBoolFeature(_index);
  }
  @Override
  public AbstractFeature preAndCallFeature()
  {
    return _libModule.featurePreAndCallFeature(_index);
  }

  @Override
  public AbstractFeature postFeature()
  {
    return _libModule.featurePostFeature(_index);
  }


  /**
   * All features that have been found to be directly redefined by this feature.
   * This does not include redefinitions of redefinitions.  For Features loaded
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
    int result;
    if (other instanceof Feature)
      {
        result = -1;
      }
    else if (other instanceof LibraryFeature lf)
      {
        result = globalIndex() - lf.globalIndex();
      }
    else
      {
        throw new Error("LibraryFeature.compareTo expects that there are only two subclasses: Feature and LibraryFeature.");
      }
    return result;
  }

  /**
   * Union of the library modules of all inner features. Checks inner features recursively.
   * @return immutable set with all library modules for which an inner feature exists
   */
  public Set<LibraryModule> modulesOfInnerFeatures()
  {
    if (_modulesOfInnerFeatures == null)
      {
        _modulesOfInnerFeatures = new TreeSet<LibraryModule>(Comparator.comparingInt(System::identityHashCode));

        var declaredOrInherited = new LinkedList<AbstractFeature>();
        _libModule.forEachDeclaredOrInheritedFeature(this, f -> declaredOrInherited.add(f));

        var libFeatures = declaredOrInherited.stream()
                           .map(f -> (LibraryFeature) f)
                           .collect(Collectors.toList());

        for (LibraryFeature lf : libFeatures)
        {
            // modules of inner features
            _modulesOfInnerFeatures.add( lf._libModule );
            // for inner features recursively add modules their inner features
            _modulesOfInnerFeatures.addAll( lf.modulesOfInnerFeatures() );
          }
      }

    return Collections.unmodifiableSet(_modulesOfInnerFeatures);
  }

  /**
   * Does this feature belong to or contain inner features of the given module?
   * And should therefore be shown on the api page for that module
   * @param module the module for which the belonging is to be checked
   * @return true iff this feature needs to be included in the api page for module
   */
  public boolean showInMod(LibraryModule module)
  {
    // Problem: all features inherit from any, which is in base
    // therefore all features from other modules would be shown in base module because they always have an inner feature from base
    if (module.name().equals("base"))
      {
        return _libModule == module || isUniverse();
      }
    else
      {
        return _libModule == module || modulesOfInnerFeatures().contains(module);
      }
  }

}



/* end of file */
