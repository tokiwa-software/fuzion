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
import java.nio.ByteBuffer;

import java.nio.channels.FileChannel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.mir.MIR;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.ast.Contract;
import dev.flang.ast.Expr;
import dev.flang.ast.HasGlobalIndex;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureAndOuter;
import dev.flang.ast.FeatureName;
import dev.flang.ast.State;
import dev.flang.ast.Types;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourceDir;


/**
 * The FrontEnd creates the module IR (mir) from the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FrontEnd extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Offset added to global indices to detect false usage of these early on.
   */
  static final int GLOBAL_INDEX_OFFSET = 0x40000000;
  static
  {
    // NYI: CLEANUP: #2411: Temporary solution to give global indices to the AST
    // parts created by parser
    HasGlobalIndex.FIRST_GLOBAL_INDEX = 0x10000000;
    HasGlobalIndex.LAST_GLOBAL_INDEX = GLOBAL_INDEX_OFFSET-1;
  }


  /*-----------------------------  classes  -----------------------------*/


  /**
   * Class for the Universe Feature.
   */
  class Universe extends Feature
  {
    private final FeatureName _fn = FeatureName.get(FuzionConstants.UNIVERSE_NAME, 0);

    { setState(State.LOADING); }
    public boolean isUniverse()
    {
      return true;
    }

    public FeatureName featureName()
    {
      return _fn;
    }
  }

  /*----------------------------  variables  ----------------------------*/


  /**
   * The options argument
   */
  public final FrontEndOptions _options;


  /**
   * The library modules loaded so far.  Maps the module name, e.g. "base" to
   * the corresponding LibraryModule instance.
   */
  private TreeMap<String, LibraryModule> _modules = new TreeMap<>();


  /**
   * The module we are compiling. null if !options._loadSources or Errors.count() != 0
   */
  private SourceModule _sourceModule;


  /**
   * The total # of bytes loadeded for modules. Global indices are in this range.
   */
  private int _totalModuleData = 0;


  /**
   * The compiled main module.
   */
  private LibraryModule _mainModule;


  /**
   * The universe that is used by frontend.
   */
  public final Universe _feUniverse;


  /**
   * The modules loaded by frontend.
   */
  private final LibraryModule[] _dependsOn;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create front end for given options.
   */
  public FrontEnd(FrontEndOptions options)
  {
    _options = options;
    reset();
    _feUniverse = new Universe();

    var sourcePaths = options.sourcePaths();
    var sourceDirs = new SourceDir[sourcePaths.length + options._modules.size()];
    for (int i = 0; i < sourcePaths.length; i++)
      {
        sourceDirs[i] = new SourceDir(sourcePaths[i]);
      }

    _dependsOn = loadModules(_feUniverse);

    if (options._loadSources)
      {
        _sourceModule = new SourceModule(options, sourceDirs, _dependsOn, _feUniverse);
        _sourceModule.createASTandResolve();
      }
    else
      {
        _sourceModule = null;
      }
  }


  /**
   * Load modules using universe.
   *
   * @param universe the universe to use to load the modules
   *
   * @return dependsOn, usually base module and modules explicitly specified in -modules=...
   */
  private LibraryModule[] loadModules(AbstractFeature universe)
  {
    if (_options._loadBaseLib)
      {
        module(FuzionConstants.BASE_MODULE_NAME, modulePath(FuzionConstants.BASE_MODULE_NAME), universe);
      }
    _options._modules.stream().forEach(mn -> loadModule(mn, universe));

    // Visibility not propagated through modules: see #484
    return _modules
      .entrySet()
      .stream()
      .filter(kv -> {
        var moduleName = kv.getKey();
        return _options._loadBaseLib && moduleName.equals(FuzionConstants.BASE_MODULE_NAME)
          || _options._modules.contains(moduleName);
      })
      .map(x -> x.getValue())
      .toArray(LibraryModule[]::new);
  }


  /**
   * reset almost all data in the front end.
   *
   * NYI: CLEANUP: remove this code
   */
  private void reset()
  {
    _totalModuleData = 0;
    Types.reset(_options);
    FeatureAndOuter.reset();
    Errors.reset();
    FeatureName.reset();
    Expr.reset();
    Call.reset();
    Contract.reset();
    HasGlobalIndex.reset();
    _sourceModule = null;
    _modules.clear();
    _mainModule = null;
  }


  /**
   * Determine the path of the base modules, "$(FUZION)/modules".
   */
  private Path baseModuleDir()
  {
    return _options.fuzionHome().resolve("modules");
  }


  /**
   * Determine the path to load module 'name' from.  E.g., for module 'base',
   * this returns the path '<fuzionHome>/modules/base.fum'.
   *
   * @param name module name, without path or suffix
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
  private LibraryModule module(String m, Path p, AbstractFeature universe)
  {
    LibraryModule result = null;
    try (var ch = (FileChannel) Files.newByteChannel(p, EnumSet.of(StandardOpenOption.READ)))
      {
        var data = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        result = libModule(data, x -> new LibraryModule[0], universe);
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
   * create a new LibraryModule from `data`
   */
  private LibraryModule libModule(ByteBuffer data, Function<AbstractFeature, LibraryModule[]> loadDependsOn, AbstractFeature universe)
  {
    LibraryModule result;
    var base = _totalModuleData;
    _totalModuleData = base + data.limit();
    result = new LibraryModule(GLOBAL_INDEX_OFFSET + base,
                               this,
                               data,
                               loadDependsOn,
                               universe);
    return result;
  }


  /**
   * Load library module with given module name
   *
   * @param m the module name, excluding path or ".fum" suffix
   *
   * @return the loaded module or null if it was not found or an error occurred.
   */
  LibraryModule loadModule(String m, AbstractFeature universe)
  {
    var result = _modules.get(m);
    if (result == null)
      {
        var p = modulePath(m);
        if (p != null)
          {
            result = module(m, p, universe);
          }
        else
          {
            Errors.error("Module file '"+(m + ".fum")+"' for module '"+m+"' not found, "+
                         "module directories checked are '" + baseModuleDir() + "' and " +
                         _options._moduleDirs.toString("'","', '", "'") + ".");
          }
      }
    return result;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * create the module intermediate representation
   *
   * @return the MIR
   */
  public MIR createMIR()
  {
    var main = _sourceModule._main;
    var mm = mainModule();
    return mm.createMIR(main);
  }


  /**
   * @return The source module.
   */
  public SourceModule sourceModule()
  {
    if (CHECKS) check
      (_sourceModule != null);
    return _sourceModule;
  }


  /**
   * Return the collection of loaded modules.
   */
  public Collection<LibraryModule> getModules()
  {
    return _modules.values();
  }


  /**
   * Get the compiled module main.
   */
  public LibraryModule mainModule()
  {
    if (_mainModule == null)
      {
        _sourceModule.checkMain();
        Errors.showAndExit();

        var data = _sourceModule.data("main");
        reset();
        _mainModule = libModule(data, af -> loadModules(af), null /* use universe of module */);
        var ignore = new Types.Resolved(_mainModule, _mainModule.libraryUniverse(), false);
      }
    return _mainModule;
  }


  /**
   * @return The base module.
   */
  public LibraryModule baseModule()
  {
    return _modules.get("base");
  }


  /**
   * A module that consists of all modules that
   * this front end depends on without the need for a
   * source module.
   */
  public Module feModule()
  {
    if (Types.resolved == null)
      {
        _feUniverse.setState(State.RESOLVED);
        new Types.Resolved(_modules.get("base"), _feUniverse, true);
      }
    return new Module(_dependsOn) {
      @Override
      public SortedMap<FeatureName, AbstractFeature> declaredFeatures(AbstractFeature outer)
      {
        return Stream
          .of(this._dependsOn)
          .flatMap(m -> m.declaredFeatures(outer).entrySet().stream())
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              Map.Entry::getValue,
              (v1, v2) -> v1,
              TreeMap::new
          ));
      }

      @Override
      String name()
      {
        return "frontend";
      }
    };
  }

}

/* end of file */
