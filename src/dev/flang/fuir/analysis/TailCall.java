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
   * The intermediate code we are analyzing.
   */
  final FUIR _fuir;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create TailCall for given intermediate code.
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
   * in which case the call can be replaced by assigning the arguments to the
   * current clazz' arguments and performing a goto to the beginning of the
   * current clazz's code.
   *
   * @param cl index of clazz containing the call
   *
   * @param s site of the call
   *
   * @return true if this is a tail call, false if this is no tail call or this
   * it is unknown whether this is a tail call.
   */
  public boolean callIsTailCall(int cl, int s)
  {
    if (PRECONDITIONS) require
      (_fuir.withinCode(s),
       _fuir.codeAt(s) == IR.ExprKind.Call);

    var c2 = _fuir.clazzCode(cl);
    return _fuir.codeSize(c2) > 0 &&
      isTailCall(cl, _fuir.codeBlockEnd(c2), s, _fuir.clazzResultField(cl));
  }


  /**
   * check if the first argument in the given call is the current instance's
   * outer reference.
   *
   * @param cl index of clazz containing the call
   *
   * @param s site of the call
   */
  public boolean firstArgIsOuter(int s)
  {
    if (PRECONDITIONS) require
      (_fuir.withinCode(s),
       _fuir.codeAt(s) == IR.ExprKind.Call);

    // the called clazz:
    var cc = _fuir.accessedClazz(s);

    // skip back all argument to reach the target instance
    var nargs = _fuir.clazzArgCount(cc);
    var ts = _fuir.codeIndex(s, -1);
    for (var i = 0; i < nargs; i++)
      {
        ts = _fuir.skipBack(ts);
      }
    // get type of target of call
    var tc = _fuir.accessTargetClazz(s);

    // we are ok if there is no outer ref in the current clazz, or if
    // the target provided in this call is 'Current.outerRef'.
    var cl = _fuir.clazzAt(s);
    var outerRef = _fuir.clazzOuterRef(cl);
    var res =
      outerRef == -1 ||
      nargs >= 1 &&
      (tc == _fuir.clazzUniverse() ||
       _fuir.codeAt       (ts) == IR.ExprKind.Call    &&
       _fuir.accessedClazz(ts) == outerRef            &&
       _fuir.codeAt(_fuir.codeIndex(ts, -1)) == IR.ExprKind.Current);

    return res;
  }


  /**
   * Take a field or NO_CLAZZ and return that field unless its result is unit
   * type, then return NO_CLAZZ.
   *
   * @return f unless f is unit type, then return NO_CLAZZ.
   */
  private int noClazzIfResultUnitType(int f)
  {
    if (f != IR.NO_CLAZZ &&
        _fuir.clazzIsUnitType(_fuir.clazzResultClazz(f)))
      {
        f = IR.NO_CLAZZ;
      }
    return f;
  }

  /**
   * Check if `a` and `b` refer to the same field are both NO_CLAZZ. A field
   * with unit type result clazz is considered the same as `NO_CLAZZ`.
   *
   * @param a a field or NO_CLAZZ
   *
   * @param b a field or NO_CLAZZ
   *
   * @return true if a == b or both are one of NO_CLAZZ or fields with unit type
   * result.
   */
  private boolean sameField(int a, int b)
  {
    var a0 = noClazzIfResultUnitType(a);
    var b0 = noClazzIfResultUnitType(b);
    return a0 == b0;
  }


  /**
   * Helper to check from the last expr in cl's code if we find a tail call at c,ix.
   *
   * @param cl index of clazz containing the call
   *
   * @param cls the site of the last Expr of a code block that is to be checked if it results in the tail call at s
   *
   * @param s site of the the call
   *
   * @param mustAssignTo -1 iff the result should be the last expr in the code
   * block, otherwise the clazz of a field in Current the result should be
   * assigned to.
   */
  private boolean isTailCall(int cl, int cls, int s, int mustAssignTo)
  {
    return switch (_fuir.codeAt(cls))
      {
      case Call ->
        {
          var cc = _fuir.accessedClazz(cls);
          yield mustAssignTo == IR.NO_CLAZZ &&
            (// we found call c/ix and we do not need to assign any variable, so we have a success!
             cls == s ||

             // in case we have a call to 'Current.f' for some field 'f',
             // recursively check if 'Current.f' is set to the call c/ix:
             _fuir.clazzKind(cc) == IR.FeatureKind.Field &&
             cls > _fuir.codeBlockStart(cls)+1 &&
             _fuir.codeAt(_fuir.codeIndex(cls, -1)) == IR.ExprKind.Current &&
             isTailCall(cl, _fuir.codeIndex(cls, -2), s, cc));
        }

      case Assign ->
        {
          var cc = _fuir.accessedClazz(cls);
          if (cc != IR.NO_CLAZZ &&
              _fuir.clazzIsUnitType(_fuir.clazzResultClazz(cc)))
            {
              cc = IR.NO_CLAZZ;
            }
          yield
            // if this is an assignment to 'Current.mustAssignTo' with, recursively check if
            // the value assigned is the call s.
            sameField(cc, mustAssignTo) && cls > _fuir.codeBlockStart(cls)+1 &&
            _fuir.codeAt(_fuir.codeIndex(cls, -1)) == IR.ExprKind.Current &&
            isTailCall(cl, _fuir.codeIndex(cls, -2), s, -1);
        }

      case Match ->
        {
          // for a match, check if any case of the match results in s:
          for (var mc = 0; mc < _fuir.matchCaseCount(cls); mc++)
            {
              var mcc = _fuir.matchCaseCode(cls, mc);
              if (_fuir.codeSize(mcc) > 0 && isTailCall(cl, _fuir.codeBlockEnd(mcc), s, mustAssignTo))
                {
                  yield true;
                }
            }

          // no case ended in a tail call, we fail:
          yield false;
        }

      case Box ->
        {
          // true if isTailCall=true for what we are boxing
          yield isTailCall(cl, _fuir.codeIndex(cls, -1), s, mustAssignTo);
        }

      // any other code results in failure to detect a tail call:
      default -> false;
      };
  }


}

/* end of file */
