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
 * Source of class OptimizedFUIR
 *
 *---------------------------------------------------------------------*/


package dev.flang.fuir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;


public class OptimizedFUIR extends GeneratingFUIR {


  /*--------------------------  constructors  ---------------------------*/


  private final GeneratingFUIR _original;


  /**
   * Clone this FUIR such that modifications can be made by optimizers.  An heir
   * of FUIR can use this to redefine methods.
   *
   * @param original the original FUIR instance that we are cloning.
   */
  public OptimizedFUIR(GeneratingFUIR original)
  {
    super(original);
    _original = original;
  }

  // passthrough methods that DFA overrides
  @Override public LifeTime lifeTime(int cl) {  return _original.lifeTime(cl); }
  @Override public boolean doesResultEscape(int s) { return _original.doesResultEscape(s); }
  @Override public boolean alwaysResultsInVoid(int s){ return _original.alwaysResultsInVoid(s); }
  @Override public int[] matchCaseTags(int s, int cix){ return _original.matchCaseTags(s, cix); }
  @Override public int matchCaseField(int s, int cix){ return _original.matchCaseField(s, cix); }
  @Override public boolean clazzIsUnitType(int cl){ return _original.clazzIsUnitType(cl); }
  @Override public int clazzOuterRef(int cl){  return _original.clazzOuterRef(cl); }
  @Override public int accessedClazz(int s){ return _original.accessedClazz(s); }


  /*----------------------  serializing FUIR  ----------------------*/


  /**
   * The count of sites that this FUIR contains
   */
  private int siteCount()
  {
    return _allCode.size();
  }


  /**
   * for SpecialClazzes.values get the clazzes
   */
  private int[] specialClazzes()
  {
    return Arrays
      .stream(SpecialClazzes.values())
      .mapToInt(sc -> sc == SpecialClazzes.c_NOT_FOUND ? NO_CLAZZ : clazz(sc))
      .toArray();
  }


  /**
   * for clazz `cl` get all argument clazzes.
   */
  private int[] clazzArgs(int cl)
  {
    var result = new int[clazzArgCount(cl)];
    for (int i = 0; i < result.length; i++)
      {
        result[i]= clazzArg(cl, i);
      }
    return result;
  }


  /**
   * for choice `cl` get the choice element clazzes.
   */
  private int[] clazzChoices(int cl)
  {
    var numChoices = clazzChoiceCount(cl);
    var result = new int[numChoices > 0 ? numChoices : 0];
    for (int i = 0; i < result.length; i++)
      {
        result[i]= clazzChoice(cl, i);
      }
    return result;
  }


  /**
   * the actual generics for clazz `cl`
   */
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


