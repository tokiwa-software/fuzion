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
 * Source of class FrontEnd
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.Block;
import dev.flang.ast.Feature;
import dev.flang.ast.Impl;
import dev.flang.ast.Resolution;

import dev.flang.mir.MIR;

import dev.flang.parser.Parser;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.SourceDir;
import dev.flang.util.SourceFile;
import dev.flang.util.SourcePosition;


/**
 * The FrontEnd creates the module IR (mir) from the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FrontEnd extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The universe is the implicit root of all features that
   * themeselves do not have their own root.
   */
  private Feature _universe;


  /**
   * Configuration
   */
  public final FrontEndOptions _options;


  /**
   * All the directories we are reading Fuzion sources form.
   */
  private final SourceDir[] _sourceDirs;


  /*--------------------------  constructors  ---------------------------*/


  public FrontEnd(FrontEndOptions options)
  {
    _options = options;
    var sourcePaths = new Path[] { options._fuzionHome.resolve("lib"), Path.of(".") };
    _sourceDirs = new SourceDir[sourcePaths.length + options._modules.size()];
    for (int i = 0; i < sourcePaths.length; i++)
      {
        _sourceDirs[i] = new SourceDir(sourcePaths[i]);
      }
    for (int i = 0; i < options._modules.size(); i++)
      {
        _sourceDirs[sourcePaths.length + i] = new SourceDir(options._fuzionHome.resolve(Path.of("modules")).resolve(Path.of(options._modules.get(i))));
      }
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


  public MIR createMIR()
  {
    /* create the universe */
    _universe = true ? Feature.createUniverse()
                    : loadUniverse();
    var main = _options._main;
    if (_options._readStdin)
      {
        main = parseStdIn(new Parser(SourceFile.STDIN));
      }
    else if (_options._inputFile != null)
      {
        main = parseStdIn(new Parser(_options._inputFile));
      }
    _universe.findDeclarations(null);
    var res = new Resolution(_options, _universe, (r, f) -> loadInnerFeatures(r, f));

    // NYI: middle end starts here:

    _universe.markUsed(res, SourcePosition.builtIn);
    Feature d = main == null
      ? _universe
      : _universe.markUsedAndGet(res, main);

    res.resolve(); // NYI: This should become the middle end phase!

    if (false)  // NYI: Eventually, we might want to stop here in case of errors. This is disabled just to check the robustness of the next steps
      {
        Errors.showAndExit();
      }

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
    var result = new MIR(main);
    if (Errors.count() == 0)
      {
        new DFA(result).check();
      }
    return result;
  }



  /**
   * NYI: Under development: load universe from sys/universe.fz.
   */
  Feature loadUniverse()
  {
    Feature result = parseFile(_options._fuzionHome.resolve("sys").resolve("universe.fz"));
    result.findDeclarations(null);
    new Resolution(_options, result, (r, f) -> loadInnerFeatures(r, f));
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
   * same as _options._inputFile (which will be read explicitly).
   */
  boolean isValidSourceFile(Path p)
  {
    try
      {
        return p.getFileName().toString().endsWith(".fz") &&
          Files.isReadable(p) &&
          (_options._inputFile == null || !Files.isSameFile(_options._inputFile, p));
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
            Errors.warning("Problem when listing source directory '" + root + "': " + e);
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
