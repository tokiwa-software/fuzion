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
 * Source of class Assign
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Collections;
import java.util.Iterator;

import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Destructure represents syntactic sugar for a destructuring assignment of the form
 *
 * (a,b) := point;
 *
 * which will be converted into
 *
 * tmp := point;
 * a   := tmp.x;
 * b   := tmp.y;
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Destructure extends ExprWithPos
{


  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for tmp fields
   */
  static private long id = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The field names of the fields we are destructuring into.
   */
  final List<ParsedName> _names;


  /**
   * The fields created by this destructuring.
   */
  final List<AbstractFeature> _fields;


  /**
   * The value that will be destructured
   */
  Expr _value;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param n The field names of the fields we are destructuring into.
   *
   * @param fs The fields created by this destructuring.
   *
   * @param v The value that will be destructured
   */
  private Destructure(SourcePosition pos, List<ParsedName> n, List<AbstractFeature> fs, Expr v)
  {
    super(pos);

    if (PRECONDITIONS) require
      (pos != null,
       n != null,
       fs != null,
       v != null);

    _names = n;
    _fields = fs;
    _value = v;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Helper routine for create to expand this into a block of expressions.
   */
  private Expr expand()
  {
    List<Expr> exprs = new List<Expr>();
    exprs.add(this);
    for (var f : _fields)
      {
        exprs.add((Feature) f);
      }
    return new Block(exprs);
  }


  /**
   * Create a Destructure instance during parsing.
   *
   * @param pos the source code position
   *
   * @param fields the fields we are destructuring into in case they are declared
   * within the destructure expression
   *
   * @param names the names of the variables to store the destructured values if
   * we are not destructuring to new fields.
   *
   * @param v the value that is destructured.
   *
   * @return a expression that implements the destructuring.
   */
  public static Expr create(SourcePosition pos, List<AbstractFeature> fields, List<ParsedName> names, Expr v)
  {
    if (PRECONDITIONS) require
      ((fields == null) != (names == null));

    if (fields == null)
      {
        fields = new List<AbstractFeature>();
        for (var name : names)
          {
            fields.add(new Feature(name._pos,
                                   Visi.PRIV,
                                   0,
                                   new FunctionReturnType(Types.t_UNDEFINED), // NoType.INSTANCE,
                                   new List<String>(name._name),
                                   new List<>(),
                                   new List<>(),
                                   Contract.EMPTY_CONTRACT,
                                   Impl.FIELD));
          }
      }
    else
      {
        names = new List<>();
        for (var f : fields)
          {
            names.add(new ParsedName(f.pos(), f.featureName().baseName()));
          }
      }
    return new Destructure(pos, names, fields, v).expand();
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this
   */
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    _value = _value.visit(v, outer);
    return v.action(this, outer);
  }


  /**
   * Helper routine for resolveTypes.
   *
   + NYI: Document
   */
  private void addAssign(Resolution res,
                         AbstractFeature outer,
                         Context context,
                         List<Expr> exprs,
                         Feature tmp,
                         AbstractFeature f,
                         Iterator<ParsedName> names,
                         int select,
                         Iterator<AbstractFeature> fields,
                         AbstractType t)
  {
    Expr thiz     = This.thiz(res, pos(), outer, context, outer);
    Call thiz_tmp = new Call(pos(), thiz    , tmp, -1    ).resolveTypes(res, outer, context);
    Call call_f   = new Call(pos(), thiz_tmp, f  , select).resolveTypes(res, outer, context);
    Assign assign = null;
    if (fields != null && fields.hasNext())
      {
        var newF = (Feature) fields.next();
        newF._returnType = new FunctionReturnType(t);
        assign = new Assign(res, pos(), newF, call_f, outer, context);
      }
    else if (fields == null && names.hasNext())
      {
        var pn = names.next();
        var name = pn._name;
        if (!name.equals("_"))
          {
            assign = new Assign(pn._pos, name, call_f);
          }
      }
    if (assign != null)
      {
        assign.resolveTypes(res, outer, context, this);
        exprs.add(assign);
      }
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param context the source code context where this Call is used
   */
  public Expr resolveTypes(Resolution res, Context context)
  {
    return resolveTypes(res, context.outerFeature(), context);
  }
  public Expr resolveTypes(Resolution res, AbstractFeature outer, Context context)
  {
    if (PRECONDITIONS) require(outer == context.outerFeature());
    List<Expr> exprs = new List<>();
    // NYI: This might fail in conjunction with type inference.  We should maybe
    // create the decomposition code later, after resolveTypes is done.
    var t = _value.type();
    if (t.isGenericArgument())
      {
        AstErrors.destructuringForGeneric(pos(), t, _names);
      }
    else if (t != Types.t_ERROR)
      {
        _names
          .stream()
          .map(n -> n._name)
          .filter(n -> !n.equals("_"))
          .filter(n -> Collections.frequency(_names, n) > 1)
          .forEach(n -> AstErrors.destructuringRepeatedEntry(pos(), n, Collections.frequency(_names, n)));
        Feature tmp = new Feature(res,
                                  pos(),
                                  Visi.PRIV,
                                  t,
                                  FuzionConstants.DESTRUCTURE_PREFIX + id++,
                                  outer);
        tmp.scheduleForResolution(res);
        exprs.add(new Assign(res, pos(), tmp, _value, outer, context));
        var names = _names.iterator();
        var fields = _fields.iterator();
        List<String> fieldNames = new List<>();
        for (var f : t.feature().valueArguments())
          {
            // NYI: check if f is visible
            var tf = f.resultTypeIfPresent(res, UnresolvedType.NONE);
            if (tf != null && tf.isOpenGeneric())
              {
                Generic g = tf.genericArgument();
                int select = 0;
                for (var tfs : g.replaceOpen(t.generics()))
                  {
                    fieldNames.add(f.featureName().baseName() + "." + select);
                    addAssign(res, outer, context, exprs, tmp, f, names, select, fields, tfs);
                    select++;
                  }
              }
            else
              {
                fieldNames.add(f.featureName().baseName());
                addAssign(res, outer, context, exprs, tmp, f, names, -1, fields, tf);
              }
          }
        if (fieldNames.size() != _names.size())
          {
            AstErrors.destructuringMisMatch(pos(), fieldNames, _names);
          }
      }
    else
      { // in case of an error in value, set the type of fields to Types.t_ERROR
        // to avoid subsequent errors:
        for (var f : _fields)
          {
            ((Feature) f)._returnType = new FunctionReturnType(Types.t_ERROR);
          }
      }
    return new Block(exprs);
  }


  /**
   * Does this expression consist of nothing but declarations? I.e., it has no
   * code that actually would be executed at runtime.
   */
  public boolean containsOnlyDeclarations()
  {
    throw new Error("Destructure should have disappeared after resolveTypes");
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "(" + _names + ") = " + _value;
  }

}

/* end of file */
