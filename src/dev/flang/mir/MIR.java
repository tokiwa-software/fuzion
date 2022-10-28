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
 * Source of class MIR
 *
 *---------------------------------------------------------------------*/

package dev.flang.mir;

import java.nio.ByteBuffer;

import dev.flang.ast.AbstractAssign;  // NYI: Remove dependency!
import dev.flang.ast.AbstractCall;  // NYI: Remove dependency!
import dev.flang.ast.AbstractFeature;  // NYI: Remove dependency!

import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.Map2Int;
import dev.flang.util.MapComparable2Int;


/**
 * The MIR contains the module-intermediate representation of a Fuzion module.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MIR extends IR
{



  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The main feature
   */
  final AbstractFeature _main;
  final AbstractFeature _universe;

  public final MirModule _module;


  /**
   * integer ids for features in this module
   */
  final Map2Int<AbstractFeature> _featureIds = new MapComparable2Int(FEATURE_BASE);


  /*--------------------------  constructors  ---------------------------*/


  public MIR(AbstractFeature universe, AbstractFeature main, MirModule module)
  {
    _universe = universe;
    _main = main;
    _module = module;
    addFeatures();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The main Feature.
   */
  public AbstractFeature main()
  {
    return _main;
  }


  /**
   * Create the mapping from Features to integers exists.
   */
  private void addFeatures()
  {
    if (_featureIds.size() == 0)
      {
        var u = universe();
        addFeatures(u);
      }
  }


  /**
   * Helper to addFeatures() to add feature f and all features declared within f.
   */
  private void addFeatures(AbstractFeature f)
  {
    _featureIds.add(f);
    for (var i : _module.declaredFeatures(f).values())
      {
        addFeatures(i);
      }
  }


  /**
   * The first feature in this module.
   */
  public int firstFeature()
  {
    return FEATURE_BASE;
  }


  /**
   * The last feature in this module.
   */
  public int lastFeature()
  {
    return FEATURE_BASE + _featureIds.size() - 1;
  }


  public AbstractFeature universe() {
    return _universe;
  }


  /**
   * Code for a routine or precondition prolog.
   *
   * This adds code to initialize outer reference, must be done at the
   * beginning of every routine and precondition.
   *
   * @param f the feature id of the routine we need the code for.
   *
   * @return the code
   */
  private List<Object> prolog(AbstractFeature f)
  {
    List<Object> code = new List<>();
    // NYI: MIR.prolog
    /* code used in FUIR.java:
    var vcc = cc.asValue();
    var or = vcc.outerRef();
    var cco = cc._outer;
    if (or != null && !cco.isUnitType())
      {
        code.add(ExprKind.Outer);
        code.add(ExprKind.Current);
        code.add(or);
      }
    */
    return code;
  }


  /**
   * add the code of feature ff to code.  In case ff has inherits calls, also
   * include the code of the inherited features.
   *
   * @param code a list that code should be added to.
   *
   * @param ff a routine or constructor feature.
   */
  private void addCode(AbstractFeature heir, List<Object> code, AbstractFeature ff)
  {
    for (var p: ff.inherits())
      {
        /*
NYI: Any side-effects in p.target or p.actuals() will be executed twice, once for
     the precondition and once for the inlinded call! See this example:

hw25 is
  A (a i32)
    pre
      a < 100
  is
    say "in A: $a"

  B : A x is

  count := 0

  x =>
    set count := count + 1
    count

  B; B; B
  if (count == 3) say "PASS" else say "FAIL"
        */

        toStack(code, p.target());
        var pf = p.calledFeature();
        /* NYI: initialize outer ref

        var or = cc._inner.get(pf.outerRef_);
        if (or != null && !or.resultClazz().isUnitType())
          {
            if (!or.resultClazz().isRef() &&
                !or.resultClazz().feature().isBuiltInPrimitive())
              {
                code.add(ExprKind.AdrOf);
              }
            code.add(ExprKind.Dup);
            code.add(ExprKind.Current);
            code.add(or);  // field clazz means assignment to field
          }
        */

        if (CHECKS) check
          (p.actuals().size() == p.calledFeature().arguments().size());
        for (var i = 0; i < p.actuals().size(); i++)
          {
            var a = p.actuals().get(i);
            var f = pf.arguments().get(i);
            toStack(code, a);
            code.add(ExprKind.Current);
            // Field clazz means assign value to that field
            // NYI: code.add((Clazz) cc.getRuntimeData(p._parentCallArgFieldIds + i));
          }
        addCode(ff, code, p.calledFeature());
      }
    toStack(code, ff.code());
  }


  /**
   * Get access to the code of a feature
   *
   * @param f a feature id
   *
   * @return a code id referring to f's code
   */
  public int featureCode(int f)
  {
    if (PRECONDITIONS) require
      (featureKind(f) == FeatureKind.Routine);

    var ff = _featureIds.get(f);
    var code = prolog(ff);
    addCode(ff, code, ff);
    return _codeIds.add(code);
  }


  /**
   * Determine the kind of a given feature.
   *
   * @param f a feature index
   *
   * @return the kind of that feature, FeatureKind.Choice, Routine, Field, etc.
   */
  public FeatureKind featureKind(int f)
  {
    var ff = _featureIds.get(f);

    return switch (ff.kind())
      {
      case Routine   -> FeatureKind.Routine;
      case Field     -> FeatureKind.Field;
      case Intrinsic -> FeatureKind.Intrinsic;
      case Abstract  -> FeatureKind.Abstract;
      case Choice    -> FeatureKind.Choice;
      default        -> throw new Error ("Unexpected feature impl kind: " + ff.kind());
      };
  }


  /**
   * Get a string representatin of a a given feature, for debugging only
   *
   * @param f a feature index
   *
   * @return a string identifying f.
   */
  public String featureAsString(int f)
  {
    return f == -1
      ? "-- no feature --"
      : _featureIds.get(f).qualifiedName();
  }


  /**
   * Get the accessed feature for a non dynamic access or the static clazz of a
   * dynamic access.
   *
   * @param f index of feature containing the access
   *
   * @param c code block containing the access
   *
   * @param ix index of the access
   *
   * @return the feature that has to be accessed or -1 if the access is an
   * assignment to a field that is unused, so the assignment is not needed.
   */
  public int accessedFeature(int f, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       withinCode(c, ix),
       codeAt(c, ix) == ExprKind.Call   ||
       codeAt(c, ix) == ExprKind.Assign    );

    var ff = _featureIds.get(f);
    var s = _codeIds.get(c).get(ix);
    var af =
      (s instanceof AbstractCall   call) ? call.calledFeature() :
      (s instanceof AbstractAssign a   ) ? a._assignedField :
      (AbstractFeature) (Object) new Object() { { if (true) throw new Error("acccessedFeature found unexpected Stmnt."); } } /* Java is ugly... */;

    return af == null ? -1 : _featureIds.get(af);
  }


  /**
   * Get the number of arguments of a given feature.
   *
   * @param f a feature index
   *
   * @return the number of declared arguments f expects. Arguments of open
   * generic type are counted as one argument.
   */
  public int featureArgCount(int f)
  {
    var ff = _featureIds.get(f);
    return ff.arguments().size();
  }


  /**
   * Get argument of a given feature.
   *
   * @param f a feature index
   *
   * @param i argument index
   *
   * @return the argument #i
   */
  public int featureArg(int f, int i)
  {
    if (PRECONDITIONS) require
      (0 <= i && i < featureArgCount(f));

    var ff = _featureIds.get(f);
    var af = ff.arguments().get(i);
    return _featureIds.get(af);
  }


  /**
   * Get number of declared features within a given feature.
   *
   * @param f a feature index
   *
   * @return the number of features declared in feature f
   */
  public int featureDeclaredCount(int f)
  {
    var ff = _featureIds.get(f);
    return _module.declaredFeatures(ff).size();
  }


  /**
   * Get feature declared within a given feature.
   *
   * @param f a feature index
   *
   * @param i index of declared feature
   *
   * @return the declared feature #i
   */
  public int featureDeclared(int f, int i)
  {
    if (PRECONDITIONS) require
      (0 <= i && i < featureDeclaredCount(f));

    var ff = _featureIds.get(f);
    // NYI: Quadratic performance in case we iterate over all declared features.
    for (var df : _module.declaredFeatures(ff).values())
      {
        if (i == 0)
          return _featureIds.get(df);
        i--;
      }
    throw new Error("declared feature not found");
  }


  /**
   * Is the given field a reference to an outer feature?
   *
   * @param f a field
   *
   * @return true for automatically generated references to outer instance
   */
  public boolean fieldIsOuterRef(int f)
  {
    if (PRECONDITIONS) require
      (featureKind(f) == FeatureKind.Field);

    var ff = _featureIds.get(f);
    return ff.isOuterRef();
  }

}

/* end of file */
