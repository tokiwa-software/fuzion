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
 * Source of interface MirModule
 *
 *---------------------------------------------------------------------*/

package dev.flang.mir;

import dev.flang.ast.AbstractModule; // NYI: CLEANUP: Remove dependency!
import dev.flang.fe.LibraryFeature;


/**
 * MirModule provides callbacks from the MIR to data structures in the current
 * module, in particular to sets of features.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public interface MirModule extends AbstractModule
{

  public LibraryFeature f_Typed_Combiner_call();
  public LibraryFeature f_typed_applicator_apply();
  public LibraryFeature f_Open_Types_type_foldf();
  public LibraryFeature f_choice_apply();
  public LibraryFeature f_Typed_Function_call();
  public LibraryFeature f_choice_apply_f();
  public LibraryFeature f_choice_combine();
  public LibraryFeature f_choice_combine_other();
  public LibraryFeature f_choice_combine_e();
  public LibraryFeature f_effect_types_type_foldf();
  public LibraryFeature f_Function_call();
  public LibraryFeature f_flow_fallible_cause();
  public LibraryFeature f_mutate_var_mutable_value();
  public LibraryFeature f_Type_infix_colon();
  public LibraryFeature f_Type_infix_colon_true();
  public LibraryFeature f_Type_infix_colon_false();
  public LibraryFeature f_effect_static_finally();
  public LibraryFeature f_fuzion_Java_Object_Ref();
  public LibraryFeature f_type_applicator_apply();
  public LibraryFeature f_choice_combine_f();

}

/* end of file */
