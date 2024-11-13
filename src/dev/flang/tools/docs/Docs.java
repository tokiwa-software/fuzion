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
 * Source of class Docs
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.docs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.fe.FrontEnd;
import dev.flang.fe.FrontEndOptions;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;
import dev.flang.tools.FuzionHome;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;

public class Docs extends ANY
{


  private final FrontEndOptions frontEndOptions = new FrontEndOptions(
    /* verbose                 */ 0,
    /* fuzionHome              */ new FuzionHome()._fuzionHome,
    /* loadBaseLib             */ true,
    /* eraseInternalNamesInLib */ false,
    /* modules                 */ allModules(), // generate API docs for all modules (except Java ones)
    /* moduleDirs              */ new List<>(),
    /* dumpModules             */ new List<>(),
    /* fuzionDebugLevel        */ 0,
    /* fuzionSafety            */ false,
    /* enableUnsafeIntrinsics  */ false,
    /* sourceDirs              */ null,
    /* readStdin               */ false,
    /* executeCode             */ null,
    /* main                    */ null,
    /* loadSources             */ false,
    /* timer                   */ s->{});

  /**
   * Generate a list of all fuzion modules available in build/modules
   */
  private List<String> allModules()
  {
    List<String> modules = new List<>();

    try {
      modules.addAll((Files.list(new FuzionHome()._fuzionHome.resolve("modules"))
                            .filter(Files::isRegularFile)
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .filter(name -> name.endsWith(".fum"))
                            // exclude Java Modules from API docs
                            // (they also caused an endless recursion when using the docs generation on them)
                            .filter(name -> !name.startsWith("java."))
                            .map(name -> name.substring(0, name.lastIndexOf('.')))
                            .collect(Collectors.toList())));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return modules;
  }

  private final FrontEnd fe = new FrontEnd(frontEndOptions);

  private final AbstractFeature universe = fe._feUniverse;


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
   * Get the declared features of f as stream
   * @param f the feature for which the declared features are to be returned
   * @return a stream of the declared features of f
   */
  private Stream<AbstractFeature> declaredFeatures(AbstractFeature f)
  {
    return fe.feModule()
      .declaredFeatures(f)
      .values()
      .stream();
  }


  /**
   * Get all features that can be called on f (declared and inherited) as stream
   * @param f the feature for which the callable features are to be returned
   * @return a stream of the callable (declared and inherited) features of f
   */
  private Stream<AbstractFeature> allInnerAndInheritedFeatures(AbstractFeature f)
  {
    var result = new List<AbstractFeature>();
    fe.feModule().forEachDeclaredOrInheritedFeature(f, af -> result.add(af));
    return result
      .stream();
  }


  /**
   * do a breath first traversal of declared features,
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
        say_err(usage());
        System.exit(1);
      }

    if (Stream.of(args).anyMatch(arg -> arg.equals("-styles")))
      {
        return new DocsOptions(null, false, true, false);
      }

    var destination = parseDestination(args);

    var bare = Stream.of(args).anyMatch(arg -> arg.equals("-bare"));
    var ignoreVisibility = Stream.of(args).anyMatch(arg -> arg.equals("-ignoreVisibility"));
    return new DocsOptions(destination, bare, false, ignoreVisibility);
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
            say(line);
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
  private static boolean ignoreFeature(AbstractFeature af, boolean ignoreVisibility)
  {
    if (af.isUniverse())
      {
        return false;
      }

    return af.resultType().equals(Types.t_ADDRESS)
      || af.featureName().isInternal()
      || af.featureName().isNameless()
      || !(ignoreVisibility || Util.isVisible(af))
      || af.isCotype()
      || Util.isArgument(af)
      || af.featureName().baseName().equals(FuzionConstants.RESULT_NAME);
  }


  /**
   * Get a path for a feature.
   * This is where the docs of this feature are
   * @param f the feature for which to get the path for
   * @param module the module for which the docs are created
   * @return
   */
  private static String featurePath(AbstractFeature f, LibraryModule module)
  {
    return featurePath(f, module, true);
  }


  /**
   * Get a path for a feature.
   * This is where the docs of this feature are
   * @param f the feature for which to get the path for
   * @param module the module for which the docs are created
   * @param modulePrefix whether the module prefix should be included in the path
   * @return
   */
  private static String featurePath(AbstractFeature f, LibraryModule module, boolean modulePrefix)
  {
    if (f.isUniverse())
      {
        return "";
      }

      // docs are generated per module, for features in universe add the module's folder
      String path = (modulePrefix && f.outer().isUniverse()) ? module.name() + "/" : "";

      path += f.isCotype() ? (featurePath(f.cotypeOrigin(), module, false) + "/" + "type.")
                                : (featurePath(f.outer(), module) + f.featureName().toString()) + "/";

    return path
      .replace(" ", "+");
  }

  /**
   * Cast an AbstractFeature to LibraryFeature, requires that this is possible
   * @param af an AbstractFeature that can be casted to LibraryFeature
   * @return
   */
  private static final LibraryFeature lf(AbstractFeature af)
  {
    return (LibraryFeature) af;
  }


  private void run(DocsOptions config)
  {
    // get all modules
    var all_modules = allInnerAndInheritedFeatures(universe)
              .map(af->lf(af)._libModule)
              .distinct()
              .filter(m->!m.name().equals("main")) // NYI: CLEANUP: Don't generate page for main module. Is there a better way to do this?
              .collect(Collectors.toCollection(List::new));

    // collect all features for all modules
    var mapOfDeclaredFeatures = new HashMap<AbstractFeature, Map<AbstractFeature.Kind, TreeSet<AbstractFeature>>>();

    breadthFirstTraverse(feature -> {
      if (ignoreFeature(feature, config.ignoreVisibility()))
        {
          return;
        }
      var s = allInnerAndInheritedFeatures(feature)
        .filter(af -> !ignoreFeature(af, config.ignoreVisibility()));

      Stream<AbstractFeature> st = Stream.empty();
      if (feature.hasCotype())
        {
          var tf = feature.cotype();
          st = allInnerAndInheritedFeatures(tf)
            .filter(af -> !ignoreFeature(af, config.ignoreVisibility()));
        }

      mapOfDeclaredFeatures.put(
        feature,
        Stream
          .concat(s, st)
          .collect(Collectors.groupingBy(x -> x.kind(), Collectors.toCollection(TreeSet::new)))
      );

    }, universe);


    // generate documentation per module
    for (var module : all_modules)
    {
        var htmlTool = new Html(config, mapOfDeclaredFeatures, universe, module, all_modules);

        mapOfDeclaredFeatures
          .keySet()
          .stream()
          .filter(af -> af.isUniverse() || lf(af).showInMod(module))
          .forEach(af -> {
            var path = af.isUniverse()
                                        ? config.destination().resolve(module.name())
                                        : config.destination().resolve(featurePath(af, module));
            path.toFile().mkdirs();

            var file = new File(path.toFile(), "index.html");
            try
              {
                FileWriter writer = new FileWriter(file);
                var output = htmlTool.content(af);
                writer.write(output);
                writer.close();
              }
            catch (IOException e)
              {
                throw new Error("file not writable: " + file.getPath());
              }
          });
        }

      // generate overview page of modules
      var path = config.destination();
      path.toFile().mkdirs();
      var htmlTool = new Html(config, mapOfDeclaredFeatures, universe, all_modules.getFirst(), all_modules);

      var file = new File(path.toFile(), "index.html");
      try
        {
          FileWriter writer = new FileWriter(file);
          writer.write(htmlTool.modulePage());
          writer.close();
        }
      catch (IOException e)
        {
          throw new Error("file not writable: " + file.getPath());
        }
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

    new Docs().run(config);
  }


}
