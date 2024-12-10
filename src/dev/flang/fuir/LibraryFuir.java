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
 * Source of class LibraryFuir
 *
 *---------------------------------------------------------------------*/

package dev.flang.fuir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import dev.flang.util.SourcePosition;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;

public class LibraryFuir extends FUIR {

  private int _mainClazz;
  private ClazzRecord[] _clazzes;
  private int[] _specialClazzes;
  private SiteRecord[] _sites;

  public LibraryFuir(byte[] data)
  {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
      this._mainClazz = ois.readInt();
      this._clazzes = (ClazzRecord[]) ois.readObject();
      this._sites = (SiteRecord[]) ois.readObject();
      this._specialClazzes = (int[]) ois.readObject();
    }
    catch(ClassNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private int siteCount()
  {
    return _sites.length;
  }


  @Override
  public int firstClazz()
  {
    return CLAZZ_BASE;
  }
  @Override
  public int lastClazz()
  {
    return CLAZZ_BASE+_clazzes.length-1;
  }
  @Override
  public int mainClazz()
  {
    return _mainClazz;
  }

  @Override
  public FeatureKind clazzKind(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzKind();
  }

  @Override
  public String clazzBaseName(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzBaseName();
  }

  @Override
  public int clazzResultClazz(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzResultClazz();
  }

  @Override
  public String clazzOriginalName(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzOriginalName();
  }

  @Override
  public String clazzAsString(int cl)
  {
    return cl == NO_CLAZZ ? "-- no clazz --" : clazzOriginalName(cl);
  }

  @Override
  public String clazzAsStringHuman(int cl)
  {
    return  cl == NO_CLAZZ ? "-- no clazz --" : _clazzes[clazzId2num(cl)].clazzAsStringHuman();
  }

  @Override
  public int clazzOuterClazz(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzOuterClazz();
  }

  @Override
  public int clazzNumFields(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzFields().length;
  }

  @Override
  public int clazzField(int cl, int i)
  {
    return _clazzes[clazzId2num(cl)].clazzFields()[i];
  }

  @Override
  public boolean clazzIsOuterRef(int cl)
  {
    // NYI: UNDER DEVELOPMENT:
    return clazzBaseName(cl).startsWith(FuzionConstants.OUTER_REF_PREFIX);
  }

  @Override
  public boolean clazzFieldIsAdrOfValue(int fcl)
  {
    return _clazzes[clazzId2num(fcl)].clazzFieldIsAdrOfValue();
  }

  @Override
  public int fieldIndex(int field)
  {
    check(clazzKind(field) == FeatureKind.Field);
    return clazzId2num(field);
  }

  @Override
  public boolean clazzIsChoice(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzIsChoice();
  }

  @Override
  public int clazzNumChoices(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzChoices().length;
  }

  @Override
  public int clazzChoice(int cl, int i)
  {
    return _clazzes[clazzId2num(cl)].clazzChoices()[i];
  }

  @Override
  public boolean clazzIsChoiceWithRefs(int cl)
  {
    // NYI move to FUIR
    for (var c : clazzChoices(cl))
    {
      if (clazzIsRef(c))
        {
          return true;
        }
    }
  return false;
  }

  @Override
  public boolean clazzIsChoiceOfOnlyRefs(int cl)
  {
    // NYI move to FUIR
    for (var c : clazzChoices(cl))
      {
        if (!clazzIsRef(c))
          {
            return false;
          }
      }
    return true;
  }

  @Override
  public int[] clazzInstantiatedHeirs(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzInstantiatedHeirs();
  }

  @Override
  public int clazzArgCount(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzArgs().length;
  }

  @Override
  public int clazzArgClazz(int cl, int arg)
  {
    // NYI move to fuir
    return clazzResultClazz(clazzArg(cl, arg));
  }

  @Override
  public int clazzArg(int cl, int arg)
  {
    return _clazzes[clazzId2num(cl)].clazzArgs()[arg];
  }

  @Override
  public int clazzResultField(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzResultField();
  }

  @Override
  public int clazzOuterRef(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzOuterRef();
  }

  @Override
  public int clazzCode(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzCode();
  }

  @Override
  public boolean clazzNeedsCode(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzNeedsCode();
  }

  @Override
  public boolean isConstructor(int clazz)
  {
    // move to fuir
    return clazz == clazzResultClazz(clazz);
  }

  @Override
  public boolean clazzIsRef(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzIsRef();
  }

  @Override
  public boolean clazzIsBoxed(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzIsBoxed();
  }

  @Override
  public int clazzAsValue(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzAsValue();
  }

  @Override
  public byte[] clazzTypeName(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzTypeName();
  }

  @Override
  public int clazzTypeParameterActualType(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzTypeParameterActualType();
  }

  @Override
  public SpecialClazzes getSpecialClazz(int cl)
  {
    for (int i = 0; i < _specialClazzes.length; i++)
      {
        if (_specialClazzes[i] == cl)
          {
            return SpecialClazzes.values()[i];
          }
      }
    return SpecialClazzes.c_NOT_FOUND;
  }

  @Override
  public boolean clazzIs(int cl, SpecialClazzes c)
  {
    // NYI: do this in FUIR
    return getSpecialClazz(cl) == c;
  }

  @Override
  public int clazz(SpecialClazzes c)
  {
    return _specialClazzes[c.ordinal()];
  }

  @Override
  public int clazzAny()
  {
    // NYI: move to FUIR
    return clazz(SpecialClazzes.c_Any);
  }

  @Override
  public int clazzUniverse()
  {
    // NYI: move to FUIR
    return clazz(SpecialClazzes.c_universe);
  }

  @Override
  public int clazz_Const_String()
  {
    // NYI: move to FUIR
    return clazz(SpecialClazzes.c_Const_String);
  }

  @Override
  public int clazz_Const_String_utf8_data()
  {
    // NYI: move to FUIR
    return clazz(SpecialClazzes.c_CS_utf8_data);
  }

  @Override
  public int clazz_array_u8()
  {
    // NYI: move to FUIR
    var utf8_data = clazz_Const_String_utf8_data();
    return utf8_data == NO_CLAZZ ? NO_CLAZZ : clazzResultClazz(utf8_data);
  }


  /**
   * Get the id of clazz {@code fuzion.sys.array u8}
   *
   * @return the id of {@code fuzion.sys.array u8} or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8()
  {
    // NYI: move to FUIR
    var a8 = clazz_array_u8();
    var ia = a8 == NO_CLAZZ ? NO_CLAZZ : lookup_array_internal_array(a8);
    return ia == NO_CLAZZ ? NO_CLAZZ : clazzResultClazz(ia);
  }


  /**
   * Get the id of clazz {@code (fuzion.sys.array u8).data}
   *
   * @return the id of {@code (fuzion.sys.array u8).data} or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_data()
  {
    // NYI: move to FUIR
    var sa8 = clazz_fuzionSysArray_u8();
    return sa8 == NO_CLAZZ ? NO_CLAZZ : lookup_fuzion_sys_internal_array_data(sa8);
  }


  /**
   * Get the id of clazz {@code (fuzion.sys.array u8).length}
   *
   * @return the id of {@code (fuzion.sys.array u8).length} or -1 if that clazz was not created.
   */
  @Override
  public int clazz_fuzionSysArray_u8_length()
  {
    // NYI: move to FUIR
    var sa8 = clazz_fuzionSysArray_u8();
    return sa8 == NO_CLAZZ ? NO_CLAZZ : lookup_fuzion_sys_internal_array_length(sa8);
  }

  @Override
  public int clazz_error()
  {
    return clazz(SpecialClazzes.c_error);
  }

  @Override
  public int lookupJavaRef(int cl)
  {
    cl = clazzAsValue(cl);
    for (int index = 0; index < clazzNumFields(cl); index++)
      {
        if (clazzBaseName(clazzField(cl, index)).compareTo("Java_Ref") == 0)
          {
            return clazzField(cl, index);
          }
      }
    return NO_CLAZZ;
  }

  @Override
  public boolean isJavaRef(int cl)
  {
    // NYI: HACK
    return clazzKind(cl) == FeatureKind.Field && this.clazzBaseName(cl).compareTo("Java_Ref") == 0;
  }

  @Override
  public int lookupCall(int cl)
  {
    return _clazzes[clazzId2num(cl)].lookupCall();
  }

  @Override
  public int lookupCall(int cl, boolean markAsCalled)
  {
    return _clazzes[clazzId2num(cl)].lookupCall();
  }

  @Override
  public int lookup_static_finally(int cl)
  {
    return _clazzes[clazzId2num(cl)].lookup_static_finally();
  }

  @Override
  public int lookupAtomicValue(int cl)
  {
    for (int index = 0; index < clazzNumFields(cl); index++)
      {
        if (clazzBaseName(clazzField(cl, index)).compareTo("v") == 0)
          {
            return clazzField(cl, index);
          }
      }
    Errors.fatal("v field not found!");
    return -1;
  }

  @Override
  public int lookup_array_internal_array(int cl)
  {
    for (int index = 0; index < clazzNumFields(cl); index++)
      {
        if (clazzBaseName(clazzField(cl, index)).compareTo("internal_array") == 0)
          {
            return clazzField(cl, index);
          }
      }
    Errors.fatal("internal_array field not found!");
    return -1;
  }

  @Override
  public int lookup_fuzion_sys_internal_array_data(int cl)
  {
    for (int index = 0; index < clazzNumFields(cl); index++)
      {
        if (clazzBaseName(clazzField(cl, index)).compareTo("data") == 0)
          {
            return clazzField(cl, index);
          }
      }
    Errors.fatal("data field not found!");
    return -1;
  }

  @Override
  public int lookup_fuzion_sys_internal_array_length(int cl)
  {
    for (int index = 0; index < clazzNumFields(cl); index++)
      {
        if (clazzBaseName(clazzField(cl, index)).compareTo("length") == 0)
          {
            return clazzField(cl, index);
          }
      }
    Errors.fatal("length field not found!");
    return -1;
  }

  @Override
  public int lookup_error_msg(int cl)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'lookup_error_msg'");
  }

