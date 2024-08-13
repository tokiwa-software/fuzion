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
 * Source of class Context
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.ANY;

/**
 * Context represents the context of Expressions that affects how these Exprs
 * are interpreted. In particular, in Fuzion code like
 *
 *   f(v T) =>
 *      if      T : String then
 *        say "String of {v.codepoint_length} codepoints"
 *      else if T : integer then
 *        say "integer, neg is {-v}"
 *
 * The context of the calls to `say` would contain the constraints `T : String`
 * or `T : integer`.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Context extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Pre-allocated instance of no context.
   */
  public static final Context NONE = new Context()
    {
      @Override AbstractFeature outerFeature()
      {
        throw new Error("outerFeature missing in context!");
      }
      @Override Context exterior()
      {
        return null;
      }
      @Override String localToString()
      {
        return "NO CONTEXT";
      }
    };

  /*----------------------------  variables  ----------------------------*/


  /**
   * Contexts are nested. This method provides the surrounding context of null
   * if this=NONE, i..e, there is no surrounding context.
   */
  abstract Context exterior();


  /*-------------------------  static methods  --------------------------*/



  /**
   * Create the default context for the given feature f.
   *
   * The result is a context whose `outerFeature()` equals to `f` and whose
   * `exterior()` context is the contains the source code context of the
   * declaration of `f`.
   *
   * This means that any type constraints that are made outside of the
   * declaration of `f` will be part of the constraints on the result.
   */
  static Context forFeature(AbstractFeature f)
  {
    return new Context()
      {

        @Override AbstractFeature outerFeature()
        {
          return f;
        }

        @Override Context exterior()
        {
          return f instanceof Feature ff ? ff._sourceCodeContext
                                         : NONE;
        }

        @Override String localToString()
        {
          return f.qualifiedName() + " at " + f.pos().show();
        }

        @Override
        public AbstractType constraintFor(AbstractFeature typeParameter)
        {
          if (f instanceof Feature ff)
            {
              for (var c : ff.contract()._declared_preconditions)
                {
                  if (c.cond instanceof Call cc &&
                      cc.calledFeatureKnown() &&
                      cc.calledFeature() == Types.resolved.f_Type_infix_colon &&
                      cc.target() instanceof Call tc &&
                      tc.calledFeature() == typeParameter)
                    {
                      return cc.actualTypeParameters().get(0);
                    }
                }
            }
          return super.constraintFor(typeParameter);
        }
      };
  }


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor, used to crate anonymous inner classes.
   */
  private Context()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Check if the current context defines any constraint for the given type
   * parameter. If so, return that constraint. Otherwise, return null.
   *
   * @param typeParameter a type parameter feature.
   */
  public AbstractType constraintFor(AbstractFeature typeParameter)
  {
    var e = exterior();
    return e != null ? e.constraintFor(typeParameter)
                     : null;
  }


  /**
   * Create a new context that adds the constraint imposed by a call `T : x` to
   * this context.
   */
  public Context addTypeConstraint(AbstractCall infix_colon_call)
  {
    if (PRECONDITIONS) require
      (infix_colon_call.calledFeature() == Types.resolved.f_Type_infix_colon);

    var result = this;
    if (infix_colon_call.target() instanceof AbstractCall t)
      {
        result = new Context()
          {
            @Override Context exterior()
            {
              check(this != Context.this);
              return Context.this;
            }
            @Override
            public AbstractType constraintFor(AbstractFeature typeParameter)
            {
              if (t.calledFeature() == typeParameter)
                {
                  return infix_colon_call.actualTypeParameters().get(0);
                }
              return super.constraintFor(typeParameter);
            }

            @Override
            String localToString()
            {
              return "Type context at " + infix_colon_call.pos().show();
            }
          };
      }
    return result;
  }


  /**
   * Create a String describing this Context without the exterior(), for debugging.
   */
  abstract String localToString();


  AbstractFeature outerFeature()
  {
    return exterior().outerFeature();
  }


  /**
   * Create a String describing this Context with the exterior(), for debugging.
   * Uses localToString().
   */
  @Override
  public String toString()
  {
    return toString(localToString());
  }


  /**
   * Create a String describing this Context with the exterior(), for debugging.
   * Uses localToString().
   */
  private String toString(String inner)
  {
    var r = inner.replace("\n", "\n| ") + "\n" + localToString();
    var e = exterior();
    return e == null ? r : e.toString(r);
  }

}

/* end of file */
