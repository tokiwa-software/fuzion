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
 * Source of class ThisType
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.TypeKind;
import dev.flang.util.List;

class ThisType extends LibraryType {

  private AbstractFeature _feature;
  private AbstractType _asRef;

  public ThisType(LibraryModule mod, int at, AbstractFeature feature)
  {
    super(mod, at);
    _feature = feature;
  }

  @Override
  protected AbstractFeature backingFeature()
  {
    return _feature;
  }

  @Override
  public List<AbstractType> generics()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'generics'");
  }

  @Override
  public AbstractType outer()
  {
    // NYI: UNDER DEVELOPMENT: better null or not legal to call?
    return _libModule.libraryUniverse().selfType();
  }

  @Override
  public TypeKind kind()
  {
    return TypeKind.ThisType;
  }

  @Override
  public AbstractType asRef()
  {
    // NYI: CLEANUP: isAssignableFrom should create the ref-type itself
    if (PRECONDITIONS) require
      (Thread.currentThread().getStackTrace()[2].getMethodName().equals("isAssignableFrom"));

    if (_asRef == null)
      {
        _asRef = new NormalType(_libModule, _at, _feature, TypeKind.RefType, _feature.generics().asActuals(), _feature.outer().selfType().asThis());
      }
    return _asRef;
  }

  @Override
  public AbstractType asValue()
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'asValue'");
  }

  @Override
  public AbstractType asThis()
  {
    return this;
  }

}