  @Override
  public boolean clazzIsUnitType(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzIsUnitType();
  }

  @Override
  public boolean clazzIsVoidType(int cl)
  {
    return clazzIs(cl, SpecialClazzes.c_void);
  }

  @Override
  public boolean hasData(int cl)
  {
    return _clazzes[clazzId2num(cl)].hasData();
  }

  @Override
  public int clazzActualGeneric(int cl, int gix)
  {
    return clazzActualGenerics(cl)[gix];
  }

  public int[] clazzActualGenerics(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzActualGenerics();
  }

  @Override
  public LifeTime lifeTime(int cl)
  {
    return _clazzes[clazzId2num(cl)].lifeTime();
  }

  @Override
  public int clazzAt(int s)
  {
    return _sites[s-SITE_BASE].clazzAt();
  }

  @Override
  public String siteAsString(int s)
  {
    String res;
    if (s == NO_SITE)
      {
        res = "** NO_SITE **";
      }
    else if (s >= SITE_BASE && s < SITE_BASE+siteCount())
      {
        var cl = clazzAt(s);
        var p = sitePos(s);
        res = clazzAsString(cl) + "(" + clazzArgCount(cl) + " args)" + (p == null ? "" : " at " + sitePos(s).show());
      }
    else
      {
        res = "ILLEGAL site " + s;
      }
    return res;
  }

