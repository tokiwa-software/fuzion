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
 * Source of class Interpreter
 *
 *---------------------------------------------------------------------*/


package dev.flang.be.interpreter;

import java.nio.charset.StandardCharsets;

import dev.flang.fuir.GeneratingFUIR;
import dev.flang.fuir.FUIR;
import dev.flang.fuir.analysis.AbstractInterpreter;

import dev.flang.util.Errors;
import dev.flang.util.FatalError;
import dev.flang.util.FuzionOptions;


/**
 * Interpreter contains interpreter for Fuzion application that is present as
 * intermediate code.
 */
public class Interpreter extends FUIRContext
{
  private final AbstractInterpreter<Value, Object> _ai;
  private final FUIR _fuir;
  private final FuzionOptions _options_;
  public Interpreter(FuzionOptions options, FUIR fuir)
  {
    this._options_ = options;
    this._fuir = new GeneratingFUIR((GeneratingFUIR) fuir)
      {
        // NYI: BUG: fuir should be thread safe #2760
        @Override
        public synchronized int[] matchCaseTags(int s, int cix)
        {
          return super.matchCaseTags(s, cix);
        };
        // NYI: BUG: fuir should be thread safe #2760
        @Override
        public synchronized int[] accessedClazzes(int s)
        {
          return super.accessedClazzes(s);
        }
      };
    FUIRContext.set_fuir(fuir);
    var processor = new Executor(_fuir, _options_);
    _ai = new AbstractInterpreter<Value, Object>(_fuir, processor);
    Intrinsics.ENABLE_UNSAFE_INTRINSICS = options.enableUnsafeIntrinsics();  // NYI: Add to Fuzion IR or BE Config
  }


  /**
   * Run the application with the given args.
   * This is the main entry point of the interpreter and starts
   * the execution of the main clazz.
   *
   *  param args -- NYI: command line args not supported yet
   */
  public void run()
  {
    try
      {
        FuzionThread.current()._callStackFrames.push(_fuir.mainClazzId());
        _ai.processClazz(_fuir.mainClazzId());
      }
    catch (FatalError e)
      {
        throw e;
      }
    catch (StackOverflowError e)
      {
        Errors.fatal("*** " + e + "\n" + Executor.callStack(_fuir));
      }
    catch (RuntimeException | Error e)
      {
        Errors.error("*** " + e + "\n" + Executor.callStack(_fuir));
        throw e;
      }
  }


  /*-----------------------------  statics  -----------------------------*/


  /**
   * Create runtime value of given String constant.
   *
   * @str the string in UTF-16
   */
  static Value value(String str)
  {
    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
    return value(bytes);
  }

  /**
   * Create runtime value of given String constant.
   *
   * @bytes the string in UTF-16
   */
  public static Value value(byte[] bytes)
  {
    int cl = fuir().clazz_Const_String();
    Instance result = new Instance(cl);
    var clArr = fuir().clazz_array_u8();
    Instance arr = new Instance(clArr);
    var saCl = fuir().clazz_fuzionSysArray_u8();
    Instance sa = new Instance(saCl);
    setField(fuir().clazz_fuzionSysArray_u8_length(), saCl, sa, new i32Value(bytes.length));
    var arrayData = new ArrayData(bytes);
    setField(fuir().clazz_fuzionSysArray_u8_data(), saCl, sa, arrayData);
    setField(fuir().lookup_array_internal_array(clArr), cl, arr, sa);
    setField(fuir().clazz_Const_String_utf8_data(), cl, result, arr);

    return result;
  }


  /**
   * setField stores a value into a field
   *
   * @param staticClazz is the static type of the clazz that contains the
   * written field
   *
   * @param curValue the Instance or LValue of that contains the written field
   *
   * @param v the value to be stored in the field
   */
  static void setField(int thiz, int staticClazz, Value curValue, Value v)
  {
    if (PRECONDITIONS) require
      (// NYI: thiz.feature().isField(),
       (curValue instanceof Instance) || curValue instanceof Boxed || (curValue instanceof LValue),
       staticClazz > 0
       // NYI: thiz.feature().isOpenGenericField() == (thiz._select != -1)
       );

    int  fclazz = clazzForField(thiz);
    LValue slot = fieldSlot(thiz, staticClazz, fclazz, curValue);
    setFieldSlot(thiz, fclazz, slot, v);
  }


  /**
   * Get the result clazz of thiz
   * or if thiz is an outer ref c_address.
   */
  private static int clazzForField(int thiz)
  {
    return fuir().clazzFieldIsAdrOfValue(thiz)
      ? fuir().clazz(FUIR.SpecialClazzes.c_sys_ptr)
      : fuir().clazzResultClazz(thiz);
  }


