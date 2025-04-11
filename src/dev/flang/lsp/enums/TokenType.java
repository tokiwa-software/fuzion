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
 * Source of enum TokenType
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.SemanticTokenTypes;

public enum TokenType
{
  Namespace(0, SemanticTokenTypes.Namespace),
  Type(1, SemanticTokenTypes.Type),
  Class(2, SemanticTokenTypes.Class),
  Enum(3, SemanticTokenTypes.Enum),
  Interface(4, SemanticTokenTypes.Interface),
  Struct(5, SemanticTokenTypes.Struct),
  TypeParameter(6, SemanticTokenTypes.TypeParameter),
  Parameter(7, SemanticTokenTypes.Parameter),
  Variable(8, SemanticTokenTypes.Variable),
  Property(9, SemanticTokenTypes.Property),
  EnumMember(10, SemanticTokenTypes.EnumMember),
  Event(11, SemanticTokenTypes.Event),
  Function(12, SemanticTokenTypes.Function),
  Method(13, SemanticTokenTypes.Method),
  Macro(14, SemanticTokenTypes.Macro),
  Keyword(15, SemanticTokenTypes.Keyword),
  Modifier(16, SemanticTokenTypes.Modifier),
  Comment(17, SemanticTokenTypes.Comment),
  String(18, SemanticTokenTypes.String),
  Number(19, SemanticTokenTypes.Number),
  Regexp(20, SemanticTokenTypes.Regexp),
  Operator(21, SemanticTokenTypes.Operator);

  public final String str;
  public final Integer num;

  TokenType(Integer num, String str)
  {
    this.num = num;
    this.str = str;
  }

  public static final List<String> asList =
    Arrays.stream(TokenType.values()).map(x -> x.str).collect(Collectors.toList());
}
