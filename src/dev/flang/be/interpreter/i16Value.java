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
 * Source of class i16Value
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;


/**
 * i16Value is a value of type i16
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class i16Value extends Value
{


  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  private int _val;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  public i16Value(int val)
  {
    if (PRECONDITIONS) require
      (-0x8000 <= val && val <= 0x7fff);

    _val = val;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString
   *
   * @return
   */
  @Override
  public String toString()
  {
    return Integer.toString(_val);
  }


  /** {@inheritDoc} */
  @Override
  public int i16Value()
  {
    return _val;
  }


  /** {@inheritDoc} */
  @Override
  void storeNonRef(LValue slot, int size)
  {
    if (PRECONDITIONS) require
      (size == 1);

    slot.container.nonrefs[slot.offset] = _val;
  }


  @Override
  protected Object toNative()
  {
    return (short)this.i16Value();
  }

}

/* end of file */
