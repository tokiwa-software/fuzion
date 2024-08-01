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

import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.air.AIR;

import dev.flang.ast.AbstractCall; // NYI: remove dependency!
import dev.flang.ast.AbstractConstant; // NYI: remove dependency!
import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.AbstractType; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.FeatureName; // NYI: remove dependency!
import dev.flang.ast.FeatureVisitor; // NYI: remove dependency!
import dev.flang.ast.InlineArray; // NYI: remove dependency!
import dev.flang.ast.State; // NYI: remove dependency!
import dev.flang.ast.Tag; // NYI: remove dependency!

import dev.flang.fe.FeatureLookup;

import dev.flang.mir.MIR;

import dev.flang.air.Clazz;
import dev.flang.air.Clazzes;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.HasSourcePosition;
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
  final LinkedList<AbstractFeature> _forFindingUsedFeatures = new LinkedList<>();


  /**
   * Redefinitions collected for used features. This contains the results of
   * redefinitions().
   */
  private TreeMap<AbstractFeature, Set<AbstractFeature>> _redefinitions = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  public MiddleEnd(FuzionOptions options, MIR mir, FeatureLookup flu)
  {
    _options = options;
    _mir = mir;
    Clazz._flu = flu; // NYI: Bad hack!
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

    Clazzes.instance.init(_options);

    Clazz cl = main != null ? Clazzes.instance.clazz(main.selfType()) : null;
    return cl;
  }


  /**
   * Mark internally used features as used.
   */
  void markInternallyUsed() {
    var universe = _mir.universe();
    var flu = Clazz._flu;
    var fuzion     = flu.lookupFeature(universe,   FeatureName.get("fuzion",      0), null);
    var fuzionJava = flu.lookupFeature(fuzion,     FeatureName.get("java",        0), null);
    var JavaObject = flu.lookupFeature(fuzionJava, FeatureName.get("Java_Object", 1), null);
    var JavaRef    = flu.lookupFeature(JavaObject, FeatureName.get("Java_Ref",    0), null);
    markUsed(JavaObject                                                                       , SourcePosition.builtIn);
    markUsed(JavaRef                                                                          , SourcePosition.builtIn);
    markUsed(flu.lookupFeature(universe, FeatureName.get("Const_String"           , 0), null) , SourcePosition.builtIn); // NYI this should be unnecessary?
    markUsed(flu.lookupFeature(universe, FeatureName.get(FuzionConstants.UNIT_NAME, 0), null) , SourcePosition.builtIn);
    markUsed(flu.lookupFeature(universe, FeatureName.get("void"                   , 0), null) , SourcePosition.builtIn);
  }


  void scheduleForFindUsedFeatures(AbstractFeature f)
  {
    _forFindingUsedFeatures.add(f);
  }


  /**
   * During FINDING_USED_FEATURES, this sets the flag that this feature is used.
   *
   * @param usedAt the position this feature was used at, for creating useful
   * error messages
   */
  void markUsed(AbstractFeature f, HasSourcePosition usedAt)
  {
    markUsed(f, false, usedAt);
  }


  /**
   * Get direct redefinitions of given Feature.  This set is filled
   * dynamically with all used features that are found.
   *
   * Result is never null.
   *
   * @param f the original feature
   */
  Set<AbstractFeature>redefinitions(AbstractFeature f)
  {
    var r = _redefinitions.get(f);
    if (r == null)
      {
        r = new TreeSet<>();
        _redefinitions.put(f,r);
      }
    return r;
  }

  /**
   * During FINDING_USED_FEATURES, this sets the flag that this feature is used.
   *
   * @param dynamically true iff this feature is called dynamically, i.e., it
   * has to be part of the dynamic binding data.
   *
   * @param usedAt the position this feature was used at, for creating useful
   * error messages
   */
  void markUsed(AbstractFeature f, boolean dynamically, HasSourcePosition usedAt)
  {
    if (!Clazzes.instance.isUsed(f))
      {
        Clazzes.instance.addUsedFeature(f, usedAt);
        if (f.state() == State.RESOLVED)
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
              { // Since instances of choice types are never created explicitly,
                // they will be marked as used if they are used as a result type
                // of a function or field.
                AbstractFeature rtf = f.resultType().feature();
                if (rtf.isChoice())
                  {
                    markUsed(rtf, usedAt);
                  }
              }
          }
        if (f.isIntrinsic() && f.outerRef() != null)
          {
            markUsed(f.outerRef(), false, usedAt);
          }

        for (var df : _mir._module.declaredFeatures(f).values())
          {
            for (AbstractFeature of : df.redefines())
              {
                if (Clazzes.instance.isUsed(of))
                  {
                    markUsed(df, usedAt);
                  }
                redefinitions(of).add(df);
              }
          }
        for (AbstractFeature rf : redefinitions(f))
          {
            markUsed(rf, usedAt);
          }
        if (f.hasTypeFeature())
          { // NYI: This might mark too many type features. It should be
            // sufficient to mark all type features of types passed as type
            // parameters and of all features that whose ancestors use this.type
            // (i.e., Types.get) to access the current type instance.
            markUsed(f.typeFeature(), false, usedAt);
          }
      }
  }


  void findUsedFeatures(AbstractFeature f)
  {
    if (f.outer() != null)
      {
        markUsed(f.outer(), f);
      }
    for (var fa : f.arguments())
      {
        markUsed(fa, f);
      }
    for (var p: f.inherits())
      {
        markUsed(p.calledFeature(), p);
      }
    findUsedFeatures(f.resultType(), f);

    var fv = new FeatureVisitor() {
        // it does not seem to be necessary to mark all features in types as used:
        // public Type  action(Type    t, AbstractFeature outer) { t.findUsedFeatures(res, pos); return t; }
        public void action(AbstractCall c               ) { findUsedFeatures(c); }
        public void action(AbstractConstant c           ) { findUsedFeatures(c); }
        //        public Expr action(Feature f, AbstractFeature outer) { markUsed(res, pos);      return f; } // NYI: this seems wrong ("f." missing) or unnecessary
        public void action(Tag     t, AbstractFeature outer) { findUsedFeatures(t._taggedType, t); }
        @Override
        public Expr action(InlineArray i, AbstractFeature outer)
        {
          // mark assigned sys-array-field as used
          i.code().visit(this, outer);
          return i;
        }
      };
    f.visitCode(fv);
  }


  /**
   * Mark all features used for this abstract constant as used.
   */
  void findUsedFeatures(AbstractConstant c)
  {
    findUsedFeatures(c.type(), c);
  }


  /**
   * Mark all features used within this type as used.
   */
  void findUsedFeatures(AbstractType t, HasSourcePosition pos)
  {
    if (!t.isGenericArgument())
      {
        markUsed(t.feature(), pos);
        for (var tg : t.generics())
          {
            findUsedFeatures(tg, pos);
          }
      }
  }


  /**
   * Find used features, i.e., mark all features that are found to be the target of a call as used.
   */
  void findUsedFeatures(AbstractCall c)
  {
    if (PRECONDITIONS) require
      (Errors.any() || c.calledFeature() != null);

    var cf = c.calledFeature();
    if (cf != null)
      {
        markUsed(cf, c.isDynamic(), c);
        for (var t : c.actualTypeParameters())
          {
            if (!t.isGenericArgument())
              {
                AbstractFeature f = t.feature();
                markUsed(f, c);  // NYI: needed? If the actual generic type is not called anywhere, maybe it can go
                if (CHECKS) check
                  (Errors.any() || f.hasTypeFeature());

                if (f.hasTypeFeature())
                  {
                    markUsed(f.typeFeature(), c);
                  }
              }
          }
      }
  }

}

/* end of file */