  /**
   * for clazz `cl` get all field clazzes.
   */
  private int[] clazzFields(int cl)
  {
    var numFields = clazzIsRef(cl) ? 0 : clazzFieldCount(cl);
    var result = new ArrayList<Integer>();
    var lastClazz = lastClazz();
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


  /**
   * serialize the FUIR to a byte array
   * which can be written to a file.
   */
  public byte[] serialize()
  {
    var firstClazz = firstClazz();
    var lastClazz = lastClazz();
    var siteCount = siteCount();

    var baos = new ByteArrayOutputStream();
    /**
     * NYI: UNDER DEVELOPMENT:
     * replace by custom serialization of FUIR
     */
    try (ObjectOutputStream oos = new ObjectOutputStream(baos))
      {
        oos.writeInt(mainClazz());
        var clazzes = new ClazzRecord[lastClazz-firstClazz+1];
        for (int cl = firstClazz; cl <= lastClazz; cl++)
          {
            var clazzKind = clazzKind(cl);
            var isRoutine = clazzKind == FeatureKind.Routine;
            var needsCode = isRoutine && clazzNeedsCode(cl);
            clazzes[clazzId2num(cl)] = new ClazzRecord(
                clazzBaseName(cl),
                clazzOuterClazz(cl),
                clazzIsBoxed(cl),
                clazzArgs(cl),
                clazzKind,
                clazzOuterRef(cl),
                clazzResultClazz(cl),
                clazzIsRef(cl),
                clazzIsUnitType(cl),
                clazzAsValue(cl),
                clazzChoices(cl),
                clazzInstantiatedHeirs(cl),
                clazzNeedsCode(cl),
                clazzFields(cl),
                needsCode ? clazzCode(cl) : NO_SITE,
                clazzResultField(cl),
                clazzOriginalName(cl),
                clazzActualGenerics(cl),
                lookupCall(cl),
                lookupStaticFinally(cl),
                isRoutine ? lifeTime(cl) : null,
                clazzTypeName(cl),
                clazzAsStringHuman(cl),
                clazzSrcFile(cl),
                clazzDeclarationPos(cl).bytePos(),
                lookupJavaRef(cl),
                lookupCause(cl)
                );
          }
        oos.writeObject(clazzes);

        var sites = new SiteRecord[siteCount];
        for (int s = SITE_BASE; s < SITE_BASE+siteCount; s++)
          {
            if (invalidSite(s))
              {
                sites[s-SITE_BASE] = new SiteRecord(
                  clazzAt(s), false, false, null, NO_CLAZZ, null, NO_CLAZZ, null,
                  NO_CLAZZ, NO_SITE, NO_SITE, NO_SITE, NO_CLAZZ, NO_SITE, siteCount,
                  null, null, NO_CLAZZ, -1, null, false, null, -1, -1, null);
              }
            else
              {
                var codeAt = codeAt(s);
                var sitePos = sitePos(s);
                var accessedClazz =
                  codeAt.isCallOrAssign() && accessedClazz(s) <= lastClazz
                    ? accessedClazz(s)
                    : NO_CLAZZ;

                sites[s-SITE_BASE] = new SiteRecord(
                    clazzAt(s),
                    alwaysResultsInVoid(s),
                    doesResultEscape(s),
                    codeAt,
                    codeAt != ExprKind.Const ? NO_CLAZZ : constClazz(s),
                    codeAt == ExprKind.Const ? constData(s) : null,
                    accessedClazz,
                    accessedClazz != NO_CLAZZ ? accessedClazzes(s) : null,
                    codeAt.isCallOrAssign() ? accessTargetClazz(s) : NO_CLAZZ,
                    codeAt != ExprKind.Tag ? NO_CLAZZ : tagValueClazz(s),
                    codeAt != ExprKind.Assign ? NO_CLAZZ : assignedType(s),
                    codeAt != ExprKind.Box ? NO_CLAZZ : boxValueClazz(s),
                    codeAt != ExprKind.Box ? NO_CLAZZ : boxResultClazz(s),
                    codeAt != ExprKind.Match ? NO_CLAZZ : matchStaticSubject(s),
                    codeAt == ExprKind.Match ? matchCaseCount(s) : -1,
                    codeAt == ExprKind.Match ? matchCaseTags(s) : null,
                    codeAt == ExprKind.Match ? matchCaseCode(s) : null,
                    codeAt != ExprKind.Tag ? NO_CLAZZ : tagNewClazz(s),
                    codeAt != ExprKind.Tag ? -1 : tagTagNum(s),
                    codeAt != ExprKind.Match ? null : matchCaseFields(s),
                    codeAt.isCallOrAssign() ? accessIsDynamic(s) : false,
                    sitePos == null ? null : sitePos._sourceFile._fileName.toString(),
                    sitePos == null ? -1   : sitePos.line(),
                    sitePos == null ? -1   : sitePos.column(),
                    sitePos == null ? null : sitePos.show()
                  );
              }
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


  /**
   * Does `s` represent an invalid site?
   *
   * This either means `s` does not point to any code
   * or the code was never accessed during the DFA.
   */
  private boolean invalidSite(int s)
  {
    return !withinCode(s) || !_accessedSites.get(s-SITE_BASE);
  }


  /**
   * For the match at `s` for each case get the tags
   * that it matches.
   */
  private int[][] matchCaseTags(int s)
  {
    var result = new int[matchCaseCount(s)][];
    for (int cix = 0; cix < result.length; cix++)
      {
        result[cix] = matchCaseTags(s, cix);
      }
    return result;
  }


  /**
   * For the match at `s` get the sites of the code of the cases
   */
  private int[] matchCaseCode(int s)
  {
    var result = new int[matchCaseCount(s)];
    for (int cix = 0; cix < result.length; cix++)
      {
        result[cix] = matchCaseCode(s, cix);
      }
    return result;
  }


  /**
   * For the match at `s` get the clazzes of the field cases
   */
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
