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

import java.util.function.Consumer;

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
   * Read code from command line {@code -e/-execute <code>}, or null if option not
   * given.
   */
  final byte[] _executeCode;


  /**
   * Read code from file?
   */
  final Path _inputFile;


  /**
   * List of modules added to fuzion.
   */
  final List<String> _modules; // = new List<>("java.base");


  /**
   * Directories to load module files from.
   */
  final List<String> _moduleDirs;


  /**
   * List of modules to be dumped to stdout after loading
   */
  final List<String> _dumpModules;


  /**
   * main feature name, null iff _readStdin || _executeCode != null
   */
  final String _main;


  /**
   * The name of the module we are compiling.
   */
  final String _moduleName;


  /**
   * true to load base module (false if we are creating it)
   */
  final boolean _loadBaseMod;


  /**
   * When saving to a .fum module file, erase internal names of features since
   * they should not be needed. This can be disabled for debugging.
   */
  final boolean _eraseInternalNamesInMod;


  /**
   * Should we load any source files after we loaded the base module?
   */
  final boolean _loadSources;


  /**
   * Do we need to perform escape analysis during DFA phase since the backend needs that?
   *
   * This currently has a significant impact on the DFA performance, so we try to
   * avoid this for backends that do not need it (JVM and interpreter).
   */
  final boolean _needsEscapeAnalysis;


  /**
   * Should the FUIR be serialized or, in case already
   * serialized, loaded from .fuir file?
   */
  final boolean _serializeFuir;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor initializing fields as given.
   *
   * @param timer can be called with a phase name to measure the time spent in
   * this phase, printed if {@code -verbose} level is sufficiently high.
   */
  public FrontEndOptions(int verbose,
                         Path fuzionHome,
                         boolean loadBaseMod,
                         boolean eraseInternalNamesInMod,
                         List<String> modules,
                         List<String> moduleDirs,
                         List<String> dumpModules,
                         int fuzionDebugLevel,
                         boolean fuzionSafety,
                         boolean enableUnsafeIntrinsics,
                         List<String> sourceDirs,
                         boolean readStdin,
                         byte[] executeCode,
                         String main,
                         String moduleName,
                         boolean loadSources,
                         boolean needsEscapeAnalysis,
                         boolean serializeFuir,
                         Consumer<String> timer)
  {
    super(verbose,
          fuzionDebugLevel,
          fuzionSafety,
          enableUnsafeIntrinsics,
          fuzionHome,
          timer);

    if (PRECONDITIONS) require
      (verbose >= 0,

       // at most one of _readStdin, main != null or executeCode != null may be true.
       !readStdin          || main == null && executeCode == null,
       executeCode == null || main == null && !readStdin,
       main == null        || !readStdin   && executeCode == null,

       modules != null,
       moduleDirs != null);

    _loadBaseMod = loadBaseMod;
    _eraseInternalNamesInMod = eraseInternalNamesInMod;
    _readStdin = readStdin;
    _executeCode = executeCode;
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
    _moduleDirs = moduleDirs;
    _dumpModules = dumpModules;
    _main = main;
    _moduleName = moduleName;
    _loadSources = loadSources;
    _needsEscapeAnalysis = needsEscapeAnalysis;
    if (sourceDirs == null)
      {
        sourceDirs = inputFile != null || readStdin  || executeCode != null ? new List<>() : new List<>(".");
      }
    _sourceDirs = sourceDirs;
    _serializeFuir = serializeFuir;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Get all the paths to use to read source code from
   */
  Path[] sourcePaths()
  {
    return _sourceDirs.stream().map(x -> Path.of(x)).toArray(Path[]::new);
  }


  /**
   * Do we need to perform escape analysis during DFA phase since the backend needs that?
   *
   * This is always the case if we serialize the FUIR.
   *
   * This currently has a significant impact on the DFA performance, so we try to
   * avoid this for backends that do not need it (JVM and interpreter).
   *
   * @return true if escape analysis has to be performed.
   */
  public boolean needsEscapeAnalysis()
  {
    return _needsEscapeAnalysis || serializeFuir();
  }


  /**
   * Should the FUIR be serialized or, in case already
   * serialized, loaded from .fuir file?
   */
  public boolean serializeFuir()
  {
    return _serializeFuir;
  }


  /**
   * The input file to use.
   *
   * This is either a regular file,
   * SourceFile.STDIN or
   * SourceFile.COMMAND_LINE_DUMMY
   */
  public Path inputFile()
  {
    return _inputFile;
  }

}

/* end of file */
