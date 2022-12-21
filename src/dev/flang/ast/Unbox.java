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
 * Source of class Unbox
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Iterator;

import dev.flang.util.Errors;


/**
 * Unbox is an expression that dereferences an address of a value type to
 * the value type.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class Unbox extends Expr
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The address of the value type
   */
  public Expr _adr;


  /**
   * The type of this, set during creation.
   */
  private AbstractType _type;


  /**
   * Is this Unbox needed, i.e, not a NOP. This might be a NOP if this is
   * used as a reference.
   */
  public boolean _needed = false;


  /**
   * Clazz index for value clazz that is being unboxed and, at
   * _refAndValClazzId+1, value clazz that is the result clazz of the unboxing.
   */
  public int _refAndValClazzId = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   *
   * @param adr the expression (outer ref) leading to the outer ref value that should
   * be unboxed.
   *
   * @param type the result type
   */
  public Unbox(Expr adr, AbstractType type)
  {
    if (PRECONDITIONS) require
      (adr != null,
       adr.type().isRef() || adr instanceof AbstractCall c && c.calledFeature().isOuterRef(),
       !type.featureOfType().isThisRef()
       );

    this._adr = adr;
    this._type = Types.intern(type); // outer.thisType().resolve(outer);
  }


  /**
   * Constructor
   *
   * @param adr the expression (outer ref) leading to the outer ref value that should
   * be unboxed.
   *
   * @param t the result type
   */
  public Unbox(Expr adr, AbstractType type, AbstractFeature outer)
  {
    this(adr, type);

    if (PRECONDITIONS) require
      (adr != null,
       adr.type().isRef(),
       Errors.count() > 0 || type.featureOfType() == outer,
       !type.featureOfType().isThisRef()
       );
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * type returns the type of this expression or Types.t_ERROR if the type is
   * still unknown, i.e., before or during type resolution.
   *
   * @return this Expr's type or t_ERROR in case it is not known yet.
   */
  public AbstractType type()
  {
    return _type;
  }


  /**
   * visit all the features, expressions, statements within this feature.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited objects.
   *
   * @param outer the feature surrounding this expression.
   *
   * @return this.
   */
  public Unbox visit(FeatureVisitor v, AbstractFeature outer)
  {
    _adr = _adr.visit(v, outer);
    v.action(this, outer);
    return this;
  }


  /**
   * visit all the statements within this Unbox.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited statements
   */
  public void visitStatements(StatementVisitor v)
  {
    _adr.visitStatements(v);
    super.visitStatements(v);
  }


  /**
   * Check if this value might need boxing, unboxing or tagging and wrap this
   * into Box()/Tag() if this is the case.
   *
   * @param frmlT the formal type this is assigned to.
   *
   * @return this or an instance of Box wrapping this.
   */
  Expr box(AbstractType frmlT)
  {
    var t = type();
    if (t.compareTo(Types.resolved.t_void) != 0 && !frmlT.isRef())
      {
        if (t.isThisType())
          { // we need this to unbox an outer ref even if the type does not change
            this._needed = true;
          }
        else
          {
            this._needed = true;
            this._type = frmlT;
          }
      }
    return super.box(frmlT);
  }


  /**
   * Is this Expr a call to an outer ref?
   */
  public boolean isCallToOuterRef()
  {
    return _adr.isCallToOuterRef();
  }


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return "deref(" + _adr + ")";
  }

}

/* end of file */
