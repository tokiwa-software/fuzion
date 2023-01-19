/*

This file is part of the Fuzion language implementation.

The Fuzion docs generator implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion docs generator implementation is distributed in the hope that it will be
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
 * Source of class Main
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.docs;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.mir.MIR;
import dev.flang.tools.FuzionHome;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;

public class Docs
{

  /**
   * Compare Features by basename + args
   */
  private static final Comparator<? super AbstractFeature> byFeatureName = Comparator.comparing(
    af -> af.featureName().toString(),
    (name1, name2) -> {
      var caseInsensitive = name1.compareToIgnoreCase(name2);
      if (caseInsensitive != 0)
        {
          return caseInsensitive;
        }
      return name1.compareTo(name2);
    });


  private final FrontEndOptions frontEndOptions = new FrontEndOptions(
    /* verbose                 */ 0,
    /* fuzionHome              */ new FuzionHome()._fuzionHome,
    /* loadBaseLib             */ true,
    /* eraseInternalNamesInLib */ false,
    /* modules                 */ new List<>(),
    /* moduleDirs              */ new List<>(),
    /* dumpModules             */ new List<>(),
    /* fuzionDebugLevel        */ 0,
    /* fuzionSafety            */ false,
    /* enableUnsafeIntrinsics  */ false,
    /* sourceDirs              */ null,
    /* readStdin               */ true,
    /* main                    */ null,
    /* loadSources             */ true);

  private final FrontEnd fe = new FrontEnd(frontEndOptions);

  private final MIR mir = fe.createMIR();

  private final AbstractFeature universe = mir.universe();


  /**
   * recursive breadth first traversal
   * @param c
   * @param queue
   */
  private void breadthFirstTraverse0(Consumer<AbstractFeature> c, Queue<AbstractFeature> queue)
  {
    if (queue.isEmpty())
      {
        return;
      }
    var head = queue.remove();
    c.accept(head);
    queue.addAll(declaredFeatures(head).collect(Collectors.toList()));
    breadthFirstTraverse0(c, queue);
  }


  /**
   * get the declared features of f as stream
   * @param f
   * @return
   */
  private Stream<AbstractFeature> declaredFeatures(AbstractFeature f)
  {
    return fe.module()
      .declaredFeatures(f)
      .values()
      .stream();
  }


  /**
   * do a breath first traversel of declared features,
   * passing found features to consumer c.
   * @param c
   * @param start
   */
  private void breadthFirstTraverse(Consumer<AbstractFeature> c, AbstractFeature start)
  {
    var queue = new LinkedList<AbstractFeature>();
    queue.add(start);
    breadthFirstTraverse0(c, queue);
  }


  /**
   * parse arguments passed to fzdocs
   * @param args
   * @return
   * @throws FileNotFoundException
   * @throws IOException
   */
  private static DocsOptions parseArgs(String[] args) throws FileNotFoundException, IOException
  {
    if (args.length < 1)
      {
        System.err.println(usage());
        System.exit(1);
      }

    if (Stream.of(args).anyMatch(arg -> arg.equals("-styles")))
      {
        return new DocsOptions(null, false, true);
      }

    var destination = parseDestination(args);

    var bare = Stream.of(args).anyMatch(arg -> arg.equals("-bare"));
    return new DocsOptions(destination, bare, false);
  }


  /**
   * parse destination for fzdocs
   * @param args
   * @return
   */
  private static Path parseDestination(String[] args)
  {
    var outputPath = Stream.of(args).reduce((first, second) -> second).get();
    return Path.of(".").resolve(outputPath);
  }


  /**
   * print file to stdout
   * @param file
   * @throws FileNotFoundException
   * @throws IOException
   */
  private static void echoFile(String file) throws FileNotFoundException, IOException
  {
    try (BufferedReader br = new BufferedReader(new FileReader(file)))
      {
        String line;
        while ((line = br.readLine()) != null)
          {
            System.out.println(line);
          }
      }
  }


  /**
   * get usage info
   * @return
   */
  @Deprecated
  private static String usage()
  {
    return """
      Usage: fzdoc [-bare] <destination>
      or     fzdoc -styles
      """;
  }


  /**
   * should this feature be ignored in docs?
   * @param af
   * @return
   */
  // NYI we want to ignore most but not all fields
  // but how to distinguish?
  private static boolean ignoreFeature(AbstractFeature af)
  {
    return af.visibility() == Visi.INVISIBLE
      || af.visibility() == Visi.PRIVATE
      || af.isTypeFeature()
      || Util.isArgument(af)
      || isDummyFeature(af);
  }


  /**
   * is this feature the dummy feature?
   * @param af
   * @return
   */
  private static boolean isDummyFeature(AbstractFeature af)
  {
    return af.qualifiedName().equals("dummyFeature");
  }


  /**
   * Get a path for a feature.
   * This is where the docs of this feature are
   * @param f
   * @return
   */
  private static String featurePath(AbstractFeature f)
  {
    if (f.isUniverse())
      {
        return "";
      }
    String path = (featurePath(f.outer()) + f.featureName().toString());

    return path
      .replace(" ", "+")
      + "/";
  }


  private void run(DocsOptions config)
  {
    // declared features are sorted by feature name
    var mapOfDeclaredFeatures = new HashMap<AbstractFeature, SortedSet<AbstractFeature>>();

    breadthFirstTraverse(feature -> {
      if (ignoreFeature(feature))
        {
          return;
        }
      var s = declaredFeatures(feature)
        .filter(af -> !ignoreFeature(af))
        .collect(Collectors.toCollection(
          () -> new TreeSet<>(byFeatureName)));
      mapOfDeclaredFeatures.put(feature, s);
    }, universe);

    var htmlTool = new Html(config, mapOfDeclaredFeatures, universe);

    mapOfDeclaredFeatures
      .keySet()
      .stream()
      .forEach(af -> {
        var path = af.isUniverse()
                                    ? config.destination()
                                    : config.destination().resolve(featurePath(af));
        path.toFile().mkdirs();

        try
          {
            FileWriter writer = new FileWriter(new File(path.toFile(), "index.html"));
            var output = htmlTool.content(af);
            writer.write(output);
            writer.close();
          }
        catch (IOException e)
          {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      });
  }


  /**
   * main of fzdocs
   */
  public static void main(String[] args) throws Exception
  {
    var config = parseArgs(args);
    if (config.printCSSStyles())
      {
        echoFile("./assets/docs/style.css");
        System.exit(0);
        return;
      }


    // NYI get rid of this hack
    var fakeIn = new ByteArrayInputStream("dummyFeature is".getBytes());
    System.setIn(fakeIn);

    new Docs().run(config);
  }


}
