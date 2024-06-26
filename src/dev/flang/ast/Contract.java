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
 * Source of class Contract
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceRange;
import dev.flang.util.SourcePosition;


/**
 * Contract <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Contract extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Empty list of conditions.
   */
  static final List<Cond> NO_COND = new List<>();
  static { NO_COND.freeze(); }


  /**
   * Empty contract
   */
  public static final Contract EMPTY_CONTRACT = new Contract(NO_COND, NO_COND, null, null,
                                                             NO_COND, null, null,
                                                             null);


  /*--------------------------  static fields  --------------------------*/


  /**
   * Id used to generate unique names for pre- and postcondution features.
   */
  public static int _id_ = 0;


  /**
   * Reset static fields
   */
  public static void reset()
  {
    _id_ = 0;
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  public List<Cond>            _declared_preconditions;
  public List<Cond>            _declared_preconditions2;
  public List<Cond>            _declared_postconditions;


  /**
   * Clone of parsed arguments of the feature this contract belongs to.  To be
   * used to create arguments for precondition and postcondition features.
   */
  java.util.function.Supplier<List<AbstractFeature>> _argsSupplier;

  /**
   * Did the parser find `pre` / `post` or even `pre else` / `post then` ? These
   * might be present even if the condition list is NO_COND.
   */
  public final SourceRange _hasPre,     _hasPost;
  public final SourceRange _hasPreElse, _hasPostThen;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a contract
   *
   * @param r1, r2 the preconditions, parsed twice since we will need them twice. null if not present.
   *
   * @param hasPre if `pre` was found, this gives its position, otherwise it is null.
   *
   * @param hasElse if `else` after `pre` was found, this gives its prosition,
   * otherwise it is null.
   *
   * @param e the postcondition or null if not present.
   *
   * @param hasPost if `post` was found, this gives its position, otherwise it is null
   *
   * @param hasThen if `then` after `post` was found, this gives its prosition,
   * otherwise it is null.
   *
   * @param preArgs, preArgs1, preArgs2, postArgs List or parsed feature arguments.
   */
  public Contract(List<Cond> r1, List<Cond> r2, SourceRange hasPre,  SourceRange hasElse,
                  List<Cond> e,                 SourceRange hasPost, SourceRange hasThen,
                  java.util.function.Supplier<List<AbstractFeature>> args)
  {
    _hasPre  = hasPre;
    _hasPost = hasPost;
    _hasPreElse  = hasElse;
    _hasPostThen = hasThen;
    _declared_preconditions   = r1 == null || r1.isEmpty() ? NO_COND : r1;
    _declared_preconditions2  = r2 == null || r2.isEmpty() ? NO_COND : r2;
    _declared_postconditions = e == null || e.isEmpty() ? NO_COND : e;
    _argsSupplier = args;
  }


  /**
   * Constructor use for contract loaded from fum file
   */
  public Contract(List<Cond> r, List<Cond> e)
  {
    this(r, r, null, null, e, null, null, null);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * When redefining a feature, the original contract is inherited with
   * preconditions ORed and postconditions ANDed.  This feature performs this
   * condition inheritance.
   *
   * @param to the redefining feature that inherits a contract
   *
   * @param from the redefined feature this contract should inherit from.
   */
  public void addInheritedContract(Feature to, AbstractFeature from)
  {
    if (PRECONDITIONS) require
      (this == to.contract(),
       this != EMPTY_CONTRACT);

    if (!to.isUniverse())
      {
        to._inheritedPre.add(from);
      }
    if (hasPostConditionsFeature(from))
      {
        to._inheritedPost.add(from);
      }
  }


  /**
   * Does this contract require a pre condition feature due to inherited or declared post
   * conditions?
   */
  boolean requiresPreConditionsFeature(Feature f)
  {
    return _hasPre != null &&
      (!_declared_preconditions.isEmpty() || !f._inheritedPre.isEmpty());
  }


  /**
   * Does this contract require a post condition feature due to inherited or declared post
   * conditions?
   */
  boolean requiresPostConditionsFeature(Feature f)
  {
    return !_declared_postconditions.isEmpty() || !f._inheritedPost.isEmpty();

  }


  /**
   * Does the given feature either have a pre condition feature or, for an
   * dev.flang.ast.Feature, will it get one due to inherited or declared pre
   * conditions?
   *
   * @param f a feature
   */
  static boolean hasPreConditionsFeature(AbstractFeature f)
  {
    return f.preFeature() != null || f instanceof Feature ff && ff.contract().requiresPreConditionsFeature(ff);
  }


  /**
   * Does the given feature either have a post condition feature or, for an
   * dev.flang.ast.Feature, will it get one due to inherited or declared post
   * conditions?
   *
   * @param f a feature
   */
  static boolean hasPostConditionsFeature(AbstractFeature f)
  {
    return f.postFeature() != null || f instanceof Feature ff && ff.contract().requiresPostConditionsFeature(ff);
  }


  private String _preConditionFeatureName = null;
  static String preConditionsFeatureName(Feature f)
  {
    if (PRECONDITIONS) require
      (hasPreConditionsFeature(f));

    var c = f.contract();
    if (c._preConditionFeatureName == null)
      {
        c._preConditionFeatureName = FuzionConstants.PRECONDITION_FEATURE_PREFIX + f.featureName().baseName() +  "_" + (_id_++);
      }
    return c._preConditionFeatureName;
  }
  private String _preBoolConditionFeatureName = null;
  static String preBoolConditionsFeatureName(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (hasPreConditionsFeature(f));

    var c = f.contract();
    if (c._preBoolConditionFeatureName == null)
      {
        c._preBoolConditionFeatureName = FuzionConstants.PRECONDITION_FEATURE_PREFIX + "Bool" + f.featureName().baseName() +  "_" + (_id_++);
      }
    return c._preBoolConditionFeatureName;
  }
  private String _preConditionAndCallFeatureName = null;
  static String preConditionsAndCallFeatureName(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (hasPreConditionsFeature(f));

    var c = f.contract();
    if (c._preConditionAndCallFeatureName == null)
      {
        c._preConditionAndCallFeatureName = FuzionConstants.PRECONDITION_FEATURE_PREFIX + "_AND_CALL_NYI" + f.featureName().baseName() +  "_" + (_id_++);
      }
    return c._preConditionAndCallFeatureName;
  }

  private String _postConditionFeatureName = null;
  static String postConditionsFeatureName(AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (hasPostConditionsFeature(f));

    var c = f.contract();
    if (c._postConditionFeatureName == null)
      {
        c._postConditionFeatureName = FuzionConstants.POSTCONDITION_FEATURE_PREFIX + f.featureName().baseName() +  "_" + (_id_++);
      }
    return c._postConditionFeatureName;
  }


  /**
   * Create call to outer's pre condition feature
   *
   * @param res resolution instance
   *
   * @param outer a feature with a pre condition whose body the result will be
   * added to
   *
   * @return a call to outer.preFeature() to be added to code of outer.
   */
  static Call callPreConditionX(Resolution res, Feature originalOuter)
  {
    return callPreConditionXX(res, originalOuter, originalOuter.preAndCallFeature());
  }


  /**
   * Create call to outer's pre condition feature
   *
   * @param res resolution instance
   *
   * @param outer a feature with a pre condition whose body the result will be
   * added to
   *
   * @return a call to outer.preFeature() to be added to code of outer.
   */
  static Call callPreConditionXX(Resolution res, AbstractFeature originalOuter, AbstractFeature actualOuter)
  {
    var oc = originalOuter.contract();
    var p = oc._hasPre != null ? oc._hasPre : originalOuter.pos();
    List<Expr> args = new List<>();
    for (var a : actualOuter.valueArguments())
      {
        var ca = new Call(p,
                          new Current(p, actualOuter),
                          a,
                          -1);
        ca = ca.resolveTypes(res, actualOuter);
        args.add(ca);
      }
    return callPreCondition(res, originalOuter, (Feature) actualOuter, args);
  }


  /**
   * Create call to outer's pre condition feature to be added to code of feature `in`.
   *
   * @param res resolution instance
   *
   * @param outer a feature with a pre condition
   *
   * @param in either equal to outer or the pre condition feature of a
   * redefinition of outer. The call ot outer's precondition is to be added to
   * in's code.
   *
   * @param args actual arguments to be passed to the call
   *
   * @return a call to outer.preFeature() to be added to code of in.
   */
  private static Call callPreCondition(Resolution res, AbstractFeature outer, Feature in, List<Expr> args)
  {
    var p = in.contract()._hasPre != null
          ? in.contract()._hasPre    // use `pre` position if `in` is of the form `f pre cc is ...`
          : in.pos();                // `in` does not have `pre` clause, only inherits pre conditions. So use the feature position instead

    var t = (in.outerRef() != null) ? new This(p, in, in.outer()).resolveTypes(res, in)
                                    : new Universe();
    if (outer instanceof Feature of)  // if outer is currently being compiled, make sure its post feature is added first
      {
        addContractFeatures(of, res);
      }
    var callPreCondition = new Call(p,
                                    t,
                                    in.generics().asActuals(),
                                    args,
                                    outer.preFeature(),
                                    Types.resolved.t_unit /* NYI: bool? */);
    callPreCondition = callPreCondition.resolveTypes(res, in);
    return callPreCondition;
  }


  /**
   * Create call to outer's pre condition feature
   *
   * @param res resolution instance
   *
   * @param outer a feature with a pre condition whose body the result will be
   * added to
   *
   * @return a call to outer.preFeature() to be added to code of outer.
   */
  static Call callPreBool(Resolution res, AbstractFeature originalOuter, AbstractFeature actualOuter)
  {
    var oc = originalOuter.contract();
    var p = oc._hasPre != null ? oc._hasPre : originalOuter.pos();
    List<Expr> args = new List<>();
    for (var a : actualOuter.valueArguments())
      {
        var ca = new Call(p,
                          new Current(p, actualOuter),
                          a,
                          -1);
        ca = ca.resolveTypes(res, actualOuter);
        args.add(ca);
      }
    return callPreBool(res, originalOuter, (Feature) actualOuter, args);
  }


  /**
   * Create call to outer's pre condition feature to be added to code of feature `in`.
   *
   * @param res resolution instance
   *
   * @param outer a feature with a pre condition
   *
   * @param in either equal to outer or the pre condition feature of a
   * redefinition of outer. The call ot outer's precondition is to be added to
   * in's code.
   *
   * @param args actual arguments to be passed to the call
   *
   * @return a call to outer.preFeature() to be added to code of in.
   */
  private static Call callPreBool(Resolution res, AbstractFeature outer, Feature in, List<Expr> args)
  {
    var p = in.contract()._hasPre != null
          ? in.contract()._hasPre    // use `pre` position if `in` is of the form `f pre cc is ...`
          : in.pos();                // `in` does not have `pre` clause, only inherits pre conditions. So use the feature position instead

    var t = (in.outerRef() != null) ? new This(p, in, in.outer()).resolveTypes(res, in)
                                    : new Universe();
    if (outer instanceof Feature of)  // if outer is currently being compiled, make sure its post feature is added first
      {
        addContractFeatures(of, res);
      }
    var callPreCondition = new Call(p,
                                    t,
                                    in.generics().asActuals(),
                                    args,
                                    outer.preBoolFeature(),
                                    Types.resolved.t_bool);
    callPreCondition = callPreCondition.resolveTypes(res, in);
    return callPreCondition;
  }


  /**
   * Create call to outer's pre condition feature
   *
   * @param res resolution instance
   *
   * @param outer a feature with a pre condition whose body the result will be
   * added to
   *
   * @return a call to outer.preFeature() to be added to code of outer.
   */
  static Call callReal(Resolution res, Feature originalOuter)
  {
    var preAndCallOuter = originalOuter.preAndCallFeature();
    var oc = originalOuter.contract();
    var p = oc._hasPre != null ? oc._hasPre : originalOuter.pos();
    List<Expr> args = new List<>();
    for (var a : preAndCallOuter.valueArguments())
      {
        var ca = new Call(p,
                          new Current(p, preAndCallOuter),
                          a,
                          -1);
        ca = ca.resolveTypes(res, preAndCallOuter);
        args.add(ca);
      }
    var t = new This(p, preAndCallOuter, preAndCallOuter.outer()).resolveTypes(res, preAndCallOuter);
    var callReal = new Call(p,
                            t,
                            preAndCallOuter.generics().asActuals(),
                            args,
                            originalOuter,
                            originalOuter.resultType())
      {
        @Override
        boolean preChecked() { return true; }
      };

    return callReal;
  }


  /**
   * Create call to outer's post condition feature
   *
   * @param res resolution instance
   *
   * @param outer a feature with a post condition whose body the result will be
   * added to
   *
   * @return a call to outer.postFeature() to be added to code of outer.
   */
  static Call callPostCondition(Resolution res, Feature outer)
  {
    var oc = outer.contract();
    var p = oc._hasPost != null ? oc._hasPost : outer.pos();
    List<Expr> args = new List<>();
    for (var a : outer.valueArguments())
      {
        var ca = new Call(p,
                          new Current(p, outer),
                          a,
                          -1);
        ca = ca.resolveTypes(res, outer);
        args.add(ca);
      }
    if (outer.hasResultField())
      {
        var c2 = new Call(p,
                          new Current(p, outer),
                          outer.resultField(),
                          -1);
        c2 = c2.resolveTypes(res, outer);
        args.add(c2);
      }
    else if (outer.isConstructor())
      {
        args.add(new Current(p, outer));
      }
    return callPostCondition(res, outer, outer, args);
  }


  /**
   * Create call to outer's post condition feature to be added to code of feature `in`.
   *
   * @param res resolution instance
   *
   * @param outer a feature with a post condition
   *
   * @param in either equal to outer or the post condition feature of a
   * redefinition of outer. The call ot outer's postcondition is to be added to
   * in's code.
   *
   * @param args actual arguments to be passed to the call
   *
   * @return a call to outer.postFeature() to be added to code of in.
   */
  private static Call callPostCondition(Resolution res, AbstractFeature outer, Feature in, List<Expr> args)
  {
    var p = in.contract()._hasPost != null
          ? in.contract()._hasPost   // use `post` position if `in` is of the form `f post cc is ...`
          : in.pos();                // `in` does not have `post` clause, only inherits post conditions. So use the feature position instead

    var t = (in.outerRef() != null) ? new This(p, in, in.outer()).resolveTypes(res, in)
                                    : new Universe();
    if (outer instanceof Feature of)  // if outer is currently being compiled, make sure its post feature is added first
      {
        addContractFeatures(of, res);
      }
    var callPostCondition = new Call(p,
                                     t,
                                     in.generics().asActuals(),
                                     args,
                                     outer.postFeature(),
                                     Types.resolved.t_unit);
    callPostCondition = callPostCondition.resolveTypes(res, in);
    return callPostCondition;
  }


  private static ParsedCall pc(SourcePosition p, String n)
  {
    return new ParsedCall(new ParsedName(p, n));
  }
  private static ParsedCall pc(Expr t, SourcePosition p, String n)
  {
    return new ParsedCall(t, new ParsedName(p, n));
  }
  private static ParsedCall pc(SourcePosition p, String[] n, List<Expr> a)
  {
    Expr target = null;
    for (var i = 0; i<n.length-1; i++)
      {
        target = pc(target, p, n[i]);
      }
    return new ParsedCall(target, new ParsedName(p, n[n.length-1]), a);
  }

  static String[] fuzion_runtime_precondition_fault = new String[] { "fuzion", "runtime", "precondition_fault" };

  private static void addPreFeature(Feature f, Resolution res, boolean preBool)
  {
    var fc = f.contract();
    var dc = preBool ? fc._declared_preconditions2
                     : fc._declared_preconditions;
    var inhpres = f._inheritedPre;
    var inheritingTrue = inhpres.stream()
                                .filter(inh -> !hasPreConditionsFeature(inh))
                                .findFirst();
    var name = preBool ? preBoolConditionsFeatureName(f) : preConditionsFeatureName(f);
    var args = fc._argsSupplier == null ? null : fc._argsSupplier.get();
    var pos = fc._hasPre != null ? fc._hasPre : f.pos();
    var l = new List<Expr>();
    Expr cc = null;

    for (var c : dc)
      {
        var p = c.cond.pos();
        if (preBool)
          {
            cc = cc == null
              ? c.cond
              : new ParsedCall(cc, new ParsedName(pos, "infix &&"), new List<>(c.cond));
          }
        else
          {
            var cond = c.cond;
            if (inheritingTrue.isPresent())
              { // one of the inherited preconditions is `true`, so we do not
                // need to check the conditions defined locally at all.
                // However, we want to check the condition code for errors etc.,
                // so we wrap it into `(true || <cond>)`
                cond = new ParsedCall(pc(pos, "true"),
                                      new ParsedName(pos, "infix ||"), new List<>(cond));
              }
            l.add(new If(p,
                         cond,
                         new Block(),
                         pc(p, fuzion_runtime_precondition_fault, new List<>(new StrConst(p, p.sourceText())))
                         )
              {
                @Override boolean fromContract() { return true; }
              }
                  );
          }
      }
    if (preBool && cc != null)
      {
        l.add(cc);
      }

    if (inheritingTrue.isPresent() && !dc.isEmpty())
      {
        /*
        var inh = inheritingTrue.get();
        System.err.println("WARNING: For "+f.qualifiedName()+" there are declared preconditions "+dc.getFirst().cond.pos().show()+"\n"+
                           "but these are ignored since we inherit precondition `true` from "+inh+" at "+inh.pos().show());
        */
      }
    var code = new Block(l);
    AbstractType universe_type = null; //new ParsedType(pos, "universe", UnresolvedType.NONE, null);
    var result_type     = new ParsedType(pos,
                                         preBool ? "bool"
                                                 : "unit",
                                         UnresolvedType.NONE,
                                         universe_type);
    var pF = new Feature(pos,
                         f.visibility().eraseTypeVisibility(),
                         // 0, // NYI: why not this:
                         f.modifiers() & FuzionConstants.MODIFIER_FIXED, // modifiers
                         new FunctionReturnType(result_type),
                         new List<>(name),
                         args,
                         new List<>(), // inheritance
                         Contract.EMPTY_CONTRACT,
                         new Impl(pos, code, Impl.Kind.Routine));
    res._module.findDeclarations(pF, f.outer());
    res.resolveDeclarations(pF);
    res.resolveTypes(pF);
    if (preBool)
      {
        f._preBoolFeature = pF;
      }
    else
      {
        f._preFeature = pF;
      }

    // We add calls to preconditions of redefined features after creating pF since
    // this enables us to access pF directly:

    // List<Expr> li = null;
    // var s = inhpres.stream()
    //  .takeWhile(inh -> hasPreconditionsFeature(inh));
    //var c = s.count();
    var new_code = code._expressions;
    if (inheritingTrue.isPresent() || !dc.isEmpty() || preBool)
      { // all inherited are added using
        //
        // if (pre_bool_inh1 || pre_bool_inh2 || ... || pre_bool_inh<n>) then
        // else  check declared
        for (var i = 0; i < inhpres.size() && hasPreConditionsFeature(inhpres.get(i)); i++)
          {
            var call = callPreBool(res, inhpres.get(i), pF);
            cc = cc == null
              ? call
              : new ParsedCall(cc, new ParsedName(pos, "infix ||"), new List<>(call));
          }
      }
    else
      { // The last inherited precondition may cause a fault and is checked using
        //
        // if (pre_bool_inh1 || pre_bool_inh2 || ... || pre_bool_inh<n-1>) then
        // else pre_inh<n>
        for (var i = 0; i < inhpres.size()-1; i++)
          {
            var call = callPreBool(res, inhpres.get(i), pF);
            cc = cc == null
              ? call
              : new ParsedCall(cc, new ParsedName(pos, "infix ||"), new List<>(call));
          }
        if (inhpres.size() == 0)
          {
            System.err.println("NYI: no inherited and no declared preconditions, we should not end up here");
          }
        else
          { // code is empty anyway, replace it by call to pre_inh<n>:
            new_code = new List<>(callPreConditionXX(res, inhpres.getLast(), pF));
          }
      }

    if (preBool && cc != null)
      {
        new_code = new List<>(cc);
      }
    else if (preBool)
      {
        new_code = new List<>(pc(pos, "true"));
      }
    else if (cc != null)
      {
        new_code = new List<>(new If(pos,
                                     cc,
                                     new Block(),
                                     new Block(new_code)));
      }
    code._expressions = new_code;
    var e = res.resolveType(code, pF);
  }


  /**
   * Part of the syntax sugar phase: For all contracts, create artificial
   * features that check that contract.
   */
  static void addContractFeatures(Feature f, Resolution res)
  {
    if (PRECONDITIONS) require
      (f != null,
       res != null,
       Errors.any() || !f.isUniverse() || (f.contract()._declared_preconditions.isEmpty () &&
                                           f.contract()._declared_postconditions.isEmpty()   ));

    var fc = f.contract();

    // add precondition feature
    if (fc.requiresPreConditionsFeature(f) &&
        f._preFeature == null)
      {

        /*
    // tag::fuzion_rule_SEMANTIC_CONTRACT_PRE_ORDER[]
The conditions of a pre-condition are checked at run-time in sequential
source-code order after any inherited pre-conditions have been
checked. +
Inherited pre-conditions of redefined inherited features are checked at
runtime in the source code order of the `inherit` clause of the corresponding
outer features.  +
In case an inherited pre-condition is `false`, the
pre-conditions following the failing one will not be evaluated and checked, but
precondition checking continues with the preconditions of the next inherited
contract, if that exists, or with the declared preconditions after `require else`
in the redefining feature, unless there is no `require else` present.
Redefined inherited features that neither declare nor inherit a precondition will
have `true` as their imlicit precondition, effectively turning the precondition of
all of their redefinition to `true`. +
    // end::fuzion_rule_SEMANTIC_CONTRACT_PRE_ORDER[]
        */

        /* We add three features for every feature with an own or inherited pre-condition as follows:

           pre_<name>      is a feature that checks the precondition and causes a fault in case any condition fails.

                           First, inherited preconditions are checked via cals to their pre_bool_<name> and
                           precondition checking is stopped with success if those return true

                           If there are no own pre-conditions, the last inherited precondition is checked
                           by pre_<name> instead of pre_bool_<name>.

           pre_bool_<name> is a feature that check the precondition and results in true iff all preconditions hold.

                           First, inherited preconditions are checked via cals to their pre_bool_<name> and
                           precondition checking is stopped with success if those return true.

                           Finally, the own pre-condition is checked

           pre_and_call_<name>
                           This calls pre_<name> followed by <name>, just for convenience to avoid
                           duplicate calls in the code

           Example: For a fuzion feature with a precondition as in

             a is
               f(a,b) c
                 pre
                   cc1
                   cc2
               =>
                 x

             z := a.f x y

           we add

             a is

               pre_f(a,b) unit =>
                 if cc1 then else fuzion.runtime.precondition_fault "cc1"
                 if cc2 then else fuzion.runtime.precondition_fault "cc2"

               pre_and_call_f(a,b) c =>
                 pre_f a b
                 f a b

               f(a,b) c
                 pre
                   cc1
                   cc2
               =>
                 x

             z := a.pre_and_Call_f x y

           furthermore, in case of a redefinition

             a is
               f(a,b) c
                 pre
                   cc1
               =>
                 x

             b : a is
               redef f(a,b) c
               =>
                 x

             c is
               f(a,b) c
                 pre
                   cc2
               =>
                 x

             d : a, c is
               redef f(a,b) c
               =>
                 x

             e : a, c is
               redef f(a,b) c
                 pre else
                   cc3
                   cc4
               =>
                 x

             z := b.f x y
             z := c.f x y
             z := d.f x y
             z := e.f x y

           we add

             a is

               pre_f1(a,b) unit =>
                 if cc1 then else fuzion.runtime.precondition_fault "cc1"

               pre_bool_f1(a,b) bool =>
                 cc1

               pre_and_call_f2(a,b) c =>
                 pre_f1 a b
                 f a b

               f(a,b) c
                 pre
                   cc1
               =>
                 x

             b : a is

               pre_f3(a,b) unit =>
                 pre_f1 a b

               pre_bool_f3(a,b) bool =>
                 pre_bool_f1 a b

               pre_and_call_f4(a,b) c =>
                 pre_f3 a b
                 f a b

               redef f(a,b) c
               =>
                 x

             c is

               pre_f5(a,b) unit =>
                 if cc2 then else fuzion.runtime.precondition_fault "cc2"

               pre_bool_f5(a,b) bool =>
                 if pre_bool_f1 a b then true
                 else
                   pre_f5 a b
                   false

               pre_and_call_f6(a,b) c =>
                 pre_f5 a b
                 f a b

               f(a,b) c
                 pre
                   cc2
               =>
                 x

             d : a, c is

               pre_f7(a,b) unit =>
                 if pre_bool_f1 a b then
                 else
                   pre_f5 a b

               pre_bool_f7(a,b) bool =>
                 if      pre_bool_f1 a b then true
                 else if pre_bool_f5 a b then true
                 else                         false

               pre_and_call_f8(a,b) c =>
                 pre_f7 a b
                 f a b

               redef f(a,b) c
               =>
                 x

             e : a, c is

               pre_f9(a,b) unit =>
                 if      pre_bool_f1 a b then
                 else if pre_bool_f5 a b then
                 else if cc3 then else fuzion.runtime.precondition_fault "cc3"
                 else if cc5 then else fuzion.runtime.precondition_fault "cc5"

               pre_bool_f9(a,b) bool =>
                 if      pre_bool_f1 a b then true
                 else if pre_bool_f5 a b then true
                 else if cc3 then
                   cc4

               pre_and_call_f8(a,b) c =>
                 pre_f7 a b
                 f a b

               redef f(a,b) c
                 pre else
                   cc3
               =>
                 x

             z := b.f x y
             z := c.f x y
             z := d.f x y
             z := e.f x y
         */

        addPreFeature(f, res, true);
        addPreFeature(f, res, false);

        if (!f.isConstructor())
          {
            var pos = fc._hasPre != null ? fc._hasPre : f.pos();
            var name2 = preConditionsAndCallFeatureName(f);
            var args2 = fc._argsSupplier.get();
            var l2 = new List<Expr>();
            var code2 = new Block(l2);
            var pF2 = new Feature(pos,
                                  f.visibility().eraseTypeVisibility(),
                                  // 0, // NYI: why not this:
                                  f.modifiers() & FuzionConstants.MODIFIER_FIXED, // modifiers
                                  NoType.INSTANCE,
                                  new List<>(name2),
                                  args2,
                                  new List<>(), // inheritance
                                  Contract.EMPTY_CONTRACT,
                                  new Impl(pos, code2, Impl.Kind.RoutineDef));
            res._module.findDeclarations(pF2, f.outer());
            f._preAndCallFeature = pF2;

            res.resolveDeclarations(pF2);
            l2.add(callPreConditionX(res, f));
            l2.add(callReal(res, f));
            res.resolveTypes(pF2);
          }

      }

    // add postcondition feature
    if (fc.requiresPostConditionsFeature(f) && f._postFeature == null)
      {
        var name = postConditionsFeatureName(f);
        var args = fc._argsSupplier.get();
        var pos = fc._hasPost != null ? fc._hasPost : f.pos();
        var resultField = new Feature(pos,
                                      Visi.PRIV,
                                      f.isConstructor()
                                      ? f.thisType()
                                      : f.resultType(), // NYI: replace type parameter of f by type parameters of _postFeature!
                                      FuzionConstants.RESULT_NAME)
          {
            public boolean isResultField() { return true; }
          };
        args.add(resultField);
        var l = new List<Expr>();
        for (var c : fc._declared_postconditions)
          {
            var p = c.cond.pos();
            l.add(new If(p,
                         c.cond,
                         new Block(),
                         new ParsedCall(new ParsedCall(new ParsedCall(new ParsedName(p, "fuzion")), new ParsedName(p, "runtime")), new ParsedName(p, "postcondition_fault"),
                                        new List<>(new StrConst(p, p.sourceText()))
                                        )
                         )
              {
                @Override boolean fromContract() { return true; }
              }
                  );
          }
        var code = new Block(l);
        var pF = new Feature(pos,
                             f.visibility().eraseTypeVisibility(),
                             f.modifiers() & FuzionConstants.MODIFIER_FIXED, // modifiers
                             NoType.INSTANCE,
                             new List<>(name),
                             args,
                             new List<>(), // inheritance
                             Contract.EMPTY_CONTRACT,
                             new Impl(pos, code, Impl.Kind.RoutineDef));
        res._module.findDeclarations(pF, f.outer());
        res.resolveDeclarations(pF);
        res.resolveTypes(pF);
        f._postFeature = pF;

        /*
    // tag::fuzion_rule_SEMANTIC_CONTRACT_POST_ORDER[]
The conditions of a post-condition are checked at run-time in sequential source-code order after any inherited post-conditions have been checked. Inherited post-conditions of redefined inherited features are checked at runtime in the source code order of the `inherit` clause of the corresponding outer features.
    // end::fuzion_rule_SEMANTIC_CONTRACT_POST_ORDER[]
        */

        // We add calls to postconditions of redefined features after creating pF since
        // this enables us to access pF directly:
        List<Expr> l2 = null;
        for (var inh : f._inheritedPost)
          {
            if (hasPostConditionsFeature(inh))
              {
                List<Expr> args2 = new List<>();
                for (var a : args)
                  {
                    var ca = new Call(pos,
                                      new Current(pos, pF),
                                      a,
                                      -1);
                    ca = ca.resolveTypes(res, pF);
                    args2.add(ca);
                  }
                var inhpost = callPostCondition(res, inh, pF, args2);
                inhpost = inhpost.resolveTypes(res, pF);
                if (l2 == null)
                  {
                    l2 = new List<>();
                  }
                l2.add(inhpost);
              }
          }
        if (l2 != null)
          {
            l2.addAll(code._expressions);
            code._expressions = l2;
          }
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    StringBuffer res = new StringBuffer();
    if (_hasPre != null)
      {
        res
          .append("\n  pre ")
          .append(_hasPreElse != null ? "else " : "")
          .append(_declared_preconditions);
      }
    if (_hasPost != null)
      {
        res
          .append("\n  post ")
          .append(_hasPostThen != null ? "then " : "")
          .append(_declared_postconditions);
      }
    return res.toString();
  }

}

/* end of file */
