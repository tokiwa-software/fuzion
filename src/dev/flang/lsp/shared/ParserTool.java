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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCase;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Block;
import dev.flang.ast.Call;
import dev.flang.ast.Cond;
import dev.flang.ast.Destructure;
import dev.flang.ast.DotType;
import dev.flang.ast.Expr;
import dev.flang.ast.Feature;
import dev.flang.ast.FeatureVisitor;
import dev.flang.ast.Function;
import dev.flang.ast.If;
import dev.flang.ast.Impl;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Tag;
import dev.flang.ast.This;
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
  // NYI fix memory leak
  private static TreeMap<String, URI> tempFile2Uri = new TreeMap<>();

  private static final int END_OF_FEATURE_CACHE_MAX_SIZE = 1;

  private static List<String> javaModules = List.<String>of();

  public static void setJavaModules(List<String> javaModules)
  {
    javaModules = javaModules;
  }

  private static ParserCache parserCache = new ParserCache();

  /**
   * LRU-Cache holding end of feature calculations
   */
  private static final Map<AbstractFeature, SourcePosition> EndOfFeatureCache =
    Util.threadSafeLRUMap(END_OF_FEATURE_CACHE_MAX_SIZE, null);

  /**
   * NYI in the case of uri to stdlib  we need context
   * @param uri
   * @return ParserCacheItem, empty if user starts in stdlib file and no record present yet.
   */
  private synchronized static ParserCacheItem getParserCacheItem(URI uri)
  {
    var sourceText = SourceText.getText(uri);
    var result = parserCache.computeIfAbsent(uri, sourceText, key -> createParserCacheItem(uri));
    // NYI hack! Without this the test RegressionRenameMandelbrotImage fails
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
        /* modules                 */ isStdLib ? new dev.flang.util.List<String>() : new dev.flang.util.List<String>(javaModules.iterator()),
        /* moduleDirs              */ new dev.flang.util.List<String>(),
        /* dumpModules             */ new dev.flang.util.List<String>(),
        /* fuzionDebugLevel        */ 1,
        /* fuzionSafety            */ true,
        /* enableUnsafeIntrinsics  */ true,
        /* sourceDirs              */ isStdLib ? new dev.flang.util.List<String>(uri.getRawPath().substring(0,uri.getRawPath().indexOf("/lib/")) + "/lib") : new dev.flang.util.List<String>(),
        /* readStdin               */ false,
        /* executeCode             */ null,
        /* main                    */ isStdLib ? null : tempFile.getAbsolutePath(),
        /* moduleName              */ "main",
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
   * NYI explain which pos are we actually returning here?
   * @param feature
   * @return
   */
  public static SourcePosition endOfFeature(AbstractFeature feature)
  {
    if (PRECONDITIONS)
      require(!feature.pos().isBuiltIn());

    if (feature.featureName().baseName().equals(FuzionConstants.RESULT_NAME))
      {
        return endOfFeature(feature.outer());
      }
    // NYI replace by real end of feature once we have this information in the
    // AST
    // NOTE: since this is a very expensive calculation and frequently used we
    // cache this
    return EndOfFeatureCache.computeIfAbsent(feature, af -> {
      if (FeatureTool.isArgument(af))
        {
          return LexerTool.endOfToken(af.pos());
        }
      if (!af.isUniverse() && FeatureTool.isOfLastFeature(af))
        {
          return new SourcePosition(af.pos()._sourceFile, af.pos()._sourceFile.byteLength());
        }

      var visitor = new FeatureVisitor() {
        public SourcePosition lastPos = SourcePosition.notAvailable;
        private void FoundPos(SourcePosition visitedPos)
        {
          if (visitedPos != null)
            {
              lastPos = SourcePositionTool.compare(lastPos, visitedPos) >=0 ? lastPos : visitedPos;
            }
        }
        @Override public void         action      (AbstractAssign a) { FoundPos(a.pos()); }
        @Override public void         actionBefore(Block          b) { FoundPos(b.pos()); }
        @Override public void         actionAfter (Block          b) { FoundPos(b.pos()); }
        @Override public void         action      (AbstractCall   c) { FoundPos(c.pos()); }
        @Override public Expr         action      (Call           c) { FoundPos(c.pos()); return c; }
        @Override public Expr         action      (DotType        d) { FoundPos(d.pos()); return d; }
        @Override public void         actionBefore(AbstractCase   c) { FoundPos(c.pos()); }
        @Override public void         actionAfter (AbstractCase   c) { FoundPos(c.pos()); }
        @Override public void         action      (Cond           c) { FoundPos(c.cond.pos()); }
        @Override public Expr         action      (Destructure    d) { FoundPos(d.pos()); return d; }
        @Override public Expr         action      (Feature        f, AbstractFeature outer) { FoundPos(f.pos()); return f; }
        @Override public Expr         action      (Function       f) { FoundPos(f.pos()); return f; }
        @Override public Expr         action      (If             i) { FoundPos(i.pos()); return i; }
        @Override public void         action      (Impl           i) { FoundPos(i.pos); }
        @Override public Expr         action      (InlineArray    i) { FoundPos(i.pos()); return i; }
        @Override public void         action      (AbstractMatch  m) { FoundPos(m.pos()); }
        @Override public void         action      (Tag            b) { FoundPos(b.pos()); }
        @Override public Expr         action      (This           t) { FoundPos(t.pos()); return t; }
        @Override public AbstractType action      (AbstractType   t) { FoundPos(t.declarationPos()); return t; }
      };
      if (af instanceof Feature f)
        {
          f.visit(visitor);
        }
      af.visitCode(visitor);

      var result = visitor.lastPos.equals(SourcePosition.notAvailable) ? af.pos() : visitor.lastPos;

      result = (SourcePosition) LexerTool
          .tokensFrom(result)
          .skip(1)
          // NYI do we need to sometimes consider right brackets as well?
          .filter(t -> !(t.isWhitespace()))
          // t is the first token not belonging to feature
          .map(t -> LexerTool.goLeft(t.start()))
          .findFirst()
          .orElse(result);

      if (POSTCONDITIONS)
        ensure(af.pos().line() < result.line()
          || (af.pos().line() == result.line() && af.pos().column() < result.column()));
      return result;
    });
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
