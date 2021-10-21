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
 * Source of class FrontEndOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;
import dev.flang.util.List;


/**
 * FrontEndOptions specify the configuration of the front end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FrontEndOptions extends FuzionOptions
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Read code from stdin?
   */
  final boolean _readStdin;


  /**
   * Read code from file?
   */
  final Path _inputFile;


  /**
   * List of modules added to fuzion.
   */
  final List<String> _modules; // = new List<>("java.base");


  /**
   * main feature name, null iff _readStdin
   */
  final String _main;


  /**
   * Path to the Fuzion home directory, never null.
   */
  final Path _fuzionHome;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Costructor initializing fields as given.
   */
  public FrontEndOptions(int verbose, Path fuzionHome, List<String> modules, int fuzionDebugLevel, boolean fuzionSafety, boolean readStdin, String main)
  {
    super(verbose,
          fuzionDebugLevel,
          fuzionSafety);

    if (PRECONDITIONS) require
                         (verbose >= 0,
                          fuzionHome != null,
                          readStdin || main != null,
                          !readStdin || main == null,
                          modules != null);

    _fuzionHome = fuzionHome;
    _readStdin = readStdin;
    Path inputFile = null;
    if (main != null)
      {
        var ix = main.lastIndexOf(".");
        if (ix >= 0)
          {
            var suffix = main.substring(ix+1).toUpperCase();
            if (suffix.equals("FZ"    ) ||
                suffix.equals("FU"    ) ||
                suffix.equals("FUZION") ||
                suffix.equals("TXT"   ) ||
                suffix.equals("SRC"   )    )
              {
                var p = Path.of(main).toAbsolutePath();
                if (Files.exists(p))
                  {
                    inputFile = p;
                    main = null;
                  }
              }
          }
      }
    _inputFile = inputFile;
    _modules = modules;
    _main = main;
  }


  /*-----------------------------  methods  -----------------------------*/

}

/* end of file */
