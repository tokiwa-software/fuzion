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
 * Source of class HexDump
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.nio.ByteBuffer;

import java.util.TreeMap;

/**
 * HexDump produces an annotated hex dump from a byte buffer.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class HexDump
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Default value for number of bytes to print per line
   */
  static final int BYTES_PER_LINE = 16;


  /**
   * Default value for maximum length of annotation per line, will be broken
   * into next line if this is exceeded.
   */
  static final int MAX_DETAIL = 64;


  /**
   * Should we use ANSI highlights?
   */
  static final boolean USE_HIGHLIGHTS = true;


  /**
   * Highlight colors
   */
  static String[] HIGHLIGHTS = USE_HIGHLIGHTS ?
    new String[]
    {
      Terminal.RED                      ,
      Terminal.GREEN                    ,
      Terminal.YELLOW                   ,
      Terminal.BLUE                     ,
      Terminal.PURPLE                   ,
      Terminal.CYAN                     ,
      Terminal.BOLD_RED                 ,
      Terminal.BOLD_GREEN               ,
      Terminal.BOLD_YELLOW              ,
      Terminal.BOLD_BLUE                ,
      Terminal.BOLD_PURPLE              ,
      Terminal.BOLD_CYAN                ,
      Terminal.INTENSE_BOLD_RED         ,
      Terminal.INTENSE_BOLD_GREEN       ,
      Terminal.INTENSE_BOLD_YELLOW      ,
      Terminal.INTENSE_BOLD_BLUE        ,
      Terminal.INTENSE_BOLD_PURPLE      ,
      Terminal.INTENSE_BOLD_CYAN        ,
    }
    :
    new String[] { "" };


  /**
   * Reset to default color
   */
  static String RESET = USE_HIGHLIGHTS ? Terminal.RESET : "";


  /*----------------------------  variables  ----------------------------*/


  /**
   * The underlying byte buffer to print.
   */
  final ByteBuffer _data;


  /**
   * Number of bytes to print per line
   */
  final int _bytesPerLine = BYTES_PER_LINE;


  /**
   * Maximum length of annotation per line, will be broken into next line if
   * this is exceeded.
   */
  final int _maxDetail = MAX_DETAIL;


  /**
   * Map of position to explaining text for that position
   */
  TreeMap<Integer, String> _marks = new TreeMap<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create HexDump for given byte buffer
   */
  public HexDump(ByteBuffer data)
  {
    _data = data;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Add detail text for given position.
   *
   * @param pos the position
   *
   * @param text the text to be added at pos
   */
  public void mark(int pos, String text)
  {
    var s = _marks.get(pos);
    if (s != null)
      {
        text = s + " " + text;
      }
    _marks.put(pos, text);
  }


  /**
   * Helper for toString() to finish one line.  The address and byteInLine bytes
   * for the line have already been written to out.
   *
   * @param out the buffer to write to
   *
   * @param pos the current byte position
   *
   * @param lineStart the position where the current line started.  Usually
   * equal to pos - _bytesPerLine, but smaller for last line)
   *
   * @param detail the detail message for this line.
   */
  private void finishLine(StringBuilder out, StringBuilder chars, int pos, int lineStart, String detail)
  {
    var bytesInLine = pos - lineStart;
    out.append(RESET);
    for (var i = bytesInLine; i < _bytesPerLine; i++)
      {
        out.append("   ");
      }
    out.append("  ");
    out.append(chars);
    out.append(RESET);
    for (var i = bytesInLine; i < _bytesPerLine; i++)
      {
        out.append(' ');
      }
    out.append("  ");
    var di = 0; // current index in detail
    var dl = 0; // # of chars printed to detail message in current line
    while (di < detail.length())
      {
        var c = detail.charAt(di);
        di++;
        if (c == '\033' && di < detail.length()-1) // skip escape sequence
          {
            do
              {
                out.append(c);
                c = detail.charAt(di);
                di++;
              }
            while (c != 'm' && c != 'K' && c != 'H' && c != 'f' && c != 'J' && di < detail.length()-1);
            out.append(c);
            c = detail.charAt(di);
            di++;
          }
        out.append(c);
        dl++;
        if (di < detail.length() && dl >= _maxDetail)
          {
            out.append('\n');
            var nadr = Integer.numberOfTrailingZeros(Integer.highestOneBit(_data.limit()))/4+1;
            for (var i = 0; i < nadr + 1 + 3 * _bytesPerLine + 2 + _bytesPerLine + 2; i++)
              {
                out.append(' ');
              }
            dl = 0;
          }
      }
    out.append('\n');
  }


  /**
   * Create the HexDump from data and the marked positions
   */
  public String toString()
  {
    var out = new StringBuilder();
    int pos = 0;
    int lineStart = 0;
    var detail = "";
    var nadr = Integer.numberOfTrailingZeros(Integer.highestOneBit(_data.limit()))/4+1;
    var hilite = -1;
    var chars = new StringBuilder();
    while (pos < _data.limit())
      {
        var b = _data.get(pos);
        if (pos - lineStart >= _bytesPerLine)
          {
            if (pos > 0)
              {
                finishLine(out, chars, pos, lineStart, detail);
                detail = "";
              }
            lineStart = pos;
            chars = new StringBuilder();
          }
        if (lineStart == pos)
          {
            var adr = "000000000000000" + Integer.toHexString(pos);
            adr = adr.substring(adr.length() - nadr);
            out.append(adr).append(":");
            if (hilite >= 0)
              {
                out  .append(HIGHLIGHTS[hilite]);
                chars.append(HIGHLIGHTS[hilite]);
              }
          }
        var t = _marks.get(pos);
        if (t != null)
          {
            hilite = (hilite + 1) % HIGHLIGHTS.length;
            var th = HIGHLIGHTS[hilite] + t + RESET;
            detail = detail.length() == 0 ? th : detail + ", " + th;
          }
        out
          .append(' ')
          .append(t == null ? "" : HIGHLIGHTS[hilite])
          .append("0123456789abcdef".charAt((b >> 4) & 0xf))
          .append("0123456789abcdef".charAt( b       & 0xf));
        chars
          .append(t == null ? "" : HIGHLIGHTS[hilite])
          .append(' ' <= b && b <= '~' ? (char) b : '.');
        pos = pos + 1;
      }
    finishLine(out, chars, pos, lineStart, detail);
    return out.toString();
  }

}

/* end-of-file */
