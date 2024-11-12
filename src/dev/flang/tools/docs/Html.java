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
 * Source of class Html
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools.docs;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;
import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;


public class Html extends ANY
{
  final DocsOptions config;
  private final Map<AbstractFeature, Map<AbstractFeature.Kind,TreeSet<AbstractFeature>>> mapOfDeclaredFeatures;
  private final String navigation;
  private final LibraryModule lm;
  private final List<LibraryModule> libModules;

  /**
   * the constructor taking the options
   */
  public Html(DocsOptions config, Map<AbstractFeature, Map<AbstractFeature.Kind,TreeSet<AbstractFeature>>> mapOfDeclaredFeatures, AbstractFeature universe, LibraryModule lm, List<LibraryModule> libModules)
  {
    this.config = config;
    this.mapOfDeclaredFeatures = mapOfDeclaredFeatures;
    this.lm = lm;
    this.libModules = libModules;
    this.navigation = navigation(universe, 0);
  }


  /*----------------------------  constants  ----------------------------*/

  static final String RUNCODE_BOX_HTML = """
    <div class="runcode-wrapper">
      <i class="far fa-spinner fa-spin"></i>
      <div class="mb-15 runcode" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%,40ch), min(100%, 80ch))); max-width: 49rem; opacity: 0;">
        <div class="position-relative">
          <form id="##ID##">
            <textarea class="codeinput" required="required" maxlength="4096" id="##ID##.code" name="code" rows="3" spellcheck="false">##CODE##</textarea>
            <div class="position-absolute runbuttons">
              <input type="button" onclick="runit('##ID##')" class="runbutton" name="run" value="Run!" />
              <input type="button" onclick="runiteff('##ID##')" class="runbutton" name="run" value="Effects!" />
              <a href="/tutorial/effects.html"><i>What are effects?</i></a>
            </div>
          </form>
        </div>
        <div class="computeroutput" id="##ID##.result"></div>
      </div>
    </div>""";


  /*-----------------------------  private methods  -----------------------------*/


  /*
   * html containing the inherited features of af
   */
  private String inherited(AbstractFeature af)
  {
    if (af.inherits().isEmpty() || af.signatureWithArrow()) // don't show inheritance for function features
      {
        return "";
      }
    return "<div class='fd-keyword mx-5'>:</div>" + af.inherits()
      .stream()
      .<String>map(c -> {
        var f = c.calledFeature();
        return "<a class='fd-feature fd-inherited' href='$1'>".replace("$1", featureAbsoluteURL(f))
          + htmlEncodedBasename(f)
          + (c.actualTypeParameters().size() > 0 ? "&nbsp;" : "")
          + c.actualTypeParameters().stream().map(at -> htmlEncodeNbsp(at.asString(false, af))).collect(Collectors.joining(", ")) + "</a>";
      })
      .collect(Collectors.joining("<span class='mr-2 fd-keyword'>,</span>"));
  }


  /**
   * anchor tag for type
   * @param at
   * @param context the feature to which the name should be relative to, full qualified name if null
   * @return
   */
  private String anchor(AbstractType at, AbstractFeature context)
  {
    if (at.isGenericArgument())
      {
        return htmlEncodeNbsp(at.asString(false, context));
      }
    return "<a class='fd-type' href='$2'>$1</a>".replace("$1", htmlEncodeNbsp(at.asString(false, context)))
      .replace("$2", featureAbsoluteURL(at.feature()));
  }


  /**
   * anchor tags for feature
   * eg: <a>outer outer basename</a>.<a>outer basename</a>.<a>this basename</a>
   */
  private String anchorTags(AbstractFeature f)
  {
    return anchorTags0(f).collect(Collectors.joining("."));
  }

