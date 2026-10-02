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

import java.util.Set;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Contract;
import dev.flang.ast.Expr;
import dev.flang.ast.Visi;

import dev.flang.util.List;
import dev.flang.util.SourcePosition;


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

  @Override public Set<AbstractFeature>  redefines()               { return AbstractFeature.EMPTY_SET; }
  @Override public Visi                  visibility()              { return Visi.PRIV; };
  @Override public int                   modifiers()               { return 0;  };
  @Override public List<AbstractCall>    inherits()                { return AbstractCall.NO_CALLS; }
  @Override public List<AbstractFeature> arguments()               { return AbstractFeature._NO_FEATURES_; };
  @Override public SourcePosition        resultTypePos()           { return pos(); }
  @Override public FuirFeature           resultField()             { return null; }
  @Override public FuirFeature           outerRef()                { return null; };
  @Override public Expr                  code()                    { require(isRoutine()); return null;  };
  @Override public Contract              contract()                { return Contract.EMPTY_CONTRACT; }
  @Override public AbstractFeature       preFeature()              { return null; }
  @Override public AbstractFeature       preBoolFeature()          { return null; }
  @Override public AbstractFeature       preAndCallFeature()       { return null; }
  @Override public AbstractFeature       postFeature()             { return null; }
  @Override public AbstractFeature       openTypesFeature()        { require(isOpenTypeParameter()); return null; }
  @Override public AbstractFeature       valuesAsOpenTypeFeature() { require(isOpenTypeParameter()); return null; }
  @Override public AbstractType          asParametricType()        { require(isTypeParameter()); return null; }

}


/* end of file */
