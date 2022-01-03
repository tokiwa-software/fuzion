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
import dev.flang.ast.Resolution;
import dev.flang.ast.Types;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.SourceDir;
import dev.flang.util.SourceFile;


/**
 * The FrontEnd creates the module IR (mir) from the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FrontEnd extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The module we are compiling.
   */
  private final Module _module;

  private LibraryModule _stdlib = null;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create front end for given options.
   */
  public FrontEnd(FrontEndOptions options)
  {
    var universe = Feature.createUniverse();
    Types.reset();
    if (options._saveBaseLib != null)
      {
        LibraryModule.USE_FUM = true;
      }
    if (LibraryModule.USE_FUM)
      {
        universe = new Feature.Universe()
          {
            public AbstractFeature get(String name)
            {
              return get(name, -1);
            }
            public AbstractFeature get(String name, int argcount)
            {
              if (_stdlib == null)
                {
                  return super.get(name, argcount);
                }
              // NYI: Code dupliction with LibraryFeature.get and ast.Feature.get()
              AbstractFeature result = Types.f_ERROR;
              var d = _stdlib.featuresMap();
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
                  return super.get(name, argcount);
                }
              else
                {
                  AstErrors.internallyReferencedFeatureNotUnique(LibraryModule.DUMMY_POS, name + (argcount >= 0 ? " (" + Errors.argumentsString(argcount) : ""), set);
                }
              return result;
            }
          };

        var p = options._saveBaseLib;
        if (p != null)
          {
            var sourceDirs = new SourceDir[] { new SourceDir(options._fuzionHome.resolve("lib")) };
            var srcModule = new SourceModule(options, sourceDirs, null, null, new Module[0], universe);
            var mir = srcModule.createMIR();
            var data = mir._module.data();
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
            _module = null;
            return;
          }

        // yippieh: At this point, we forget srcModule and continue with data only:
        var b = options._fuzionHome.resolve("modules").resolve("base.fum");
        try (var ch = (FileChannel) Files.newByteChannel(b, EnumSet.of(StandardOpenOption.READ)))
          {
            var data = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
            _stdlib = new LibraryModule("base", data, new Module[0], universe);
          }
        catch (IOException io)
          {
            Errors.error("FrontEnd I/O error when reading module file",
                         "While trying to read file '"+ b + "' received '" + io + "'");
          }
        universe.setState(Feature.State.RESOLVED);
      }
    else
      {
        _stdlib = new LibraryModule(options, "stdlib", new SourceDir[] { new SourceDir(options._fuzionHome.resolve("lib")) }, null, null, new Module[0], universe);
        _stdlib._srcModule.data(universe)._declaredOrInheritedFeatures = null;
      }

    Path[] sourcePaths;
    Path inputFile;
    if (options._readStdin)
      {
        sourcePaths = new Path[] { };
        inputFile = SourceFile.STDIN;
      }
    else if (options._inputFile != null)
      {
        sourcePaths = new Path[] { };
        inputFile = options._inputFile;
      }
    else
      {
        sourcePaths = new Path[] { Path.of(".") };
        inputFile = null;
      }
    var sourceDirs = new SourceDir[sourcePaths.length + options._modules.size()];
    for (int i = 0; i < sourcePaths.length; i++)
      {
        sourceDirs[i] = new SourceDir(sourcePaths[i]);
      }
    for (int i = 0; i < options._modules.size(); i++)
      {
        sourceDirs[sourcePaths.length + i] = new SourceDir(options._fuzionHome.resolve(Path.of("modules")).resolve(Path.of(options._modules.get(i))));
      }
    _module = new SourceModule(options, sourceDirs, inputFile, options._main, new Module[] {_stdlib}, universe);
    if (LibraryModule.USE_FUM)
      {
        ((Feature.Universe)universe).setModule((dev.flang.ast.SrcModule) _module);
      }
  }


  /*-----------------------------  methods  -----------------------------*/


  public MIR createMIR()
  {
    return _module.createMIR();
  }


  public Resolution res()
  {
    return ((SourceModule) _module)._res; // NYI: Cast!
  }


}

/* end of file */
