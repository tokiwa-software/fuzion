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
import dev.flang.fe.Module;

import dev.flang.util.Errors;

import java.util.TreeSet;
import java.util.Set;


/**
 * CheckIntrinsics is only a built-time helper to detect missing intrinsics in backends or analysis code like CFG.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class CheckIntrinsics extends ANY
{

  CheckIntrinsics(FrontEnd fe)
  {
    var all = new TreeSet<String>();
    getAll(all, fe, fe._universe);

    var c = dev.flang.be.c.Intrinsics.supportedIntrinsics();
    checkIntrinsics(all, c, "C backend");
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
   * @param implemented set of names of implemented intrinsics
   *
   * @param where module that provides impelmented intrinsics, e.g., "C backend".
   */
  void checkIntrinsics(Set<String> all, Set<String> implemented, String where)
  {
    for (var a : all)
      {
        if (!implemented.contains(a))
          {
            Errors.warning(where + " does not implement intrinsic '" + a + "'.");
          }
      }
    for (var a : implemented)
      {
        if (!all.contains(a))
          {
            Errors.warning(where + " implement intrinsic '" + a + "', even though this is never used.");
          }
      }
  }

}

/* end of file */
