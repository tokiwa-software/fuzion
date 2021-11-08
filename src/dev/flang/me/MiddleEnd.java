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
 * Source of class MiddleEnd
 *
 *---------------------------------------------------------------------*/

package dev.flang.me;

import dev.flang.air.AIR;

import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.Call; // NYI: remove dependency!
import dev.flang.ast.Feature; // NYI: remove dependency!
import dev.flang.ast.FeatureVisitor; // NYI: remove dependency!
import dev.flang.ast.Impl; // NYI: remove dependency!
import dev.flang.ast.Match; // NYI: remove dependency!
import dev.flang.ast.Resolution; // NYI: remove dependency!
import dev.flang.ast.Stmnt; // NYI: remove dependency!
import dev.flang.ast.Tag; // NYI: remove dependency!
import dev.flang.ast.Type; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!

import dev.flang.mir.MIR;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * The MiddleEnd creates application IR (air) from the the module IRs (mir)
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MiddleEnd extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FuzionOptions _options;


  public final MIR _mir;


  /**
   * List of features scheduled for feature index resolution
   */
  final List<AbstractFeature> _forFindingUsedFeatures = new List<>();


  final Resolution _res;

  /*--------------------------  constructors  ---------------------------*/


  public MiddleEnd(FuzionOptions options, MIR mir, Resolution res)
  {
    _options = options;
    _mir = mir;
    _res = res;
    Clazz._res = res; // NYI: Bad hack!
  }


  /*-----------------------------  methods  -----------------------------*/


  public AIR air()
  {
    return new AIR(main());
  }


  private Clazz main()
  {
    var main = _mir.main();
    markUsed(_mir.universe(), SourcePosition.builtIn);
    markUsed(main,            SourcePosition.builtIn);

    markInternallyUsed();
    while (!_forFindingUsedFeatures.isEmpty())
      {
        AbstractFeature f = _forFindingUsedFeatures.removeFirst();
        findUsedFeatures(f);
      }

    Clazzes.init(_options);

    Clazz cl = main != null ? Clazzes.clazz(main.thisType()) : null;
    return cl;
  }


  /**
   * Mark internally used features as used.
   */
  void markInternallyUsed() {
    var tag = FuzionConstants.CHOICE_TAG_NAME;
    var universe = _mir.universe();
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "i8" ,1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "i16",1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "i32",1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "i64",1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "u8" ,1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "u16",1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "u32",1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "u64",1).get(_res, "val"), SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "bool").get(_res, tag) , SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "conststring")   , SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "conststring").get(_res, "isEmpty"), SourcePosition.builtIn);  // NYI: check why this is not found automatically
    markUsed(Types.resolved.f_sys_array_data              , SourcePosition.builtIn);
    markUsed(Types.resolved.f_sys_array_length            , SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "unit")          , SourcePosition.builtIn);
    markUsed(((Feature)universe /* NYI: Cast! */).get(_res, "void")          , SourcePosition.builtIn);
  }


  void scheduleForFindUsedFeatures(AbstractFeature f)
  {
    _forFindingUsedFeatures.add(f);
  }


  /**
   * During FINDING_USED_FEATURES, this sets the flag that this feature is used.
   *
   * @param usedAt the position this feature was used at, for creating usefule
   * error messages
   */
  void markUsed(AbstractFeature f, SourcePosition usedAt)
  {
    markUsed(f, false, usedAt);
  }


  /**
   * During FINDING_USED_FEATURES, this sets the flag that this feature is used.
   *
   * @param dynamically true iff this feature is called dynamically, i.e., it
   * has to be part of the dynamic binding data.
   *
   * @param usedAt the position this feature was used at, for creating usefule
   * error messages
   */
  void markUsed(AbstractFeature af, boolean dynamically, SourcePosition usedAt)
  {
    var f = (Feature) af;  /* NYI: Cast! */
    if (dynamically)
      {
        Clazzes.setCalledDynamically0(af);
      }
    if (!Clazzes.isUsedAtAll(f))
      {
        Clazzes.addUsedFeature(f, usedAt);
        if (f.state() != Feature.State.ERROR)
          {
            scheduleForFindUsedFeatures(f);
          }
        if (f.resultField() != null)
          {
            markUsed(f.resultField(), usedAt);
          }
        if (f.resultType() != null)
          {
            if (!f.resultType().isGenericArgument())
              { // Since instances of choice types are never created explicity,
                // they will be marked as used if they are used as a result type
                // of a function or field.
                AbstractFeature rtf = f.resultType().featureOfType();
                if (rtf.isChoice())
                  {
                    markUsed(rtf, usedAt);
                  }
              }
          }
        if (f.isIntrinsic() && f.outerRefOrNull() != null)
          {
            markUsed(f.outerRefOrNull(), false, usedAt);
          }
        for (AbstractFeature rf : _mir._module.redefinitions(f))
          {
            markUsed(rf, usedAt);
          }
      }
  }


  void findUsedFeatures(AbstractFeature f)
  {
    if (f.outer() != null)
      {
        markUsed(f.outer(), f.pos());
      }
    for (var fa : f.arguments())
      {
        markUsed(fa, f.pos());
        if (fa.isOpenGenericField())
          {
            for (var i = 0; i<fa.selectSize(); i++)
              {
                markUsed(fa.select(i), f.pos());
              }
          }
      }
    for (var p: f.inherits())
      {
        markUsed(p.calledFeature(), p.pos);
      }
    findUsedFeatures(f.resultType(), f.pos());
    if (f.choiceTag() != null)
      {
        markUsed(f.choiceTag(), f.pos());
      }

    f.visit(new FeatureVisitor() {
        // it does not seem to be necessary to mark all features in types as used:
        // public Type  action(Type    t, Feature outer) { t.findUsedFeatures(res, pos); return t; }
        public Call  action(Call    c, Feature outer) { findUsedFeatures(c); return c; }
        //        public Stmnt action(Feature f, Feature outer) { markUsed(res, pos);      return f; } // NYI: this seems wrong ("f." missing) or unnecessary
        public void  action(Match   m, Feature outer) { findUsedFeatures(m);           }
        public void  action(Tag     t, Feature outer) { findUsedFeatures(t._taggedType, t.pos()); }
      });
  }



  /**
   * Mark all features used within this type as used.
   */
  void findUsedFeatures(Type t, SourcePosition pos)
  {
    if (!t.isGenericArgument())
      {
        markUsed(t.featureOfType(), pos);
        for (var tg : t._generics)
          {
            findUsedFeatures(tg, pos);
          }
      }
  }


  /**
   * Find used features, i.e., mark all features that are found to be the target of a call as used.
   */
  void findUsedFeatures(Call c)
  {
    if (PRECONDITIONS) require
      (Errors.count() > 0 || c.calledFeature_ != null);

    var cf = c.calledFeature();
    if (cf != null)
      {
        markUsed(cf, c.isDynamic(), c.pos());
        for (Type t : c.generics)
          {
            if (!t.isGenericArgument())
              {
                AbstractFeature f = t.featureOfType();
                markUsed(f, t.pos);  // NYI: needed? If the actual generic type is not called anywhere, maybe it can go
              }
          }
      }
  }


  /**
   * Find used features, i.e., mark all features that are found to be the target of a call as used.
   */
  void findUsedFeatures(Match m)
  {
    AbstractFeature sf = m.subject.type().featureOfType();
    AbstractFeature ct = sf.choiceTag();

    check
      (Errors.count() > 0 || ct != null);

    if (ct != null)
      {
        markUsed(ct, m.pos());
      }
  }


}

/* end of file */
