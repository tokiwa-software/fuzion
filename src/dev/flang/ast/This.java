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
 * Source of class Self
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * This <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class This extends ExprWithPos
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * the qualified name of the this that is to be accessed.
   */
  final List<String> qual_;


  /**
   * For a This created implicitly for a call in an inherits clause, this gives
   * the current feature this is executed in, i.e. the outer feature of the
   * feature that uses this inherits clause.
   */
  private AbstractFeature cur_ = null;

  /**
   * The feature the this expression refers to, i.e,. for a.b.this this is a.b.
   */
  private AbstractFeature feature_;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param qual
   *
   * @param a
   */
  public This(SourcePosition pos, List<String> qual)
  {
    super(pos);

    if (PRECONDITIONS) require
      (!qual.isEmpty());

    this.qual_ = qual;
    this.feature_ = null;
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
  public This(SourcePosition pos, AbstractFeature cur, AbstractFeature f)
  {
    super(pos);

    if (PRECONDITIONS) require
      (cur != null,
       f != null);

    this.qual_ = null;
    this.cur_ = cur;
    this.feature_ = f;
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

    this.qual_ = null;
    this.feature_ = null;
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
   * @param cur the current feature that contains this this expression
   *
   * @param f the outer feature whose instance we want to access.
   *
   * @param the type resolved expression to access f.this.
   */
  public static Expr thiz(Resolution res, SourcePosition pos, AbstractFeature cur, AbstractFeature f)
  {
    return new This(pos, cur, f).resolveTypes(res, cur);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForFeatureResultTypeInferencing returns the type of this expression or
   * null if the type is still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForFeatureResultTypeInferencing()
  {
    return null;  // After type resolution, This is no longer part of the code.
  }


  /**
   * visit all the features, expressions, statements within this feature.
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
    return v.action(this, outer);
  }


  /**
   * Find all the types used in this that refer to formal generic arguments of
   * this or any of this' outer classes.
   *
   * @param outer the root feature that contains this statement.
   */
  public void findGenerics(AbstractFeature outer)
  {
    // NYI: Check if qual starts with the name of a formal generic in outer or
    // outer.outer..., flag an error if this is the case.
  }


  /**
   * determine the static type of all expressions and declared features in this feature
   *
   * @param res this is called during type resolution, res gives the resolution
   * instance.
   *
   * @param outer the root feature that contains this statement.
   *
   * @return a call to the outer references to access the value represented by
   * this.
   */
  public Expr resolveTypes(Resolution res, AbstractFeature outer)
  {
    Type type;
    if (qual_ != null)
      {
        int d = getThisDepth(outer);
        if (d < 0)
          {
            if (CHECKS) check
              (Errors.count() > 0);
            this.feature_ = Types.f_ERROR;
          }
        else
          {
            var f = outer;
            for (int i=0; i<d; i++)
              {
                f = f.outer();
              }
            this.feature_ = f;
          }
      }
    else
      {
        if (this.feature_ == null)  /* convenience for This(pos) constructor that does not provide outer */
          {
            this.feature_ = outer;
          }
      }

    Expr getOuter = null;
    var f = this.feature_;
    if (f.isUniverse())
      {
        getOuter = new Universe();
      }
    else
      {
        /* NYI: Ugly special handling: In case this has been created as part of
         * an inherits call, cur_ is outer.outer() since this call is done
         * before outer is set up.
         */
        var cur = cur_ == null ? outer : cur_;
        getOuter = new Current(pos(), cur.thisType());
        while (f != Types.f_ERROR && cur != f)
          {
            var or = cur.outerRef();
            if (CHECKS) check
              (Errors.count() > 0 || (or != null));
            if (or != null)
              {
                Expr c = new Call(pos(), or.featureName().baseName(), Call.NO_GENERICS, Expr.NO_EXPRS, getOuter, or, null)
                  .resolveTypes(res, outer);
                if (cur.isOuterRefAdrOfValue())
                  {
                    c = new Unbox(c, cur.outer().thisType(), cur.outer())
                      { public SourcePosition pos() { return This.this.pos(); } };
                  }
                getOuter = c;
              }
            cur = cur.outer();
          }
      }

    return getOuter;
  }


  /**
   * Resolve syntatictic suger. In this case, This is removed completely, it has
   * been replaced during resolveTypes by calls to the outer reference this
   * refers to.
   *
   * @return the Expr this was replaced by.
   */
  public Expr resolveSyntacticSugar()
  {
    throw new Error("This should disappear after type resolution");
  }


  /**
   * Check if this is an implicit access to the universe, i.e., for a feature
   * call f.g.h where f is found in the universe, this call will be converted to
   * "universe.this.f.g.h", this returns true for "universe.this".
   *
   * NYI: CLEANUP: This is used only in Feature.isChoice, which should be
   * improved not to need this.
   */
  public static boolean isUniverse(Expr e)
  {
    return
      (e instanceof This) &&
      ((This) e).feature_.isUniverse();
  }


  /**
   * getThisDepth
   *
   * @param feat
   *
   * @return
   */
  private int getThisDepth(AbstractFeature feat)
  {
    Iterator<String> it = qual_.iterator();
    int d = getDepth(0, feat, it.next(), it);
    if (d < 0)
      {
        var qname = new StringBuilder();
        for (var name : qual_)
          {
            qname.append(qname.length() > 0 ? "." : "")
              .append(name);
          }
        var list = new List<String>();
        var o = feat;
        while (o.outer() != null) // exclude universe
          {
            list.add(o.qualifiedName());
            o = o.outer();
          }
        AstErrors.outerFeatureNotFoundInThis(this, feat, qname.toString(), list);
      }

    return d;
  }


  /**
   * recursive helper function for getThisDepth()
   */
  private int getDepth(int d, AbstractFeature feat, String name, Iterator<String> it)
  {
    if (feat.featureName().baseName().equals(name))
      {
        return d;
      }
    else
      {
        var outer = feat.outer();
        if (outer == null)
          {
            d = -1;
          }
        else
          {
            d = getDepth(d + 1, outer, name, it);
            if ((d >= 0) && it.hasNext())
              {
                d = it.next().equals(feat.featureName().baseName()) ? d - 1
                                                                    : -1;
              }
          }
      }
    return d;
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    if (qual_ != null)
      {
        return qual_+".this";
      }
    else if (feature_ != null)
      {
        return feature_.qualifiedName() + ".this";
      }
    else
      {
        return "this";
      }
  }


}

/* end of file */
