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
 * Source of class FeErrors
 *
 *---------------------------------------------------------------------*/

package dev.flang.fe;

import dev.flang.ast.AstErrors;
import dev.flang.ast.Feature;

import dev.flang.mir.MIR;

import static dev.flang.util.Errors.*;
import dev.flang.util.SourcePosition;


/**
 * FeErrors handles front end compilation error messages for Fuzion
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeErrors extends AstErrors
{

  /*-----------------------------  methods  -----------------------------*/


  public static void mainFeatureMustNotHaveArguments(Feature m)
  {
    error(m.pos,
          "Main feature must not have arguments",
          "Main feature has " + argumentsString(m.arguments.size()) + m.arguments.size()+", but should have no arguments to be used as main feature in an application\n" +
          "To solve this, remove the arguments from feature " + s(m) + "\n");
  }

  public static void mainFeatureMustNotHaveTypeArguments(Feature m)
  {
    var g = m.generics.list;
    error(m.pos,
          "Main feature must not have type arguments",
          "Main feature has " + singularOrPlural(g.size(),"type argument") + " " + g + ", but should have no arguments to be used as main feature in an application\n" +
          "To solve this, remove the arguments from feature " + s(m) + "\n");
  }

  static void mainFeatureMustNot(Feature m, String what)
  {
    error(m.pos,
          "Main feature must not " +  what,
          "Main feature must be a non-abstract non-intrinsic routine\n" +
          "To solve this, use a non-abstract, non-intrinsic, non-generic routine as the main feature of your application.\n");
  }

  public static void mainFeatureMustNotBeField(Feature m)
  {
    mainFeatureMustNot(m, "be a field");
  }

  public static void mainFeatureMustNotBeAbstract(Feature m)
  {
    mainFeatureMustNot(m, "be abstract");
  }

  public static void mainFeatureMustNotBeIntrinsic(Feature m)
  {
    mainFeatureMustNot(m, "be intrinsic");
  }

  static void mainFeatureMustNotHaveGenericArguments(Feature m)
  {
    mainFeatureMustNot(m, "have generic arguments");
  }

  static void fieldNotInitialized(MIR mir, SourcePosition pos, int af)
  {
    var afn = sqn(mir.featureAsString(af));
    error(pos, "Field may not have been initialized",
          "Some execution paths to the use of field " + afn + " must "+
          "assign a value to this field. \n"+
          "Uninitialized field: " + afn + "\n"+
          "To solve this, make sure a value is assigned to the field before it is used and "+
          "there are no calls to features that end up reading this field before it is "+
          "initialized.\n");
  }

}

/* end of file */
