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
 * Source of class Intrinsics
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import dev.flang.ast.Consts; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Feature; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Impl; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Type; // NYI: remove dependency! Use dev.flang.fuir instead.
import dev.flang.ast.Types; // NYI: remove dependency! Use dev.flang.fuir instead.

import dev.flang.ir.Clazz;
import dev.flang.ir.Clazzes;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Intrinsics provides the implementation of Fuzion's intrinsic features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class Intrinsics extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * NYI: This will eventually be part of a Fuzion IR / BE Config class.
   */
  public static Boolean ENABLE_UNSAFE_INTRINSICS = null;


  /**
   * Result of debugLevel:
   */
  public static int FUZION_DEBUG_LEVEL = 1;


  /**
   * Result of safety
   */
  public static boolean FUZION_SAFETY = true;


  /*----------------------------  variables  ----------------------------*/


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create a Callable to call an intrinsic feature.
   *
   * @param innerClazz the frame clazz of the called feature
   *
   * @return a Callable instance to execute the intrinsic call.
   */
  public static Callable call(Clazz innerClazz)
  {
    if (PRECONDITIONS) require
      (innerClazz.feature().impl == Impl.INTRINSIC);

    Callable result;
    var f = innerClazz.feature();
    String n = f.qualifiedName();
    // NYI: We must check the argument count in addition to the name!
    if (n.equals("fuzion.std.out.write"))
      {
        result = (args) ->
          {
            System.out.write(args.get(1).i32Value());
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.std.out.flush"))
      {
        result = (args) ->
          {
            System.out.flush();
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.std.exit"))
      {
        result = (args) ->
          {
            int rc = args.get(1).i32Value();
            System.exit(rc);
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("fuzion.java.getStaticField"))
      {
        var actualGenerics = innerClazz._type._generics;
        if ((actualGenerics == null) || (actualGenerics.size() != 1))
          {
            System.err.println("fuzion.java.getStaticField called with wrong number of actual generic arguments");
            System.exit(1);
          }
        Clazz resultClazz = innerClazz.actualClazz(actualGenerics.getFirst());
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            Instance clazzI = (Instance) args.get(1);
            Instance fieldI = (Instance) args.get(2);
            if (clazzI == null)
              {
                System.err.println("fuzion.java.getStaticField called with null class argument");
                System.exit(1);
              }
            if (fieldI == null)
              {
                System.err.println("fuzion.java.getStaticField called with null field argument");
                System.exit(1);
              }
            String clazz = clazzI.string;
            String field = fieldI.string;
            if (clazz == null)
              {
                System.err.println("fuzion.java.getStaticField called with non-String class argument");
                System.exit(1);
              }
            if (field == null)
              {
                System.err.println("fuzion.java.getStaticField called with non-String field argument");
                System.exit(1);
              }
            return JavaInterface.getStaticField(clazz, field, resultClazz);
          };
      }
    else if (n.equals("fuzion.java.callVirtual"))
      {
        result = (args) ->
          {
            if (!ENABLE_UNSAFE_INTRINSICS)
              {
                System.err.println("*** error: unsafe feature "+n+" disabled");
                System.exit(1);
              }
            Instance nameI = (Instance) args.get(1);
            Instance sigI  = (Instance) args.get(2);
            Instance thizI = (Instance) args.get(3);
            Instance argI  = (Instance) args.get(4);
            if (nameI == null)
              {
                System.err.println("fuzion.java.callVirtual called with null name argument");
                System.exit(1);
              }
            if (sigI == null)
              {
                System.err.println("fuzion.java.callVirtual called with null signature argument");
                System.exit(1);
              }
            if (thizI == null)
              {
                System.err.println("fuzion.java.callVirtual called with null thiz argument");
                System.exit(1);
              }
            String name = nameI.string;
            String sig  = sigI.string;
            Object thiz = thizI.javaRef;
            JavaInterface.callVirtual(name,sig,thiz,argI);
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("sys.array.alloc"))
      {
        result = (args) ->
          {
            return new Instance(args.get(1).i32Value());
          };
      }
    else if (n.equals("sys.array.get"))
      {
        result = (args) ->
          {
            return sysArrayGet(/* data  */ ((Instance)args.get(1)),
                               /* index */ args.get(2).i32Value(),
                               /* type  */ innerClazz._outer);
          };
      }
    else if (n.equals("sys.array.setel"))
      {
        result = (args) ->
          {
            sysArraySetEl(/* data  */ ((Instance)args.get(1)),
                          /* index */ args.get(2).i32Value(),
                          /* value */ args.get(3),
                          /* type  */ innerClazz._outer);
            return Value.EMPTY_VALUE;
          };
      }
    else if (n.equals("debug"        ) ||
             n.equals("debugLevel"   ) ||
             n.equals("safety"       ) ||
             n.equals("bool.prefix !") ||
             n.equals("bool.infix ||") ||
             n.equals("bool.infix &&") ||
             n.equals("bool.infix :")   )
      {
        if (Errors.count() == 0)
          {
            Errors.error(f.pos, "intrinsic feature not supported by backend",
                         "intrinsic '"+n+"' should be handled by front end");
          }
        result = (args) -> Value.NO_VALUE;
      }
    else if (n.equals("i32.prefix -°"   )) { result = (args) -> new i32Value (                       -   args.get(0).i32Value()); }
    else if (n.equals("i32.castTo_u32"  )) { result = (args) -> new u32Value (                           args.get(0).i32Value()); }
    else if (n.equals("i32.as_i64"      )) { result = (args) -> new i64Value (                    (long) args.get(0).i32Value()); }
    else if (n.equals("i32.infix +°"    )) { result = (args) -> new i32Value (args.get(0).i32Value() +   args.get(1).i32Value()); }
    else if (n.equals("i32.infix -°"    )) { result = (args) -> new i32Value (args.get(0).i32Value() -   args.get(1).i32Value()); }
    else if (n.equals("i32.infix *°"    )) { result = (args) -> new i32Value (args.get(0).i32Value() *   args.get(1).i32Value()); }
    else if (n.equals("i32.div"         )) { result = (args) -> new i32Value (args.get(0).i32Value() /   args.get(1).i32Value()); }
    else if (n.equals("i32.mod"         )) { result = (args) -> new i32Value (args.get(0).i32Value() %   args.get(1).i32Value()); }
    else if (n.equals("i32.infix &"     )) { result = (args) -> new i32Value (args.get(0).i32Value() &   args.get(1).i32Value()); }
    else if (n.equals("i32.infix |"     )) { result = (args) -> new i32Value (args.get(0).i32Value() |   args.get(1).i32Value()); }
    else if (n.equals("i32.infix >>"    )) { result = (args) -> new i32Value (args.get(0).i32Value() >>  args.get(1).i32Value()); }
    else if (n.equals("i32.infix <<"    )) { result = (args) -> new i32Value (args.get(0).i32Value() <<  args.get(1).i32Value()); }
    else if (n.equals("i32.infix =="    )) { result = (args) -> new boolValue(args.get(0).i32Value() ==  args.get(1).i32Value()); }
    else if (n.equals("i32.infix !="    )) { result = (args) -> new boolValue(args.get(0).i32Value() !=  args.get(1).i32Value()); }
    else if (n.equals("i32.infix <"     )) { result = (args) -> new boolValue(args.get(0).i32Value() <   args.get(1).i32Value()); }
    else if (n.equals("i32.infix >"     )) { result = (args) -> new boolValue(args.get(0).i32Value() >   args.get(1).i32Value()); }
    else if (n.equals("i32.infix <="    )) { result = (args) -> new boolValue(args.get(0).i32Value() <=  args.get(1).i32Value()); }
    else if (n.equals("i32.infix >="    )) { result = (args) -> new boolValue(args.get(0).i32Value() >=  args.get(1).i32Value()); }
    else if (n.equals("i64.prefix -°"   )) { result = (args) -> new i64Value (                       -   args.get(0).i64Value()); }
    else if (n.equals("i64.low32bits"   )) { result = (args) -> new u32Value (                     (int) args.get(0).i64Value()); }
    else if (n.equals("i64.castTo_u64"  )) { result = (args) -> new u64Value (                           args.get(0).i64Value()); }
    else if (n.equals("i64.prefix -°"   )) { result = (args) -> new u64Value (                       -   args.get(0).u64Value()); }
    else if (n.equals("i64.infix +°"    )) { result = (args) -> new i64Value (args.get(0).i64Value() +   args.get(1).i64Value()); }
    else if (n.equals("i64.infix -°"    )) { result = (args) -> new i64Value (args.get(0).i64Value() -   args.get(1).i64Value()); }
    else if (n.equals("i64.infix *°"    )) { result = (args) -> new i64Value (args.get(0).i64Value() *   args.get(1).i64Value()); }
    else if (n.equals("i64.div"         )) { result = (args) -> new i64Value (args.get(0).i64Value() /   args.get(1).i64Value()); }
    else if (n.equals("i64.mod"         )) { result = (args) -> new i64Value (args.get(0).i64Value() %   args.get(1).i64Value()); }
    else if (n.equals("i64.infix &"     )) { result = (args) -> new i64Value (args.get(0).i64Value() &   args.get(1).i64Value()); }
    else if (n.equals("i64.infix |"     )) { result = (args) -> new i64Value (args.get(0).i64Value() |   args.get(1).i64Value()); }
    else if (n.equals("i64.infix >>"    )) { result = (args) -> new i64Value (args.get(0).i64Value() >>  args.get(1).i64Value()); }
    else if (n.equals("i64.infix <<"    )) { result = (args) -> new i64Value (args.get(0).i64Value() <<  args.get(1).i64Value()); }
    else if (n.equals("i64.infix =="    )) { result = (args) -> new boolValue(args.get(0).i64Value() ==  args.get(1).i64Value()); }
    else if (n.equals("i64.infix !="    )) { result = (args) -> new boolValue(args.get(0).i64Value() !=  args.get(1).i64Value()); }
    else if (n.equals("i64.infix <"     )) { result = (args) -> new boolValue(args.get(0).i64Value() <   args.get(1).i64Value()); }
    else if (n.equals("i64.infix >"     )) { result = (args) -> new boolValue(args.get(0).i64Value() >   args.get(1).i64Value()); }
    else if (n.equals("i64.infix <="    )) { result = (args) -> new boolValue(args.get(0).i64Value() <=  args.get(1).i64Value()); }
    else if (n.equals("i64.infix >="    )) { result = (args) -> new boolValue(args.get(0).i64Value() >=  args.get(1).i64Value()); }
    else if (n.equals("u32.as_i64"      )) { result = (args) -> new i64Value (Integer.toUnsignedLong(args.get(0).u32Value())); }
    else if (n.equals("u32.castTo_i32"  )) { result = (args) -> new i32Value (                            args.get(0).u32Value()); }
    else if (n.equals("u32.prefix -°"   )) { result = (args) -> new u32Value (                       -   args.get(0).u32Value()); }
    else if (n.equals("u32.infix +°"    )) { result = (args) -> new u32Value (args.get(0).u32Value() +   args.get(1).u32Value()); }
    else if (n.equals("u32.infix -°"    )) { result = (args) -> new u32Value (args.get(0).u32Value() -   args.get(1).u32Value()); }
    else if (n.equals("u32.infix *°"    )) { result = (args) -> new u32Value (args.get(0).u32Value() *   args.get(1).u32Value()); }
    else if (n.equals("u32.div"         )) { result = (args) -> new u32Value (Integer.divideUnsigned(args.get(0).u32Value(), args.get(1).u32Value())); }
    else if (n.equals("u32.mod"         )) { result = (args) -> new u32Value (Integer.remainderUnsigned(args.get(0).u32Value(), args.get(1).u32Value())); }
    else if (n.equals("u32.infix &"     )) { result = (args) -> new u32Value (args.get(0).u32Value() &   args.get(1).u32Value()); }
    else if (n.equals("u32.infix |"     )) { result = (args) -> new u32Value (args.get(0).u32Value() |   args.get(1).u32Value()); }
    else if (n.equals("u32.infix >>"    )) { result = (args) -> new u32Value (args.get(0).u32Value() >>> args.get(1).u32Value()); }
    else if (n.equals("u32.infix <<"    )) { result = (args) -> new u32Value (args.get(0).u32Value() <<  args.get(1).u32Value()); }
    else if (n.equals("u32.infix =="    )) { result = (args) -> new boolValue(args.get(0).u32Value() ==  args.get(1).u32Value()); }
    else if (n.equals("u32.infix !="    )) { result = (args) -> new boolValue(args.get(0).u32Value() !=  args.get(1).u32Value()); }
    else if (n.equals("u32.infix <"     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) <  0); }
    else if (n.equals("u32.infix >"     )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) >  0); }
    else if (n.equals("u32.infix <="    )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) <= 0); }
    else if (n.equals("u32.infix >="    )) { result = (args) -> new boolValue(Integer.compareUnsigned(args.get(0).u32Value(), args.get(1).u32Value()) >= 0); }
    else if (n.equals("u64.low32bits"   )) { result = (args) -> new u32Value (                     (int) args.get(0).i64Value()); }
    else if (n.equals("u64.castTo_u64"  )) { result = (args) -> new u64Value (                           args.get(0).i64Value()); }
    else if (n.equals("u64.prefix -°"   )) { result = (args) -> new u64Value (                       -   args.get(0).u64Value()); }
    else if (n.equals("u64.infix +°"    )) { result = (args) -> new u64Value (args.get(0).u64Value() +   args.get(1).u64Value()); }
    else if (n.equals("u64.infix -°"    )) { result = (args) -> new u64Value (args.get(0).u64Value() -   args.get(1).u64Value()); }
    else if (n.equals("u64.infix *°"    )) { result = (args) -> new u64Value (args.get(0).u64Value() *   args.get(1).u64Value()); }
    else if (n.equals("u64.div"         )) { result = (args) -> new u64Value (Long.divideUnsigned(args.get(0).u64Value(), args.get(1).u64Value())); }
    else if (n.equals("u64.mod"         )) { result = (args) -> new u64Value (Long.remainderUnsigned(args.get(0).u64Value(), args.get(1).u64Value())); }
    else if (n.equals("u64.infix &"     )) { result = (args) -> new u64Value (args.get(0).u64Value() &   args.get(1).u64Value()); }
    else if (n.equals("u64.infix |"     )) { result = (args) -> new u64Value (args.get(0).u64Value() |   args.get(1).u64Value()); }
    else if (n.equals("u64.infix >>"    )) { result = (args) -> new u64Value (args.get(0).u64Value() >>> args.get(1).u64Value()); }
    else if (n.equals("u64.infix <<"    )) { result = (args) -> new u64Value (args.get(0).u64Value() <<  args.get(1).u64Value()); }
    else if (n.equals("u64.infix =="    )) { result = (args) -> new boolValue(args.get(0).u64Value() ==  args.get(1).u64Value()); }
    else if (n.equals("u64.infix !="    )) { result = (args) -> new boolValue(args.get(0).u64Value() !=  args.get(1).u64Value()); }
    else if (n.equals("u64.infix <"     )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) <  0); }
    else if (n.equals("u64.infix >"     )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) >  0); }
    else if (n.equals("u64.infix <="    )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) <= 0); }
    else if (n.equals("u64.infix >="    )) { result = (args) -> new boolValue(Long.compareUnsigned(args.get(0).u64Value(), args.get(1).u64Value()) >= 0); }
    else if (n.equals("Object.infix !==")) { result = (args) -> new boolValue(args.get(0) !=  args.get(1)); }
    else if (n.equals("Object.infix ===")) { result = (args) -> new boolValue(args.get(0) ==  args.get(1)); }
    else if (n.equals("Object.hashCode" )) { result = (args) -> new i32Value (args.get(0).toString().hashCode()); }
    else if (n.equals("Object.asString" )) { result = (args) -> Interpreter.value(args.get(0).toString());
      // NYI: This could be more useful by giving the object's class, an id, public fields, etc.
      }
    else
      {
        Errors.error(f.pos,
                     "Intrinsic feature not supported",
                     "Missing intrinsic feature: " + f.qualifiedName());
        result = (args) -> Value.NO_VALUE;
      }
    return result;
  }


  static Type elementType(Clazz arrayClazz)
  {
    // NYI: Properly determine generic argument type of array
    var arrayType = arrayClazz._type;
    if (arrayType == Types.resolved.t_conststring /* NYI: Hack */)
      {
        return Types.resolved.t_i32;
      }
    else
      {
        return arrayType._generics.getFirst();
      }
  }


  static void sysArraySetEl(Instance ai,
                            int x,
                            Value v,
                            Clazz arrayClazz)
  {
    // NYI: Properly determine generic argument type of array
    var elementType = elementType(arrayClazz);
    if (x < 0 || x >= ai.refs.length)
      {
        Errors.fatal("array index out of bounds: " + x + " not in 0.."+(ai.refs.length-1)+"\n"+Interpreter.callStack());
      }
    else
      {
        if (elementType == Types.resolved.t_i32)
          {
            ai.nonrefs[x] = v.i32Value();
          }
        else if (elementType == Types.resolved.t_bool)
          {
            ai.nonrefs[x] = v.boolValue() ? 1 : 0;
          }
        else
          {
            ai.refs[x] = v;
          }
      }
  }


  static Value sysArrayGet(Instance ai,
                           int x,
                           Clazz arrayClazz)
  {
    var elementType = elementType(arrayClazz);
    if (x < 0 || x >= ai.refs.length)
      {
        Errors.fatal("array index out of bounds: " + x + " not in 0.."+(ai.refs.length-1)+"\n"+Interpreter.callStack());
        return Value.NO_VALUE; // just to keep javac from complaining
      }
    else if (elementType == Types.resolved.t_i32)
      {
        return new i32Value(ai.nonrefs[x]);
      }
    else if (elementType == Types.resolved.t_bool)
      {
        return new boolValue(ai.nonrefs[x] != 0);
      }
    else
      {
        return ai.refs[x];
      }
  }

}

/* end of file */
