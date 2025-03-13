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
 * Source of class Choices
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm;

import dev.flang.fuir.FUIR;
import dev.flang.fuir.SpecialClazzes;
import dev.flang.be.jvm.classfile.ClassFile;
import dev.flang.be.jvm.classfile.ClassFileConstants;
import dev.flang.be.jvm.classfile.Expr;
import dev.flang.be.jvm.classfile.Label;
import dev.flang.be.jvm.classfile.VerificationType;

import dev.flang.fuir.analysis.AbstractInterpreter;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;


/**
 * Choices provides methods supporting handling of choice type
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Choices extends ANY implements ClassFileConstants
{


  /*----------------------------  constants  ----------------------------*/


  enum ImplKind
  {
    /* an empty choice `choice` or `choice void`, this type has no values
     */
    voidlike,

    /* a single value `choice X` is equivalent to `X`. This includes the case of
     * a single unit type {@code choice nil}, but not {@code choice void}
     */
    unitlike,

    /* A choice between two unit types like `bool` or `choice nil
     * unit`. Implemented using Java's primitive type boolean.
     */
    boollike,

    /* A choice between more than two unit types like `choice of red, yellow, green.`,
     * implemented using Java's primitive type int.
     */
    intlike,

    /* Something like 'option String', implemented using 'null' to represent the unit type
     */
    nullable,

    /* A choice of disjoint ref values and unit values only such as {@code option String (Sequence u8)
     * TRUE nil} (provided no feature exists that inherits from {@code String}  and {@code Sequence u8}).
     * Implemented using an interface that is implemented by all ref types and that has specific
     * singleton instances for each unit type.
     */
    refsAndUnits,

    /* Any other choice type that requires its own instance, a tag and fields for all possible
     * values.
     */
    general
  }


  /*----------------------------  variables  ----------------------------*/



  /**
   * The intermediate code we are compiling and names, types helper instances.
   */
  private final FUIR _fuir;
  private final Names _names;
  private final Types _types;


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create instance of Types
   */
  public Choices(FUIR fuir, Names names, Types types)
  {
    this._fuir = fuir;
    this._names = names;
    this._types = types;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * For a given choice, determine its implementation kind.
   */
  ImplKind kind(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(cl));

    var nonVoid = 0;
    int units = 0;
    int refs = 0;
    boolean overlappingRefs = false;
    for (var i = 0; i < _fuir.clazzChoiceCount(cl); i++)
      {
        var tc = _fuir.clazzChoice(cl, i);
        if (!_fuir.clazzIsVoidType(tc))
          {
            nonVoid++;
            if (_fuir.clazzIsUnitType(tc))
              {
                units++;
              }
            else if (_fuir.clazzIsRef(tc))
              {
                refs++;
                for (var j = 0; j < i; j++)
                  {
                    var tcj = _fuir.clazzChoice(cl, j);
                    if (overlappingRefs(tc, tcj))
                      {
                        overlappingRefs = true;
                      }
                  }
              }
          }
      }

    if (_fuir.clazzIs(cl, SpecialClazzes.c_bool))
      { // very small examples may use only `TRUE` or only `FALSE`, or none of
        // these values, which would then turn `bool` into a `unitlike` or even
        // `voidlike` choice, but we do not want to deal with this exotic case:
        nonVoid = 2;
        units = 2;
      }

    if      (nonVoid == 0                               ) { return ImplKind.voidlike; }
    else if (nonVoid == units && units == 1             ) { return ImplKind.unitlike; }
    else if (nonVoid == units && units == 2             ) { return ImplKind.boollike; }
    else if (nonVoid == units                           ) { return ImplKind.intlike;  }
    else if (nonVoid == 2     && units == 1 && refs == 1) { return ImplKind.nullable; }
    else if (nonVoid == units + refs && !overlappingRefs) { return ImplKind.refsAndUnits; }
    else                                                  { return ImplKind.general; }
  }


  /**
   * For a nullable choice type cl, return the single reference type this might
   * hold.
   *
   * @param cl a choice type of kind nullable.
   */
  JavaType singleRefTypeInNullable(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(cl),
       kind(cl) == ImplKind.nullable);

    var nc = _fuir.clazzChoiceCount(cl);
    for (var i = 0; i < nc; i++)
      {
        var tc = _fuir.clazzChoice(cl, i);
        if (_fuir.clazzIsRef(tc))
          {
            return _types.resultType(tc);
          }
      }
    throw new Error("resultType did not find the single ref type in a choice of kind nullable");
  }


  /**
   * Check if two given reference types have a common ancestor. If so, the
   * dynamic type of a ref value of this type cannot be used to identify if the
   * value in a choice is to be tagged as c1 or c2, so we cannot use refsAndUnits.
   */
  boolean overlappingRefs(int c1, int c2)
  {
    if (_fuir.clazzIsRef(c1) && _fuir.clazzIsRef(c2))
      {
        var h1 = _fuir.clazzInstantiatedHeirs(c1);
        var h2 = _fuir.clazzInstantiatedHeirs(c2);
        for (var i1 : h1)
          {
            for (var i2 : h2)
              {
                if (i1 == i2)
                  {
                    return true;
                  }
              }
          }
      }
    return false;
  }


  /**
   * Get the Java type for a given choice.
   */
  JavaType javaType(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(cl));

    return switch (kind(cl))
      {
      case voidlike, unitlike -> PrimitiveType.type_void;
      case boollike           -> PrimitiveType.type_boolean;
      case intlike            -> PrimitiveType.type_int;
      case nullable           -> singleRefTypeInNullable(cl);
      case refsAndUnits       -> new ClassType(_names.javaInterface(cl)); // NYI: OPTIMIZATION: caching!
      case general            -> new ClassType(_names.javaClass(cl)); // NYI: OPTIMIZATION: caching!
      };
  }


  /**
   * Create code for given choice cl.  Code is added directly to the corresponding classes and interfaces.
   *
   * @param cl id of choice clazz to compile
   */
  void createCode(int cl)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzKind(cl) == FUIR.FeatureKind.Choice,
       _fuir.clazzIsChoice(cl));

    var cf = _types.classFile(cl);
    if (cf != null)
      {
        cf.field(ACC_PUBLIC,
                 Names.TAG_NAME,
                 PrimitiveType.type_int.descriptor());

        switch (kind(cl))
          {
          case refsAndUnits:
            {
              var ci = _types.interfaceFile(cl);
              cf.addImplements(ci._name);

              var gtn = _names.getTag(cl);
              ci.method(ACC_PUBLIC | ACC_ABSTRACT, gtn, "()I", new List<>());
              var nc = _fuir.clazzChoiceCount(cl);
              for (var tagNum = 0; tagNum < nc; tagNum++)
                {
                  var tc = _fuir.clazzChoice(cl, tagNum);
                  if (_fuir.clazzIsRef(tc))
                    {
                      for (var h : _fuir.clazzInstantiatedHeirs(tc))
                        {
                          var hcf = _types.classFile(h);
                          hcf.addImplements(ci._name);

                          var bc_tag = Expr.iconst(tagNum)
                            .andThen(Expr.IRETURN);
                          var code_tag = hcf.codeAttribute(gtn + "in interface for "+_fuir.clazzAsString(cl),
                                                           bc_tag, new List<>(), ClassFile.StackMapTable.empty(hcf, new List<>(VerificationType.UninitializedThis), bc_tag));
                          hcf.method(ACC_PUBLIC, gtn, "()I", new List<>(code_tag));
                        }
                    }
                }

              var bc_clinit = Expr.UNIT;
              var ut = new ClassType(cf._name);
              var uti = _types.javaType(cl);
              for (int i = 0; i < _fuir.clazzChoiceCount(cl); i++)
                {
                  var tc = _fuir.clazzChoice(cl, i);
                  if (_fuir.clazzIsUnitType(tc))
                    {
                      var u = _names.choiceUnitAsRef(i);
                      cf.field(ACC_PUBLIC | ACC_STATIC,
                               u,
                               uti.descriptor());
                      bc_clinit = bc_clinit
                        .andThen(Expr.new0(cf._name, ut))
                        .andThen(Expr.DUP)
                        .andThen(Expr.iconst(i))
                        .andThen(Expr.invokeSpecial(cf._name,
                                                    "<init>",
                                                    "(I)V"))
                        .andThen(Expr.putstatic(cf._name,
                                                u,
                                                uti));
                    }
                }

              var bc_init = Expr.aload(0, ut)
                .andThen(Expr.invokeSpecial(cf._super,"<init>","()V"))
                .andThen(Expr.aload(0, ut))
                .andThen(Expr.iload(1))
                .andThen(Expr.putfield(cf._name,
                                       Names.TAG_NAME,
                                       PrimitiveType.type_int))
                .andThen(Expr.RETURN);
              var initLocals = Types.addToLocals(new List<>(), ut);
              initLocals.add(VerificationType.Integer);
              var code_init = cf.codeAttribute("<init> in class for " + _fuir.clazzAsString(cl),
                                               bc_init, new List<>(), ClassFile.StackMapTable.empty(cf, initLocals, bc_init));
              cf.method(ACC_PUBLIC, "<init>", "(I)V", new List<>(code_init));

              var bc_tag = Expr.aload(0, ut)
                .andThen(Expr.getfield(cf._name,
                                       Names.TAG_NAME,
                                       PrimitiveType.type_int))
                .andThen(Expr.IRETURN);
              var code_tag = cf.codeAttribute(gtn + "in class for " + _fuir.clazzAsString(cl),
                                              bc_tag, new List<>(), ClassFile.StackMapTable.empty(cf, Types.addToLocals(new List<>(), ut), bc_tag));
              cf.method(ACC_PUBLIC, gtn, "()I", new List<>(code_tag));

              cf.addToClInit(bc_clinit);
              break;
            }
          case general:
            {
              for (int i = 0; i < _fuir.clazzChoiceCount(cl); i++)
                {
                  var tc = _fuir.clazzChoice(cl, i);
                  if (_fuir.clazzIsRef(tc))
                    {
                      if (!cf.hasField(Names.CHOICE_REF_ENTRY_NAME))
                        {
                          cf.field(ACC_PUBLIC,
                                   Names.CHOICE_REF_ENTRY_NAME,
                                   Names.ANYI_TYPE.descriptor());
                        }
                    }
                  else
                    {
                      var ft = generalValueFieldType(cl, i);
                      if (ft != PrimitiveType.type_void)
                        {
                          cf.field(ACC_PUBLIC,
                                   generalValueFieldName(cl, i),
                                   ft.descriptor());
                        }
                    }
                }
            }
          default:
            break;
          }
      }
  }


  /**
   * Filter out all void types in a choice of type unitlike, boollike or intlike
   * and find the number of given tag when this filtering was done
   *
   * @param choice a choice type of kind unitlike, boollike or intlike
   *
   * @param tagNum a tag number for that choice.
   *
   * @return the resulting tag number after filtering out void types, -1 in case
   * of an error, i.e., tagNum was a void type.
   */
  int intValueForTagNum(int choice, int tagNum)
  {
    if (PRECONDITIONS) require
      (switch (kind(choice))
               {
                 case unitlike, boollike, intlike -> true;
                 default -> false;
               },
       tagNum >= 0 && tagNum < _fuir.clazzChoiceCount(choice));

    int result = -1;
    int nonVoid = 0;
    for (var i = 0; result < 0 && i < _fuir.clazzChoiceCount(choice); i++)
      {
        var tc = _fuir.clazzChoice(choice, i);
        if (!_fuir.clazzIsVoidType(tc))
          {
            if (CHECKS) check
              (_fuir.clazzIsUnitType(tc));
            if (i == tagNum)
              {
                result = nonVoid;
              }
            nonVoid++;
          }
      }
    return result;
  }


  /**
   * Perform a match on value sub.
   *
   * @param jvm the JVM instance
   *
   * @param ai the abstract interpreter instance
   *
   * @param s site of the match expression
   *
   * @param sub code to produce the match subject value
   *
   * @return the code for the match, produces unit type result.
   */
  public Expr match(JVM jvm, AbstractInterpreter<Expr, Expr> ai, int s, Expr sub)
  {
    var cl = _fuir.clazzAt(s);
    var subjClazz = _fuir.matchStaticSubject(s);
    Expr code;

    switch (kind(subjClazz))
      {
      case voidlike:
        {
          Errors.fatal("JVM backend match called for void-like choice type " + _fuir.clazzAsString(subjClazz) + " when compiling " + _fuir.siteAsString(s));
          throw new Error(); // never executed, just to keep javac from complaining.
        }
      case unitlike:
        {
          code = null;
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              var tags = _fuir.matchCaseTags(s, mc);
              for (var tagNum : tags)
                {
                  var tc = _fuir.clazzChoice(subjClazz, tagNum);
                  if (!_fuir.clazzIsVoidType(tc))
                    {
                      if (CHECKS) check
                        (code == null);  // if there are several non-voids, we would have at least boollike kind
                      code = Expr.UNIT.andThen(ai.processCode(_fuir.matchCaseCode(s, mc)));
                    }
                }
            }
          if (CHECKS) check
            (code != null);  // if there is no non-void, we would have voidlike kind
          break;
        }
      case boollike:
        {
          Expr pos = null, neg = null;
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              var tags = _fuir.matchCaseTags(s, mc);
              for (var tagNum : tags)
                {
                  var t = intValueForTagNum(subjClazz, tagNum);
                  switch (t)
                    {
                    case 0: neg = Expr.UNIT.andThen(ai.processCode(_fuir.matchCaseCode(s, mc))); break;
                    case 1: pos = Expr.UNIT.andThen(ai.processCode(_fuir.matchCaseCode(s, mc))); break;
                    case -1: break; //  void type
                    default: throw new Error("JVM backend match found unexpected tag number " + t + " when compiling " + _fuir.siteAsString(s));
                  }
                }
            }
          pos = pos != null ? pos : jvm.reportUnreachable(s, "bool pos");
          neg = neg != null ? neg : jvm.reportUnreachable(s, "bool neg");
          code = sub
            .andThen(Expr.branch(O_ifeq, neg, pos));
          break;
        }
      case intlike:
        {
          code = sub;  // == tag!

          var lEnd = new Label();
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              // NYI: OPTIMIZATION: This currently uses a cascade of if..else if.., should better uses tableswitch.
              var tags = _fuir.matchCaseTags(s, mc);
              for (var tagNum : tags)
                {
                  var tc = _fuir.clazzChoice(subjClazz, tagNum);
                  if (!_fuir.clazzIsVoidType(tc))
                    {
                      if (CHECKS) check
                        (_fuir.clazzIsUnitType(tc));
                      code = code.andThen(Expr.DUP)                                 //          tag, tag
                        .andThen(Expr.iconst(tagNum))                               //          tag, tag, tagNum
                        .andThen(Expr.branch(ClassFileConstants.O_if_icmpeq,        //          tag
                                             Expr.POP                               //          -
                                               .andThen(ai.processCode(_fuir.matchCaseCode(s, mc)))
                                               .andThen(Expr.gotoLabel(lEnd))));
                    }
                }
            }
          code = code                                                               //          tag
            .andThen(Expr.POP)                                                      //          -
            .andThen(lEnd);
          break;
        }
      case nullable:
        {
          Expr pos = null, neg = null;
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              var field = _fuir.matchCaseField(s, mc);
              var tags = _fuir.matchCaseTags(s, mc);
              for (var tagNum : tags)
                {
                  var tc = _fuir.clazzChoice(subjClazz, tagNum);
                  if (_fuir.clazzIsRef(tc))
                    {
                      if (field != -1 && jvm.fieldExists(field))
                        {                                                                      // sub
                          pos =
                            (cl == _fuir.clazzUniverse()
                              ? jvm.LOAD_UNIVERSE
                              : Expr.aload(jvm.current_index(cl), _types.resultType(cl)))      // sub, cur
                            .andThen(Expr.SWAP)                                                // cur, sub
                            .andThen(jvm.putfield(field));                                     // -
                        }
                      else
                        {                                                       //          sub
                          pos = Expr.POP;                                       //          -
                        }
                      pos = pos.andThen(ai.processCode(_fuir.matchCaseCode(s, mc)));
                    }
                  else if (_fuir.clazzIsUnitType(tc))
                    {
                      neg = Expr.POP                                            //          -
                        .andThen(ai.processCode(_fuir.matchCaseCode(s, mc)));
                    }
                }
            }
          pos = pos != null ? pos : Expr.POP.andThen(jvm.reportUnreachable(s, "nullable pos"));
          neg = neg != null ? neg : Expr.POP.andThen(jvm.reportUnreachable(s, "nullable neg"));
          code = sub                                                            // stack is sub
            .andThen(Expr.DUP)                                                  //          sub, sub
            .andThen(Expr.branch(O_ifnull,                                      //          sub
                                 neg,                                           //          .. continue above pos/neg
                                 pos));
          break;
        }

      case refsAndUnits:
        {
          code = sub
            .andThen(Expr.DUP)
            .andThen(Expr.invokeInterface(_types.interfaceFile(subjClazz)._name,
                                          _names.getTag(subjClazz),
                                          "()I",
                                          PrimitiveType.type_int,
                                          _fuir.sitePos(s).line()));

          var lEnd = new Label();
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              // NYI: OPTIMIZATION: This currently uses a cascade of if..else if.., should better uses tableswitch.
              var field = _fuir.matchCaseField(s, mc);
              var tags = _fuir.matchCaseTags(s, mc);
              for (var tagNum : tags)
                {
                  Expr pos;
                  var tc = _fuir.clazzChoice(subjClazz, tagNum);
                  if (!_fuir.clazzIsVoidType(tc))
                    {
                      if (field != -1 && jvm.fieldExists(field))
                        {
                          var rt = _types.resultType(_fuir.clazzResultClazz(field));
                          pos =                                                 // stack is sub, tag
                            Expr.POP                                            //          sub
                            .andThen(cl == _fuir.clazzUniverse()
                              ? jvm.LOAD_UNIVERSE
                              : Expr.aload(jvm.current_index(cl), _types.resultType(cl))) // sub, cur
                            .andThen(Expr.SWAP)                                 //          cur, sub
                            .andThen(Expr.checkcast(rt))                        //          cur, val
                            .andThen(jvm.putfield(field));                      //          -
                        }
                      else
                        {
                          pos = Expr.UNIT                                       //          sub, tag
                            .andThen(Expr.POP)                                  //          sub
                            .andThen(Expr.POP);                                 //          -
                        }
                      pos = pos.andThen(ai.processCode(_fuir.matchCaseCode(s, mc)))
                        .andThen(Expr.gotoLabel(lEnd));
                      code = code.andThen(Expr.DUP)                             //          sub, tag, tag
                        .andThen(Expr.iconst(tagNum))                           //          sub, tag, tag, tagNum
                        .andThen(Expr.branch(ClassFileConstants.O_if_icmpeq,    //          sub, tag
                                             pos));
                    }
                }
            }
          code = code                                                           //          sub, tag
            .andThen(Expr.POP)                                                  //          sub
            .andThen(Expr.POP)                                                  //          -
            .andThen(lEnd);
          break;
        }
      case general:
        {
          code = sub                                                            // stack is sub
            .andThen(Expr.DUP)                                                  //          sub, sub
            .andThen(Expr.getfield(_names.javaClass(subjClazz),                 //          sub, tag
                                   Names.TAG_NAME,
                                   ClassFileConstants.PrimitiveType.type_int));
          var lEnd = new Label();
          for (var mc = 0; mc < _fuir.matchCaseCount(s); mc++)
            {
              // NYI: OPTIMIZATION: This currently uses a cascade of if..else if.., should better uses tableswitch.
              var field = _fuir.matchCaseField(s, mc);
              var tags = _fuir.matchCaseTags(s, mc);
              for (var tagNum : tags)
                {
                  Expr pos;
                  var tc = _fuir.clazzChoice(subjClazz, tagNum);
                  if (!_fuir.clazzIsVoidType(tc))
                    {
                      if (field != -1 && jvm.fieldExists(field))
                        {
                          var rc = _fuir.clazzResultClazz(field);
                          var rt = _types.resultType(rc);
                          pos =                                                     // stack is sub, tag
                            Expr.POP                                                //          sub
                            .andThen(cl == _fuir.clazzUniverse()
                              ? jvm.LOAD_UNIVERSE
                              : Expr.aload(jvm.current_index(cl), _types.resultType(cl))) // sub, cur
                            .andThen(Expr.SWAP)                                     //          cur, sub
                            .andThen(Expr.getfield(_names.javaClass(subjClazz),     //          cur, val
                                                   generalValueFieldName(subjClazz, tagNum),
                                                   generalValueFieldType(subjClazz, tagNum)))
                            .andThen(_fuir.clazzIsRef(rc) ? Expr.checkcast(rt)      //          cur, val
                                                          : Expr.UNIT)
                            .andThen(jvm.putfield(field));                          //          -
                        }
                      else
                        {
                          pos = Expr.UNIT                                           //          sub, tag
                            .andThen(Expr.POP)                                      //          sub
                            .andThen(Expr.POP);                                     //          -
                        }
                      pos = pos
                        .andThen(ai.processCode(_fuir.matchCaseCode(s, mc)))
                        .andThen(Expr.gotoLabel(lEnd));
                      code = code.andThen(Expr.DUP)                                 //          sub, tag, tag
                        .andThen(Expr.iconst(tagNum))                               //          sub, tag, tag, tagNum
                        .andThen(Expr.branch(ClassFileConstants.O_if_icmpeq,        //          sub, tag
                                             pos));
                    }
                }
            }
          code = code                                                               //          sub, tag
            .andThen(Expr.POP)                                                      //          sub
            .andThen(Expr.POP)                                                      //          -
            .andThen(lEnd);
          break;
        }
      default: throw new Error("Unexpected choice kind in match of JVM backend: " + kind(subjClazz));
      }
    return code;
  }


  /**
   * Create a tagged value of type newcl from an untagged value for type valuecl.
   *
   * @param jvm the JVM instance
   *
   * @param s site of the tag expression
   *
   * @param value code to produce the value we are tagging
   *
   * @param newcl the choice type after tagging
   *
   * @param tagNum the tag number, corresponding to the choice type in
   * _fuir.clazzChoice(newcl, tagNum).
   *
   * @return code to produce the tagged value as a result.
   */
  Expr tag(JVM jvm, int s, Expr value, int newcl, int tagNum)
  {
    Expr res;
    var tc = _fuir.clazzChoice(newcl, tagNum);

    switch (kind(newcl))
      {
      case voidlike:
        {
          throw new Error("JVM backend tag called for voidlike choice type" + _fuir.clazzAsString(newcl) + " when compiling " + _fuir.siteAsString(s));
        }
      case unitlike:
        {
          res = Expr.UNIT;
          break;
        }
      case boollike:
        {
          // there must be two non-void unit type choice elements, we map the
          // first one to 0/false and the second one to 1/true:
          var v = intValueForTagNum(newcl, tagNum);
          if (CHECKS) check
            (v >= 0,
             v < 2);
          res = value.drop()
            .andThen(Expr.iconst(v));
          break;
        }
      case intlike:
        {
          res = value.drop()
            .andThen(Expr.iconst(tagNum));
          break;
        }
      case nullable:
        {
          if (_fuir.clazzIsUnitType(tc))
            {
              res = value.drop()
                .andThen(Expr.ACONST_NULL);
            }
          else
            {
              if (CHECKS) check
                (_fuir.clazzIsRef(tc));
              res = value;
            }
          break;
        }
      case refsAndUnits:
        {
          // refs are returned unchanged, while unit types are replaced by preallocated instances.
          res = value;
          if (_fuir.clazzIsUnitType(tc))
            {
              res = res.drop()
                .andThen(Expr.getstatic(_names.javaClass(newcl),
                                        _names.choiceUnitAsRef(tagNum),
                                        _types.javaType(newcl)));
            }
          break;
        }
      case general:
        {
          var create = jvm.new0(newcl)                                            // choice
            .andThen(Expr.DUP)                                                    // choice, choice
            .andThen(Expr.iconst(tagNum))                                         // choice, choice, int
            .andThen(Expr.putfield(_names.javaClass(newcl),                       // choice
                                   Names.TAG_NAME,
                                   ClassFileConstants.PrimitiveType.type_int));
          var fn = generalValueFieldName(newcl, tagNum);
          var ft = generalValueFieldType(newcl, tagNum);
          if (ft == PrimitiveType.type_void)
            {
              res = value.drop().andThen(create);
            }
          else
            {
              res = create                                               // choice
                .andThen(Expr.DUP)                                       // choice, choice
                .andThen(value)                                          // choice, choice, value
                .andThen(Expr.putfield(_names.javaClass(newcl),          // choice
                                       fn,
                                       ft));
            }
          break;
        }
      default: throw new Error("Unexpected choice kind in tag of JVM backend: " + kind(newcl));
      }
    return res.is(_types.resultType(newcl));
  }


  /**
   * For a choice of kind general, get the name of the (possibly shared) field
   * for given tag number.
   *
   * @param cl a choice class of general kind
   *
   * @param tagNum a valid tag value within that choice
   *
   * @return the field name
   */
  String generalValueFieldName(int cl, int tagNum)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(cl),
       kind(cl) == ImplKind.general,
       0 <= tagNum && tagNum <= _fuir.clazzChoiceCount(cl));

    return _names.choiceEntryName(cl, tagNum);
  }


  /**
   * For a choice of kind general, get the type of the (possibly shared) field
   * for given tag number.
   *
   * @param cl a choice class of general kind
   *
   * @param tagNum a valid tag value within that choice
   *
   * @return the field type
   */
  JavaType generalValueFieldType(int cl, int tagNum)
  {
    if (PRECONDITIONS) require
      (_fuir.clazzIsChoice(cl),
       kind(cl) == ImplKind.general,
       0 <= tagNum && tagNum <= _fuir.clazzChoiceCount(cl));

    var tc = _fuir.clazzChoice(cl, tagNum);
    var ft = _fuir.clazzIsRef(tc) ? Names.ANYI_TYPE
                                  : _types.resultType(tc);
    return ft;
  }


}
/* end of file */
