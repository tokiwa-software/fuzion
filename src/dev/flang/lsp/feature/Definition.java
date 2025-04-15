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
 * Source of class Definition
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.util.Bridge;
import dev.flang.shared.QueryAST;

/**
 * tries to provide the definition of a call
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_definition
 */
public class Definition
{
  public static Either<List<? extends Location>, List<? extends LocationLink>> getDefinitionLocation(
    DefinitionParams params)
  {

    var feature = QueryAST.featureAt(Bridge.toSourcePosition(params));
    if (feature.isEmpty())
      {
        return null;
      }
    // NYI should also include where feature is beeing redefined
    var redefAbstractAndSelf = new TreeSet<>(feature.get().redefines());
    redefAbstractAndSelf.add(feature.get());
    return getDefinition(redefAbstractAndSelf.stream().collect(Collectors.toList()));
  }

  private static Either<List<? extends Location>, List<? extends LocationLink>> getDefinition(List<AbstractFeature> fl)
  {
    return Either
      .forLeft(
        fl
          .stream()
          .map(f -> Bridge.toLocation(f))
          .collect(Collectors.toList()));
  }

}
