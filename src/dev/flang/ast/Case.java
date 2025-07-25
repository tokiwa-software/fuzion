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
public class Case extends AbstractCase
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /**
   * Field with type from this.type created in case fieldName != null.
   */
  private final Feature _field;
  public AbstractFeature field() { return _field; }


  /**
   * List of types to be matched against. null if we match against type or match
   * everything.
   */
  private List<AbstractType> _types;
  public List<AbstractType> types() { return _types; }


  /**
   * code to be executed in case of a match
   */
  public Expr _code;
  public Expr code() { return _code; }


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Case that assigns the value to a new field
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param t the type to be matched against
   *
   * @param n name of a new field of type t that can be used to access the value
   * of the expression checked by the surrounding match expression.
   *
   * @param c code to be executed in case of a match
   */
  public Case(SourcePosition pos,
              AbstractType t,
              String n,
              Block c)
  {
    this(pos,
         new Feature(pos, Visi.PRIV, t, n)
         {
           @Override
           public boolean isCaseField() { return true; }
         },
         null,
         c);
  }


  /**
   * Constructor for a Case that checks for one or several types at once
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param l List of types to be matched against
   *
   * @param c code to be executed in case of a match
   */
  public Case(SourcePosition pos,
              List<AbstractType> l,
              Expr c)
  {
    this(pos, null, l, c);
  }


  /**
   * Constructor for a Case that matches all cases
   * which are not matched by other cases.
   *
   * @param pos the sourcecode position, used for error messages.
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
   * @param p the sourcecode position, used for error messages.
   *
   * @param f the field declared to hold the value in this case
   *
   * @param l List of types to be matched against
   *
   * @param c code to be executed in case of a match
   */
  private Case(SourcePosition p,
               Feature f,
               List<AbstractType> l,
               Expr c)
  {
    super(p);

    if (PRECONDITIONS) require
      (p != null,
       (l == null) || (f == null),  // if l is non-null, t is null
       c != null                    // code is never null
       );

    _field = f;
    _types = l;
    _code  = c;
    if (f != null)
      {
        if (f.returnType().functionReturnType() instanceof UnresolvedType ut)
          {
            ut.doIgnoreEmptyActualTypePars();
          }
      }
    if (l != null)
      {
        for (var t : l)
          {
            if (t instanceof UnresolvedType ut)
              {
                ut.doIgnoreEmptyActualTypePars();
              }
          }
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   */
  @Override
  public void visit(FeatureVisitor v, AbstractMatch m, AbstractFeature outer)
  {
    v.actionBefore(this, m);
    if (_field != null)
      {
        _field.visit(v, outer);
      }
    _code = _code.visit(v, outer);
    v.actionAfter(this, m);
  }


  /**
   * Resolve types in this case.  Produce an error in case it does not match any
   * of the subject's types or if it matches several of the subject's types.
   *
   * @param cgs the choiceGenerics of the match's subject's type
   *
   * @param context the source code context where this Case is used
   *
   * @param matched map from index in cgs to source position for all matches
   * that have already been found.  This is updated and used to report an error
   * in case there are repeated matches.
   *
   * @return true iff all types could be resolved, false if any type resolution
   * failed and the type was set to Types.t_ERROR.
   */
  boolean resolveType(Resolution res, List<AbstractType> cgs, Context context, SourcePosition[] matched)
  {
    boolean result = true;
    if (_field != null)  // matching 'x type'
      {
        var t = _field.returnType().functionReturnType(true);
        var rt = resolveType(res, t, cgs, context, matched);
        _field._returnType = new FunctionReturnType(rt);
        result &= rt != Types.t_ERROR;
      }
    else if (_types != null)  // matching 'type1, type2, type3'
      {
        var ti = _types.listIterator();
        while (ti.hasNext())
          {
            var t = ti.next();
            var rt = resolveType(res, t, cgs, context, matched);
            ti.set(rt);
            result &= rt != Types.t_ERROR;
          }
      }
    else  // matching '*'
      {
        _types = new List<>();
        int i = 0;
        for (var cg : cgs)
          {
            if (matched[i] == null)
              {
                _types.add(cg);
                matched[i] = pos();
              }
            i++;
          }
        if (_types.isEmpty())
          {
            if (cgs.isEmpty())
              {
                AstErrors.matchCaseDoesNotMatchAny(pos(), null, cgs);
              }
            else
              {
                AstErrors.repeatedMatch(pos(), matched, null, cgs);
              }
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
   * @param context the source code context where this Case is used
   *
   * @param matched map from index in cgs to source position for all matches
   * that have already been found.  This is updated and used to report an error
   * in case there are repeated matches.
   */
  private AbstractType resolveType(Resolution res, AbstractType t, List<AbstractType> cgs, Context context, SourcePosition[] matched)
  {
    var original_t = t;
    List<AbstractType> matches = new List<>();
    int i = 0;
    t = t.resolve(res, context);
    var inferGenerics = !t.isGenericArgument() && (t.isThisType() || t.generics().isEmpty()) && !t.feature().typeArguments().isEmpty();
    var hasErrors = t.containsError();
    check
      (!hasErrors || Errors.any());
    for (var cg : cgs)
      {
        if (CHECKS) check
          (Errors.any() || cg != null);
        if (cg != null &&
            (inferGenerics  && !cg.isGenericArgument() && t.feature() == cg.feature() /* match feature, take generics from cg */ ||
             !inferGenerics && t.compareTo(cg) == 0                    /* match exactly */ ))
          {
            t = cg;
            hasErrors = hasErrors || t.containsError();
            check
              (!hasErrors || Errors.any());
            matches.add(cg);
            if (matched[i] != null && !hasErrors)
              {
                AstErrors.repeatedMatch(pos(), matched[i], t, cgs);
              }
            matched[i] = pos();
          }
        i++;
      }
    if (matches.isEmpty())
      {
        if (!hasErrors)
          {
            AstErrors.matchCaseDoesNotMatchAny(pos(), original_t, cgs);
          }
        t = Types.t_ERROR;
      }
    else if (!hasErrors && matches.size() != 1)
      {
        AstErrors.matchCaseMatchesSeveral(pos(), original_t, cgs, matches);
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
    if (_field != null)
      {
        sb.append(_field.featureName().baseNameHuman() + " " + _field.returnType());
      }
    else if (_types == null)
      {
        sb.append("*");
      }
    else
      {
        boolean first = true;
        for (var t : _types)
          {
            sb.append(first ? "" : ", ");
            sb.append(t.toString(true));
            first = false;
          }
      }
    sb.append(" => ").append(code());

    return sb.toString();
  }

}
