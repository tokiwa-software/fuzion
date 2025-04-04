/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
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
 * Source of enum TokenModifier
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.enums;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.SemanticTokenModifiers;


public enum TokenModifier
{
  Declaration(0, SemanticTokenModifiers.Declaration),
  Definition(1, SemanticTokenModifiers.Definition),
  Readonly(2, SemanticTokenModifiers.Readonly),
  Static(3, SemanticTokenModifiers.Static),
  Deprecated(4, SemanticTokenModifiers.Deprecated),
  Abstract(5, SemanticTokenModifiers.Abstract),
  Async(6, SemanticTokenModifiers.Async),
  Modification(7, SemanticTokenModifiers.Modification),
  Documentation(8, SemanticTokenModifiers.Documentation),
  DefaultLibrary(9, SemanticTokenModifiers.DefaultLibrary);

  public final String str;
  public final Integer num;

  TokenModifier(Integer num, String str)
  {
    this.num = num;
    this.str = str;
  }

  public static Integer DataOf(Set<TokenModifier> modifiers)
  {
    var result = new BitSet();
    for(TokenModifier modifier : modifiers)
      {
        result.set(modifier.num);
      }
    if (result.isEmpty())
      {
        return 0;
      }
    return (int) result.toLongArray()[0];
  }

  public static final List<String> asList =
    Arrays
      .stream(TokenModifier.values())
      .map(x -> x.str)
      .collect(Collectors.toList());

}