  /**
   * From a value val of type valueClazz which is in
   * choiceClazz.choiceGenerics_, create a new value of type choiceClazz
   * consisting of val and the choice tag.
   *
   * @param choiceClazz the choice clazz the result should have
   *
   * @param valueClazz the static type of val
   *
   * @param val the value
   *
   * @param tagNum the number to be used for tagging the value
   *
   * @return a new value of type choiceClazz containing val.
   */
  static Value tag(int choiceClazz, int valueClazz, Value val, int tagNum)
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(choiceClazz));

    var result  = new Instance(choiceClazz);
    LValue slot = result.at(choiceClazz, 0); // NYI: needed? just result?
    setChoiceField(choiceClazz,
                   choiceClazz,
                   slot,
                   valueClazz,
                   val,
                   tagNum);
    return result;
  }


  /**
   * getField loads the value of a field.
   *
   * @param staticClazz is the static type of the clazz that contains the
   * loaded field
   *
   * @param curValue the Instance or LValue of the object that contains the
   * loaded field
   *
   * @param allowUninitializedRefField When boxing a partially initialized value
   * (this), some fields may not be initialized yet.
   *
   * NYI: Once static analysis detects use of uninitialized data, boxing this
   * data should be disallowed.
   *
   * @return the value that was loaded from the field, of type LValue for
   * non-refs, Instance for normal refs, of type ChoiceIdAsRef, LValue or null
   * for boxed choice tag or ref to outer instance.
   */
  static Value getField(int thiz, int staticClazz, Value curValue, boolean allowUninitializedRefField)
  {
    var staticClazzSpecial = fuir().getSpecialClazz(staticClazz);
    if (PRECONDITIONS) require
      (// NYI: thiz.feature().isField(),
       (curValue instanceof Instance) || (curValue instanceof LValue) ||
       curValue instanceof i8Value   && staticClazzSpecial == FUIR.SpecialClazzes.c_i8   ||
       curValue instanceof i16Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_i16  ||
       curValue instanceof i32Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_i32  ||
       curValue instanceof i64Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_i64  ||
       curValue instanceof u8Value   && staticClazzSpecial == FUIR.SpecialClazzes.c_u8   ||
       curValue instanceof u16Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_u16  ||
       curValue instanceof u32Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_u32  ||
       curValue instanceof u64Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_u64  ||
       curValue instanceof f32Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_f32  ||
       curValue instanceof f64Value  && staticClazzSpecial == FUIR.SpecialClazzes.c_f64  ||
       curValue instanceof boolValue && staticClazzSpecial == FUIR.SpecialClazzes.c_bool,
       staticClazz > 0
      //  NYI: thiz.feature().isOpenGenericField() == (thiz._select != -1)
       );

    Value result = switch (staticClazzSpecial)
      {
      case c_f32 , c_f64 , c_i8 , c_i16 , c_i32 , c_i64 , c_u8 , c_u16 , c_u32 , c_u64 , c_bool -> curValue;
      default ->
        {
          int fclazz = clazzForField(thiz);
          LValue slot = fieldSlot(thiz, staticClazz, fclazz, curValue);
          yield loadField(thiz, fclazz, slot, allowUninitializedRefField);
        }
      };

    if (POSTCONDITIONS) ensure
      (   result != null                          // there must not be any null
       || allowUninitializedRefField              // unless we explicitly allowed uninitialized data
      );

    return result;
  }


  /**
   * compareField does a bitwise comparison of a value and the contents of a
   * field
   *
   * @param staticClazz is the static type of the clazz that contains the
   * written field
   *
   * @param curValue the Instance or LValue of that contains the written field
   *
   * @param v the value to be compared to the field
   *
   * @return true iff both are equal
   */
  static boolean compareField(int thiz, int staticClazz, Value curValue, Value v)
  {
    if (PRECONDITIONS) require
      ( // NYI : thiz.feature().isField(),
       (curValue instanceof Instance) || curValue instanceof Boxed || (curValue instanceof LValue),
       staticClazz > 0);

    int  fclazz = clazzForField(thiz);
    LValue slot   = fieldSlot(thiz, staticClazz, fclazz, curValue);
    return compareFieldSlot(thiz, fclazz, slot, v);
  }


  /**
   * Read a value slot within a choice clazz.
   *
   * @param choiceClazz the runtime clazz corresponding to this
   *
   * @param choice the value containing the choice.
   *
   * @param tag the tag value identifying the slot to be read.
   */
  static Value getChoiceVal(int thiz, int choiceClazz, Value choice, int tag)
  {
    if (PRECONDITIONS) require
      (choiceClazz > 0,
       choiceClazz == thiz,
       choice != null,
       tag >= 0);

    int vclazz = fuir().clazzChoice(choiceClazz, tag);
    LValue slot = choice.at(vclazz, Layout.get(choiceClazz).choiceValOffset(tag));
    return loadField(thiz, vclazz, slot, false);
  }


  /**
   * Read the value slot for refs within a choice clazz.
   *
   * @param choiceClazz the runtime clazz corresponding to this
   *
   * @param choice the value containing the choice.
   */
  static Value getChoiceRefVal(int thiz, int choiceClazz, Value choice)
  {
    if (PRECONDITIONS) require
      (choiceClazz > 0,
       choiceClazz == thiz,
       fuir().clazzIsChoiceWithRefs(choiceClazz),
       choice != null);

    int offset  = Layout.get(choiceClazz).choiceRefValOffset();
    LValue slot = choice.at(fuir().clazzAny(), offset);
    return loadRefField(thiz, slot, false);
  }


  /**
   * load a field from memory
   *
   * @param slot reference to instance and offset of the field to be loaded
   *
   * @param allowUninitializedRefField true if a ref field may be not
   * initialized (e.g., when boxing this).
   *
   * @return the value that was loaded from the field, of type Instance for
   * normal refs, of type ChoiceIdAsRef, LValue for non-reference fields or ref
   * to outer instance, LValue or null for boxed choice tag.
   */
  private static Value loadField(int thiz, int fclazz, LValue slot, boolean allowUninitializedRefField)
  {
    if (CHECKS) check
      (fclazz > 0,
       slot != null);

    Value result = fuir().clazzIsRef(fclazz) ? loadRefField(thiz, slot, allowUninitializedRefField)
                                  : slot;

    if (POSTCONDITIONS) ensure
      (valueTypeMatches(thiz, result, allowUninitializedRefField));

    return result;
  }


  /**
   * Store a value in this choice type and set the proper tag. This is not
   * intended only for assigning a value of one specific generic parameter type
   * of the choice type to a choice field, not for assigning a choice value to a
   * field of the same choice type.
   *
   * @param choiceClazz the runtime clazz of this choice type
   *
   * @param choice the LValue referring to the choice field to be set
   *
   * @param staticTypeOfValue the static type of the value to be assigned to
   * choice.
   *
   * @param v the value to be stored in choice.
   */
  private static void setChoiceField(int thiz,
                                     int choiceClazz,
                                     LValue choice,
                                     int staticTypeOfValue,
                                     Value v,
                                     int tagNum)
  {
    if (PRECONDITIONS) require
      (fuir().clazzIsChoice(choiceClazz),
       choiceClazz == thiz,
       choiceClazz != staticTypeOfValue);

    int  vclazz  = fuir().clazzChoice(choiceClazz, tagNum);
    LValue valSlot = choice.at(vclazz, Layout.get(choiceClazz).choiceValOffset(tagNum));
    if (fuir().clazzIsChoiceOfOnlyRefs(choiceClazz))
      { // store reference only
        if (!fuir().clazzIsRef(staticTypeOfValue))
          { // the value is a stateless value type, so we store the tag as a reference.
            v = ChoiceIdAsRef.get(choiceClazz, tagNum);
            vclazz = fuir().clazzAny();
            staticTypeOfValue = vclazz;
            valSlot = choice.at(vclazz, Layout.get(choiceClazz).choiceRefValOffset());
          }
      }
    else
      { // store tag and value separately
        LValue slot   = choice.at(vclazz, 0);
        (new i32Value(tagNum)).storeNonRef(slot, 1);
      }

    setFieldSlot(thiz, vclazz, valSlot, v);

    if (POSTCONDITIONS) ensure
      (fuir().clazzIsChoiceOfOnlyRefs(choiceClazz) || choice.container.nonrefs[0] >= 0);
  }


  /**
   * Create an LValue that refers to the slot that contains this field.
   *
   * @param thiz the field to access.
   *
   *
   * @param staticClazz is the static type of the clazz that contains the
   * this field
   *
   * @param fclazz is the static type of the field
   *
   * @param curValue the Instance or LValue of the object that contains the
   * loaded field
   *
   * @return an LValue that refers directly to the memory for the field.
   */
  private static LValue fieldSlot(int thiz, int staticClazz, int fclazz, Value curValue)
  {
    var clazz = staticClazz;
    if (fuir().clazzIsRef(staticClazz))
      {
        curValue = (curValue instanceof LValue lv) ? loadRefField(thiz, lv, false)
                                                   : curValue;
        clazz = ((ValueWithClazz) curValue).clazz();
      }
    if (fuir().clazzIsBoxed(staticClazz))
      {
        clazz = ((Boxed)curValue)._valueClazz;
        curValue = ((Boxed)curValue)._contents;
      }
    int off = Layout.get(clazz).offset(thiz);

    // NYI: check if this is a can be enabled or removed:
    //
    //  check
    //    (staticClazz.isAssignableFrom(clazz));
    return curValue.at(fclazz, off);
  }


  /**
   * setFieldSlot stores a value into a field.
   *
   * @param fclazz is the static type of the field to be written to
   *
   * @param slot is the address of the field to be written
   *
   * @param v the value to be stored in slot
   */
  private static void setFieldSlot(int thiz, int fclazz, LValue slot, Value v)
  {
    if (PRECONDITIONS) require
      (fclazz > 0,
       slot != null,
       v != null || fuir().clazzIsChoice(thiz));

    if (fuir().clazzIsRef(fclazz))
      {
        setRefField   (thiz,        slot, v);
      }
    else
      {
        setNonRefField(thiz, fclazz, slot, v);
      }
  }



  /**
   * Store a value in a given field of value type.
   *
   * @param fclazz the runtime clazz of the value type
   *
   * @param slot reference to instance and offset of the field to be set
   *
   * @param v the value to be stored in cur at offset
   */
  private static void setNonRefField(int thiz,
                                     int fclazz,
                                     LValue slot,
                                     Value v)
  {
    if (PRECONDITIONS) require
      (!fuir().clazzIsRef(fclazz),
       slot != null,
       v != null || fuir().clazzIsChoice(thiz) ||
       v instanceof LValue    ||
       v instanceof Instance  ||
       v instanceof i8Value   ||  // NYI: what about u8/u16/..
       v instanceof i16Value  ||
       v instanceof i32Value  ||
       v instanceof i64Value  ||
       v instanceof boolValue    );

    v.storeNonRef(slot, Layout.get(fclazz).size());
  }


  /**
   * Store a reference in a given field of reference type.
   *
   * @param slot reference to instance and offset of the field to be set
   *
   * @param v the value to be stored in cur at offset
   */
  private static void setRefField(int thiz,
                                  LValue slot,
                                  Value v)
  {
    if (PRECONDITIONS) require
      (slot != null,
       valueTypeMatches(thiz, v, false)
       );

    slot.container.refs[slot.offset] = v;
  }


  /**
   * load a field of reference type from memory
   *
   * @param slot reference to instance and offset of the field to be loaded
   *
   * @return the value that was loaded from the field, of type Instance for
   * normal refs, of type ChoiceIdAsRef, LValue or null for boxed choice tag or
   * ref to outer instance.
   *
   * @param allowUninitializedRefField true if a ref field may be not
   * initialized (e.g., when boxing this).
   */
  private static Value loadRefField(int thiz, LValue slot, boolean allowUninitializedRefField)
  {
    if (PRECONDITIONS) require
      (slot != null);

    Value result = slot.container.refs[slot.offset];

    if (POSTCONDITIONS) ensure
      (valueTypeMatches(thiz, result, allowUninitializedRefField));

    return result;
  }


  /**
   * Check if the given field could hold the given value.  Just for pre-/postconditions.q
   *
   * @param thiz a field
   *
   * @param v a value
   *
   * @param allowUninitializedRefField true if a ref field may be not
   * initialized (e.g., when boxing this).
   */
  private static boolean valueTypeMatches(int thiz, Value v, boolean allowUninitializedRefField)
  {
    return
      v instanceof Instance                                            /* a normal ref type     */ ||
      v instanceof JavaRef                                             /* Java_Ref fzjava type  */ ||
      v instanceof Boxed                                               /* a boxed value type    */ ||
      v instanceof ArrayData                                           /* fuzion.sys.array.data */ ||
      v instanceof LValue                                              /* ref type as LValue    */ ||
      v instanceof ChoiceIdAsRef && fuir().clazzIsChoice(thiz)         /* a boxed choice tag    */ ||
      (v instanceof i8Value ||
       v instanceof i16Value ||
       v instanceof i32Value ||
       v instanceof i64Value ||
       v instanceof u8Value ||
       v instanceof u16Value ||
       v instanceof u32Value ||
       v instanceof u64Value ||
       v instanceof f32Value ||
       v instanceof f64Value   ) && fuir().clazzIsOuterRef(thiz)        /* e.g. outerref in integer.infix /-/ */ ||
      v == null                  && fuir().clazzIsChoice(thiz)          /* Nil/Null boxed choice tag */ ||
      v == null                  && allowUninitializedRefField;
  }


  /**
   * compareFieldSlot does a bitwise comparison of a value with the contents of
   * a field.
   *
   * @param fclazz is the static type of the field to be written to
   *
   * @param slot is the address of the field to be written
   *
   * @param v the value to be stored in slot
   */
  private static boolean compareFieldSlot(int thiz, int fclazz, LValue slot, Value v)
  {
    if (PRECONDITIONS) require
      (fclazz > 0,
       slot != null,
       v != null || fuir().clazzIsChoice(thiz));

    if (fuir().clazzIsRef(fclazz))
      {
        return slot.container.refs[slot.offset] == v;
      }
    else
      {
        return v.equalsBitWise(slot, Layout.get(fclazz).size());
      }
  }

}
