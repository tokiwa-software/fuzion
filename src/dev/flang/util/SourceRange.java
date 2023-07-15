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
 * Source of class SourceRange
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;


/**
 * SourceRange represents a position in a source code file with a given, non-empty length.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SourceRange extends SourcePosition
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The byte position in the source file of the first byte after _bytePos that
   * is not part of this range.
   */
  private final int _byteEndPos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create source range for given source file and byte start and end position.
   *
   * @param sourceFile the source file
   *
   * @param bytePos the byte position within sourceFile
   *
   * @param byteEndPos the byte position just after this range.
   */
  public SourceRange(SourceFile sourceFile, int bytePos, int byteEndPos)
  {
    super(sourceFile, bytePos);

    if (PRECONDITIONS) require
      (sourceFile != null,
       0 <= bytePos,
       bytePos <= byteEndPos,
       byteEndPos <= sourceFile._bytes.length);

    this._byteEndPos = byteEndPos;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * End position within _sourceFile.  This is equal to bytePos() for a plain
   * SourcePosition and may be larger than bytePos for a SourceRange.
   */
  public int byteEndPos()
  {
    return _byteEndPos;
  }


  /**
   * Convert this into a two or more line string that shows the referenced source code
   * line followed by a line with caret (^) at the relevant position.  The
   * last line is not terminated by LF.
   */
  public String showInSource()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(Terminal.BLUE);
    var l = line();
    sb.append(_sourceFile.line(l));

    // add LF in case this is the last line of a file that does not end in a line break
    if (sb.length() > 0 && sb.charAt(sb.length()-1) != '\n')
      {
        sb.append("\n");
      }
    sb.append(Terminal.YELLOW);
    for (int j=0; j < column()-1; j++)
      {
        sb.append('-');
      }
    for(var p = _bytePos; p < _byteEndPos; p++)
      {
        sb.append('^');
        while (_sourceFile.lineStartPos(l+1) < p)
          {
            l = l + 1;
            sb.append(Terminal.RESET);
            sb.append("\n");
            sb.append(_sourceFile.line(l));
            // add LF in case this is the last line of a file that does not end in a line break
            if (sb.charAt(sb.length()-1) != '\n')
              {
                sb.append("\n");
              }
            sb.append(Terminal.YELLOW);
          }
      }
    sb.append(Terminal.RESET);
    return sb.toString();
  }

}

/* end of file */
