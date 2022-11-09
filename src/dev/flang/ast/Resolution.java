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
 * Source of class Feature
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.LinkedList;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;


/**
 * Resolution provides feature resolution for Fuzion.
 *
 * The purpose of feature resolution is to determine the actual types of all
 * fields, expressions and features, to replace syntactic sugar (such as
 * inline function definition) by proper feature declarations, to determine
 * actual generic parameters to form runtime types and to layout objects and
 * frames and to provide the basis for code analysis.
 *
 * Certain errors are detected during resolution, e.g., cyclic inheritance or
 * cyclic nesting of types.
 *
 * Resolution starts with the universe, the outermost feature, and proceeds
 * forward further and further inside. This approach clearly does not scale to
 * large applications with big libraries, so it will be neccessary to revisit
 * this and implement a more on-demand approach once the language is more
 * stable (e.g., by adding singleton features lazily only when they are
 * referenced).
 *
 * Resolution consists of the following steps
 *
 * 1. Start by creating the universe and add it to the set of features to be
 *    resolved for inheritance.
 *
 * 2. Take the first feature of the list of features to be inheritance
 *    resolved and perform inheritance resolution for it.
 *
 * 3. Inheritance resolution for a feature f: recursively, perform inheritance
 *    resolution for the outer feature of f and for all direct ancestors of a
 *    f, then perform inheritance resolution of the feature f itself.
 *
 * 4. after inheritance resolution for a feature f, add it to the set of
 *    features to be type resolved.
 *
 * 5. As long as there are features to be inheritance resolved, got to step
 *    2. Otherwise, take the first feature of the list of features to be
 *    resolved for declarations.
 *
 * 6. Declaration resolution for a feature f: For all declarations of features
 *    in f (formal arguments, local features, implicit result field), add these
 *    features to the set of features to be resolved for inheritance. Schedule f
 *    for type resolution.
 *
 * 7. As long as there are features to be declaration resolved, got to step
 *    6. Otherwise, take the first feature of the list of features to be
 *    resolved for types.
 *
 * 8. Type resolution for a feature f: For all expressions and statements in f's
 *    inheritance clause, contract, and implementation, determine the static
 *    type of the expression. Were needed, perform type inference. Schedule f
 *    for syntactic sugar resolution.
 *
 * 9. If there are any features schedule for inheritance, declaration or type
 *    resolution, go to step 7. Otherwise, take the first feature of the list of
 *    features to be syntactic sugar resolved.
 *
 * 10. Syntactic sugar resolution of a feature f: For all expressions and
 *     statements in f's inheritance clause, contract, and implementation,
 *     resolve syntactic sugar, e.g., by replacing anonymous inner functions by
 *     declaration of corresponding inner features. Add (f,<>) to the list of
 *     features to be searched for runtime types to be layouted.
 *
 * 11. If there are any features scheduled for inheritance, declaration. type,
 *     or syntactic sugar resolution, go to step 9. Otherwise, take the first
 *     entry (f,G) from the list of features with actual generics to be searched
 *     for runtime types to be layouted.
 *
 * 10. Searching for runtime types for a feature f with actual generics G: For
 *     all expressions and statements in f's inheritance clause, contract, and
 *     implementation, find declarations and calls to features f1 with actual
 *     generic arguments G1. Add all found (f1,G1) to the set of runtime types
 *     to be layouted.
 *
 * 11. If there are any features scheduled for inheritance, declaration. type,
 *     syntactic sugar resolution, or entries in the list of features to be
 *     searched for runtime types, go to step 10. Otherwise, take a type (f,G)
 *     from the list of runtime types to be layouted and start the layout.
 *
 * 12. Recursive layout of (f,G): For all fields in f, determine the actual
 *     type using the actual generic parameters. For actual types that are
 *     non-references, recursively perform the layout if that has not been
 *     done yet. Flag an error for recursive value types in case this leads to
 *     an endless recursion.
 *
 * 13. Now that all fields in f are layouted, perform the layout of (f,G) as a
 *     needed as a stack frame, value type, and a heap allocated object. For
 *     this, analyse the code for locations were fields accessible after the
 *     feature call is done, e.g., since they are visible outside of f or they
 *     escape as part of the closure of an inner feature of f.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Resolution extends ANY
{


  /*----------------------------  variables  ----------------------------*/


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
   * List of features scheduled for boxing
   */
  final LinkedList<Feature> forBoxing = new LinkedList<>();

  /**
   * List of features scheduled for type checking
   */
  final LinkedList<Feature> forCheckTypes1 = new LinkedList<>();

  /**
   * List of features scheduled for second syntactic sugar resolution
   */
  final LinkedList<Feature> forSyntacticSugar2 = new LinkedList<>();

  /**
   * List of features scheduled for second pass of type checking
   */
  final LinkedList<Feature> forCheckTypes2 = new LinkedList<>();


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
      (f.state() == Feature.State.RESOLVING);

    forInheritance.add(f);
  }


  /**
   * Add a feature to the set of features schedule for type resolution
   */
  void scheduleForDeclarations(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.RESOLVED_INHERITANCE);

    forDeclarations.add(f);
  }


  /**
   * Add a feature to the set of features schedule for type resolution
   */
  void scheduleForTypeResolution(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.RESOLVED_DECLARATIONS);

    forType.add(f);
  }


  /**
   * Add a feature to the set of features scheduled for syntactic sugar
   * resolution
   */
  void scheduleForSyntacticSugar1Resolution(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.RESOLVED_TYPES);

    forSyntacticSugar1.add(f);
  }


  /**
   * Add a feature to the set of features schedule for type resolution
   */
  void scheduleForTypeInteference(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.RESOLVED_SUGAR1);

    forTypeInference.add(f);
  }


  /**
   * Add a feature to the set of features schedule for boxing
   */
  void scheduleForBoxing(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.TYPES_INFERENCED);

    forBoxing.add(f);
  }



  /**
   * Add a feature to the set of features schedule for type checking
   */
  void scheduleForCheckTypes1(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.BOXED);

    forCheckTypes1.add(f);
  }


  /**
   * Add a feature to the set of features scheduled for syntactic sugar
   * resolution
   */
  void scheduleForSyntacticSugar2Resolution(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.CHECKED_TYPES1);

    forSyntacticSugar2.add(f);
  }


  /**
   * Add a feature to the set of features schedule for 2nd type checking after
   * syntactic sugar resolution. This is just a safety feature to check that the
   * syntactic sugar resolution did not introduce any violations to the type
   * system
   */
  void scheduleForCheckTypes2(Feature f)
  {
    if (PRECONDITIONS) require
      (f.state() == Feature.State.RESOLVED_SUGAR2);

    forCheckTypes2.add(f);
  }


  /**
   * Resolve all entries in the lists for resolution (forInheritance, etc.) up
   * to state RESOLVED_TYPES.
   */
  void resolveTypes()
  {
    while (resolveOne(false));
  }


  /**
   * Resolve all entries in the lists for resolution (forInheritance, etc.)
   */
  public void resolve()
  {
    while (resolveOne(true));
  }


  /**
   * Resolve one entry in the lists for resolution (forInheritance, etc.)
   *
   * @param moreThanTypes true to fully resolve everything, false to resolve
   * everything to be at least type resolved.
   *
   * @return true if one such entry was found.
   */
  private boolean resolveOne(boolean moreThanTypes)
  {
    boolean result = true;
    if (!forInheritance.isEmpty())
      {
        Feature f = forInheritance.removeFirst();
        f.resolveInheritance(this);
      }
    else if (!forDeclarations.isEmpty())
      {
        Feature f = forDeclarations.removeFirst();
        f.resolveDeclarations(this);
      }
    else if (!forType.isEmpty())
      {
        if (Types.resolved == null)
          {
            new Types.Resolved(this, universe);
          }

        Feature f = forType.removeFirst();
        f.internalResolveTypes(this);
      }
    else if (!moreThanTypes)
      {
        result = false;
      }
    else if (!forSyntacticSugar1.isEmpty())
      {
        Feature f = forSyntacticSugar1.removeFirst();
        f.resolveSyntacticSugar1(this);
      }
    else if (!forTypeInference.isEmpty())
      {
        Feature f = forTypeInference.removeFirst();
        f.typeInference(this);
      }
    else if (!forBoxing.isEmpty())
      {
        Feature f = forBoxing.removeFirst();
        f.box(this);
      }
    else if (!forCheckTypes1.isEmpty())
      {
        Feature f = forCheckTypes1.removeFirst();
        f.checkTypes1and2(this);
      }
    else if (!forSyntacticSugar2.isEmpty())
      {
        Feature f = forSyntacticSugar2.removeFirst();
        f.resolveSyntacticSugar2(this);
      }
    else if (!forCheckTypes2.isEmpty())
      {
        Feature f = forCheckTypes2.removeFirst();
        f.checkTypes1and2(this);
      }
    else if (false && Errors.count() > 0)  // NYI: We could give up here in case of errors, we do not to make the next phases more robust and to find more errors at once
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
   * @param f the feature to be resolved
   */
  public void resolveDeclarations(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (af.state().atLeast(Feature.State.LOADED));

    if (af instanceof Feature f)
      {
        f.scheduleForResolution(this);
        f.resolveInheritance(this);
        f.resolveDeclarations(this);
      }

    if (POSTCONDITIONS) ensure
      (af.state().atLeast(Feature.State.RESOLVED_DECLARATIONS));
  }


  /**
   * Make sure feature f is in state RESOLVED_TYPES. This is used for
   * recursive resolution of artificially added features during
   * RESOLVING_TYPES.
   *
   * @param f the feature to be resolved
   */
  void resolveTypes(AbstractFeature af)
  {
    if (PRECONDITIONS) require
      (af.state().atLeast(Feature.State.LOADED));

    if (af instanceof Feature f)
      {
        resolveDeclarations(f);
        f.internalResolveTypes(this);
      }

    if (POSTCONDITIONS) ensure
      (Errors.count() > 0 || af.state().atLeast(Feature.State.RESOLVED_TYPES));
  }


  /**
   * Resolve the type of statement s within outer
   *
   * @param s a statement
   *
   * @param outer the outer feature that contains s
   *
   * @return s or a new statement that replaces s after type resolution.
   */
  Stmnt resolveType(Stmnt s, AbstractFeature outer)
  {
    var rt = new Feature.ResolveTypes(this);
    return s.visit(rt, outer);
  }


  /**
   * Resolve the type of expression s within outer
   *
   * @param s an expression
   *
   * @param outer the outer feature that contains s
   *
   * @return s or a new expression that replaces s after type resolution.
   */
  Expr resolveType(Expr s, AbstractFeature outer)
  {
    var rt = new Feature.ResolveTypes(this);
    return s.visit(rt, outer);
  }

}

/* end of file */
