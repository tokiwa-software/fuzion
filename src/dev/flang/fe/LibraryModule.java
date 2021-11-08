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
 * Source of class LibraryModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import java.nio.file.Path;

import java.util.Set;
import java.util.SortedMap;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureName;

import dev.flang.mir.MIR;

import dev.flang.util.SourceDir;


/**
 * A LibraryModule represents a Fuzion module loaded from a precompiled Fuzion
 * module file .fum.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class LibraryModule extends Module
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FrontEndOptions _options;


  /**
   * NYI: For now, a LibraryModule is just a wrapper around a SourceModule.
   * This will change once the source module can actually be saved to a file.
   */
  public final SourceModule _srcModule;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create SourceModule for given options and sourceDirs.
   */
  LibraryModule(FrontEndOptions options, SourceDir[] sourceDirs, Path inputFile, String defaultMain, Module[] dependsOn, Feature universe)
  {
    super(dependsOn);

    _options = options;
    _srcModule = new SourceModule(options, sourceDirs, inputFile, defaultMain, dependsOn);
    _srcModule.createMIR0(universe);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the module intermediate representation for this module.
   */
  public MIR createMIR()
  {
    return _srcModule.createMIR();
  }


  /**
   * Get declared features for given outer Feature as seen by this module.
   * Result is null if outer has no declared features in this module.
   *
   * @param outer the declaring feature
   */
  SortedMap<FeatureName, AbstractFeature>declaredFeaturesOrNull(AbstractFeature outer)
  {
    return _srcModule.declaredFeaturesOrNull(outer);
  }


  /**
   * Get declared and inherited features for given outer Feature as seen by this
   * module.  Result may be null if this module does not contribute anything to
   * outer.
   *
   * @param outer the declaring feature
   */
  SortedMap<FeatureName, AbstractFeature>declaredOrInheritedFeaturesOrNull(AbstractFeature outer)
  {
    return _srcModule.declaredOrInheritedFeaturesOrNull(outer);
  }


  /**
   * Get direct redefininitions of given Feature as seen by this module.
   * Result is null if f has no redefinitions in this module.
   *
   * @param f the original feature
   */
  Set<AbstractFeature>redefinitionsOrNull(AbstractFeature f)
  {
    return _srcModule.redefinitionsOrNull(f);
  }


  /*-------------------------------  misc  ------------------------------*/


  /**
   * Create String representation for debugging.
   */
  public String toString()
  {
    return "LibraryModule for '" + _srcModule + "'";
  }

}

/* end of file */
