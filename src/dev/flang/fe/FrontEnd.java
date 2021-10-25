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

import dev.flang.util.ANY;
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


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create front end for given options.
   */
  public FrontEnd(FrontEndOptions options)
  {
    var stdlib = new SourceModule(options, new SourceDir[] { new SourceDir(options._fuzionHome.resolve("lib")) }, null, null, new Module[0]);
    stdlib.createMIR0();
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
    var m = new SourceModule(options, sourceDirs, inputFile, options._main, new Module[] {stdlib});
    m.createMIR0();
    _module = m;
  }


  /*-----------------------------  methods  -----------------------------*/


  public MIR createMIR()
  {
    return _module.createMIR();
  }

}

/* end of file */