  private Stream<String> anchorTags0(AbstractFeature f)
  {
    if (f.isUniverse())
      {
        return Stream.empty();
      }
    return Stream.concat(anchorTags0(f.outer()),
      Stream.of(typePrfx(f) + "<a class='fd-feature font-weight-600' href='$2'>$1</a>".replace("$1", htmlEncodedBasename(f))
        .replace("$2", featureAbsoluteURL(f))));
  }


  /**
   * Return "type." prefix if af is a type feature.
   * @param af the feature to be checked
   * @return html for "type." prefix if type feature, empty string otherwise
   */
  private String typePrfx(AbstractFeature af)
  {
    // NYI: does not treat features that `Type` inherits but does not redefine as type features, see #3716
    return af.outer() != null && (af.outer().isCotype()  || af.outer().compareTo(Types.resolved.f_Type) == 0) && !af.isCotype() ? "<span class=\"fd-keyword\">type</span>." : "";
  }


  /**
   * summary for feature af
   * @param af
   * @return
   */
  private String summary(AbstractFeature af)
  {
    return summary(af, null);
  }

  /**
   * summary for feature af
   * @param af
   * @param printArgs whether or not arguments of the feature should be included in output
   * @return
   */
  private String summary(AbstractFeature af, AbstractFeature outer)
  {
    return "<div class='d-grid' style='grid-template-columns: 1fr min-content;'>"
      + "<div class='d-flex flex-wrap word-break-break-word'>"
      + "<a class='fd-anchor-sign mr-2' href='#" + htmlID(af) + "'>§</a>"
      + "<div class='d-flex flex-wrap word-break-break-word fz-code'>"
      + anchor(af)
      + arguments(af)
      + (af.isRef() ? "<div class='fd-keyword'>&nbsp;ref</div>" : "")
      + inherited(af)
      + (af.signatureWithArrow() ? "<div class='fd-keyword'>" + htmlEncodeNbsp(" => ") + "</div>" + anchor(af.resultType(), af)
        : af.isConstructor()     ? "<div class='fd-keyword'>" + htmlEncodeNbsp(" is") + "</div>"
        : af.isField()           ? "&nbsp;" + anchor(af.resultType(), outer) //+ "_af:" + af.featureName().baseName() + "_out:" + (outer != null ? outer.featureName().baseName() : "_out=null")
                                 : "")
      + annotateInherited(af, outer)
      + annotateRedef(af, outer)
      + annotateAbstract(af)
      + annotateContainsAbstract(af)
      + annotatePrivateConstructor(af)
      + annotateModule(af)
      //+ annotateInnerModules(af) // NYI: CLEANUP: for debugging only
      // fills remaining space
      + "<div class='flex-grow-1'></div>"
      + "</div>"
      + source(af)
      + "</div>";
  }

  /**
   * Returns a html formatted annotation to indicate if a feature was declared or inherited
   * @param af the feature to for which to create the annotation for
   * @param outer the feature in whose context af is used
   * @return html to annotate an inherited feature
   */
  private String annotateInherited(AbstractFeature af, AbstractFeature outer)
  {
    if (isDeclared(af, outer))
      {
        return ""; // not inherited, nothing to display
      }
    else
      {
        String anchorParent = "<a class='' href='" + featureAbsoluteURL(af.outer()) + "'>"
                              + htmlEncodedBasename(af.outer()) + "</a>";
        return "&nbsp;&nbsp;<div class='fd-parent'>[Inherited from&nbsp; <span class=fz-code>$0</span>]</div>"
          .replace("$0", anchorParent);
      }
  }

  /**
   * Checks if feature af is declared in outer
   * @param af the feature for which to check the declaration context
   * @param outer the feature in whose context af is used
   * @return true if af is declared in outer or if either of them is declared in universe
   */
  private boolean isDeclared(AbstractFeature af, AbstractFeature outer)
  {
    return (af == null || outer == null || af.outer() == outer
               // type features have their own chain of parents internally, avoid annotation in this case
            || af.outer().featureName().baseNameHuman().equals(outer.featureName().baseNameHuman()));
  }


