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
 * Source of class StackMapFullFrame
 *
 *---------------------------------------------------------------------*/


package dev.flang.be.jvm.classfile;

import dev.flang.be.jvm.classfile.ClassFile.Kaku;
import dev.flang.be.jvm.classfile.ClassFile.StackMapTable;
import dev.flang.util.ANY;
import dev.flang.util.List;

/**
 * A full stack map frame
 */
public class StackMapFullFrame extends ANY implements Comparable<StackMapFullFrame>
{

  public final int byteCodePos;
  private final StackMapTable stackMapTable;


  /**
   * @param stackMapTable the table this frame is part of.
   * @param byteCodePos the byte code position this frame applies to.
   */
  public StackMapFullFrame(StackMapTable stackMapTable, int byteCodePos)
  {
    this.stackMapTable = stackMapTable;
    this.byteCodePos = byteCodePos;
  }


  /**
   * Write the bytes of this frame to write `o`.
   */
  void write(Kaku o)
  {
    o.writeU1(ClassFileConstants.STACK_MAP_FRAME_FULL_FRAME);
    // u2 offset_delta;
    // u2 number_of_locals;
    // verification_type_info locals[number_of_locals];
    // u2 number_of_stack_items;
    // verification_type_info stack[number_of_stack_items];
    o.writeU2(stackMapTable.offset(this));
    var locals0 = thinOutTwoSlotTypes(stackMapTable.unifiedLocals(byteCodePos));
    o.writeU2(locals0.size());
    for (var l : locals0)
      {
        l.write(o);
      }
    var stack = stackMapTable.stacks.get(byteCodePos);
    o.writeU2(stack.size());
    for (var s : stack)
      {
        s.write(o);
      }
  }


  public int compareTo(StackMapFullFrame o)
  {
    if (PRECONDITIONS) require
      (byteCodePos >= 0 && o.byteCodePos >= 0);

    return byteCodePos - o.byteCodePos;
  }


  /**
   * Remove duplicate entries of longs and doubles from `locals`.
   */
  private static List<VerificationType> thinOutTwoSlotTypes(List<VerificationType> locals)
  {
    var result = new List<VerificationType>();
    boolean skipNext = false;
    for (int index = 0; index < locals.size(); index++)
      {
        if (skipNext)
          {
            if (CHECKS)
              check(locals.get(index).needsTwoSlots());
            skipNext = false;
          }
        else
          {
            var l = locals.get(index);
            result.add(l);
            if (l.needsTwoSlots())
              {
                skipNext = true;
              }
          }
      }
    if (CHECKS)
      check(skipNext == false);

    return result;
  }

}
