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
 * Source of class AbstractInterpreter
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir.analysis;

import java.util.Stack;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.Pair;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;


/**
 * AbstractInterpreter provides a skeleton of an abstract interpreter
 * executing on the FUIR representation.  This skeleton can be used to
 * implement analysis or code generation tools.
 *
 * This class has two generic type parameters that specify the types of values
 * used by the actual interpreter:
 *
 *  - VALUE represents an abstract result value created by an expression.
 *    for a compiler, this might be the code required to obtain that value.
 *
 *  - RESULT represents the result of the abstract interpretation. For a
 *    compiler, this would, e.g, be the generated code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class AbstractInterpreter<VALUE, RESULT> extends ANY
{


  /*----------------------------  interfaces  ---------------------------*/


  /**
   * Interface that defines the operations of the actual interpreter
   * that processes this code.
   */
  public static abstract class ProcessStatement<VALUE, RESULT> extends ANY implements HasSourcePosition
  {

    /**
     * FUIR we are currently interpreting
     */
    FUIR _fuir;


    /**
     * id of clazz we are interpreting, will be set by AbstractInterpreter before
     * performing an Expression.
     */
    int _cl = -1;


    /**
     * true iff interpreting _cl's precondition, false for _cl itself, will be
     * set by AbstractInterpreter before performing an Expression.
     */
    boolean _pre = false;


    /**
     * current code block, will be set by AbstractInterpreter before performing
     * an Expression.
     */
    int _c;


    /**
     * index of match in current code block, will be set by AbstractInterpreter
     * before performing an Expression.
     */
    int _i;


    /**
     * Create and return the actual source code position held by this instance.
     */
    public SourcePosition pos()
    {
      return _fuir.codeAtAsPos(_c, _i);
    }

    /**
     * Join a List of RESULT from subsequent statements into a compound
     * statement.  For a code generator, this could, e.g., join statements "a :=
     * 3;" and "b(x);" into a block "{ a := 3; b(x); }".
     */
    public abstract RESULT sequence(List<RESULT> l);

    /*
     * Produce the unit type value.  This is used as a placeholder
     * for the universe instance as well as for the instance 'unit'.
     */
    public abstract VALUE unitValue();

    /**
     * Called before each statement is processed. May be used to, e.g., produce
     * tracing code for debugging or a comment.
     */
    public abstract RESULT statementHeader(int cl, int c, int i);

    /**
     * A comment, adds human readable information
     */
    public abstract RESULT comment(String s);

    /**
     * no operation, like comment, but without giving any comment.
     */
    public abstract RESULT nop();

    /**
     * drop a value, but process its side-effect.
     *
     * @param v an expression that calculates a value that is not needed, but
     * where the calculation might have side-effects (like performing a call) that
     * we do need.
     *
     * For backends that do not perform any side-effects in RESULT, this does
     * not need to be redefined, the default implementation is nop() which is
     * fine in this case.
     *
     * @param type clazz id for the type of the value
     *
     * @return code to perform the side effects of v and ignoring the produced value.
     */
    public RESULT drop(VALUE v, int type)
    {
      return nop(); // NYI, should be implemented by BEs.
    }

    /**
     * Determine the address of a given value.  This is used on a call to an
     * inner feature to pass a reference to the outer value type instance.
     */
    public abstract Pair<VALUE, RESULT> adrOf(VALUE v);

    /**
     * Perform an assignment val to field f in instance rt
     *
     * @param cl id of clazz we are interpreting
     *
     * @param pre true iff interpreting cl's precondition, false for cl itself.
     *
     * @param tc clazz id of the target instance
     *
     * @param f clazz id of the assigned field
     *
     * @param rt clazz is of the field type
     *
     * @param tvalue the target instance
     *
     * @param val the new value to be assigned to the field.
     *
     * @return resulting code of this assignment.
     */
    public abstract RESULT assignStatic(int cl, boolean pre, int tc, int f, int rt, VALUE tvalue, VALUE val);

    /**
     * Perform an assignment of a value to a field in tvalue. The type of tvalue
     * might be dynamic (a reference). See FUIR.access*().
     *
     * @param cl id of clazz we are interpreting
     *
     * @param pre true iff interpreting cl's precondition, false for cl itself.
     *
     * @param c current code block
     *
     * @param i index of call in current code block
     *
     * @param tvalue the target instance
     *
     * @param avalue the new value to be assigned to the field.
     */
    public abstract RESULT              assign(int cl, boolean pre, int c, int i, VALUE tvalue, VALUE avalue);

    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.  The type of tvalue might be dynamic (a reference). See
     * FUIR.access*().
     *
     * Result.v0() may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     *
     * @param cl id of clazz we are interpreting
     *
     * @param pre true iff interpreting cl's precondition, false for cl itself.
     *
     * @param c current code block
     *
     * @param i index of call in current code block
     *
     * @param tvalue target value the call is performed on
     *
     * @param args argument values passed to the call
     */
    public abstract Pair<VALUE, RESULT> call  (int cl, boolean pre, int c, int i, VALUE tvalue, List<VALUE> args);

    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    public abstract Pair<VALUE, RESULT> box(VALUE v, int vc, int rc);

    /**
     * Get the current instance
     *
     * @param cl id of clazz we are interpreting
     *
     * @param pre true iff interpreting cl's precondition, false for cl itself.
     */
    public abstract Pair<VALUE, RESULT> current(int cl, boolean pre);

    /**
     * Get the outer instance
     *
     * @param cl id of clazz we are interpreting
     */
    public abstract Pair<VALUE, RESULT> outer(int cl);

    /**
     * Get the argument #i
     *
     * @param cl id of clazz we are interpreting
     *
     * @param i index of value argument of cl
     */
    public abstract VALUE arg(int cl, int i);

    /**
     * Get a constant value of type constCl with given byte data d.
     */
    public abstract Pair<VALUE, RESULT> constData(int constCl, byte[] d);

    /**
     * Perform a match on value subv.
     *
     * @param ai current abstract interpreter instance
     *
     * @param cl id of clazz we are interpreting
     *
     * @param pre true iff interpreting cl's precondition, false for cl itself.
     *
     * @param c current code block
     *
     * @param i index of match in current code block
     *
     * @param subv value of subject of this match that is being tested.
     */
    public abstract Pair<VALUE, RESULT> match(AbstractInterpreter<VALUE, RESULT> ai, int cl, boolean pre, int c, int i, VALUE subv);

    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    public abstract Pair<VALUE, RESULT> tag(int cl, VALUE value, int newcl, int tagNum);

    /**
     * Access the effect of type ecl that is installed in the environment.
     */
    public abstract Pair<VALUE, RESULT> env(int ecl);

    /**
     * Process a contract of kind ck of clazz cl that results in bool value cc
     * (i.e., the contract fails if !cc).
     */
    public abstract RESULT contract(int cl, FUIR.ContractKind ck, VALUE cc);

  }


  /*-----------------------------  classes  -----------------------------*/


  /*----------------------------  constants  ----------------------------*/


  /**
   * property-controlled flag to enable debug output.
   *
   * To enable debugging, use fz with
   *
   *   FUZION_JAVA_OPTIONS=-Ddev.flang.fuir.analysis.AbstractInterpreter.DEBUG=true
   *
   * or
   *
   *   FUZION_JAVA_OPTIONS=-Ddev.flang.fuir.analysis.AbstractInterpreter.DEBUG=".*install_default"
   *
   */
  static final String DEBUG;
  static {
    var debug = System.getProperty("dev.flang.fuir.analysis.AbstractInterpreter.DEBUG");
    DEBUG =
      debug == null ||
      debug.equals("false") ? null :
      debug.equals("true" ) ? ".*"
                            : debug;
  }

  /*-------------------------  static methods  --------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are working on.
   */
  public final FUIR _fuir;


  /**
   * The processor provided to the constructor.
   */
  public final ProcessStatement<VALUE, RESULT> _processor;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create AbstractInterpreter for given intermediate code.
   *
   * @param fuir the intermediate code.
   */
  public AbstractInterpreter(FUIR fuir, ProcessStatement<VALUE, RESULT> processor)
  {
    if (PRECONDITIONS) require
      (fuir != null,
       processor != null);

    _fuir = fuir;
    _processor = processor;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check if the given clazz has a unique value that does not need to be pushed
   * onto the stack.
   */
  public static boolean clazzHasUniqueValue(FUIR fuir, int cl)
  {
    return cl == fuir.clazzUniverse() || fuir.clazzIsUnitType(cl) && !fuir.clazzIsRef(cl);
    // NYI: maybe we should restrict this to c_unit only?
    // return cl == _fuir.clazzUniverse() || FUIR.SpecialClazzes.c_unit != _fuir.getSpecialId(cl);
  }


  /**
   * Check if the given clazz has a unique value that does not need to be pushed
   * onto the stack.
   */
  public boolean clazzHasUniqueValue(int cl)
  {
    return clazzHasUniqueValue(_fuir, cl);
  }


  /**
   * Push the given value to the stack unless it is of unit or void type or the
   * clazz is -1
   *
   * @param stack the stack to push val to
   *
   * @param cl the clazz of val, may be -1
   *
   * @param val the value to push
   */
  void push(Stack<VALUE> stack, int cl, VALUE val)
  {
    if (PRECONDITIONS) require
      (!_fuir.clazzIsVoidType(cl) || (val == null),
       !containsVoid(stack));

    if (!clazzHasUniqueValue(cl))
      {
        stack.push(val);
      }

    if (POSTCONDITIONS) ensure
      (clazzHasUniqueValue(cl) || stack.get(stack.size()-1) == val,
       !_fuir.clazzIsVoidType(cl) || containsVoid(stack));
  }


  /**
   * Pop value from the stack unless it is of unit or void type or the
   * clazz is -1
   *
   * @param stack the stack to pop value from
   *
   * @param cl the clazz of value, may be -1
   *
   * @return the popped value or null if cl is -1 or unit type
   */
  VALUE pop(Stack<VALUE> stack, int cl)
  {
    if (PRECONDITIONS) require
      (clazzHasUniqueValue(cl) || stack.size() > 0,
       !containsVoid(stack));

    return
      clazzHasUniqueValue(cl) ? _processor.unitValue() : stack.pop();
  }


  /**
   * Check if the given stack contains a void value.  If so, code generation has
   * to stop immediately.
   */
  boolean containsVoid(Stack<VALUE> stack)
  {
    return stack.size() > 0 && stack.get(stack.size()-1) == null;
  }


  /**
   * Create code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.
   *
   * @param cc clazz that is called
   *
   * @param stack the stack containing the code of the args.
   *
   * @param argCount the number of arguments.
   *
   * @return list of arguments to be passed to the call
   */
  List<VALUE> args(int cc, Stack<VALUE> stack, int argCount)
  {
    if (argCount > 0)
      {
        var ac = _fuir.clazzArgClazz(cc, argCount-1);
        var a = pop(stack, ac);
        var result = args(cc, stack, argCount-1);
        result.add(a);
        return result;
      }
    return new List<>();
  }


  /**
   * As part of the prolog of a clazz' code, perform the initialization of the
   * clazz' outer ref and argument fields with the target and the actual
   * arguments.
   *
   * @param l list that will receive the result
   *
   * @param cl the clazz we are interpreting.
   *
   * @param pre true iff interpreting cl's precondition, false for cl itself.
   */
  void assignOuterAndArgFields(List<RESULT> l, int cl, boolean pre)
  {
    var or = _fuir.clazzOuterRef(cl);
    if (or != -1)
      {
        var rt = _fuir.clazzResultClazz(or);
        var cur = _processor.current(cl, pre);
        l.add(cur.v1());
        var out = _processor.outer(cl);
        l.add(out.v1());
        l.add(_processor.assignStatic(cl, pre, cl, or, rt, cur.v0(), out.v0()));
      }

    var ac = _fuir.clazzArgCount(cl);
    for (int i = 0; i < ac; i++)
      {
        var cur = _processor.current(cl, pre);
        l.add(cur.v1());
        var af = _fuir.clazzArg(cl, i);
        var at = _fuir.clazzArgClazz(cl, i);
        var ai = _processor.arg(cl, i);
        if (ai != null)
          {
            l.add(_processor.assignStatic(cl, pre, cl, af, at, cur.v0(), ai));
          }
      }
  }


  /**
   * Perform abstract interpretation on given clazz
   *
   * @param cl clazz id
   *
   * @param pre true to process cl's precondition, false to process cl's code
   * followed by its postcondition.
   *
   * @return A Pair consisting of a VALUE that is either
   * _processor().unitValue() or null (in case cl diverges) and the result of
   * the abstract interpretation, e.g., the generated code.
   */
  public Pair<VALUE,RESULT> process(int cl, boolean pre)
  {
    var l = new List<RESULT>();
    assignOuterAndArgFields(l, cl, pre);
    var p = pre
      ? processContract(cl, FUIR.ContractKind.Pre)
      : process(cl, pre, _fuir.clazzCode(cl));
    l.add(p.v1());
    var res = p.v0();
    if (!pre)
      {
        var post = processContract(cl, FUIR.ContractKind.Post);
        l.add(post.v1());
        if (post.v0() == null) // post condition results in void, so we do not return:
          {
            res = null;
          }
      }
    return new Pair<>(res, _processor.sequence(l));
  }


  /**
   * Perform abstract interpretation on given code
   *
   * @param cl clazz id
   *
   * @param pre true to process cl's precondition, false to process cl's code.
   *
   * @param c the code block to interpret
   *
   * @return A Pair consisting of a VALUE that is either
   * _processor().unitValue() or null (in case cl diverges) and the result of
   * the abstract interpretation, e.g., the generated code.
   */
  public Pair<VALUE,RESULT> process(int cl, boolean pre, int c)
  {
    var stack = new Stack<VALUE>();
    var l = new List<RESULT>();
    int last_i = -1;
    for (int i = 0; !containsVoid(stack) && _fuir.withinCode(c, i) && !_fuir.alwaysResultsInVoid(cl, c, last_i); i = i + _fuir.codeSizeAt(c, i))
      {
        l.add(_processor.statementHeader(cl, c, i));
        l.add(process(cl, pre, stack, c, i));
        last_i = i;
      }

    var v = containsVoid(stack) ? null
                                : _processor.unitValue();

    if (!containsVoid(stack) && stack.size() > 0)
      { // NYI: #1875: Manual stack cleanup.  This should not be needed since the
        // FUIR has the (so far undocumented) invariant that the stack must be
        // empty at the end of a basic block. There were some cases
        // (tests/reg_issue1294) where this is not the case that need to be
        // fixed, the FUIR code should contain a POP instructions to avoid this
        // special handling here!
        //
        var s = _fuir.codeAt(c, last_i);
        switch (s)
          {
          case Call:
            var cc0 = _fuir.accessedClazz  (cl, c, last_i);
            var rt = _fuir.clazzResultClazz(cc0);
            l.add(_processor.drop(stack.pop(), rt));
            break;
          default:
            break; // NYI: ignore this case for now, this occurs infrequently, one example is tests/reg_issue1294.
          }
      }

    return new Pair<>(v, _processor.sequence(l));
  }


  /**
   * Perform abstract interpretation on given code
   *
   * @param cl clazz id
   *
   * @param ck the contracts kind
   *
   * @return the result of the abstract interpretation, e.g., the generated
   * code.
   */
  Pair<VALUE,RESULT> processContract(int cl, FUIR.ContractKind ck)
  {
    var l = new List<RESULT>();
    for (var ci = 0;
         _fuir.clazzContract(cl, ck, ci) != -1;
         ci++)
      {
        var c = _fuir.clazzContract(cl, ck, ci);
        var stack = new Stack<VALUE>();
        for (int i = 0; !containsVoid(stack) && _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
          {
            l.add(_processor.statementHeader(cl, c, i));
            l.add(process(cl, ck == FUIR.ContractKind.Pre, stack, c, i));
          }
        if (!containsVoid(stack))
          {
            l.add(_processor.contract(cl, ck, stack.pop()));
          }
      }
    return new Pair<>(_processor.unitValue(), _processor.sequence(l));
  }


  /**
   * Perform abstract interpretation on given statement
   *
   * @param cl clazz id
   *
   * @param pre true to process cl's precondition, false to process cl's code
   * followed by its postcondition.
   *
   * @param stack the stack containing the current arguments waiting to be used
   *
   * @param c the code block to compile
   *
   * @param i the index within c
   *
   * @return the result of the abstract interpretation, e.g., the generated
   * code.
   */
  public RESULT process(int cl, boolean pre, Stack<VALUE> stack, int c, int i)
  {
    if (DEBUG != null && _fuir.clazzAsString(cl).matches(DEBUG))
      {
        say("process "+_fuir.clazzAsString(cl)+"."+c+"."+i+":\t"+_fuir.codeAtAsString(cl, c, i)+" stack is "+stack);
      }
    var s = _fuir.codeAt(c, i);

    _processor._fuir = _fuir;
    _processor._cl = cl;
    _processor._pre = pre;
    _processor._c = c;
    _processor._i = i;
    switch (s)
      {
      case AdrOf:
        {
          var v = stack.pop();
          var r = _processor.adrOf(v);
          stack.push(r.v0());
          return r.v1();
        }
      case Assign:
        {
          // NYI: pop the stack values even in case field is unused.
          var ft = _fuir.assignedType(cl, c, i);
          var tc = _fuir.accessTargetClazz(cl, c, i);
          var tvalue = pop(stack, tc);
          var avalue = pop(stack, ft);
          var f = _fuir.accessedClazz  (cl, c, i);
          if (f != -1)  // field we are assigning to may be unused, i.e., -1
            {
              return _processor.assign(cl, pre, c, i, tvalue, avalue);
            }
          else
            {
              return _processor.sequence(new List<>(_processor.drop(tvalue, tc),
                                                    _processor.drop(avalue, ft)));
            }
        }
      case Box:
        {
          var vc = _fuir.boxValueClazz(cl, c, i);
          var rc = _fuir.boxResultClazz(cl, c, i);
          if (_fuir.clazzIsRef(vc) || !_fuir.clazzIsRef(rc))
            { // vc's type is a generic argument whose actual type does not need
              // boxing
              return _processor.comment("Box is a NOP, clazz is already a ref");
            }
          else
            {
              var val = pop(stack, vc);
              var r = _processor.box(val, vc, rc);
              push(stack, rc, r.v0());
              return r.v1();
            }
        }
      case Call:
        {
          var cc0 = _fuir.accessedClazz  (cl, c, i);
          var args = args(cc0, stack, _fuir.clazzArgCount(cc0));
          var tc = _fuir.accessTargetClazz(cl, c, i);
          var tvalue = pop(stack, tc);
          var r = _processor.call(cl, pre, c, i, tvalue, args);
          if (r.v0() == null)  // this may happen even if rt is not void (e.g., in case of tail recursion or error)
            {
              stack.push(null);
            }
          else
            {
              var rt = _fuir.clazzResultClazz(cc0);
              push(stack, rt, r.v0());
            }
          return r.v1();
        }
      case Comment:
        {
          return _processor.comment(_fuir.comment(cl, c, i));
        }
      case Current:
        {
          var r = _processor.current(cl, pre);
          push(stack, cl, r.v0());
          return r.v1();
        }
      case Const:
        {
          var constCl = _fuir.constClazz(cl, c, i);
          var d = _fuir.constData(c, i);
          var r = _processor.constData(constCl, d);

          if (CHECKS) check
            // check that constant creation has no side effects.
            (r.v1() == _processor.nop());

          push(stack, constCl, r.v0());
          return r.v1();
        }
      case Match:
        {
          var subjClazz = _fuir.matchStaticSubject(cl, c, i);
          var subv      = pop(stack, subjClazz);
          RESULT code = _processor.nop();

          var r = _processor.match(this, cl, pre, c, i, subv);
          if (r.v0() == null)
            {
              stack.push(null);
            }
          if (CHECKS) check
            (r.v0() == null || r.v0() == _processor.unitValue());
          return _processor.sequence(new List<>(code, r.v1()));
        }
      case Tag:
        {
          var valuecl = _fuir.tagValueClazz(cl, c, i);  // static clazz of value
          var value   = pop(stack, valuecl);            // value that will be tagged
          var newcl   = _fuir.tagNewClazz  (cl, c, i);  // static clazz of result
          if (CHECKS) check
            (!_fuir.clazzIsVoidType(valuecl));
          int tagNum  = _fuir.clazzChoiceTag(newcl, valuecl);
          var r = _processor.tag(cl, value, newcl, tagNum);
          push(stack, newcl, r.v0());
          return r.v1();
        }
      case Env:
        {
          var ecl = _fuir.envClazz(cl, c, i);
          var r = _processor.env(ecl);
          push(stack, ecl, r.v0());
          return r.v1();
        }
      case Pop:
        {
          // Pop can only follow a Call, we need the call to determine the type
          // of the popped value, which might be a unit type value.
          //
          if (CHECKS) check
            (_fuir.codeAt(c, i-1) == FUIR.ExprKind.Call);

          var cc = _fuir.accessedClazz(cl, c, i-1);
          var rt = _fuir.clazzResultClazz(cc);
          var v = pop(stack, rt);
          return _processor.drop(v, rt);
        }
      default:
        {
          Errors.fatal("AbstractInterpreter backend does not handle statements of type " + s);
          return null;
        }
      }
  }


}

/* end-of-file */
