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
 * Source of class SourceFile
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;


/**
 * SourceFile represents a UTF-8 encoded source code file and provides codepoint
 * encoding and line / position counting.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SourceFile extends ANY
{


  /*----------------------------  constants  ----------------------------*/

  /**
   * SourceFile instance used for SourcePosition._builtIn_.
   */
  public static final SourceFile _builtIn_ = new SourceFile(Path.of("--builtin--"), new byte[0]);


  /**
   * Result of failure to decode a codepoint.
   */
  public static final int BAD_CODEPOINT = 0xffFFFF;


  /**
   * Result of nextCodePoint in case end-of-file is reached.
   */
  public static final int END_OF_FILE = 0xffFFFe;


  /**
   * Dummy value for lastCodePoint if curCodePoint is at position 0.
   */
  static final int BEGINNING_OF_FILE = 0xffFFFd;

  /**
   * code points that start a new line:
   */
  protected static final int LF  = 0x000a;   // Line Feed, U+000A
  protected static final int VT  = 0x000b;   // Vertical Tab, U+000B
  protected static final int FF  = 0x000c;   // Form Feed, U+000C
  protected static final int CR  = 0x000d;   // Carriage Return, U+000D
  protected static final int NEL = 0x0085;   // Next Line, U+0085
  protected static final int LS  = 0x2028;   // Line Separator, U+2028
  protected static final int PF  = 0x2029;   // Paragraph Separator, U+2029


  protected static final int SP  = 0x0020;   // Space character, U+0020


  /**
   * Quick byte -> hex conversion table:
   */
  static String[] HEX = new String[]
  {
    "00", "01", "03", "04", "05", "06", "07", "08", "09", "0a", "0b", "0c", "0d", "0e", "0f",
    "10", "11", "13", "14", "15", "16", "17", "18", "19", "1a", "1b", "1c", "1d", "1e", "1f",
    "20", "21", "23", "24", "25", "26", "27", "28", "29", "2a", "2b", "2c", "2d", "2e", "2f",
    "30", "31", "33", "34", "35", "36", "37", "38", "39", "3a", "3b", "3c", "3d", "3e", "3f",
    "40", "41", "43", "44", "45", "46", "47", "48", "49", "4a", "4b", "4c", "4d", "4e", "4f",
    "50", "51", "53", "54", "55", "56", "57", "58", "59", "5a", "5b", "5c", "5d", "5e", "5f",
    "60", "61", "63", "64", "65", "66", "67", "68", "69", "6a", "6b", "6c", "6d", "6e", "6f",
    "70", "71", "73", "74", "75", "76", "77", "78", "79", "7a", "7b", "7c", "7d", "7e", "7f",
    "80", "81", "83", "84", "85", "86", "87", "88", "89", "8a", "8b", "8c", "8d", "8e", "8f",
    "90", "91", "93", "94", "95", "96", "97", "98", "99", "9a", "9b", "9c", "9d", "9e", "9f",
    "a0", "a1", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "aa", "ab", "ac", "ad", "ae", "af",
    "b0", "b1", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "ba", "bb", "bc", "bd", "be", "bf",
    "c0", "c1", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "ca", "cb", "cc", "cd", "ce", "cf",
    "d0", "d1", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "da", "db", "dc", "dd", "de", "df",
    "e0", "e1", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ea", "eb", "ec", "ed", "ee", "ef",
    "f0", "f1", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "fa", "fb", "fc", "fd", "fe", "ff"
      };


  /**
   * Special value for fileName argument of constructor to use stdin as input.
   */
  public static Path STDIN = Path.of("-");


  /**
   * Dummy value for fileName argument for data from command line. Unlike STDIN,
   * no data can be read from this, it must not be passed to the constructor of
   * SourceFile without providing a {@code byte[]} or file data!
   */
  public static Path COMMAND_LINE_DUMMY = Path.of("command line");


  /*-----------------------------  statics  -----------------------------*/


  /**
   * We might want to move this optimization to a special variant of SourceFile,
   * so I make this dependent on a static final flag for now:
   */
  private static final boolean _FAST_LINE_NUM_ = true;


  /*----------------------------  variables  ----------------------------*/


  /**
   * Name of the file that is being processed
   */
  public final Path _fileName;


  /**
   * The source code that we are parsing, using UTF8 encoding.
   */
  final byte[] _bytes;


  /**
   * Array of byte positions of the first code point of all lines, i.e.,
   * _lines[4] is the byte that starts the fourth line. _lines[0] is unused and
   * set to -1,
   *
   * This is created on demand by lines();
   */
  private int _lines[];


  /**
   * For each byte position p that starts a code point, if _FAST_LINE_NUM_ is
   * set, the line number of the line containing that code point is
   * _lineNumOffset[p] + _lineNumBase[p/128].
   */
  private byte _lineNumOffset[];
  private int _lineNumBase[];


  /**
   * For each position of a codepoint, _indentationByte[p] will hold the
   * indentation in number of codepoints from the beginning of the line as an
   * unsigned 8-bit integer.  If the indentation is >= 128, this will be an
   * offset that must be added to _indentationBase[p/128] to get the
   * actual indentation.
   */
  private byte _indentationByte[];


  /**
   * For groups of 128 bytes of source code data: if for a position p the
   * indentation from the start of its line exceeds 127, then
   * _indentationBase[p/128] will be a base indentation to be added to
   * _indentationByte[p].
   */
  private int _indentationBase[];


  /**
   * The current codePoint, i.e., the last result of decodeCodePoint[AndSize].
   * BAD_CODEPOINT if decodeCodePoint has not been called yet, END_OF_FILE if
   * all codePoints have been decoded.
   */
  private int _cur;


  /**
   * The size of the current code point.
   */
  private int _size;


  /**
   * The byte position within _bytes of the current code point _cur.
   */
  private int _pos;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * If sf is null, Load UTF-8 encoded source code from given file. Otherwise,
   * load UTF-8 encoded source code sf as if it came from a file with the given
   * Path.
   *
   * Reset the position to the beginning of this file.
   */
  public SourceFile(Path fileName, byte[] sf)
  {
    if (PRECONDITIONS) require
      (fileName != null);

    _fileName = fileName;
    if (sf == null)
      {
        try
          {
            sf = fileName == STDIN ? System.in.readAllBytes()
                                   : Files    .readAllBytes(fileName);
          }
        catch (IOException e)
          {
            sf = new byte[0];
            Errors.error(new SourcePosition(/* cannt use `this` here since `_bytes` etc. is not intialized yet */
                                            new SourceFile(fileName, sf),
                                            0),
                         "I/O Error: " + e.getMessage(),
                         "");
          }
      }
    _bytes = sf;
    _pos = 0;
    _cur = BAD_CODEPOINT;
    _size = 0;
    if (_FAST_LINE_NUM_)
      {
        lines();  // eagerly determine lines
      }
  }


  /**
   * Load UTF-8 encoded source code from given file and reset the position to
   * the beginning of this file.
   */
  public SourceFile(Path fileName)
  {
    this(fileName, null);
  }


  /**
   * Fork this SourceFile.
   */
  public SourceFile(SourceFile original)
  {
    _fileName        = original._fileName;
    _bytes           = original._bytes;
    _lines           = original._lines;
    _lineNumOffset   = original._lineNumOffset;
    _lineNumBase     = original._lineNumBase;
    _indentationByte = original._indentationByte;
    _indentationBase = original._indentationBase;
    _pos             = original._pos;
    _cur             = original._cur;
    _size            = original._size;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Convert a given byte into a two-char hex digit, for error messages.
   */
  private String hex(int b)
  {
    return HEX[b];
  }


  /**
   * Decode the code point at the given position and return it together with its size.
   *
   * @param pos the byte position of the code point
   *
   * @return the decoded code point or BAD_CODEPOINT in case decoding failed
   * together with the size in bytes of the UTF-8 encoding, joined via
   * makeCodePointWithSize().
   */
  private int decodeCodePointAndSize(int pos)
  {
    /*
    // tag::fuzion_rule_SRCF_UTF8[]
Fuzion input uses UTF8 encoding version {UNICODE_VERSION} from {UNICODE_SOURCE}[].  Improperly encoded input will be treated as an error.
    // end::fuzion_rule_SRCF_UTF8[]
    */
    int result;
    int sz;
    int b1 = _bytes[pos] & 0xff;
    // UTF-8 definition taken from https://en.wikipedia.org/wiki/UTF-8
    if (0x00 <= b1 && b1 <= 0x7f)   // ASCII
      {
        result = b1;
        sz = 1;
      }
    else if (0xc0 <= b1 && b1 <= 0xdf)   // 0x0080..0x7ff encoded in 2 bytes
      {
        if (pos + 2 > _bytes.length)
          {
            Errors.SRCF.UTF8.report(sourcePos(pos),
                                    ": found end-of-file while decoding " + hex(b1),
                                    "Expected one continuation byte, but reached end of file.");
            result = BAD_CODEPOINT;
            sz = 1;
          }
        else
          {
            int b2 = _bytes[pos + 1] & 0xff;
            if ((b2 & 0xc0) != 0x80)
              {
                Errors.SRCF.UTF8.report(sourcePos(pos),
                                        ": while decoding " + hex(b1) + " " + hex(b2),
                                        "Expected one continuation byte in the range 0x80..0xbf.");
                result = BAD_CODEPOINT;
                sz = 1;
              }
            else
              {
                result = (b1 & 0x1f) << 6 | (b2 & 0x3f);
                sz = 2;
                if (result == 0x00)
                  {
                    Errors.SRCF.UTF8.report(sourcePos(pos),
                                            ": while decoding " + hex(b1) + " " + hex(b2),
                                            "Two-byte NUL-character encoding is allowed in modified UTF-8 only, not in standard UTF-8 encoding.");
                    result = BAD_CODEPOINT;
                  }
                else if (result < 0x80)
                  {
                    Errors.SRCF.UTF8.report(sourcePos(pos),
                                            ": while decoding " + hex(b1) + " " + hex(b2),
                                            "Code point "+Integer.toHexString(result)+" uses overlong 2-byte encoding.");
                    result = BAD_CODEPOINT;
                  }
              }
          }
      }
    else if (0xe0 <= b1 && b1 <= 0xef)   // 0x0800..0xffff encoded in 3 bytes
      {
        if (pos + 3 > _bytes.length)
          {
            Errors.SRCF.UTF8.report(sourcePos(pos),
                                    ": found end-of-file while decoding " + hex(b1),
                                    "Expected two continuation bytes, but reached end of file.");
            result = BAD_CODEPOINT;
            sz = _bytes.length - pos;
          }
        else
          {
            int b2 = _bytes[pos + 1] & 0xff;
            int b3 = _bytes[pos + 2] & 0xff;
            if ((b2 & 0xc0) != 0x80 ||
                (b3 & 0xc0) != 0x80)
              {
                Errors.SRCF.UTF8.report(sourcePos(pos),
                                        ": while decoding " + hex(b1) + " " + hex(b2) + " " + hex(b3),
                                        "Expected two continuation bytes in the range 0x80..0xbf.");
                result = BAD_CODEPOINT;
                sz = ((b2 & 0xc0) != 0x80) ? 1 : 2;
              }
            else
              {
                result = (((b1 & 0x0f) << 12) |
                          ((b2 & 0x3f) <<  6) |
                          ((b3 & 0x3f)      )   );
                if (result < 0x800)
                  {
                    Errors.SRCF.UTF8.report(sourcePos(pos),
                                            ": while decoding " + hex(b1) + " " + hex(b2) + " " + hex(b3),
                                            "Code point "+Integer.toHexString(result)+" uses overlong 3-byte encoding.");
                    result = BAD_CODEPOINT;
                  }
                else if (result >= 0xd800 && result <= 0xdfff)
                  {
                    Errors.SRCF.UTF8.report(sourcePos(pos),
                                            ": while decoding " + hex(b1) + " " + hex(b2) + " " + hex(b3),
                                            "Code point "+Integer.toHexString(result)+" is invalid, values in the " +
                                            "range 0xd800..0xdfff are reserved for UTF-16 surrogate halves.");
                    result = BAD_CODEPOINT;
                  }
                sz = 3;
              }
          }
      }
    else if (0xf0 <= b1 && b1 <= 0xf4)   // 0x010000..0x10ffff encoded in 4 bytes
      {
        if (pos + 4 > _bytes.length)
          {
            Errors.SRCF.UTF8.report(sourcePos(pos),
                                    ": found end-of-file while decoding " + hex(b1),
                                    "Expected three continuation bytes, but reached end of file.");
            result = BAD_CODEPOINT;
            sz = _bytes.length - pos;
          }
        else
          {
            int b2 = _bytes[pos + 1] & 0xff;
            int b3 = _bytes[pos + 2] & 0xff;
            int b4 = _bytes[pos + 3] & 0xff;
            if ((b2 & 0xc0) != 0x80 ||
                (b3 & 0xc0) != 0x80 ||
                (b4 & 0xc0) != 0x80)
              {
                Errors.SRCF.UTF8.report(sourcePos(pos),
                                        ": while decoding " + hex(b1) + " " + hex(b2) + " " + hex(b3) + " " + hex(b4),
                                        "Expected three continuation bytes in the range 0x80..0xbf.");
                result = BAD_CODEPOINT;
                sz = (((b2 & 0xc0) != 0x80) ? 1 :
                      ((b3 & 0xc0) != 0x80) ? 2 : 3);
              }
            else
              {
                result = (((b1 & 0x07) << 18) |
                          ((b2 & 0x3f) << 12) |
                          ((b3 & 0x3f) <<  6) |
                          ((b4 & 0x3f)      )   );
                if (result < 0x10000)
                  {
                    Errors.SRCF.UTF8.report(sourcePos(pos),
                                            ": while decoding " + hex(b1) + " " + hex(b2) + " " + hex(b3) + " " + hex(b4),
                                            "Code point "+Integer.toHexString(result)+" uses overlong 4-byte encoding.");
                    result = BAD_CODEPOINT;
                  }
                else if (result > 0x10ffff)
                  {
                    Errors.SRCF.UTF8.report(sourcePos(pos),
                                            ": while decoding " + hex(b1) + " " + hex(b2) + " " + hex(b3) + " " + hex(b4),
                                            "Code point "+Integer.toHexString(result)+" is outside of the allowed range for " +
                                            "codepoints 0x000000..0x10ffff.");
                    result = BAD_CODEPOINT;
                  }
                sz = 4;
              }
          }
      }
    else if (0x80 <= b1 && b1 <= 0xbf)
      {
        Errors.SRCF.UTF8.report(sourcePos(pos),
                                ": while decoding " + hex(b1),
                                "Stray continuation byte without preceding leading byte.");
        result = BAD_CODEPOINT;
        sz = 1;
      }
    else if (0xf5 <= b1 && b1 <= 0xfd)
      {
        Errors.SRCF.UTF8.report(sourcePos(pos),
                                ": while decoding " + hex(b1),
                                "Code 0xf8..0xff are undefined.");
        result = BAD_CODEPOINT;
        sz = 1;
      }
    else if (0xfe <= b1 && b1 <= 0xff)
      {
        Errors.SRCF.UTF8.report(sourcePos(pos),
                     "while decoding : " + hex(b1),
                     "Code 0xfe and 0xff are undefined.");
        result = BAD_CODEPOINT;
        sz = 1;
      }
    else
      {
        throw new Error("Missing case: " + hex(b1));
      }
    return makeCodePointWithSize(result, sz);
  }


  /**
   * As long as Java does not support cheap tuples, pack a code point and its
   * size in bytes into one int.
   *
   * @param cp a code point, 0..0x10ffff or BAD_CODEPOINT.
   *
   * @param sz the byte size of cp in the UTF-8 encoding in the source code, in
   * the range 1..4.
   *
   * @return a combination of cp ans sz such that
   * cp==codePointFromCpAndSize(result) and sz==sizeFromCpAndSize(result).
   */
  private int makeCodePointWithSize(int cp,
                                    int sz)
  {
    if (PRECONDITIONS) require
      (cp >= 0,
       cp <= 0x10FFFF || cp == BAD_CODEPOINT,
       sz > 0,
       sz <= 4);

    return cp | (sz << 24);
  }


  /**
   * Extract the codePoint from the result of makeCodePointWithSize.
   */
  private int codePointFromCpAndSize(int cpAndSz)
  {
    return cpAndSz & ((1 << 24) -1);
  }


  /**
   * Extract the size from the result of makeCodePointWithSize.
   */
  private int sizeFromCpAndSize(int cpAndSz)
  {
    return cpAndSz >> 24;
  }


  /**
   * Decode the code point at _pos and store the code point in _cur and
   * its size in _size.
   */
  private void decodeCodePoint()
  {
    int cpAndSz = decodeCodePointAndSize(_pos);
    int p  = codePointFromCpAndSize(cpAndSz);
    int sz = sizeFromCpAndSize     (cpAndSz);
    _cur = p;
    _size = sz;
  }


  /**
   * Check if _pos is before the end of the file. If so, decode the code point
   * at _pos and store the result in _cur/_size, otherwise set _cur/_size to
   * END_OF_FILE/0.
   */
  private void decode()
  {
    if (_pos < _bytes.length)
      {
        decodeCodePoint();
      }
    else
      {
        _cur = END_OF_FILE;
        _size = 0;
      }
  }


  /**
   * Obtain the current code point without advancing the position in the file.
   */
  public int curCodePoint()
  {
    return _cur;
  }


  /**
   * Obtain the code point at the given position in the file.
   *
   * @param pos the byte position within this file, may be negative or larger
   * than _bytes.length, in which case this will return BEGINNING_OF_FILE or
   * END_OF_FILE, respectively.
   *
   * @return the decoded code point at pos, BAD_CODEPOINT in case of a decoding
   * error, BEGINNING_OF_FILE or END_OF_FILE in case pos is outside of the file.
   */
  public int codePointAt(int pos)
  {
    return
      pos <  0             ? BEGINNING_OF_FILE :
      pos >= _bytes.length ? END_OF_FILE       : codePoint(pos);
  }


  /**
   * Advance the position in the file by one code point.
   */
  public void nextCodePoint()
  {
    if (PRECONDITIONS) require
      (curCodePoint() != END_OF_FILE);

    _pos = _pos + _size;
    decode();
  }


  /**
   * Check if codepoint ends a line.
   *
   * New lines are started after code points LF, VT, FF, NEL, LS and PF.
   * Additionally, BEGINNING_OF_FILE starts a new line since it is followed by
   * the first line of the file.
   */
  public boolean isNewLine(int codePoint)
  {
    /*
    // tag::fuzion_rule_LEXR_NEWLINE[]
The end of a source code line is marked by one of the code points LF 0x000a, VT 0x000b, FF 0x000c, NEL 0x0085, LS 0x2028 or PF 0x2029.
    // end::fuzion_rule_LEXR_NEWLINE[]
    */

    // line break, taken from https://en.wikipedia.org/wiki/Newline#Unicode
    return switch (codePoint)
      {
      case
        LF,
        VT,
        FF,
        NEL,
        LS,
        PF,
        BEGINNING_OF_FILE -> true;
      default -> false;
      };
  }


  /**
   * The byte position within the file. 0 before the first call to
   * nextCodePoint. _bytes.length if the end of the file has been reached.
   */
  public int bytePos()
  {
    return _pos;
  }


  /**
   * Set the current position to the given value and decode the code point at
   * that position.
   *
   * @param newPos the desired position.
   */
  public void setPos(int newPos)
  {
    if (PRECONDITIONS) require
      (0 <= newPos,
       newPos <= _bytes.length);

    _pos = newPos;
    decode();
  }


  /**
   * Obtain the lines array. This is an array of byte positions of the first
   * code point of all lines, i.e., result[4] is the byte that starts the fourth
   * line. result[0] is unused and set to -1,
   *
   * The result is cached in _lines.
   *
   * As a side-effect, this determines the indentation data for
   * _indentationByte and _indentationBase that is used in codePointIndentation.
   */
  private int[] lines()
  {
    if (_lines == null)
      {
        _indentationByte = new byte[_bytes.length+1];
        _indentationBase = new int[(_bytes.length+1+127)/128];
        if (_FAST_LINE_NUM_)
          {
            _lineNumOffset   = new byte[_bytes.length+1];
            _lineNumBase     = new int[(_bytes.length+1+127)/128];
          }
        IntStream.Builder b = IntStream.builder();
        b.add(-1);  // dummy line # 0 does not exist.
        int lineCnt = 0;
        int sz;
        int curCodePoint  = BEGINNING_OF_FILE;
        var ind = 1;
        for (int pos = 0;
             _bytes != null && pos < _bytes.length;
             pos = pos + sz,
             ind = ind + cpWidth(curCodePoint))
          {
            if (isNewLine(curCodePoint))
              {
                b.add(pos);
                lineCnt = lineCnt + 1;
                ind = 1;
              }
            storeIndentation(pos, ind);
            storeLineNum(pos, lineCnt);
            int cpAndSz  = decodeCodePointAndSize(pos);
            curCodePoint = codePointFromCpAndSize(cpAndSz);
            sz           = sizeFromCpAndSize     (cpAndSz);
          }
        if (isNewLine(curCodePoint))
          {
            b.add(_bytes.length);
            lineCnt = lineCnt + 1;
            ind = 1;
          }
        storeIndentation(_bytes.length, ind);
        storeLineNum(_bytes.length, lineCnt);
        _lines = b.build().toArray();
      }
    return _lines;
  }


  /**
   * get the width of a codepoint as specified in:
   * https://www.unicode.org/reports/tr11/
   */
  private int cpWidth(int curCodePoint)
  {
    // Unassigned code points in ranges intended for CJK ideographs are classified as Wide. Those ranges are:

    // the CJK Unified Ideographs Extension A block, 3400..4DBF
    // the CJK Unified Ideographs block, 4E00..9FFF
    // the CJK Compatibility Ideographs block, F900..FAFF
    // the Supplementary Ideographic Plane, 20000..2FFFF
    // the Tertiary Ideographic Plane, 30000..3FFFF

    // NYI: UNDER DEVELOPMENT
    // could be made faster (binary search?)

    // NYI: UNDER DEVELOPMENT
    // We would need to evaluate list
    // http://www.unicode.org/Public/UNIDATA/EastAsianWidth.txt
    // to be more correct.

          // common case first
    return curCodePoint < 0x3400
      ? 1
      : 0x3400 <= curCodePoint && curCodePoint <= 0x4DBF ||
        0x4E00 <= curCodePoint && curCodePoint <= 0x9FFF ||
        0xF900 <= curCodePoint && curCodePoint <= 0xFAFF ||
        0x20000 <= curCodePoint && curCodePoint <= 0x2FFFF ||
        0x30000 <= curCodePoint && curCodePoint <= 0x3FFFF
      ? 2
      : 1;
  }


  /**
   * record the codepoint indentation at pos to be ind.
   */
  void storeIndentation(int pos, int ind)
  {
    if (ind >= 128)
      {
        if (_indentationBase[pos / 128] == 0)
          {
            _indentationBase[pos / 128] = ind - 128;
          }
        ind = ind - _indentationBase[pos/128];
        if (CHECKS) check
          (ind >= 128);
      }
    _indentationByte[pos] = (byte) ind;
  }


  /**
   * record the line num at pos to be l
   */
  void storeLineNum(int pos, int l)
  {
    if (_FAST_LINE_NUM_)
      {
        if (_lineNumBase[pos / 128] == 0)
          {
            _lineNumBase[pos / 128] = l;
          }
        l = l - _lineNumBase[pos / 128];
        if (CHECKS) check
          (0 <= l,
           l < 128);
        _lineNumOffset[pos] = (byte) l;
      }
  }


  /**
   * Return the code point at the given position in the file.
   *
   * @param pos position of the code point
   *
   * @return the code point at that position.
   */
  public int codePoint(int pos)
  {
    if (PRECONDITIONS) require
      (pos >= 0,
       pos < _bytes.length);

    int cpAndSz = decodeCodePointAndSize(pos);
    return codePointFromCpAndSize(cpAndSz);
  }


  /**
   * Return the byte size of the code point at the given position in the file.
   *
   * @param pos position of the code point
   *
   * @return the byte size of the code point at that position.
   */
  public int codePointSize(int pos)
  {
    if (PRECONDITIONS) require
      (pos >= 0,
       pos < _bytes.length);

    int cpAndSz = decodeCodePointAndSize(pos);
    return sizeFromCpAndSize     (cpAndSz);
  }


  /**
   * Return a part of the file as a String.
   *
   * @param start the byte position of the first code point of the desired
   * portion of the file
   *
   * @param end the byte position of first code point after the desired portion
   * of the file
   *
   * @return the desired portion as a Java String.
   */
  public String asString(int start, int end)
  {
    if (PRECONDITIONS) require
      (start >= 0,
       end <= _bytes.length);

    StringBuilder sb = new StringBuilder();
    int pos = start;
    while (pos < end)
    {
      int cpAndSz = decodeCodePointAndSize(pos);
      int p  = codePointFromCpAndSize(cpAndSz);
      int sz = sizeFromCpAndSize     (cpAndSz);
      sb.appendCodePoint(p);
      pos += sz;
    }
    return sb.toString();
  }


  /**
   * Compare the section from start..end with the given string.
   *
   * @param start the byte position of the first code point of the desired
   * portion of the file
   *
   * @param end the byte position of first code point after the desired portion
   * of the file
   *
   * @param s a String
   *
   * @return -1, 0, +1 if the string in the file is smaller, equal or larger
   * than s comparing each single code point until the end. If all are equal,
   * the shorter string is considered smaller.
   */
  public int compareToString(int start, int end, String s)
  {
    if (PRECONDITIONS) require
      (start >= 0,
       end <= _bytes.length);

    int result = 0;
    int pos = start;
    int i = 0;
    while (pos < end && result == 0)
    {
      int cpAndSz = decodeCodePointAndSize(pos);
      int p  = codePointFromCpAndSize(cpAndSz);
      int sz = sizeFromCpAndSize     (cpAndSz);
      if (i < s.length())
        {
          int sc    = s.codePointAt(i);
          result =
            p <  sc ? -1 :
            p == sc ?  0 : +1;
          i = i + (s.charAt(i) == sc ? 1 : 2 /* a surrogate pair */);
        }
      else
        {
          result = p <  0 ? -1 : +1;
        }
      pos += sz;
    }
    result =
      result != 0     ? result :
      i <  s.length() ? -1 :
      i == s.length() ?  0 : +1;
    return result;
  }

  /**
   * Determine the line number of the given byte position in this file
   *
   * @param pos a byte position
   *
   * @return the line number, 1..lines().length.  return 1 for empty file.
   */
  public int lineNum(int pos)
  {
    if (PRECONDITIONS) require
      (pos >= 0,
       pos <= _bytes.length);

    int line;
    if (_FAST_LINE_NUM_)
      {
        line = _lineNumBase[pos / 128] + _lineNumOffset[pos];
      }
    else
      {
        int l = Arrays.binarySearch(lines(), pos);
        line = (l >= 0) ? l :
          -l - 2; // l == -ip-1, where ip is the element behind the desired line index (ip == line + 1), so line == ip - 1 = -l - 2
      }
    if (POSTCONDITIONS) ensure
      (lines().length == 1 || line >= 1,
       _bytes.length != 0 || line == 1,
       line <  lines().length);

    return line;
  }


  /**
   * Determine the start byte position of the given line.
   *
   * @param line the line number starting at 1 for the first line. May be 0
   *
   + @return byte position of the beginning of the line, -1 for line==0.
   */
  public int lineStartPos(int line)
  {
    if (PRECONDITIONS) require
      (line >= 0,
       line <= numLines());

    return lines()[line];
  }


  /**
   * Number of lines in the file.
   */
  public int numLines()
  {
    return lines().length-1;
  }


  /**
   * Determine the last byte position of the given line
   */
  public int lineEndPos(int line)
  {
    return line == numLines() ? _bytes.length : lineStartPos(line + 1) - 1;
  }


  /**
   * Determine the code point index of pos within its line, starting at 1 for
   * the first code point in a line.
   */
  public int codePointIndentation(int pos)
  {
    if (!_FAST_LINE_NUM_)
      {
        lines();  // make sure indentation data is present.
      }
    var res = _indentationByte[pos] & 0xff;
    if (res >= 128)
      {
        res = _indentationBase[pos/128] + res;
      }
    return res;
  }


  /**
   * Obtain the given position as a SourcePosition object.
   *
   * @param pos a byte position within this file.
   */
  public SourcePosition sourcePos(int pos)
  {
    return new SourcePosition(this, pos);
  }


  /**
   * Obtain the current position as a SourcePosition object.
   */
  public SourcePosition sourcePos()
  {
    return sourcePos(_pos);
  }


  /**
   * Obtain the given range pos..endPos as a SourceRange object.
   *
   * @param pos a byte position within this file.
   *
   * @param endPos a byte position after pos within this file.
   */
  public SourceRange sourceRange(int pos, int endPos)
  {
    if (PRECONDITIONS) require
      (0 <= pos,
       pos <= endPos,
       endPos <= byteLength());

    return new SourceRange(this, pos, endPos);
  }


  /**
   * Get the contents of the line with the given number.
   *
   * @param l a line number
   *
   * @return {@literal if 0 < l < lines().length, the contents of that line. Otherwise,
   * if l <= 0, <line does not exist>, otherwise <end of file>.}
   */
  public String line(int l)
  {
    String result;
    int[] lines = lines();
    if (l <= 0)
      {
        result = "<line does not exist>";
      }
    else if (l >= lines.length)
      {
        result = "<end of file>";
      }
    else
      {
        StringBuilder sb = new StringBuilder();
        int pos = lines[l];
        int lineEnd = l+1 < lines.length ? lines[l+1] : _bytes.length;
        while (pos < lineEnd)
          {
            int cpAndSz = decodeCodePointAndSize(pos);
            int p  = codePointFromCpAndSize(cpAndSz);
            int sz = sizeFromCpAndSize     (cpAndSz);
            sb.appendCodePoint(p);
            pos += sz;
          }
        result = sb.toString();
      }
    return result;
  }


  /**
   * Get the length of this file in bytes
   *
   * @return the length
   */
  public int byteLength()
  {
    return _bytes.length;
  }


  /**
   * Get the byte at the given position
   *
   * @param i an index in the file
   *
   * @return the byte at given index
   */
  public byte byteAt(int i)
  {
    if (PRECONDITIONS) require
                         (i >= 0,
                          i < byteLength());

    return _bytes[i];
  }


  /**
   * Get the raw bytes of this file
   *
   * @return the byte array
   */
  public byte[] bytes()
  {
    return _bytes;
  }


  /**
   * Get bytes of codepoint starting at the given position
   *
   * @param pos an index in the file
   *
   * @return the byte[] at given index
   */
  public byte[] bytesAt(int pos) {
    var codePointSize = codePointSize(pos);
    byte[] result = new byte[codePointSize];
    for (int i = 0; i < codePointSize; i++) {
      result[i] = byteAt(pos+i);
    }
    return result;
  }


  /**
   * Check if this SourceFile operates on the same byte array as other.
   *
   * This ensures that any clones created via {@code new SourceFile(origin)} will be
   * considered to be the same.
   */
  public boolean sameAs(SourceFile other)
  {
    return _bytes == other._bytes;
  }

}

/* end of file */
