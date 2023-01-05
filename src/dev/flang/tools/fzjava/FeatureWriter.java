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
 * Source of class FeatureWriter
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.fzjava;

import dev.flang.parser.Lexer;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;


/**
 * FeatureWriter provides helper methods for FZJava to write source code of
 * Fuzion features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
class FeatureWriter extends ANY
{


  /**
   * Write a feature's code to a file fn + suffix + ".fz".
   *
   * @param fzj the FZJava tool instance
   *
   * @param fn a feature name, e.g., "Java/java/lang/Object"
   *
   * @param suffix a suffix, e.g., "" or "_static"
   *
   * @param data the data to write to the file.
   */
  static void write(FZJava fzj, String fn, String suffix, String data)
  {
    var fzp = fzj._options._dest;
    if (fzj._options._modules.size() > 1)
      {
        fzp = fzp.resolve(fzj._currentModule);
      }

    while (fn.indexOf("/") >= 0)
      {
        var d = mangle(fn.substring(0, fn.indexOf("/")));
        fzp = fzp.resolve(d);
        fn = fn.substring(fn.indexOf("/")+1);
      }
    fzp = fzp.resolve(mangle(fn) + suffix + ".fz");
    try
      {
        var fzd = fzp.getParent();
        if (fzd != null)
          {
            Files.createDirectories(fzd);
          }
        if (fzj._options._overwrite || !Files.exists(fzp))
          {
            if (fzj._verbose > 0)
              {
                System.out.println(" + " + fzp);
              }
            Files.write(fzp, data.getBytes(StandardCharsets.UTF_8));
          }
      }
    catch (IOException ioe)
      {
        Errors.error("failed to write file '" + fzp + "': " + ioe);
      }
  }


  /**
   * Process a name first by cleanName, then by mangle.
   *
   * @param n a string, e.g., "java/lang/ref/Phantom$Reference"
   *
   * @return a string without any keywords nor special characters, e.g.,
   * "java/lang/_k_ref/Phantom_S_Reference"
   */
  static String mangledCleanName(String n)
  {
    return mangle(cleanName(n));
  }


  /**
   * For a name that is separated by '.', find all parts that are Fuzion
   * keywords and replace them by "_k_" + <keyword>
   *
   * @param n a string, e.g., "java/lang/ref/PhantomReference"
   *
   * @return a string without any keywords, e.g., "java/lang/_k_ref/PhantomReference"
   */
  static String cleanName(String n)
  {
    StringBuilder res = new StringBuilder();
    for (var s : n.split("\\."))
      {
        if (Lexer.isKeyword(s))
          {
            s = "_k_" + s;
          }
        else if (s.equals(FuzionConstants.OBJECT_NAME))
          {
            // NYI: Due to #40, we cannot declare an inner feature with name 'Object',
            // so we replace it by '_jObject'.
            s = "_j" + FuzionConstants.OBJECT_NAME;
          }
        else if (s.equals("List"))
          {
            // NYI: Due to #40, we cannot declare an inner feature with name 'List',
            // so we replace it by '_jList'.
            s = "_jList";
          }
        else if (s.equals("Sequence") ||
                 s.equals("hashCode") ||
                 s.equals(FuzionConstants.STRING_NAME) ||
                 s.equals("array"   ) ||
                 s.equals("isBlank" ) ||
                 s.equals("split"   )    )
          {
            // NYI: this is just a precaution to avoid confusion with Fuzion
            // types.  Need to find a way to avoid this, e.g., by using
            // 'universe.string' to refer to the Fuzion string or by renaming
            // inherited 'hashCode'
            s = "_j" + s;
          }

        if (res.length() > 0)
          {
            res.append(".");
          }
        res.append(s);
      }
    return res.toString();
  }


  /**
   * Perform name mangling to create a fuzion name
   *
   * @param n a name such as "java/security/Policy$Parameters" or
   * "java.security.Policy$Parameters"
   *
   * @return a mangled name such as "java.security.Policy_S_Parameters"
   */
  static String mangle(String n)
  {
    StringBuilder res = new StringBuilder();
    n.codePoints().forEach(i ->
      {
        if (i >= 'a' && i <= 'z' ||
            i >= 'A' && i <= 'Z' ||
            i >= '0' && i <= '9' ||
            i == '.')
          {
            res.appendCodePoint(i);
          }
        else if (i == '_')
          {
            res.append("__");
          }
        else if (i == '$')
          {
            res.append("_S_");
          }
        else if (i == '/')
          {
            res.append("_7_");
          }
        else if (i == ';')
          {
            res.append("_s_");
          }
        else
          {
            res.append("_u").append(Integer.toHexString(0x1000000 + i).substring(1)).append("_");
          }
      });
    return res.toString();
  }

}

/* end of file */
