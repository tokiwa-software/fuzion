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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;
import dev.flang.tools.Tool;
import dev.flang.util.ANY;
import dev.flang.util.List;


public class Html extends ANY
{
  final DocsOptions config;
  private final Map<AbstractFeature, Map<AbstractFeature.Kind,TreeSet<AbstractFeature>>> mapOfDeclaredFeatures;
  private final String navigationBareHTML;
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

    // cache navigation if URLs absolute to webserver root are used
    navigationBareHTML = config.bare() ? navigation(universe, null) : null;
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



  /**
   * Does this feature have an arrow "=>" in it's signature, i.e. is a function or an intrinsic
   * @return true if the signature contains an arrow "=>"
   */
  private static boolean signatureWithArrow(AbstractFeature af)
  {
    return (af.isRoutine() && !af.isConstructor()) || af.isIntrinsic() || af.isAbstract() || af.isNative();
  }


  /*
   * html containing the inherited features of af or constraint in case of a type parameter
   */
  private String inherited(AbstractFeature af, AbstractFeature relativeTo)
  {
    if (af.inherits().isEmpty() || signatureWithArrow(af)) // don't show inheritance for function features
      {
        return "";
      }
    else if (af.kind() == AbstractFeature.Kind.TypeParameter || af.kind() == AbstractFeature.Kind.OpenTypeParameter)
      {
        var constraint = af.resultType().feature();
        return "<div class='fd-keyword mx-5'>:</div><a class='fd-feature fd-inherited' href='$1'>$2</a>"
          .replace("$1", featureRelativeURL(constraint, relativeTo))
          .replace("$2", htmlEncodedQualifiedName(constraint));
      }
    else
      {
        return "<div class='fd-keyword mx-5'>:</div>" + af.inherits()
          .stream()
          .<String>map(c -> {
            var f = c.calledFeature();
            return "<a class='fd-feature fd-inherited' href='$1'>".replace("$1", featureRelativeURL(f, relativeTo))
              + htmlEncodedBasename(f)
              + (c.actualTypeParameters().size() > 0 ? "&nbsp;" : "")
              + c.actualTypeParameters().stream()
                 .map(at -> htmlEncodeNbsp(at.toString(false, af)))
                 .collect(Collectors.joining(", ")) + "</a>";
          })
          .collect(Collectors.joining("<span class='mr-2 fd-keyword'>,</span>"));
      }
  }


  /**
   * anchor tag for type
   * @param at
   * @param context the feature to which the name should be relative to, full qualified name if null
   * @return
   */
  private String anchorType(AbstractFeature af, AbstractFeature context, AbstractFeature relativeTo)
  {
    var at = af.resultType();
    if (at.isGenericArgument())
      {
        return htmlEncodeNbsp(at.toString(false, context))
               + (at.isOpenGeneric() ? "..." : "");
      }
    return "<a class='fd-type' href='$2'>$1</a>".replace("$1", htmlEncodeNbsp(at.toString(false, context)))
      .replace("$2", featureRelativeURL(at.feature(), relativeTo));
  }


  /**
   * anchor tags for feature
   * eg: <a>outer outer basename</a>.<a>outer basename</a>.<a>this basename</a>
   */
  private String anchorTags(AbstractFeature f)
  {
    return anchorTags0(f, f).collect(Collectors.joining("."));
  }

  private Stream<String> anchorTags0(AbstractFeature f, AbstractFeature relativeTo)
  {
    if (f.isUniverse())
      {
        return Stream.empty();
      }
    return Stream.concat(anchorTags0(f.outer(), relativeTo),
      Stream.of(typePrfx(f) + "<a class='fd-feature font-weight-600' href='$2'>$1</a>".replace("$1", htmlEncodedBasename(f))
        .replace("$2", featureRelativeURL(f, relativeTo))));
  }


