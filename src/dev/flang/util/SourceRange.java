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

}

/* end of file */
