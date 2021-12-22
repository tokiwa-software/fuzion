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

import java.nio.file.Path;

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
      }

    Types.reset();
    _stdlib = new LibraryModule(options, "stdlib", new SourceDir[] { new SourceDir(options._fuzionHome.resolve("lib")) }, null, null, new Module[0], universe);
    _stdlib._srcModule.data(universe)._declaredOrInheritedFeatures = null;

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
