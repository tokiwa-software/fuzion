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
 * Source of class COptions
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionOptions;


/**
 * COptions specify the configuration of the C back end
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class COptions extends FuzionOptions
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * The desired name of the binary to create, null if main feature name is to
   * be used.
   */
  final String _binaryName;


  /**
   * Should the boehm garbage collector be used?
   */
  final boolean _useBoehmGC;


  /**
   * Should we use the DFA analysis to improve the generated code?
   */
  final boolean _Xdfa;


  /**
   * Name of the C compiler to use.
   */
  final String _cCompiler;


  /**
   * Flags to pass to the C compiler.
   */
  final String _cFlags;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Costructor initializing fields as given.
   */
  public COptions(FuzionOptions fo, String binaryName, boolean useBoehmGC, boolean Xdfa, String cCompiler, String cFlags)
  {
    super(fo);

    _binaryName = binaryName;
    _useBoehmGC = useBoehmGC;
    _Xdfa = Xdfa;
    _cCompiler = cCompiler;
    _cFlags = cFlags;
  }


  /*-----------------------------  methods  -----------------------------*/

}

/* end of file */
