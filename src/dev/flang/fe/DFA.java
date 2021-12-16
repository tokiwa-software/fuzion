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
 * Source of class DFA
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.util.TreeSet;

import dev.flang.mir.MIR;

import dev.flang.util.ANY;


/**
 * DFA performs data flow analysis on modules and checks data-flow related
 * correctness conditions.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DFA extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The MIR corresponding to the underlying module.
   */
  private MIR _mir;


  /*--------------------------  constructors  ---------------------------*/


  public DFA(MIR module)
  {
    _mir = module;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check the correctness of this module.  In particular, check that all fields
   * are initialized before they are read.
   */
  void check()
  {
    for (var f = _mir.firstFeature(); f <= _mir.lastFeature(); f++)
      {
        if (!LibraryModule.USE_FUM) checkFeature(f);
      }
  }


  /**
   * Check the correctness of given feature in given module. In particular,
   * check that all fields are initialized before they are read.
   */
  void checkFeature(int f)
  {
    switch (_mir.featureKind(f))
      {
      case Routine:
        var c = _mir.featureCode(f);
        var lastWasCurrent = false;
        var show = _mir.featureAsString(f).equals("uninitialized");
        var initialized = new TreeSet<Integer>();
        for (int i = 0; i < _mir.featureArgCount(f); i++)
          {
            initialized.add(_mir.featureArg(f, i));
          }
        var declaredInF_NYI = new TreeSet<Integer>();
        for (int i = 0; i < _mir.featureDeclaredCount(f); i++)
          {
            var df = _mir.featureDeclared(f, i);
            if (_mir.featureKind(df) == MIR.FeatureKind.Field)
              {
                declaredInF_NYI.add(df);
              }
          }
        var giveUpDueToControlFlowNYI = false;
        for (int i = 0; _mir.withinCode(c, i); i = i + _mir.codeSizeAt(c, i))
          {
            var s = _mir.codeAt(c, i);
            switch (s)
              {
              case Assign:
                if (lastWasCurrent)
                  {
                    var af = _mir.accessedFeature(f, c, i);
                    initialized.add(af);
                  }
                lastWasCurrent = false;
                break;
              case Current:
                lastWasCurrent = true;
                break;
              case Call:
                if (lastWasCurrent)
                  {
                    var af = _mir.accessedFeature(f, c, i);
                    if (_mir.featureKind(af) == MIR.FeatureKind.Field &&
                        declaredInF_NYI.contains(af) &&
                        !initialized.contains(af) &&
                        !_mir.fieldIsOuterRef(af) &&
                        !giveUpDueToControlFlowNYI)
                      {
                        FeErrors.fieldNotInitialized(_mir, _mir.codeAtPos(c, i), af);
                      }
                  }
                lastWasCurrent = false;
                break;
              case Match:
                giveUpDueToControlFlowNYI = true;
                break;
              default:
                lastWasCurrent = false;
                break;
              }
          }
        // NYI
        break;
      default:
        break;
      }
  }


}

/* end of file */
