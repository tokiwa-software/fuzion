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
 * Source of class Case
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.ListIterator;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Case represents one case in a match expression, e.g.,
 *
 *   A,B => { a; }
 *
 * or
 *
 *   C c => { c.x; },
 *
 * or
 *
 *   *   => { q; }
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Case extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this case, used for error messages.
   */
  public final SourcePosition pos;


  /**
   * Field with type from this.type created in case fieldName != null.
   */
  public final Feature field;


  /**
   * List of types to be matched against. null if we match against type or match
   * everything.
   */
  public final List<Type> types;


  /**
   * code to be executed in case of a match
   */
  public Block code;


  /**
   * Counter for a unique id for this case statement. This is used to store data
   * in the runtime clazz for this case.
   */
  public int runtimeClazzId_ = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Case that assigns the value to a new field
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param t the type to be matched against
   *
   * @param n name of a new field of type t that can be used to access the value
   * of the expression checked by the surrounding match expression.
   *
   * @param c code to be executed in case of a match
   */
  public Case(SourcePosition pos,
              Type t,
              String n,
              Block c)
  {
    this(pos, new Feature(pos, Consts.VISIBILITY_PRIVATE, t, n), null, c);
  }


  /**
   * Constructor for a Case that checks for one or several types at once
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param l List of types to be matched against
   *
   * @param c code to be executed in case of a match
   */
  public Case(SourcePosition pos,
              List<Type> l,
              Block c)
  {
    this(pos, null, l, c);
  }


  /**
   * Constructor for a Case that matches all cases
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param c code to be executed in case of a match
   */
  public Case(SourcePosition pos,
              Block c)
  {
    this(pos, (Feature) null, null, c);
  }


  /**
   * Constructor for a Case that assigns the value to a new field
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param f the field declared to hold the value in this case
   *
   * @param l List of types to be matched against
   *
   * @param c code to be executed in case of a match
   */
  private Case(SourcePosition p,
               Feature f,
               List<Type> l,
               Block c)
  {
    if (PRECONDITIONS) require
      (p != null,
       (l == null) || (f == null),  // if l is non-null, t is null
       c != null                    // code is never null
       );

    pos   = p;
    field = f;
    types = l;
    code  = c;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  public void visit(FeatureVisitor v, Feature outer)
  {
    v.actionBefore(this, outer);
    if (field != null)
      {
        field.visit(v, outer);
      }
    if (types != null)
      {
        ListIterator<Type> i = types.listIterator();
        while (i.hasNext())
          {
            i.set(i.next().visit(v, outer));
          }
      }
    code = code.visit(v, outer);
    v.actionAfter(this, outer);
  }


  /**
   * Resolve types in this case.  Produce an error in case it does not match any
   * of the subject's types or if it matches several of the subject's types.
   *
   * @param cgs the choiceGenerics of the match's subject's type
   *
   * @param outer the outer feature that contains this match statement
   *
   * @param matched map from index in cgs to source position for all matches
   * that have already beend found.  This is updated and used to report an error
   * in case there are repeated matches.
   *
   * @return true iff all types could be resolved, false if any type resolution
   * failed and the type was set to Types.t_ERROR.
   */
  boolean resolveType(Resolution res, List<Type> cgs, Feature outer, SourcePosition[] matched)
  {
    boolean result = true;
    if (field != null)
      {
        var t = field.returnType().functionReturnType();
        var rt = resolveType(res, t, cgs, outer, matched);
        field._returnType = new FunctionReturnType(rt);
        result &= rt != Types.t_ERROR;
      }
    if (types != null)
      {
        ListIterator<Type> ti = types.listIterator();
        while (ti.hasNext())
          {
            var t = ti.next();
            var rt = resolveType(res, t, cgs, outer, matched);
            ti.set(rt);
            result &= rt != Types.t_ERROR;
          }
      }
    return result;
  }


  /**
   * Resolve one type found in a case. Produce an error in case it does not
   * match any of the subject's types or if it matches several of the subject's
   * types.
   *
   * @param t the type within this case we are resolving
   *
   * @param cgs the choiceGenerics of the match's subject's type
   *
   * @param outer the outer feature that contains this match statement
   *
   * @param matched map from index in cgs to source position for all matches
   * that have already beend found.  This is updated and used to report an error
   * in case there are repeated matches.
   */
  Type resolveType(Resolution res, Type t, List<Type> cgs, Feature outer, SourcePosition[] matched)
  {
    var original_t = t;
    List<Type> matches = new List<Type>();
    int i = 0;
    t.resolveFeature(res, outer);
    var inferGenerics = !t.isGenericArgument() && t._generics.isEmpty() && t.featureOfType().generics() != FormalGenerics.NONE;
    if (!inferGenerics)
      {
        t = t.resolve(res, outer);
      }
    for (var cg : cgs)
      {
        if (inferGenerics  && t.featureOfType() == cg.featureOfType() /* match feature, take generics from cg */ ||
            !inferGenerics && t == cg                                 /* match exactly */ )
          {
            t = cg;
            matches.add(cg);
            if (matched[i] != null)
              {
                AstErrors.repeatedMatch(pos, matched[i], t, cgs);
              }
            matched[i] = pos;
          }
        i++;
      }
    if (matches.size() != 1)
      {
        if (matches.isEmpty())
          {
            if (t == Types.t_ERROR)
              {
                check
                  (Errors.count() > 0);
              }
            else
              {
                AstErrors.matchCaseDoesNotMatchAny(pos, original_t, cgs);
                t = Types.t_ERROR;
              }
          }
        else
          {
            AstErrors.matchCaseMatchesSeveral(pos, original_t, cgs, matches);
          }
      }
    return t;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    var sb = new StringBuilder();
    if (field != null)
      {
        sb.append(field.featureName().baseName() + " " + field.returnType());
      }
    else if (types == null)
      {
        sb.append("*");
      }
    else
      {
        boolean first = true;
        for (var t : types)
          {
            sb.append(first ? "" : ", ");
            sb.append(t.toString());
            first = false;
          }
      }
    sb.append(" => ").append(code);

    return sb.toString();
  }

}
