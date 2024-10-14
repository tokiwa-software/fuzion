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
 * Source of interface FUIRI
 *
 *---------------------------------------------------------------------*/


package dev.flang.fuir;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Expr;

import dev.flang.fe.LibraryModule;

import dev.flang.util.List;

/**
 * Temporary interface for Clazz to call dev.flang.fuir.GeneratingFuir methods.
 * Will be removed once air package is joined into dev.flang.fuir package.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
interface FUIRI
{
  Clazz universe();
  Clazz type2clazz(AbstractType thiz);
  Clazz newClazz(AbstractType t);
  Clazz newClazz(Clazz outer, AbstractType actualType);
  boolean lookupDone();
  Clazz error();
  LibraryModule mainModule();
  Clazz clazz(Expr e, Clazz outerClazz, List<AbstractCall> inh);

}
