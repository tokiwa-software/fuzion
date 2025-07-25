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
 * Source of class Resolution
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.LinkedList;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;


/**
 * Resolution provides feature resolution for Fuzion.<p>
 *
 * The purpose of feature resolution is to determine the actual types of all
 * fields, expressions and features, to replace syntactic sugar (such as
 * inline function definition) by proper feature declarations, to determine
 * actual generic parameters to form runtime types and to layout objects and
 * frames and to provide the basis for code analysis.<p>
 *
 * Certain errors are detected during resolution, e.g., cyclic inheritance or
 * cyclic nesting of types.<p>
 *
 * Resolution starts with the universe, the outermost feature, and proceeds
 * forward further and further inside. This approach clearly does not scale to
 * large applications with big libraries, so it will be necessary to revisit
 * this and implement a more on-demand approach once the language is more
 * stable (e.g., by adding singleton features lazily only when they are
 * referenced).<p>
 *
 * Resolution consists of the following steps<p>
 *
 * 1. Start by creating the universe and add it to the set of features to be
 *    resolved for inheritance.<p>
 *
 * 2. Take the first feature of the list of features to be inheritance
 *    resolved and perform inheritance resolution for it.<p>
 *
 * 3. Inheritance resolution for a feature f: recursively, perform inheritance
 *    resolution for the outer feature of f and for all direct ancestors of a
 *    f, then perform inheritance resolution of the feature f itself.<p>
 *
 * 4. after inheritance resolution for a feature f, add it to the set of
 *    features to be type resolved.<p>
 *
 * 5. As long as there are features to be inheritance resolved, go to step
 *    2. Otherwise, take the first feature of the list of features to be
 *    resolved for declarations.<p>
 *
 * 6. Declaration resolution for a feature f: For all declarations of features
 *    in f (formal arguments, local features, implicit result field), add these
 *    features to the set of features to be resolved for inheritance. Schedule f
 *    for type resolution.<p>
 *
 * 7. As long as there are features to be declaration resolved, go to step
 *    6. Otherwise, take the first feature of the list of features to be
 *    resolved for types.<p>
 *
 * 8. Type resolution for a feature f: For all expressions and expressions in f's
 *    inheritance clause, contract, and implementation, determine the static
 *    type of the expression. Were needed, perform type inference. Schedule f
 *    for syntactic sugar resolution.<p>
 *
 * 9. If there are any features schedule for inheritance, declaration or type
 *    resolution, go to step 7. Otherwise, take the first feature of the list of
 *    features to be syntactic sugar resolved.<p>
 *
 * 10. Syntactic sugar resolution of a feature f: For all expressions and
 *     expressions in f's inheritance clause, contract, and implementation,
 *     resolve syntactic sugar, e.g., by replacing anonymous inner functions by
 *     declaration of corresponding inner features. Add (f,{@literal <>}) to the list of
 *     features to be searched for runtime types to be layouted.<p>
 *
 * 11. If there are any features scheduled for inheritance, declaration, type,
 *     or syntactic sugar resolution, go to step 9. Otherwise, take the first
 *     entry (f,G) from the list of features with actual generics to be searched
 *     for runtime types to be layouted.<p>
 *
 * 10. Searching for runtime types for a feature f with actual generics G: For
 *     all expressions and expressions in f's inheritance clause, contract, and
 *     implementation, find declarations and calls to features f1 with actual
 *     generic arguments G1. Add all found (f1,G1) to the set of runtime types
 *     to be layouted.<p>
 *
 * 11. If there are any features scheduled for inheritance, declaration. type,
 *     syntactic sugar resolution, or entries in the list of features to be
 *     searched for runtime types, go to step 10. Otherwise, take a type (f,G)
 *     from the list of runtime types to be layouted and start the layout.<p>
 *
 * 12. Recursive layout of (f,G): For all fields in f, determine the actual
 *     type using the actual generic parameters. For actual types that are
 *     non-references, recursively perform the layout if that has not been
 *     done yet. Flag an error for recursive value types in case this leads to
 *     an endless recursion.<p>
 *
 * 13. Now that all fields in f are layouted, perform the layout of (f,G) as a
 *     needed as a stack frame, value type, and a heap allocated object. For
 *     this, analyze the code for locations were fields accessible after the
 *     feature call is done, e.g., since they are visible outside of f or they
 *     escape as part of the closure of an inner feature of f.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Resolution extends ANY
{

  /* flag to control debug output */
  private static final boolean DEBUG = "true".equals(FuzionOptions.propertyOrEnv("dev.flang.ast.Resolution.DEBUG"));


  /*------------------------  static variables  -------------------------*/


  /**
   * Counter for assigning unique names to cotype() results. This is
   * currently used only for non-constructors since they do not create a type
   * name.
   */
  private int _cotypeId_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * FeatureVisitor to call resolve() on all types.
   *
   * This is used during state RESOLVING_DECLARATIONS to find called features.
   */
  FeatureVisitor resolveTypesOnly(AbstractFeature f)
  {
    var c = f.context();
    return new FeatureVisitor()
      {
        @Override public AbstractType action(AbstractType t) { return t.resolve(Resolution.this, c); }
      };
  }

  Feature.ResolveTypes resolveTypesFully(AbstractFeature f)
  {
    return new Feature.ResolveTypes(this, f.context());
  }


  final FuzionOptions _options;


  final AbstractFeature universe;


  public final SrcModule _module;


  /**
   * List of features scheduled for inheritance resolution
   */
  final LinkedList<Feature> forInheritance = new LinkedList<>();

  /**
   * List of features scheduled for resolution for declarations
   */
  final LinkedList<Feature> forDeclarations = new LinkedList<>();

  /**
   * List of features waiting for calls since argument types are inferred from
   * actual arguments. These will need to be resolved for declarations next.
   */
  final LinkedList<Feature> _waitingForCalls = new LinkedList<>();

  /**
   * List of features scheduled for type resolution
   */
  final LinkedList<Feature> forType = new LinkedList<>();

  /**
   * List of features scheduled for first syntactic sugar resolution
   */
  final LinkedList<Feature> forSyntacticSugar1 = new LinkedList<>();

  /**
   * List of features scheduled for type inference
   */
  final LinkedList<Feature> forTypeInference = new LinkedList<>();

  /**
   * List of features scheduled for second syntactic sugar resolution
   */
  final LinkedList<Feature> forSyntacticSugar2 = new LinkedList<>();

  /**
   * List of features scheduled for second pass of type checking
   */
  final LinkedList<Feature> forCheckTypes = new LinkedList<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor to Resolve the universe.
   */
  public Resolution(FuzionOptions options,
                    AbstractFeature universe,
                    SrcModule sm)
  {
    this.universe = universe;
    this._options = options;
    this._module = sm;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add a feature to the first step of resolution, i.e, for inheritance.
   */
  void add(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.RESOLVING);

    forInheritance.add(f);
  }


  /**
   * Add a feature to the set of features schedule for type resolution
   */
  void scheduleForDeclarations(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.RESOLVED_INHERITANCE);

    if (requiresCall(f))
      {
        _waitingForCalls.add(f);
      }
    else
      {
        forDeclarations.add(f);
      }
  }


  /**
   * If there are features in waitingForCalls that still require calls for type
   * resolution, then try to find those that meanwhile have found a call.  Only
   * if none were found, start resolving those without calls (i.e., argument
   * types will end up being void).
   */
  Feature resolveDelayed()
  {
    var f = _waitingForCalls.removeFirst();
    var first = f;
    var cycling = false;
    while (requiresCall(f) && !cycling)
      {
        _waitingForCalls.add(f);
        f = _waitingForCalls.removeFirst();
        cycling = f == first;
      }
    return f;
  }


  /**
   * Does given feature have value arguments whose type is inferred from a call.
   * If so, we delay type resolution for f until when such a call is found.
   *
   * @param f a feature that was resolved for declarations.
   */
  boolean requiresCall(Feature f)
  {
    return
      f.valueArguments().stream()
        .anyMatch(a -> a instanceof Feature af &&
                  af.impl()._kind == Impl.Kind.FieldActual &&
                  af.impl()._initialCalls.isEmpty()           ) ||
      f.outer() instanceof Feature of && !of.isUniverse() && requiresCall(of);
  }


  /**
   * Add a feature to the set of features schedule for type resolution
   */
  void scheduleForTypeResolution(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.RESOLVED_DECLARATIONS);

    forType.add(f);
  }


  /**
   * Add a feature to the set of features scheduled for syntactic sugar
   * resolution
   */
  void scheduleForSyntacticSugar1Resolution(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.RESOLVED_TYPES);

    forSyntacticSugar1.add(f);
  }


  /**
   * Add a feature to the set of features schedule for type resolution
   */
  void scheduleForTypeInference(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.RESOLVED_SUGAR1);

    forTypeInference.add(f);
  }


  /**
   * Add a feature to the set of features schedule for type checking
   */
  void scheduleForCheckTypes(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.RESOLVED_SUGAR2);

    forCheckTypes.add(f);
  }


  /**
   * Add a feature to the set of features scheduled for syntactic sugar
   * resolution
   */
  void scheduleForSyntacticSugar2Resolution(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == State.TYPES_INFERENCED);

    forSyntacticSugar2.add(f);
  }


  /**
   * Resolve all entries in the lists for resolution (forInheritance, etc.)
   */
  public void resolve()
  {
    while (resolveOne());
  }


  /**
   * Resolve one entry in the lists for resolution (forInheritance, etc.)
   *
   * @return true if one such entry was found.
   */
  private boolean resolveOne()
  {
    boolean result = true;
    if (!forInheritance.isEmpty())
      {
        Feature f = forInheritance.removeFirst();
        if (DEBUG) sayDebug("resolve inheritance: " + f);
        f.resolveInheritance(this);
      }
    else if (!forDeclarations.isEmpty())
      {
        Feature f = forDeclarations.removeFirst();
        if (DEBUG) sayDebug("resolve declarations: " + f);
        f.resolveDeclarations(this);
      }
    else if (!forType.isEmpty())
      {
        if (Types.resolved == null)
          {
            new Types.Resolved(this, universe);
          }

        Feature f = forType.removeFirst();
        if (DEBUG) sayDebug("resolve types: " + f);
        if (f.isCotype())
          {
            resolveTypes(f.cotypeOrigin());
          }
        f.internalResolveTypes(this);
      }
    else if (!forSyntacticSugar1.isEmpty())
      {
        Feature f = forSyntacticSugar1.removeFirst();
        if (DEBUG) sayDebug("resolve syntax sugar 1: " + f);
        f.resolveSyntacticSugar1(this);
      }
    else if (!forTypeInference.isEmpty())
      {
        Feature f = forTypeInference.removeFirst();
        if (DEBUG) sayDebug("resolve type inference: " + f);
        f.typeInference(this);
      }
    else if (!_waitingForCalls.isEmpty())
      {
        // there are some features that still require calls for type resolution,
        // so try to find those fot which we have found a call meanwhile.  Only if none
        // was found, start resolving declarations anyways.
        resolveDelayed().resolveDeclarations(this);
      }
    else if (!forSyntacticSugar2.isEmpty())
      {
        Feature f = forSyntacticSugar2.removeFirst();
        if (!_options.isLanguageServer())
          {
            if (DEBUG) sayDebug("resolve syntax sugar 2: " + f);
            f.resolveSyntacticSugar2(this);
          }
      }
    else if (!forCheckTypes.isEmpty())
      {
        Feature f = forCheckTypes.removeFirst();
        if (DEBUG) sayDebug("resolve check types: " + f);
        f.checkTypes(this);
      }
    else if (false && Errors.any())  // NYI: We could give up here in case of errors, we do not to make the next phases more robust and to find more errors at once
      {
        // The following phases should not reveal any new errors and will assume
        // correct input.  So if there were any errors, let's give up at this
        // point:
        result = false;
      }
    else
      {
        result = false;
      }
    return result;
  }


  /**
   * Make sure feature f is in state RESOLVED_DECLARATIONS. This is used for
   * recursive resolution during RESOLVING_TYPES when declarations in a
   * referenced feature are needed.
   *
   * @param af the feature to be resolved
   */
  public void resolveDeclarations(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (state(af).atLeast(State.LOADED),
       af != Types.f_ERROR);

    if (af instanceof Feature f)
      {
        f.scheduleForResolution(this);
        f.resolveInheritance(this);
        f.resolveDeclarations(this);
      }

    if (POSTCONDITIONS) ensure
      (state(af).atLeast(State.RESOLVING_DECLARATIONS));
  }


  /**
   * Make sure feature f is in state RESOLVED_TYPES. This is used for
   * recursive resolution of artificially added features during
   * RESOLVING_TYPES.
   *
   * @param af the feature to be resolved
   */
  void resolveTypes(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (state(af).atLeast(State.LOADED));

    if (af instanceof Feature f)
      {
        resolveDeclarations(f);
        f.internalResolveTypes(this);
      }

    if (POSTCONDITIONS) ensure
      (Errors.any() || state(af).atLeast(State.RESOLVING_TYPES));
  }


  /**
   * Resolve the type of expression s within outer
   *
   * @param e an expression
   *
   * @param context the source code context where e is used
   *
   * @return e or a new expression that replaces e after type resolution.
   */
  Expr resolveType(Expr e, Context context)
  {
    if (PRECONDITIONS) require
      (context != null);

    return e.visit(new Feature.ResolveTypes(this, context),
                   context.outerFeature());
  }


  /**
   * Returns the state the feature {@code af} is in
   * w.r.t. this resolution.
   */
  public State state(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (af != null,
       af != Types.f_ERROR);

    return af.state();
  }


  /**
   * Ask the resolution for the resulttype of feature f.
   * returns null if result type is not known yet.
   */
  public AbstractType resultTypeIfPresent(Feature f)
  {
    return f.resultTypeIfPresent(this);
  }


  /**
   * For every feature 'f', this produces the corresponding type feature
   * 'f.type'.  This feature inherits from the abstract type features of all
   * direct ancestors of this, and, if there are no direct ancestors (for
   * Object), this inherits from 'Type'.
   *
   * @return The feature that should be the direct ancestor of this feature's
   * type feature.
   */
  public AbstractFeature cotype(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (Errors.any() || !af.isUniverse(),
       Errors.any() || state(af).atLeast(State.RESOLVED_DECLARATIONS),
       !af.isCotype());

    if (af._cotype == null)
      {
        resolveDeclarations(af);
        if (af.hasCotype())
          {
            af._cotype = af.cotype();
          }
        else if (af.isUniverse())
          {
            if (CHECKS) check
              (Errors.any());
            af._cotype = Types.f_ERROR;
          }
        else
          {
            var name = af.featureName().baseName() + ".";
            if (!af.isConstructor() && !af.isChoice())
              {
                name = name + "_" + (_cotypeId_++) + "_" + _module.name();
              }
            name = name + FuzionConstants.TYPE_NAME;

            var p = af.pos();
            var inh = cotypeInherits(af);
            var typeArg = new Feature(p,
                                      Visi.PRIV,
                                      0,
                                      af.selfType(),
                                      FuzionConstants.COTYPE_THIS_TYPE,
                                      Contract.EMPTY_CONTRACT,
                                      Impl.TYPE_PARAMETER)
              {
                @Override
                public boolean isCoTypesThisType()
                {
                  return true;
                }
              };
            var typeArgs = new List<AbstractFeature>(typeArg);
            for (var t : af.typeArguments())
              {
                var i = t.isOpenTypeParameter() ? Impl.TYPE_PARAMETER_OPEN
                                                : Impl.TYPE_PARAMETER;
                var constraint0 = (t instanceof Feature tf ? tf.returnType().functionReturnType() : t.resultType())
                  .resolve(this, af.context());
                var constraint = af.rebaseTypeForCotype(constraint0);
                var ta = new Feature(p, t.visibility(), t.modifiers() & FuzionConstants.MODIFIER_REDEFINE, constraint, t.featureName().baseName(),
                                     Contract.EMPTY_CONTRACT,
                                     i);
                typeArgs.add(ta);
              }

            if (inh.isEmpty() && !Errors.any())
              { // let `Any.type` inherit from `Type`
                if (CHECKS) check
                  (af instanceof Feature && af.featureName().baseName().equals(FuzionConstants.ANY_NAME));
                inh.add(new Call(af.pos(), FuzionConstants.TYPE_FEAT));
              }
            existingOrNewCotype(af, name, typeArgs, inh);
          }
      }
    return af._cotype;
  }


  /**
   * Helper method for cotype() to create the list of inherits calls of
   * this' type feature.
   */
  private List<AbstractCall> cotypeInherits(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (af.state().atLeast(State.RESOLVED_INHERITANCE));

    return af.inherits()
      .stream()
      .filter(pc -> pc.calledFeature() != Types.f_ERROR)
      .map(pc -> pc.cotypeInheritanceCall(this, af))
      .collect(List.collector());
  }


  /**
   * Helper method for cotype to create a new feature with given name and
   * inherits clause iff no such feature exists in outer().cotype().
   *
   * The new type feature will be stored in _cotype.
   *
   * @param name the name of the type feature to be created
   *
   * @param typeArgs arguments of the type feature.
   * NYI: OPTIMIZATION: typeArgs should be determined within this method and
   * only when needed.
   *
   * @param inh the inheritance clause of the new type feature.
   */
  private void existingOrNewCotype(AbstractFeature af, String name, List<AbstractFeature> typeArgs, List<AbstractCall> inh)
  {
    if (PRECONDITIONS) require
      (!af.isUniverse(),
       Errors.any() || state(af).atLeast(State.RESOLVED_DECLARATIONS));

    var outerType = af.outer().isUniverse() ? universe :
                    af.outer().isCotype()   ? af.outer()
                                            : cotype(af.outer());

    af._cotype = _module
      .declaredOrInheritedFeatures(outerType,
                                   FeatureName.get(name, typeArgs.size()))
      .getFirstOrNull();

    if (af._cotype == null)
      {
        var p = af.pos();
        var cotype = new Feature(p, af.visibility().typeVisibility(), 0, NoType.INSTANCE, new List<>(name), typeArgs,
                                      inh,
                                      Contract.EMPTY_CONTRACT,
                                      new Impl(p, new Block(new List<>()), Impl.Kind.Routine));

        // we need to set _cotype early to avoid endless recursion during
        // res._module.addCotype for `Any.type`:
        af._cotype = cotype;

        cotype._cotypeOrigin = af;
        _module.addCotype(outerType, cotype);
      }
  }


  /**
   * Convenience function to resolve all types in a list of types.
   *
   * @param types the types to resolve
   *
   * @param context the context to use when resolving the types
   *
   * @return a list of resolved types
   */
  public List<AbstractType> resolveTypes(List<AbstractType> types, Context context)
  {
    if (!(types instanceof FormalGenerics.AsActuals))
      {
        types = types.map(t -> t.resolve(this, context));
      }
    return types;
  }


}

/* end of file */
