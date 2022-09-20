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
   * Directories to load source files from.
   */
  final List<String> _sourceDirs;


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
   * List of modules to be dumped to stdout after loading
   */
  final List<String> _dumpModules;


  /**
   * main feature name, null iff _readStdin
   */
  final String _main;


  /**
   * Path to the Fuzion home directory, never null.
   */
  final Path _fuzionHome;


  /**
   * true to load base library (false if we are creating it)
   */
  final boolean _loadBaseLib;


  /**
   * When saving to a .fum module file, erase internal names of features since
   * they should not be needed. This can be disabled for debugging.
   */
  final boolean _eraseInternalNamesInLib;


  /**
   * Should we load any source files after we loaded the base library?
   */
  final boolean _loadSources;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Costructor initializing fields as given.
   */
  public FrontEndOptions(int verbose,
                         Path fuzionHome,
                         boolean loadBaseLib,
                         boolean eraseInternalNamesInLib,
                         List<String> modules,
                         List<String> dumpModules,
                         int fuzionDebugLevel,
                         boolean fuzionSafety,
                         boolean enableUnsafeIntrinsics,
                         List<String> sourceDirs,
                         boolean readStdin,
                         String main,
                         boolean loadSources)
  {
    super(verbose,
          fuzionDebugLevel,
          fuzionSafety,
          enableUnsafeIntrinsics);

    if (PRECONDITIONS) require
                         (verbose >= 0,
                          fuzionHome != null,
                          !readStdin || main == null,
                          modules != null);

    _fuzionHome = fuzionHome;
    _loadBaseLib = loadBaseLib;
    _eraseInternalNamesInLib = eraseInternalNamesInLib;
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
                else
                  {
                    Errors.fatal("file does not exist: " + p, "");
                  }
              }
          }
      }
    _inputFile = inputFile;
    _modules = modules;
    _dumpModules = dumpModules;
    _main = main;
    _loadSources = loadSources;
    if (sourceDirs == null)
      {
        sourceDirs = inputFile != null || readStdin ? new List<>() : new List<>(".");
      }
    _sourceDirs = sourceDirs;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get all the paths to use to read source code from
   */
  Path[] sourcePaths()
  {
    return _sourceDirs.stream().map(x -> Path.of(x)).toArray(Path[]::new);
  }


}

/* end of file */
