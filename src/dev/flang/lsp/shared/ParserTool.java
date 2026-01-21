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
 * Source of class ParserTool
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.shared;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fe.LibraryFeature;
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.SourcePosition;

/**
 * - does the parsing of a given URI
 * - caches parsing results.
 * - provides a function to get the original URI of a SourcePosition
 */
public class ParserTool extends ANY
{

  /**
   * maps temporary files which are fed to the parser to their original uri.
   */
  // NYI: UNDER DEVELOPMENT: fix memory leak
  private static final TreeMap<String, URI> tempFile2Uri = new TreeMap<>();

  private static List<String> _modules = List.<String>of();

  public static void setModules(List<String> modules)
  {
    _modules = modules;
  }

  private static final ParserCache parserCache = new ParserCache();

  /**
   * NYI: UNDER DEVELOPMENT: in the case of uri to stdlib  we need context
   * @param uri
   * @return ParserCacheItem, empty if user starts in stdlib file and no record present yet.
   */
  private synchronized static ParserCacheItem getParserCacheItem(URI uri)
  {
    var sourceText = SourceText.getText(uri);
    var result = parserCache.computeIfAbsent(uri, sourceText, key -> createParserCacheItem(uri));
    // NYI: UNDER DEVELOPMENT: hack! Without this the test RegressionRenameMandelbrotImage fails
    // when running all tests
    Types.resolved = result.resolved();
    return result;
  }

  private static ParserCacheItem createParserCacheItem(URI uri)
  {
    var frontEndOptions = frontEndOptions(uri);
    var frontEnd = new FrontEnd(frontEndOptions);
    var errors = Errors.errors();
    var warnings = Errors.warnings();

    return new ParserCacheItem(uri, frontEndOptions, frontEnd, errors, warnings, Types.resolved);
  }

  private static FrontEndOptions frontEndOptions(URI uri)
  {
    File tempFile = ParserTool.toTempFile(uri);
    // this is too slow
    // var isStdLib = Util.IsStdLib(uri);
    var isStdLib = false;
    var frontEndOptions =
      new FrontEndOptions(
        /* verbose                 */ 0,
        /* fuzionHome              */ SourceText.fuzionHome,
        /* loadBaseLib             */ !isStdLib,
        /* eraseInternalNamesInLib */ false,
        /* modules                 */ isStdLib ? new dev.flang.util.List<String>() : new dev.flang.util.List<String>(_modules.iterator()),
        /* moduleDirs              */ new dev.flang.util.List<String>(),
        /* dumpModules             */ new dev.flang.util.List<String>(),
        /* fuzionDebugLevel        */ 1,
        /* fuzionSafety            */ true,
        /* sourceDirs              */ isStdLib ? new dev.flang.util.List<String>(uri.getRawPath().substring(0,uri.getRawPath().indexOf("/lib/")) + "/lib") : new dev.flang.util.List<String>(),
        /* readStdin               */ false,
        /* executeCode             */ null,
        /* main                    */ isStdLib ? null : tempFile.getAbsolutePath(),
        /* moduleName              */ FuzionConstants.MAIN_MODULE_NAME,
        /* loadSources             */ true,
        /* needsEscapeAnalysis     */ false,
        /* serializeFuir           */ false,
        /* timer                   */ s -> {})
        {
          @Override
          public boolean isLanguageServer()
          {
            return true;
          }
        };
    return frontEndOptions;
  }

  /**
   * get original URI of given sourcePosition if present
   * necessary because we are feeding the parser temporary files
   * @param sourcePosition
   * @return
   */
  public static URI getUri(SourcePosition sourcePosition)
  {
    var result = tempFile2Uri.get(sourcePosition._sourceFile._fileName.toString());
    if (result != null)
      {
        return result;
      }
    return SourceText.uriOf(sourcePosition);
  }

  private static File toTempFile(URI uri)
  {
    var sourceText = Util.isStdLib(uri) ? "dummyFeature is": SourceText.getText(uri);
    File sourceFile = IO.writeToTempFile(sourceText);
    try
      {
        tempFile2Uri.put(sourceFile.toPath().toString(), uri);
      }
    catch (Exception e)
      {
        ErrorHandling.writeStackTrace(e);
      }
    return sourceFile;
  }

  public static AbstractFeature universe(URI uri)
  {
    return getParserCacheItem(uri).universe();
  }

  public static Stream<AbstractFeature> declaredFeatures(AbstractFeature f)
  {
    return declaredFeatures(f, false);
  }

  public static Stream<AbstractFeature> declaredFeatures(AbstractFeature f, boolean includeInternalFeatures)
  {
    if (TypeTool.containsError(f.selfType()))
      {
        return Stream.empty();
      }
    return parserCache.sourceModule(f)
      .declaredFeatures(f)
      .values()
      .stream()
      .filter(af -> {
        var isFromModule = (af instanceof LibraryFeature);
        return !isFromModule || af.visibility().eraseTypeVisibility() == Visi.PUB;
      })
      .filter(feat -> includeInternalFeatures
        || !FeatureTool.isInternal(feat));
  }

  /**
   * NYI: UNDER DEVELOPMENT: explain which pos are we actually returning here?
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    var result = feature.isRoutine()
      ? endPos(feature.code().sourceRange())
      : endPos(feature.sourceRange());


    return result;
  }

  private static SourcePosition endPos(SourcePosition pos)
  {
    return new SourcePosition(pos._sourceFile, pos.byteEndPos());
  }

  public static Stream<Errors.Error> warnings(URI uri)
  {
    return getParserCacheItem(uri).warnings().stream();
  }

  public static Stream<Errors.Error> errors(URI uri)
  {
    return getParserCacheItem(uri).errors().stream();
  }

  public static Stream<AbstractFeature> topLevelFeatures(URI uri)
  {
    return getParserCacheItem(uri).topLevelFeatures();
  }

}
