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
 * Source of class ParseUnicodeData
 *
 *---------------------------------------------------------------------*/

package dev.flang.util.unicode;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FatalError;
import dev.flang.util.List;

/**
 * ParseUnicodeData parses file UnicodeData.txt from
 * https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt and uses the
 * information from that file to create Java classes to access unicode
 * properties.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class ParseUnicodeData extends ANY
{


  /*----------------------------  classes  ----------------------------*/

  /**
   * Class representing a line in UnicodeData.txt that represents on code point.
   */
  class CP implements Comparable<CP>
  {
    int _code;
    String _name;
    String _category;
    String _canonical_combining_classes;
    String _bidir_cat;
    String _decomp_mapping;
    String _decimal_digit_value;
    String _digit_value;
    String _numeric_value;
    String _mirrored;
    String _unicode1_0name;
    String _iso10646comment;
    String _uppercaseMapping;
    String _lowercaseMapping;
    String _titlecaseMapping;


    /**
     * Decode UnicodeData.txt line as defined in http://www.unicode.org/L2/L1999/UnicodeData.html
     *
     * @param s the line
     */
    CP(String s)
    {
      var e = s.split(";");
      if (e.length < 10)
        {
          Errors.fatal("*** error, expected 15 entries, found "+e.length+" for "+s);
        }
      _code = Integer.parseInt(e[0],16);
      _name = e[1];
      var c = e[2];
      var cat = _cats.get(c);
      if (cat == null)
        {
          _cats.put(c, c);
          _categories.add(c);
          cat = c;
        }
      _category = cat;
      _canonical_combining_classes = e[3];
      _bidir_cat = e[4];
      _decomp_mapping = e[5];
      _decimal_digit_value = e[6];
      _digit_value = e[7];
      _numeric_value = e[8];
      _mirrored = e[9];
      _unicode1_0name = e.length > 10 ? e[10] : "";
      _iso10646comment = e.length > 11 ? e[11] : "";
      _uppercaseMapping = e.length > 12 ? e[12] : "";
      _lowercaseMapping = e.length > 13 ? e[13] : "";
      _titlecaseMapping = e.length > 14 ? e[14] : "";
    }

    /**
     * Is this the start code point of a special range of code points (such as
     * CJK Ideographs)?
     */
    boolean isFirst()
    {
      return _name.startsWith("<") && _name.endsWith(", First>");
    }

    /**
     * Is this the end code point of a special range of code points (such as CJK
     * Ideographs)?
     */
    boolean isLast()
    {
      return _name.startsWith("<") && _name.endsWith(", Last>");
    }

    /*
     * compare this CP to other
     */
    public int compareTo(CP other)
    {
      return this._code - other._code;
    }
  }


  /**
   * A block of consecutive code points of the same category
   */
  class Block
  {
    CP _first, _last;

    Block(CP f, CP l)
    {
      _first = f;
      _last = l;
    }
  }


  /*-----------------------------  statics  -----------------------------*/


  /**
   * Set to true for verbose output.
   */
  static final boolean VERBOSE = false;


  /*----------------------------  variables  ----------------------------*/


  /**
   * All parsed codepoints.
   */
  Set<CP> _codepoints = new HashSet<CP>();


  /**
   * The consecutive ranges of equal category code points that were found.
   */
  List<Block> _blocks = new List<Block>();

  /**
   * The categories that were found, both as a map to unique strings and as a
   * list.
   */
  Map<String, String> _cats = new TreeMap<String, String>();
  List<String> _categories = new List<String>();

  /**
   * Number of blocks of letter categories found
   */
  int _letterBlocks = 0;

  /**
   * Number of blocks of letters found
   */
  int _letters = 0;

  /**
   * First code point of current block
   */
  CP _firstCP = null;


  /**
   * Last code point of current block (or current code point if end of block not
   * reached).
   */
  CP _lastCP = null;


  /**
   * Filepath of UnicodeData.txt
   */
  private String _name;


  /**
   * Last modified time of UnicodeData.txt
   */
  private FileTime _lastModified;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor of ParseUnicodeData to be called with path of UnicodeData.txt
   * @param name
   */
  ParseUnicodeData(String name)
  {
    _name = name;
    var p = Path.of(name);
    try
      {
        _lastModified = Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime();
        Files.lines(p).forEach(s -> {
            var e = new CP(s);
            _codepoints.add(e);
            if (_lastCP != null && e._code <= _lastCP._code)
              {
                Errors.fatal("*** error, expected unicode data to be sorted");
              }
            if (_firstCP != null && (_firstCP.isLast() || !e.isLast() && e._code > _lastCP._code + 1 || e.isFirst() || e._category != _firstCP._category))
              {
                finishBlock();
              }
            if (_firstCP == null || e._category != _firstCP._category)
              {
                _firstCP = e;
              }
            _lastCP = e;
          });
      }
    catch (IOException | UncheckedIOException e)
      {
        Errors.fatal("*** I/O error: " + e);
      }
    if (_firstCP != null)
      {
        finishBlock();
      }
  }


  interface ToString<T>
  {
    String call(T b);
  }


  <T> String table(java.util.List<T> l, ToString<T> b2s)
  {
    StringBuilder sb = new StringBuilder();
    int line = 200;
    String comma1 = "";
    String comma2 = "";
    for (var e : l)
      {
        var s = b2s.call(e);
        if (line + comma2.length() + s.length() > 118)
          {
            sb.append(comma1)
              .append("\n    ");
            line = 4;
          }
        else
          {
            sb.append(comma2);
            line += comma2.length();
          }
        sb.append(s);
        line += s.length();
        comma1 = ",";
        comma2 = ", ";
      }
    return sb.toString();
  }


  /*--------------------------  static methods  -------------------------*/


  public static void main(String[] args)
  {
    try
      {
        if (!(args.length == 2 && args[0].equals("-fz") || args.length == 1))
          {
            Errors.fatal("Usage: ParseUnicodeData [-fz] <UnicodeData.txt>");
          }

        if (!args[0].equals("-fz"))
          {
            new ParseUnicodeData(args[0]).printJava();
          }
        else
          {
            new ParseUnicodeData(args[1]).printFuzion();
          }
      }
    catch (FatalError e)
      {
        System.exit(e.getStatus());
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Print Java source code for file $(BUILD_DIR)/UnicodeData.java.generated
   */
  private void printJava()
  {
    System.out.println("  /* Unicode data from '" + _name + "' last modified '" + _lastModified + "' */");

    if (VERBOSE)
      {
        System.out.println("" +
                           _letters + " letters in " + _letterBlocks + " blocks, " +
                           _cats.size() + " categories in " + _blocks.size() + " blocks. ");
      }

    System.out.println("  static final int[] _START_ = start0();\n" +
                       "  private static int[] start0() { return new int[] {"          + table(_blocks, x -> "0x" + Integer.toHexString(x._first._code)) + "};\n  }");
    System.out.println("  static final int[] _END_ = end0();\n" +
                       "  private static int[] end0() { return new int[] {"            + table(_blocks, x -> "0x" + Integer.toHexString(x._last ._code)) + "};\n  }");
    System.out.println("  static final String[] _CATEGORY_ = category0();\n"+
                       "  private static String[] category0() { return new String[] {" + table(_blocks, x -> "\"" + x._first._category + "\"") + "};\n  }");
    var c = Arrays.asList(_cats.keySet().toArray(new String[_cats.size()]));
    System.out.println("  static final String[] _CATEGORIES_ = new String[] {" + table(c, x -> "\"" + x + "\"") + "\n  };");
  }


  /**
   * Print Fuzion source code for file unicode_data.fz
   */
  private void printFuzion()
  {
    // NYI performance, see: https://doc.rust-lang.org/src/core/unicode/unicode_data.rs.html
    // NYI special casings: https://www.unicode.org/Public/UCD/latest/ucd/SpecialCasing.txt

    var lTable = _codepoints
      .stream()
      .sorted()
      .filter(cp -> !cp._lowercaseMapping.isBlank())
      .map(cp -> {
        var m = Integer.parseInt(cp._lowercaseMapping, 16);
        return "# " + new String(new int[] { cp._code }, 0, 1) + " => " + new String(new int[] { m }, 0, 1) + "\n" +
          "    (u32 " + cp._code + ", codepoint " + m + ")";
      })
      .collect(Collectors.joining(",\n    "));

    var uTable = _codepoints
      .stream()
      .sorted()
      .filter(cp -> !cp._uppercaseMapping.isBlank())
      .map(cp -> {
        var m = Integer.parseInt(cp._uppercaseMapping, 16);
        return "# " + new String(new int[] { cp._code }, 0, 1) + " => " + new String(new int[] { m }, 0, 1) + "\n" +
          "    (u32 " + cp._code + ", codepoint " + m + ")";
      })
      .collect(Collectors.joining(",\n    "));

    var tTable = _codepoints
      .stream()
      .sorted()
      .filter(cp -> !cp._titlecaseMapping.isBlank())
      .map(cp -> {
        var m = Integer.parseInt(cp._titlecaseMapping, 16);
        return "# " + new String(new int[] { cp._code }, 0, 1) + " => " + new String(new int[] { m }, 0, 1) + "\n" +
          "    (u32 " + cp._code + ", codepoint " + m + ")";
      })
      .collect(Collectors.joining(",\n    "));

    System.out.println("# DO NOT EDIT - AUTOMATICALLY GENERATED");
    System.out.println("# GENERATED BY ./src/dev/flang/util/unicode/ParseUnicodeData.java");
    System.out.println("# Unicode data from '" + _name + "' last modified '" + _lastModified + "'");

    System.out.println();

    System.out.println("module unicode.data is" + "\n\n"
      + "  module lower_case_mappings => " + "container.map_of [\n    " + lTable + "]\n\n\n"
      + "  module upper_case_mappings => " + "container.map_of [\n    " + uTable + "]\n\n\n"
      + "  module title_case_mappings => " + "container.map_of [\n    " + tTable + "]"
      );
  }


  void finishBlock()
  {
    var b = new Block(_firstCP, _lastCP);
    _blocks.add(b);
    int delta = _lastCP._code + 1 - _firstCP._code;
    if (_firstCP._category.startsWith("L"))
      {
        _letterBlocks++;
        _letters += (_lastCP._code + 1 - _firstCP._code);
      }
    _firstCP = null;
  }

}

/* end of file */