  /**
   * Returns a html formatted annotation to indicate if a feature redefines another feature
   * @param af the feature to for which to create the annotation for
   * @return html to annotate a redefined feature
   */
  private String annotateRedef(AbstractFeature af, AbstractFeature outer)
  {
    // don't mark inherited redefinitions as redefinitions when they are not redefined in the current feature
    if (!isDeclared(af, outer) )
      {
        return "";
      }

    var redefs = af.redefines();

    return redefs.isEmpty()
            ? ""
            : "&nbsp;&nbsp;<div class='fd-parent'>[Redefinition of&nbsp; <span class=fz-code>$0</span>]</div>"
              .replace("$0", (redefs.stream()
                                    .map(f->"<a class='' href='" + featureAbsoluteURL(f) + "'>" +
                                              htmlEncodedQualifiedName(f) + "</a>")
                                    .collect(Collectors.joining(",&nbsp;")) ));
  }


  /**
   * Returns a html formatted annotation to indicate if a feature is abstract
   * @param af the feature to for which to create the annotation for
   * @return html to annotate an abstract feature
   */
  private String annotateAbstract(AbstractFeature af)
  {
    return af.isAbstract()
             ? "&nbsp;&nbsp;<div class='fd-parent' title='An abstract feature is a feature declared using ⇒ abstract. " +
               "To be able to call it, it needs to be implemented (redefined) in an heir.'>[Abstract feature]</div>"
             : "";
  }


  /**
   * Returns a html formatted annotation to mark private constructors where only type is visible
   * @param af the feature to for which to create the annotation for
   * @return html to annotate a private constructor
   */
  private String annotatePrivateConstructor(AbstractFeature af)
  {
    return af.visibility().eraseTypeVisibility() != Visi.PUB
             ? "&nbsp;<div class='fd-parent' title='This feature can not be called to construct a new instance of itself, " +
               "only the type it defines is visible.'>[Private constructor]</div>" // NYI: replace title attribute with proper tooltip
             : "";
  }


  /**
   * Returns a html formatted annotation to indicate if a feature contains inner or inherited features which are abstract
   * @param af the feature to for which to create the annotation for
   * @return html to annotate a feature containing abstract features
   */
  private String annotateContainsAbstract(AbstractFeature af)
  {
    var allInner = new List<AbstractFeature>();
    lm.forEachDeclaredOrInheritedFeature(af, f -> allInner.add(f));

    return allInner.stream().filter(f->isVisible(f)).anyMatch(f->f.isAbstract())
             ? "&nbsp;&nbsp;<div class='fd-parent' title='This feature contains inner or inherited features " +
               "which are abstract.'>[Contains abstract features]</div>"
             : "";
  }


  /**
   * Returns a html formatted annotation for features from modules other than base
   * @param af the feature to for which to create the annotation for
   * @return html to annotate a feature from other modules than base
   */
  private String annotateModule(AbstractFeature af)
  {
    var afModule = lf(af)._libModule;

    // don't add annotation for features of own module
    return afModule == lm ? "" : "&nbsp;<div class='fd-parent'>[Module " + afModule.name() + "]</div>";
  }

  // NYI: CLEANUP: for debugging only: show modules of inner features
  private String annotateInnerModules(AbstractFeature af)
  {
    String modules = lf(af).modulesOfInnerFeatures().stream().map(m -> m.name()).collect(Collectors.joining(", "));
    return "&nbsp;<div class='fd-parent'>[Inner modules: " + modules + "]</div>";
  }

  private boolean isVisible(AbstractFeature af)
  {
    var vis = af.visibility();
    return vis.typeVisibility() == Visi.PUB;

  }


  private String anchor(AbstractFeature af) {
    return "<div class='font-weight-600'>"
            + (noFeatureLink(af) ? "" : "<a class='fd-feature' href='" + featureAbsoluteURL(af) + "'>")
            + typePrfx(af) + htmlEncodedBasename(af)
            + (noFeatureLink(af) ? "" : "</a>")
            + "</div>";
  }

