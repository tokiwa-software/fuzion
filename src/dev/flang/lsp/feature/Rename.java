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
 * Source of class Rename
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import dev.flang.ast.AbstractFeature;
import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.LSP4jUtils;
import dev.flang.parser.Lexer.Token;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.LexerTool;
import dev.flang.shared.ParserTool;
import dev.flang.shared.QueryAST;
import dev.flang.shared.SourcePositionTool;
import dev.flang.shared.TypeTool;
import dev.flang.shared.Util;
import dev.flang.util.ANY;
import dev.flang.util.SourcePosition;

/**
 * for rename request return all appropriate TextEdits
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_rename
 * https://microsoft.github.io/language-server-protocol/specification#textDocument_prepareRename
 */
public class Rename extends ANY
{

  public static WorkspaceEdit getWorkspaceEdit(RenameParams params) throws ResponseErrorException
  {
    var res = getWorkspaceEditsOrError(params, params.getNewName());
    if (res.isLeft())
      {
        return res.getLeft();
      }
    throw res.getRight();
  }


  // NYI check for name collisions?
  public static Either<WorkspaceEdit, ResponseErrorException> getWorkspaceEditsOrError(
    TextDocumentPositionParams params, String newName)
  {
    if (!LexerTool.IsValidIdentifier(newName))
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidParams, "new name no valid identifier.", null);
        return Either.forRight(new ResponseErrorException(responseError));
      }

    var pos = Bridge.toSourcePosition(params);

    var feature = QueryAST.FeatureAt(pos);
    if (feature.isEmpty())
      {
        var responseError = new ResponseError(ResponseErrorCode.InvalidRequest, "nothing found for renaming.", null);
        return Either.forRight(new ResponseErrorException(responseError));
      }

    Stream<SourcePosition> renamePositions = getRenamePositions(params, feature.get());

    var changes = renamePositions
      .map(start -> {
        var end =
          SourcePositionTool.ByLineColumn(start._sourceFile, start.line(), start.column() + lengthOfFeatureIdentifier(feature.get()));
        return Bridge.toLocation(start, end);
      })
      .map(location -> new SimpleEntry<String, TextEdit>(location.getUri(),
        new TextEdit(location.getRange(), newName)))
      .collect(Collectors.groupingBy(e -> e.getKey(), Collectors.mapping(e -> e.getValue(), Collectors.toList())));

    return Either.forLeft(new WorkspaceEdit(changes));
  }


  /**
   *
   * @param params
   * @param featureToRename
   * @param featureIdentifier
   * @return stream of sourcepositions where renamings must be done
   */
  private static Stream<SourcePosition> getRenamePositions(TextDocumentPositionParams params,
    AbstractFeature featureToRename)
  {
    var callsSourcePositions = FeatureTool
      .CallsTo(featureToRename)
      .map(entry -> entry.getKey().pos());
    var pos = FeatureTool.BareNamePosition(featureToRename);

    // positions where feature is used as type
    var typePositions = FeatureTool.SelfAndDescendants(ParserTool.Universe(Util.toURI(params.getTextDocument().getUri())))
      .filter(f -> !f.equals(featureToRename) && !f.resultType().isGenericArgument()
        && f.resultType().feature().equals(featureToRename))
      .flatMap(f -> {
        var tokens = LexerTool.TokensFrom(f.pos()).skip(1).collect(Collectors.toList());
        var whitespace = tokens.get(0);

        if (CHECKS)
          check(whitespace.token() == Token.t_ws);

        if (!tokens.get(1).text().equals(FeatureTool.BareName(featureToRename)))
          {
            return Stream.empty();
          }

        return Stream.of(SourcePositionTool.ByLineColumn(f.pos()._sourceFile, f.pos().line(),
          f.pos().column() + Util.CharCount(f.featureName().baseName()) + Util.CodepointCount(whitespace.text())));
      });


    var assignmentPositions = ASTWalker
      .Assignments(featureToRename.outer(), featureToRename)
      .map(x -> x.getKey().pos())
      .filter(x -> !x.pos().equals(featureToRename.pos()))
      .flatMap(x -> {
        // NYI better if we had the needed and more correct info directly
        // in the AST
        return LexerTool.NextTokenOfType(SourcePositionTool.ByLine(x._sourceFile, x.line()), Util.ArrayToSet(new Token[]
          {
              Token.t_set
          })).map(set -> {
            var whitespace =
              LexerTool.TokensAt(new SourcePosition(x._sourceFile, set.end().bytePos())).right();
            if (CHECKS)
              check(whitespace.token() == Token.t_ws);
            return whitespace.end();
          }).stream();
      });


    var choiceGenerics = ASTWalker
      .Features(LSP4jUtils.getUri(params))
      .filter(f -> f.resultType().isChoice())
      .filter(f -> {
        return f.resultType().choiceGenerics().stream().anyMatch(t -> {
          return TypeTool.baseName(t).equals(featureToRename.featureName().baseName());
        });
      })
      .map(f -> positionOfChoiceGeneric(featureToRename.featureName().baseName(), f));

    return Util.ConcatStreams(
      callsSourcePositions,
      typePositions,
      Stream.of(pos),
      assignmentPositions,
      choiceGenerics);
  }

  private static SourcePosition positionOfChoiceGeneric(String name, AbstractFeature f)
  {
    return LexerTool
      .TokensFrom(new SourcePosition(f.pos()._sourceFile, 0))
      .filter(token -> name.equals(token.text()))
      .filter(token -> {
        return SourcePositionTool.Compare(
          token.start(), new SourcePosition(token.start()._sourceFile, f.pos().bytePos())) > 0;
      })
      .findFirst()
      .get()
      .start();
  }

  private static int lengthOfFeatureIdentifier(AbstractFeature feature)
  {
    return Arrays.stream(feature.featureName().baseName().split(" "))
      .map(str -> Util.CharCount(str))
      .reduce(0, (acc, item) -> item);
  }

  // NYI disallow renaming of stdlib
  public static PrepareRenameResult getPrepareRenameResult(TextDocumentPositionParams params)
  {
    var pos = Bridge.toSourcePosition(params);
    var featureAt = QueryAST.FeatureAt(pos);
    if (featureAt.isEmpty())
      {
        return new PrepareRenameResult();
      }

    return LexerTool.IdentOrOperatorTokenAt(pos)
      .map(token -> {
        return new PrepareRenameResult(LSP4jUtils.range(token), token.text());
      })
      .orElse(new PrepareRenameResult());
  }

}
