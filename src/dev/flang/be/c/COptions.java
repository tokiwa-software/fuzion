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
   * Name of the C compiler to use.
   */
  final String _cCompiler;


  /**
   * Flags to pass to the C compiler.
   */
  final String _cFlags;


  /**
   * Target to use for compilation
   * <arch><sub>-<vendor>-<sys>-<env>
   *
   * e.g.: x86_64-pc-linux-gnu
   */
  final String _cTarget;


  /**
   * Flag to keep the generated c code after compilation.
   */
  final boolean _keepGeneratedCode;


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor initializing fields as given.
   * @param keepGeneratedCode
   */
  public COptions(FuzionOptions fo, String binaryName, boolean useBoehmGC, String cCompiler, String cFlags, String cTarget, boolean keepGeneratedCode)
  {
    super(fo);

    _binaryName = binaryName;
    _useBoehmGC = useBoehmGC;
    _cCompiler = cCompiler;
    _cFlags = cFlags;
    _cTarget = cTarget;
    _keepGeneratedCode = keepGeneratedCode;
  }


  /*-----------------------------  methods  -----------------------------*/


  /*
   * Get the absolute path of `p` as a String.
   * `p` is relative to fuzionHome.
   */
  public String pathOf(String p)
  {
    return fuzionHome().resolve(p).normalize().toAbsolutePath().toString();
  }

}

/* end of file */
