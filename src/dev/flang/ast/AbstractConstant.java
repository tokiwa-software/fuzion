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
 * Source of class Constant
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

/**
 * AbstractConstant represents a constant in the source code such as '3.14',
 * '"Hello"'.  This class might be loaded from a library or parsed in sources.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class AbstractConstant extends Expr
{

  /**
   * The clazz this abstract constant will result in.
   *
   * Not null only for calls that are turned into compile time constants.
   */
  public Object runtimeClazz;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for a Constant at the given source code position.
   */
  public AbstractConstant()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * The type of the constant.  This may be different to the the user-visible
   * `type()` of this constant, in particular, for a constant string, `type()`
   * returns `String`, while `typeOfConstant` is the actual child of `String`
   * used for constants: `Const_String`.
   *
   * @return the type to be used to create the constant value. Is assignment
   * compatible to `type()`.
   */
  public AbstractType typeOfConstant()
  {
    return type();
  }


  /**
   * Serialized form of the data of this constant.
   */
  public abstract byte[] data();


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
  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    v.action(this);
    return this;
  }


  /**
   * Is this a compile-time constant?
   */
  @Override
  public boolean isCompileTimeConst()
  {
    // NYI everything ref, e.g. strings do not work yet.
    // everything ref needs to live on the heap, does not work with compound literals.
    return !type().isRef();
  }


  /**
   * This expression as a compile time constant.
   */
  @Override
  public AbstractConstant asCompileTimeConstant()
  {
    return this;
  }

}

/* end of file */
