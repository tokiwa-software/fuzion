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
 * Source of class AbstractCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.io.ByteArrayOutputStream;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


/**
 * AbstractCall is an expression that is a call to a class and that results in
 * the result value of that class.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractCall extends Expr
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Special value for an empty generics list to distinguish a call without
   * generics ("a.b(x,y)") from a call with an empty actual generics list
   * ("a.b<>(x,y)").
   */
  public static final List<AbstractType> NO_GENERICS = new List<>();


  /*----------------------------  variables  ----------------------------*/


  // NYI: Move _sid to target?
  public int _sid = -1;  // NYI: Used by dev.flang.be.interpreter, REMOVE!


  // For a call to parent in an inherits clause, these are the ids of the
  // argument fields for the parent feature.
  //
  // NYI: remove, used in FUIR.  This should be replaced by explicit assignments to fields
  public int _parentCallArgFieldIds = -1;


  /*-------------------------- constructors ---------------------------*/


  /**
   * Constructor
   *
   * @param pos the sourcecode position, used for error messages.
   */
  public AbstractCall()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  public abstract List<AbstractType> actualTypeParameters();
  public abstract AbstractFeature calledFeature();
  public abstract Expr target();
  public abstract List<Expr> actuals();
  public abstract int select();
  public abstract boolean isInheritanceCall();
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    System.err.println("Called "+this.getClass()+".visit");
    return this;
  }


  /**
   * visit all the expressions within this Call.
   *
   * @param v the visitor instance that defines an action to be performed on
   * visited expressions
   */
  public void visitExpressions(ExpressionVisitor v)
  {
    for (var a : actuals())
      {
        a.visitExpressions(v);
      }
    if (target() != null)
      {
        target().visitExpressions(v);
      }
    super.visitExpressions(v);
  }


  /**
   * Does this call use dynamic binding.  Dynamic binding is used if the target
   * is not Current (either explicitly or in an inheritance call).  In case the
   * target is current, this call will be specialized to avoid dynamic binding.
   */
  public boolean isDynamic()
  {
    return
      !(target() instanceof AbstractCurrent) &&
      !isInheritanceCall();
  }


  /**
   * This call serialized as a constant.
   */
  public AbstractConstant asCompileTimeConstant()
  {
    if (PRECONDITIONS) require
      (isCompileTimeConst());

    return new AbstractConstant() {

      @Override
      public SourcePosition pos()
      {
        return AbstractCall.this.pos();
      }


      /**
       * actuals are serialized in order. example
       * `tuple (u8 5) (codepoint u32 72)` results in
       * the following data:
       *        b b b b b
       * u8 ----^ ^^^^^^^--- codepoint u32 (both little endian)
       */
      @Override
      public byte[] data()
      {
        var b = AbstractCall.this
          .actuals()
          .map2(x -> x.asCompileTimeConstant().data());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (var d : b)
          {
            out.write(d, 0, d.length);
          }

        return out.toByteArray();
      }

      @Override
      AbstractType typeIfKnown()
      {
        return AbstractCall.this.type();
      }

      @Override
      public Expr visit(FeatureVisitor v, AbstractFeature outer)
      {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
      }
    };
  }


  @Override
  public boolean isCompileTimeConst()
  {
    var result =
      !isInheritanceCall() &&
      calledFeature().isConstructor() &&
      // contains no fields
      calledFeature().code().containsOnlyDeclarations() &&
      // we are calling a value type feature
      !calledFeature().selfType().isRef() &&
      // only refs without args may be inherited
      calledFeature().inherits().stream().allMatch(c -> c.calledFeature().arguments().isEmpty() && c.type().isRef()) &&
      // no unit
      this.actuals().size() > 0 &&
      this.actuals().stream().allMatch(x -> x.isCompileTimeConst());
    return result;
  }

}

/* end of file */
