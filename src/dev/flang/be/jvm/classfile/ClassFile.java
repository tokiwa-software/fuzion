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
 * Source of class ClassFile
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.classfile;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.Pair;


/**
 * ClassFile provides means to synthesize a Java class file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ClassFile extends ANY implements ClassFileConstants
{


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Helper to create the UTF bytes of a given Java string.
   *
   * @param s a Java string
   *
   * @return the corresponding UTF encoded bytes.
   */
  static byte[] toUTF(String s)
  {
    try
      {
        var ba = new ByteArrayOutputStream();
        var da = new DataOutputStream(ba);
        da.writeUTF(s);
        return ba.toByteArray();
      }
    catch (IOException o)
      {
        throw new Error(o);
      }
  }


  /**
   * Abstract writer for bytecode instructions. Different implementations are
   * used to collect information on bytecodes and to finally generate code.
   */
  static abstract class ByteCodeWriter
  {
    /**
     * what are we compiling, used for error messages
     */
    final String _where;

    /**
     * Constructor
     *
     * @param where string describing what we are compiling, for error messages.
     */
    ByteCodeWriter(String where)
    {
      _where = where;
    }


    /**
     * Write unsigned or signed integers or byte arrays:
     */
    abstract void writeU4(int v);
    abstract void writeI4(int v);
    abstract void writeI8(long v);
    abstract void writeU2(int v);
    abstract void writeU1(int v);
    abstract void write(byte v);
    abstract void write(byte[] v);


    void writeUTF(String s) // NYI: OPTIMIZATION: better use toUTF at the caller and cache the result there
    {
      write(toUTF(s));
    }


    /**
     * Set the given label to the current position.
     */
    abstract void setLabel(Label l);


    /**
     * Add exception table.  This is used to record the existence of a TryCatch in the code for
     * ByteCodeWriters that need this data (to actually write it out).
     *
     * The default implementation is empty since this is not needed for size estimation etc.
     */
    void addExceptionTable(Expr.TryCatch et)
    {
    }


    /**
     * Determine the offset for a branch instruction at from to label at to.
     */
    abstract int offset(Label from, Label to);

    /**
     * Check if the given offset fits in a signed 16-bit value used in
     * bytecode. Produce fatal error if this is not the case.
     */
    void checkBranchOffset(int offset)
    {
      if (offset < MIN_BRANCH_OFFSET || offset >= MAX_BRANCH_OFFSET)
        {
          Errors.fatal("Offset in branch within bytecode created "+(_where == null ? "" : "for `" + _where) + "` is " +
                       offset + ", the maximum allowed offset is in the range of " + MIN_BRANCH_OFFSET + " .. " + MAX_BRANCH_OFFSET + ".\n" +
                       "To solve this, you might simplify the code or split up that feature into several smaller features.");
        }
    }

  }


  /**
   * ByteCodeWriter that produces an upper bound estimate of the bytecode size.
   */
  static class ByteCodeSizeEstimate extends ByteCodeWriter
  {
    /**
     * The current size estimate.
     */
    int _size = 0;


    /**
     * Constructor
     *
     * @param where string describing what we are compiling, for error messages.
     */
    ByteCodeSizeEstimate(String where)
    {
      super(where);
    }


    /**
     * Get the current size.  Produce an error in case this exceeds
     * MAX_BYTECODE_LENGTH.
     */
    int size()
    {
      if (_size > MAX_BYTECODE_LENGTH)
        {
          Errors.fatal("Code size of bytecode created " + (_where == null ? "" : "for `" + _where) + "` is " +
                       _size + ", the maximum allowed length is " + MAX_BYTECODE_LENGTH + ".\n" +
                       "To solve this, you might simplify the code or split up that feature into several smaller features.");
        }
      return _size;
    }


    /**
     * Write unsigned or signed integers or byte arrays: These only increment the size.
     */
    void writeU4 (int    v) { _size += 4; }
    void writeI4 (int    v) { _size += 4; }
    void writeI8 (long   v) { _size += 8; }
    void writeU2 (int    v) { _size += 2; }
    void writeU1 (int    v) { _size += 1; }
    void write   (byte   v) { _size += 1; }
    void write   (byte[] v) { _size += v.length; }


    /**
     * Set the label's position estimate to the current position.
     */
    void setLabel(Label l)
    {
      if (PRECONDITIONS) require
        (l._posEstimate == -1 || l._posEstimate == _size,
         l._posFinal    == -1);

      l._posEstimate = _size;
    }

    /**
     * Get an estimate of the offset for a branch. This is MAX_BRANCH_OFFSET
     * during the estimate phase, wide branches are not yet supported.
     */
    int offset(Label from, Label to)
    {
      setLabel(from);
      return MAX_BRANCH_OFFSET;
    }

  }


  /**
   * ByteCodeWriter that fixes code size and positions.
   */
  static class ByteCodeFixLabels extends ByteCodeSizeEstimate
  {

    /**
     * Constructor
     *
     * @param where string describing what we are compiling, for error messages.
     */
    ByteCodeFixLabels(String where)
    {
      super(where);
    }


    /**
     * Set the label's final position to the current position.
     */
    void setLabel(Label l)
    {
      if (PRECONDITIONS) require
        (l._posEstimate != -1,
         l._posFinal    == -1 || l._posFinal == _size);

      l._posFinal = _size;

      if (CHECKS) check
        (l._posFinal <= l._posEstimate);
    }


    /**
     * Get an estimate of the offset for a branch based on the estimated
     * position determined in the previous phase.
     */
    int offset(Label from, Label to)
    {
      if (PRECONDITIONS) require
        (from._posEstimate != -1,
         to._posEstimate != -1);

      setLabel(from);
      int offset = to._posEstimate - from._posEstimate;
      checkBranchOffset(offset);
      return offset;
    }

  }


  /**
   * ByteCodeWriter that writes the bytecode to an instance of Kaku.
   */
  static class ByteCodeWrite extends ByteCodeWriter
  {
    /**
     * The target to write to.
     */
    private final Kaku _kaku;

    /**
     * The initial size of _kaku.
     */
    private final int _initialSize;

    /**
     * The exception table found while writing this code, NO_EXC_TABLE if none.
     */
    List<Expr.TryCatch> _exceptionTable = NO_EXC_TABLE;

    static List<Expr.TryCatch> NO_EXC_TABLE = new List<Expr.TryCatch>();
    static { NO_EXC_TABLE.freeze(); }


    /**
     * Constructor
     *
     * @param where string describing what we are compiling, for error messages.
     *
     * @param kaku target to write bytecodes to, may already contain code.
     */
    ByteCodeWrite(String where, Kaku kaku)
    {
      super(where);
      this._kaku = kaku;
      this._initialSize = _kaku.size();
    }


    /**
     * Write unsigned or signed integers or byte arrays:
     */
    void writeU4 (int    v) { _kaku.writeU4(v); }
    void writeI4 (int    v) { _kaku.writeI4(v); }
    void writeI8 (long   v) { _kaku.writeI8(v); }
    void writeU2 (int    v) { _kaku.writeU2(v); }
    void writeU1 (int    v) { _kaku.writeU1(v); }
    void write   (byte   v) { _kaku.write  (v); }
    void write   (byte[] v) { _kaku.write  (v); }


    /**
     * Check that the final position of the label is equal to the actual position.
     */
    void setLabel(Label l)
    {
      if (PRECONDITIONS) require
        (l._posEstimate != -1,
         l._posFinal    != -1);

      if (CHECKS) check
        (l._posFinal == _kaku.size() - _initialSize);
    }


    /**
     * Add exception table that was found in the code.
     */
    @Override
    void addExceptionTable(Expr.TryCatch et)
    {
      _exceptionTable = _exceptionTable.addAfterUnfreeze(et);
    }


    /**
     * Get the offset for a branch from from to label to using the final positions.
     */
    int offset(Label from, Label to)
    {
      if (PRECONDITIONS) require
        (from._posFinal != -1,
         to  ._posFinal != -1);

      setLabel(from);
      var offset = to._posFinal - from._posFinal;
      return offset;
    }

  }


  /**
   * Kaku is japanese for writing, this is our writer. Maybe Sakka (作家) would
   * be better?
   *
   * This provides convenience methods to write data as needed in the class file
   * to a byte array.
   */
  static class Kaku
  {
    ByteArrayOutputStream _b = new ByteArrayOutputStream();
    DataOutputStream _o = new DataOutputStream(_b);

    int size()
    {
      return _b.size();
    }

    void writeU4(int v)
    {
      write((byte) (v >> 24));
      write((byte) (v >> 16));
      write((byte) (v >>  8));
      write((byte)  v       );
    }
    void writeI4(int v)
    {
      write((byte) (v >> 24));
      write((byte) (v >> 16));
      write((byte) (v >>  8));
      write((byte)  v       );
    }
    void writeI8(long v)
    {
      write((byte) (v >> 56));
      write((byte) (v >> 48));
      write((byte) (v >> 40));
      write((byte) (v >> 32));
      write((byte) (v >> 24));
      write((byte) (v >> 16));
      write((byte) (v >>  8));
      write((byte)  v       );
    }

    void writeU2(int v)
    {
      if (PRECONDITIONS) require
        (0 <= v,
         v < 0x10000);

      write((byte) (v >> 8));
      write((byte)  v      );
    }

    void writeU1(int v)
    {
      if (PRECONDITIONS) require
        (0 <= v,
         v < 0x100);

      write((byte) v);
    }

    void write(byte v)
    {
      _b.write(v);
    }

    void write(byte[] v)
    {
      try
        {
          _b.write(v);
        }
      catch (IOException o)
        {
          throw new Error(o);
        }
    }

    void writeUTF(String s)
    {
      try
        {
          _o.writeUTF(s);
        }
      catch (IOException o)
        {
          throw new Error(o);
        }
    }
  }



  /**
   * The constant pool of a class. All CPool entries created will be added to
   * this pool and check for duplicate entries.
   */
  static class CPool
  {

    // all the entries that were added.
    TreeMap<CPEntry, CPEntry> _entries = new TreeMap<>();

    // the entries as a list in the order they have indices assigned.
    List<CPEntry> _entriesList = new List<>();

    // the total number of slots taken so far, counting long/double entries
    // twice.
    int _totalSlots = 1;

    // write the constant pool to o.
    void write(Kaku o)
    {
      o.writeU2(_totalSlots);
      for (var e : _entriesList)
        {
          o.writeU1(e.tag()._tag);
          e.write(o);
        }
    }

  }

  /**
   * Abstract CPool entry.
   */
  abstract class CPEntry implements Comparable<CPEntry>
  {
    // the index in the constant pool. Will be validated by every CPEntry handed
    // out by one of the cp* methods since they will all be added to _cpool.
    int _index = -1;

    CPEntry()
    {
    }

    /**
     * Add this to the surrounding _cpool and set index. If an equal entry
     * exists, return that entry.
     *
     * @return this or an equal entry that was already added.
     */
    CPEntry add()
    {
      var result = _cpool._entries.get(this);
      if (result == null)
        {
          if (CHECKS) check
            (!_finished);

          _cpool._entries.put(this, this);
          _cpool._entriesList.add(this);
          _index = _cpool._totalSlots;
          _cpool._totalSlots += slots();
          result = this;
        }
      return result;
    }


    public int compareTo(CPEntry other)
    {
      var t1 = tag()._tag;
      var t2 = other.tag()._tag;
      return
        t1 < t2 ? -1 :
        t1 > t2 ? +1
                : compareTo2(other);
    }


    /**
     * Tag of this entry
     */
    abstract CPoolTag tag();

    /**
     * write this entry's data to o.
     */
    abstract void write(Kaku o);

    /**
     * Compare two entries with the same tag. This is used by compareTo after it
     * was ensured that the tags of this and other are equal.
     */
    abstract int compareTo2(CPEntry other);


    /**
     * The index of this entry.
     */
    int index()
    {
      if (PRECONDITIONS) require
        (_index >= 0);

      return _index;
    }

    /**
     * Number of slots taken by this entry.
     */
    int slots()
    {
      return switch (tag())
        {
        case tag_long, tag_double -> 2;
        default -> 1;
        };
    }

  }

  /**
   * Utf8-String cpool entry
   *
   * NYI: UNDER DEVELOPMENT: These are modified utf8 strings and not standard utf8 strings. We need
   * to check the corner cases (0 bytes) where these to differ!
   */
  class CPUtf8 extends CPEntry
  {
    String _str;

    CPUtf8(byte[] utf8)
    {
      this(new String(utf8, StandardCharsets.UTF_8));

      if (PRECONDITIONS) require
        (utf8 != null);
    }

    CPUtf8(String str)
    {
      if (PRECONDITIONS) require
        (str != null);

      _str = str;
    }

    CPoolTag tag() { return CPoolTag.tag_utf8; }

    int compareTo2(CPEntry other)
    {
      return _str.compareTo(((CPUtf8)other)._str);
    }

    void write(Kaku o)
    {
      o.writeUTF(_str);
    }

    public String toString()
    {
      return _str;
    }
  }


  /**
   * Class reference
   */
  class CPClass extends CPEntry
  {
    final CPUtf8 _utf8;
    CPClass(CPUtf8 utf8)
    {
      _utf8 = utf8;
    }

    CPClass(String name)
    {
      this(cpUtf8(name));
    }

    CPClass(AType t)
    {
      this(t.refDescriptor());
    }

    CPoolTag tag() { return CPoolTag.tag_class; }

    int compareTo2(CPEntry other)
    {
      var u1 = _utf8;
      var u2 = ((CPClass) other)._utf8;
      return u1.compareTo(u2);
    }

    void write(Kaku o)
    {
      o.writeU2(_utf8.index());
    }

    public String toString()
    {
      return _utf8.toString();
    }
  }


  /**
   * Java string
   */
  class CPString extends CPEntry
  {
    final CPUtf8 _utf8;

    CPString(CPUtf8 utf8)
    {
      _utf8 = utf8;
    }

    CPString(String name)
    {
      this(cpUtf8(name));
    }

    CPString(byte[] name)
    {
      this(cpUtf8(name));
    }

    CPoolTag tag() { return CPoolTag.tag_string; }

    int compareTo2(CPEntry other)
    {
      var u1 = _utf8;
      var u2 = ((CPString) other)._utf8;
      return u1.compareTo(u2);
    }

    void write(Kaku o)
    {
      o.writeU2(_utf8.index());
    }
  }


  /**
   * Name-and-Type entry
   */
  class CPNameAndType extends CPEntry
  {
    final CPUtf8 _name, _type;

    CPNameAndType(CPUtf8 name, CPUtf8 type)
    {
      _name = name;
      _type = type;
    }

    CPNameAndType(String name, String type)
    {
      this(cpUtf8(name), cpUtf8(type));
    }

    CPoolTag tag() { return CPoolTag.tag_name_and_type; }

    int compareTo2(CPEntry other)
    {
      var n1 = _name;
      var t1 = _type;
      var n2 = ((CPNameAndType) other)._name;
      var t2 = ((CPNameAndType) other)._type;
      var rn = n1.compareTo(n2);
      var rt = t1.compareTo(t2);
      return rn != 0 ? rn : rt;
    }

    void write(Kaku o)
    {
      o.writeU2(_name.index());
      o.writeU2(_type.index());
    }

  }


  /**
   * Field reference
   */
  class CPField extends CPEntry
  {

    final CPClass _class;

    final CPNameAndType _nat;

    CPField(CPClass c, CPNameAndType nat)
    {
      _class = c;
      _nat = nat;
    }

    CPoolTag tag() { return CPoolTag.tag_field_ref; }

    int compareTo2(CPEntry other)
    {
      var c1 = _class;
      var n1 = _nat;
      var c2 = ((CPField) other)._class;
      var n2 = ((CPField) other)._nat;
      var rc = c1.compareTo(c2);
      var rn = n1.compareTo(n2);
      return rc != 0 ? rc : rn;
    }

    void write(Kaku o)
    {
      o.writeU2(_class.index());
      o.writeU2(_nat.index());
    }
  }


  /**
   * Method reference
   */
  class CPMethod extends CPEntry
  {

    final CPClass _class;

    final CPNameAndType _nat;

    CPMethod(CPClass c, CPNameAndType nat)
    {
      _class = c;
      _nat = nat;
    }

    CPoolTag tag() { return CPoolTag.tag_method_ref; }

    int compareTo2(CPEntry other)
    {
      var c1 = _class;
      var n1 = _nat;
      var c2 = ((CPMethod) other)._class;
      var n2 = ((CPMethod) other)._nat;
      var rc = c1.compareTo(c2);
      var rn = n1.compareTo(n2);
      return rc != 0 ? rc : rn;
    }

    void write(Kaku o)
    {
      o.writeU2(_class.index());
      o.writeU2(_nat.index());
    }

  }


  /**
   * Interface method reference
   */
  class CPInterfaceMethod extends CPEntry
  {

    final CPClass _class;

    final CPNameAndType _nat;

    CPInterfaceMethod(CPClass c, CPNameAndType nat)
    {
      _class = c;
      _nat = nat;
    }

    CPoolTag tag() { return CPoolTag.tag_interface_method_ref; }

    int compareTo2(CPEntry other)
    {
      var c1 = _class;
      var n1 = _nat;
      var c2 = ((CPInterfaceMethod) other)._class;
      var n2 = ((CPInterfaceMethod) other)._nat;
      var rc = c1.compareTo(c2);
      var rn = n1.compareTo(n2);
      return rc != 0 ? rc : rn;
    }

    void write(Kaku o)
    {
      o.writeU2(_class.index());
      o.writeU2(_nat.index());
    }

  }


  /**
   * Integer constant
   */
  class CPInteger extends CPEntry
  {

    final int _value;

    CPInteger(int value)
    {
      _value = value;
    }

    CPoolTag tag() { return CPoolTag.tag_integer; }

    int compareTo2(CPEntry other)
    {
      var v1 = _value;
      var v2 = ((CPInteger) other)._value;
      return
        v1 < v2 ? -1 :
        v1 > v2 ? +1 : 0;
    }

    void write(Kaku o)
    {
      o.writeI4(_value);
    }
  }


  /**
   * long constant
   */
  class CPLong extends CPEntry
  {

    final long _value;

    CPLong(long value)
    {
      _value = value;
    }

    CPoolTag tag() { return CPoolTag.tag_long; }

    int compareTo2(CPEntry other)
    {
      var v1 = _value;
      var v2 = ((CPLong) other)._value;
      return
        v1 < v2 ? -1 :
        v1 > v2 ? +1 : 0;
    }

    void write(Kaku o)
    {
      o.writeI8(_value);
    }
  }


  /**
   * float constant
   */
  class CPFloat extends CPEntry
  {

    final float _value;
    final int _bits;

    CPFloat(float value, int bits)
    {
      _value = value;
      _bits  = bits;
    }
    CPFloat(float value) { this(value, Float.floatToIntBits(value)); }
    CPFloat(int   value) { this(Float.intBitsToFloat(value), value);  }

    CPoolTag tag() { return CPoolTag.tag_float; }

    int compareTo2(CPEntry other)
    {
      var v1 = _bits;
      var v2 = ((CPFloat) other)._bits;
      return
        v1 < v2 ? -1 :
        v1 > v2 ? +1 : 0;
    }

    void write(Kaku o)
    {
      o.writeI4(_bits);
    }

  }


  /**
   * double constant
   */
  class CPDouble extends CPEntry
  {

    final double _value;
    final long _bits;

    CPDouble(double value, long bits)
    {
      _value = value;
      _bits  = bits;
    }
    CPDouble(double value) { this(value, Double.doubleToLongBits(value)); }
    CPDouble(long   value) { this(Double.longBitsToDouble(value), value); }

    CPoolTag tag() { return CPoolTag.tag_double; }

    int compareTo2(CPEntry other)
    {
      var v1 = _bits;
      var v2 = ((CPDouble) other)._bits;
      return
        v1 < v2 ? -1 :
        v1 > v2 ? +1 : 0;
    }

    void write(Kaku o)
    {
      o.writeI8(_bits);
    }

  }


  /**
   * Method to create instances of CPEntry and directly add them to this class' _cpool:
   */
  CPUtf8            cpUtf8           (byte[] u                  ) { return (CPUtf8           ) (new CPUtf8           (u)         ).add(); }
  CPUtf8            cpUtf8           (String s                  ) { return (CPUtf8           ) (new CPUtf8           (s)         ).add(); }
  CPInteger         cpInteger        (int    v                  ) { return (CPInteger        ) (new CPInteger        (v)         ).add(); }
  CPLong            cpLong           (long   v                  ) { return (CPLong           ) (new CPLong           (v)         ).add(); }
  CPFloat           cpFloat          (float  v                  ) { return (CPFloat          ) (new CPFloat          (v)         ).add(); }
  CPFloat           cpFloat          (int    v                  ) { return (CPFloat          ) (new CPFloat          (v)         ).add(); }
  CPDouble          cpDouble         (double v                  ) { return (CPDouble         ) (new CPDouble         (v)         ).add(); }
  CPDouble          cpDouble         (long   v                  ) { return (CPDouble         ) (new CPDouble         (v)         ).add(); }
  CPString          cpString         (String s                  ) { return (CPString         ) (new CPString         (s)         ).add(); }
  CPString          cpString         (byte[] s                  ) { return (CPString         ) (new CPString         (s)         ).add(); }
  CPNameAndType     cpNameAndType    (CPUtf8 name, CPUtf8 type  ) { return (CPNameAndType    ) (new CPNameAndType    (name, type)).add(); }
  CPNameAndType     cpNameAndType    (String name, String type  ) { return (CPNameAndType    ) (new CPNameAndType    (name, type)).add(); }
  CPClass           cpClass          (CPUtf8 name               ) { return (CPClass          ) (new CPClass          (name      )).add(); }
  CPClass           cpClass          (String name               ) { return (CPClass          ) (new CPClass          (name      )).add(); }
  CPClass           cpClass          (AType  t                  ) { return (CPClass          ) (new CPClass          (t         )).add(); }
  CPField           cpField          (CPClass c, CPNameAndType n) { return (CPField          ) (new CPField          (c, n      )).add(); }
  CPMethod          cpMethod         (CPClass c, CPNameAndType n) { return (CPMethod         ) (new CPMethod         (c, n      )).add(); }
  CPInterfaceMethod cpInterfaceMethod(CPClass c, CPNameAndType n) { return (CPInterfaceMethod) (new CPInterfaceMethod(c, n      )).add(); }


  /**
   * A field or method
   */
  class Member
  {
    final int _access_flags;
    final String _nameStr;
    final CPUtf8 _name;
    final CPUtf8 _descriptor;
    final List<Attribute> _attributes;

    Member(int access_flags,
           String name,
           String descriptor,
           List<Attribute> attributes)
    {
      _access_flags = access_flags;
      _nameStr = name;
      _name = cpUtf8(name);
      _descriptor = cpUtf8(descriptor);
      _attributes = attributes;
    }

    String name()
    {
      return _nameStr;
    }

    void write(Kaku o)
    {
      o.writeU2(_access_flags);
      o.writeU2(_name.index());
      o.writeU2(_descriptor.index());
      o.writeU2(_attributes.size());
      for (var a : _attributes)
        {
          a.write(o);
        }
    }
  }


  /**
   * A field
   */
  class Field extends Member
  {
    Field(int access_flags,
           String name,
           String descriptor,
           List<Attribute> attributes)
    {
      super(access_flags, name, descriptor, attributes);

      if (PRECONDITIONS) require
        ((access_flags & ~METHOD_ACCESS_FLAGS) == 0,
         !descriptor.equals("V"));
    }
  }

  /**
   * A method
   */
  class Method extends Member
  {
    Method(int access_flags,
           String name,
           String descriptor,
           List<Attribute> attributes)
    {
      super(access_flags, name, descriptor, attributes);

      if (PRECONDITIONS) require
        ((access_flags & ~METHOD_ACCESS_FLAGS) == 0,
         ClassFileConstants.argTypesFromDescriptor(descriptor).noneMatch(x-> x == ClassFileConstants.PrimitiveType.type_void));
    }
  }


  /**
   * Abstract attribute
   */
  public abstract class Attribute
  {
    final CPUtf8 _name;

    Attribute(String name)
    {
      _name = cpUtf8(name);
    }

    abstract byte[] data();

    void write(Kaku o)
    {
      o.writeU2(_name.index());
      var d = data();
      o.writeU4(d.length);
      o.write(d);
    }
  }


  /**
   * StackMapTable-Attribute
   *
   * See section #4.7.4 in https://docs.oracle.com/javase/specs/jvms/se21/jvms21.pdf
   */
  public class StackMapTable extends Attribute
  {

    /**
     * The stackmap frames of this table.
     */
    Set<StackMapFullFrame> stackMapFrames;

    /**
     * The state of the stack at bytecode position. Saved during buildStackMapTable.
     * Note: long and double occupy only one stack slot.
     */
    final Map<Integer, Stack<VerificationType>> stacks = new TreeMap<>();


    /**
     * The state of locals at bytecode positions that are found during buildStackMapTable.
     * Note: long and double always occupy two list entries.
     */
    final List<Pair<Integer, List<VerificationType>>> locals = new List<>();


    /**
     * The code for which to build this table.
     */
    private Expr _code;

    private int _max_stack = 0;

    private int _max_locals = 0;


    /**
     * @param initialLocals the initial state of the locals when
     * this method starts executing.
     *
     * @param code the code for which to build this stackmap table.
     *
     */
    StackMapTable(List<VerificationType> initialLocals, Expr code)
    {
      super("StackMapTable");
      this._code = code;
      stacks.put(0, new Stack<VerificationType>() {
        @Override
        public VerificationType push(VerificationType item)
        {
          var result = item == null ? null: super.push(item);
          _max_stack = Math.max(_max_stack, this.stream().mapToInt(vti -> vti.needsTwoSlots() ? 2 : 1).sum());
          return result;
        }
      });
      _max_locals = initialLocals.size();
      locals.add(new Pair<>(0, initialLocals));
    }

    /**
     * The data of this attribute:
     * - u2 number_of_entries;
     * - stack_map_frame entries[number_of_entries];
     */
    @Override
    byte[] data()
    {
      var o = new Kaku();
      // there may be a stackmapframe after bytecode
      // which was added by endless_loop but this stackmapframe
      // is only valid if there actually is bytecode after endless_loop.
      var byteCodeSize = byteCodeSize();
      var smfs = stackMapFrames.stream().filter(x -> x.byteCodePos < byteCodeSize).toList();
      o.writeU2(smfs.size());
      // NYI: PERFORMANCE: optimization potential
      // currently we write full frames only
      // we could use the other frame types as well:
      // - same_frame
      // - same_locals_1_stack_item_frame
      // - same_locals_1_stack_item_frame_extended
      // - chop_frame
      // - same_frame_extended
      // - append_frame
      for (var s : smfs)
        {
          s.write(o);
        }
      return o._b.toByteArray();
    }


    /**
     * @return the size of the java bytecode
     */
    private int byteCodeSize()
    {
      var o = new ClassFile.Kaku();
      var bcw = new ClassFile.ByteCodeWrite("", o);
      _code.code(bcw, ClassFile.this);
      return o._b.toByteArray().length;
    }


    /*
     * evaluate the code and build this stackmap table.
     */
    private void build()
    {
      if (stackMapFrames == null)
        {
          stackMapFrames = new TreeSet<>();
          stackMapFrames.add(new StackMapFullFrame(StackMapTable.this, 0));
          _code.buildStackMapTable(
            this,
            Expr.clone(stacks.get(0)),
            locals.get(0).v1().clone()
          );
        }
    }


    /**
     * @return A union all locals states that have been found for {@code byteCodePos}.
     */
    public List<VerificationType> unifiedLocals(int byteCodePos)
    {
      var result = locals
        .stream()
        .filter(x -> x.v0() == byteCodePos)
        .map(x -> x.v1())
        .reduce(null, (a, b) -> a == null ? b : VerificationType.union(a, b));

      if (POSTCONDITIONS) ensure
        (result != null);

      return result;
    }


    /*
     * The offset of this frame in the set of stackmap frames.
     */
    public int offset(StackMapFullFrame s)
    {
      return s.byteCodePos == 0
        ? 0
        : s.byteCodePos
          - stackMapFrames
            .stream()
            .filter(x -> x.byteCodePos < s.byteCodePos)
            .max(Comparator.comparingInt(x -> x.byteCodePos))
            .get()
            .byteCodePos
          - 1;
    }

    /**
     * @return the class file this table is part of
     */
    public ClassFile classFile()
    {
      return ClassFile.this;
    }


    /**
     * static initializer for an empty table.
     */
    public static StackMapTable empty(ClassFile cf, List<VerificationType> argsLocals, Expr code)
    {
      return cf.new StackMapTable(argsLocals, code)
        {
          @Override
          byte[] data()
          {
            var o = new Kaku();
            o.writeU2(0);
            return o._b.toByteArray();
          }
        };
    }


    /**
     * @param cf the class file the stackmap table belongs to
     * @param argsLocals the initial locals
     * @param code the code for which to build this table.
     * @return
     */
    public static StackMapTable fromCode(ClassFile cf, List<VerificationType> argsLocals, Expr code)
    {
      return cf.new StackMapTable(argsLocals, code);
    }

    public void updateMaxLocal(int n)
    {
      _max_locals = Math.max(_max_locals, n);
    }

    public int max_stack()
    {
      return _max_stack;
    }

    public int max_locals()
    {
      return _max_locals;
    }

  }


  /**
   * line number attribute table as described in §4.7.12 of JVM-Spec
   */
  class LineNumberTableAttribute extends Attribute
  {
    /**
     * list of pairs of:
     *   1) valid index into the code array (start_pc)
     *   2) line number
     */
    private List<Pair<Integer, Integer>> _lnt = null;
    private final Expr _code;

    LineNumberTableAttribute(Expr code)
    {
      super("LineNumberTable");
      this._code = code;
    }

    @Override
    byte[] data()
    {
      // u2 line_number_table_length;
      // { u2 start_pc;
      //   u2 line_number;
      // }
      var o = new Kaku();
      o.writeU2(_lnt.size());
      for (var lnte : _lnt)
        {
          o.writeU2(lnte.v0());
          o.writeU2(lnte.v1());
        }
      return o._b.toByteArray();
    }

    public void build()
    {
      if (_lnt == null)
        {
          _lnt = new List<>();
          _code.buildLineNumberTable(ClassFile.this, _lnt, new int[]{0});
        }
    }
  }


  /**
   * code attribute for methods
   */
  class CodeAttribute extends Attribute
  {
    final String _where;
    final ByteCode _code;
    final List<Attribute> _attributes;
    int _size;
    private final StackMapTable _smt;
    private final LineNumberTableAttribute _lnta;
    CodeAttribute(String where,
                  ByteCode code,
                  List<Attribute> attributes,
                  StackMapTable smt,
                  LineNumberTableAttribute lnta)
    {
      super("Code");
      this._where = where;
      this._code = code;
      this._attributes = attributes;
      this._smt = smt;
      this._lnta = lnta;
      var be = new ByteCodeSizeEstimate(_where   ); _code.code(be, ClassFile.this);
      var bf = new ByteCodeFixLabels   (_where   ); _code.code(bf, ClassFile.this);
      _size = bf.size();
      _attributes.addAll(_smt, _lnta);
    }

    byte[] data()
    {
      _smt.build();
      _lnta.build();
      var o = new Kaku();
      o.writeU2(_smt.max_stack());
      o.writeU2(_smt.max_locals());
      o.writeU4(_size);
      var ba = new ByteCodeWrite(_where, o);
      _code.code(ba, ClassFile.this);
      if (ba._exceptionTable == null)
        {
          o.writeU2(0);
        }
      else
        {
          o.writeU2(ba._exceptionTable.size());
          for (var e : ba._exceptionTable)
            {
              o.writeU2(e._posFinal);
              o.writeU2(e._end._posFinal);
              o.writeU2(e._handler._posFinal);
              o.writeU2(cpClass(e._type)._index);
            }
        }
      o.writeU2(_attributes.size());
      for (var a : _attributes)
        {
          a.write(o);
        }
      return o._b.toByteArray();
    }
  }


  /**
   * Exception table entry as part of the CodeAttribute
   */
  class ExceptionTableEntry
  {
    final int _start_pc, _end_pc, _handler_pc, _catch_pc;
    ExceptionTableEntry(int start_pc,
                        int end_pc,
                        int handler_pc,
                        int catch_pc)
    {
      this._start_pc   = start_pc;
      this._end_pc     = end_pc;
      this._handler_pc = handler_pc;
      this._catch_pc   = catch_pc;
    }
  }

  /*
   * https://docs.oracle.com/javase/specs/jvms/se21/jvms21.pdf
   * §4.7.10 "The SourceFile attribute is an optional fixed-length attribute in the attributes
   * table of a ClassFile structure (§4.1).
   * There may be at most one SourceFile attribute in the attributes table of a
   * ClassFile structure."
   */
  public class SourceFileAttribute extends Attribute {

    private CPEntry _srcFile;

    SourceFileAttribute(String srcFile)
    {
      super("SourceFile");
      this._srcFile = cpUtf8(srcFile);
    }

    @Override
    byte[] data()
    {
      var o = new Kaku();
      o.writeU2(_srcFile.index());
      return o._b.toByteArray();
    }

  }


  /*----------------------------  constants  ----------------------------*/


  // constants inherited from interface ClassFileConstants!


  /*----------------------------  variables  ----------------------------*/

  private final FuzionOptions _opt;

  public final String _name;
  public final ClassType _type;

  public final byte[] _version;

  public final CPool _cpool;

  public final int _flags;

  public final CPClass _this;
  public final CPClass _super;

  final List<CPClass> _interfaces = new List<>();
  final List<Field> _fields = new List<>();
  final List<Method> _methods = new List<>();
  final List<Attribute> _attributes = new List<>();

  /**
   * True if this class was finished by a call to finish().
   */
  boolean _finished = false;

  /**
   * static initializer code or null if none. Modified via calls to addToClInit.
   */
  Expr _clinitCode = null;

  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create class file with given class name.
   *
   * @param name the class name
   */
  public ClassFile(FuzionOptions opt, String name, String supr, String srcFile)
  {
    this(opt, name, supr, false, srcFile);
  }


  /**
   * Create class file with given class name.
   *
   * @param name the class name
   */
  public ClassFile(FuzionOptions opt, String name, String supr, boolean interfce, String srcFile)
  {
    _opt = opt;
    _name = name;
    _type = new ClassType(name);
    _version = DEFAULT_VERSION;
    _cpool = new CPool();
    _flags = ACC_PUBLIC | (interfce ? (ACC_INTERFACE|ACC_ABSTRACT) : ACC_SUPER);
    _this = cpClass(name);
    _super = cpClass(supr == null ? "java/lang/Object" : supr);
    _attributes.add(new SourceFileAttribute(srcFile));
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * That path of this class file relative to the classpath directory it should
   * be contained in.
   */
  public Path classFile()
  {
    // NYI: UNDER DEVELOPMENT: no support for packages yet!
    return Path.of(_name + ".class");
  }


  /**
   * Write this class file out to dir.
   */
  public void write(Path dir) throws IOException
  {
    var fp = dir.resolve(classFile());
    _opt.verbosePrintln(2, " + " + fp);
    Files.write(fp, bytes());
  }


  /**
   * Write this class file out to the given JarOutputStream.
   */
  public void write(JarOutputStream jos) throws IOException
  {
    jos.putNextEntry(new JarEntry(_name + ".class"));
    jos.write(bytes());
  }


  /**
   * Get the bytes of this class file as an array.
   */
  public byte[] bytes()
  {
    finish();
    var o = new Kaku();
    o.write(MAGIC);
    o.write(_version);
    _cpool.write(o);
    o.writeU2(_flags);
    o.writeU2(_this.index());
    o.writeU2(_super.index());
    o.writeU2(_interfaces.size());
    for (var i : _interfaces)
      {
        o.writeU2(i.index());
      }
    o.writeU2(_fields.size());
    for (var f : _fields)
      {
        f.write(o);
      }
    o.writeU2(_methods.size());
    for (var m : _methods)
      {
        m.write(o);
      }
    o.writeU2(_attributes.size());
    for (var a : _attributes)
      {
        a.write(o);
      }
    return o._b.toByteArray();
  }


  /**
   * Add an interface implemented by this class file.
   */
  public void addImplements(String name)
  {
    if (!_interfaces.contains(cpClass(name)))
      {
        _interfaces.add(cpClass(name));
      }
  }


  /**
   * Add a method declared in this class file.
   */
  public void method(int access_flags,
                     String name,
                     String descr,
                     List<Attribute> attributes)
  {
    _methods.add(new Method(access_flags, name, descr, attributes));
  }


  /**
   * Check if this class file declares a method with the given name.
   */
  public boolean hasMethod(String name)
  {
    for (var m : _methods)
      {
        if (m.name().equals(name))
          {
            return true;
          }
      }
    return false;
  }


  /**
   * Check if this class file declares a field with the given name.
   */
  public boolean hasField(String name)
  {
    for (var f : _fields)
      {
        if (f.name().equals(name))
          {
            return true;
          }
      }
    return false;
  }


  /**
   * create a code attribute to be used in this class file.
   */
  public CodeAttribute codeAttribute(String where,
                                     Expr code,
                                     List<Attribute> attributes,
                                     StackMapTable smt)
  {
    return new CodeAttribute(where,
                             code,
                             attributes,
                             smt,
                             new LineNumberTableAttribute(code));
  }


  /**
   * Add a field declared in this class file.
   */
  public void field(int access_flags,
                    String name,
                    String descr,
                    List<Attribute> attributes)
  {
    _fields.add(new Field(access_flags, name, descr, attributes));
  }


  /**
   * Add a field declared in this class file.
   */
  public void field(int access_flags,
                    String name,
                    String descr)
  {
    field(access_flags, name, descr, new List<>());
  }


  /**
   * Get the type of this class as a ClassType instance
   */
  public ClassType classType()
  {
    return _type;
  }


  /**
   * Add given code to the static initializer of this clazz.
   *
   * @param code the code to add.
   */
  public void addToClInit(Expr code)
  {
    if (PRECONDITIONS) require
      (!_finished);

    if (code != Expr.UNIT)
      {
        _clinitCode = _clinitCode == null ? code
                                          : _clinitCode.andThen(code);
      }
  }


  /**
   * Finish this clazz and prepare it for writing. This will add a static
   * initializer if code was added via addToClInit.
   */
  private void finish()
  {
    if (PRECONDITIONS) require
      (!_finished);

    var bc_clinit = _clinitCode;
    if (bc_clinit != null)
      {
        bc_clinit = bc_clinit
          .andThen(Expr.RETURN);
        var code_clinit = codeAttribute("<clinit> in class for " + _name,
                                        bc_clinit, new List<>(), StackMapTable.empty(this, new List<>(), bc_clinit));
        method(ACC_PUBLIC | ACC_STATIC, "<clinit>", "()V", new List<>(code_clinit));
      }

    // Doing this for the sake of side effects.
    // This will sometimes add things to constant pool
    // for description of stackmapframe.
    // NYI: UNDER DEVELOPMENT: we could only evaluate for stackmapframes...
    // instead of simulating writing of whole bytecode
    for (var m : _methods)
      {
        for (var a : m._attributes)
        {
          a.data();
        }
      }

    _finished = true;
  }


  /**
   * String representation for debugging.
   */
  public String toString()
  {
    return "ClassFile instance for class '" + _name + "' to be saved to '" + classFile() + "'";
  }


}

/* end of file */
