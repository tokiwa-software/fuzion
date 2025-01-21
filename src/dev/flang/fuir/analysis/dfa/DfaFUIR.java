package dev.flang.fuir.analysis.dfa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

import dev.flang.fuir.ClazzRecord;
import dev.flang.fuir.GeneratingFUIR;
import dev.flang.fuir.SiteRecord;
import dev.flang.fuir.SpecialClazzes;


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
    // NYI: CLEANUP: DFA should probably already create those?
    // make sure there is a value clazz for
    // every boxed clazz, backends jvm and c need this.
    // test inheritance fails without this.
    for (int cl = firstClazz(); cl <= lastClazz(); cl++)
      {
        if (clazzIsBoxed(cl))
          {
            clazzAsValue(cl);
          }
      }
    // NYI: CLEANUP: currently needed by interpreter
    _lookupDone = false;
    clazz(SpecialClazzes.c_sys_ptr);
    _lookupDone = true;

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
            var cl0 = cl;
            var needsCode = clazzKind(cl) == FeatureKind.Routine && clazzNeedsCode(cl) && _accessedCode.contains(cl);
            clazzes[clazzId2num(cl)] = new ClazzRecord(
                clazzBaseName(cl),
                clazzOuterClazz(cl),
                clazzIsBoxed(cl),
                clazzArgs(cl),
                clazzKind(cl),
                clazzOuterRef(cl0),
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
                safe(()->lookupCall(cl0), NO_CLAZZ),
                safe(()->lookup_static_finally(cl0), NO_CLAZZ),
                clazzKind(cl) == FeatureKind.Routine ? lifeTime(cl) : null,
                clazzTypeName(cl0),
                clazzIsArray(cl) ? inlineArrayElementClazz(cl) : NO_CLAZZ,
                clazzAsStringHuman(cl),
                clazzSrcFile(cl),
                safe(()->lookupJavaRef(cl0), NO_CLAZZ)
                );
          }
        oos.writeObject(clazzes);

        var sites = new SiteRecord[siteCount];
        for (int s = SITE_BASE; s < SITE_BASE+siteCount; s++)
          {
            if (_accessedSites.contains(s))
              {
                var s0 = s;
                var accessedClazz =
                  !withinCode(s) || !(codeAt(s) == ExprKind.Call || codeAt(s) == ExprKind.Assign)
                    ? NO_CLAZZ
                    : accessedClazz(s0) > lastClazz
                    ? NO_CLAZZ
                    : accessedClazz(s0);

                sites[s-SITE_BASE] = new SiteRecord(
                    clazzAt(s),
                    !withinCode(s) ? false : alwaysResultsInVoid(s),
                    !withinCode(s) ? false : doesResultEscape(s),
                    !withinCode(s) ? null : codeAt(s),
                    !withinCode(s) || codeAt(s) != ExprKind.Const ? NO_CLAZZ : constClazz(s) ,
                    !withinCode(s) ? null : codeAt(s) == ExprKind.Const ? constData(s) : null,
                    accessedClazz,
                    accessedClazz != NO_CLAZZ ? accessedClazzes(s) : null,
                    !withinCode(s) || !(codeAt(s) == ExprKind.Call || codeAt(s) == ExprKind.Assign) ? NO_CLAZZ : accessTargetClazz(s),
                    !withinCode(s) || codeAt(s) != ExprKind.Tag ? NO_CLAZZ : tagValueClazz(s),
                    !withinCode(s) || codeAt(s) != ExprKind.Assign ? NO_CLAZZ : assignedType(s),
                    !withinCode(s) || codeAt(s) != ExprKind.Box ? NO_CLAZZ : boxValueClazz(s0),
                    !withinCode(s) || codeAt(s) != ExprKind.Box ? NO_CLAZZ : boxResultClazz(s0),
                    !withinCode(s) || codeAt(s) != ExprKind.Match ? NO_CLAZZ : matchStaticSubject(s0),
                    !withinCode(s) ? -1 : codeAt(s) == ExprKind.Match ? matchCaseCount(s) : NO_CLAZZ,
                    !withinCode(s) ? null : codeAt(s) == ExprKind.Match ? matchCaseTags(s) : null,
                    !withinCode(s) ? null : codeAt(s) == ExprKind.Match ? matchCaseCode(s) : null,
                    !withinCode(s) || codeAt(s) != ExprKind.Tag ? NO_CLAZZ : tagNewClazz(s),
                    !withinCode(s) || codeAt(s) != ExprKind.Tag ? -1 : tagTagNum(s),
                    !withinCode(s) || codeAt(s) != ExprKind.Match ? null : matchCaseFields(s),
                    !withinCode(s) || !(codeAt(s) == ExprKind.Assign || codeAt(s) == ExprKind.Call) ? false : accessIsDynamic(s),
                    !withinCode(s) || sitePos(s) == null ? null : sitePos(s)._sourceFile._fileName.toString(),
                    !withinCode(s) || sitePos(s) == null ? -1 : sitePos(s).line(),
                    !withinCode(s) || sitePos(s) == null ? -1 : sitePos(s).column(),
                    !withinCode(s) || sitePos(s) == null ? null : sitePos(s).show()
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

  // NYI: cleanup
  private <T> T safe(Supplier<T> fn, T dflt)
  {
    try {
      return fn.get();
    } catch (Throwable e) {
      return dflt;
    }
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
