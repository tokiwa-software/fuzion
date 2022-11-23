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
  public static abstract class ProcessStatement<VALUE, RESULT>
  {
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
     * Determine the address of a given value.  This is used on a call to an
     * inner feature to pass a reference to the outer value type instance.
     */
    public abstract Pair<VALUE, RESULT> adrOf(VALUE v);

    /**
     * Perform an assignment val to field f in instance rt
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
    public abstract RESULT assignStatic(int tc, int f, int rt, VALUE tvalue, VALUE val);

    /**
     * Perform an assignment of a value to a field in tvalue. The type of tvalue
     * might be dynamic (a reference). See FUIR.acess*().
     */
    public abstract RESULT              assign(int cl, int c, int i, VALUE tvalue, VALUE avalue);

    /**
     * Perform a call of a feature with target instance tvalue with given
     * arguments.. The type of tvalue might be dynamic (a refernce). See
     * FUIR.acess*().
     *
     * Result._v0 may be null to indicate that code generation should stop here
     * (due to an error or tail recursion optimization).
     */
    public abstract Pair<VALUE, RESULT> call  (int cl, int c, int i, VALUE tvalue, List<VALUE> args);

    /**
     * For a given value v of value type vc create a boxed ref value of type rc.
     */
    public abstract Pair<VALUE, RESULT> box(VALUE v, int vc, int rc);

    /**
     * For a given reference value v create an unboxed value of type vc.
     */
    public abstract Pair<VALUE, RESULT> unbox(VALUE v, int vc);

    /**
     * Get the current instance
     */
    public abstract Pair<VALUE, RESULT> current(int cl);

    /**
     * Get the outer instance
     */
    public abstract Pair<VALUE, RESULT> outer(int cl);

    /**
     * Get the argument #i
     */
    public abstract VALUE arg(int cl, int i);

    /**
     * Get a constant value of type constCl with given byte data d.
     */
    public abstract Pair<VALUE, RESULT> constData(int constCl, byte[] d);

    /**
     * Perform a match on value subv.
     */
    public abstract Pair<VALUE, RESULT> match(AbstractInterpreter<VALUE, RESULT> ai, int cl, int c, int i, VALUE subv);

    /**
     * Create a tagged value of type newcl from an untagged value for type valuecl.
     */
    public abstract Pair<VALUE, RESULT> tag(int cl, int valuecl, VALUE value, int newcl, int tagNum);

    /**
     * Access the effect of type ecl that is installed in the environemnt.
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
   */
  static final boolean DEBUG =
    System.getProperty("dev.flang.fuir.analysis.AbstractInterpreter.DEBUG",
                       "false").equals("true");


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


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create AbstractInterpreter for given intermediate code.
   *
   * @param fuir the intermediate code.
   */
  public AbstractInterpreter(FUIR fuir, ProcessStatement processor)
  {
    if (PRECONDITIONS) require
      (fuir != null,
       processor != null);

    _fuir = fuir;
    _processor = processor;
    Errors.showAndExit();
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
   * @param the value to push
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
   * Create C code to pass given number of arguments plus one implicit target
   * argument from the stack to a called feature.
   *
   * @param cc clazz that is called
   *
   * @param stack the stack containing the C code of the args.
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
   * calzz' outer ref and argument fields with the target and the actual
   * arguments.
   *
   * @param l list that will receive the result
   *
   * @param cl the clazz we are interpreting.
   */
  void assignOuterAndArgFields(List<RESULT> l, int cl)
  {
    var or = _fuir.clazzOuterRef(cl);
    if (or != -1)
      {
        var rt = _fuir.clazzResultClazz(or);
        var cur = _processor.current(cl);
        l.add(cur._v1);
        var out = _processor.outer(cl);
        l.add(out._v1);
        l.add(_processor.assignStatic(cl, or, rt, cur._v0, out._v0));
      }

    var vcl = _fuir.clazzAsValue(cl);
    var ac = _fuir.clazzArgCount(vcl);
    for (int i = 0; i < ac; i++)
      {
        var cur = _processor.current(cl);
        l.add(cur._v1);
        var af = _fuir.clazzArg(vcl, i);
        var at = _fuir.clazzArgClazz(vcl, i);
        var ai = _processor.arg(cl, i);
        if (ai != null)
          {
            l.add(_processor.assignStatic(cl, af, at, cur._v0, ai));
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
    assignOuterAndArgFields(l, cl);
    var p = pre
      ? processContract(cl, FUIR.ContractKind.Pre)
      : process(cl, _fuir.clazzCode(cl));
    l.add(p._v1);
    var res = p._v0;
    if (!pre)
      {
        var post = processContract(cl, FUIR.ContractKind.Post);
        l.add(post._v1);
        if (post._v0 == null) // post condition results in void, so we do not return:
          {
            res = null;
          }
      }
    return new Pair(res, _processor.sequence(l));
  }


  /**
   * Perform abstract interpretation on given code
   *
   * @param cl clazz id
   *
   * @param c the code block to interpret
   *
   * @return A Pair consisting of a VALUE that is either
   * _processor().unitValue() or null (in case cl diverges) and the result of
   * the abstract interpretation, e.g., the generated code.
   */
  public Pair<VALUE,RESULT> process(int cl, int c)
  {
    var stack = new Stack<VALUE>();
    var l = new List<RESULT>();
    for (int i = 0; !containsVoid(stack) && _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
      {
        l.add(_processor.statementHeader(cl, c, i));
        l.add(process(cl, stack, c, i));
      }
    var v = containsVoid(stack) ? null : _processor.unitValue();
    return new Pair<>(v, _processor.sequence(l));
  }


  /**
   * Perform abstract interpretation on given code
   *
   * @param cl clazz id
   *
   * @param c the code block to interpret
   *
   * @return the result of the abstract interpretation, e.g., the generated
   * code.
   */
  Pair<VALUE,RESULT> processContract(int cl, FUIR.ContractKind ck)
  {
    var l = new List<RESULT>();
    if (ck == FUIR.ContractKind.Pre)
      {
        assignOuterAndArgFields(l, cl);
      }
    for (var ci = 0;
         _fuir.clazzContract(cl, ck, ci) != -1;
         ci++)
      {
        var c = _fuir.clazzContract(cl, ck, ci);
        var stack = new Stack<VALUE>();
        for (int i = 0; !containsVoid(stack) && _fuir.withinCode(c, i); i = i + _fuir.codeSizeAt(c, i))
          {
            l.add(_processor.statementHeader(cl, c, i));
            l.add(process(cl, stack, c, i));
          }
        if (!containsVoid(stack))
          {
            l.add(_processor.contract(cl, ck, stack.pop()));
          }
      }
    return new Pair<>(_processor.unitValue(), _processor.sequence(l));
  }


  /**
   * Perform absstract interpretation on given statement
   *
   * @param cl clazz id
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
  public RESULT process(int cl, Stack<VALUE> stack, int c, int i)
  {
    if (DEBUG)
      {
        System.out.println("process "+_fuir.clazzAsString(cl)+"."+c+"."+i+":\t"+_fuir.codeAtAsString(cl, c, i)+" stack is "+stack);
      }
    var s = _fuir.codeAt(c, i);
    switch (s)
      {
      case AdrOf:
        {
          var v = stack.pop();
          var r = _processor.adrOf(v);
          stack.push(r._v0);
          return r._v1;
        }
      case Assign:
        {
          // NYI: pop the stack values even in case field is unused.
          if (_fuir.accessedClazz(cl, c, i) != -1)  // field we are assigning to may be unused, i.e., -1
            {
              var f = _fuir.accessedClazz  (cl, c, i);
              var ft = _fuir.clazzResultClazz(f);
              var tc = _fuir.accessTargetClazz(cl, c, i);
              var tvalue = pop(stack, tc);
              var avalue = pop(stack, ft);
              return _processor.assign(cl, c, i, tvalue, avalue);
            }
          else
            {
              return _processor.nop();
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
              push(stack, rc, r._v0);
              return r._v1;
            }
        }
      case Unbox:
        {
          var orc = _fuir.unboxOuterRefClazz(cl, c, i);
          var vc = _fuir.unboxResultClazz(cl, c, i);
          if (_fuir.clazzIsRef(orc) && !_fuir.clazzIsRef(vc))
            {
              var refval = pop(stack, orc);
              var r = _processor.unbox(refval, orc);
              push(stack, vc, r._v0);
              return r._v1;
            }
          else
            {
              return _processor.nop();
            }
        }
      case Call:
        {
          var cc0 = _fuir.accessedClazz  (cl, c, i);
          var args = args(cc0, stack, _fuir.clazzArgCount(cc0));
          var tc = _fuir.accessTargetClazz(cl, c, i);
          var tvalue = pop(stack, tc);
          var r = _processor.call(cl, c, i, tvalue, args);
          if (r._v0 == null)  // this may happen even if rt is not void (e.g., in case of tail recursion or error)
            {
              stack.push(null);
            }
          else
            {
              var rt = _fuir.clazzResultClazz(cc0);
              push(stack, rt, r._v0);
            }
          return r._v1;
        }
      case Comment:
        {
          return _processor.comment(_fuir.comment(cl, c, i));
        }
      case Current:
        {
          var r = _processor.current(cl);
          push(stack, cl, r._v0);
          return r._v1;
        }
      case Const:
        {
          var constCl = _fuir.constClazz(c, i);
          var d = _fuir.constData(c, i);
          var r = _processor.constData(constCl, d);
          push(stack, constCl, r._v0);
          return r._v1;
        }
      case Match:
        {
          var subjClazz = _fuir.matchStaticSubject(cl, c, i);
          var subv      = pop(stack, subjClazz);
          RESULT code = _processor.nop();

          // Unbox in case subject is a ref.
          //
          // NYI: it might be better to unbox the subject of a match using an
          // explicit command in the IR instead of this implicit handling:
          //
          if (_fuir.clazzIsRef(subjClazz))
            {
              var ub = _processor.unbox(subv, subjClazz);
              subv = ub._v0;
              code = ub._v1;
            }

          var r = _processor.match(this, cl, c, i, subv);
          if (r._v0 == null)
            {
              stack.push(null);
            }
          return _processor.sequence(new List<>(code, r._v1));
        }
      case Tag:
        {
          var valuecl = _fuir.tagValueClazz(cl, c, i);  // static clazz of value
          var value   = pop(stack, valuecl);            // value that will be tagged
          var newcl   = _fuir.tagNewClazz  (cl, c, i);  // static clazz of result
          int tagNum  = _fuir.clazzChoiceTag(newcl, valuecl);
          var r = _processor.tag(cl, valuecl, value, newcl, tagNum);
          push(stack, newcl, r._v0);
          return r._v1;
        }
      case Env:
        {
          var ecl = _fuir.envClazz(cl, c, i);
          var r = _processor.env(ecl);
          push(stack, ecl, r._v0);
          return r._v1;
        }
      case Dup:
        {
          var v = stack.pop();
          stack.push(v);
          stack.push(v);
          return _processor.nop();
        }
      case Pop:
        { // NYI: pop should not be a NOP.
          return _processor.nop();
        }
      default:
        {
          Errors.fatal("AbstractInterpreter backend does not handle statments of type " + s);
          return null;
        }
      }
  }


}

/* end-of-file */
