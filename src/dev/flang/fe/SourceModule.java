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
 * Source of class SourceModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.flang.ast.Block;
import dev.flang.ast.Feature;
import dev.flang.ast.Impl;
import dev.flang.ast.Resolution;

import dev.flang.mir.MIR;

import dev.flang.parser.Parser;

import dev.flang.util.Errors;
import dev.flang.util.SourceDir;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * A SourceModule represents a Fuzion module created directly from source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SourceModule extends Module
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FrontEndOptions _options;


  /**
   * All the directories we are reading Fuzion sources form.
   */
  private final SourceDir[] _sourceDirs;


  /**
   * If input comes from a specific file, this give the file.  May be
   * SourceFile.STDIN.
   */
  private final Path _inputFile;


  /**
   * The universe is the implicit root of all features that
   * themeselves do not have their own root.
   */
  private Feature _universe;


  /**
   * If a main feature is defined for this module, this gives its name. Should
   * be null if a specific _inputFile defines the main feature.
   */
  private String _defaultMain;


  /**
   * Flag to forbid loading of source code for new features for this module once
   * MIR was created.
   */
  private boolean _closed = false;


  /**
   * In case this module defines a main feature, this is its fully qualified
   * name.
   */
  String _main;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create SourceModule for given options and sourceDirs.
   */
  SourceModule(FrontEndOptions options, SourceDir[] sourceDirs, Path inputFile, String defaultMain, Module[] dependsOn)
  {
    super(dependsOn);

    _options = options;
    _sourceDirs = sourceDirs;
    _inputFile = inputFile;
    _defaultMain = defaultMain;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Run the given parser to parse statements. This is used for processing stdin
   * or an explicit input file.  These require special treatment since it is
   * allowed to declare initializes fields in here.
   *
   * @return the main feature found or null if none
   */
  String parseStdIn(Parser p)
  {
    var stmnts = p.stmntsEof();
    ((Block) _universe.impl._code).statements_.addAll(stmnts);
    boolean first = true;
    String main = null;
    for (var s : stmnts)
      {
        main = null;
        if (s instanceof Feature f)
          {
            f.legalPartOfUniverse();  // suppress FeErrors.initialValueNotAllowed
            if (first)
              {
                main = f.featureName().baseName();
              }
          }
        first = false;
      }
    return main;
  }


  /**
   * Create the module intermediate representation for this module.
   */
  void createMIR0()
  {
    /* create the universe */
    _universe = _dependsOn.length > 0 ? ((SourceModule)_dependsOn[0])._universe : Feature.createUniverse();
    check
      (_universe != null);

    _main = (_inputFile != null)
      ? parseStdIn(new Parser(_inputFile))
      : _defaultMain;

    if (_dependsOn.length > 0)
      {
        _universe.resetState();   // NYI: HACK: universe is currently resolved twice, once as part of stdlib, and then as part of another module
      }
    _universe.findDeclarations(null);
    var res = new Resolution(_options, _universe, (r, f) -> loadInnerFeatures(r, f));
    _universe.scheduleForResolution(res);
    res.resolve();
  }


  /**
   * Create the module intermediate representation for this module.
   */
  public MIR createMIR()
  {
    Feature d = _main == null
      ? _universe
      : _universe.get(_main);

    if (false)  // NYI: Eventually, we might want to stop here in case of errors. This is disabled just to check the robustness of the next steps
      {
        Errors.showAndExit();
      }

    _closed = true;
    return createMIR(d);
  }



  /**
   * Create MIR based on given main feature.
   */
  MIR createMIR(Feature main)
  {
    if (main != null && Errors.count() == 0)
      {
        if (main.arguments.size() != 0)
          {
            FeErrors.mainFeatureMustNotHaveArguments(main);
          }
        if (main.isField())
          {
            FeErrors.mainFeatureMustNotBeField(main);
          }
        if (main.impl == Impl.ABSTRACT)
          {
            FeErrors.mainFeatureMustNotBeAbstract(main);
          }
        if (main.impl == Impl.INTRINSIC)
          {
            FeErrors.mainFeatureMustNotBeIntrinsic(main);
          }
        if (!main.generics.list.isEmpty())
          {
            FeErrors.mainFeatureMustNotHaveTypeArguments(main);
          }
      }
    var result = new MIR(_universe, main);
    if (Errors.count() == 0)
      {
        new DFA(result).check();
      }

    return result;
  }



  /**
   * Check if a sub-directory corresponding to the given feature exists in the
   * source directory with the given root.
   *
   * @param root the top-level directory of the source directory
   *
   * @param f a feature
   *
   * @return a path from root, via the base names of f's outer features to a
   * directory wtih f's base name, null if this does not exist.
   */
  private SourceDir dirExists(SourceDir root, Feature f) throws IOException, UncheckedIOException
  {
    var o = f.outer();
    if (o == null)
      {
        return root;
      }
    else
      {
        var d = dirExists(root, o);
        return d == null ? null : d.dir(f.featureName().baseName());
      }
  }


  /**
   * Check if p denotes a file that should be read implicitly as source code,
   * i.e., its name ends with ".fz", it is a readable file and it is not the
   * same as _inputFile (which will be read explicitly).
   */
  boolean isValidSourceFile(Path p)
  {
    try
      {
        return p.getFileName().toString().endsWith(".fz") &&
          Files.isReadable(p) &&
          (_inputFile == null || _inputFile == SourceFile.STDIN || !Files.isSameFile(_inputFile, p));
      }
    catch (IOException e)
      {
        throw new UncheckedIOException(e);
      }
  }


  /**
   * During resolution, load all inner classes of this that are
   * defined in separate files.
   */
  private void loadInnerFeatures(Resolution res, Feature f)
  {
    if (!_closed)
      {
        for (var root : _sourceDirs)
          {
            try
              {
                var d = dirExists(root, f);
                if (d != null)
                  {
                    Files.list(d._dir)
                      .forEach(p ->
                               {
                                 if (isValidSourceFile(p))
                                   {
                                     Feature inner = parseFile(p);
                                     check
                                       (inner != null || Errors.count() > 0);
                                     if (inner != null)
                                       {
                                         inner.findDeclarations(f);
                                         inner.scheduleForResolution(res);
                                       }
                                   }
                               });
                  }
              }
            catch (IOException | UncheckedIOException e)
              {
                Errors.warning("Problem when listing source directory '" + root._dir + "': " + e);
              }
          }
      }
  }


  /**
   * Load and parse the fuzion source file for the feature with the
   * given file name
   *
   * @param name a qualified name, e.g. "fuzion.std.out"
   *
   * @return the parsed source file or null in case of an error.
   */
  Feature parseFile(Path fname)
  {
    _options.verbosePrintln(2, " - " + fname);
    return new Parser(fname).unit();
  }

}

/* end of file */
