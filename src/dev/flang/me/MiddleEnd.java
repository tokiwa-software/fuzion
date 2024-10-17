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
 * Source of class MiddleEnd
 *
 *---------------------------------------------------------------------*/

package dev.flang.me;

import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.flang.ast.AbstractCall; // NYI: remove dependency!
import dev.flang.ast.Constant; // NYI: remove dependency!
import dev.flang.ast.AbstractFeature; // NYI: remove dependency!
import dev.flang.ast.AbstractType; // NYI: remove dependency!
import dev.flang.ast.Expr; // NYI: remove dependency!
import dev.flang.ast.FeatureName; // NYI: remove dependency!
import dev.flang.ast.FeatureVisitor; // NYI: remove dependency!
import dev.flang.ast.InlineArray; // NYI: remove dependency!
import dev.flang.ast.State; // NYI: remove dependency!
import dev.flang.ast.Tag; // NYI: remove dependency!
import dev.flang.ast.Types; // NYI: remove dependency!

import dev.flang.fe.FeatureLookup;

import dev.flang.mir.MIR;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.FuzionOptions;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;


/**
 * The MiddleEnd creates application IR (air) from the the module IRs (mir)
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class MiddleEnd extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Configuration
   */
  public final FuzionOptions _options;


  public final MIR _mir;


  /*--------------------------  constructors  ---------------------------*/


  public MiddleEnd(FuzionOptions options, MIR mir)
  {
    _options = options;
    _mir = mir;
  }


  /*-----------------------------  methods  -----------------------------*/




}

/* end of file */
