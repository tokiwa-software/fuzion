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
 * Source of class Commands
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import com.google.gson.JsonPrimitive;

import dev.flang.ast.AbstractMatch;
import dev.flang.lsp.Config;
import dev.flang.lsp.FuzionLanguageClient;
import dev.flang.lsp.util.Bridge;
import dev.flang.lsp.util.Computation;
import dev.flang.shared.ASTWalker;
import dev.flang.shared.CaseConverter;
import dev.flang.shared.Concurrency;
import dev.flang.shared.ErrorHandling;
import dev.flang.shared.ExprTool;
import dev.flang.shared.FeatureTool;
import dev.flang.shared.HasSourcePositionTool;
import dev.flang.shared.IO;
import dev.flang.shared.ParserTool;
import dev.flang.shared.TypeTool;
import dev.flang.shared.Util;

public enum Commands
{
  showSyntaxTree,
  run, callGraph, codeActionFixIdentifier, codeActionGenerateMatchCases;

  public String toString()
  {
    switch (this)
      {
      case showSyntaxTree :
        return "Show syntax tree";
      case callGraph :
        return "Show call graph";
      case codeActionFixIdentifier :
        return "Fix identifier";
      case codeActionGenerateMatchCases :
        return "Generate match cases";
      default:
        return "not implemented";
      }
  }

  private final static CompletableFuture<Object> completedFuture = CompletableFuture.completedFuture(null);

  public static CompletableFuture<Object> execute(ExecuteCommandParams params)
  {
    var uri = getArgAsString(params, 0);

    switch (Commands.valueOf(params.getCommand()))
      {

      case showSyntaxTree :
        return showSyntaxTree(uri);


      case callGraph :
        return callGraph(params, uri);


      case codeActionFixIdentifier :
        return codeActionFixIdentifier(params, uri);

      case codeActionGenerateMatchCases :
        return codeActionGenerateMatchCases(params, uri);


      default:
        ErrorHandling.WriteStackTrace(new Exception("not implemented"));
        return completedFuture;
      }
  }

  private static CompletableFuture<Object> codeActionGenerateMatchCases(ExecuteCommandParams params, String uri)
  {
    return Computation.cancellableComputation(() -> {

      var matchPos = new Position(getArgAsInt(params, 1), getArgAsInt(params, 2));

      return ASTWalker
        .Traverse(Util.toURI(uri))
        .filter(x -> x.getKey() instanceof AbstractMatch)
        .map(x -> (AbstractMatch) x.getKey())
        .filter(x -> x.pos().line() < (matchPos.getLine() + 1)
          || (x.pos().line() == (matchPos.getLine() + 1) && x.pos().column() <= (matchPos.getCharacter() + 1)))
        .sorted(HasSourcePositionTool.CompareBySourcePosition.reversed())
        .findFirst()
        .map(m -> {
          // NYI support indent different from two spaces
          var indent = IntStream.range(0, m.pos().column() + 1).mapToObj(x -> " ").collect(Collectors.joining());

          var text = System.lineSeparator()
            + m.subject()
              .type()
              .choiceGenerics()
              .stream()
              .filter(cg -> !m.cases().stream().anyMatch(c -> c.field() == null || c.field().resultType().isAssignableFrom(cg)))
              .map(t -> indent + CaseConverter.ToSnakeCase(TypeTool.baseName(t)) + " " + TypeTool.Label(t) + " =>")
              .collect(Collectors.joining(System.lineSeparator()));

          var endOfSubPos = Bridge.toPosition(ExprTool.EndOfExpr(m.subject()));

          var edit = new WorkspaceEdit(Map.of(
            uri,
            Stream.of(new TextEdit(new Range(endOfSubPos, endOfSubPos), text)).toList()));
          Config.languageClient().applyEdit(new ApplyWorkspaceEditParams(edit));
          return edit;
        })
        .orElse(null);
    }, "codeActionGenerateMatchCases", 1000);
  }

  private static CompletableFuture<Object> showSyntaxTree(String uri)
  {
    Concurrency.MainExecutor.submit(() -> showSyntaxTree(Util.toURI(uri)));
    return completedFuture;
  }

  private static CompletableFuture<Object> callGraph(ExecuteCommandParams params, String uri)
  {
    var arg1 = getArgAsString(params, 1);
    Concurrency.MainExecutor.submit(() -> callGraph(uri, arg1));
    return completedFuture;
  }

  private static CompletableFuture<Object> codeActionFixIdentifier(ExecuteCommandParams params, String uri)
  {
    var line = getArgAsInt(params, 1);
    var character = getArgAsInt(params, 2);
    var newName = getArgAsString(params, 3);

    return Rename
      .getWorkspaceEditsOrError(
        new TextDocumentPositionParams(new TextDocumentIdentifier(uri), new Position(line, character)), newName)
      .map(
        edit -> {
          Config.languageClient().applyEdit(new ApplyWorkspaceEditParams(edit));
          return completedFuture;
        },
        error -> {
          Config.languageClient().showMessage(new MessageParams(MessageType.Error, error.getMessage()));
          return completedFuture;
        });
  }

  private static String getArgAsString(ExecuteCommandParams params, int index)
  {
    return ((JsonPrimitive) params.getArguments().get(index)).getAsString();
  }

  private static int getArgAsInt(ExecuteCommandParams params, int index)
  {
    return ((JsonPrimitive) params.getArguments().get(index)).getAsInt();
  }

  public static Command create(Commands c, URI uri, List<Object> args)
  {
    var arguments = new ArrayList<Object>();
    arguments.add(uri.toString());
    arguments.addAll(args);
    return new Command(c.toString(), c.name(), arguments);
  }

  private static void callGraph(String arg0, String arg1)
  {
    // NYI go to correct feature via more direct way
    var feature = FeatureTool.SelfAndDescendants(ParserTool.Universe(Util.toURI(arg0)))
      .filter(f -> FeatureTool.UniqueIdentifier(f).equals(arg1))
      .findFirst()
      .get();
    var callGraph = FeatureTool.CallGraph(feature);
    var file = IO.writeToTempFile(callGraph, String.valueOf(System.currentTimeMillis()), ".fuzion.dot");
    try
      {
        // generate png
        (new ProcessBuilder(("dot -Tpng -o output.png " + file.toString()).split(" ")))
          .directory(file.getParentFile())
          .start()
          .waitFor();
        try
          {
            // first try: image magick
            (new ProcessBuilder("display output.png".split(" ")))
              .directory(file.getParentFile())
              .start();
          }
        catch (Exception e)
          {
            // try again with xdg-open
            (new ProcessBuilder("xdg-open output.png".split(" ")))
              .directory(file.getParentFile())
              .start();
          }
      }
    catch (Exception e)
      {
        Config.languageClient()
          .showMessage(new MessageParams(MessageType.Warning,
            "Display of call graph failed. Do you have graphviz and imagemagick installed?"));
        Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
      }
  }

  private static void showSyntaxTree(URI uri)
  {
    var ast = FeatureTool.AST(uri);
    var file = IO.writeToTempFile(ast, String.valueOf(System.currentTimeMillis()), ".fuzion.ast");
    Config.languageClient().showDocument(new ShowDocumentParams(file.toURI().toString()));
  }
}
