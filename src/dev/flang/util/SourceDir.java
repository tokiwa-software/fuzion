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
 * Source of class SourceDir
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.TreeMap;


/**
 * SourceDir represents a directory containing fuzion source code and sub-
 * directories that potentially contain source code for inner features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class SourceDir extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The directory
   */
  public final Path _dir;


  /**
   * The inner directories cache or null if these were not checked yet.
   */
  private TreeMap<String, SourceDir> _subDirs = null;


  /*--------------------------  constructors  ---------------------------*/


  /*
   * Create an entry for the given (sub-) directory
   */
  public SourceDir(Path d)
  {
    _dir = d;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * If this contains a sub-directory with given name, return it.
   *
   * @param name name of a potential sub-directory.
   *
   * @return a SourceDir instance for the sub-directory name or null if no such
   * sub-directory exists.
   */
  public SourceDir dir(String name) throws IOException, UncheckedIOException
  {
    if (_subDirs == null)
      { // on first call, create dir listing and cache it:
        _subDirs = new TreeMap<>();
        Files.list(_dir)
          .forEach(p ->
                   { if (Files.isDirectory(p))
                       {
                         _subDirs.put(p.getFileName().toString(), new SourceDir(p));
                       }
                   });
      }
    return _subDirs.get(name);
  }


  /**
   * Create String representation, for debugging only.
   */
  public String toString()
  {
    return _dir.toString();
  }

}

/* end of file */
