/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
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
 * Source of class SourcepositionTool
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import dev.flang.util.ANY;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;

public class SourcePositionTool extends ANY
{
  static boolean PositionIsAfterOrAtCursor(SourcePosition params, SourcePosition sourcePosition)
  {
    return Compare(params, sourcePosition) <= 0;
  }

  static boolean PositionIsBeforeCursor(SourcePosition params, SourcePosition sourcePosition)
  {
    return Compare(params, sourcePosition) > 0;
  }

  /**
   * compare by line and column only. not the source file
   */
  public static int Compare(SourcePosition a, SourcePosition b)
  {
    var result = a.line() < b.line() ? -1: a.line() > b.line() ? +1: 0;
    if (result == 0)
      {
        result = a.column() < b.column() ? -1: a.column() > b.column() ? +1: 0;
      }
    return result;
  }

  public static SourcePosition ByLineColumn(SourceFile sf, int line, int column)
  {
    // lineStartPos throws in case of empty file
    if (line == 1 && column == 1)
    {
      return new SourcePosition(sf, 0);
    }
    if (line > sf.numLines())
    {
      return new SourcePosition(sf, sf.byteLength());
    }
    var bytePos = sf.lineStartPos(line);
    var curColumn = 1;
    while (curColumn < column && bytePos < sf.byteLength())
      {
        bytePos += sf.codePointSize(bytePos);
        curColumn++;
      }
    return new SourcePosition(sf, bytePos);
  }

  public static SourcePosition ByLine(SourceFile sf, int line)
  {
    // lineStartPos throws in case of empty file
    if(line == 1)
    {
      return new SourcePosition(sf, 0);
    }
    if (line > sf.numLines())
    {
      return new SourcePosition(sf, sf.byteLength());
    }
    return new SourcePosition(sf, sf.lineStartPos(line));
  }
}
