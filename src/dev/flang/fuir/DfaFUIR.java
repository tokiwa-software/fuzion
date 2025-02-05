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
 * Source of class DfaFUIR
 *
 *---------------------------------------------------------------------*/


package dev.flang.fuir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;


public class DfaFUIR extends GeneratingFUIR {


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Clone this FUIR such that modifications can be made by optimizers.  An heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public DfaFUIR(GeneratingFUIR original)
  {
    super(original);
  }


  /*----------------------  serializing FUIR  ----------------------*/


  private int siteCount()
  {
    return _allCode.size();
  }

  private int[] specialClazzes()
  {
    return Arrays
      .stream(SpecialClazzes.values())
      .mapToInt(sc -> sc == SpecialClazzes.c_NOT_FOUND ? NO_CLAZZ : clazz(sc))
      .toArray();
  }

  private int[] clazzArgs(int cl)
  {
    var result = new int[clazzArgCount(cl)];
    for (int i = 0; i < result.length; i++)
      {
        result[i]= clazzArg(cl, i);
      }
    return result;
  }

  protected int[] clazzChoices(int cl)
  {
    var numChoices = clazzChoiceCount(cl);
    var result = new int[numChoices > 0 ? numChoices : 0];
    for (int i = 0; i < result.length; i++)
      {
        result[i]= clazzChoice(cl, i);
      }
    return result;
  }


  private int[] clazzActualGenerics(int cl)
  {
    var cc = id2clazz(cl);
    var generics = cc.actualTypeParameters();
    var result = new int[generics.length];
    for (int gix = 0; gix < result.length; gix++)
      {
        result[gix] = generics[gix]._id;
      }
    return result;
  }


  private int[] clazzFields(int cl, int lastClazz)
  {
    var numFields = clazzIsRef(cl) ? 0 : clazzFieldCount(cl);
    var result = new ArrayList<Integer>();
    for (int i = 0; i < numFields; i++)
      {
        var clazzField = clazzField(cl, i);
        if (clazzField <= lastClazz)
          {
            result.add(clazzField);
          }
      }
    return result
      .stream()
      .mapToInt(Integer::intValue)
      .toArray();
  }

