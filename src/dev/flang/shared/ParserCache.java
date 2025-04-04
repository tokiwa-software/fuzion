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
 * Source of class ParserCache
 *
 *---------------------------------------------------------------------*/

package dev.flang.shared;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import dev.flang.ast.AbstractFeature;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.SourceModule;
import dev.flang.util.ANY;

public class ParserCache extends ANY
{
  private int PARSER_CACHE_MAX_SIZE = 1;

  /*
   * this map is kept in sync with sourceText2ParserCache
   */
  private HashMap<AbstractFeature, FrontEnd> universe2FrontEndMap = new HashMap<>();

  // LRU-Cache holding the most recent results of parser
  private Map<String, ParserCacheItem> sourceText2ParserCache =
    Util.ThreadSafeLRUMap(PARSER_CACHE_MAX_SIZE, (removed) -> {
      var frontEnd = universe2FrontEndMap.remove(removed.getValue().universe());
      check(frontEnd != null, universe2FrontEndMap.size() <= PARSER_CACHE_MAX_SIZE);
    });

  public ParserCacheItem computeIfAbsent(URI uri, String sourceText,
    Function<String, ParserCacheItem> mappingFunction)
  {
    var key = uri + sourceText;
    return sourceText2ParserCache.computeIfAbsent(key, (str) -> {
      long startTime = System.nanoTime();

      var parserCacheItem = mappingFunction.apply(str);
      universe2FrontEndMap.put(parserCacheItem.universe(), parserCacheItem.frontEnd());

      long stopTime = System.nanoTime();
      var elapsedTime = (int) ((stopTime - startTime) / 1E6);
      Context.Logger.Log("[Parsing] finished in " + elapsedTime + "ms: " + uri);

      return parserCacheItem;
    });
  }


  /**
   * get the SourceModule the Feature belongs to
   * @param f
   * @return
   */
  public SourceModule SourceModule(AbstractFeature f)
  {
    if (PRECONDITIONS)
      require(!TypeTool.ContainsError(f.selfType()));
    var universe = FeatureTool.Universe(f);
    return universe2FrontEndMap.get(universe).sourceModule();
  }
}
