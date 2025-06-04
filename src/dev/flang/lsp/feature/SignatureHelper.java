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
 * Source of class SignatureHelper
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.feature;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Call;
import dev.flang.lsp.shared.FeatureTool;
import dev.flang.lsp.shared.LexerTool;
import dev.flang.lsp.shared.ParserTool;
import dev.flang.lsp.shared.QueryAST;
import dev.flang.lsp.shared.TypeTool;
import dev.flang.lsp.util.Bridge;
import dev.flang.util.ANY;

public class SignatureHelper extends ANY
{

  public enum TriggerCharacters
  {
    Comma(","),
    Space(" "),
    ParensLeft("(");

    private final String triggerChar;

    private TriggerCharacters(String s)
    {
      triggerChar = s;
    }

    public String toString()
    {
      return this.triggerChar;
    }
  }

  public static SignatureHelp getSignatureHelp(SignatureHelpParams params)
  {
    if (PRECONDITIONS)
      require(params.getPosition().getCharacter() > 0);

    var pos = Bridge.toSourcePosition(params);

    Optional<AbstractCall> call = QueryAST.callAt(pos);

    if (call.isEmpty())
      {
        return new SignatureHelp();
      }

    var featureOfCall =
      call.get().target() instanceof AbstractCall callTarget
                                                             ? Optional.of(callTarget.calledFeature())
                                                             : QueryAST.featureAt(LexerTool.goLeft(pos));

    if (featureOfCall.isEmpty())
      {
        return new SignatureHelp();
      }

    return getSignatureHelp(call.get(), featureOfCall.get());
  }

  private static SignatureHelp getSignatureHelp(AbstractCall call, AbstractFeature featureOfCall)
  {
    var consideredCallTargets_declaredOrInherited = ParserTool.declaredFeatures(featureOfCall); // NYI: what about inherited features?
    var consideredCallTargets_outerFeatures =
      FeatureTool.outerFeatures(featureOfCall).flatMap(f -> ParserTool.declaredFeatures(f));

    var consideredFeatures =
      Stream.concat(consideredCallTargets_declaredOrInherited, consideredCallTargets_outerFeatures);

    var calledFeatures = consideredFeatures
      .filter(f -> featureNameMatchesCallName(f, call));

    // NYI: UNDER DEVELOPMENT: how to "intelligently" sort the signatureinfos?
    return new SignatureHelp(calledFeatures.map(f -> signatureInformation(f)).collect(Collectors.toList()), 0, 0);
  }

  private static SignatureInformation signatureInformation(AbstractFeature feature)
  {
    var description = new MarkupContent(MarkupKind.MARKDOWN, FeatureTool.commentOfInMarkdown(feature));
    return new SignatureInformation(FeatureTool.label(feature, false), description,
      parameterInfo(feature));
  }

  private static boolean featureNameMatchesCallName(AbstractFeature f, AbstractCall ac)
  {
    if (!TypeTool.containsError(ac.type()))
      {
        return f.featureName().baseName().equals(ac.calledFeature().featureName().baseName());
      }
    if (ac instanceof Call c)
      {
        return f.featureName().baseName().equals(c.name());
      }
    throw new RuntimeException("not implemented");
  }

  private static List<ParameterInformation> parameterInfo(AbstractFeature calledFeature)
  {
    return calledFeature.arguments()
      .stream()
      .map(
        arg -> new ParameterInformation("NYI" + arg.featureName().baseName() + " " + arg.selfType().toString()))
      .collect(Collectors.toList());
  }

}
