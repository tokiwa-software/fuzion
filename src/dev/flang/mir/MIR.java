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

import dev.flang.ast.Call;  // NYI: Remove dependency!
import dev.flang.ast.Feature;  // NYI: Remove dependency!

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


  /**
   * The basic types of features in Fuzion:
   */
  public enum FeatureKind
  {
    Routine,
    Field,
    Intrinsic,
    Abstract,
    Choice
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The main feature
   */
  final Feature _main;


  /**
   * integer ids for features in this module
   */
  final Map2Int<Feature> _featureIds = new MapComparable2Int(FEATURE_BASE);


  /*--------------------------  constructors  ---------------------------*/


  public MIR(Feature main)
  {
    _main = main;
    addFeatures();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The main Feature.
   */
  public Feature main()
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
        var u = main().universe();
        addFeatures(u);
      }
  }


  /**
   * Helper to addFeatures() to add feature f and all features declared within f.
   */
  private void addFeatures(Feature f)
  {
    _featureIds.add(f);
    for (var i : f.declaredFeatures().values())
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
  private List<Object> prolog(Feature f)
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
  private void addCode(Feature heir, List<Object> code, Feature ff)
  {
    for (Call p: ff.inherits)
      {
        /*
NYI: Any side-effects in p.target or p._actuals will be executed twice, once for
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

        toStack(code, p.target);
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

        check
          (p._actuals.size() == p.calledFeature().arguments.size());
        for (var i = 0; i < p._actuals.size(); i++)
          {
            var a = p._actuals.get(i);
            var f = pf.arguments.get(i);
            toStack(code, a);
            code.add(ExprKind.Current);
            // Field clazz means assign value to that field
            // NYI: code.add((Clazz) cc.getRuntimeData(p.parentCallArgFieldIds_ + i));
          }
        addCode(ff, code, p.calledFeature());
      }
    toStack(code, ff.impl._code);
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

  public FeatureKind featureKind(int f)
  {
    var ff = _featureIds.get(f);

    return ff.isChoice()
      ? FeatureKind.Choice
      : switch (ff.impl.kind_)
        {
        case Routine, RoutineDef                     -> FeatureKind.Routine;
        case Field, FieldDef, FieldActual, FieldInit -> FeatureKind.Field;
        case Intrinsic                               -> FeatureKind.Intrinsic;
        case Abstract                                -> FeatureKind.Abstract;
        default -> throw new Error ("Unexpected feature impl kind: "+ff.impl.kind_);
        };
  }


}

/* end of file */
