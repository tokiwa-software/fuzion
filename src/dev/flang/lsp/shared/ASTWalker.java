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
 * Source of class ASTWalker
 *
 *---------------------------------------------------------------------*/

package dev.flang.lsp.shared;

import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Stream;

import dev.flang.ast.AbstractAssign;
import dev.flang.ast.AbstractBlock;
import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractCase;
import dev.flang.ast.Constant;
import dev.flang.ast.AbstractCurrent;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractLambda;
import dev.flang.ast.AbstractMatch;
import dev.flang.ast.Box;
import dev.flang.ast.Call;
import dev.flang.ast.Expr;
import dev.flang.ast.If;
import dev.flang.ast.InlineArray;
import dev.flang.ast.Nop;
import dev.flang.ast.Tag;
import dev.flang.ast.Universe;
import dev.flang.util.HasSourcePosition;
import dev.flang.util.SourcePosition;

/**
 * depth first traversal
 * collects calls, features etc. (=key) as well as their outer features (=value).
 */
public class ASTWalker
{

  public static Stream<Entry<HasSourcePosition, AbstractFeature>> traverse(SourcePosition pos)
  {
    return traverse(SourceText.uriOf(pos));
  }

  public static Stream<Entry<HasSourcePosition, AbstractFeature>> traverse(URI uri)
  {
    return ParserTool.topLevelFeatures(uri)
      .flatMap(f -> traverseFeature(f, true));
  }

  public static Stream<Entry<HasSourcePosition, AbstractFeature>> traverseFeature(AbstractFeature feature,
    boolean descend)
  {
    // NYI heuristic to abort traverse
    // if (feature.outer() != null
    //   && feature.outer().pos()._sourceFile._fileName.startsWith(FuzionConstants.SYMBOLIC_FUZION_HOME.toString()))
    //   {
    //     return Stream.empty();
    //   }
    return Util.concatStreams(

      FeatureTool.isInternal(feature) ? Stream.empty(): asStream(feature, feature.outer()),

      feature.arguments()
        .stream()
        .flatMap(f -> traverseFeature(f, false)),

      // feature.isRoutine() sometimes throws because it depends on
      // statically held Types.resolved.f_choice which may have been cleared
      // already.
      // We may remove wrapper ResultOrDefault in the future if this changes.
      ErrorHandling.resultOrDefault(() -> feature.isRoutine(), true)
                                                                     ? traverseExpression(feature.code(), feature)
                                                                     : Stream.empty(),

      feature.inherits()
        .stream()
        // filter implicit inheritance of Object
        .filter(x -> x.calledFeature().inherits().size() != 0)
        .flatMap(x -> traverseCall(x, feature)),

      feature.contract()._declared_preconditions.stream().flatMap(x -> traverseExpression(x.cond, feature.outer())),
      feature.contract()._declared_postconditions.stream().flatMap(x -> traverseExpression(x.cond, feature.outer())),

      descend
              ? ParserTool.declaredFeatures(feature, true)
                .flatMap(f -> traverseFeature(f, descend))
              : Stream.empty())
      .distinct();
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> traverseCase(AbstractCase c, AbstractFeature outer)
  {
    return traverseExpression(c.code(), outer);
  }


  private static Stream<Entry<HasSourcePosition, AbstractFeature>> traverseAssign(AbstractAssign a,
    AbstractFeature outer)
  {
    return Util.concatStreams(

      asStream(a, outer),

      traverseExpression(a._value, outer),
      traverseExpression(a._target, outer));
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> asStream(HasSourcePosition item,
    AbstractFeature outer)
  {
    return Stream.of(new SimpleEntry<HasSourcePosition, AbstractFeature>(item, outer));
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> traverseBlock(AbstractBlock b, AbstractFeature outer)
  {
    return b._expressions.stream().flatMap(s -> traverseExpression(s, outer));
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> traverseExpression(Expr expr, AbstractFeature outer)
  {
    if (expr == null)
      {
        return Stream.empty();
      }
    if (expr instanceof AbstractBlock b)
      {
        return traverseBlock(b, outer);
      }
    if (expr instanceof AbstractMatch m)
      {
        return Util.concatStreams(
          // used for generating match cases
          asStream(m, outer),
          traverseExpression(m.subject(), outer),
          m.cases().stream().flatMap(c -> traverseCase(c, outer)));
      }
    if (expr instanceof AbstractCall c)
      {
        return traverseCall(c, outer);
      }
    if (expr instanceof Tag t)
      {
        return traverseExpression(t._value, outer);
      }
    if (expr instanceof Box b)
      {
        return traverseExpression(b._value, outer);
      }
    if (expr instanceof If i)
      {
        return Util.concatStreams(
          traverseExpression(i.cond, outer),
          traverseBlock(i.block, outer),
          i.elseBlock != null ? traverseBlock(i.elseBlock, outer): Stream.empty());
      }
    // for offering completions on constants
    if (expr instanceof Constant ac)
      {
        return asStream(ac, outer);
      }
    if (expr instanceof AbstractFeature af)
      {
        return traverseFeature(af, false);
      }
    if (expr instanceof AbstractAssign a)
      {
        return traverseAssign(a, outer);
      }
    if (expr instanceof InlineArray ia)
      {
        return Stream.concat(asStream(ia, outer), ia._elements.stream().flatMap(e -> traverseExpression(e, outer)));
      }
    if ( expr == Call.ERROR
      || expr instanceof AbstractCurrent
      || expr instanceof Constant
      || expr instanceof Universe
      || expr instanceof AbstractLambda
      || expr instanceof Nop)
      {
        return Stream.empty();
      }
    throw new RuntimeException("TraverseExpression not implemented for: " + expr.getClass());
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> traverseCall(AbstractCall c, AbstractFeature outer)
  {
    return Util.concatStreams(
      asStream(c, outer),
      c.actuals().stream().flatMap(a -> traverseExpression(a, outer)),
      traverseExpression(c.target(), outer));
  }

  /**
   * @param start
   * @return any calls - and their outer features - happening in feature start or descending features of start
   */
  public static Stream<SimpleEntry<AbstractCall, AbstractFeature>> calls(AbstractFeature start)
  {
    return traverseFeature(start, true)
      .filter(entry -> {
        return entry.getKey() instanceof AbstractCall;
      })
      .map(obj -> new SimpleEntry<>((AbstractCall) obj.getKey(), obj.getValue()));
  }

  public static Stream<AbstractFeature> Features(URI uri)
  {
    return traverse(uri)
      .filter(entry -> {
        return entry.getKey() instanceof AbstractFeature;
      })
      .map(obj -> (AbstractFeature) obj.getKey());
  }

  /**
   * @param start
   * @return any assigns - and their outer features - happening in feature start or descending features of start
   */
  public static Stream<SimpleEntry<AbstractAssign, AbstractFeature>> assignments(AbstractFeature start,
    AbstractFeature assignedFeature)
  {
    return traverseFeature(start, true)
      .filter(entry -> {
        return AbstractAssign.class.isAssignableFrom(entry.getKey().getClass());
      })
      .map(obj -> new SimpleEntry<>((AbstractAssign) obj.getKey(), obj.getValue()))
      .filter(entry -> entry.getKey()._assignedField != null
        && entry.getKey()._assignedField.equals(assignedFeature));
  }

}
