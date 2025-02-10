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
 * Source of class CallGroup
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis.dfa;


import dev.flang.util.ANY;

import java.util.TreeSet;


/**
 * CallGroup represents all calls that differ only by their environment, i.e.,
 * by the effects installed when thihs call is made.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class CallGroup extends ANY implements Comparable<CallGroup>
{


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/

  /*-------------------------  static methods  --------------------------*/


  static long quickHash(DFA dfa, int cl, int site, Value tvalue)
  {
    long k = -1;
    var k1 = dfa._fuir.clazzId2num(cl);
    var k2 = tvalue._id;
    var k3 = dfa.siteSensitive(cl) ? dfa.siteIndex(site) : 0;
    var k4 = 0; /// NYI: unused!
    if (CHECKS) check
      (k1 >= 0,
       k2 >= 0,
       k3 >= 0,
       k4 >= 0);
    // We use a LongMap in case we manage to fiddle k1..k4 into a long
    //
    // try to fit clazz id, tvalue id, siteIndex and env id into long as follows
    //
    // Bit 6666555555555544444444443333333333222222222211111111110000000000
    //     3210987654321098765432109876543210987654321098765432109876543210
    //     <----clazz id----><---tvalue id----><---siteIndex----><-env-id->
    //     |     18 bits    ||     18 bits    ||     18 bits    ||10 bits |
    //
    if (k1 <= 0x3FFFE &&
        k2 <= 0x3FFFE &&
        k3 <= 0x3FFFE &&
        k4 <= 0x03FE)
      {
        k = ((k1 * 0x40000L + k2) * 0x40000L + k3) * 0x400L + k4;
        /*
          if (!(((k >> (18*2+10)) & 0x3FFFF) == k1))
          {
          System.out.println("k1: "+Long.toHexString(k1));
          System.out.println("k: "+Long.toHexString(k));
          System.out.println("k >> (18*2+10): "+Long.toHexString(k >> (18*2+10)));
          }
        */
        if (CHECKS) check
          (((k >> (18*2+10)) & 0x3FFFF) == k1,
           ((k >> (18  +10)) & 0x3FFFF) == k2,
           ((k >> (     10)) & 0x3FFFF) == k3,
           ((k               & 0x003FF) == k4));
      }
    return k;
  }

  /*----------------------------  variables  ----------------------------*/


  /**
   * The DFA instance we are working with.
   */
  final DFA _dfa;


  /**
   * The clazz this is calling.
   */
  final int _cc;


  /**
   * If available, _site gives the call site of this Call as used in the IR.
   * Calls with different call sites are analysed separately, even if the
   * context and environment of the call is the same.
   *
   * IR.NO_SITE if the call site is not known, i.e., the call is coming from
   * intrinsic call or the main entry point.
   */
  final int _site;


  /**
   * Target value of the call
   */
  Value _target;


  TreeSet<CallGroup> _from = new TreeSet<>();

  TreeSet<Integer> _effects = new TreeSet<>();

  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create CallGroup
   *
   * @param dfa the DFA instance we are analyzing with
   *
   * @param cc called clazz
   *
   * @param site the call site, -1 if unknown (from intrinsic or program entry
   * point)
   *
   * @param target is the target value of the call
   */
  public CallGroup(DFA dfa, int cc, int site, Value target)
  {
    _dfa = dfa;
    _cc = cc;
    _site = site;
    _target = target;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Compare this to another Call.
   */
  public int compareTo(CallGroup other)
  {
    return
      _cc         != other._cc         ? Integer.compare(_cc        , other._cc        ) :
      _target._id != other._target._id ? Integer.compare(_target._id, other._target._id) :
      _dfa.siteSensitive(_cc)          ? Integer.compare(_site      , other._site      ) : 0;
  }


  /**
   * For debugging: Why did {@code compareTo(other)} return a value != 0?
   */
  String compareToWhy(Call other)
  {
    return
      _cc         != other._cc            ? "cc different" :
      _target._id != other._target._id    ? "target different" :
      _dfa.siteSensitive(_cc) && _site  != other._site          ? "site different" : null;
  }


  void needsEffect(int ecl)
  {
    if (_effects.add(ecl))
      {
        _dfa.wasChanged(() -> "needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
        for (var f : _from)
          {
            f.needsEffect(ecl);
          }
      }
  }

  void calledFrom(CallGroup cg)
  {
    _from.add(cg);
    for (var ecl : _effects)
      {
        cg.needsEffect(ecl);
      }
  }

}

/* end of file */
