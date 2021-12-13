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
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * Destructure represents syntactic sugar for a destructuring assignment of the form
 *
 * (a,b) = point;
 *
 * which will be converted into
 *
 * tmp := point;
 * a = tmp.x;
 * b = tmp.y;
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Destructure extends ANY implements Stmnt
{


  /*-------------------------  static variables -------------------------*/


  /**
   * quick-and-dirty way to make unique names for tmp fields
   */
  static private long id = 0;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The soucecode position of this destructure, used for error messages.
   */
  final SourcePosition _pos;


  /**
   * The field names of the fields we are destructuring into. May not be empty.
   * null if _fields != null.
   */
  final List<String> _names;


  /**
   * The fields created by this destructuring.  May be empty. null if _names !=
   * null.
   */
  final List<Feature> _fields;


  final boolean _isDefinition;

  /**
   * The value that will be destructured
   */
  Expr _value;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the soucecode position, used for error messages.
   *
   * @param n
   *
   * @param def
   *
   * @param v
   */
  private Destructure(SourcePosition pos, List<String> n, List<Feature> fs, boolean def, Expr v)
  {
    if (PRECONDITIONS) require
      (pos != null,
       !def || fs != null,
       v != null);

    _pos = pos;
    _names = n;
    _fields = fs;
    _isDefinition = def;
    _value = v;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The soucecode position of this statment, used for error messages.
   */
  public SourcePosition pos()
  {
    return _pos;
  }


  /**
   * Helper routine for create to expand this into a block of statements.
   */
  private Stmnt expand()
  {
    List<Stmnt> stmnts = new List<Stmnt>();
    if (_fields != null)
      {
        stmnts.addAll(_fields);
      }
    stmnts.add(this);
    return new Block(_pos,stmnts);
  }


  /**
   * Create a Destructure instance during parsing.
   *
   * @param pos the source code position
   *
   * @param fields the fields we are destructuring into in case they are declared
   * within the destructure statement
   *
   * @param names the names of the variables to store the destructured values if
   * we are not destructuring to new fields.
   *
   * @param def true if destructuring using :=
   *
   * @param v the value that is destructured.
   *
   * @return a statement that implements the destructuring.
   */
  public static Stmnt create(SourcePosition pos, List<Feature> fields, List<String> names, boolean def, Expr v)
  {
    if (PRECONDITIONS) require
      ((fields == null) != (names == null),
       !def || (names != null && fields == null));

    if (fields == null)
      {
        if (def)
          {
            fields = new List<Feature>();
            for (String name : names)
              {
                fields.add(new Feature(pos,
                                       Consts.VISIBILITY_LOCAL,
                                       0,
                                       new FunctionReturnType(Types.t_UNDEFINED), // NoType.INSTANCE,
                                       new List<String>(name),
                                       FormalGenerics.NONE,
                                       new List<Feature>(),
                                       new List<>(),
                                       new Contract(null, null, null),
                                       Impl.FIELD));
              }
          }
      }
    else
      {
        check
          (!def);
        names = new List<String>();
        for (Feature f : fields)
          {
            names.add(f.featureName().baseName());
          }
      }
    return new Destructure(pos, names, fields, def, v).expand();
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this
   */
  public Stmnt visit(FeatureVisitor v, Feature outer)
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
                         Feature outer,
                         List<Stmnt> stmnts,
                         Feature tmp,
                         AbstractFeature f,
                         Iterator<String> names,
                         int select,
                         Iterator<Feature> fields,
                         AbstractType t)
  {
    Expr thiz     = This.thiz(res, _pos, outer, outer);
    Call thiz_tmp = new Call(_pos, thiz    , tmp, -1    ).resolveTypes(res, outer);
    Call call_f   = new Call(_pos, thiz_tmp, f  , select).resolveTypes(res, outer);
    Assign assign = null;
    if (fields != null && fields.hasNext())
      {
        Feature newF = fields.next();
        if (_isDefinition)
          {
            newF._returnType = new FunctionReturnType(t);
          }
        assign = new Assign(res, _pos, newF, call_f, outer);
      }
    else if (fields == null && names.hasNext())
      {
        String name = names.next();
        if (!name.equals("_"))
          {
            assign = new Assign(_pos, name, call_f);
          }
      }
    if (assign != null)
      {
        assign.resolveTypes(res, outer, this);
        stmnts.add(assign);
      }
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res the resolution instance.
   *
   * @param outer the root feature that contains this statement.
   */
  public Stmnt resolveTypes(Resolution res, Feature outer)
  {
    List<Stmnt> stmnts = new List<>();
    // NYI: This might fail in conjunction with type inference.  We should maybe
    // create the dcomposition code later, after resolveTypes is done.
    var t = _value.type();
    if (t.isGenericArgument())
      {
        AstErrors.destructuringForGeneric(_pos, t, _names);
      }
    else if (t != Types.t_ERROR)
      {
        _names
          .stream()
          .filter(n -> !n.equals("_"))
          .filter(n -> Collections.frequency(_names, n) > 1)
          .forEach(n -> AstErrors.destructuringRepeatedEntry(_pos, n, Collections.frequency(_names, n)));
        Feature tmp = new Feature(res,
                                  _pos,
                                  Consts.VISIBILITY_PRIVATE,
                                  t,
                                  "#destructure" + id++,
                                  outer);
        tmp.scheduleForResolution(res);
        stmnts.add(tmp.resolveTypes(res, outer));
        Assign atmp = new Assign(res, _pos, tmp, _value, outer);
        atmp.resolveTypes(res, outer);
        stmnts.add(atmp);
        Iterator<String> names = _names.iterator();
        Iterator<Feature> fields = _fields == null ? null : _fields.iterator();
        List<String> fieldNames = new List<>();
        for (var f : t.featureOfType().arguments())
          {
            // NYI: check if f is visible
            var tf = f.resultTypeIfPresent(res, Type.NONE);
            if (tf != null && tf.isOpenGeneric())
              {
                Generic g = tf.genericArgument();
                var frmlTs = g.replaceOpen(t.generics());
                int select = 0;
                for (var tfs : g.replaceOpen(t.generics()))
                  {
                    fieldNames.add(f.featureName().baseName() + "." + select);
                    addAssign(res, outer,stmnts, tmp, f, names, select, fields, tfs);
                    select++;
                  }
              }
            else
              {
                fieldNames.add(f.featureName().baseName());
                addAssign(res, outer, stmnts, tmp, f, names, -1, fields, tf);
              }
          }
        if (fieldNames.size() != _names.size())
          {
            AstErrors.destructuringMisMatch(_pos, fieldNames, _names);
          }
      }
    else if (_fields != null && _isDefinition)
      { // in case of an error in value, set the type of fields to Types.t_ERROR
        // to avoid subsequent errors:
        for (var f : _fields)
          {
            f._returnType = new FunctionReturnType(Types.t_ERROR);
          }
      }
    return new Block(_pos, stmnts);
  }


  /**
   * Does this statement consist of nothing but declarations? I.e., it has no
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
