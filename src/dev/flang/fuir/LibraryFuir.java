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
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.flang.util.SourcePosition;
import dev.flang.fe.LibraryModule;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourceFile;


/**
 * The FUIR loaded/deserialized from a byte array
 */
public class LibraryFuir extends FUIR {


  /*-----------------------------  final fields  -----------------------------*/


  private final int _mainClazz;
  private final ClazzRecord[] _clazzes;
  private final int[] _specialClazzes;
  private final SiteRecord[] _sites;
  private final LibraryModule _mainModule;


  /*-----------------------------  cache  -----------------------------*/


  private final Map<String,SourceFile> _srcFiles = new ConcurrentHashMap<String, SourceFile>();


  /*-----------------------------  constructor  -----------------------------*/


  public LibraryFuir(byte[] data, LibraryModule lm)
  {
    var mainClazz = NO_CLAZZ;
    var clazzes = new ClazzRecord[0];
    var sites = new SiteRecord[0];
    var specialClazzes = new int[0];
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data)))
      {
        mainClazz = ois.readInt();
        clazzes = (ClazzRecord[]) ois.readObject();
        sites = (SiteRecord[]) ois.readObject();
        specialClazzes = (int[]) ois.readObject();
      }
    catch(ClassNotFoundException | IOException e)
      {
        Errors.fatal(e);
      }
    _mainClazz = mainClazz;
    _clazzes = clazzes;
    _sites = sites;
    _specialClazzes = specialClazzes;
    _mainModule = lm;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * helper to get SourceFile from cache or compute it once
   */
  private SourceFile sourceFile(String path)
  {
    return _srcFiles
      .computeIfAbsent(path, p -> new SourceFile(Path.of(p)));
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
  public int clazzFieldCount(int cl)
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
  public int clazzChoiceCount(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzChoices().length;
  }

  @Override
  public int clazzChoice(int cl, int i)
  {
    return _clazzes[clazzId2num(cl)].clazzChoices()[i];
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
  public int clazz(SpecialClazzes c)
  {
    return _specialClazzes[c.ordinal()];
  }

  @Override
  public int clazzRefConstString()
  {
    var cs = clazz(SpecialClazzes.c_const_string);
    for (int i = 0; i < _clazzes.length; i++)
      {
        if (_clazzes[i].clazzIsRef() && _clazzes[i].clazzAsValue() == cs)
          {
            return i + CLAZZ_BASE;
          }
      }
    Errors.fatal("clazz_ref_const_string");
    return NO_CLAZZ;
  }

  @Override
  public int lookupJavaRef(int cl)
  {
    return _clazzes[clazzId2num(cl)].lookupJavaRef();
  }

  @Override
  public int lookupCall(int cl)
  {
    return _clazzes[clazzId2num(cl)].lookupCall();
  }

  @Override
  public int lookupStaticFinally(int cl)
  {
    return _clazzes[clazzId2num(cl)].lookupStaticFinally();
  }

  @Override
  public int lookupAtomicValue(int cl)
  {
    for (int index = 0; index < clazzFieldCount(cl); index++)
      {
        if (clazzBaseName(clazzField(cl, index)).compareTo("v") == 0)
          {
            return clazzField(cl, index);
          }
      }
    Errors.fatal("v field not found!");
    return NO_CLAZZ;
  }


  @Override
  public int lookupCause(int ecl)
  {
    return _clazzes[clazzId2num(ecl)].lookupCause();
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
  public int clazzActualGeneric(int cl, int gix)
  {
    return _clazzes[clazzId2num(cl)].clazzActualGenerics()[gix];
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
    else if (s >= SITE_BASE && s < SITE_BASE+_sites.length)
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
    return "NYI: Unimplemented method 'comment'";
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
  public boolean doesResultEscape(int s)
  {
    return s==NO_SITE || s<0 ? false : _sites[s-SITE_BASE].doesResultEscape();
  }

  @Override
  public SourcePosition sitePos(int s)
  {
    return s==NO_SITE || _sites[s-SITE_BASE].module() == null
      ? SourcePosition.notAvailable
      : _mainModule.pos(_sites[s-SITE_BASE].module(), _sites[s-SITE_BASE].bytePos());
  }


  @Override
  public String clazzSrcFile(int cl)
  {
    return _clazzes[clazzId2num(cl)].clazzSrcFile();
  }


  @Override
  public SourcePosition clazzDeclarationPos(int cl)
  {
    var r = _clazzes[clazzId2num(cl)];
    return new SourcePosition(sourceFile(r.clazzSrcFile()),
                              r.clazzSrcBytePos());
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
