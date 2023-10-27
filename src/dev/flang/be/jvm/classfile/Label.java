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
 * Source of class Label
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.jvm.classfile;



/**
 * Label is a symbolic label in byte code. A Label is an Expr that produces
 * no code.  Its only function is to carry around the bytecode location of the
 * subsequent bytecode.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */

public class Label extends Expr
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * Absolute position of this label: estimate and final value.
   *
   * The position has the following values:
   *
   * initially: -1
   *
   * after ByteCodeSizeEstimate: pe: A position estimate that may shrink
   * during ByteCodeFixLabels phase.
   *
   * after ByteCodeFixLabels: pf: The final position.
   *
   * after Kaku: pf, unchanged.
   *
   * Final positions may not be larger than estimates:
   *
   *   ∀ l: l.pe >= l.pf
   *
   * And distances between final labels will not exceed the estimates:
   *
   *   ∀ l1, l2: |l1.pe - l2.pe| >= |l1.pf - l2.pf|
   *
   */
  int _posEstimate = -1;
  int _posFinal = -1;


  /*---------------------------  constructors  ---------------------------*/


  public Label()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the byte code for this ByteCode.
   *
   * For a Label, this will set this label.
   *
   * @param bw the writer to write the bytecode to.
   *
   * @param cf the class file we are generating, used for cpool indices.
   */
  public void code(ClassFile.ByteCodeWriter ba, ClassFile cf)
  {
    ba.setLabel(this);
  }


  public JavaType type()
  {
    return ClassFileConstants.PrimitiveType.type_void;
  }


  /**
   * String representation of this label:
   */
  public String toString() { return "label:"; }


}

/* end of file */
