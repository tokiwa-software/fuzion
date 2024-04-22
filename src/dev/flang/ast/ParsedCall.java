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
 * Source of class ParsedCall
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import dev.flang.util.List;


/**
 * Any call that was created by the parser.
 */
public class ParsedCall extends Call
{

  /**
   * Constructor to read a field in target t
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   */
  public ParsedCall(Expr target, ParsedName name)
  {
    super(name._pos, target, name._name);
  }


  /**
   * Constructor to call feature with name 'n' on target 't' with actual
   * arguments 'la'.
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   *
   * @param arguments list of actual arguments
   */
  public ParsedCall(Expr target, ParsedName name, List<Actual> arguments)
  {
    super(name._pos, target, name._name, arguments);
  }


  /**
   * Constructor to call field 'n' on target 't' and select an open generic
   * variant.
   *
   * @param target the target of the call, null if none.
   *
   * @param name the name of the called feature
   *
   * @param select for selecting a open type parameter field, this gives the
   * index '.0', '.1', etc. -1 for none.
   */
  public ParsedCall(Expr target, ParsedName name, int select)
  {
    super(name._pos, target, name._name, select, NO_PARENTHESES);
  }



  public ParsedType asParsedType()
  {
    var target = target();
    var o = target == null ? null : target().asParsedType();
    var ok = target == null || o != null;
    var l = new List<AbstractType>();
    for (var a : _actualsNew)
      {
        var at = a._type;
        ok = ok && at != null;
        l.add(at);
      }
    if (ok)
      {
        return new ParsedType(pos(), name(), l, o);
      }
    else
      {
        return null;
      }
  }


}

/* end of file */
