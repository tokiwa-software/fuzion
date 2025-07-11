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
 * Source of class This
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * This
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class This extends ExprWithPos
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the qualified name of the this that is to be accessed.
   */
  private final List<ParsedName> _qual;


  /**
   * For a This created implicitly for a call in an inherits clause, this gives
   * the current feature this is executed in, i.e. the outer feature of the
   * feature that uses this inherits clause.
   */
  private AbstractFeature _cur = null;

  /**
   * The feature the this expression refers to, i.e,. for a.b.this this is a.b.
   */
  private AbstractFeature _feature;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public This(List<ParsedName> qual)
  {
    super(SourcePosition.range(qual));

    if (PRECONDITIONS) require
      (!qual.isEmpty());

    this._qual = qual;
    this._feature = null;
  }


  /**
   * Constructor to be used during type resolution.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param cur the current feature that contains this expression
   *
   * @param f the outer feature whose instance we want to access.
   */
  private This(SourcePosition pos, AbstractFeature cur, AbstractFeature f)
  {
    super(pos);

    if (PRECONDITIONS) require
      (cur != null || Errors.any(),
       f != null || Errors.any());

    this._qual = null;
    this._cur = cur;
    this._feature = f;
  }


  /**
   * Constructor to be used for implicit This instances to be used before type
   * resolution.
   *
   * @param pos the sourcecode position, used for error messages.
   */
  public This(SourcePosition pos)
  {
    super(pos);

    this._qual = null;
    this._feature = null;
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Create Expression to access f.this during type resolution.  This will
   * create a call to the outer references to access f.this.
   *
   * @param res The Resolution instance to be used for resolveTypes().
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param context the source code context where this This is to be used
   *
   * @param f the outer feature whose instance we want to access.
   *
   * @return the expression to access f.this.
   */
  static Expr thiz(Resolution res, SourcePosition pos, Context context, AbstractFeature f)
  {
    if (PRECONDITIONS) require
      (context != null,
       f != null);

    var outer = context.outerFeature();

    var result = new This(pos, outer, f);

    return res.state(outer) != State.RESOLVING_INHERITANCE &&
           res.state(outer) != State.RESOLVING
      ? result.resolveTypes(res, context)
      : result;
  }


  /*-----------------------------  methods  -----------------------------*/


  @Override
  public UnresolvedType asParsedType()
  {
    return _qual != null ? new QualThisType(_qual)
                         : null;
  }


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  @Override
  AbstractType typeForInferencing()
  {
    return null;  // After type resolution, This is no longer part of the code.
  }


  /**
   * visit all the expressions within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this or an alternative Expr if the action performed during the
   * visit replaces this by the alternative.
   */
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    return v.action(this);
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param context the source code context where this Call is used
   *
   * @return a call to the outer references to access the value represented by
   * this.
   */
  Expr resolveTypes(Resolution res, Context context)
  {
    if (PRECONDITIONS) require
      (res != null || Errors.any(),
       context != null);

    var outer = context.outerFeature();
    if (_qual != null)
      {
        this._feature = getThisFeature(pos(), this, _qual, outer);
      }
    else
      {
        if (this._feature == null)  /* convenience for This(pos) constructor that does not provide outer */
          {
            this._feature = outer;
          }
      }

    Expr getOuter;
    var f = this._feature;
    if (f == null)
      {
        if (CHECKS) check
          (Errors.any());
        f = Types.f_ERROR;
      }
    if (f == Types.f_ERROR)
      {
        getOuter = Call.ERROR;
      }
    else if (f.isUniverse())
      {
        getOuter = Universe.instance;
      }
    else
      {
        /* NYI: Ugly special handling: In case this has been created as part of
         * an inherits call, _cur is outer.outer() since this call is done
         * before outer is set up.
         */
        var cur = _cur == null ? outer : _cur;
        getOuter = new Current(pos(), cur);
        while (f != Types.f_ERROR && cur != f && !cur.isUniverse())
          {
            var or = cur.outerRef();
            if (CHECKS) check
              (Errors.any() || (or != null));
            if (or != null)
              {
                getOuter = new Call(pos(), getOuter, or).resolveTypes(res, context);
              }
            cur = cur.outer();
          }
      }

    return getOuter;
  }


  /**
   * getThisFeature find the outer feature {@code x.y.z.a.b.c} for a given qualified name 'a.b.c' as
   * seen for a feature within outer {@code x.y.z.a.b.c.d.e.f.}.
   *
   * @param thisOrType instance of {@code This} or {@code Type} depending on whether this is a lookup for {@code this} as a value or as a type.
   *
   * @param qual the qualified name
   *
   * @param outer the outer feature that contains the 'this' expression.
   *
   * @return the feature that was found or Types.f_ERROR in case of an error.
   */
  private static AbstractFeature getThisFeature(SourcePosition pos, ANY thisOrType, List<ParsedName> qual, AbstractFeature outer)
  {
    // The comments on the right hand side will give an example to illustrate how this works: Note
    // that indices in outer start from the right, innermost name:
    //
    //                                               outer       is w.x.y.z.a.b.c.d.e
    //                                               qual        is a.b.c
    //                                               qual.size() is 3
    var all = new TreeMap<String, Integer>();     // all.get(b)  is 8,7,6,5,4,3,2,1,0 == positions in outer
    var ambig = new TreeSet<String>();
    var o = outer;
    var d = 0;                                    // d           is 9
    while (o.outer() != null)
      {
        var b = o.featureName().baseName();
        if (all.containsKey(b))
          {
            ambig.add(b);
          }
        all.put(b, d);
        d++;
        o = o.outer();
      }
    var s = qual.getFirst()._name;                // s           is 'a'
    AbstractFeature result = Types.f_ERROR;
    var isAmbiguous = ambig.contains(s);
    var p = isAmbiguous ? -1 : all.getOrDefault(s, -1);
    if (p >= 0)                                   // p           is 4
      {
        var q = p - qual.size() + 1;              // q           is 2, the index of 'c' in outer
        if (q >= 0)
          {
            // we found qual at positions p..q in outer, now check that all these
            // positions contain the correct name:
            var o2 = outer;
            var d2 = 0;
            while (p >= 0 && o2.outer() != null)
              {
                var b = o2.featureName().baseName();
                if (d2 == q)
                  {
                    result = o2;
                  }
                if (p >= d2 && d2 >= q && !b.equals(qual.get(qual.size()-(d2-q)-1)._name))
                  {
                    p = -1;
                    result =  Types.f_ERROR;
                  }
                o2 = o2.outer();
                d2++;
              }
          }
      }
    if (result == Types.f_ERROR)
      { // find all available names to create error:
        var ol   = new List<String>();  // all qualified names starting with o2 found so far
        var list = new List<String>();  // all unambiguous names starting with o2 or later found so far
        var o2 = outer;                  // go backwards from outer to fill ol and list.
        while (o2.outer() != null)
          {
            var b = o2.featureName().baseName();
            ol.add("this");
            ol = ol.map(n -> b + "." + n);   // prefix all entries with o2's base name
            if (!ambig.contains(b))
              {
                list.addAll(ol);             // unless ambiguous, add to list
              }
            o2 = o2.outer();
          }
        AstErrors.outerFeatureNotFoundInThis(pos,
                                             thisOrType,
                                             outer,
                                             qual.map2(x -> x._name).toString("", ".", ""),
                                             list,
                                             isAmbiguous);
      }

    return result;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    if (_qual != null)
      {
        return _qual.map2(n -> n._name)+".this";
      }
    else if (_feature != null)
      {
        return _feature.qualifiedName() + ".this";
      }
    else
      {
        return "this";
      }
  }


}

/* end of file */