  public byte[] serialize()
  {
    var firstClazz = firstClazz();
    var lastClazz = lastClazz();
    var siteCount = siteCount();

    var baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos))
      {
        oos.writeInt(mainClazz());
        var clazzes = new ClazzRecord[lastClazz-firstClazz+1];
        for (int cl = firstClazz; cl <= lastClazz; cl++)
          {
            var needsCode = clazzKind(cl) == FeatureKind.Routine && clazzNeedsCode(cl) && _accessedCode.contains(cl);
            clazzes[clazzId2num(cl)] = new ClazzRecord(
                clazzBaseName(cl),
                clazzOuterClazz(cl),
                clazzIsBoxed(cl),
                clazzArgs(cl),
                clazzKind(cl),
                clazzOuterRef(cl),
                clazzResultClazz(cl),
                clazzIsRef(cl),
                clazzIsUnitType(cl),
                clazzAsValue(cl),
                clazzChoices(cl),
                clazzInstantiatedHeirs(cl),
                clazzNeedsCode(cl),
                clazzFields(cl, lastClazz),
                needsCode ? clazzCode(cl) : NO_SITE,
                clazzResultField(cl),
                clazzTypeParameterActualType(cl),
                clazzOriginalName(cl),
                clazzActualGenerics(cl),
                lookupCall(cl),
                lookup_static_finally(cl),
                clazzKind(cl) == FeatureKind.Routine ? lifeTime(cl) : null,
                clazzTypeName(cl),
                clazzIsArray(cl) ? inlineArrayElementClazz(cl) : NO_CLAZZ,
                clazzAsStringHuman(cl),
                clazzSrcFile(cl),
                lookupJavaRef(cl)
                );
          }
        oos.writeObject(clazzes);

        var sites = new SiteRecord[siteCount];
        for (int s = SITE_BASE; s < SITE_BASE+siteCount; s++)
          {
              var s0 = s;
              var accessedClazz =
                invalidSite(s) || !(codeAt(s) == ExprKind.Call || codeAt(s) == ExprKind.Assign)
                  ? NO_CLAZZ
                  : accessedClazz(s0) > lastClazz
                  ? NO_CLAZZ
                  : accessedClazz(s0);

              sites[s-SITE_BASE] = new SiteRecord(
                  clazzAt(s),
                  invalidSite(s) ? false : alwaysResultsInVoid(s),
                  invalidSite(s) ? false : doesResultEscape(s),
                  invalidSite(s) ? null : codeAt(s),
                  invalidSite(s) || codeAt(s) != ExprKind.Const ? NO_CLAZZ : constClazz(s) ,
                  invalidSite(s) ? null : codeAt(s) == ExprKind.Const ? constData(s) : null,
                  accessedClazz,
                  accessedClazz != NO_CLAZZ ? accessedClazzes(s) : null,
                  invalidSite(s) || !(codeAt(s) == ExprKind.Call || codeAt(s) == ExprKind.Assign) ? NO_CLAZZ : accessTargetClazz(s),
                  invalidSite(s) || codeAt(s) != ExprKind.Tag ? NO_CLAZZ : tagValueClazz(s),
                  invalidSite(s) || codeAt(s) != ExprKind.Assign ? NO_CLAZZ : assignedType(s),
                  invalidSite(s) || codeAt(s) != ExprKind.Box ? NO_CLAZZ : boxValueClazz(s0),
                  invalidSite(s) || codeAt(s) != ExprKind.Box ? NO_CLAZZ : boxResultClazz(s0),
                  invalidSite(s) || codeAt(s) != ExprKind.Match ? NO_CLAZZ : matchStaticSubject(s0),
                  invalidSite(s) ? -1 : codeAt(s) == ExprKind.Match ? matchCaseCount(s) : NO_CLAZZ,
                  invalidSite(s) ? null : codeAt(s) == ExprKind.Match ? matchCaseTags(s) : null,
                  invalidSite(s) ? null : codeAt(s) == ExprKind.Match ? matchCaseCode(s) : null,
                  invalidSite(s) || codeAt(s) != ExprKind.Tag ? NO_CLAZZ : tagNewClazz(s),
                  invalidSite(s) || codeAt(s) != ExprKind.Tag ? -1 : tagTagNum(s),
                  invalidSite(s) || codeAt(s) != ExprKind.Match ? null : matchCaseFields(s),
                  invalidSite(s) || !(codeAt(s) == ExprKind.Assign || codeAt(s) == ExprKind.Call) ? false : accessIsDynamic(s),
                  invalidSite(s) || sitePos(s) == null ? null : sitePos(s)._sourceFile._fileName.toString(),
                  invalidSite(s) || sitePos(s) == null ? -1 : sitePos(s).line(),
                  invalidSite(s) || sitePos(s) == null ? -1 : sitePos(s).column(),
                  invalidSite(s) || sitePos(s) == null ? null : sitePos(s).show()
                );
          }
        oos.writeObject(sites);

        oos.writeObject(specialClazzes());
      }
    catch(IOException e)
      {
        e.printStackTrace();
      }
    return baos.toByteArray();
  }

  private boolean invalidSite(int s)
  {
    return !withinCode(s) || !_accessedSites.contains(s);
  }

  private int[][] matchCaseTags(int s)
  {
    var result = new int[matchCaseCount(s)][];
    for (int cix = 0; cix < result.length; cix++)
      {
        result[cix] = matchCaseTags(s, cix);
      }
    return result;
  }

  private int[] matchCaseCode(int s)
  {
    var result = new int[matchCaseCount(s)];
    for (int cix = 0; cix < result.length; cix++)
      {
        result[cix] = matchCaseCode(s, cix);
      }
    return result;
  }

  private int[] matchCaseFields(int s)
  {
    var result = new int[matchCaseCount(s)];
    for (int cix = 0; cix < result.length; cix++)
      {
        result[cix] = matchCaseField(s, cix);
      }
    return result;
  }


}
