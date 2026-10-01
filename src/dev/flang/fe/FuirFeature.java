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
 * Source of class FuirFeature
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import dev.flang.ast.AbstractFeature;


/**
 * A FuirFeature represents a Fuzion feature that is present when the FUIR is
 * generated.  It can be either a feature loaded from a precompiled Fuzion
 * module file .fum or a feature created by `GeneratingFUIR` for code generated
 * during monomorphization.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class FuirFeature extends AbstractFeature
{


}
