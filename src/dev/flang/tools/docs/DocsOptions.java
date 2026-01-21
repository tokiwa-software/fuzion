/*

This file is part of the Fuzion language implementation.

The Fuzion docs generator implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion docs generator implementation is distributed in the hope that it will be
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
 * Source of class DocsOptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.docs;

import java.nio.file.Path;

/**
 * api doc specific options
 *
 * @param destination
 * @param apiSrcDir the location to which the src links in the docs should point to, null for default
 * @param bare
 * @param printCSSStyles
 * @param ignoreVisibility
 */
public record DocsOptions(Path destination, String docsRoot, String apiSrcDir, boolean bare, boolean printCSSStyles, boolean ignoreVisibility)
{

  public String apiSrcDir()
  {
    return apiSrcDir == null ? "/api" : apiSrcDir;
  }


  public boolean ignoreVisibility()
  {
    return ignoreVisibility;
  }

  public String docsRoot()
  {
    return bare ? (docsRoot == null ? "/docs" : docsRoot): "";
  }
}
