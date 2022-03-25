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
 * Source of class Effects
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.effects;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Effects is an analysis backend that finds effects that are used by a Fuzion
 * program.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Effects extends ANY
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Helper clazz to contain a map from A to a set of B.
   */
  static class MapToN<A,B>
  {
    TreeMap<A, TreeSet<B>> _map = new TreeMap<>();

    boolean put(A a, B b)
    {
      var s = _map.get(a);
      if (s == null)
        {
          s = new TreeSet<B>();
          _map.put(a, s);
        }
      return s.add(b);
    }

    boolean contains(A a, B b)
    {
      var s = _map.get(a);
      return s != null && s.contains(b);
    }
  }

  /**
   * Helper clazz to contain a directed bi-partite graph with edges from A to B
   * and efficient lookup for backwards edges from B to A.
   */
  static class BiGraph<A,B>
  {
    MapToN<A,B> _to   = new MapToN<>();
    MapToN<B,A> _back = new MapToN<>();

    void put(A a, B b)
    {
      _to  .put(a, b);
      _back.put(b, a);
    }

    boolean contains(A a, B b)
    {
      return _to.contains(a,b);
    }
  }


  /**
   * Helper clazz to contain a directed graph.
   */
  static class Graph<A> extends BiGraph<A,A>
  {
  }


  /*----------------------------  constants  ----------------------------*/



  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  final FUIR _fuir;


  /**
   * Map from clazz cl to set of effects ecl that are required for a call to cl.
   */
  Graph<Integer> _effects = new Graph<>();


  /**
   * Map from clazz cl to set of clazzes that are called by cl
   */
  Graph<Integer> _callGraph = new Graph<>();

  /**
   * All clazzes that are call
   */
  TreeSet<Integer> _calledClazzes = new TreeSet<>();


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create Effects code backend for given intermediate code.
   *
   * @param fuir the intermediate code.
   */
  public Effects(FUIR fuir)
  {
    _fuir = fuir;
    Errors.showAndExit();
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Find default effects required by this program
   */
  public void find()
  {
    var cl = _fuir.mainClazzId();
    createCallGraph(cl);
    Errors.showAndExit();
  }


  /**
   * Create call graph for given clazz cl.
   *
   * @param cl id of clazz to compile
   */
  public void createCallGraph(int cl)
  {
    if (_fuir.clazzNeedsCode(cl))
      {
        var ck = _fuir.clazzKind(cl);
        switch (ck)
          {
          case Routine:
          case Intrinsic:
            {
              if (ck == FUIR.FeatureKind.Routine)
                {
                  createCallGraphForRoutine(cl, false);
                }
              else
                {
                  // NYI: _intrinsics.code(this, cl);
                }
            }
          }
        if (_fuir.clazzContract(cl, FUIR.ContractKind.Pre, 0) != -1)
          {
            createCallGraphForRoutine(cl, true);
          }
      }
  }


  /**
   * Create code for given clazz cl.
   *
   * @param cl id of clazz to generate code for
   *
   * @param pre true to create code for cl's precondition, false to create code
   * for cl itself.
   */
  void createCallGraphForRoutine(int cl, boolean pre)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Routine || pre);

    if (pre)
      {
        // NYI: preOrPostCondition(cl, FUIR.ContractKind.Pre);
      }
    else
      {
        createCallGraphForBlock(cl, _fuir.clazzCode(cl));
      }
  }



  /**
   * Create call graph for calls in code block c of clazz cl.
   *
   * @param cl clazz id
   *
   * @param c the code block to analyze.
   */
  void createCallGraphForBlock(int cl, int c)
  {
    for (int i = 0; /* NYI: !containsVoid(stack) &&*/ _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
      {
        var s = _fuir.codeAt(c, i);
        createCallGraphForStmnt(cl, c, i, s);
      }
  }


  /**
   * Create call graph for calls made by statemnt s at index i in code block c
   * of clazz cl.
   *
   * @param cl clazz id
   *
   * @param c the code block to analyze
   *
   * @param i the index within c
   *
   * @param s the FUIR.ExprKind of the statement to analyze
   */
  void createCallGraphForStmnt(int cl, int c, int i, FUIR.ExprKind s)
  {
    switch (s)
      {
      case AdrOf : break;
      case Assign: break;
      case Box   : break;
      case Unbox : break;
      case Call:
        {
          var cc0 = _fuir.accessedClazz  (cl, c, i);
          if (_fuir.clazzContract(cc0, FUIR.ContractKind.Pre, 0) != -1)
            {
             call(cl, cc0, true);
            }
          if (!_fuir.callPreconditionOnly(cl, c, i))
            {
              access(cl, c, i);
            }
          break;
        }
      case Comment: break;
      case Current: break;
      case Outer  : break;
      case Const  : break;
      case Match  :
        {
          for (var mc = 0; mc < _fuir.matchCaseCount(c, i); mc++)
            {
              createCallGraphForBlock(cl, _fuir.matchCaseCode(c, i, mc));
            }
          break;
        }
      case Tag: break;
      case Env:
        {
          var ecl = _fuir.envClazz(cl, c, i);
          addEffect(cl, ecl);
          break;
        }
      case Dup: break;
      case Pop: break;
      default:
        {
          Errors.fatal("Effects backend does not handle statments of type " + s);
        }
      }
  }


  /**
   * Create call graph for access (call or write) of a feature.
   *
   * @param cl clazz id
   *
   * @param c the code block to compile
   *
   * @param i index of the access statement, must be ExprKind.Assign or ExprKind.Call
   */
  void access(int cl, int c, int i)
  {
    var cc0 = _fuir.accessedClazz  (cl, c, i);

    if (_fuir.accessIsDynamic(cl, c, i))
      {
        var ccs = _fuir.accessedClazzes(cl, c, i);
        for (var cci = 0; cci < ccs.length; cci += 2)
          {
            var tt = ccs[cci  ];
            var cc = ccs[cci+1];
            call(cl, cc, false);
          }
      }
    else if (_fuir.clazzNeedsCode(cc0))
      {
        call(cl, cc0, false);
      }
  }


  /**
   * Create call graph for call to a feature
   *
   * @param cl clazz id of the call
   *
   * @param cc clazz that is called
   *
   * @param pre true to call the precondition of cl instead of cl.
   */
  void call(int cl, int cc, boolean pre)
  {
    if (_fuir.clazzNeedsCode(cc))
      {
        addToCallGraph(cl, cc, pre);
      }
  }


  /**
   * Add edge from cl to cc to call graph
   *
   * @param cl the caller clazz
   *
   * @param cc the callee clazz
   *
   * @param pre true iff cc's precondition is called, not cc itself.
   */
  void addToCallGraph(int cl, int cc, boolean pre)
  {
    if (pre)
      {
        // NYI:
      }
    else
      {
        _callGraph.put(cl, cc);

        if (!_calledClazzes.contains(cc))
          {
            _calledClazzes.add(cc);
            createCallGraph(cc);
          }
      }
  }


  /**
   * Add connection from cl to ecl in _effects
   *
   * @param cl a clazz
   *
   * @param ecl an effect that is required by cl
   */
  void addEffect(int cl, int ecl)
  {
    if (!_effects.contains(cl, ecl))
      {
        System.out.println(_fuir.clazzAsString(ecl));
      }
    _effects.put(cl, ecl);
  }

}

/* end of file */
