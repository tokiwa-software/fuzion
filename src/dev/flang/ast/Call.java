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
 * Source of class Call
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Arrays;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.TreeMap;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Call is an expression that is a call to a class and that results in
 * the result value of that class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Call extends AbstractCall
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty actuals lists to distinguish a call without
   * parenthesis ("a.b") from a call with parenthesises and an empty actual
   * arguments list ("a.b()").
   */
  public static final List<Expr> NO_PARENTHESES = new List<>();
  public static final List<Actual> NO_PARENTHESES_A = new List<>();


  /**
   * Empty map for general use.
   */
  public static final SortedMap<FeatureName, Feature> EMPTY_MAP = new TreeMap<>();


  /*------------------------  static variables  -------------------------*/

  /**
   * quick-and-dirty way to get unique values for temp fields in
   * findChainedBooleans.
   */
  static int _chainedBoolTempId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  private final SourcePosition _pos;


  /**
   * name of called feature, set by parser
   */
  private String _name;
  public String name() { return _name; }


  /**
   * For a call a.b.4 with a select clause ".4" to pick a variant from a field
   * of an open generic type, this is the chosen variant.
   */
  final int _select;
  public int select() { return _select; }


  /**
   * actual generic arguments, set by parser
   */
  public /*final*/ List<AbstractType> _generics; // NYI: Make this final again when resolveTypes can replace a call
  public List<AbstractType> generics() { return _generics; }


  /**
   * Actual arguments, set by parser
   */
  public List<Actual> _actualsNew;
  public List<Expr> _actuals;
  public List<Expr> actuals() { return _actuals; }


  /**
   * the target of the call, null for "this". Set by parser
   */
  private Expr _target;
  public Expr target() { return _target; }


  /**
   * The feature that is called by this call, resolved when
   * loadCalledFeature() is called.
   */
  public AbstractFeature _calledFeature;


  /**
   * Static type of this call. Set during resolveTypes().
   */
  AbstractType _type;


  /**
   * forFun specifies iff this call is within a function declaration, e.g., the
   * call to "b" in "x(fun a.b)". In this case, the actual arguments list must
   * be empty, independent of the formal arguments expected by b.
   */
  boolean _forFun = false;


  /**
   * For static type analysis: This gives the resolved formal argument types for
   * the arguemnts of this call.  During type checking, it has to be checked
   * that the actual arguments can be assigned to these types.
   *
   * The number of resolved formal arguments might be different to the number of
   * formal arguments in case the last formal argument is of an open generic
   * type.
   */
  AbstractType[] _resolvedFormalArgumentTypes = null;


  /**
   * Will be set to true for a call to a direct parent feature in an inheritance
   * call.
   */
  public boolean _isInheritanceCall = false;
  public boolean isInheritanceCall() { return _isInheritanceCall; }


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor to read a local field
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   */
  public Call(SourcePosition pos, String n)
  {
    this(pos, null,n,null, null, NO_PARENTHESES);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   *
   * @param a
   */
  public Call(SourcePosition pos, String n, List<Expr> a)
  {
    this(pos, null,n,null, null, a);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n
   *
   * @param g
   *
   * @param a
   */
  public Call(SourcePosition pos, String n, List<AbstractType> g, List<Expr> a)
  {
    this(pos, null,n,g,null,a);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param g
   *
   * @param la list of actual arguments
   *
   * @param a
   */
  public Call(SourcePosition pos,
              Expr t, String n, List<AbstractType> g /* NYI: remove! */, List<Actual> la, List<Expr> a /* NYI: remove! */)
  {
    if (la == NO_PARENTHESES_A)
      {
        a = NO_PARENTHESES;
      }
    if (a == null)
      {
        a = new List<Expr>();
        for (var aa : la)
          {
            a.add(aa._expr);
          }
      }
    else if (la == null)
      {
        la = new List<>();
        for (var ae : a)
          {
            la.add(new Actual(null, ae));
          }
      }
    this._pos = pos;
    this._target = t;
    this._name = n;
    this._select = -1;
    this._generics = (g == null) ? NO_GENERICS : g;
    this._actualsNew = la;
    this._actuals = a;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param a
   */
  public Call(SourcePosition pos,
              Expr t, String n, List<Expr> a)
  {
    this(pos, t, n, null, null, a);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   */
  public Call(SourcePosition pos, Expr t, String n)
  {
    this(pos, t, n, -1);
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param select
   */
  public Call(SourcePosition pos,
              Expr t, String n, String select)
  {
    this._pos = pos;
    this._target = t;
    this._name = n;
    int s = -1;
    try
      {
        s = Integer.parseInt(select);
        if (CHECKS) check
          (s >= 0); // parser should not allow negative value
      }
    catch (NumberFormatException e)
      {
        AstErrors.illegalSelect(pos, select, e);
      }
    this._select = s;
    this._generics = NO_GENERICS;
    this._actuals = NO_PARENTHESES;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param select
   */
  public Call(SourcePosition pos, Expr t, String n, int select)
  {
    this._pos = pos;
    this._target = t;
    this._name = n;
    this._select = select;
    this._generics = NO_GENERICS;
    this._actuals = NO_PARENTHESES;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t
   *
   * @param n
   *
   * @param select
   */
  public Call(SourcePosition pos, Expr t, AbstractFeature calledFeature, int select)
  {
    this._pos = pos;
    this._name = calledFeature.featureName().baseName();
    this._select = select;
    this._generics = NO_GENERICS;
    this._actuals = NO_PARENTHESES;
    this._target = t;
    this._calledFeature = calledFeature;
    this._type = null;
  }


  /**
   * A call to an anonymous feature declared in an expression.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param anonymous the anonymous feature
   */
  public Call(SourcePosition pos,
              Feature anonymous)
  {
    this(pos, new This(pos), anonymous);
  }


  /**
   * A call to an anonymous feature declared using "fun a.b.c".
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param target the target, "a.b".
   *
   * @param anonymous the anonymous feature, which is the wrapper created around
   * the call to "c".
   */
  public Call(SourcePosition pos,
              Expr    target,
              AbstractFeature anonymous)
  {
    this._pos = pos;
    this._target         = target;
    this._name           = null;
    this._select        = -1;
    this._generics       = NO_GENERICS;
    this._actuals       = Expr.NO_EXPRS;
    this._calledFeature = anonymous;
  }


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param name
   *
   * @param generics
   *
   * @param actuals
   *
   * @param target
   *
   * @param calledFeature
   *
   * @param type
   */
  public Call(SourcePosition pos,
              String name, List<AbstractType> generics, List<Expr> actuals, Expr target, AbstractFeature calledFeature, AbstractType type)
  {
    this._pos = pos;
    this._name = name;
    this._select = -1;
    this._generics = generics;
    this._actuals = actuals;
    this._target = target;
    this._calledFeature = calledFeature;
    this._type = type;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The sourcecode position of this expression, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * True iff this call was performed giving 0 or more actual arguments in
   * parentheses.  This allows a disinction between "a.b" and "a.b()" if b has
   * no formal arguments and is of a fun type. In this case, "a.b" calls only b,
   * while "a.b()" is syntactic sugar for "a.b.call".
   *
   * @return true if parentheses were present.
   */
  boolean hasParentheses()
  {
    return _actuals != NO_PARENTHESES;
  }


  /**
   * Get the type of the target.  In case the target's type is a generic type
   * parameter, return its constraint.
   *
   * @return the type of the target.
   */
  private AbstractType targetTypeOrConstraint(Resolution res)
  {
    if (PRECONDITIONS) require
      (_target != null);

    var result = _target.type();
    if (result.isGenericArgument())
      {
        var g = result.genericArgument();
        result = g.constraint().resolve(res, g.feature());
      }

    if (POSTCONDITIONS) ensure
      (!result.isGenericArgument());
    return result;
  }


  /**
   * Get the feature of the target of this call.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * @return the feature of the target of this call.
   */
  private AbstractFeature targetFeature(Resolution res, AbstractFeature thiz)
  {
    // are we searching for features called via thiz' inheritance calls?
    if (thiz.state() == Feature.State.RESOLVING_INHERITANCE)
      {
        if (_target instanceof Call tc)
          {
            _target.loadCalledFeature(res, thiz);
            return tc.calledFeature();
          }
        else
          {
            return thiz.outer();   // For an inheritance call, we do not permit call to thiz' features,
                                   // but only to the outer clazz' features:
          }
      }
    else if (_target != null)
      {
        _target.loadCalledFeature(res, thiz);
        return targetTypeOrConstraint(res).featureOfType();
      }
    else
      { // search for feature in thiz
        return thiz;
      }
  }


  /**
   * Find all the candidates of features that might be called at this point as
   * long as the argument count is ignored.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * @return the map of FeatureName to Features of the found candidates. May be
   * empty. ERROR_MAP in case an error occured and was reported already.
   */
  private FeaturesAndOuter calledFeatureCandidates(AbstractFeature targetFeature, Resolution res, AbstractFeature thiz)
  {
    if (PRECONDITIONS) require
      (targetFeature != null);

    FeaturesAndOuter result;
    // are we searching for features called via thiz' inheritance calls?
    if (_target != null)
      {
        res.resolveDeclarations(targetFeature);
        result = new FeaturesAndOuter();
        result.features = res._module.lookupFeatures(targetFeature, _name);
        result.outer = targetFeature;
      }
    else
      { /* search for feature in thiz and outer classes */
        result = res._module.lookupNoTarget(targetFeature, _name, this, null, null);
        _target = result.target(pos(), res, thiz);
      }
    return result;
  }


  /*-------------------------------------------------------------------*/


  /**
   * if loadCalledFeature is about to fail, try if we can convert this call into
   * a chain of boolean calls:
   *
   * check if we have a call of the form
   *
   *   a < b <= c
   *
   * and convert it to
   *
   *   a < {tmp := b; tmp} && tmp <= c
   */
  private void findChainedBooleans(Resolution res, AbstractFeature thiz)
  {
    var cb = chainedBoolTarget(res, thiz);
    if (cb != null && _actuals.size() == 1)
      {
        var bix = cb._actuals.size() - 1; // index of 'b' in first call 'a < b'
        var b = cb._actuals.get(bix);
        b = res.resolveType(b, thiz);
        String tmpName = FuzionConstants.CHAINED_BOOL_TMP_PREFIX + (_chainedBoolTempId_++);
        var tmp = new Feature(res,
                              pos(),
                              Consts.VISIBILITY_INVISIBLE,
                              b.type(),
                              tmpName,
                              thiz);
        Expr t1 = new Call(pos(), new Current(pos(), thiz.thisType()), tmp, -1);
        Expr t2 = new Call(pos(), new Current(pos(), thiz.thisType()), tmp, -1);
        Expr result = new Call(pos(), t2, _name, _actuals)
          {
            boolean isChainedBoolRHS() { return true; }
          };
        Stmnt as = new Assign(res, pos(), tmp, b, thiz);
        t1 = res.resolveType(t1    , thiz);
        as = res.resolveType(as    , thiz);
        result = res.resolveType(result, thiz);
        cb._actuals.set(cb._actuals.size()-1,
                        new Block(b.pos(),new List<Stmnt>(as, t1)));
        _actuals = new List<Expr>(result);
        _calledFeature = Types.resolved.f_bool_AND;
        _name = _calledFeature.featureName().baseName();
      }
  }



  /**
   * Predicate that is true if this call is the result of pushArgToTemp in a
   * chain of boolean operators.  This is used for longer chains such as
   *
   *   a < b <= c < d
   *
   * which is first converted into
   *
   *   (a < {t1 := b; t1} && t1 <= c) < d
   *
   * where this returns 'true' for the call 't1 <= c', that in the next steps
   * needs to get 'c' stored into a temporary variable as well.
   */
  boolean isChainedBoolRHS() { return false; }


  /**
   * Does this call a non-generic infix operator?
   */
  boolean isInfixOperator()
  {
    return
      _name.startsWith("infix ") &&
      (_actuals.size() == 1 /* normal infix operator 'a.infix + b' */                ||
       _actuals.size() == 2 /* infix on different target 'X.Y.Z.this.infix + a b' */    ) &&
      true; /* no check for _generics.size(), we allow infix operator to infer arbitrary number of type parameters */
  }


  /**
   * Check if this call is a chained boolean call of the form
   *
   *   b <= c < d
   *
   * or, if the LHS is also a chained bool
   *
   *   (a < {t1 := b; t1} && t1 <= c) < d
   *
   * and return the part of the LHS that has the term that will need to be
   * stored in a temp variable, 'c', as an argument, i.e., 'b <= c' or 't1 <=
   * c', resp.
   *
   * @return the term whose RHS would have to be stored in a temp variable for a
   * chained boolean call.
   */
  private Call chainedBoolTarget(Resolution res, AbstractFeature thiz)
  {
    Call result = null;
    if (Types.resolved != null &&
        targetFeature(res, thiz) == Types.resolved.f_bool &&
        isInfixOperator() &&
        _target instanceof Call tc &&
        tc.isInfixOperator())
      {
        result = (tc._actuals.get(0) instanceof Call acc && acc.isChainedBoolRHS())
          ? acc
          : tc;
      }
    return result;
  }


  /*-------------------------------------------------------------------*/


  /**
   * Load all features that are called by this expression.  This is called
   * during state RESOLVING_INHERITANCE for calls in the inherits clauses and
   * during state RESOLVING_TYPES for all other calls.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature. For a call c in an inherits clause ("f
   * : c { }"), thiz is the outer feature of f.  For a expression in the
   * contracts or implementation of a feature f, thiz is f itself.
   *
   * NYI: Check if it might make more sense for thiz to be the declared feature
   * instead of the outer feature when processing an inherits clause.
   */
  void loadCalledFeature(Resolution res, AbstractFeature thiz)
  {
    if (PRECONDITIONS) require
      (thiz.state() == Feature.State.RESOLVING_INHERITANCE
       ? thiz.outer().state().atLeast(Feature.State.RESOLVED_DECLARATIONS)
       : thiz        .state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

    if (_calledFeature == null)
      {
        var actualsResolved = false;
        if (_name != Errors.ERROR_STRING)    // If call parsing failed, don't even try
          {
            var targetFeature = targetFeature(res, thiz);
            if (CHECKS) check
              (Errors.count() > 0 || targetFeature != null);
            if (targetFeature != null && targetFeature != Types.f_ERROR)
              {
                var fo = calledFeatureCandidates(targetFeature, res, thiz);
                FeatureName calledName = FeatureName.get(_name, _actuals.size());
                _calledFeature = fo.filter(pos(), calledName, ff -> mayMatchArgList(ff, false) || ff.hasOpenGenericsArgList() /* remove? */);
                if (_calledFeature != null &&
                    _generics.isEmpty() &&
                    _actuals.size() != _calledFeature.valueArguments().size() &&
                    !_calledFeature.hasOpenGenericsArgList())
                  {
                    splitOffTypeArgs(thiz);
                  }
                resolveTypesOfActuals(res,thiz);
                actualsResolved = true;
                if (_calledFeature == null)
                  {
                    _calledFeature = fo.filter(pos(), calledName, ff -> isSpecialWrtArgs(ff));
                  }
                if (_calledFeature == null)
                  {
                    findChainedBooleans(res, thiz);
                  }
                if (_calledFeature == null)
                  {
                    findOperatorOnOuter(res, thiz);
                  }
                if (_calledFeature == null) // nothing found, so flag error
                  {
                    AstErrors.calledFeatureNotFound(this, calledName, targetFeature);
                  }
              }
          }
        if (!actualsResolved)
          {
            resolveTypesOfActuals(res,thiz);
          }
      }

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0 || calledFeature() != null,
       Errors.count() > 0 || _target         != null);
  }


  void resolveTypesOfActuals(Resolution res, AbstractFeature outer)
  {
    ListIterator<Expr> i = _actuals.listIterator(); // _actuals can change during resolveTypes, so create iterator early
    outer.whenResolvedTypes
      (() ->
       {
         while (i.hasNext())
           {
             var a = i.next();
             if (a != null) // splitOffTypeArgs might have set this to null
               {
                 i.set(res.resolveType(a, outer));
               }
           }
       });
  }


  /**
   * For an infix, prefix or postfix operator call of the form
   *
   *   a ⨁ b     -- or --
   *   ⨁ a       -- or --
   *   a ⨁
   *
   * that was not found within the target 'a', try to find this operator in 'thiz'
   * or any outer feature.  If found in X.Y.Z.this, then convert this call into
   *
   *   X.Y.Z.this.infix  ⨁ a b     -- or --
   *   X.Y.Z.this.prefix ⨁ a       -- or --
   *   X.Y.Z.this.postix ⨁ a       ,
   *
   * respectively.  This permits the introduction of binary or unary operators
   * within any feature, e.g., within unit type features that can be inherited
   * from or even in the universe.
   *
   * If successful, field _calledFeature will be set to the called feature and
   * fields _target and _actuals will be changed accordingly.
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param thiz the surrounding feature
   */
  void findOperatorOnOuter(Resolution res, AbstractFeature thiz)
  {
    if (_name.startsWith("infix "  ) ||
        _name.startsWith("prefix " ) ||
        _name.startsWith("postfix ")    )
      {
        var calledName = FeatureName.get(_name, _actuals.size()+1);
        var oldTarget = _target;
        _target = null;
        var fo = calledFeatureCandidates(thiz, res, thiz);
        _calledFeature = fo.filter(pos(), calledName, ff -> mayMatchArgList(ff, true));
        if (_calledFeature != null)
          {
            var oldActuals = _actuals;
            _actuals = new List(oldTarget);
            _actuals.addAll(oldActuals);
          }
        else
          {
            _target = oldTarget;
          }
      }
  }


  /**
   * For a call of the form
   *
   *   array i32 10 i->i*i
   *
   * split the actuals list (i32, 10, i->i*i) into generics (i32) and actuals
   * (10, i->i*i).
   */
  void splitOffTypeArgs(AbstractFeature outer)
  {
    var g = new List<AbstractType>();
    var a = new List<Expr>();
    var ts = _calledFeature.typeArguments();
    var tn = ts.size();
    var ti = 0;
    var vs = _calledFeature.valueArguments();
    var vn = vs.size();
    var i = 0;
    ListIterator<Expr> ai = _actuals.listIterator();
    while (ai.hasNext())
      {
        var aa = ai.next();

        // check that ts[ti] is open type parameter only iff ti == tn-1, ie.,
        // only the last type parameter may be open
        if (CHECKS) check
          (ti >= tn-1 ||
           ts.get(ti).kind() == AbstractFeature.Kind.TypeParameter    ,
           ti != tn-1 ||
           ts.get(ti).kind() == AbstractFeature.Kind.TypeParameter     ||
           ts.get(ti).kind() == AbstractFeature.Kind.OpenTypeParameter);

        if (_actuals.size() - i > vn)
          {
            AbstractType t = _actualsNew.get(i)._type;
            if (t != null)
              {
                t.visit(Feature.findGenerics, outer);
                g.add(t);
              }
            ai.set(null);  // make sure visit() no longer visits this
            if (ts.get(ti).kind() != AbstractFeature.Kind.OpenTypeParameter)
              {
                ti++;
              }
          }
        else
          {
            a.add(aa);
          }
        i++;
      }
    _generics = g;
    _actuals = a;
  }


  /**
   * Check if this expression can also be parsed as a type and return that type. Otherwise,
   * report an error (AstErrors.expectedActualTypeInCall).
   *
   * @param outer the outer feature containing this expression
   *
   * @param tp the type parameter this expression is assigned to
   *
   * @return the Type corresponding to this, Type.t_ERROR in case of an error.
   */
  AbstractType asType(AbstractFeature outer, AbstractFeature tp)
  {
    var g = _generics;
    if (!_actuals.isEmpty())
      {
        g = new List<AbstractType>();
        g.addAll(_generics);
        for (var a : _actuals)
          {
            g.add(a.asType(outer, tp));
          }
      }
    AbstractType result = new Type(pos(), _name, g,
                                   _target == null             ||
                                   _target instanceof Universe ||
                                   _target instanceof Current     ? null
                                                                  : _target.asType(outer, tp));
    return result.visit(Feature.findGenerics, outer);
  }


  /**
   * Check if this call when the _calledFeature would be ff needs special
   * handling of the argument count.  This is the case for open generics, "fun
   * a.b.f" calls and implicit calls using f() for f returning Function value.
   *
   * @param ff the called feature candidate.
   *
   * @return true iff ff may be the called feature due to the special cases
   * listed above.
   */
  private boolean isSpecialWrtArgs(AbstractFeature ff)
  {
    return _forFun                                     /* a fun-declaration "fun a.b.f" */
      || ff.arguments().size()==0 && hasParentheses(); /* maybe an implicit call to a Function / Routine, see resolveImmediateFunctionCall() */
  }


  /**
   * Check if the actual arguments to this call may match the formal arguments
   * for calling ff.
   *
   * @param ff the candidate that might be called
   *
   * @param addOne true iff one actual argument will be added (used in
   * findOperatorOnOuter wich will add the target to the actual arguments).
   *
   * @return true if ff is a valid candidate to be called.
   */
  private boolean mayMatchArgList(AbstractFeature ff, boolean addOne)
  {
    var asz = _actuals.size() + (addOne ? 1 : 0);
    var fvsz = ff.valueArguments().size();
    var ftsz = ff.typeArguments().size();

    var result = fvsz == asz ||
      _generics.isEmpty() && (fvsz + ftsz == asz) ||
      _generics.isEmpty() && asz >= fvsz + ftsz -1 &&
      ff.typeArguments().stream().anyMatch(ta -> ta.kind() == AbstractFeature.Kind.OpenTypeParameter);
    return result;
  }


  /**
   * After resolveTypes or if calledFeatureKnown(), this can be called to obtain
   * the feature that is called.
   */
  public AbstractFeature calledFeature()
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || calledFeatureKnown());

    AbstractFeature result = _calledFeature != null ? _calledFeature : Types.f_ERROR;

    if (POSTCONDITIONS) ensure
      (result != null);
    return result;
  }


  /**
   * Is the called feature known? This is the case for calls to anonymous inner
   * features even before resolveTypes is executed. After resolveTypes, this is
   * the case unless there was an error finding the called feature.
   */
  boolean calledFeatureKnown()
  {
    return _calledFeature != null;
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  public boolean isCallToOuterRef()
  {
    return calledFeature().isOuterRef();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return (_target == null ||
            (_target instanceof Universe) ||
            (_target instanceof This t && t.toString().equals(FuzionConstants.UNIVERSE_NAME + ".this"))
            ? ""
            : _target.toString() + ".")
      + (_name != null ? _name : _calledFeature.featureName().baseName())
      + (_generics.isEmpty() ? "" : "<" + _generics + ">")
      + (_actuals.isEmpty() ? "" : "(" + _actuals +")")
      + (_select < 0        ? "" : "." + _select);
  }


  /**
   * setTarget
   *
   * @param t
   */
  public void setTarget(Expr t)
  {
    Expr ot = this._target;
    if (ot instanceof Call)
      {
        ((Call)ot).setTarget(t);
      }
    else  if (ot != null)
      {
        throw new Error("target alredy set: old: "+ot+" new: "+t);
      }
    else
      {
        this._target = t;
      }
  }


  /**
   * setBlock
   *
   * @param l
   */
  public void setBlock(List l)
  {
  }


  /**
   * typeForFeatureResultTypeInferencing returns the type of this expression or
   * null if the type is still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForFeatureResultTypeInferencing()
  {
    return _type;
  }


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
    if (!_generics.isEmpty())
      {
        var i = _generics.listIterator();
        while (i.hasNext())
          {
            var n = i.next();
            if (CHECKS) check
              (Errors.count() > 0 || n != null);
            if (n != null)
              {
                i.set(n.visit(v, outer));
              }
          }
      }
    if (v.doVisitActuals())
      {
        ListIterator<Expr> i = _actuals.listIterator(); // _actuals can change during resolveTypes, so create iterator early
        while (i.hasNext())
          {
            var a = i.next();
            if (a != null)
              {
                i.set(a.visit(v, outer));
              }
          }
      }
    if (_target != null)
      {
        _target = _target.visit(v, outer);
      }
    v.action((AbstractCall) this);
    return v.action(this, outer);
  }


  /**
   * Helper function called during resolveTypes to resolve syntactic sugar that
   * allows directly calling a function returned by a call.
   *
   * If this is a normal call (e.g. "f.g") whose result is a function type,
   * ("fun (int a,b) float"), and if g does not take any arguments, syntactic
   * sugar allows an implicit call to Function/Routine.call, i.e., "f.g(3,5)" is
   * a short form of "f.g.call(3,5)".
   *
   * NYI: we could also permit "f.g(x,y)(3,5)" as a short form for
   * "f.g(x,y).call(3,5)" in case g takes arguments.  But this might be too
   * confusing and it would require a change in the grammar.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   *
   * @param result this in case this was not an immediate call, otherwise qthe
   * resulting cll to Function/Routine.call.
   */
  private Call resolveImmediateFunctionCall(Resolution res, AbstractFeature outer)
  {
    Call result = this;
    if (!_forFun && // not a call to "b" within an expression of the form "fun a.b", will be handled after syntactic sugar
        _type.isFunType() &&
        _calledFeature != Types.resolved.f_function && // exclude inherits call in function type
        _calledFeature.arguments().size() == 0 &&
        hasParentheses())
      {
        // a Function: the result is the first generic argument
        var funResultType = _type.generics().isEmpty() ? Types.t_ERROR : _type.generics().getFirst();
        var numFormalArgs = _type.generics().size() - 1;
        result = new Call(pos(),
                          "call",
                          NO_GENERICS,
                          _actuals,
                          this /* this becomes target of "call" */,
                          res._module.lookupFeature(_type.featureOfType(), FeatureName.get("call", _actuals.size())),
                          funResultType)
          .resolveTypes(res, outer);
        _actuals = NO_PARENTHESES;
      }
    return result;
  }


  /**
   * Helper routine for resolveFormalArgumentTypes to determine the actual type
   * of a formal argument after inheritance.
   *
   * The result will be stored in
   * _resolvedFormalArgumentTypes[argnum..argnum+result-1].
   *
   * @param res Resolution instance
   *
   * @param argnum the number of this formal argument
   *
   * @param frml the formal argument
   *
   * @return the number of arguments that correspond to argnum after handing
   * down.
   */
  private int handDownFormalArg(Resolution res, int argnum, AbstractFeature frml)
  {
    int result = 1;
    var frmlT = frml.resultType();
    if (CHECKS) check
      (frmlT == Types.intern(frmlT));

    var declF = _calledFeature.outer();
    var heirF = targetTypeOrConstraint(res).featureOfType();
    if (target() instanceof Call tc              &&
        tc.calledFeature().isTypeParameter()     &&
        heirF.isStaticTypeFeature()              &&
        calledFeature().outer().isTypeFeature()  &&
        calledFeature().belongsToNonStaticType()    )
      {  // divert calls T.f with a type parameter as target to the non-static type if needed.
        heirF = heirF.typeFeaturesNonStaticParent();
      }
    if (declF != heirF)
      {
        var a = _calledFeature.handDown(res, new AbstractType[] { frmlT }, heirF);
        if (a.length != 1)
          {
            // Check that the number or args can only change for the
            // last argument (when it is of an open generic type).  if
            // it would change for other arguments, changing the
            // _resolvedFormalArgumentTypes array would invalidate
            // argnum for following arguments.
            if (CHECKS) check
              (Errors.count() > 0 || argnum == _resolvedFormalArgumentTypes.length - 1);
            if (argnum != _resolvedFormalArgumentTypes.length -1)
              {
                a = new AbstractType[] { Types.t_ERROR }; /* do not change _resolvedFormalArgumentTypes array length */
              }
          }
        addToResolvedFormalArgumentTypes(res, argnum, a, frml);
        result = a.length;
      }
    else
      {
        if (CHECKS) check
          (Errors.count() > 0 || argnum <= _resolvedFormalArgumentTypes.length);

        if (argnum < _resolvedFormalArgumentTypes.length)
          {
            if (CHECKS) check
              (frmlT != null);
            _resolvedFormalArgumentTypes[argnum] = frmlT;
          }
      }
    return result;
  }


  /**
   * Helper routine for resolveFormalArgumentTypes to determine the actual type
   * of a formal argument from the target type and generics provided to the call.
   *
   * The type(s) will be takenb from
   * _resolvedFormalArgumentTypes[argnum..argnum+n-1], the result will be stored
   * in _resolvedFormalArgumentTypes[argnum..].
   *
   * @param res Resolution instance
   *
   * @param argnum the number of this formal argument
   *
   * @param n the number of arguments. This might be != 1 if
   * frml.resultType().isOpenGeneric() and this type has been replaced with no
   * or several actual types.
   *
   * @param frml the formal argument
   */
  private void replaceGenericsInFormalArg(Resolution res, int argnum, int n, AbstractFeature frml)
  {
    for (int i = 0; i < n; i++)
      {
        if (CHECKS) check
          (Errors.count() > 0 || argnum + i <= _resolvedFormalArgumentTypes.length);

        if (argnum + i < _resolvedFormalArgumentTypes.length)
          {
            var frmlT = _resolvedFormalArgumentTypes[argnum + i];

            if (frmlT.isOpenGeneric())
              { // formal arg is open generic, i.e., this expands to 0 or more actual args depending on actual generics for target:
                Generic g = frmlT.genericArgument();
                var frmlTs = g.replaceOpen(g.feature() == _calledFeature
                                           ? _generics
                                           : _target.type().generics());
                addToResolvedFormalArgumentTypes(res, argnum + i, frmlTs.toArray(new AbstractType[frmlTs.size()]), frml);
                i = i + frmlTs.size() - 1;
                n = n + frmlTs.size() - 1;
              }
            else
              {
                frmlT = frmlT.replace_THIS_TYPE(target());
                frmlT = targetTypeOrConstraint(res).actualType(frmlT);
                frmlT = frmlT.actualType(_calledFeature, _generics);
                frmlT = Types.intern(frmlT);
                if (CHECKS) check
                  (frmlT != null);
                _resolvedFormalArgumentTypes[argnum + i] = frmlT;
              }
          }
      }
  }


  /**
   * Helper routine for handDownFormalArg and replaceGenericsInFormalArg to
   * extend the _resolvedFormalArgumentTypes array.
   *
   * In case frml.resultType().isOpenGeneric(), this will call frml.select() for
   * all the actual types the open generic is replaced by to make sure the
   * corresponding features exist.
   *
   * @param res Resolution instance
   *
   * @param argnum index in _resolvedFormalArgumentTypes at which we add new
   * elements
   *
   * @param a the new elements to add to _resolvedFormalArgumentTypes
   *
   * @param frml the argument whose type we are resolving.
   */
  private void addToResolvedFormalArgumentTypes(Resolution res, int argnum, AbstractType[] a, AbstractFeature frml)
  {
    var na = new AbstractType[_resolvedFormalArgumentTypes.length - 1 + a.length];
    var j = 0;
    for (var i = 0; i < _resolvedFormalArgumentTypes.length; i++)
      {
        if (i == argnum)
          {
            for (var at : a)
              {
                if (CHECKS) check
                  (at != null);
                na[j] = at;
                j++;
              }
          }
        else
          {
            na[j] = _resolvedFormalArgumentTypes[i];
            j++;
          }
      }
    _resolvedFormalArgumentTypes = na;
  }


  /**
   * Helper routine for resolveTypes to resolve the formal argument types of the
   * arguments in this call. Results will be stored in
   * _resolvedFormalArgumentTypes array.
   */
  private void resolveFormalArgumentTypes(Resolution res)
  {
    var fargs = _calledFeature.valueArguments();
    _resolvedFormalArgumentTypes = fargs.size() == 0 ? Type.NO_TYPES
                                                    : new AbstractType[fargs.size()];
    Arrays.fill(_resolvedFormalArgumentTypes, Types.t_ERROR);
    int count = 0;
    for (var frml : fargs)
      {
        int argnum = count;  // effectively final copy of count
        frml.whenResolvedTypes
          (() ->
           {
             // first, replace generics according to inheritance:
             int n = handDownFormalArg (res, argnum,    frml);
             // next, replace generics given in the target type and in this call
             replaceGenericsInFormalArg(res, argnum, n, frml);
           });
        count++;
      }
    if (POSTCONDITIONS) ensure
      (_resolvedFormalArgumentTypes != null);
  }


  /**
   * Helper function for resolveTypes to determine the static result type of
   * this call.
   *
   * In particular, this replaces formal generic types by actual generics
   * provided to this call and it replaces select calls to fields of open
   * genenric type by calls to the actual fields.
   *
   * @param res the resolution instance.
   *
   * @param t the result type of the called feature, might be open genenric.
   */
  private void resolveType(Resolution res, AbstractType t, AbstractFeature outer)
  {
    /* make sure '.type' features are declared for all actual generics: */
    for (var g : _generics)
      {
        if (CHECKS) check
          (Errors.count() > 0 || g != null);
        if (g != null)
          {
            g.resolve(res, outer);
            if (!g.isGenericArgument())
              {
                g.featureOfType().typeFeature(res);
              }
          }
      }

    var tt = targetTypeOrConstraint(res);
    if (_select < 0 && t.isOpenGeneric())
      {
        AstErrors.cannotAccessValueOfOpenGeneric(pos(), _calledFeature, t);
        t = Types.t_ERROR;
      }
    else if (_select >= 0 && !t.isOpenGeneric())
      {
        AstErrors.useOfSelectorRequiresCallWithOpenGeneric(pos(), _calledFeature, _name, _select, t);
        t = Types.t_ERROR;
      }
    else if (_select < 0)
      {
        t = t.resolve(res, tt.featureOfType());
        t = (target() instanceof Current) || tt.isGenericArgument() ? t : tt.actualType(t);
        if (_calledFeature.isConstructor() && t.compareTo(Types.resolved.t_void) != 0)
          {  /* specialize t for the target type here */
            t = new Type(t, t.generics(), _target.type());
          }
      }
    else
      {
        var types = t.genericArgument().replaceOpen(tt.generics());
        int sz = types.size();
        if (_select >= sz)
          {
            AstErrors.selectorRange(pos(), sz, _calledFeature, _name, _select, types);
            _calledFeature = Types.f_ERROR;
            t = Types.t_ERROR;
          }
        else
          {
            t = types.get(_select);
          }
      }
    if (_calledFeature.isTypeParameter())
      {
        if (_select >= 0 || _calledFeature.isOpenTypeParameter())
          {
            throw new Error("NYI (see #283): Calling open type parameter");
          }
        var tptype = t.resolve(res, tt.featureOfType());
        if (!tptype.isGenericArgument())
          {
            tptype = tptype.featureOfType().typeFeature(res).thisType();
          }
        _type = tptype;
      }
    else if (_calledFeature == Types.resolved.f_Types_get)
      { // NYI (see #282): special handling could maybe be avoided? Maybe make
        // this special handling the normal handlng for all features whose
        // result type depends on a generic that can be replaced by an actual
        // generic given in the call?
        var gt = _generics.get(0);
        if (gt.isGenericArgument())
          {
            _type = t.resolve(res, tt.featureOfType());
          }
        else
          {
            _type = gt.featureOfType().typeFeature(res).resultTypeIfPresent(res, _generics);
            if (_type == null)
              {
                throw new Error("NYI (see #283): resolveTypes for .type: resultType not present at "+pos().show());
              }
          }
      }
    else
      {
        _type = t.resolve(res, tt.featureOfType());
      }
  }


  /**
   * Helper routine for inferGenericsFromArgs: Get the next element from aargs,
   * perform type resolution (which includes possibly replacing it by a
   * different Expr) and return it.
   *
   * @param aargs iterator
   *
   * @param res the resolution instance
   *
   * @param outer the root feature that contains this statement
   */
  private Expr resolveTypeForNextActual(ListIterator<Expr> aargs, Resolution res, AbstractFeature outer)
  {
    Expr actual = aargs.next();
    actual = res.resolveType(actual, outer);
    aargs.set(actual);
    return actual;
  }


  /**
   * infer the missing generic arguments to this call by inspecting the types of
   * the actual arguments.
   *
   * This is called during resolveTypes, so we have to be careful since type
   * information is not generally available yet.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  private void inferGenericsFromArgs(Resolution res, AbstractFeature outer)
  {
    var cf = _calledFeature;
    int sz = cf.generics().list.size();
    boolean[] conflict = new boolean[sz]; // The generics that had conflicting types
    String [] foundAt  = new String [sz]; // detail message for conflicts giving types and their location

    _generics = new List<>();
    for (Generic g : cf.generics().list)
      {
        if (!g.isOpen())
          {
            _generics.add(Types.t_UNDEFINED);
          }
      }

    var va = cf.valueArguments();
    var checked = new boolean[va.size()];
    int last, next = 0;
    do
      {
        last = next;
        inferGenericsFromArgs(res, outer, checked, conflict, foundAt);
        next = 0;
        for (var b : foundAt)
          {
            next = next + (b != null ? 1 : 0);
          }
      }
    while (last < next);

    List<Generic> missing = new List<Generic>();
    for (Generic g : cf.generics().list)
      {
        int i = g.index();
        if ( g.isOpen() && foundAt[i] == null ||
            !g.isOpen() && _generics.get(i) == Types.t_UNDEFINED)
          {
            missing.add(g);
            if (CHECKS) check
              (Errors.count() > 0 || g.isOpen() || i < _generics.size());
            if (i < _generics.size())
              {
                _generics.set(i, Types.t_ERROR);
              }
          }
        else if (conflict[i])
          {
            AstErrors.incompatibleTypesDuringTypeInference(pos(), g, foundAt[i]);
          }
      }

    if (!missing.isEmpty())
      {
        AstErrors.failedToInferActualGeneric(pos(),cf, missing);
      }
  }


  /**
   * infer the missing generic arguments to this call by inspecting the types of
   * the actual arguments.
   *
   * This is called during resolveTypes, so we have to be careful since type
   * information is not generally available yet.
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   *
   * @param checked boolean array for all cf.valuedArguments() that have been
   * checked already.
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  void inferGenericsFromArgs(Resolution res, AbstractFeature outer, boolean[] checked, boolean[] conflict, String[] foundAt)
  {
    var cf = _calledFeature;
    int count = 1; // argument count, for error messages
    ListIterator<Expr> aargs = _actuals.listIterator();
    var va = cf.valueArguments();
    var vai = 0;
    for (var frml : va)
      {
        if (CHECKS) check
          (Errors.count() > 0 || frml.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));

        if (!checked[vai])
          {
            var t = frml.resultTypeIfPresent(res, NO_GENERICS);
            var g = t.isGenericArgument() ? t.genericArgument() : null;
            if (g != null && g.feature() == cf && g.isOpen())
              {
                checked[vai] = true;
                foundAt[g.index()] = "open"; // set to something not null to avoid missing argument error below
                while (aargs.hasNext())
                  {
                    count++;
                    Expr actual = resolveTypeForNextActual(aargs, res, outer);
                    var actualType = actual.typeForFeatureResultTypeInferencing();
                    if (actualType == null)
                      {
                        actualType = Types.t_ERROR;
                        AstErrors.failedToInferOpenGenericArg(pos(), count, actual);
                      }
                    _generics.add(actualType);
                  }
              }
            else if (aargs.hasNext())
              {
                count++;
                Expr actual = resolveTypeForNextActual(aargs, res, outer);
                var actualType = actual.typeForGenericsTypeInfereing();
                if (actualType != null)
                  {
                    inferGeneric(res, t, actualType, actual.pos(), conflict, foundAt);
                    checked[vai] = true;
                  }
                else if (actual instanceof Function af)
                  {
                    checked[vai] = inferGenericLambdaResult(res, outer, t, af, actual.pos(), conflict, foundAt);
                  }
              }
          }
        vai++;
      }
  }



  /**
   * Perform type inference for generics used in formalType that are instantiated by actualType.
   *
   * @param res the resolution instance.
   *
   * @param formalType the (possibly generic) formal type
   *
   * @param actualType the actual type
   *
   * @param pos source code position of the expression actualType was derived from
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  private void inferGeneric(Resolution res, AbstractType formalType, AbstractType actualType, SourcePosition pos, boolean[] conflict, String[] foundAt)
  {
    if (formalType.isGenericArgument())
      {
        var g = formalType.genericArgument();
        if (g.feature() == _calledFeature)
          { // we found a use of a generic type, so record it:
            var i = g.index();
            if (foundAt[i] == null || actualType.isAssignableFromOrContainsError(_generics.get(i)))
              {
                _generics.set(i, actualType);
              }
            conflict[i] = conflict[i] || !_generics.get(i).isAssignableFromOrContainsError(actualType);
            foundAt [i] = (foundAt[i] == null ? "" : foundAt[i]) + actualType + " found at " + pos.show() + "\n";
          }
      }
    else
      {
        var fft = formalType.featureOfType();
        res.resolveTypes(fft);
        var aft = actualType.isGenericArgument() ? null : actualType.featureOfType();
        if (fft == aft)
          {
            for (int i=0; i < formalType.generics().size(); i++)
              {
                if (i < actualType.generics().size())
                  {
                    inferGeneric(res,
                                 formalType.generics().get(i),
                                 actualType.generics().get(i),
                                 pos, conflict, foundAt);
                  }
              }
          }
        else if (formalType.isChoice())
          {
            for (var ct : formalType.choiceGenerics())
              {
                inferGeneric(res, ct, actualType, pos, conflict, foundAt);
              }
          }
        else if (aft != null)
          {
            for (var p: aft.inherits())
              {
                var pt = p.typeForFeatureResultTypeInferencing();
                if (pt != null)
                  {
                    var apt = actualType.actualType(pt);
                    inferGeneric(res, formalType, apt, pos, conflict, foundAt);
                  }
              }
          }
      }
  }


  /**
   * Perform type inference for result type of lambda
   *
   * @param res the resolution instance.
   *
   * @param outer the feature containing this call
   *
   * @param formalType the (possibly generic) formal type
   *
   * @param actualType the actual type
   *
   * @param pos source code position of the expression actualType was derived from
   *
   * @param conflict set of generics that caused conflicts
   *
   * @param foundAt the position of the expressions from which actual generics
   * were taken.
   */
  private boolean inferGenericLambdaResult(Resolution res,
                                           AbstractFeature outer,
                                           AbstractType formalType,
                                           Function af,
                                           SourcePosition pos,
                                           boolean[] conflict,
                                           String[] foundAt)
  {
    var result = false;
    if (!formalType.isGenericArgument() &&
        formalType.featureOfType() == Types.resolved.f_function &&
        formalType.generics().get(0).isGenericArgument()
        )
      {
        var rg = formalType.generics().get(0).genericArgument();
        var ri = rg.index();
        var cf = _calledFeature;
        if (rg.feature() == cf && foundAt[ri] == null)
          {
            var at = targetTypeOrConstraint(res).actualType(formalType).actualType(cf, _generics);
            if (!at.containsUndefined(true))
              {
                var rt = af.propagateExpectedType2(res, outer, at, true);
                if (rt != null)
                  {
                    _generics.set(ri, rt);
                  }
                foundAt[ri] = (foundAt[ri] == null ? "" : foundAt[ri]) + rt + " found at " + pos.show() + "\n";
                result = true;
              }
          }
      }
    return result;
  }


  /**
   * Is this a tail recursive call?
   *
   * A tail recursive call within 'outer' is a call to 'outer' whose result is
   * returned without any further modification.
   *
   * This means, any call
   *
   *    target.outer arg1 arg2 ...
   *
   * is a tail recursive call provided that the result returned is not
   * processed. The call may be dynamic, i.e., target may evalute to something
   * different than outer.outer.
   *
   * This is used to allow cyclic type inferencing of the form
   *
   *   f =>
   *     if c
   *       x
   *     else
   *       f
   *
   * Which must return a value of x's type.
   */
  boolean isTailRecursive(AbstractFeature outer)
  {
    return
      calledFeature() == outer &&
      returnsThis(outer.code());
  }


  /**
   * Check if the result returns by the given expression is the result of this
   * call (i.e., this call is a tail call in e).
   *
   * @param e an expression.
   *
   * @return true iff this is a expression that can produce the result of e (but
   * not necesarily the only one).
   */
  boolean returnsThis(Expr e)
  {
    if (e instanceof If i)
      {
        var it = i.branches();
        while (it.hasNext())
          {
            if (returnsThis(it.next()))
              {
                return true;
              }
          }
      }
    else if (e instanceof Match m)
      {
        for (var c : m.cases())
          {
            if (returnsThis(c.code()))
              {
                return true;
              }
          }
      }
    else if (e instanceof Block b)
      {
        var r = b.resultExpression();
        return r != null && returnsThis(r);
      }
    return e == this;
  }

  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Call resolveTypes(Resolution res, AbstractFeature outer)
  {
    Call result = this;
    loadCalledFeature(res, outer);
    FormalGenerics.resolve(res, _generics, outer);

    if (CHECKS) check
      (Errors.count() > 0 || _calledFeature != null);

    if (_calledFeature == null)
      {
        _type = Types.t_ERROR;
      }
    else
      {
        if (_generics == NO_GENERICS && _calledFeature.generics() != FormalGenerics.NONE)
          {
            inferGenericsFromArgs(res, outer);
          }
        if (_calledFeature.generics().errorIfSizeOrTypeDoesNotMatch(_generics,
                                                                    pos(),
                                                                    "call",
                                                                    "Called feature: "+_calledFeature.qualifiedName()+"\n"))
          {
            var cf = _calledFeature;
            var t = isTailRecursive(outer) ? Types.resolved.t_void // a tail recursive call will not return and execute further
                                     : cf.resultTypeIfPresent(res, _generics);
            if (t == null)
              {
                cf.whenResolvedTypes
                  (() ->
                   {
                     var t2 = cf.resultTypeForTypeInference(pos(), res, _generics);
                     resolveType(res, t2, _calledFeature.outer());
                   });
              }
            else
              {
                resolveType(res, t, _calledFeature.outer());
                if (isTailRecursive(outer))
                  {
                    cf.whenResolvedTypes
                      (() ->
                       {
                         var t2 = cf.resultTypeForTypeInference(pos(), res, _generics);
                         resolveType(res, t2, _calledFeature.outer());
                       });
                  }

                // Convert a call "f.g(a,b)" into "f.g.call(f,g)" in case f.g takes no
                // arguments and returns a Function or Routine
                result = resolveImmediateFunctionCall(res, outer); // NYI: Separate pass? This currently does not work if type was inferred
              }
          }
        else
          {
            _type = Types.t_ERROR;
          }
        resolveFormalArgumentTypes(res);
      }
    return result;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   */
  public void propagateExpectedType(Resolution res, AbstractFeature outer)
  {
    if (!_forFun &&
        _type != Types.t_ERROR &&
        _resolvedFormalArgumentTypes != null &&
        _actuals.size() == _resolvedFormalArgumentTypes.length /* this will cause an error in checkTypes() */ )
      {
        int count = 0;
        ListIterator<Expr> i = _actuals.listIterator();
        while (i.hasNext())
          {
            Expr actl = i.next();
            var frmlT = _resolvedFormalArgumentTypes[count];
            if (CHECKS) check
              (frmlT != null,
               frmlT != Types.t_ERROR || Errors.count() > 0);
            i.set(actl.propagateExpectedType(res, outer, frmlT));
            count++;
          }

        if (_target != null)
          {
            // NYI: Need to check why this is needed, it does not make sense to
            // propagate the target's type to target. But if removed,
            // tests/reg_issue16_chainedBool/ fails with C backend:
            _target = _target.propagateExpectedType(res, outer, _target.typeForFeatureResultTypeInferencing());
          }
      }
  }


  /**
   * Boxing for actual arguments: Find actual arguments of value type that are
   * assigned to formal argument types that are references and box them.
   *
   * @param outer the feature that contains this expression
   */
  public void box(AbstractFeature outer)
  {
    if (!_forFun && _type != Types.t_ERROR)
      {
        int fsz = _resolvedFormalArgumentTypes.length;
        if (_actuals.size() ==  fsz)
          {
            int count = 0;
            ListIterator<Expr> i = _actuals.listIterator();
            while (i.hasNext())
              {
                Expr actl = i.next();
                var rft = _resolvedFormalArgumentTypes[count];
                if (CHECKS) check
                  (rft != null,
                   rft != Types.t_ERROR || Errors.count() > 0);
                i.set(actl.box(rft));
                count++;
              }
          }
      }
  }


  /**
   * perform static type checking, i.e., make sure that in all assignments from
   * actual to formal arguments, the types match.
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes(AbstractFeature outer)
  {
    if (_forFun)
      { // this is a call to "b" within an expression of the form "fun a.b". In
        // this case, there must be no generics nor actual arguments to "b", the
        // call will be replaced during Function.resolveSyntacticSugar.
        if (_actuals.size() != 0)
          {
            AstErrors.functionMustNotProvideActuals(pos(), this, _actuals);
          }
        else if (hasParentheses())
          {
            AstErrors.functionMustNotProvideParentheses(pos(), this);
          }
      }
    else if (_type != Types.t_ERROR)
      {
        int fsz = _resolvedFormalArgumentTypes.length;
        if (_actuals.size() !=  fsz)
          {
            AstErrors.wrongNumberOfActualArguments(this);
          }
        else
          {
            int count = 0;
            for (Expr actl : _actuals)
              {
                var frmlT = _resolvedFormalArgumentTypes[count];
                if (frmlT != null /* NYI: make sure this is never null */ && !frmlT.isAssignableFrom(actl.type()))
                  {
                    AstErrors.incompatibleArgumentTypeInCall(_calledFeature, count, frmlT, actl);
                  }
                count++;
              }
          }
        if (_calledFeature.isChoice())
          {
            boolean ok = false;
            if (outer != null && outer.isChoice())
              {
                for (var p : outer.inherits())
                  {
                    ok = ok || p == this;
                  }
              }
            if (!ok)
              {
                AstErrors.cannotCallChoice(pos(), _calledFeature);
              }
          }

        // Check that generics match formal generic constraints
        var fi = _calledFeature.generics().list.iterator();
        var gi = _generics.iterator();
        while (fi.hasNext() &&
               gi.hasNext()    ) // NYI: handling of open generic arguments
          {
            var f = fi.next();
            var g = gi.next();

            if (CHECKS) check
              (Errors.count() > 0 || f != null && g != null);
            if (f != null && g != null &&
                !f.constraint().constraintAssignableFrom(g))
              {
                if (!f.typeParameter().isTypeFeaturesThisType())  // NYI: CLEANUP: #706: remove special handling for 'THIS_TYPE'
                  {
                    AstErrors.incompatibleActualGeneric(pos(), f, g);
                  }
              }
          }
      }
  }


  /**
   * Helper function for resolveSyntacticSugar: Create "if cc block else
   * elseBlock" and handle the case that cc is a compile time constant.
   *
   * NYI: move this to If.resolveSyntacticSugar!
   */
  private Expr newIf(Expr cc, Expr block, Expr elseBlock)
  {
    return
      !cc.isCompileTimeConst()     ? new If(pos(), cc, block, elseBlock) :
      cc.getCompileTimeConstBool() ? block : elseBlock;
  }


  /**
   * Syntactic sugar resolution: This does the following:
   *
   *  - convert boolean operations &&, || and : into if-statements
   *  - convert repeated boolean operations ! into identity   // NYI
   *  - perform constant propagation for basic algebraic ops  // NYI
   *  - replace calls to intrinsics that return compile time constants
   *
   * @return a new Expr to replace this call or this if it remains unchanged.
   */
  Expr resolveSyntacticSugar(Resolution res, AbstractFeature outer)
  {
    Expr result = this;
    //    if (true) return result;
    if (Errors.count() == 0)
      {
        // convert
        //   a && b into if a b     else false
        //   a || b into if a true  else b
        //   a: b   into if a b     else true
        //   !a     into if a false else true
        var cf = _calledFeature;
        if      (cf == Types.resolved.f_bool_AND    ) { result = newIf(_target, _actuals.get(0), BoolConst.FALSE); }
        else if (cf == Types.resolved.f_bool_OR     ) { result = newIf(_target, BoolConst.TRUE , _actuals.get(0)); }
        else if (cf == Types.resolved.f_bool_IMPLIES) { result = newIf(_target, _actuals.get(0), BoolConst.TRUE ); }
        else if (cf == Types.resolved.f_bool_NOT    ) { result = newIf(_target, BoolConst.FALSE, BoolConst.TRUE ); }
      }
    return result;
  }

}

/* end of file */
