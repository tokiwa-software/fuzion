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

import java.io.IOException;

import java.nio.channels.FileChannel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.EnumSet;
import java.util.TreeMap;

import dev.flang.mir.MIR;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourceDir;
import dev.flang.util.SourceFile;


/**
 * The FrontEnd creates the module IR (mir) from the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FrontEnd extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  static FeatureName UNIVERSE_NAME = FeatureName.get(FuzionConstants.UNIVERSE_NAME, 0);


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Class for the Universe Feature.
   */
  class Universe extends Feature
  {
    { setState(Feature.State.LOADING); }
    public boolean isUniverse()
    {
      return true;
    }

    public FeatureName featureName()
    {
      return UNIVERSE_NAME;
    }

    public AbstractFeature get(String name, int argcount)
    {
      AbstractFeature result = Types.f_ERROR;
      var d = _module.declaredFeatures(this);
      var set = (argcount >= 0
                 ? FeatureName.getAll(d, name, argcount)
                 : FeatureName.getAll(d, name          )).values();
      if (set.size() == 1)
        {
          for (var f2 : set)
            {
              result = f2;
            }
        }
      else if (set.isEmpty())
        {
          AstErrors.internallyReferencedFeatureNotFound(pos(), name, this, name);
        }
      else
        { // NYI: This might happen if the user adds additional features
          // with different argCounts. name should contain argCount to
          // avoid this
          AstErrors.internallyReferencedFeatureNotUnique(pos(), name + (argcount >= 0 ? " (" + Errors.argumentsString(argcount) : ""), set);
        }
      return result;
    }
  }


  /*----------------------------  variables  ----------------------------*/


  /**
   * The options argument
   */
  public final FrontEndOptions _options;


  /**
   * The universe.
   */
  public final AbstractFeature _universe;


  /**
   * The base module if it was loade from base.fum, null otherwise.
   */
  public final LibraryModule _baseModule;


  /**
   * The library modules loaded so far.  Maps the module name, e.g. "base" to
   * the corresponding LibraryModule instance.
   */
  TreeMap<String, LibraryModule> _modules = new TreeMap<>();


  /**
   * The module we are compiling. null if !options._loadSources or Errors.count() != 0
   */
  private final SourceModule _module;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create front end for given options.
   */
  public FrontEnd(FrontEndOptions options)
  {
    _options = options;
    Types.reset();
    Errors.reset();
    FeatureName.reset();
    var universe = new Universe();
    _universe = universe;

    var sourcePaths = options.sourcePaths();
    var sourceDirs = new SourceDir[sourcePaths.length + options._modules.size()];
    for (int i = 0; i < sourcePaths.length; i++)
      {
        sourceDirs[i] = new SourceDir(sourcePaths[i]);
      }
    var lms = new List<LibraryModule>();
    if (options._loadBaseLib)
      {
        _baseModule = module(modulePath("base"));
        lms.add(_baseModule);
      }
    else
      {
        _baseModule = null;
      }
    for (int i = 0; i < options._modules.size(); i++)
      {
        var m = _options._modules.get(i);
        var p = modulePath(_options._modules.get(i));
        if (Files.exists(p))
          {
            lms.add(module(p));
          }
        else
          { // NYI: Fallback if module file does not exists use source files instead. Remove this.
            sourceDirs[sourcePaths.length + i] = new SourceDir(options._fuzionHome.resolve(Path.of("modules")).resolve(Path.of(m)));
          }
      }
    var dependsOn = lms.toArray(LibraryModule[]::new);
    if (options._loadSources)
      {
        _module = new SourceModule(options, sourceDirs, inputFile(options), options._main, dependsOn, universe);
        _module.createASTandResolve();
      }
    else
      {
        _module = null;
      }
  }


  /**
   * Determine the path to load module 'name' from.  E.g., for module 'base',
   * this returns the path '<fuzionHome>/modules/base.fum'.
   *
   * @þaram a module name, without path or suffix
   *
   * @return the path to the module, never null.
   */
  private Path modulePath(String name)
  {
    return _options._fuzionHome.resolve("modules").resolve(name + ".fum");
  }


  /**
   * Load module from given path.
   */
  private LibraryModule module(Path p)
  {
    try (var ch = (FileChannel) Files.newByteChannel(p, EnumSet.of(StandardOpenOption.READ)))
      {
        var data = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        return new LibraryModule(this, data, new LibraryModule[0], _universe);
      }
    catch (IOException io)
      {
        Errors.error("FrontEnd I/O error when reading module file",
                     "While trying to read file '"+ p + "' received '" + io + "'");
        return null;
      }
  }


  /**
   * Get the path of one additional main input file (like compiling from stdin
   * or just one single source file).
   */
  private Path inputFile(FrontEndOptions options)
  {
    return
      options._readStdin         ? SourceFile.STDIN   :
      options._inputFile != null ? options._inputFile
                                 : null;
  }


  /*-----------------------------  methods  -----------------------------*/


  public MIR createMIR()
  {
    return _module.createMIR();
  }


  public SourceModule module()
  {
    return _module;
  }

}

/* end of file */
