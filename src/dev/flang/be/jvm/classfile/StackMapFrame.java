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
 * Source of class StackMapFrame
 *
 *---------------------------------------------------------------------*/


package dev.flang.be.jvm.classfile;

import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;

import dev.flang.be.jvm.classfile.ClassFile.Kaku;
import dev.flang.util.ANY;
import dev.flang.util.List;

public abstract class StackMapFrame extends ANY implements Comparable<StackMapFrame>
{

  private int frameType;

  public StackMapFrame(int frameType)
  {
    this.frameType = frameType;
  }

  abstract int byteCodePos();

  abstract byte[] data();

  void write(Kaku o)
  {
    o.writeU1(frameType);
    o.write(data());
  }

  public int compareTo(StackMapFrame o)
  {
    return byteCodePos() - o.byteCodePos();
  }

  Optional<List<VerificationTypeInfo>> localsFor(StackMapFrame smf)
  {
    return Optional.empty();
  }

  List<VerificationTypeInfo> localsAtEnd()
  {
    return null;
  }

  public StackMapFrame previousStackMapFrame(TreeSet<StackMapFrame> smfs)
  {
    return smfs
      .stream()
      .filter(x -> x.byteCodePos() < byteCodePos())
      .max(Comparator.comparingInt(x -> x.byteCodePos()))
      .get();
  }

}
