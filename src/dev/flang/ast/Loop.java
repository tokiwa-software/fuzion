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
import java.util.Set;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.Pair;
import dev.flang.util.SourcePosition;


/**
 * Loops are very generic in Fuzion.  The basic loop structure consists of a
 * list of index variables, a loop failure condition, a loop body, a loop
 * success condition and a loop epilog consisting of a branch executed after
 * successful loop termination and one after failed loop termination.
 *
 * In general, this is

  for
    x1 := init1, next1
    x2 in set2
    x3 := init3, next3
    x4 in set4
    x5 := init5, next5
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
  x1 := init1
  list2 := set2.as_list
  ### loopElse will be put here ###
  match list2
    c2 Cons =>
      x2 := c2.head
      x2t := c2.tail
      x3 := init3
      list4 := set4.as_list
      match list4
        c4 Cons =>
          x4 := c4.head
          x4t := c4.tail
          x5 := init5

          ### loop will be put here ###

        _ nil => loopElse
    _ nil => loopElse

 * The loop will be implemented using a tail recursive feature as follows

  loop(x1, x2, x2a, x3, x4, x4a, x5, ... inferred-type) =>
     if whileCond
       <body>
       if untilCond
         <success>
         ### OPTIONAL TRUE ###
       else ### nextIteration will be put here ###
         loop(x1,x2,x2t,x3,x4,x4t,x5,...)   // tail recursion
       else
         <failure>
     else
       <failure>
  loop(x1,x2,x2t,x3,x4,x4t,x5,...)

 * The part marked
 *
 *   ### nextIteration will be put here ###
 *
 * is code that calculates the next values of the index variables similar
 * to the prolog

  // nextIteration:
  x1 := next1
  ### loopElse will be put here ###
  match x2a
    c2 Cons =>
      x2 := c2.head
      x2t := c2.tail
      x3 := next3
      match x4a
        c4 Cons =>
          x4 := c4.head
          x4t := c4.tail
          x5 := next5

          ### loop tail recursive call will be put here ###

        _ nil => loopElse
    _ nil => loopElse

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
  static private final boolean FUZION_DEBUG_LOOPS = FuzionOptions.boolPropertyOrEnv("FUZION_DEBUG_LOOPS");


  /*----------------------------  constants  ----------------------------*/


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
   * The block containing the implementation of the loop.
   */
  private final Block _impl;


  /**
   * The name of this loop's tail recursive routine, used as prefix for internal names
   */
  private final String _rawLoopName;


  /**
   * Position of the "else" keyword if present, or something close to it if not
   */
  private final SourcePosition _elsePos;


  /*----------------------------  variables  ----------------------------*/


  /**
   * Success block or null.
   */
  private Block _successBlock;


  /**
   * Else block or null if not present. Parsed twice since we might need the code twice.
   */
  private Expr _elseBlock0;
  private Expr _elseBlock1;
  private Expr _elseBlock2;


  /**
   * In case the else-clause has to be put into a routine, these are two
   * routines, the first for the prolog, the other for the rest of the loop.
   */
  private Feature[] _loopElse;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a loop
   *
   * @param pos Position of the initial keyword ("for", "variant", "while")
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
   * @param sb block to execute on successful loop exit, null for none.
   *
   * @param eb0 block to execute on unsuccessful loop exit, null for none.
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
              Expr eb1,
              Expr eb2)
  {
    if (PRECONDITIONS) require
      (iv != null,
       nv != null,
       iv.size() == nv.size(),
       sb == null || untilCond != null,
       eb0 == null || eb0 instanceof Block || eb0 instanceof If);

    _elsePos   = pos;  // NYI: if present, use position of "else" keyword
    _indexVars = iv;
    _nextValues = nv;
    block = Block.newIfNull(block);
    _successBlock = sb;
    _elseBlock0 = eb0;
    _elseBlock1 = eb1;
    _elseBlock2 = eb2;
    var loopName = FuzionConstants.REC_LOOP_PREFIX +  _id_++ ;
    _rawLoopName = loopName;
    if (!iterates() && whileCond == null && _elseBlock0 != null)
      {
        AstErrors.loopElseBlockRequiresWhileOrIterator(pos, _elseBlock0);
      }

    var hasImplicitResult = defaultSuccessAndElseBlocks(whileCond, untilCond);
    // if there are no iteratees then else block may access every loop var.
    // if there are iteratees we move else block to feature and
    // insert it later, see `addIterators()`.
    if (_elseBlock0 != null && iterates())
      {
        moveElseBlockToRoutine();
        _elseBlock0 = new Block(new List<>(_loopElse[1], callLoopElse(1)));
      }

    var prologBlock = new List<Expr>();
    var nextItBlock = new List<Expr>();

    var r = addIterators(prologBlock, nextItBlock);
    var prologSuccessBlock = r.v0();
    var nextItSuccessBlock = r.v1();

    var formalArguments = new List<AbstractFeature>();
    var initialActuals = new List<Expr>();
    var nextActuals = new List<Expr>();
    initialArguments(formalArguments, initialActuals, nextActuals);

    var tailRecursiveCall = new Call(pos, null, loopName, nextActuals);
    nextItSuccessBlock.add(tailRecursiveCall);

    Expr nextIteration = untilCond == null
      ? new Block(nextItBlock, hasImplicitResult)
      : new If(untilCond.pos(),
               untilCond,
               Block.newIfNull(_successBlock),
               new Block(nextItBlock, hasImplicitResult));

    block._expressions.add(nextIteration);
    if (whileCond != null)
      {
        block = Block.fromExpr(new If(whileCond.pos(), whileCond, block, _elseBlock0));
      }
    var p = block.pos();
    Feature loop = new Feature(p,
                               Visi.PRIV,
                               0,
                               NoType.INSTANCE,
                               new List<String>(loopName),
                               formalArguments,
                               Function.NO_CALLS,
                               Contract.EMPTY_CONTRACT,
                               new Impl(p, block, Impl.Kind.RoutineDef));

    var initialCall = new Call(pos, null, loopName, initialActuals);
    prologSuccessBlock.add(initialCall);

    _impl           = new Block(new List<>(loop, prologBlock), hasImplicitResult);
    _impl._newScope = true;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Return the AST for this loop using a tail recursive call.
   */
  public Expr tailRecursiveLoop()
  {
    if (FUZION_DEBUG_LOOPS)
      {
        say(_impl);
      }
    return _impl;
  }


  /**
   * Is any of the _indexVars an iteration ('x in Set')?
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
   * Does this loop implicitly produce a boolean result that indicates successful
   * (until condition holds) or failed (while condition is false or iteration
   * ended) execution.
   *
   * This is the case for loops that have an until condition and that are iterating
   * or have a while condition and that have neither a else nor a success block.
   */
  private boolean booleanAsImplicitResult(Expr whileCond, Expr untilCond)
  {
    return
      /* loop can fail: */     (iterates() || whileCond != null) &&
      /* loop can succeed: */  (untilCond != null) &&
      /* success and else block do not end in expression: */
      (_successBlock == null || _elseBlock0 == null);
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
  private boolean defaultSuccessAndElseBlocks(Expr whileCond, Expr untilCond)
  {
    boolean result = false;
    if (lastIndexVarAsImplicitResult())
      { /* add last index var as implicit result */
        Feature lastIndexVar = _indexVars.getLast();
        var p = lastIndexVar.pos();
        var readLastIndexVar0 = new Call(p, lastIndexVar.featureName().baseName());
        var readLastIndexVar1 = new Call(p, lastIndexVar.featureName().baseName());
        var readLastIndexVar2 = new Call(p, lastIndexVar.featureName().baseName());
        var readLastIndexVar3 = new Call(p, lastIndexVar.featureName().baseName());
        _elseBlock0   = Block.fromExpr(readLastIndexVar0);
        _elseBlock1   = Block.fromExpr(readLastIndexVar1);
        _elseBlock2   = Block.fromExpr(readLastIndexVar2);
        _successBlock = Block.fromExpr(readLastIndexVar3);
        result = true;
      }
    else if (booleanAsImplicitResult(whileCond, untilCond))
      { /* add implicit TRUE / FALSE results to success and else blocks: */
        _successBlock = Block.newIfNull(_successBlock);
        _successBlock._expressions.add(BoolConst.TRUE );
        if (_elseBlock0 == null)
          {
            _elseBlock0 = BoolConst.FALSE;
            _elseBlock1 = BoolConst.FALSE;
            _elseBlock2 = BoolConst.FALSE;
          }
        else
          {
            var e0 = Block.fromExpr(_elseBlock0);
            var e1 = Block.fromExpr(_elseBlock1);
            var e2 = Block.fromExpr(_elseBlock2);
            e0._expressions.add(BoolConst.FALSE);
            e1._expressions.add(BoolConst.FALSE);
            e2._expressions.add(BoolConst.FALSE);
            _elseBlock0 = e0;
            _elseBlock1 = e1;
            _elseBlock2 = e2;
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
    _loopElse = new Feature[3];
    for (int ei=0; ei<3; ei++)
      {
        var name = _rawLoopName + "else" + ei;
        _loopElse[ei] = new Feature(_elsePos,
                                    Visi.PRIV,
                                    0,
                                    NoType.INSTANCE,
                                    new List<String>(name),
                                    new List<>(),
                                    Function.NO_CALLS,
                                    Contract.EMPTY_CONTRACT,
                                    new Impl(_elsePos, ei == 0 ? _elseBlock0 : (ei == 1 ? _elseBlock1 : _elseBlock2), Impl.Kind.RoutineDef));
      }
  }


  /**
   * Create a call to the feature that contains the else block of this loop.
   *
   * @param elseNum
   * 0 for a call to else-clause in the loop prolog
   * 1 for a call to else-clause if while-condition fails
   * 2 for a call to else-clause in remaining cases
   *
   * @return an expression that performs the call
   */
  private Expr callLoopElse(int elseNum)
  {
    if (PRECONDITIONS) require
                         (_loopElse != null);

    return new Call(_elsePos,
                    _loopElse[elseNum].featureName().baseName());
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
  private void initialArguments(List<AbstractFeature> formalArguments,
                                List<Expr> initialActuals,
                                List<Expr> nextActuals)
  {
    int i = -1;
    int iteratorCount = 0;
    Iterator<Feature> ivi = _indexVars.iterator();
    while (ivi.hasNext())
      {
        i++;
        Feature f = ivi.next();

        // iterators should have been replaced by FieldDef in `addIterators`
        if (CHECKS) check
          (f.impl()._kind != Impl.Kind.FieldIter);

        var p = f.pos();
        var ia = new Call(p, FuzionConstants.ITER_ARG_PREFIX_INIT + f.featureName().baseName());
        var na = new Call(p, FuzionConstants.ITER_ARG_PREFIX_NEXT + f.featureName().baseName());
        var type = (f.impl()._kind == Impl.Kind.FieldDef)
          ? null        // index var with type inference from initial actual
          : _indexVars.get(i).returnType().functionReturnType();
        var arg = new Feature(p,
                              Visi.PRIV,
                              type,
                              f.featureName().baseName(),
                              type != null ? Impl.FIELD
                                           : new Impl(Impl.Kind.FieldActual));
        arg._isIndexVarUpdatedByLoop = true;
        // no need to add for features named just underscore: _
        if (!f.featureName().isInternal())
          {
            formalArguments.add(arg);
            initialActuals .add(ia);
            nextActuals    .add(na);
          }
        if (f._isLoopIterator)
          {
            var argList = new Feature(SourcePosition.notAvailable,
                                      Visi.PRIV,
                                      null,
                                      f._loopIteratorListName + "arg",
                                      new Impl(Impl.Kind.FieldActual));
            formalArguments.add(argList);
            var listName = _rawLoopName + "list" + (iteratorCount++);
            initialActuals.add(new Call(p, new Call(p, listName + "cons"), "tail"));
            nextActuals.add(new Call(p, new Call(p, listName + "cons"), "tail"));
          }
      }
  }


  /*
   * Get a FeatureVisitor that prefixes calls that are contained
   * in set names with the given prefix.
   */
  private FeatureVisitor prefixVisitor(Set<String> names, String prefix)
  {
    return new FeatureVisitor() {
      @Override
      public Expr action(Call c, AbstractFeature outer)
      {
        if (c._target == null && names.contains(c._name))
          {
            c._name = prefix + c._name;
          }
        return super.action(c, outer);
      }

      @Override
      public Expr action(Function f, AbstractFeature outer)
      {
        f._expr.visit(this, outer);
        return super.action(f, outer);
      }
    };
  }


  /**
   * Helper routine to add code to prologBlock and nextItBlock for index vars,
   * and return the corresponding success blocks.
   */
  private Pair<List<Expr>,List<Expr>> addIterators(List<Expr> prologBlock, List<Expr> nextItBlock)
  {
    boolean mustDeclareLoopElse = _loopElse != null;
    int iteratorCount = 0;
    var ivi = _indexVars .iterator();
    var nvi = _nextValues.iterator();

    var names = new TreeSet<String>();

    while (ivi.hasNext())
      {
        Feature f = ivi.next();
        Feature n = nvi.next();

        f.impl().expr().visit(prefixVisitor(names, FuzionConstants.ITER_ARG_PREFIX_INIT), null);
        n.impl().expr().visit(prefixVisitor(names, FuzionConstants.ITER_ARG_PREFIX_NEXT), null);

        if (f.impl()._kind == Impl.Kind.FieldIter)
          {
            if (mustDeclareLoopElse)
              { // we declare loopElse function after all non-iterating index
                // vars such that the else clause can access these vars.
                prologBlock.add(_loopElse[0]);
                nextItBlock.add(_loopElse[2]);
                mustDeclareLoopElse = false;
              }
            var listName = _rawLoopName + "list" + (iteratorCount++);
            var p = SourcePosition.notAvailable;
            Call asList = new Call(f.impl().expr().pos(), f.impl().expr(), "as_list");
            Feature list = new Feature(p,
                                         Visi.PRIV,
                                         /* modifiers */   0,
                                         /* return type */ NoType.INSTANCE,
                                         /* name */        new List<>(listName),
                                         /* args */        new List<>(),
                                         /* inherits */    new List<>(),
                                         /* contract */    null,
                                         /* impl */        new Impl(p, asList, Impl.Kind.FieldDef));
            list._isIndexVarUpdatedByLoop = true;  // hack to prevent error AstErrors.initialValueNotAllowed(this)
            prologBlock.add(list);
            ParsedType nilType = new ParsedType(p, "nil", new List<>(), null);
            ParsedType consType = new ParsedType(p, "Cons", new List<>(), null);
            Call next1    = new Call(p, new Call(p, listName + "cons"), "head");
            Call next2    = new Call(p, new Call(p, listName + "cons"), "head");
            List<Expr> prolog2 = new List<>();
            List<Expr> nextIt2 = new List<>();
            Case match1c = new Case(p, consType, listName + "cons", new Block(prolog2));
            Case match1n = new Case(p, nilType, listName + "nil", (_loopElse != null) ? Block.fromExpr(callLoopElse(0)) : Block.newIfNull(null));
            Match match1 = new Match(p, new Call(p, listName), new List<AbstractCase>(match1c, match1n));
            Case match2c = new Case(p, consType, listName + "cons", new Block(nextIt2));
            Case match2n = new Case(p, nilType, listName + "nil", (_loopElse != null) ? Block.fromExpr(callLoopElse(2)) : Block.newIfNull(null));
            Match match2 = new Match(p, new Call(p, listName + "arg"), new List<AbstractCase>(match2c, match2n));
            prologBlock.add(match1);
            nextItBlock.add(match2);
            prologBlock = prolog2;
            nextItBlock = nextIt2;
            f.setImpl(new Impl(f.impl().pos, next1, Impl.Kind.FieldDef));
            n.setImpl(new Impl(n.impl().pos, next2, Impl.Kind.FieldDef));
            f._isLoopIterator = true;
            f._loopIteratorListName = listName;
            n._isLoopIterator = true;
            n._loopIteratorListName = listName;
          }

        f._isIndexVarUpdatedByLoop = true;
        n._isIndexVarUpdatedByLoop = true;

        names.add(f.featureName().baseName());
        prologBlock.add(createInternalIterArgFeature(f, FuzionConstants.ITER_ARG_PREFIX_INIT));
        nextItBlock.add(createInternalIterArgFeature(n, FuzionConstants.ITER_ARG_PREFIX_NEXT));
      }

    // replace names in else clause
    if (_loopElse != null)
      {
        _loopElse[0].code().visit(prefixVisitor(names, FuzionConstants.ITER_ARG_PREFIX_INIT), _loopElse[0]);
        _loopElse[2].code().visit(prefixVisitor(names, FuzionConstants.ITER_ARG_PREFIX_NEXT), _loopElse[2]);
      }

    return new Pair<>(prologBlock, nextItBlock);
  }


  /*
   * This copies the given feature and prefixes
   * the name with prefix.
   *
   * This is done to avoid name clashes with the formal arguments
   * of the tail recursive loop feature.
   */
  private Feature createInternalIterArgFeature(Feature f, String prefix)
  {
    var f1 = new Feature(f.visibility(), f.modifiers(), f.returnType(),
      new List<>(new ParsedName(f.pos(), prefix + f.featureName().baseName())),
      new List<>(), new List<>(),
      Contract.EMPTY_CONTRACT, f.impl(), null);
    f1._isLoopIterator = f._isLoopIterator;
    f1._loopIteratorListName = f._loopIteratorListName;
    f1._isIndexVarUpdatedByLoop = f._isIndexVarUpdatedByLoop;
    return f1;
  }

}

/* end of file */
