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

import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.EnumSet;

import dev.flang.mir.MIR;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AstErrors;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;
import dev.flang.ast.Types;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
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
    var universe = new Universe();
    _universe = universe;

    var sourcePaths = sourcePaths();
    var sourceDirs = new SourceDir[sourcePaths.length + options._modules.size()];
    for (int i = 0; i < sourcePaths.length; i++)
      {
        sourceDirs[i] = new SourceDir(sourcePaths[i]);
      }
    for (int i = 0; i < options._modules.size(); i++)
      {
        sourceDirs[sourcePaths.length + i] = new SourceDir(options._fuzionHome.resolve(Path.of("modules")).resolve(Path.of(options._modules.get(i))));
      }
    LibraryModule[] dependsOn;
    var save = options._saveBaseLib;
    _baseModule = save == null ? baseModule() : null;
    dependsOn = _baseModule == null ? new LibraryModule[] { } : new LibraryModule[] { _baseModule };
    if (options._loadSources && Errors.count() == 0)
      {
        _module = new SourceModule(options, sourceDirs, inputFile(options), options._main, dependsOn, universe);
        _module.createASTandResolve();
      }
    else
      {
        _module = null;
      }
    if (save != null && Errors.count() == 0)
      {
        saveModule(save);
      }
  }


  /**
   * Get all the paths to use to read source code from
   */
  private Path[] sourcePaths()
  {
    return
      (_options._saveBaseLib != null  ) ? new Path[] { _options._fuzionHome.resolve("lib") } :
      (_options._readStdin         ||
       _options._inputFile != null    ) ? new Path[] { }
                                        : new Path[] { Path.of(".") };
  }


  /**
   * Load Base module for given options.
   */
  private LibraryModule baseModule()
  {
    var b = _options._fuzionHome.resolve("modules").resolve("base.fum");
    try (var ch = (FileChannel) Files.newByteChannel(b, EnumSet.of(StandardOpenOption.READ)))
      {
        var data = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
        return new LibraryModule("base", data, new LibraryModule[0], _universe);
      }
    catch (IOException io)
      {
        Errors.error("FrontEnd I/O error when reading module file",
                     "While trying to read file '"+ b + "' received '" + io + "'");
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


  /**
   * Save _module to a module file
   */
  private void saveModule(Path p)
  {
    var data = _module.data();
    System.out.println(" + " + p);
    try (var os = Files.newOutputStream(p))
      {
        Channels.newChannel(os).write(data);
      }
    catch (IOException io)
      {
        Errors.error("FrontEnd I/O error when writing module file",
                     "While trying to write file '"+ p + "' received '" + io + "'");
      }
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
