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
 * Source of class FZJavaOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.fzjava;

import dev.flang.util.ANY;
import dev.flang.util.List;

import java.nio.file.Path;


/**
 * FZJavaOptions contains the options of the fzjava tool.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FZJavaOptions extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /**
   * Some public members in a Java module may return a result type or expect an
   * argument that that is not public.  This flag enables warnings in this case.
   */
  static final boolean SHOW_WARNINGS_FOR_NON_PUBLIC_TYPES = false;


  /*----------------------------  variables  ----------------------------*/


  /**
   * The set of Java modules specified on the command line, e.g., ["java.base",
   * "java.desktop"].
   */
  List<String> _modules = new List<String>();


  /**
   * List of modules to check for existing features given by '-modules'.
   */
  List<String> _loadModules = new List<String>();


  /**
   * List of module directories added using '-moduleDirs'.
   */
  List<String> _moduleDirs = new List<String>();


  /**
   * The set of regex patterns to use to filter java classes, e.g., ["java..*",
   * "javax..*"]
   */
  List<String> _patterns = new List<String>();


  /**
   * The module to create, .e.g. "build/modules/java.base"
   */
  Path _dest;


  /**
   * Should existing files be overwritten?
   */
  boolean _overwrite = false;

}

/* end of file */
