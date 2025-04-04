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
 * Source of class CodeActions
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.shared.CaseConverter;
import dev.flang.shared.QueryAST;
import dev.flang.shared.Util;

public class CodeActions
{

  public static List<Either<Command, CodeAction>> getCodeActions(CodeActionParams params)
  {
    return Util.ConcatStreams(
      NameingFixes(params, Diagnostics.nameingFeatures, oldName -> CaseConverter.ToSnakeCase(oldName)),
      NameingFixes(params, Diagnostics.nameingRefs, oldName -> CaseConverter.ToSnakePascalCase(oldName)),
      NameingFixes(params, Diagnostics.nameingTypeParams, oldName -> oldName.toUpperCase()),
      GenerateMatchCases(params))
      .collect(Collectors.toList());
  }

  private static Stream<Either<Command, CodeAction>> GenerateMatchCases(CodeActionParams params)
  {
    var uri = LSP4jUtils.getUri(params.getTextDocument());
    return params
      .getContext()
      .getDiagnostics()
      .stream()
      // NYI replace string comparison by sth. more adequate
      .filter(x -> x.getMessage().startsWith("'match' statement does not cover all of the subject's types") ||
        x.getMessage().startsWith("'match' expression requires at least one case"))
      .map(x -> {
        var res = new CodeAction();
        res.setTitle(Commands.codeActionGenerateMatchCases.toString());
        res.setKind(CodeActionKind.QuickFix);
        res.setDiagnostics(List.of(x));
        res.setCommand(
          Commands.Create(Commands.codeActionGenerateMatchCases, uri,
            List.of(x.getRange().getStart().getLine(), x.getRange().getStart().getCharacter())));
        return Either.forRight(res);
      });
  }

  private static Stream<Diagnostic> getDiagnostics(CodeActionParams params, Diagnostics diag)
  {
    return params
      .getContext()
      .getDiagnostics()
      .stream()
      .filter(x -> x.getCode().isRight() && x.getCode().getRight().equals(diag.ordinal()));
  }

  private static Stream<Either<Command, CodeAction>> NameingFixes(CodeActionParams params, Diagnostics diag,
    Function<String, String> fix)
  {
    return getDiagnostics(params, diag)
      .flatMap(d -> CodeActionForNameingIssue(params.getTextDocument(), d, fix).stream())
      .<Either<Command, CodeAction>>map(
        ca -> Either.forRight(ca));
  }

  /**
   * if renameing of identifier is possible
   * return code action for fixing identifier name
   *
   * @param tdi
   * @param d
   * @param convertIdentifier
   * @return
   */
  private static Optional<CodeAction> CodeActionForNameingIssue(TextDocumentIdentifier tdi, Diagnostic d,
    Function<String, String> convertIdentifier)
  {
    var uri = LSP4jUtils.getUri(tdi);
    return QueryAST
      .FeatureAt(Bridge.ToSourcePosition(new TextDocumentPositionParams(tdi, d.getRange().getStart())))
      .map(f -> {
        var oldName = f.featureName()
          .baseName();

        if (oldName.length() > 1 && oldName.equals(oldName.toUpperCase()))
          {
            return null;
          }

        var res = new CodeAction();
        res.setTitle(Commands.codeActionFixIdentifier.toString());
        res.setKind(CodeActionKind.QuickFix);
        res.setDiagnostics(List.of(d));
        res.setCommand(Commands.Create(Commands.codeActionFixIdentifier, uri,
          List.of(d.getRange().getStart().getLine(), d.getRange().getStart().getCharacter(),
            convertIdentifier.apply(oldName))));
        return res;
      });
  }

}
