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
 * Source of class TailCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis;

import dev.flang.fuir.FUIR;

import dev.flang.ir.IR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;


/**
 * TailCall determines if a call is a tail call.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class TailCall extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/



  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are analysing.
   */
  final FUIR _fuir;


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create TaiCall for given intermediate code.
   *
   * @param fuir the intermediate code.
   */
  public TailCall(FUIR fuir)
  {
    _fuir = fuir;
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Is this call a tail call.
   *
   * A tail call is a call whose result directly becomes the result of the
   * current routine without any further processing or side-effects in between.
   *
   * A tail call may be dynamic, i.e., it might branch off into different
   * called clazzes.
   *
   * The back-end might be able to replace a tail call by a jump.  In
   * particular, a tail call might call the current clazz cl (tail recursion),
   * in which case the call can be replaced by assiging the arguments to the
   * current clazz' arguments and performing a goto to the beginning of the
   * current clazz's code.
   *
   * @param cl index of clazz containing the call
   *
   * @param c code block containing the call
   *
   * @param ix index of the call
   *
   * @return true if this is a tail call, false if this is no tail call or this
   * it is unknown whether this is a tail call.
   */
  public boolean callIsTailCall(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       _fuir.withinCode(c, ix),
       _fuir.codeAt(c, ix) == IR.ExprKind.Call);

    var c2 = _fuir.clazzCode(cl);
    return _fuir.codeSize(c2) > 0 &&
      isTailCall(cl, c2, _fuir.codeSize(c2)-1, c, ix, _fuir.clazzResultField(cl));
  }


  /**
   * check if the first argument in the given call is the current instance's
   * outer reference.
   *
   * @param cl index of clazz containing the call
   *
   * @param c code block containing the call
   *
   * @param ix index of the call
   */
  public boolean firstArgIsOuter(int cl, int c, int ix)
  {
    if (PRECONDITIONS) require
      (ix >= 0,
       _fuir.withinCode(c, ix),
       _fuir.codeAt(c, ix) == IR.ExprKind.Call);

    // the called clazz:
    var cc = _fuir.accessedClazz(cl, c, ix);

    // skip back all argument to reach the target instance
    var nargs = _fuir.clazzArgCount(cc);
    var ti = _fuir.codeIndex(c, ix, -1);
    for (var i = 0; i < nargs; i++)
      {
        ti = _fuir.skipBack(cl, c, ti);
      }

    // get type of target of call
    var tc = _fuir.accessTargetClazz(cl, c, ix);

    // we are ok if there is no outer ref in the current clazz, or if
    // the target provided in this call is 'Current.outerRef'.
    var outerRef = _fuir.clazzOuterRef(cl);
    var res =
      outerRef == -1 ||
      nargs >= 1 &&
      (tc == _fuir.clazzUniverse() ||
       _fuir.codeAt       (    c, ti) == IR.ExprKind.Call    &&
       _fuir.accessedClazz(cl, c, ti) == outerRef            &&
       _fuir.codeAt(c, _fuir.codeIndex(c, ti, -1)) == IR.ExprKind.Current);

    return res;
  }


  /**
   * Helper to check from the last expr in cl's code if we find a tail call at c,ix.
   *
   * @param cl index of clazz containing the call
   *
   * @param clc the code block of cl that is currently searched for
   *
   * @param clix the current index in clc
   *
   * @param c code block containing the call
   *
   * @param ix index of the call
   *
   * @param mustAssignTo -1 iff the result shouldbe the last expr in the code
   * block, otherwise the clazz of a field in Current the result should be
   * assigned to.
   */
  private boolean isTailCall(int cl, int clc, int clix, int c, int ix, int mustAssignTo)
  {
    return switch (_fuir.codeAt(clc, clix))
      {
      case Call ->
        {
          var cc = _fuir.accessedClazz(cl, clc, clix);
          yield mustAssignTo == -1 &&
            (// we found call c/ix and we do not need to assign any variable, so we have a success!
             clc == c && clix == ix ||

             // in case we have a call to 'Current.f' for some field 'f',
             // recursively check if 'Current.f' is set to the call c/ix:
             _fuir.clazzKind(cc) == IR.FeatureKind.Field &&
             clix > 1 &&
             _fuir.codeAt(clc, _fuir.codeIndex(clc, clix, -1)) == IR.ExprKind.Current &&
             isTailCall(cl, clc, _fuir.codeIndex(clc, clix, -2), c, ix, cc));
        }

      case Assign ->
        {
          var cc = _fuir.accessedClazz(cl, clc, clix);
          yield
            // if this is an assignment to 'Current.mustAssignTo' with, recursively check if
            // the value assigned is the call c/ix.
            cc == mustAssignTo && clix > 1 &&
            _fuir.codeAt(clc, _fuir.codeIndex(clc, clix, -1)) == IR.ExprKind.Current &&
            isTailCall(cl, clc, _fuir.codeIndex(clc, clix,-2), c, ix, -1);
        }

      case Match ->
        {
          // for a match, check if any case of the match results in c/ix:
          for (var mc = 0; mc < _fuir.matchCaseCount(clc, clix); mc++)
            {
              var mcc = _fuir.matchCaseCode(clc, clix, mc);
              if (_fuir.codeSize(mcc) > 0 && isTailCall(cl, mcc, _fuir.codeSize(mcc)-1, c, ix, mustAssignTo))
                {
                  yield true;
                }
            }

          // no case ended in a tail call, we fail:
          yield false;
        }

      // any other code resuts in failure to detect a tail call:
      default -> false;
      };
  }


}

/* end of file */
