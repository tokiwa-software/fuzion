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
 * Source of class ArrayConstant
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.nio.ByteBuffer;
import java.util.stream.Collectors;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;

/**
 * ArrayConstant represents a constant array the source code such as '[3.14,
 * 2.71]', '[1,2,3]'.
 */
public class ArrayConstant extends Constant
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The type of the array constant
   */
  private final AbstractType _type;


  /**
   * The elements of the array constant
   */
  private final List<Expr> _elements;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for ArrayConstant at the given source code position, with the
   * given elements and element type.
   *
   * @param pos the sourcecode position, used for error messages.
   *
   * @param elements the element values
   *
   * @param et the elements type, e.g. u64, i32 etc.
   */
  public ArrayConstant(SourcePosition pos, List<Expr> elements, AbstractType et)
  {
    super(pos);

    this._elements = elements;
    this._type = new ResolvedNormalType(Types.resolved.f_array.selfType(),
                                        new List<>(et),
                                        new List<>(),
                                        Types.resolved.universe.selfType());
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * typeForInferencing returns the type of this expression or null if the type is
   * still unknown, i.e., before or during type resolution.  This is redefined
   * by sub-classes of Expr to provide type information.
   *
   * @return this Expr's type or null if not known.
   */
  AbstractType typeForInferencing()
  {
    return _type;
  }


  @Override
  public byte[] data()
  {
    var l = _elements
      .stream()
      .map(x -> x.asCompileTimeConstant().data().length)
      .collect(Collectors.summingInt(x -> x));
    var b = ByteBuffer.wrap(new byte[l]);
    _elements
      .stream()
      .forEach(x -> b.put(x.asCompileTimeConstant().data()));
    return b.array();
  }


  @Override
  public Expr visit(FeatureVisitor v, AbstractFeature outer)
  {
    v.action(this);
    return this;
  }

}

/* end of file */