  /**
   * Should a hyperlink be created for feature af?
   * Fields and type parameters don't have their own page in the docs.
   * @param af the feature which should be checked
   * @return true iff there is no doc page for this feature and no hyperlink should be created
   */
  private boolean noFeatureLink(AbstractFeature af)
  {
    return af.isArgument() || af.isTypeParameter() || af.isOpenTypeParameter();
  }


  /**
   * list of features that are redefined by feature af
   * @param af
   * @return list of redefined features, as HTML
   */
  private String redefines(AbstractFeature af)
  {
    var result = "";

    if (!af.redefines().isEmpty())
      {
        result = "<div class='fd-redefines'><br />redefines: <br /><ul>" + redefines0(af) + "</ul><br /></div>";
      }

    return result;
  }


  /**
   * helper for redefines. returns the list of features that are redefined by feature
   * af. unlike redefine, which wraps the result of this in a <div></div> container, this
   * just wraps the redefined features in <li><a></a></li> tags.
   *
   * @param af
   * @return list of redefined features, wrapped in <li> and <a> HTML tags
   */
  private String redefines0(AbstractFeature af)
  {
    return af
      .redefines()
      .stream()
      .map(f -> """
        <li><a href="$1">$2</a></li>$3
      """.replace("$1", featureAbsoluteURL(f)).replace("$2", htmlEncodeNbsp(f.qualifiedName())).replace("$3", redefines0(f)))
      .collect(Collectors.joining(System.lineSeparator()));
  }


