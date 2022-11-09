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

import java.util.Collection;
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
   * The base module if it was loaded from base.fum, null otherwise.
   */
  public final LibraryModule _baseModule;


  /**
   * The library modules loaded so far.  Maps the module name, e.g. "base" to
   * the corresponding LibraryModule instance.
   */
  private TreeMap<String, LibraryModule> _modules = new TreeMap<>();


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
        _baseModule = module("base", modulePath("base"));
        if (_baseModule != null)
          {
            lms.add(_baseModule);
          }
      }
    else
      {
        _baseModule = null;
      }
    for (int i = 0; i < options._modules.size(); i++)
      {
        var m = _options._modules.get(i);
        var loaded = loadModule(m, true);
        if (loaded != null)
          {
            lms.add(loaded);
          }
        else if (Errors.count() == 0)
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
   * Determine the path of the base modules, "$(FUZION)/modules".
   */
  private Path baseModuleDir()
  {
    return _options._fuzionHome.resolve("modules");
  }


  /**
   * Determine the path to load module 'name' from.  E.g., for module 'base',
   * this returns the path '<fuzionHome>/modules/base.fum'.
   *
   * @param a module name, without path or suffix
   *
   * @return the path to the module, null if not found.
   */
  private Path modulePath(String name)
  {
    var n = name + ".fum";
    var p = baseModuleDir().resolve(n);
    var i = 0;
    var mds = _options._moduleDirs;
    while (!Files.exists(p) && i < mds.size())
      {
        p = Path.of(mds.get(i)).resolve(n);
        i++;
      }
    return p;
  }


  /**
   * Load module from given path.
   */
  private LibraryModule module(String m, Path p)
  {
    LibraryModule result = null;
    try (var ch = (FileChannel) Files.newByteChannel(p, EnumSet.of(StandardOpenOption.READ)))
      {
        var data = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        result = new LibraryModule(this, data, new LibraryModule[0], _universe);
        if (!m.equals(result.name()))
          {
            Errors.error("Module name mismatch for module file '" + p + "' expected name '" +
                         m + "' but found '" + result.name() + "'");
          }
        _modules.put(m, result);
      }
    catch (IOException io)
      {
        Errors.error("FrontEnd I/O error when reading module file",
                     "While trying to read file '"+ p + "' received '" + io + "'");
      }
    return result;
  }

  /**
   * Load library module with given module name
   *
   * @param m the module name, excluding path or ".fum" suffix
   *
   * @return the loaded module or null if it was not found or an error occured.
   */
  LibraryModule loadModule(String m)
  {
    return loadModule(m, false);
  }
  LibraryModule loadModule(String m,
                           boolean ignoreNotFound // NYI: remove when module support is stable
                           )
  {
    var result = _modules.get(m);
    if (result != null)
      {
        return result;
      }
    else
      {
        var p = modulePath(m);
        if (p != null)
          {
            return module(m, p);
          }
        else if (ignoreNotFound)
          {
            return null;
          }
        else
          {
            Errors.error("Module file '"+(m + ".fum")+"' for module '"+m+"' not found, "+
                         "module directories checked are '" + baseModuleDir() + "' and " +
                         _options._moduleDirs.toString("'","', '", "'") + ".");
            return null;
          }
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


  /**
   * During resolution, load all inner classes of this that are
   * defined in separate files.
   */
  void loadInnerFeatures(AbstractFeature f)
  {
    var m = module();
    if (m != null)
      {
        m.loadInnerFeatures(f);
      }
  }


  /**
   * Return the collection of loaded modules.
   */
  public Collection<LibraryModule> getModules()
  {
    return _modules.values();
  }

}

/* end of file */
