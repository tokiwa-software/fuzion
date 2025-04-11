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
 * Source of class Diagnostics
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.Config;
import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.QueryAST;
import dev.flang.shared.Util;

/**
 * provide diagnostics for a given uri
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_publishDiagnostics
 */
public enum Diagnostics
{
  nameingRefs, nameingFeatures, nameingTypeParams, duplicateName, errors, warnings, unusedFeatures;

  public static void publishDiagnostics(URI uri)
  {
    var diagnostics =
      new PublishDiagnosticsParams(uri.toString(), getDiagnostics(uri).collect(Collectors.toList()));
    Config.languageClient().publishDiagnostics(diagnostics);
  }

  public static Stream<Diagnostic> getDiagnostics(URI uri)
  {
    // NYI check names of type arguments
    return Util.ConcatStreams(
      errors(uri),
      warnings(uri),
      namingFeatures(uri),
      namingRefs(uri),
      namingTypeParams(uri));
  }

  private static Stream<Diagnostic> errors(URI uri)
  {
    var errorDiagnostics =
      ParserTool.Errors(uri)
        .filter(error -> ParserTool.getUri(error.pos).equals(uri))
        .map((error) -> {
            var message = error.msg + System.lineSeparator() + error.detail;
            return create(Bridge.toRange(error.pos), message,
              DiagnosticSeverity.Error,
              errors);
          });
    return errorDiagnostics;
  }

  private static Stream<Diagnostic> warnings(URI uri)
  {
    var warningDiagnostics =
      ParserTool.Warnings(uri)
        .filter(warning -> ParserTool.getUri(warning.pos).equals(uri))
        .map((warning) -> {
            var message = warning.msg + System.lineSeparator() + warning.detail;
            return create(Bridge.toRange(warning.pos), message,
              DiagnosticSeverity.Warning, warnings);
          });
    return warningDiagnostics;
  }

  private static Stream<Diagnostic> namingRefs(URI uri)
  {
    return QueryAST.SelfAndDescendants(uri)
      .filter(f -> !f.isTypeParameter())
      .filter(f -> (f.isOuterRef() || f.isRef()) && !f.isField())
      .filter(f -> {
        var basename = f.featureName().baseName();
        var splittedBaseName = basename.split("_");
        return
        // any lowercase after _
        Arrays.stream(splittedBaseName).anyMatch(str -> Character.isLowerCase(str.codePointAt(0)))
          // any uppercase after first char
          || Arrays.stream(splittedBaseName)
            .anyMatch(str -> !str.isEmpty()
              && str.substring(1).codePoints().anyMatch(c -> Character.isUpperCase(c)));
      })
      .map(f -> {
        return create(Bridge.toRangeBaseName(f),
          "use Snake_Pascal_Case for refs, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, nameingRefs);
      });
  }

  private static Stream<Diagnostic> namingFeatures(URI uri)
  {
    var snakeCase = QueryAST.SelfAndDescendants(uri)
      .filter(f -> !f.isTypeParameter())
      .filter(f -> !(f.isOuterRef() || f.isRef()) || f.isField())
      .filter(f -> {
        var basename = f.featureName().baseName();
        return
        // any uppercase
        basename.codePoints().anyMatch(c -> Character.isUpperCase(c));
      })
      .map(f -> {
        return create(Bridge.toRangeBaseName(f),
          "use snake_case for features and value types, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, nameingFeatures);
      });
    return snakeCase;
  }

  private static Stream<Diagnostic> namingTypeParams(URI uri)
  {
    var uppercase = QueryAST.SelfAndDescendants(uri)
      .filter(f -> f.isTypeParameter())
      .filter(f -> {
        var basename = f.featureName().baseName();
        return basename.codePoints().anyMatch(c -> Character.isLowerCase(c));
      })
      .map(f -> {
        return create(Bridge.toRangeBaseName(f),
          "use UPPERCASE for type parameters, check: https://flang.dev/design/identifiers",
          DiagnosticSeverity.Information, Diagnostics.nameingTypeParams);
      });
    return uppercase;
  }


  private static Diagnostic create(Range range, String msg, DiagnosticSeverity diagnosticSeverity, Diagnostics d)
  {
    var diagnostic = new Diagnostic(range, msg,
      diagnosticSeverity, "fuzion language server");
    diagnostic.setCode(d.ordinal());
    return diagnostic;
  }

}
