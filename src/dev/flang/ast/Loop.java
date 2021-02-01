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
 * Tokiwa GmbH, Berlin
 *
 * Source of class Loop
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Loops are very generic in Fusion. The basic loop structure consists of a list
 * of index variables, a loop failure condition, a loop body, a loop success
 * condition and a loop epilog consiting of a branch executed after successful
 * loop termination and one after failed loop termination.
 *
 * In general, this is

  for
    x1 := init1, next1;
    x2 in set2;
    x3 := init3, next3;
    x4 in set4;
    x5 := init5, next5;
  while <continueCond>
    <body>
  until <successCond>
    <success>
  else
    <failure>

 * This will be converted into a loop prolog that defines a variable to hold the
 * loop success, defines temp vars for streams as needed and initializes the
 * index variables

  // loop prolog
  success := false;
  x1 := init1;
  stream2 := set2.asStream;
  if (stream2.hasNext)
    {
      x2 := stream2.next;
      x3 := init3;
      stream4 := set4.asStream;
      if (stream4.hasNext)
        {
          x4 := stream4.next;
          x5 := init5;

 * in case any of the code block <success> or <failure> is missing and there are
 * no streams, the prolog also sets a result to the value of the last index
 * variable

          res := x5

* if initialization was sucessful, we set a flag that keeps the loop running
* before entring the loop

          continue := true;

 * Next, while continue is true we run the loop by first checking if
 * <continueCond> holds. If not, the loop has failed, otherwilse the loop can
 * run and execute <body>

          while continue && <continueCond>
            {
              <body>

 * next, we check if the loop must terminate with success since <successCode>
 * holds;

              continue = false;
              if <successCond>
                {
                  success = true;
                }

 * if the loop was not yet successul, we have to update the index variables. In
 * case any of these updates fails, we exit the loop with state == failure.

              else
                {
                  x1 = next1;
                  if (stream2.hasNext)
                    {
                      x2 = stream2.next;
                      x3 = next3;
                      if (stream4.hasNext)
                        {
                          x4 = stream4.next;
                          x5 = next5;

 * Again, in case any of the code blocks <success> or <failure> is missing, we
 * update the result to the value of the last index variable

                          res := x5

 * and we record that we continue

                          continue = true;
                        }
                    }
                }
            }
        }
    }

 * finally, in the epilog, we executed <success> or <failure> depending on state:

      if (success)
        res = <success>
      else
        res = <failure>

 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Loop extends Expr
{

  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for loop result vars
   */
  static private long _id_ = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The list of index variables of this loop, never null
   */
  final List<Feature> _indexVars;


  /**
   * The list of iterator variables of this loop, a subset of _indexVars
   */
  final List<Feature> _iteratorVars;


  /**
   * The list of next values for index variables, may contain null for index
   * variables that do not get update
   */
  public final List<Expr> _nextValues;


  /**
   * loop variant or null if not present.
   */
  Expr _var;


  /**
   * loop invariant or null if not present.
   */
  final List<Cond> _inv;


  /**
   * Loop prolog: Code block that initializes the index variables with their
   * initial values. May be null if none.
   */
  public Block _prolog;


  /**
   * condition that must hold to enter the first and any following loop
   * iteration. null means true.
   */
  public Expr _whileCond;


  /**
   * Loop body. May be null if no loop body present.
   */
  public Block _block;


  /**
   * condition that must hold to exit the loop successfully after last
   * iteration.  null means false.
   */
  public Expr _untilCond;

  /**
   * Code to be executed to update index variables after _untilCond was checked
   * to be false.
   */
  public Block _nextIteration;


  /**
   * Code to be executed when loop exits after successful check of
   * _untilCond.  May be null.
   */
  public Block _successBlock;


  /**
   * Code to be executed when loop exits after failed check of _whileCond.  May
   * be null.
   */
  public Expr _elseBlock;


  /**
   * Result type of the value returned by this loop's _successBlock and _elseBlock
   */
  public Type _type;


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
   * @param v loop variant or null
   *
   * @param i loop invariant or null
   *
   * @param w loop while condition or null for true.
   *
   * @param b loop body or null for empty loop
   *
   * @param u condition to exit loop successfully, null for false.
   *
   * @param ub block to execute on successful loop exit, null for none.
   *
   * @param eb block to execute on unsuccessful loop exit, null for none.
   */
  public Loop(SourcePosition pos, List<Feature> iv, List<Expr> nv, Expr v, List<Cond> i, Expr w, Block b, Expr u, Block ub, Expr eb)
  {
    super(pos);

    if (PRECONDITIONS) require
      (iv != null,
       nv != null,
       iv.size() == nv.size(),
       ub == null || u != null,
       eb == null || eb instanceof Block || eb instanceof If);

    _indexVars = iv;
    _iteratorVars = new List<>();
    _var = v;
    _inv = i;
    _whileCond = w;
    _block = b;
    if (b != null)
      {
        /* declarations in block remain visible, details are handled by Feature.findFieldDefInScope */
        b._newScope = false;
      }
    _untilCond = u;
    _successBlock = ub;
    _elseBlock = eb;
    _nextValues = nv;
    addPrologAndNextIteration();
    if (!iterates() && _whileCond == null && _elseBlock != null)
      {
        FeErrors.loopElseBlockRequiresWhileOrIterator(pos, _elseBlock);
      }
  }
  public Expr tailRec()
  {
    Expr result = this;
    if (!iterates() && _indexVars.isEmpty() && _untilCond == null && _successBlock == null && _elseBlock == null)
      {
        /* create the following:

           if prolog    // true if prolog is null
             loop is
               if whileCond   // true if whileCond is null
                 block
                 if !untilCond   // true if untilCond is null
                   if nextIteration   // true if nextIteration is null
                     loop (tail recursion)
                 else
                   successBlock  // nop if successBlock is null
               else
                 elseBlock   // nop if elseBlock is null
             loop
        */

        var loopName = "--loop<"+ _id_++ +">--";
        var statements = new List<Stmnt>();
        var formalArguments = new List<Feature>();
        var initialActuals = new List<Expr>();
        var nextActuals = new List<Expr>();
        initialArguments(formalArguments, initialActuals, nextActuals);
        var loopBlock = new Block(pos, statements);
        Feature loop = new Feature(pos,
                                   Consts.VISIBILITY_INVISIBLE,
                                   Consts.MODIFIER_FINAL,
                                   NoType.INSTANCE,
                                   new List<String>(loopName),
                                   FormalGenerics.NONE,
                                   formalArguments,
                                   Function.NO_CALLS,
                                   new Contract(null,null,null),
                                   new Impl(pos, loopBlock, Impl.Kind.Routine));
        if (_block == null)
          {
            _block = new Block(pos, new List<Stmnt>());
          }
        statements.add(_whileCond != null
                       ? new If(pos, _whileCond, _block)
                       : _block);
        _block.statements_.add(new Call(pos, loopName, new List<Expr>()));

        if (_prolog == null)
          {
            _prolog = new Block(pos, new List<Stmnt>());
          }
        _prolog.statements_.add(loop);
        _prolog.statements_.add(new Call(pos, loopName, new List<Expr>()));
        result = _prolog;
      }
    return result;
  }

  Expr resolveSyntacticSugar2(Resolution res, Feature outer)
  {
    Expr result = this;
    if (false) // NYI: remove
    if (!iterates() && _indexVars.isEmpty() && _untilCond == null && _successBlock == null && _elseBlock == null) // res._options.tailRecursionInsteadOfLoops())
      {
        /* create the following:

           if prolog    // true if prolog is null
             loop is
               if whileCond   // true if whileCond is null
                 block
                 if !untilCond   // true if untilCond is null
                   if nextIteration   // true if nextIteration is null
                     loop (tail recursion)
                 else
                   successBlock  // nop if successBlock is null
               else
                 elseBlock   // nop if elseBlock is null
             loop
        */

        var loopName = "--loop<"+ _id_++ +">--";
        var statements = new List<Stmnt>();
        var formalArguments = new List<Feature>();
        var initialActuals = new List<Expr>();
        var nextActuals = new List<Expr>();
        initialArguments(formalArguments, initialActuals, nextActuals);
        var loopBlock = new Block(pos, statements);
        Feature loop = new Feature(pos,
                                   Consts.VISIBILITY_INVISIBLE,
                                   Consts.MODIFIER_FINAL,
                                   NoType.INSTANCE,
                                   new List<String>(loopName),
                                   FormalGenerics.NONE,
                                   formalArguments,
                                   Function.NO_CALLS,
                                   new Contract(null,null,null),
                                   new Impl(pos, loopBlock, Impl.Kind.Routine));
        loop.findDeclarations(outer);
        if (_block == null)
          {
            _block = new Block(pos, new List<Stmnt>());
          }
        statements.add(new If(pos, _whileCond, _block));
        loopBlock.visit(new FeatureVisitor()
          {
            public Expr action (Current c, Feature outer)
            {
              var getOuter = new Current(pos, loop.thisType());
              Feature or = loop.outerRef();
              Expr getOuter2 = new Call(pos, or._featureName.baseName(), Call.NO_GENERICS, Expr.NO_EXPRS, getOuter, or, null)
                .resolveTypes(res, loop);
              return getOuter2;
            }
          }, outer);
        loop.scheduleForResolution(res);
        res.resolveTypes();
        _block.statements_.add(new Call(pos, new Current(pos, outer.thisType()), loop, -1).resolveTypes(res, loop));

        if (_prolog == null)
          {
            _prolog = new Block(pos, new List<Stmnt>());
          }
        _prolog.statements_.add(new Call(pos, new Current(pos, outer.thisType()), loop, -1).resolveTypes(res, outer));
        result = _prolog;
        System.out.println("replaced loop by tailrec: "+pos.show());

        /* interpreter code:
        var l = (Loop) s;
        result = Value.NO_VALUE;
        if (l._prolog != null)
          {
            result = execute(l._prolog, staticClazz, cur);
          }
        boolean success = false;
        while (!success &&
               (!l._iterates || result.i32Value() == 1) &&
               (l._whileCond == null || execute(l._whileCond, staticClazz, cur).boolValue()))
          {
            if (l._block != null)
              {
                result = execute(l._block, staticClazz, cur);
              }
            success = l._untilCond != null && execute(l._untilCond, staticClazz, cur).boolValue();
            if (!success && l._nextIteration != null)
              {
                result = execute(l._nextIteration, staticClazz, cur);
              }
          }

        result = success
          ? l._successBlock != null ? execute(l._successBlock, staticClazz, cur) : result
          : l._elseBlock    != null ? execute(l._elseBlock   , staticClazz, cur) : result;
        */
      }
    return result;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Helper routine to add code to prolog and nextIt statements for index vars
   *
   * @param prolog statements of loop prolog that will receive the code to set initial values of the index vars
   *
   * @param nextIt statements of loop's next iteration block that will update the index variables.
   */
  private void initialArguments(List<Feature> formalArguments,
                                List<Expr> initialActuals,
                                List<Expr> nextActuals)
  {
    Iterator<Feature> ivi = _indexVars.iterator();
    Iterator<Expr> nvi = _nextValues.iterator();
    while (ivi.hasNext())
      {
        Feature f = ivi.next();
        Expr n = nvi.next();
        if (f.impl.kind_ == Impl.Kind.FieldIter)
          {
            //            check
            //              (n == null);
            //            _iteratorVars.add(f);
            //            String streamName = "--loopIterationStream" + (_id_++) + "--";
            //            Call asStream = new Call(f.pos, f.impl.initialValue, "asStream", Call.NO_GENERICS, Call.NO_PARENTHESES);
            //            Feature stream = new Feature(f.pos,
            //                                         Consts.VISIBILITY_INVISIBLE,
            //                                         /* modifiers */   0,
            //                                         /* return type */ NoType.INSTANCE,
            //                                         /* name */        new List<>(streamName),
            //                                         /* generics */    FormalGenerics.NONE,
            //                                         /* args */        new List<Feature>(),
            //                                         /* inherits */    new List<Call>(),
            //                                         /* contract */    null,
            //                                         /* impl */        new Impl(f.pos, asStream, Impl.Kind.FieldDef));
            //            prolog.add(stream);
            //            Call hasNext1 = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call1: "+super.toString(); } }, "hasNext" );
            //            Call hasNext2 = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call2: "+super.toString(); } }, "hasNext" );
            //            Call next1    = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call3: "+super.toString(); } }, "next");
            //            Call next2    = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call4: "+super.toString(); } }, "next");
            //            List<Stmnt> prolog2 = new List<>();
            //            List<Stmnt> nextIt2 = new List<>();
            //            If ifHasNext1 = new If(f.pos, hasNext1, new Block(f.pos, prolog2));
            //            If ifHasNext2 = new If(f.pos, hasNext2, new Block(f.pos, nextIt2));
            //            ifHasNext1.setElse(new Block(f.pos, new List<Stmnt>(new IntConst(0))));
            //            ifHasNext2.setElse(new Block(f.pos, new List<Stmnt>(new IntConst(0))));
            //            prolog.add(ifHasNext1);
            //            nextIt.add(ifHasNext2);
            //            prolog = prolog2;
            //            nextIt = nextIt2;
            //            f.impl = new Impl(f.impl.pos, next1, Impl.Kind.FieldDef);
            //            n = next2;
            //
            var streamName = "NYI";
            Feature stream = null;
            Feature arg = new Feature(f.pos,
                                      Consts.VISIBILITY_INVISIBLE,
                                      stream.resultType(),
                                      streamName);
            initialActuals.add(new Call(f.pos, streamName));
            nextActuals   .add(new Call(f.pos, streamName));
          }
        else
          {
            int i = 0; // NYI
            Feature arg = new Feature(f.pos,
                                      Consts.VISIBILITY_INVISIBLE,
                                      _indexVars.get(i).resultType(),
                                      f.featureName().baseName());
            formalArguments.add(arg);
            initialActuals.add(f.impl.code_);
            nextActuals.add(n);
          }
      }
  }



  /**
   * Helper routine for constructor to create prolog and nextIteration for index
   * vars.
   */
  private void addPrologAndNextIteration()
  {
    if (_indexVars.isEmpty())
      {
        _prolog        = null;
        _nextIteration = null;
      }
    else
      {
        List<Stmnt> prolog = new List<>();
        List<Stmnt> nextIt = new List<>();
        _prolog        = new Block(pos(), prolog);
        _nextIteration = new Block(pos(), nextIt);
        addIterators(prolog, nextIt);
      }
  }


  /**
   * Helper routine to add code to prolog and nextIt statements for index vars
   *
   * @param prolog statements of loop prolog that will receive the code to set initial values of the index vars
   *
   * @param nextIt statements of loop's next iteration block that will update the index variables.
   */
  private void addIterators(List<Stmnt> prolog, List<Stmnt> nextIt)
  {
    Iterator<Feature> ivi = _indexVars.iterator();
    Iterator<Expr> nvi = _nextValues.iterator();
    while (ivi.hasNext())
      {
        Feature f = ivi.next();
        Expr n = nvi.next();
        if (f.impl.kind_ == Impl.Kind.FieldIter)
          {
            check
              (n == null);
            _iteratorVars.add(f);
            String streamName = "--loopIterationStream" + (_id_++) + "--";
            Call asStream = new Call(f.pos, f.impl.initialValue, "asStream", Call.NO_GENERICS, Call.NO_PARENTHESES);
            Feature stream = new Feature(f.pos,
                                         Consts.VISIBILITY_INVISIBLE,
                                         /* modifiers */   0,
                                         /* return type */ NoType.INSTANCE,
                                         /* name */        new List<>(streamName),
                                         /* generics */    FormalGenerics.NONE,
                                         /* args */        new List<Feature>(),
                                         /* inherits */    new List<Call>(),
                                         /* contract */    null,
                                         /* impl */        new Impl(f.pos, asStream, Impl.Kind.FieldDef));
            prolog.add(stream);
            Call hasNext1 = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call1: "+super.toString(); } }, "hasNext" );
            Call hasNext2 = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call2: "+super.toString(); } }, "hasNext" );
            Call next1    = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call3: "+super.toString(); } }, "next");
            Call next2    = new Call(f.pos, new Call(f.pos, streamName) { public String toString() { return "call4: "+super.toString(); } }, "next");
            List<Stmnt> prolog2 = new List<>();
            List<Stmnt> nextIt2 = new List<>();
            If ifHasNext1 = new If(f.pos, hasNext1, new Block(f.pos, prolog2));
            If ifHasNext2 = new If(f.pos, hasNext2, new Block(f.pos, nextIt2));
            ifHasNext1.setElse(new Block(f.pos, new List<Stmnt>(new IntConst(0))));
            ifHasNext2.setElse(new Block(f.pos, new List<Stmnt>(new IntConst(0))));
            prolog.add(ifHasNext1);
            nextIt.add(ifHasNext2);
            prolog = prolog2;
            nextIt = nextIt2;
            f.impl = new Impl(f.impl.pos, next1, Impl.Kind.FieldDef);
            n = next2;
          }
        prolog.add(f);
        f._isIndexVarUpdatedByLoop = true;
        nextIt.add(new Assign(f.pos, f, n, true));
      }
    if (!_iteratorVars.isEmpty())
      {  /* no user-visible result in case index variable is iterator, since
          * iterated data can be empty, so we would not reach end of
          * prolog */
        prolog.add(new IntConst(1));
        nextIt.add(new IntConst(1));
      }
    else if ((_successBlock == null || _elseBlock == null) /* if until block and else block is present, result comes from them */
             && !_indexVars.isEmpty() /* if there is no index variable, there is no result */
             )
      {
        Feature res = _indexVars.getLast();
        prolog.add(new Call(res.pos, res._featureName.baseName()));
        nextIt.add(new Call(res.pos, res._featureName.baseName()));
      }
  }


  /**
   * Is any of the _indexVars an iteration (x in data)?
   */
  public final boolean iterates()
  {
    return !_iteratorVars.isEmpty();
  }


  /**
   * Check if ix is an index variable that might be undefined in this loop's
   * else block. This is true for index variables that either are iterator
   * variables or that are declared after the first iterator variable.
   *
   * This is used during call resolution to determine if ix is visible in an
   * else block.
   *
   * @param ix any Feature
   *
   * @param true iff ix is an iterator variable or an index variable declared
   * after an iterator variable in this loop.
   */
  boolean mightNotBeSetInElse(Feature ix)
  {
    boolean result = false;
    for (var iv : _iteratorVars)
      {
        result |= ix == iv;
      }
    return result;
  }


  /**
   * Helper routine for typeOrNull to determine the type of this loop on demand,
   * i.e., as late as possible.
   */
  private Type typeFromSuccessOrFailure()
  {
    Type tProlog    = _prolog        != null ? _prolog       .type() : Types.t_VOID;
    Type tNextIt    = _nextIteration != null ? _nextIteration.type() : Types.t_VOID;
    check
      (tProlog == tNextIt);
    Type tIndexVars = iterates() ? Types.t_VOID : tProlog;
    Type tBlock     = _block         != null ? _block        .type() : tIndexVars;
    Type tfailure   = _elseBlock     != null ? _elseBlock    .type() : tIndexVars;
    Type tsuccess   = _successBlock  != null ? _successBlock .type() : tBlock;

    return
      (_untilCond == null) // no until condition means loop exit is possible via failure only
      ? tfailure
      : (!iterates() && _whileCond == null) // no iterators and no while condition means loop exit is possible via success only
      ? tsuccess
      : tsuccess.union(tfailure);
  }


  /**
   * typeOrNull returns the type of this expression or Null if the type is still
   * unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  public Type typeOrNull()
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    if (_type == null)
      {
        _type = typeFromSuccessOrFailure();
      }
    if (_type == Types.t_UNDEFINED)
      {
        List<Expr> branches = new List<>();
        List<String> names = new List<>();
        Block i = _prolog;
        Block u = _successBlock;
        Block b = _block;
        Expr e = _elseBlock;
        if (i != null && (u == null || e == null))
          {
            branches.add(i);
            names.add("index");
          }
        if (b != null && _untilCond != null && u == null)
          {
            branches.add(b);
            names.add("loop body");
          }
        if (u != null)
          {
            branches.add(u);
            names.add("until");
          }
        if (e != null)
          {
            branches.add(e);
            names.add("else");
          }
        check // if u and e are both null, _type should be VOID, so we should not be here
          (u != null || e != null);
        new IncompatibleResultsOnBranches
          (pos,
           branches.size() == 2 ? "Incompatible types in loop's " + names.get(0) + " and " + names.get(1) + " blocks" :
           "Loop defines only " + names.get(0) + " block, but it requires either both, an until and an else block, or an index block",
           branches.iterator());
        return Types.t_ERROR;
      }
    return _type; // == null ? Types.t_VOID : _type; // NYI: should be just _type
  }


  /**
   * Convert this Expression into an assignment to the given field.  In case
   * this is a statment with several branches such as an "if" or a "match"
   * statement, add corresponding assignments in each branch and convert this
   * into a statement that does not produce a value.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param r the field this should be assigned to.
   *
   * @return the Stmnt this Expr is to be replaced with, typically an Assign
   * that performs the assignment to r.
   */
  Loop assignToField(Resolution res, Feature outer, Feature r)
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    if (_successBlock != null)
      {
        _successBlock = _successBlock.assignToField(res, outer, r);
      }
    if (_elseBlock instanceof If)
      {
        _elseBlock = ((If) _elseBlock).assignToField(res, outer, r);
      }
    else if (_elseBlock instanceof Block)
      {
        _elseBlock = ((Block) _elseBlock).assignToField(res, outer, r);
      }
    else if (_elseBlock != null)
      {
        throw new Error("Loop._elseBlock must be If or Block");
      }
    if (_successBlock == null && _untilCond != null && _block != null)
      {
        _block.assignToField(res, outer, r);
      }
    if (_successBlock == null && _untilCond != null && _block == null ||
        _elseBlock == null && (_whileCond != null || iterates()))
      {
        check
          ((_prolog != null) == (_nextIteration != null));
        if (_prolog != null)
          {
            _prolog        = _prolog.assignToField       (res, outer, r);
            _nextIteration = _nextIteration.assignToField(res, outer, r);
          }
      }
    return this;
  }


  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   */
  public void propagateExpectedType(Resolution res, Feature outer)
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    if (_whileCond != null)
      {
        _whileCond = _whileCond.propagateExpectedType(res, outer, Types.resolved.t_bool);
      }
    if (_untilCond != null)
      {
        _untilCond = _untilCond.propagateExpectedType(res, outer, Types.resolved.t_bool);
      }
    if (_var != null)
      {
        _var = _var.propagateExpectedType(res, outer, Types.resolved.t_i64);
      }
  }



  /**
   * During type inference: Inform this expression that it is used in an
   * environment that expects the given type.  In particular, if this
   * expression's result is assigned to a field, this will be called with the
   * type of the field.
   *
   * @param res this is called during type inference, res gives the resolution
   * instance.
   *
   * @param outer the feature that contains this expression
   *
   * @param t the expected type.
   *
   * @return either this or a new Expr that replaces thiz and produces the
   * result. In particular, if the result is assigned to a temporary field, this
   * will be replaced by the statement that reads the field.
   */
  public Expr propagateExpectedType(Resolution res, Feature outer, Type t)
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    // A loop with VOID result assigned to an expected bool type will be changed
    // to return true on success, false otherwise:
    Type tsf = typeFromSuccessOrFailure();
    if (t == Types.resolved.t_bool &&
        (tsf == Types.t_UNDEFINED || tsf == Types.t_VOID || tsf == Types.resolved.t_void))
      { // the expected type is bool and the actual type is void or undefined,
        // and the loop may succeed (through _untilCond) and fail (iteration end
        // or _whileCond), so we add true/false as the result in _successBlock /
        // _elseBlock:
        if (_successBlock == null)
          {
            _successBlock = new Block(pos, new List<Stmnt>());
          }
        if (!(_elseBlock instanceof Block))
          {
            _elseBlock = new Block(pos, _elseBlock == null ? new List<Stmnt>()
                                                           : new List<Stmnt>(_elseBlock));
          }
        Call T = new Call(pos, new Singleton(pos, Types.resolved.universe), Types.resolved.f_TRUE ); T.resolveTypes(res, outer);
        Call F = new Call(pos, new Singleton(pos, Types.resolved.universe), Types.resolved.f_FALSE); F.resolveTypes(res, outer);
        _successBlock      .statements_.add(T);
        ((Block)_elseBlock).statements_.add(F);
      }
    return addFieldForResult(res, outer, t);
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Expr visit(FeatureVisitor v, Feature outer)
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    Expr result = this;
    if (v.actionBefore(this, outer))
      {
        if (_prolog != null)
          {
            _prolog = _prolog.visit(v, outer);
          }
        if (_var != null)
          {
            _var = _var.visit(v, outer);
          }
        if (_inv != null)
          {
            for(Cond c: _inv)
              {
                c.visit(v, outer);
              }
          }
        if (_whileCond != null)
          {
            _whileCond = _whileCond.visit(v, outer);
          }
        if (_block != null)
          {
            _block = _block.visit(v, outer);
          }
        if (_untilCond != null)
          {
            _untilCond = _untilCond.visit(v, outer);
          }
        if (_nextIteration != null)
          {
            _nextIteration = _nextIteration.visit(v, outer);
          }
        if (_successBlock != null)
          {
            _successBlock = _successBlock.visit(v, outer);
          }
        if (_elseBlock != null)
          {
            _elseBlock = _elseBlock.visit(v, outer);
          }
        result = v.action(this, outer);
      }
    return result;
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    return false;
  };


  /**
   * check the types in this loop, in particular check that while- and
   * until-conditions are of type bool.
   *
   * @param outer the root feature that contains this statement.
   */
  public void checkTypes()
  { // NYI: remove: when loop has been replaced by tail recursion, this is no longer needed
    if (_whileCond != null)
      {
        Type t = _whileCond.type();
        if (!Types.resolved.t_bool.isAssignableFrom(t))
          {
            FeErrors.whileConditionMustBeBool(_whileCond.pos, t);
          }
      }
    if (_untilCond != null)
      {
        Type t = _untilCond.type();
        if (!Types.resolved.t_bool.isAssignableFrom(t))
          {
            FeErrors.untilConditionMustBeBool(_untilCond.pos, t);
          }
      }
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return
      (_prolog != null ? "for " + _prolog + "\n" : "") +
      (_nextIteration != null ? "next " + _nextIteration + "\n" : "") +
      (_inv==null ? "" : "invariant "+_inv+"\n")+
      (_var==null ? "" : "variant "  +_var+"\n")+
      (_whileCond != null ? "while " + _whileCond + "\n" : "do ") +
      (_block != null ? _block : "") +
      (_untilCond != null ? "until " + _untilCond + "\n" : "") +
      (_successBlock != null ? "on success" + _successBlock : "" ) +
      (_elseBlock != null ? "else" + _elseBlock : "" );
  }

}

/* end of file */
