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
 * Source of class CTypes
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import java.util.TreeSet;

import dev.flang.fuir.FUIR;

import dev.flang.util.ANY;


/**
 * CTypes provides methods to handle C types
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class CTypes extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The intermediate code we are compiling.
   */
  private final FUIR _fuir;


  /**
   * The C backend
   */
  private CNames _names;


  /**
   * Set of clazz ids for all the clazzes whose structs have been declared
   * already.  Structs are declared recursively with structs of inner fields
   * declared before outer structs.  This set keeps track which structs have been
   * declared already to avoid duplicates.
   */
  private final TreeSet<Integer> _declaredStructs = new TreeSet<>();


  /*---------------------------  consructors  ---------------------------*/


  /**
   * Create instance of CTypes
   */
  public CTypes(FUIR fuir, CNames names)
  {
    this._fuir = fuir;
    this._names = names;
  }


  /*-----------------------------  methods  -----------------------------*/



  /**
   * The type of a value of the given clazz.
   */
  String clazz(int cl)
  {
    return _names.struct(cl) + (_fuir.clazzIsRef(cl) ? "*" : "");
  }


  /**
   * Test is a given clazz is not -1 and stores data.
   *
   * @param cl the clazz defining a type, may be -1
   *
   * @return true if cl != -1 and not unit or void type.
   */
  boolean hasData(int cl)
  {
    return cl != -1 &&
      !_fuir.clazzIsUnitType(cl) &&
      !_fuir.clazzIsVoidType(cl) &&
      cl != _fuir.clazzUniverse();
  }


  /**
   * The type of a field.  This is the usually the same as clazz() of
   * the field's result clazz, except for outer refs for which
   * clazzFieldIsAdrOfValue, where it is a pointer to that type.
   */
  String clazzField(int cf)
  {
    var rc = _fuir.clazzResultClazz(cf);
    return (_fuir.clazzIsVoidType(rc)
            ? "struct { }"
            : clazz(rc)) + (_fuir.clazzFieldIsAdrOfValue(cf) ? "*" : "");
  }



  /**
   * Create declarations of the C types required for the given clazz.
   *
   * @param cl a clazz id.
   *
   * @return the C declaration or CStmnt.EMPTY if none.
   */
  CStmnt types(int cl)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Choice:
      case Routine:
        var name = _names.struct(cl);
        // special handling of stdlib clazzes known to the compiler
        var stype = scalar(cl);
        var type = stype != null ? stype : "struct " + name;
        return CStmnt.typedef(type, name);
      default:
        return CStmnt.EMPTY;
      }
  }


  /**
   * Does the given clazz specify a scalar type in the C code, i.e, standard
   * numeric types i32, u64, etc.
   */
  boolean isScalar(int cl)
  {
    return scalar(cl) != null;
  }


  /**
   * Check if the given clazz specifies a scalar type in the C code, i.e,
   * standard numeric types i32, u64, etc. If so, return that C type.
   *
   * @return the C scalar type corresponding to cl, null if cl is not scaler.
   */
  String scalar(int cl)
  {
    return
      _fuir.clazzIsI32(cl) ? "int32_t" :
      _fuir.clazzIsI64(cl) ? "int64_t" :
      _fuir.clazzIsU32(cl) ? "uint32_t" :
      _fuir.clazzIsU64(cl) ? "uint64_t" : null;
  }


  /**
   * Create declarations of the C structs required for the given clazz.  Write
   * code to cf.
   *
   * @param cl a clazz id.
   */
  void structs(int cl, CFile cf)
  {
    switch (_fuir.clazzKind(cl))
      {
      case Choice:
      case Routine:
        {
          if (!_declaredStructs.contains(cl))
            {
              _declaredStructs.add(cl);
              if (!isScalar(cl)) // special handling of stdlib clazzes known to the compiler
                {
                  // first, make sure structs used for inner fields are declared:
                  for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                    {
                      var f = _fuir.clazzField(cl, i);
                      var rcl = _fuir.clazzResultClazz(f);
                      if (!_fuir.clazzIsRef(rcl))
                        {
                          structs(rcl, cf);
                        }
                    }
                  for (int i = 0; i < _fuir.clazzNumChoices(cl); i++)
                    {
                      var cc = _fuir.clazzChoice(cl, i);
                      if (!_fuir.clazzIsRef(cc))
                        {
                          structs(cc, cf);
                        }
                      else
                        {
                          structs(_fuir.clazzObject(), cf);
                        }
                    }
                  if (_fuir.clazzIsRef(cl))
                    {
                      structs(_fuir.clazzAsValue(cl), cf);
                    }

                  // next, declare the struct itself
                  cf.print
                    ("// for " + _fuir.clazzAsString(cl) + "\n" +
                     "struct " + _names.struct(cl) + " {\n");
                  if (_fuir.clazzIsRef(cl))
                    {
                      var vcl = _fuir.clazzAsValue(cl);
                      cf.print("  uint32_t clazzId;\n" +
                               "  " + clazz(vcl) + " " + _names.FIELDS_IN_REF_CLAZZ + ";\n");
                    }
                  else if (_fuir.clazzIsChoice(cl))
                    {
                      var ct = _fuir.clazzChoiceTag(cl);
                      if (ct != -1)
                        {
                          String type = clazzField(ct);
                          cf.print(" " + type + " " + _names.TAG_NAME + ";\n");
                        }
                      cf.print(" union {\n");
                      for (int i = 0; i < _fuir.clazzNumChoices(cl); i++)
                        {
                          var cc = _fuir.clazzChoice(cl, i);
                          if (!_fuir.clazzIsRef(cc))
                            {
                              String type = clazz(cc);
                              cf.print("  " + type + " " + _names.CHOICE_ENTRY_NAME + i + ";\n");
                            }
                        }
                      if (_fuir.clazzIsChoiceWithRefs(cl))
                        {
                          cf.print("  " + clazz(_fuir.clazzObject()) + " " + _names.CHOICE_REF_ENTRY_NAME + ";\n");
                        }
                      cf.print(" } " + _names.CHOICE_UNION_NAME + ";\n");
                    }
                  else
                    {
                      for (int i = 0; i < _fuir.clazzNumFields(cl); i++)
                        {
                          var f = _fuir.clazzField(cl, i);
                          String type = clazzField(f);
                          cf.print(" " + type + " " + _names.fieldName(f) + ";\n");
                        }
                    }
                  cf.print
                    ("};\n\n");
                  if (cl == _fuir.clazzUniverse())
                    {
                      cf.print
                        (CStmnt.decl("static", _names.struct(cl), _names.UNIVERSE));
                    }
                }
            }
        }
        break;
      default:
        break;
      }
  }



}

/* end of file */
