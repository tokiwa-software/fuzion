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
 * Source of class CheckIntrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import dev.flang.util.ANY;

import dev.flang.ast.AbstractFeature;

import dev.flang.fe.FrontEnd;

import dev.flang.util.Errors;

import java.util.TreeSet;
import java.util.Set;


/**
 * CheckIntrinsics is only a build-time helper to detect missing intrinsics in backends or analysis code like CFG.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class CheckIntrinsics extends ANY
{


  static interface Mangle
  {
    String doit(String s);
  }


  CheckIntrinsics(FrontEnd fe)
  {
    var all = new TreeSet<String>();
    getAll(all, fe, fe._universe);

    var i = dev.flang.be.interpreter.Intrinsics.supportedIntrinsics();
    checkIntrinsics(all, true, i, "Interpreter backend", x->x);
    var c = dev.flang.be.c.Intrinsics.supportedIntrinsics();
    checkIntrinsics(all, true, c, "C backend", x->x);
    var jvm = dev.flang.be.jvm.Intrinsix.supportedIntrinsics();
    checkIntrinsics(all, true, jvm, "JVM backend", x->dev.flang.be.jvm.Intrinsix.backendName(x));
    var dfa = dev.flang.fuir.analysis.dfa.DFA.supportedIntrinsics();
    checkIntrinsics(all, true, dfa, "DFA", x->x);
    var cfg = dev.flang.fuir.cfg.CFG.supportedIntrinsics();
    checkIntrinsics(all, true, cfg, "CFG", x->x);
  }


  void getAll(TreeSet<String> all, FrontEnd fe, AbstractFeature outer)
  {
    var s = fe._baseModule.declaredFeatures(outer);
    for (var f : s.values())
      {
        getAll(all, fe, f);
        if (f.isIntrinsic())
          {
            all.add(f.qualifiedName());
            fe._options.verbosePrintln(2, "base module intrinsic: " + f.qualifiedName());
          }
      }
  }


  /**
   * For the required intrinsic set 'all' check if 'implemented' is equal.
   * Report warnings for missing or additional entries in 'implemented' for
   * 'where'.
   *
   * @param all set of names of required intrinsics
   *
   * @param complete true if `implemented` should cover all intrinsics, false if
   * some might be missing.
   *
   * @param implemented set of names of implemented intrinsics
   *
   * @param where module that provides implemented intrinsics, e.g., "C backend".
   */
  void checkIntrinsics(Set<String> all,
                       boolean complete,
                       Set<String> implemented,
                       String where,
                       Mangle m)
  {
    var used = new TreeSet<String>();
    for (var a : all)
      {
        var ma = m.doit(a);
        if (!implemented.contains(ma))
          {
            if (complete)
              {
                Errors.warning(where + " does not implement intrinsic '" + a + "'.");
              }
          }
        else
          {
            used.add(ma);
          }
      }
    for (var a : implemented)
      {
        if (!used.contains(a))
          {
            Errors.warning(where + " implements intrinsic '" + a + "', even though this is never used.");
          }
      }
  }

}

/* end of file */
