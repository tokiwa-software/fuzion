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


import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;


/**
 * ClassFile provides means to synthesize a Java class file.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ClassFile extends ANY implements ClassFileConstants
{


  /*-----------------------------  classes  -----------------------------*/

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

    private void writeU4(int v)
    {
      write((byte) (v >> 24));
      write((byte) (v >> 16));
      write((byte) (v >>  8));
      write((byte)  v       );
    }
    private void writeI4(int v)
    {
      write((byte) (v >> 24));
      write((byte) (v >> 16));
      write((byte) (v >>  8));
      write((byte)  v       );
    }
    private void writeI8(long v)
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

    private void writeU2(int v)
    {
      if (PRECONDITIONS) require
        (0 <= v,
         v < 0x10000);

      write((byte) (v >> 8));
      write((byte)  v      );
    }

    private void writeU1(int v)
    {
      if (PRECONDITIONS) require
        (0 <= v,
         v < 0x100);

      write((byte) v);
    }

    private void write(byte v)
    {
      _b.write(v);
    }

    private void write(byte[] v)
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

    private void writeUTF(String s)
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
    // the index in the constant pool. Will be valied by every CPEntry handed
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
     * was ensured that the tags of this and other are equa.
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
   * NYI: These are modified utf8 strings and not standard utf8 strings. We need
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
      add();
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
      add();
    }

    CPClass(String name)
    {
      this(cpUtf8(name));
    }

    CPClass(AType t)
    {
      this(t.descriptor2());
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
      add();
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
      add();
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
      add();
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
      add();
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
      add();
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
      add();
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
      add();
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
      add();
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
      add();
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
  abstract class Attribute
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
   * code attribute for methods
   */
  class CodeAttribute extends Attribute
  {
    final int _max_stack;
    final int _max_locals;
    final byte[] _code;
    final List<ExceptionTableEntry> _exception_table;
    final List<Attribute> _attributes;
    CodeAttribute(int max_stack,
                  int max_locals,
                  byte[] code,
                  List<ExceptionTableEntry> exception_table,
                  List<Attribute> attributes)
    {
      super("Code");
      this._max_stack = max_stack;
      this._max_locals = max_locals;
      this._code = code;
      this._exception_table = exception_table;
      this._attributes = attributes;
    }

    byte[] data()
    {
      var o = new Kaku();
      o.writeU2(_max_stack);
      o.writeU2(_max_locals);
      o.writeU4(_code.length);
      o.write(_code);
      o.writeU2(_exception_table.size());
      for (var e : _exception_table)
        {
          o.writeU2(e._start_pc);
          o.writeU2(e._end_pc);
          o.writeU2(e._handler_pc);
          o.writeU2(e._catch_pc);
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


  /*----------------------------  constants  ----------------------------*/


  // constants inherited from interface ClassFileConstants!


  /*----------------------------  variables  ----------------------------*/


  final boolean _verbose = false;  // NYI: initialize


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


  /*---------------------------  constructors  ---------------------------*/


  /**
   * Create class file with given class name.
   *
   * @param name the class name
   */
  public ClassFile(String name, String supr)
  {
    this(name, supr, false);
  }


  /**
   * Create class file with given class name.
   *
   * @param name the class name
   */
  public ClassFile(String name, String supr, boolean interfce)
  {
    _name = name;
    _type = new ClassType(name);
    _version = DEFAULT_VERSION;
    _cpool = new CPool();
    _flags = ACC_PUBLIC | (interfce ? ACC_INTERFACE : ACC_SUPER);
    _this = cpClass(name);
    _super = cpClass(supr == null ? "java/lang/Object" : supr);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * That path of this class file relative to the eclasspath directory it should
   * be contained in.
   */
  public Path classFile()
  {
    // NYI: no support for packages yet!
    return Path.of(_name + ".class");
  }


  /**
   * Write this class file out to dir.
   */
  public void write(Path dir) throws IOException
  {
    var fp = dir.resolve(classFile());

    if (_verbose) // NYI: Use Options.verbosePrintln?
      {
        System.out.println(" + " + fp);
      }
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
                                     ByteCode code,
                                     List<ExceptionTableEntry> exception_table,
                                     List<Attribute> attributes)
  {
    return codeAttribute(where,
                         code.max_stack(),
                         code.max_locals(),
                         code,
                         exception_table,
                         attributes);
  }


  /**
   * create a code attribute to be used in this class file.
   */
  public CodeAttribute codeAttribute(String where,
                                     int max_stack,
                                     int max_locals,
                                     ByteCode code,
                                     List<ExceptionTableEntry> exception_table,
                                     List<Attribute> attributes)
  {
    var bc = code.byteCode(this);
    if (bc.length > MAX_BYTECODE_LENGTH)
      {
        Errors.fatal("Code size of bytecode created "+(where == null ? "" : "for `" + where) + "` is " +
                     bc.length+", the maximum allowed length is " + MAX_BYTECODE_LENGTH + ".\n" +
                     "To solve this, you might simplify the code or split up that feature into several smaller features.");
      }
    return new CodeAttribute(max_stack,
                             max_locals,
                             code.byteCode(this),
                             exception_table,
                             attributes);
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
   * Get the type of this class as a ClassType instance
   */
  public ClassType classType()
  {
    return _type;
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