  @Override
  public ExprKind codeAt(int s)
  {
    return _sites[s-SITE_BASE].codeAt();
  }

  @Override
  public int tagValueClazz(int s)
  {
    return _sites[s-SITE_BASE].tagValueClazz();
  }

  @Override
  public int tagNewClazz(int s)
  {
    return _sites[s-SITE_BASE].tagNewClazz();
  }

  @Override
  public int tagTagNum(int s)
  {
    return _sites[s-SITE_BASE].tagTagNum();
  }

  @Override
  public int boxValueClazz(int s)
  {
    return _sites[s-SITE_BASE].boxValueClazz();
  }

  @Override
  public int boxResultClazz(int s)
  {
    return _sites[s-SITE_BASE].boxResultClazz();
  }

  @Override
  public String comment(int s)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'comment'");
  }

  @Override
  public int accessedClazz(int s)
  {
    return _sites[s-SITE_BASE].accessedClazz();
  }

  @Override
  public int assignedType(int s)
  {
    return _sites[s-SITE_BASE].assignedType();
  }

  @Override
  public int[] accessedClazzes(int s)
  {
    return _sites[s-SITE_BASE].accessedClazzes();
  }

  @Override
  public int lookup(int s, int tclazz)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'lookup'");
  }

  @Override
  public boolean accessIsDynamic(int s)
  {
    return _sites[s-SITE_BASE].accessIsDynamic();
  }

  @Override
  public int accessTargetClazz(int s)
  {
    return _sites[s-SITE_BASE].accessTargetClazz();
  }

  @Override
  public int constClazz(int s)
  {
    return _sites[s-SITE_BASE].constClazz();
  }

  @Override
  public byte[] constData(int s)
  {
    return _sites[s-SITE_BASE].constData();
  }

  @Override
  public int matchCaseCount(int s)
  {
    return _sites[s-SITE_BASE].matchCaseCount();
  }

  @Override
  public int matchStaticSubject(int s)
  {
    return _sites[s-SITE_BASE].matchStaticSubject();
  }

  @Override
  public int matchCaseField(int s, int cix)
  {
    return _sites[s-SITE_BASE].matchCaseField()[cix];
  }

  @Override
  public int matchCaseIndex(int s, int tag)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'matchCaseIndex'");
  }

  @Override
  public int[] matchCaseTags(int s, int cix)
  {
    return _sites[s-SITE_BASE].matchCaseTags()[cix];
  }

  @Override
  public int matchCaseCode(int s, int cix)
  {
    return _sites[s-SITE_BASE].matchCaseCode()[cix];
  }

  @Override
  public boolean alwaysResultsInVoid(int s)
  {
    return s==NO_SITE || s<0 ? false : _sites[s-SITE_BASE].alwaysResultsInVoid();
  }

  @Override
  public SourcePosition sitePos(int s)
  {
    // NYI: UNDER DEVELOPMENT:
    return SourcePosition.notAvailable;
  }

  @Override
  public boolean isEffectIntrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (cl != NO_CLAZZ);

    return
      (clazzKind(cl) == FeatureKind.Intrinsic) &&
      switch(clazzOriginalName(cl))
      {
      case "effect.type.abort0"  ,
           "effect.type.default0",
           "effect.type.instate0",
           "effect.type.is_instated0",
           "effect.type.replace0" -> true;
      default -> false;
      };
  }

  @Override
  public int effectTypeFromInstrinsic(int cl)
  {
    if (PRECONDITIONS) require
      (isEffectIntrinsic(cl));

    return clazzActualGeneric(clazzOuterClazz(cl), 0);
  }

  @Override
  public int inlineArrayElementClazz(int constCl)
  {
    return _clazzes[clazzId2num(constCl)].inlineArrayElementClazz();
  }

  @Override
  public boolean clazzIsArray(int constCl)
  {
    // NYI: cleanup
    return clazzOriginalName(constCl).compareTo("array") == 0;
  }

  @Override
  public String clazzSrcFile(int cl)
  {
    return "NYI: clazzSrcFile";
  }

  @Override
  public SourcePosition declarationPos(int cl)
  {
    return SourcePosition.notAvailable;
  }

  @Override
  public void recordAbstractMissing(int cl, int f, int instantiationSite, String context, int callSite)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'recordAbstractMissing'");
  }

  @Override
  public void reportAbstractMissing()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'reportAbstractMissing'");
  }

  @Override
  public boolean withinCode(int s)
  {
    return s != NO_SITE && _sites[s - SITE_BASE].codeAt() != null;
  }

   /**
   * From a given site, determine the site of the start of the code block that
   * contains the given site.
   *
   * @param site any site
   *
   * @return the site of the first Expr in the code block containing `site`
   */
  @Override
  public int codeBlockStart(int site)
  {
    var c = site - SITE_BASE;
    var result = c;
    while (result > 0 && _sites[result-1].codeAt() != null)
      {
        result--;
      }
    return result + SITE_BASE;
  }


  /**
   * From a given site, determine the site of the last Expr in the code block
   * that contains the given site.
   *
   * @param site any site
   *
   * @return the site of the last Expr in the code block containing `site`
   */
  @Override
  public int codeBlockEnd(int site)
  {
    var s0 = codeBlockStart(site);
    while (withinCode(s0 + codeSizeAt(s0)))
      {
        s0 = s0 + codeSizeAt(s0);
      }
    return s0;
  }

}