  /**
   * Return "type." prefix if af is a type feature.
   * @param af the feature to be checked
   * @return html for "type." prefix if type feature, empty string otherwise
   */
  private String typePrfx(AbstractFeature af)
  {
    // NYI: does not treat features that `Type` inherits but does not redefine as type features, see #3716
    return af.outer() != null && (af.outer().isCotype() || af.outer().compareTo(Types.resolved.f_Type) == 0) && !af.isCotype() ? "<span class=\"fd-keyword\">type</span>." : "";
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
   * @return
   */
  private String summary(AbstractFeature af, AbstractFeature outer)
  {
    AbstractFeature relativeTo = outer != null ? outer : af;

    return
      "<div class='d-grid' style='grid-template-columns: 1fr min-content;'>"
        + "<div class='d-flex flex-wrap word-break-break-word'>"
          + "<div class='d-flex flex-wrap word-break-break-word fz-code'>"
            + anchor(af, relativeTo)
            + arguments(af, (outer != null ? outer : af))
            + (af.isRef() ? "<div class='fd-keyword'>&nbsp;ref</div>" : "")
            + inherited(af, relativeTo)
            + (signatureWithArrow(af) ? "<div class='fd-keyword'>" + htmlEncodeNbsp(" => ") + "</div>" + anchorType(af, af, relativeTo)
               : af.isConstructor()   ? "<div class='fd-keyword'>" + htmlEncodeNbsp(" is") + "</div>"
               : af.isField()         ? "&nbsp;" + anchorType(af, outer, relativeTo) //+ "_af:" + af.featureName().baseName() + "_out:" + (outer != null ? outer.featureName().baseName() : "_out=null")
                                      : "")
            + annotateInherited(af, outer)
            + annotateRedef(af, outer)
            + annotateAbstract(af)
            + annotateContainsAbstract(af)
            + annotatePrivateConstructor(af)
            + annotateModule(af)
            + "<a class='fd-anchor-sign mr-2' href='#" + htmlID(af) + "'>¶</a>"
            // fills remaining space
            + "<div class='flex-grow-1'></div>"
          + "</div>"
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
        AbstractFeature relativeTo = outer != null ? outer : af;
        String anchorParent = "<a class='' href='" + featureRelativeURL(af.outer(), relativeTo) + "'>"
                              + htmlEncodedBasename(af.outer()) + "</a>";
        return "<div class='fd-parent ml-10'>[Inherited from&nbsp; <span class=fz-code>$0</span>]</div>"
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

    AbstractFeature relativeTo = outer != null ? outer : af;
    return redefs.isEmpty()
            ? ""
            : "<div class='fd-parent ml-10'>[Redefinition of&nbsp; <span class=fz-code>$0</span>]</div>"
              .replace("$0", (redefs.stream()
                                    .map(f->"<a class='' href='" + featureRelativeURL(f, relativeTo) + "'>" +
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
             ? "<div class='fd-parent ml-10' title='An abstract feature is a feature declared using ⇒ abstract. " +
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
    return af.visibility().eraseTypeVisibility() != Visi.PUB && af.isConstructor()
             ? "<div class='fd-parent ml-10' title='This feature can not be called to construct a new instance of itself, " +
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

    return allInner.stream()
        .filter(f->isVisible(f))
        .filter(f->f instanceof LibraryFeature lf && lm.sameOrDependent(lf._libModule))
        .anyMatch(f->f.isAbstract())
             ? "<div class='fd-parent ml-10' title='This feature contains inner or inherited features " +
               "which are abstract.'>[Contains abstract features]</div>" // NYI: replace title attribute with proper tooltip
             : "";
  }


  /**
   * Returns a html formatted annotation for features from modules other than base
   * @param af the feature to for which to create the annotation for
   * @return html to annotate a feature from other modules than base
   */
  private String annotateModule(AbstractFeature af)
  {
    var afModule = libModule(af);

    // don't add annotation for features of own module
    return afModule == lm ? "" : "<div class='fd-parent ml-10'>[Module " + afModule.name() + "]</div>";
  }

  private boolean isVisible(AbstractFeature af)
  {
    return af.visibility().typeVisibility() == Visi.PUB;
  }


  private String anchor(AbstractFeature af, AbstractFeature relativeTo) {
    return "<div class='font-weight-600 ml-2'>"
            + (noFeatureLink(af) ? "" : "<a class='fd-feature' href='" + featureRelativeURL(af, lm, relativeTo) + "'>")
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
  private String redefines(AbstractFeature af, AbstractFeature relativeTo)
  {
    var result = "";

    if (!af.redefines().isEmpty())
      {
        result = "<div class='fd-redefines'><br />redefines: <br /><ul>" + redefines0(af, relativeTo) + "</ul><br /></div>";
      }

    return result;
  }


  /**
   * helper for redefines. returns the list of features that are redefined by feature
   * af. unlike redefine, which wraps the result of this in a {@code <div></div>} container, this
   * just wraps the redefined features in {@code <li><a></a></li>} tags.
   *
   * @param af
   * @return list of redefined features, wrapped in {@code <li>} and {@code <a>} HTML tags
   */
  private String redefines0(AbstractFeature af, AbstractFeature relativeTo)
  {
    return af
      .redefines()
      .stream()
      .map(f -> """
        <li><a href="$1">$2</a></li>$3
      """.replace("$1", featureRelativeURL(f, relativeTo)).replace("$2", htmlEncodeNbsp(f.qualifiedName())).replace("$3", redefines0(f, relativeTo)))
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
    // it's not possible to get an instance of a function feature, so no fields can not be accessed from outside
    if (!signatureWithArrow(outer))
      {
        fields.addAll(map.getOrDefault(AbstractFeature.Kind.Field, new TreeSet<AbstractFeature>()));
        var normalArguments = outer.arguments().clone();
        normalArguments.removeIf(a->a.isTypeParameter() || a.visibility().eraseTypeVisibility() != Visi.PUB);
        fields.addAll(normalArguments);
      }

    // Constructors
    var allConstructors =  new TreeSet<AbstractFeature>();
    allConstructors.addAll(map.getOrDefault(AbstractFeature.Kind.Routine, new TreeSet<AbstractFeature>()));
    allConstructors.removeIf(f->!f.isConstructor());

    var normalConstructors = allConstructors.stream().filter(f->!f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));
    var typeConstructors   = allConstructors.stream().filter(f->f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));

    // Functions
    var allFunctions = new TreeSet<AbstractFeature>();
    // it's not possible to get an instance of a function feature, so no features can be called on it
    if (!signatureWithArrow(outer))
      {
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Routine, new TreeSet<AbstractFeature>()));
        allFunctions.removeIf(f->f.isConstructor());
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Abstract, new TreeSet<AbstractFeature>()));
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Intrinsic, new TreeSet<AbstractFeature>()));
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Native, new TreeSet<AbstractFeature>()));
      }

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
        features = features
                    // filter out features of other modules which do not need to be shown for this module
                    .filter(af -> (af instanceof LibraryFeature lf ? lf.showInMod(lm) : false))
                    .sorted((af1, af2) -> af1.featureName().baseName().compareToIgnoreCase(af2.featureName().baseName()));
      }

    var content = features.map(af ->
      // NYI: UNDER DEVELOPMENT: summary tag must not contain div
      "<details id='" + htmlID(af)
      + "' $0><summary>$1</summary><div class='fd-comment'>$2</div>$3</details>"
        // NYI: UNDER DEVELOPMENT: rename fd-private?
        .replace("$0", (config.ignoreVisibility() && !Util.isVisible(af)) ? "class='fd-private cursor-pointer' hidden" : "class='cursor-pointer'")
        .replace("$1", summary(af, outer))
        .replace("$2", Util.commentOf(af))
        .replace("$3", redefines(af, (outer != null ? outer : af)))
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
    return "$0<h1 class='$1'>$2</h1><h2>$3</h2><div class='heading-summary'>$4</div><div class='fd-comment'>$5</div>$6"
      .replace("$0", f.isUniverse() ? "<h1 hidden>" + lm.name() + "</h1>" : "") // short version of title for navtitle
      .replace("$1", f.isUniverse() ? "": "d-none")
      .replace("$2", f.isUniverse() ? "API-Documentation: module <code style=\"font-size: 1.4em; vertical-align: bottom;\">" + lm.name() + "</code>" : htmlEncodedBasename(f))
      .replace("$3", anchorTags(f))
      .replace("$4", f.isUniverse() ? "": summary(f))
      .replace("$5", Util.commentOf(f))
      .replace("$6", redefines(f, f));
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
  private String source(AbstractFeature feature)
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


    String prevLine = "not empty";
    boolean inCodeblock = false;

    for (var l : s.lines().collect(Collectors.toList()))
      {
        if (l.startsWith("    ") && (prevLine.isBlank() || inCodeblock))
          {
            inCodeblock = true;

            /* code comment */
            codeLines.add(l);
          }
        else if (l.isBlank())
          {
            inCodeblock = false;

            /* avoid adding lots of line breaks after code comments */
            if (codeLines.isEmpty())
              {
                resultLines.add(l);
              }
          }
        else
          {
            inCodeblock = false;

            addCodeLines(name, codeNo, codeLines, resultLines);

            /* treat as normal line */
            var replacedLine = htmlEncode(l, false);

            resultLines.add(replacedLine);
          }
        prevLine = l;
      }

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
        var id = ("fzdocs." + name + codeNo[0]).replace(" ", ""); // NYI: CLEANUP: better not to add spaces in the first place
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
   * @param qualifiedName
   * @param bareHtml
   * @return
   */
  private static String fullHtml(String qualifiedName, String bareHtml)
  {
    int upDirCorrection = qualifiedName.equals("Modules") || qualifiedName.endsWith(".universe") ? 0 : 1;

    return ("""
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="utf-8" />
        <title>$qualifiedName | Fuzion Docs</title>
        <link rel="icon" sizes="32x32" href="$root32.png">
        <link rel="stylesheet" type="text/css" href="$rootstyle.css" />
      </head>
      <body>""" +
      bareHtml
      + """
        </body>
        </html>
                    """)
        .replace("$qualifiedName", String.join(" • ", java.util.List.of(qualifiedName.split("\\.")).reversed()))
        .replace("$root", upDirs((int) qualifiedName.chars().filter(c -> c == '.').count() + upDirCorrection));
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
  private String featureURL(AbstractFeature f)
  {
    return f.pos()._sourceFile._fileName
      .toString()
      .replaceFirst("\\{(.*?)\\.fum\\}", config.apiSrcDir() + "/$1")
      + "#l" + f.pos().line();
  }


  /**
   * url encode this string
   * @param s
   * @return
   */
  private static String urlEncode(String s)
  {
    var tmp = Docs.nonAsciiPattern
      .matcher(s)
      .replaceAll(match ->String.format("U+%04X", match.group().codePointAt(0)));
    return URLEncoder
      .encode(tmp, StandardCharsets.UTF_8);
  }

  /**
   * The URL of the feature
   * for full HTML URLs are relative, for bare HTML they are absolute
   * @param f the feature for which to create the relative URL
   * @param relativeTo the feature to which the URL should be relative to, ignored when bare HTML is generated
   * @return URL of feature f
   */
  private String featureRelativeURL(AbstractFeature f, AbstractFeature relativeTo)
  {
    return featureRelativeURL(f, null, relativeTo);
  }

  /**
   * The URL of the feature
   * for full HTML URLs are relative, for bare HTML they are absolute
   * @param f the feature for which to create the relative URL
   * @param module the module to which the link should point, e.g. for base module feature String there are different
   *               pages in base (docs/base/String+(no+arguments)/) and terminal (docs/terminal/String+(no+arguments)/)
   * @param relativeTo the feature to which the URL should be relative to, ignored when bare HTML is generated
   * @return URL of feature f
   */
  private String featureRelativeURL(AbstractFeature f, LibraryModule lm, AbstractFeature relativeTo)
  {
    String result;

    if (config.bare())
      {
        // use absolute URLs on the website to avoid links breaking
        // e.g. these two URLs show the exact same page, but a link starting with ../ behaves differently
        // https://fuzion-lang.dev/docs/base/Any+(no+arguments)
        // https://fuzion-lang.dev/docs/base/Any+(no+arguments)/index.html
        result = lm == null ? featureAbsoluteURL(f) : featureAbsoluteURL(f, lm);
      }
    else
      {
        if (relativeTo == null)
          {
            result = lm == null ? featureAbsoluteURL(f) : featureAbsoluteURL(f, lm);
          }
        else
          {
            String absURL = lm == null ? featureAbsoluteURL(f) : featureAbsoluteURL(f, lm);
            String absRef = lm == null ? featureAbsoluteURL(relativeTo) : featureAbsoluteURL(relativeTo, lm);

            result = relativePath(absURL, absRef);
          }
      }
    return result;
  }

  /**
   * Make a path relative based on another one
   * e.g. /foo/bar/file_1 based on /foo/other results in ../bar/file_1
   *
   * @param absolutePath the path that should be made relative
   * @param relativeTo the path to which it should be made relative to
   * @return a relative path
   */
  private String relativePath(String absoluteURL, String relativeTo)
  {
    // relative path should not be used when bare HTML with absolute paths is generated
    if (CHECKS) check
      (!config.bare());

    var u = new LinkedList<>(Arrays.asList(absoluteURL.split("/")));
    var r = new LinkedList<>(Arrays.asList(relativeTo.split("/")));
    while (!u.isEmpty() && !r.isEmpty() && u.get(0).equals(r.get(0)))
      {
        u.remove(0);
        r.remove(0);
      }

    String path = String.join("/", u);

    return upDirs(r.size()) + path + (path.isEmpty() ? "" : "/") + "index.html";
  }

  /**
   * Create a String to go up n directories
   */
  private static String upDirs(int n)
  {
    if (PRECONDITIONS) require
      (n >= 0);

    return n == 0 ? "./" : "../".repeat(n);
  }

  /**
   * the absolute URL of this feature
   */
  private String featureAbsoluteURL(AbstractFeature f)
  {
    return config.docsRoot() + "/" + libModule(f).name() + featureAbsoluteURL0(f) + "/";
  }

  /**
   * the absolute URL of this feature in the given module
   * @param module the module to which the link should point, e.g. for base module feature String there are different
   *               pages in base (docs/base/String+(no+arguments)/) and terminal (docs/terminal/String+(no+arguments)/)
   */
  private String featureAbsoluteURL(AbstractFeature f, LibraryModule module)
  {
    return config.docsRoot() + "/" + module.name() + featureAbsoluteURL0(f) + "/";
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
  private String arguments(AbstractFeature f, AbstractFeature relativeTo)
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
        + (a.isTypeParameter() ? typeArgAsString(a, relativeTo) : anchorType(a, f, relativeTo)))
      .collect(Collectors.joining(htmlEncodeNbsp(", "))) + ")";
  }


  private String typeArgAsString(AbstractFeature f, AbstractFeature relativeTo)
  {
    if (f.resultType().dependsOnGenerics())
      {
        return "<div class='fd-keyword'>type</div>"
               + (f.isOpenTypeParameter() ? "..." : "")
               + "<span class='mx-5'>:</span>" + htmlEncodeNbsp(f.resultType().toString(true));
      }
    else
      {
        var constraint = f.resultType().feature();

        return "<div class='fd-keyword'>type</div>"
                + (f.isOpenTypeParameter() ? "..." : "")
                + (f.resultType().compareTo(Types.resolved.t_Any) == 0 ? "" :
                    "<div class='mx-5'>:</div><a class='fd-feature fd-inherited' href='$1'>$2</a>"
                    .replace("$1", featureRelativeURL(constraint, relativeTo))
                    .replace("$2", htmlEncodedQualifiedName(constraint)));
      }
  }


  /**
   * render the navigation at the left side
   * @param start feature from which to start the list of features
   * @return html for the navigation, consisting of a list of modules and a list of features from the current module
   */
  private String navigation(AbstractFeature start, AbstractFeature relativeTo)
  {
    return config.bare() && navigationBareHTML != null
      ? navigationBareHTML  // cached navigation if absolute URLs are used
      : navigationModules(relativeTo) + navigationFeatures(java.util.List.of(start), "", relativeTo);
  }


  /**
   * render the tree style list of (constructor)features for the navigation on the left side
   * @param features    features that should be contained in the same block
   * @param outerPrefix prefix for the tree structure e.g. "│  │  "
   * @return rendered tree style block with sub blocks for inner features
   */
  private String navigationFeatures(java.util.List<AbstractFeature> features, String outerPrefix, AbstractFeature relativeTo)
  {
    if (features.isEmpty())
      {
        return ""; // nothing to do if list is empty, e.g. a feature has no inner features
      }

    var sb = new StringBuilder();
    var iter = features.iterator();
    do
      {
        var f = iter.next();

        var innerFeatures = lm.declaredFeatures(f).values().stream()
                              .filter(ft -> ft.definesType()
                                            && ft.visibility().typeVisibility() == Visi.PUB)
                              .sorted(Comparator.comparing(ft -> ft.featureName().baseName(), String.CASE_INSENSITIVE_ORDER))
                              .collect(Collectors.toList());

        // addition to the tree structure prefix for current feature: universe / normal element / last element
        var featPrfx = f.isUniverse() ? ""
                                      : iter.hasNext() ? "├─<span class=space-1></span>"
                                                       : "└─<span class=space-1></span>";

        // addition to the tree structure prefix for inner features of current feature: universe / normal element / last element
        var subPrfx  = f.isUniverse() ? ""
                                      : iter.hasNext() ? "│<span class=space-2></span>"
                                                       : "<span class=space-3></span>";

        sb.append(
          """

          <li>$0$1</li>"""
            .replace("$0", navFeatHtml(f, outerPrefix + featPrfx, relativeTo))
            .replace("$1", navigationFeatures(innerFeatures, outerPrefix + subPrfx, relativeTo)));
      }
    while (iter.hasNext());

    return """

      <ul class="white-space-no-wrap">$0
      </ul>"""
        .replace("$0", sb.toString());
  }

  /**
   * generate html for a single feature in the tree style navigation on the left side
   * @param f feature for which to generate the html for
   * @param prefix prefix of the tree style structure for this feature
   * @return rendered html for the feature f
   */
  private String navFeatHtml(AbstractFeature f, String prefix, AbstractFeature relativeTo)
  {
    var fName = htmlEncodedBasename(f) + (f.isUniverse() ? " (module " + lm.name() + ")" : "");
    var fHTML = "<a href='" + featureRelativeURL(f, relativeTo) + "'>" + fName + args(f) + "</a>";
    return "<div>" + prefix + fHTML + "</div>";
  }


  /**
   * render list with modules for the navigation at the left side
   */
  private String navigationModules(AbstractFeature relativeTo)
  {
    String relativeToStr = relativeTo == null ? "" : featureAbsoluteURL(relativeTo);

    return """
      <ul class="white-space-no-wrap">
        <li>
          <div><a href=$0>Modules</a></div>
            <ul style="list-style: disc inside">
              $1
            </ul>
        </li>
      </ul>
      """
      .replace("$1", libModules.stream()
                               .map(m->m.name())
                               .map(s->(config.bare()
                                          ? ("<li><a href=" + config.docsRoot() + "/" + s + ">" + s + "</a></li>")
                                          : ("<li><a href=" + relativePath("/" + s, relativeToStr) + ">" + s + "</a></li>")))
                               .collect(Collectors.joining("\n")))
      .replace("$0", config.bare() ? config.docsRoot() + "/" : relativePath("", relativeToStr));
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
   * For LibraryFeatures return the features LibraryModule
   * otherwise the LibraryModule of this HTML object
   */
  private final LibraryModule libModule(AbstractFeature f)
  {
    return f instanceof LibraryFeature lf ? lf._libModule : lm;
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
            <div class="sidenav" id="sidenav">
              <button
                class="toggle-nav"
                aria-expanded="true"
                aria-controls="sidenav"
                aria-label="Toggle navigation"
                onclick="
                  const nav = document.querySelector('.fd .sidenav nav');
                  const hidden = nav.style.display === 'none';
                  nav.style.display = hidden ? '' : 'none';
                  this.textContent = hidden ? '«' : '»';
                  this.setAttribute('aria-expanded', hidden);
                "
              >
                »
              </button>
              <nav style="display: none">$2</nav>
            </div>
            <div class="container">
              <section>$0</section>
              <section>$1</section>
              $3
            </div>
          </div>
          <div class=version-hash>$4</div>
        """
        .replace("$0", headingSection(af))
        .replace("$1", mainSection(mapOfDeclaredFeatures.get(af), af))
        .replace("$2", navigation(lm.universe(), af))
        .replace("$3", config.ignoreVisibility() ? """
          <button onclick="for (let element of document.getElementsByClassName('fd-private')) { element.hidden = !element.hidden; }">Toggle hidden features</button>
        """ : "")
        .replace("$4", Tool.fullVersion());

    return config.bare()
      ? bareHtml
      : fullHtml(lm.name() + "." + af.qualifiedName(), bareHtml);
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
<div class="sidenav" id="sidenav">
  <button
  class="toggle-nav"
  aria-expanded="true"
  aria-controls="sidenav"
  aria-label="Toggle navigation"
  onclick="
    const nav = document.querySelector('.fd .sidenav nav');
    const hidden = nav.style.display === 'none';
    nav.style.display = hidden ? '' : 'none';
    this.textContent = hidden ? '«' : '»';
    this.setAttribute('aria-expanded', hidden);
  "
  >
    »
  </button>
  <nav style="display: none">$0</nav>
</div>
<div class="container">
  <section><h1 hidden>Library Modules</h1><h1>Fuzion Library Modules</h1>
    <div class='fd-comment'></div>
  </section>
  <section>
        """.replace("$0", navigationModules(null)));

    for (LibraryModule mod : libModules)
      {
        modPage.append("""
    <div class="cursor_pointer">
      <details id="$2" $0>
        <summary>
          <div class="d-grid" style="grid-template-columns: 1fr min-content;">
            <div class="d-flex flex-wrap word-break-break-word">
              <div class="d-flex flex-wrap word-break-break-word fz-code">
                <div class="font-weight-600 ml-2"><a class="fd-feature" href="$1">$2</a></div>
                <div class="flex-grow-1"></div>
                <a class="fd-anchor-sign mr-2" href="#$2">¶</a>
              </div>
            </div>
          </div>
        </summary>
      </details>
    </div>
            """.replace("$0", config.ignoreVisibility() ? "class='fd-private cursor-pointer' hidden" : "class='cursor-pointer'")
               .replace("$1", mod.name() + "/index.html")
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
