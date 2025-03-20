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


import dev.flang.ir.IR;

import dev.flang.util.ANY;
import static dev.flang.util.FuzionConstants.EFFECT_INSTATE_NAME;

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


  boolean _real;

  TreeSet<CallGroup> _from = new TreeSet<>();
  TreeSet<CallGroup> _to   = new TreeSet<>();

  TreeSet<Integer> _usedEffects = new TreeSet<>();
  TreeSet<Integer> _mayHaveEffects = new TreeSet<>();

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
    if (dfa._real && !dfa._calledClazzesDuringPrePhase.contains(cc) && !true /* NYI! */)
      {
        System.out.println("PROBLE FOR "+dfa._fuir.clazzAsString(cc));
      }
    if (PRECONDITIONS) require
      (!dfa._real || dfa._calledClazzesDuringPrePhase.contains(cc) || true /* NYI! */);

    _real = dfa._real;
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


  void mayHaveEffect(int ecl)
  {
    if (false) if (_dfa._fuir.clazzKind(_cc) == IR.FeatureKind.Intrinsic &&
        _dfa._fuir.clazzOriginalName(_cc).equals(EFFECT_INSTATE_NAME) &&
        _dfa._fuir.effectTypeFromIntrinsic(_cc) == ecl)
      {
        return;
      }
    if (_mayHaveEffects.add(ecl))
      {
        usedAndMayHaveXXX(ecl);
        if (false) if (_dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm") ||
            _dfa._fuir.clazzAsString(_cc).equals("array#3 u8"))
            {
              System.out.println(dev.flang.util.Terminal.BOLD_GREEN + "MAY HAVE: "+_dfa._fuir.clazzAsString(_cc)+": "+
                                 clazzesAsString(_mayHaveEffects)+
                                 dev.flang.util.Terminal.RESET);
            }
        for (var t : _to)
          {
            t.mayHaveEffect(ecl);
          }
        var s = _dfa._fuir.clazzAsString(_cc);
        if (s.startsWith("instate_helper ") ||
            s.startsWith("(instate_helper ")    )
          {
            needsEffect(ecl);
          }
      }
  }

  void saveEffects()
  {
    if (PRECONDITIONS) require
      (!_real);

    _dfa._calledClazzesDuringPrePhase.add(_cc);
    // var str = _dfa._fuir.clazzAsString(_cc);
    // var show = str.equals("i32.as_string#1.digit_as_utf8_byte#1") ||
    //   str.equals("i32.as_string#1.digit_as_utf8_byte#1");
    for (var e : _usedEffects)
      {
        if (_mayHaveEffects.contains(e))
          {
            _dfa._clazzesThatRequireEffect.computeIfAbsent(e, k->new TreeSet<>()).add(_cc);
            _dfa._effectsRequiredByClazz.computeIfAbsent(_cc, k->new TreeSet<>()).add(e);
            //         if (show) dev.flang.util.Debug.uprintln("FOR "+str+" ADDING "+_dfa._fuir.clazzAsString(e));
          }
      }
  }


  boolean requiredEffect(int ecl)
  {
    if (PRECONDITIONS) require
      (_real);

    var s = _dfa._clazzesThatRequireEffect.get(_cc);
    return s != null && s.contains(ecl);
  }
  void usedAndMayHaveXXX(int ecl)
  {
    if (false) if (_usedEffects.contains(ecl) &&
        _mayHaveEffects.contains(ecl))
      {
              System.out.println(dev.flang.util.Terminal.BOLD_PURPLE + "NEEDED-AND-MAY-HAVE: "+_dfa._fuir.clazzAsString(_cc)+": "+
                                 _dfa._fuir.clazzAsString(ecl)+
                                 dev.flang.util.Terminal.RESET);

      }
  }


  String clazzesAsString(java.util.Set<Integer> s)
  {
    return s == null ? "{}" : s.stream().map(i->_dfa._fuir.clazzAsString(i)).collect(java.util.stream.Collectors.joining(","));
  }

  void needsEffect(int ecl)
  {
    if (_dfa._fuir.clazzKind(_cc) == IR.FeatureKind.Intrinsic &&
        _dfa._fuir.clazzOriginalName(_cc).equals(EFFECT_INSTATE_NAME) &&
        _dfa._fuir.effectTypeFromIntrinsic(_cc) == ecl)
      {
        return;
      }

    if (_usedEffects.add(ecl))
      {
        usedAndMayHaveXXX(ecl);
        //        _dfa.instanceNeedsEffects(_cc).add(ecl);

        if (false)
          if (_dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
            if (_dfa._fuir.clazzAsString(_cc).equals("(array u8).array_cons"))
            {
              System.out.println(dev.flang.util.Terminal.BOLD_RED + "NEEDS: "+_dfa._fuir.clazzAsString(_cc)+": "+
                                 clazzesAsString(_usedEffects)+
                                 dev.flang.util.Terminal.RESET);
              throw new Error();
            }


        // problem is that `array u8` constructor is called from expanding_array.as_array, while
        // it is also called from (list u8).as_array, where it uses a lambda that require `(list u8).as_array.lm`.
        /*
        if (_dfa._fuir.clazzAsString(_cc).equals("(container.expanding_array u8).as_array") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println(dev.flang.util.Terminal.BOLD_RED + "################ needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this+
                               dev.flang.util.Terminal.RESET);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("(container.expanding_array u8).array_cons") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println(dev.flang.util.Terminal.BOLD_RED + "<<<<<<<<<<<< needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this+
                               dev.flang.util.Terminal.RESET);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("(array u8).array_cons") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println(dev.flang.util.Terminal.BOLD_RED + ">>>>>>>>>>>> needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this+
                               dev.flang.util.Terminal.RESET);
            throw new Error();
          }
            */
        /*
        if (_dfa._fuir.clazzAsString(_cc).equals("(list.type u8).as_array.type.lm.type.instate#4 (array u8)") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println("&&&&&&&&&&& needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("(list.type u8).as_array.type.lm.type.instate#3 (array u8)") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println("%%%%%%%%%% needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("(list u8).as_array.lm.instate_self#2 (array u8)") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println("!!!!!!!!!!!! needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("(list u8).as_array.lm.infix !#2 (array u8)") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println("§§§§§§§§§§§§§§§ needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("(list u8).as_array") &&
            _dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm"))
          {
            System.out.println("============ needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            throw new Error();
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("fuzion.sys.c_string#1"))
          {
            System.out.println("+++++++++++ needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            if (_dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm")) throw new Error("needsEffect for c_string");
          }
        if (_dfa._fuir.clazzAsString(_cc).equals("u32.infix ..#1"))
          {
            System.out.println("********* needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
            if (_dfa._fuir.clazzAsString(ecl).equals("(list u8).as_array.lm")) throw new Error("needsEffect for infix ..");
          }
        */
        _dfa.wasChanged(() -> "needs effect "+_dfa._fuir.clazzAsString(ecl)+" for "+this);
        for (var f : _from)
          {
            try {
            f.needsEffect(ecl);
            } catch (Error e) {
              // System.out.println("FROM1 "+this);
              System.out.println("FROM1 "+_dfa._fuir.clazzAsString(_cc)+" from "+_dfa._fuir.clazzAsString(f._cc)+
                                 " EFFECTS: "+_usedEffects.stream().map(i->_dfa._fuir.clazzAsString(i)).reduce("", (a,b)->a+","+b));
              throw e;
            }
          }
      }
  }

  void calledFrom(CallGroup from)
  {
    if (_from.add(from))
      {
        if (_dfa._fuir.clazzAsString(from._cc).startsWith("(array u8).array_cons") &&
            _dfa._fuir.clazzAsString(_cc).startsWith("fuzion.runtime.precondition_fault"))
          {
            System.out.println("A "+ _dfa._fuir.clazzAsString(_cc)+" is calledFrom  "+ _dfa._fuir.clazzAsString(from._cc));
            Thread.dumpStack();
          }
        if (_dfa._fuir.clazzAsString(_cc).startsWith("(array u8).array_cons") &&
            _dfa._fuir.clazzAsString(from._cc).startsWith("fuzion.runtime.precondition_fault"))
          {
            System.out.println("B "+ _dfa._fuir.clazzAsString(_cc)+" is calledFrom  "+ _dfa._fuir.clazzAsString(from._cc));
            Thread.dumpStack();
          }

        // TBD: Do we need this? --Yes we do!
        //
        if (!false)
          for (var ecl : _usedEffects)
            {
              try {
                from.needsEffect(ecl);
              } catch (Error e) {
                System.out.println("FROM2 "+this+"\n"+
                                   "  CALLED FROM "+from+"\n"+
                                   "  Already needs "+_dfa._fuir.clazzAsString(ecl)+"\n  calledFrom("+from+")");
                throw e;
              }
            }


        from._to.add(this);
        for (var ecl : from._mayHaveEffects)
          {
            mayHaveEffect(ecl);
          }
      }
  }


  public String toString()
  {
    return "CALLGROUP to "+_dfa._fuir.clazzAsString(_cc)+" at "+_dfa._fuir.siteAsString(_site)+" effects: "+
      clazzesAsString(_dfa._real
                      ? _usedEffects
                      : _dfa._effectsRequiredByClazz.get(_cc));
  }


}

/* end of file */
