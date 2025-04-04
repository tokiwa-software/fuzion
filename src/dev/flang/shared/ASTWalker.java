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

package dev.flang.shared;

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

  public static Stream<Entry<HasSourcePosition, AbstractFeature>> Traverse(SourcePosition pos)
  {
    return Traverse(SourceText.UriOf(pos));
  }

  public static Stream<Entry<HasSourcePosition, AbstractFeature>> Traverse(URI uri)
  {
    return ParserTool.TopLevelFeatures(uri)
      .flatMap(f -> TraverseFeature(f, true));
  }

  public static Stream<Entry<HasSourcePosition, AbstractFeature>> TraverseFeature(AbstractFeature feature,
    boolean descend)
  {
    // NYI heuristic to abort traverse
    // if (feature.outer() != null
    //   && feature.outer().pos()._sourceFile._fileName.startsWith(FuzionConstants.SYMBOLIC_FUZION_HOME.toString()))
    //   {
    //     return Stream.empty();
    //   }
    return Util.ConcatStreams(

      FeatureTool.IsInternal(feature) ? Stream.empty(): AsStream(feature, feature.outer()),

      feature.arguments()
        .stream()
        .flatMap(f -> TraverseFeature(f, false)),

      // feature.isRoutine() sometimes throws because it depends on
      // statically held Types.resolved.f_choice which may have been cleared
      // already.
      // We may remove wrapper ResultOrDefault in the future if this changes.
      ErrorHandling.ResultOrDefault(() -> feature.isRoutine(), true)
                                                                     ? TraverseExpression(feature.code(), feature)
                                                                     : Stream.empty(),

      feature.inherits()
        .stream()
        // filter implicit inheritance of Object
        .filter(x -> x.calledFeature().inherits().size() != 0)
        .flatMap(x -> TraverseCall(x, feature)),

      feature.contract()._declared_preconditions.stream().flatMap(x -> TraverseExpression(x.cond, feature.outer())),
      feature.contract()._declared_postconditions.stream().flatMap(x -> TraverseExpression(x.cond, feature.outer())),

      descend
              ? ParserTool.DeclaredFeatures(feature, true)
                .flatMap(f -> TraverseFeature(f, descend))
              : Stream.empty())
      .distinct();
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> TraverseCase(AbstractCase c, AbstractFeature outer)
  {
    return TraverseExpression(c.code(), outer);
  }


  private static Stream<Entry<HasSourcePosition, AbstractFeature>> TraverseAssign(AbstractAssign a,
    AbstractFeature outer)
  {
    return Util.ConcatStreams(

      AsStream(a, outer),

      TraverseExpression(a._value, outer),
      TraverseExpression(a._target, outer));
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> AsStream(HasSourcePosition item,
    AbstractFeature outer)
  {
    return Stream.of(new SimpleEntry<HasSourcePosition, AbstractFeature>(item, outer));
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> TraverseBlock(AbstractBlock b, AbstractFeature outer)
  {
    return b._expressions.stream().flatMap(s -> TraverseExpression(s, outer));
  }

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> TraverseExpression(Expr expr, AbstractFeature outer)
  {
    if (expr == null)
      {
        return Stream.empty();
      }
    if (expr instanceof AbstractBlock b)
      {
        return TraverseBlock(b, outer);
      }
    if (expr instanceof AbstractMatch m)
      {
        return Util.ConcatStreams(
          // used for generating match cases
          AsStream(m, outer),
          TraverseExpression(m.subject(), outer),
          m.cases().stream().flatMap(c -> TraverseCase(c, outer)));
      }
    if (expr instanceof AbstractCall c)
      {
        return TraverseCall(c, outer);
      }
    if (expr instanceof Tag t)
      {
        return TraverseExpression(t._value, outer);
      }
    if (expr instanceof Box b)
      {
        return TraverseExpression(b._value, outer);
      }
    if (expr instanceof If i)
      {
        return Util.ConcatStreams(
          TraverseExpression(i.cond, outer),
          TraverseBlock(i.block, outer),
          i.elseBlock != null ? TraverseBlock(i.elseBlock, outer): Stream.empty());
      }
    // for offering completions on constants
    if (expr instanceof Constant ac)
      {
        return AsStream(ac, outer);
      }
    if (expr instanceof AbstractFeature af)
      {
        return TraverseFeature(af, false);
      }
    if (expr instanceof AbstractAssign a)
      {
        return TraverseAssign(a, outer);
      }
    if (expr instanceof InlineArray ia)
      {
        return Stream.concat(AsStream(ia, outer), ia._elements.stream().flatMap(e -> TraverseExpression(e, outer)));
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

  private static Stream<Entry<HasSourcePosition, AbstractFeature>> TraverseCall(AbstractCall c, AbstractFeature outer)
  {
    return Util.ConcatStreams(
      AsStream(c, outer),
      c.actuals().stream().flatMap(a -> TraverseExpression(a, outer)),
      TraverseExpression(c.target(), outer));
  }

  /**
   * @param start
   * @return any calls - and their outer features - happening in feature start or descending features of start
   */
  public static Stream<SimpleEntry<AbstractCall, AbstractFeature>> Calls(AbstractFeature start)
  {
    return TraverseFeature(start, true)
      .filter(entry -> {
        return entry.getKey() instanceof AbstractCall;
      })
      .map(obj -> new SimpleEntry<>((AbstractCall) obj.getKey(), obj.getValue()));
  }

  public static Stream<AbstractFeature> Features(URI uri)
  {
    return Traverse(uri)
      .filter(entry -> {
        return entry.getKey() instanceof AbstractFeature;
      })
      .map(obj -> (AbstractFeature) obj.getKey());
  }

  /**
   * @param start
   * @return any assigns - and their outer features - happening in feature start or descending features of start
   */
  public static Stream<SimpleEntry<AbstractAssign, AbstractFeature>> Assignments(AbstractFeature start,
    AbstractFeature assignedFeature)
  {
    return TraverseFeature(start, true)
      .filter(entry -> {
        return AbstractAssign.class.isAssignableFrom(entry.getKey().getClass());
      })
      .map(obj -> new SimpleEntry<>((AbstractAssign) obj.getKey(), obj.getValue()))
      .filter(entry -> entry.getKey()._assignedField != null
        && entry.getKey()._assignedField.equals(assignedFeature));
  }

}
