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

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AstErrors;

import dev.flang.mir.MIR;

import static dev.flang.util.Errors.*;
import dev.flang.util.SourcePosition;
import dev.flang.util.StringHelpers;


/**
 * FeErrors handles front end compilation error messages for Fuzion
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeErrors extends AstErrors
{

  /*-----------------------------  methods  -----------------------------*/


  public static void mainFeatureMustNotHaveArguments(AbstractFeature m)
  {
    var a = m.valueArguments();
    error(m.pos(),
          "Main feature must not have arguments",
          "Main feature has " + StringHelpers.argumentsString(a.size()) + " (" + sfn(a) + "), but should have no arguments to be used as main feature in an application\n" +
          "To solve this, remove the arguments from feature " + s(m) + "\n");
  }

  public static void mainFeatureMustNotHaveTypeArguments(AbstractFeature m)
  {
    var g = m.generics().list;
    error(m.pos(),
          "Main feature must not have type arguments",
          "Main feature has " + StringHelpers.singularOrPlural(g.size(),"type argument") + " " + g + ", but should have no arguments to be used as main feature in an application\n" +
          "To solve this, remove the arguments from feature " + s(m) + "\n");
  }

  static void mainFeatureMustNot(AbstractFeature m, String what)
  {
    error(m.pos(),
          "Main feature must not " +  what,
          "Main feature must be a non-abstract non-intrinsic routine\n" +
          "To solve this, use a non-abstract, non-intrinsic, non-generic routine as the main feature of your application.\n");
  }

  public static void mainFeatureMustNotBeField(AbstractFeature m)
  {
    mainFeatureMustNot(m, "be a field");
  }

  public static void mainFeatureMustNotBeAbstract(AbstractFeature m)
  {
    mainFeatureMustNot(m, "be abstract");
  }

  public static void mainFeatureMustNotBeIntrinsic(AbstractFeature m)
  {
    mainFeatureMustNot(m, "be intrinsic");
  }

  public static void mainFeatureMustNotBeChoice(AbstractFeature m)
  {
    mainFeatureMustNot(m, "be choice");
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


  static String hashString(byte[] v)
  {
    return
      v == null     ? "-- null --" :
      v.length == 0 ? "-- empty hash --"
                    : IntStream.range(0, v.length)
                        .map(j -> v[j] & 0xff)
                        .mapToObj(x -> Integer.toHexString(0x100 + x).substring(1))
                        .collect(Collectors.joining());
  }

  static void incompatibleModuleHash(LibraryModule user,
                                        LibraryModule m,
                                        byte[] expected_version,
                                        byte[] found_version)
  {
    fatal("Incompatible module hashes encountered",
          "Module " + user + " references module " + m + "\n" +
          "Expected hash: " + hashString(expected_version) + "\n" +
          "Actual hash  : " + hashString(found_version));
  }


}

/* end of file */
