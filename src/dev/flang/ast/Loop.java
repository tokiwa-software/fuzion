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
 * Source of class Loop
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Loops are very generic in Fuzion.  The basic loop structure consists of a
 * list of index variables, a loop failure condition, a loop body, a loop
 * success condition and a loop epilog consiting of a branch executed after
 * successful loop termination and one after failed loop termination.
 *
 * In general, this is

  for
    x1 := init1, next1;
    x2 in set2;
    x3 := init3, next3;
    x4 in set4;
    x5 := init5, next5;
  while <whileCond>
    <body>
  until <untilCond>
    <success>
  else
    <failure>

 * This will be converted into a loop prolog that defines a variable to hold the
 * loop success, defines temp vars for streams as needed and initializes the
 * index variables

  // loop prolog
  x1 := init1;
  stream2 := set2.asStream;
  ### loopElse will be put here ###
  if (stream2.hasNext)
    {
      x2 := stream2.next;
      x3 := init3;
      stream4 := set4.asStream;
      if (stream4.hasNext)
        {
          x4 := stream4.next;
          x5 := init5;

          ### loop will be put here ###

        }
      else
        {
           loopElse
        }
    }
  else
    {
      loopElse
    }

 * The loop will be implemented using a tail recursive feature as follows

  loop(x1, x2, x3, x4, x5, ... inferred-type) =>
     if whileCond
       <body>
       if untilCond
         <success>
         ### OPTIONAL TRUE ###
       else ### nextIteration will be put here ###
         loop(x1,x2,x3,x4,x5,...)   // tail recursion
       else
         <failure>
     else
       <failure>
  loop(x1,x2,x3,x4,x5,...)

 * The part marked
 *
 *   ### nextIteration will be put here ###
 *
 * is code that calculates the next values of the index variables similar
 * to the prolog

  // nextIteration:
  x1 := next1;
  ### loopElse will be put here ###
  if (stream2.hasNext)
    {
      x2 := stream2.next;
      x3 := next3;
      if (stream4.hasNext)
        {
          x4 := stream4.next;
          x5 := next5;

          ### loop tail recursive call will be put here ###

        }
      else
        {
          loopElse
        }
    }
  else
    {
      loopElse
    }

 * If needed, the code for the <failure> case will be put into a loopElse
 * feature that can be called at different locations:

  loopElse() =>
    <failure>
    ### OPTIONAL FALSE ###

 * In case <success> or <failure> are missing or do (syntactically) not produce
 * a result, an automatic result TRUE or FALSE, resp., will be added.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Loop extends ANY
{

  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for loop result vars
   */
  static private long _id_ = 0;


  /**
   * env var to enable debug output for code generated for loops:
   */
  static private final boolean FUZION_DEBUG_LOOPS = "true".equals(System.getenv("FUZION_DEBUG_LOOPS"));


  /*----------------------------  variables  ----------------------------*/


  /**
   * The list of index variables of this loop, never null
   */
  private final List<Feature> _indexVars;


  /**
   * The list of next values for index variables, may contain null for index
   * variables that do not get update
   */
  private final List<Feature> _nextValues;


  /**
   * Loop prolog: Code block that initializes the index variables with their
   * initial values. May be null if none.
   */
  private final Block _prolog;
  private List<Stmnt> _prologSuccessBlock;


  /**
   * Code to be executed to update index variables after _untilCond was checked
   * to be false.
   */
  private Expr _nextIteration = null;
  private List<Stmnt> _nextItSuccessBlock = null;


  /**
   * Success block or null.
   */
  private Block _successBlock;


  /**
   * Else block or null if not present. Parsed twice since we might need the code twice.
   */
  private Expr _elseBlock0;
  private Expr _elseBlock1;


  /**
   * Position of the "else" keyword if present, or something close to it if not
   */
  private SourcePosition _elsePos;

  /**
   * In case the else-clause has to be put into a routine, these are two
   * routines, the first for the prolog, the other for the rest of the loop.
   */
  private Feature[] _loopElse;


  /**
   * The name of this loop's tail recursive routine, used as prefix for internal names
   */
  private final String _rawLoopName;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a loop
   *
   * @param pos Position of the intial keyword ("for", "variant", "while")
   * introducing this loop.
   *
   * @param iv index vars of this loop
   *
   * @param nv next values for index variables, may contain null for index
   * variables that do not get update
   *
   * @param var loop variant or null
   *
   * @param inv loop invariant or null
   *
   * @param whileCond loop while condition or null for true.
   *
   * @param block loop body or null for empty loop
   *
   * @param untilCond condition to exit loop successfully, null for false.
   *
   * @param successBlock block to execute on successful loop exit, null for none.
   *
   * @param elseBlock block to execute on unsuccessful loop exit, null for none.
   */
  public Loop(SourcePosition pos,
              List<Feature> iv,
              List<Feature> nv,
              Expr var,        /* NYI: loop variant currently ignored */
              List<Cond> inv,  /* NYI: loop invariant currently ignored */
              Expr whileCond,
              Block block,
              Expr untilCond,
              Block sb,
              Expr eb0,
              Expr eb1)
  {
    if (PRECONDITIONS) require
      (iv != null,
       nv != null,
       iv.size() == nv.size(),
       sb == null || untilCond != null,
       eb0 == null || eb0 instanceof Block || eb0 instanceof If);

    var prologPos = pos;
    var nextItPos = pos;
    var succPos = pos; // NYI: if present, use position of success block, otherwise of "until" condition
    _elsePos   = pos;  // NYI: if present, use position of "else" keyword
    _indexVars = iv;
    _nextValues = nv;
    block = Block.newIfNull(pos, block);
    _successBlock = sb;
    _elseBlock0 = eb0;
    _elseBlock1 = eb1;
    var loopName = FuzionConstants.REC_LOOP_PREFIX +  _id_++ ;
    _rawLoopName = loopName;
    if (!iterates() && whileCond == null && _elseBlock0 != null)
      {
        AstErrors.loopElseBlockRequiresWhileOrIterator(pos, _elseBlock0);
      }

    var hasImplicitResult = defaultSuccessAndElseBlocks(whileCond, untilCond, succPos);
    if (_elseBlock0 != null && iterates())
      {
        moveElseBlockToRoutine();
        _elseBlock0 = callLoopElse(false);
      }

    _prologSuccessBlock = new List<>();
    _prolog             = new Block(prologPos, _prologSuccessBlock, hasImplicitResult);
    if (!_indexVars.isEmpty())
      {
        _nextItSuccessBlock = new List<>();
        _nextIteration = new Block(nextItPos, _nextItSuccessBlock, hasImplicitResult);
        addIterators();
      }

    var formalArguments = new List<Feature>();
    var initialActuals = new List<Expr>();
    var nextActuals = new List<Expr>();
    initialArguments(formalArguments, initialActuals, nextActuals);
    var initialCall       = new Call(pos, loopName, initialActuals);
    var tailRecursiveCall = new Call(pos, loopName, nextActuals   );
    if (_nextIteration == null)
      {
        _nextIteration = tailRecursiveCall;
      }
    else
      {
        _nextItSuccessBlock.add(tailRecursiveCall);
      }

    if (untilCond != null)
      {
        _nextIteration = new If(untilCond.pos(),
                                untilCond,
                                Block.newIfNull(succPos, _successBlock),
                                _nextIteration);
      }
    block._statements.add(_nextIteration);
    if (whileCond != null)
      {
        block = Block.fromExpr(new If(whileCond.pos(), whileCond, block, _elseBlock0));
      }
    var p = block.pos();
    Feature loop = new Feature(p,
                               Consts.VISIBILITY_INVISIBLE,
                               Consts.MODIFIER_FINAL,
                               NoType.INSTANCE,
                               new List<String>(loopName),
                               formalArguments,
                               Function.NO_CALLS,
                               Contract.EMPTY_CONTRACT,
                               new Impl(p, block, Impl.Kind.RoutineDef))
      {
        public boolean resultInternal() { return true; }
      };
    _prologSuccessBlock.add(loop);
    _prologSuccessBlock.add(initialCall);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Return the AST for this loop using a tail recursive call.
   */
  public Expr tailRecursiveLoop()
  {
    if (FUZION_DEBUG_LOOPS)
      {
        System.out.println(_prolog);
      }
    return _prolog;
  }


  /**
   * Is any of the _indexVars an iteration ('x in set')?
   */
  private boolean iterates()
  {
    var result = false;
    for (var f : _indexVars)
      {
        result = result || f.impl()._kind == Impl.Kind.FieldIter;
      }
    return result;
  }


  /**
   * Does this loop implicitly produce the value of the last index variable as a result.
   *
   * This is the case for non-iterating loops without an else or success block.
   */
  private boolean lastIndexVarAsImplicitResult()
  {
    return !iterates() && _elseBlock0 == null && _successBlock == null && !_indexVars.isEmpty();
  }


  /**
   * Does this loop implicitly produce a boolean result that indicates successul
   * (until condition holds) or failed (while condition is false or iteration
   * ended) execution.
   *
   * This is the case loops that have an until condition and that are iterating
   * or have a while condition and that have neither a else nor a success block.
   */
  private boolean booleanAsImplicitResult(Expr whileCond, Expr untilCond)
  {
    return
      /* loop can fail: */     (iterates() || whileCond != null) &&
      /* loop can succeed: */  (untilCond != null) &&
      /* success and else block do not end in expression: */
      (_successBlock == null || !_successBlock.producesResult() ||
       _elseBlock0 == null   || !_elseBlock0  .producesResult());
  }


  /**
   * Create default code for success and else blocks if not present.  Default code is used for
   *
   * 1. loops with index variables that are no iterators and no else block nor
   *    success block: The last index variable is returned by both success and
   *    else blocks.
   *
   * 2. loops that can fail and succeed but that syntactically cannot return a
   *    value (a call can syntactically return a value, even though the called
   *    function may return void, an assignment or empty block can not). In this
   *    case, success block returns true and else block returns false.
   *
   * @return true if implicit success and else blocks have been added.
   */
  private boolean defaultSuccessAndElseBlocks(Expr whileCond, Expr untilCond, SourcePosition succPos)
  {
    boolean result = false;
    if (lastIndexVarAsImplicitResult())
      { /* add last index var as implicit result */
        Feature lastIndexVar = _indexVars.getLast();
        var p = lastIndexVar.pos();
        var readLastIndexVar0 = new Call(p, lastIndexVar.featureName().baseName());
        var readLastIndexVar1 = new Call(p, lastIndexVar.featureName().baseName());
        var readLastIndexVar2 = new Call(p, lastIndexVar.featureName().baseName());
        _elseBlock0   = Block.fromExpr(readLastIndexVar0);
        _elseBlock1   = Block.fromExpr(readLastIndexVar1);
        _successBlock = Block.fromExpr(readLastIndexVar2);
        result = true;
      }
    else if (booleanAsImplicitResult(whileCond, untilCond))
      { /* add implicit TRUE / FALSE results to success and else blocks: */
        _successBlock = Block.newIfNull(succPos, _successBlock);
        _successBlock._statements.add(BoolConst.TRUE );
        if (_elseBlock0 == null)
          {
            _elseBlock0 = BoolConst.FALSE;
            _elseBlock1 = BoolConst.FALSE;
          }
        else
          {
            var e0 = Block.fromExpr(_elseBlock0);
            var e1 = Block.fromExpr(_elseBlock1);
            e0._statements.add(BoolConst.FALSE);
            e1._statements.add(BoolConst.FALSE);
            _elseBlock0 = e0;
            _elseBlock1 = e1;
          }
        result = true;
      }
    return result;
  }


  /**
   * Create code that moves the else block into dedicated routines.
   */
  private void moveElseBlockToRoutine()
  {
    _loopElse = new Feature[2];
    for (int ei=0; ei<2; ei++)
      {
        var name = _rawLoopName + "else" + ei;
        _loopElse[ei] = new Feature(_elsePos,
                                    Consts.VISIBILITY_INVISIBLE,
                                    Consts.MODIFIER_FINAL,
                                    NoType.INSTANCE,
                                    new List<String>(name),
                                    new List<>(),
                                    Function.NO_CALLS,
                                    Contract.EMPTY_CONTRACT,
                                    new Impl(_elsePos, ei == 0 ? _elseBlock0 : _elseBlock1, Impl.Kind.RoutineDef))
          {
            public boolean resultInternal() { return true; }
          };
      }
  }


  /**
   * Create a call to the feature that contains the else block of this loop.
   *
   * @param inProlog true for a call in the loop prolog, false for a call after
   * successful execution of the prolog.
   *
   * @return an expression that performs the call
   */
  private Expr callLoopElse(boolean inProlog)
  {
    if (PRECONDITIONS) require
                         (_loopElse != null);

    return new Call(_elsePos,
                    _loopElse[inProlog ? 0 : 1].featureName().baseName(),
                    Expr.NO_EXPRS);
  }


  /**
   * Helper routine to determine the formal and actual arguments to be passed to the tail recursive loop
   *
   * @param formalArguments will receive the formal argument declarations
   *
   * @param initialActuals will receive the initial actual arguments after prolog
   *
   * @param nextActuals will receive the actual arguments after nextIteration
   */
  private void initialArguments(List<Feature> formalArguments,
                                List<Expr> initialActuals,
                                List<Expr> nextActuals)
  {
    int i = -1;
    Iterator<Feature> ivi = _indexVars.iterator();
    Iterator<Feature> nvi = _nextValues.iterator();
    while (ivi.hasNext())
      {
        i++;
        Feature f = ivi.next();
        Feature n = nvi.next();
        if (CHECKS) check
          (f.impl()._kind != Impl.Kind.FieldIter);
        var p = f.pos();
        var ia = new Call(p, f.featureName().baseName());
        var na = new Call(p, f.featureName().baseName());
        var type = (f.impl()._kind == Impl.Kind.FieldDef)
          ? null        // index var with type inference from initial actual
          : _indexVars.get(i).returnType().functionReturnType();
        var arg = new Feature(p,
                              Consts.VISIBILITY_INVISIBLE,
                              type,
                              f.featureName().baseName(),
                              type == null ? ia : null,
                              null /* NYI outer */);
        arg._isIndexVarUpdatedByLoop = true;
        formalArguments.add(arg);
        initialActuals .add(ia);
        nextActuals    .add(na);
      }
  }


  /**
   * Helper routine to add code to _prologSuccessBlock and _nextItSuccessBlock
   * for index vars.
   */
  private void addIterators()
  {
    boolean mustDeclareLoopElse = _loopElse != null;
    int iteratorCount = 0;
    var ivi = _indexVars .iterator();
    var nvi = _nextValues.iterator();
    while (ivi.hasNext())
      {
        Feature f = ivi.next();
        Feature n = nvi.next();
        if (f.impl()._kind == Impl.Kind.FieldIter)
          {
            if (mustDeclareLoopElse)
              { // we declare loopElse function after all non-iterating index
                // vars such that the else clause can access these vars.
                _prologSuccessBlock.add(_loopElse[0]);
                _nextItSuccessBlock.add(_loopElse[1]);
                mustDeclareLoopElse = false;
              }
            var streamName = _rawLoopName + "stream" + (iteratorCount++);
            var p = f.pos();
            Call asStream = new Call(p, f.impl()._initialValue, "asStream");
            Feature stream = new Feature(p,
                                         Consts.VISIBILITY_INVISIBLE,
                                         /* modifiers */   0,
                                         /* return type */ NoType.INSTANCE,
                                         /* name */        new List<>(streamName),
                                         /* args */        new List<Feature>(),
                                         /* inherits */    new List<>(),
                                         /* contract */    null,
                                         /* impl */        new Impl(p, asStream, Impl.Kind.FieldDef));
            stream._isIndexVarUpdatedByLoop = true;  // hack to prevent error AstErrors.initialValueNotAllowed(this)
            _prologSuccessBlock.add(stream);
            Call hasNext1 = new Call(p, new Call(p, streamName), "hasNext" );
            Call hasNext2 = new Call(p, new Call(p, streamName), "hasNext" );
            Call next1    = new Call(p, new Call(p, streamName), "next");
            Call next2    = new Call(p, new Call(p, streamName), "next");
            List<Stmnt> prolog2 = new List<>();
            List<Stmnt> nextIt2 = new List<>();
            If ifHasNext1 = new If(p, hasNext1, new Block(p, prolog2));
            If ifHasNext2 = new If(p, hasNext2, new Block(p, nextIt2));
            if (_loopElse != null)
              {
                ifHasNext1.setElse(Block.fromExpr(callLoopElse(true )));
                ifHasNext2.setElse(Block.fromExpr(callLoopElse(false)));
              }
            _prologSuccessBlock.add(ifHasNext1);
            _nextItSuccessBlock.add(ifHasNext2);
            _prologSuccessBlock = prolog2;
            _nextItSuccessBlock = nextIt2;
            f.setImpl(new Impl(f.impl().pos, next1, Impl.Kind.FieldDef));
            n.setImpl(new Impl(n.impl().pos, next2, Impl.Kind.FieldDef));
          }
        _prologSuccessBlock.add(f);
        _nextItSuccessBlock.add(n);
        f._isIndexVarUpdatedByLoop = true;
        n._isIndexVarUpdatedByLoop = true;
      }
  }

}

/* end of file */