  /**
   * The summaries and the comments of the features, organized in categories
   * @param map the features to be included in the summary
   * @param outer the outer feature of the features in the summary
   * @return
   */
  private String mainSection(Map<AbstractFeature.Kind, TreeSet<AbstractFeature>> map, AbstractFeature outer)
  {
    // Type Parameters
    var typeParameters = new List<AbstractFeature>();
    typeParameters.addAll(map.getOrDefault(AbstractFeature.Kind.TypeParameter, new TreeSet<AbstractFeature>()));
    typeParameters.addAll(map.getOrDefault(AbstractFeature.Kind.OpenTypeParameter, new TreeSet<AbstractFeature>()));
    typeParameters.addAll(outer.typeArguments());

    // Fields
    var fields =  new List<AbstractFeature>();
    fields.addAll(map.getOrDefault(AbstractFeature.Kind.Field, new TreeSet<AbstractFeature>()));
    var normalArguments = outer.arguments().clone();
    normalArguments.removeIf(a->a.isTypeParameter() || a.visibility().eraseTypeVisibility() != Visi.PUB);
    fields.addAll(normalArguments);

    // Constructors
    var allConstructors =  new TreeSet<AbstractFeature>();
    allConstructors.addAll(map.getOrDefault(AbstractFeature.Kind.Routine, new TreeSet<AbstractFeature>()));
    allConstructors.removeIf(f->!f.isConstructor());

    var normalConstructors = allConstructors.stream().filter(f->!f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));
    var typeConstructors   = allConstructors.stream().filter(f->f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));

    // Functions
    var allFunctions = new TreeSet<AbstractFeature>();
    allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Routine, new TreeSet<AbstractFeature>()));
    allFunctions.removeIf(f->f.isConstructor());
    allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Abstract, new TreeSet<AbstractFeature>()));
    allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Intrinsic, new TreeSet<AbstractFeature>()));
    allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Native, new TreeSet<AbstractFeature>()));

    var normalFunctions = allFunctions.stream().filter(f->!f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));
    var typeFunctions   = allFunctions.stream().filter(f->f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));

    // Choice Types
    var choices = map.getOrDefault(AbstractFeature.Kind.Choice, new TreeSet<AbstractFeature>());

    return mainSection0("Type Parameters",   typeParameters,     outer, false)
         + mainSection0("Fields",            fields,             outer, false)
         + mainSection0("Constructors",      normalConstructors, outer, true)
         + mainSection0("Type Constructors", typeConstructors,   outer, true)
         + mainSection0("Functions",         normalFunctions,    outer, true)
         + mainSection0("Type Functions",    typeFunctions,      outer, true)
         + mainSection0("Choice Types",      choices,            outer, true);
  }


  /**
   * The summaries and the comments of the features
   * @param heading the title for this section
   * @param set the features to be included in the summary
   * @param outer the outer feature of the features in the summary
   * @param filterAndSort should features from other modules (including not having a module) be removed and the list sorted?
   * @return
   */
  private String mainSection0(String heading, Collection<AbstractFeature> set, AbstractFeature outer, boolean filterAndSort)
  {
    if (set == null) { return ""; }

    heading = "<h4>" + heading + "</h4>\n";
    var features = set.stream();

    // e.g. don't filter or sort type parameters and fields
    if (filterAndSort)
      {
        features = features.filter(af -> lf(af).showInMod(lm))  // filter out features of other modules which do not need to be shown for this module
                           .sorted((af1, af2) -> af1.featureName().baseName().compareToIgnoreCase(af2.featureName().baseName()));
      }

    var content = features.map(af ->
      // NYI summary tag must not contain div
      "<details id='" + htmlID(af)
      + "'$0><summary>$1</summary><div class='fd-comment'>$2</div>$3</details>"
        // NYI rename fd-private?
        .replace("$0", (config.ignoreVisibility() && !Util.isVisible(af)) ? "class='fd-private cursor-pointer' hidden" : "class='cursor-pointer'")
        .replace("$1",
          summary(af, outer))
        .replace("$2", Util.commentOf(af))
        .replace("$3", redefines(af))
    )
    .collect(Collectors.joining(System.lineSeparator()));

    return content.equals("") ? "" : heading + content;
  }


  /**
   * the heading section for feature
   * @param f
   * @return
   */
  private String headingSection(AbstractFeature f)
  {
    return "<h1 class='$5'>$0</h1><h2>$3</h2><h3>$1</h3><div class='fd-comment'>$2</div>$6"
      .replace("$0", f.isUniverse() ? "API-Documentation: module <code style=\"font-size: 1.4em; vertical-align: bottom;\">" + lm.name() + "</code>" : htmlEncodedBasename(f))
      .replace("$3", anchorTags(f))
      .replace("$1", f.isUniverse() ? "": summary(f))
      .replace("$2", Util.commentOf(f))
      .replace("$5", f.isUniverse() ? "": "d-none")
      .replace("$6", redefines(f));
  }

  /**
   * the html encoded basename of the feature af
   * @param af
   * @return
   *
   */
  private String htmlEncodedBasename(AbstractFeature af)
  {
    return htmlEncodeNbsp(af.featureName().baseNameHuman());
  }


  /**
   * the html encoded qualified name of the feature af
   * @param af
   * @return
   *
   */
  private String htmlEncodedQualifiedName(AbstractFeature af)
  {
    return htmlEncodeNbsp(af.qualifiedName());
  }


  /**
   * the link [src] to the source file
   */
  private static String source(AbstractFeature feature)
  {
    return "<div class='pl-5'><a href='$1'>[src]</a></div>"
      .replace("$1", featureURL(feature));
  }


  /**
   * process the comment of a feature, in particular detects lines indented
   * five spaces relative to the # as code blocks and puts them into a runcode
   * box.
   *
   * @param name the name of the feature whose comment is being processed
   * @param s the comment that is being processed
   * @return the comment wrapped in HTML
   */
  static String processComment(String name, String s)
  {
    var codeNo = new Integer[]{0};
    var codeLines = new ArrayList<String>();
    var resultLines = new ArrayList<String>();

    s.lines().forEach(l ->
      {
        if (l.startsWith("    "))
          {
            /* code comment */
            codeLines.add(l);
          }
        else if (l.isBlank())
          {
            /* avoid adding lots of line breaks after code comments */
            if (codeLines.isEmpty())
              {
                resultLines.add(l);
              }
          }
        else
          {
            addCodeLines(name, codeNo, codeLines, resultLines);

            /* treat as normal line */
            var replacedLine = htmlEncode(l, false);

            resultLines.add(replacedLine);
          }
      });

    addCodeLines(name, codeNo, codeLines, resultLines);

    return resultLines.stream().collect(Collectors.joining("<br />"));
  }


  /*
   * add codeLines to resultLines if there are any.
   */
  private static void addCodeLines(String name, Integer[] codeNo, ArrayList<String> codeLines,
    ArrayList<String> resultLines)
  {
    if (!codeLines.isEmpty())
      {
        /* dump codeLines into a fuzion-lang.dev runcode box */
        var id = "fzdocs." + name + codeNo[0];
        var code = codeLines
          .stream()
          .map(cl -> { return cl.replaceAll("^    ", ""); })
          .collect(Collectors.joining(System.lineSeparator()));
        resultLines.add(RUNCODE_BOX_HTML.replace("##ID##", id).replace("##CODE##", code));
        codeLines.clear();
        codeNo[0]++;
      }
  }


  private static String htmlEncode(String s, boolean spacesNoneBreaking)
  {

    Pattern p = Pattern.compile("(&|\"|'|<|>|\r|\n|\t|\\s)");
    Matcher m = p.matcher(s);
    StringBuffer sb = new StringBuffer();
    while (m.find())
      {
        switch (s.charAt(m.toMatchResult().start()))
          {
          case '&' :
            m.appendReplacement(sb, "&amp;");
            break;
          case '"' :
            m.appendReplacement(sb, "&quot;");
            break;
          case '\'' :
            m.appendReplacement(sb, "&#39;");
            break;
          case '<' :
            m.appendReplacement(sb, "&lt;");
            break;
          case '>' :
            m.appendReplacement(sb, "&gt;");
            break;
          case '\r' :
            break;
          case '\n' :
            m.appendReplacement(sb, "<br />");
            break;
          case ' ' :
            if (spacesNoneBreaking)
              {
                m.appendReplacement(sb, "&nbsp;");
              }
            else
              {
                m.appendReplacement(sb, " ");
              }
            break;
          default:
            throw new Error("unexpected match");
          }
      }
    m.appendTail(sb);
    return sb.toString();
  }


  /*
   * html encode this string with non breaking spaces
   */
  private static String htmlEncodeNbsp(String s)
  {
    return htmlEncode(s, true);
  }


  /**
   * get full html with doctype, head and body
   * @param af
   * @param bareHtml
   * @return
   */
  private static String fullHtml(String qualifiedName, String bareHtml)
  {
    return ("""
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="utf-8" />
        <title>Fuzion Docs - $qualifiedName</title>
        <link rel="stylesheet" type="text/css" href="/style.css" />
      </head>
      <body>""" +
      bareHtml
      + """
        </body>
        </html>
                    """)
        .replace("$qualifiedName", qualifiedName);
  }


  /**
   * the unique id used in html for feature af
   * @param f
   * @return
   */
  private static String htmlID(AbstractFeature f)
  {
    return urlEncode(f.qualifiedName() + "_" + f.arguments().size());
  }


  /**
   * the URL of the feature af
   * @param f
   * @return
   */
  private static String featureURL(AbstractFeature f)
  {
    return f.pos()._sourceFile._fileName
      .toString()
      .replace(FuzionConstants.SYMBOLIC_FUZION_MODULE.toString(), DocsOptions.baseApiDir)
      + "#l" + f.pos().line();
  }


  /**
   * url encode this string
   * @param s
   * @return
   */
  private static String urlEncode(String s)
  {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }


  /**
   * the absolute URL of this feature
   */
  private String featureAbsoluteURL(AbstractFeature f)
  {
    return config.docsRoot() + "/" + lm.name() + featureAbsoluteURL0(f) + "/";
  }

  private static String featureAbsoluteURL0(AbstractFeature f)
  {
    if (f.isUniverse())
      {
        return "";
      }
    if (f.isCotype())
      {
        return featureAbsoluteURL0(f.cotypeOrigin());
      }
    else
      {
        String prefix = f.outer().isCotype() ? "type.": "";
        return featureAbsoluteURL0(f.outer()) + "/" + prefix + urlEncode(f.featureName().toString());
      }
  }

  /**
   * arguments of this feature
   * @param f
   * @return
   */
  private String arguments(AbstractFeature f)
  {
    if (f.arguments()
         .stream()
         .filter(a -> a.isTypeParameter() || (f.visibility().eraseTypeVisibility() == Visi.PUB))
         .count() == 0)
      {
        return "";
      }
    return "(" + f.arguments()
      .stream()
      .filter(a -> a.isTypeParameter() || (f.visibility().eraseTypeVisibility() == Visi.PUB))
      .map(a ->
        htmlEncodedBasename(a) + "&nbsp;"
        + (a.isTypeParameter() ? typeArgAsString(a) : anchor(a.resultType(), f)))
      .collect(Collectors.joining(htmlEncodeNbsp(", "))) + ")";
  }


  private String typeArgAsString(AbstractFeature f)
  {
    if (f.resultType().dependsOnGenerics())
      {
        return "<div class='fd-keyword'>type</div> <span class='mx-5'>:</span>" + htmlEncodeNbsp(f.resultType().asString());
      }
    return "<div class='fd-keyword'>type</div>";
  }


  /**
   * render the navigation at the left side
   */
  private String navigation(AbstractFeature start, int depth)
  {
    var declaredFeatures = lm.declaredFeatures(start);
    if (declaredFeatures == null || start.isArgument())
      {
        return "";
      }
    var spacer = IntStream.range(0, depth)
        .mapToObj(i -> "| ")
        .collect(Collectors.joining())
        .replaceAll("\s$", "―");
    var startName = htmlEncodedBasename(start) + (start.isUniverse() ? " (module " + lm.name() + ")" : "");
    var f =  spacer + "<a href='" + featureAbsoluteURL(start) + "'>" + startName + args(start) + "</a>";

    var constructors = declaredFeatures.values().stream()
                        .filter(ft -> ft.definesType()
                                    && ft.visibility().typeVisibility() == Visi.PUB)
                        .collect(Collectors.toList());

    // list modules at the top
    String modules = start.isUniverse() ? navigationModules() : "";

    return """
      $2
      <ul class="white-space-no-wrap">
        <li>
          <div>$0</div>
          $1
        </li>
      </ul>"""
        .replace("$0", f)
        .replace("$1",
            (constructors.isEmpty()
              ? ""
              : constructors.stream()
                .sorted(Comparator.comparing(ft -> ft.featureName().baseName(), String.CASE_INSENSITIVE_ORDER))
                .map(af -> navigation(af, depth + 1))
                .collect(Collectors.joining(System.lineSeparator()))))
        .replace("$2", modules);
  }

  /**
   * render list with modules for the navigation at the left side
   */
  private String navigationModules()
  {
    return """
      <ul class="white-space-no-wrap">
        <li>
          <div><a href=$0>Modules</a></div>
            <ul style="list-style: circle inside">
              $1
      </ul></li></ul>"""
      .replace("$1", libModules.stream()
                               .map(m->"<li><a href=$0" + m.name() + ">" + m.name() + "</a></li>")
                               .collect(Collectors.joining("\n")))
      .replace("$0", config.docsRoot() + "/");
  }


  private String args(AbstractFeature start)
  {
    if (start.valueArguments().size() == 0 || (start.isChoice() || start.visibility().eraseTypeVisibility() != Visi.PUB))
      {
        return "";
      }
    if (start.valueArguments().size() == 1)
      {
        return " <small>(" + start.valueArguments().size() + " arg)</small>";
      }
    return " <small>(" + start.valueArguments().size() + " args)</small>";
  }


  /**
   * Cast an AbstractFeature to LibraryFeature
   * @param af an AbstractFeature feature which must be of type LibraryFeature
   * @return
   */
  private static final LibraryFeature lf(AbstractFeature af)
  {
    return (LibraryFeature) af;
  }


  /*-----------------------------  public methods  -----------------------------*/


  /**
   * the full content
   * @return
   */
  String content(AbstractFeature af)
  {
    var bareHtml =
      """
          <!-- GENERATED BY FZDOCS -->
          <div class='fd'>
            <div class="sidenav">
              <div onclick="document.querySelector('.fd .sidenav nav').style.display = (document.querySelector('.fd .sidenav nav').style.display === 'none' ?  '' : 'none');" class="toggle-nav cursor-pointer">☰</div>
              <nav style="display: none">$2</nav>
            </div>
            <div class="container">
              <section>$0</section>
              <section>$1</section>
              $3
            </div>
          </div>
        """
        .replace("$0", headingSection(af))
        .replace("$1", mainSection(mapOfDeclaredFeatures.get(af), af))
        .replace("$2", navigation)
        .replace("$3", config.ignoreVisibility() ? """
          <button onclick="for (let element of document.getElementsByClassName('fd-private')) { element.hidden = !element.hidden; }">Toggle hidden features</button>
        """ : "");
    return config.bare() ? bareHtml: fullHtml(af.qualifiedName(), bareHtml);
  }

  /**
   * The Module Page
   * @return
   */
  String modulePage()
  {
    // NYI: BUG: some things (e.g. html id or links) might break if there are spaces in a module name
    StringBuilder modPage = new StringBuilder();
    modPage.append("""
<!-- GENERATED BY FZDOCS -->
<div class="fd">
<div class="sidenav">
  <div onclick="document.querySelector('.fd .sidenav nav').style.display = (document.querySelector('.fd .sidenav nav').style.display === 'none' ?  '' : 'none');" class="toggle-nav cursor-pointer">☰</div>
  <nav style="display: none">$0</nav>
</div>
<div class="container">
  <section><h1>Fuzion Library Modules</h1>
    <h2></h2><h3></h3><div class='fd-comment'></div>
  </section>
  <section>
        """.replace("$0", navigationModules()));

    for (LibraryModule mod : libModules)
      {
        modPage.append("""
    <div class="cursor_pointer">
      <details id='"$2"'$0>
        <summary>
          <div class="d-grid" style="grid-template-columns: 1fr min-content;">
            <div class="d-flex flex-wrap word-break-break-word">
              <a class="fd-anchor-sign mr-2" href="#$2">§</a>
              <div class="d-flex flex-wrap word-break-break-word fz-code">
                <div class="font-weight-600"><a class="fd-feature" href="$1">$2</a></div>
                <div class="flex-grow-1"></div>
              </div>
            </div>
          </div>
        </summary>
      </details>
    </div>
            """.replace("$0", config.ignoreVisibility() ? "class='fd-private cursor-pointer' hidden" : "class='cursor-pointer'")
               .replace("$1", mod.name())
               .replace("$2", mod.name()));
      }
    // modulePage += "</ul>";

    modPage.append("""
  </section>
  $3
</div>
</div>
        """
          .replace("$3", config.ignoreVisibility() ? """
            <button onclick="for (let element of document.getElementsByClassName('fd-private')) { element.hidden = !element.hidden; }">Toggle hidden features</button>
          """ : ""));

    return config.bare() ? modPage.toString(): fullHtml("Modules", modPage.toString());
  }


}
