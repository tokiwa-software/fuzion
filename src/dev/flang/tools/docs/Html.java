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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.Types;
import dev.flang.ast.Visi;
import dev.flang.fe.LibraryFeature;
import dev.flang.fe.LibraryModule;
import dev.flang.tools.FuzionHome;
import dev.flang.tools.Tool;
import dev.flang.util.ANY;
import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
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

  private static final Pattern TEMPLATE_TOKEN = Pattern.compile("##([A-Za-z_0-9]+)##");

  static final String RUNCODE_BOX_HTML = """
    <div class="runcode-wrapper">
      <i class="far fa-spinner fa-spin"></i>
      <div class="mb-15 runcode">
        <div class="position-relative">
          <form id="##id##">
            <label for="##id##.code" class="visually-hidden">Code input</label>
            <textarea class="code-input" required="required" maxlength="4096"
                      id="##id##.code" name="code" rows="##rows##" spellcheck="false">##code##</textarea>
            <div class="position-absolute run-buttons">
              <input type="button" onclick="runit('##id##')" class="run-button" value="Run!" />
              <input type="button" onclick="runiteff('##id##')" class="run-button" value="Effects!" />
              <a href="/tutorial/effects.html"><i>What are effects?</i></a>
            </div>
          </form>
        </div>
        <div class="computer-output" id="##id##.result"></div>
      </div>
    </div>""";


  /*-----------------------------  private methods  -----------------------------*/

  /**
   * Replace ##name## placeholders in the original template only. Inserted values
   * are literal, even if they contain placeholders, dollar signs or backslashes.
   * Callers must escape values for their HTML context before substitution.
   *
   * @throws IllegalArgumentException when a replacement is missing or null
   */
  private static String fillTemplate(String template, Map<String, String> values)
  {
    var matcher = TEMPLATE_TOKEN.matcher(template);
    var result = new StringBuilder(template.length());
    var end = 0;
    while (matcher.find())
      {
        var name = matcher.group(1);
        var value = values.get(name);
        if (value == null)
          {
            throw new IllegalArgumentException("Missing template value: " + name);
          }
        result.append(template, end, matcher.start());
        result.append(value);
        end = matcher.end();
      }
    result.append(template, end, template.length());
    return result.toString();
  }



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
    if (signatureWithArrow(af) // inheritance does not make sense for function features since no instance can be obtained
        || !af.isTypeParameter() && af.inherits().isEmpty()) // type parameters always have a constraint to show
      {
        return "";
      }
    else if (af.isTypeParameter())
      {
        var constraintType = af.constraint();
        var constraint = constraintType.feature();
        return fillTemplate("<span class='fd-keyword mx-5'>:</span><a class='fd-feature fd-inherited' href='##url##'>##name##</a>", Map.of(
          "url", featureRelativeURL(constraint, relativeTo),
          "name", htmlEncodeNbsp(constraintType.toString(true, af))));
      }
    else
      {
        return "<span class='fd-keyword mx-5'>:</span>" + af.inherits()
          .stream()
          // don't show inheritance from features that are not public
          .filter(c->c.calledFeature().visibility().typeVisibility() == Visi.PUB ||
                     c.calledFeature().visibility().eraseTypeVisibility() == Visi.PUB)
          .<String>map(c -> {
            var f = c.calledFeature();
            return fillTemplate("<a class='fd-feature fd-inherited' href='##url##'>##name##</a>", Map.of(
              "url", featureRelativeURL(f, relativeTo),
              "name", htmlEncodedBasename(f)
                      + (c.actualTypeParameters().size() > 0 ? "&nbsp;" : "")
                      +  c.actualTypeParameters().stream()
                          .map(at -> htmlEncodeNbsp(at.toString(true, af)))
                          .collect(Collectors.joining(", "))));
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
    if (at.isParametricType())
      {
        return htmlEncodeNbsp(at.toString(true, context))
               + (at.isOpenGeneric() ? "..." : "");
      }
    return fillTemplate("<a class='fd-type' href='##url##'>##name##</a>", Map.of(
      "url", featureRelativeURL(at.feature(), relativeTo),
      "name", htmlEncodeNbsp(at.toString(true, context))));
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
      Stream.of(typePrfx(f) + fillTemplate("<a class='fd-feature font-weight-600' href='##url##'>##name##</a>", Map.of(
        "url", featureRelativeURL(f, relativeTo),
        "name", htmlEncodedBasename(f)))));
  }


  /**
   * Return "type." prefix if af is a type feature.
   * @param af the feature to be checked
   * @return html for "type." prefix if type feature, empty string otherwise
   */
  private String typePrfx(AbstractFeature af)
  {
    // NYI: BUG: does not show features that `Type` inherits but does not redefine as type features, see #3913
    return af.outer() != null && (af.outer().isCotype() || af.outer() == Types.resolved.f_Type) && !af.isCotype()
            ? "<span class=\"fd-keyword\">type</span>."
            : "";
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
      "<span class='fd-summary'>"
        + "<span class='overflow-wrap-break-word fz-code fd-signature'>"
          + signature(af, outer, relativeTo)
        + "</span>"
        + source(af)
      + "</span>";
  }


  /**
   * The signature of a feature, i.e. its name, arguments and result type.
   *
   * Features with arguments use a three column layout: the feature name
   * including the opening parenthesis, the arguments, and the closing
   * parenthesis followed by the result type. Features without arguments are
   * kept in a single line.
   *
   * @param af the feature whose signature is rendered
   * @param outer the feature in whose context af is used
   * @param relativeTo the feature to which links should be relative to
   * @return HTML for the feature signature
   */
  private String signature(AbstractFeature af, AbstractFeature outer, AbstractFeature relativeTo)
  {
    var args = arguments(af, relativeTo);
    var tail =
      (af.isRef() ? "<span class='fd-keyword'>&nbsp;ref</span>" : "")
      + inherited(af, relativeTo)
      + (signatureWithArrow(af) ? "<span class='fd-keyword'>" + htmlEncodeNbsp(" => ") + "</span>" + anchorType(af, af, relativeTo)
         : af.isConstructor()   ? "<span class='fd-keyword'>" + htmlEncodeNbsp(" is") + "</span>"
         : af.isField()         ? "&nbsp;" + anchorType(af, outer, relativeTo)
                                : "")
      + annotateUnitType(af)
      + annotateInherited(af, outer)
      + annotateRedef(af, outer)
      + annotateAbstract(af)
      + annotateContainsAbstract(af)
      + annotatePrivateConstructor(af)
      + annotateModule(af)
      + "<a class='fd-anchor-sign mr-2' href='#" + htmlID(af) + "'>¶</a>";

    if (args.isEmpty())
      {
        return anchor(af, relativeTo) + tail;
      }

    return "<span class='fd-signature-col1'>" + anchor(af, relativeTo) + "(" + "</span>"
      + "<span class='fd-signature-col2'>"
      + java.util.stream.IntStream.range(0, args.size())
        .mapToObj(i -> "<span class='fd-signature-arg'>" + args.get(i)
                       + (i < args.size() - 1 ? "," : "") + "</span>")
        .collect(Collectors.joining())
      + "</span>"
      + "<span class='fd-signature-col3'>)" + tail + "</span>";
  }

  /**
   * Returns an HTML-formatted annotation for features the frontend knows
   * to be unit types
   *
   * This only works for features directly in universe, as all inner features have an outer reference
   *
   * @param af the feature to for which to create the annotation for
   * @return html to annotate a unit type feature
   */
  private String annotateUnitType(AbstractFeature af)
  {
    return af.isUnitTypeWithoutSideEffect()
      ? "<span class='fd-parent ml-10' title='This feature is guaranteed to be a unit type, "
        + "it does not have any internal state and might serve as a namespace.\n\n"
        + "Note that features without this annotation might still be unit types.'>[Unit Type]</span>" // NYI: replace title attribute with proper tooltip
      : "";
  }

  /**
   * Returns a html formatted annotation to indicate if a feature was declared or inherited
   * @param af the feature to for which to create the annotation for
   * @param outer the feature in whose context af is used
   * @return html to annotate an inherited feature
   */
  private String annotateInherited(AbstractFeature af, AbstractFeature outer)
  {
    if (isDeclared(af, outer)
        || nonPublicInheritanceChain(af) // don't show annotation if feature was inherited from feature with non public outer
        || af.outer().isUniverse()) // features can not inherit from universe,
                                    // this avoid false annotation in the applicable universe features section
      {
        return ""; // not inherited, nothing to display
      }
    else
      {
        AbstractFeature relativeTo = outer != null ? outer : af;
        return fillTemplate("""
          <span class='fd-parent ml-10'>[Inherited from&nbsp; \
          <span class=fz-code><a class='' href='##url##'>##name##</a></span>]</span>""", Map.of(
          "url", featureRelativeURL(af.outer(), relativeTo),
          "name", htmlEncodedBasename(af.outer())));

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
            || af.outer().baseNameHuman().equals(outer.baseNameHuman()));
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

    // don't show annotation if redefining a feature from a non public feature
    redefs.removeIf(f->nonPublicInheritanceChain(f));

    AbstractFeature relativeTo = outer != null ? outer : af;
    return redefs.isEmpty()
            ? ""
            : "<span class='fd-parent ml-10'>[Redefinition of&nbsp; <span class=fz-code>"
              + redefs.stream()
                      .map(f->relativeAnchor(f, relativeTo))
                      .collect(Collectors.joining(",&nbsp;")) + "</span>]</span>";
  }


  private String relativeAnchor(AbstractFeature f, AbstractFeature relativeTo)
  {
    return fillTemplate("<a class='' href='##url##'>##name##</a>", Map.of(
      "url", featureRelativeURL(f, relativeTo),
      "name", htmlEncodedQualifiedName(f)));
  }


  /**
   * Is this feature or any one of the features it inherits from not public
   * i.e. has a type visibility other than public
   */
  private boolean nonPublicInheritanceChain(AbstractFeature f)
  {
    return !f.isUniverse() && (f.visibility().typeVisibility() != Visi.PUB || nonPublicInheritanceChain(f.outer()));
  }

  /**
   * Returns a html formatted annotation to indicate if a feature is abstract
   * @param af the feature to for which to create the annotation for
   * @return html to annotate an abstract feature
   */
  private String annotateAbstract(AbstractFeature af)
  {
    return af.isAbstract()
             ? "<span class='fd-parent ml-10' title='An abstract feature is a feature declared using ⇒ abstract. " +
               "To be able to call it, it needs to be implemented (redefined) in an heir.'>[Abstract feature]</span>"
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
             ? "<span class='fd-parent ml-10' title='This feature can not be called to construct a new instance of itself, " +
               "only the type it defines is visible.'>[Private constructor]</span>" // NYI: replace title attribute with proper tooltip
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
             ? "<span class='fd-parent ml-10' title='This feature contains inner or inherited features " +
               "which are abstract.'>[Contains abstract features]</span>" // NYI: replace title attribute with proper tooltip
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
    return afModule == lm ? "" : "<span class='fd-parent ml-10'>[Module " + afModule.name() + "]</span>";
  }

  private boolean isVisible(AbstractFeature af)
  {
    return af.visibility().typeVisibility() == Visi.PUB;
  }


  private String anchor(AbstractFeature af, AbstractFeature relativeTo) {
    return (noFeatureLink(af) ? "<span class='font-weight-600 ml-2'>"
                              : "<a class='fd-feature font-weight-600 ml-2' href='" + featureRelativeURL(af, lm, relativeTo) + "'>")
            + typePrfx(af) + htmlEncodedBasename(af)
            + (noFeatureLink(af) ? "</span>" : "</a>");
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
      .map(f -> fillTemplate("""
        <li><a href="##url##">##name##</a></li>##redefines##
      """, Map.of(
        "url", featureRelativeURL(f, relativeTo),
        "name", htmlEncodeNbsp(f.qualifiedNameHuman()),
        "redefines", redefines0(f, relativeTo))))
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
    typeParameters.addAll(map.getOrDefault(AbstractFeature.Kind.ExplicitTypeParameter, new TreeSet<AbstractFeature>()));
    typeParameters.addAll(map.getOrDefault(AbstractFeature.Kind.OpenTypeParameter, new TreeSet<AbstractFeature>()));
    typeParameters.addAll(outer.typeArguments());

    var explicitTypeParameters = typeParameters.stream()
                                               .filter(AbstractFeature::isExplicitTypeParameter)
                                               .collect(Collectors.toCollection(List::new));
    var openTypeParameters     = typeParameters.stream()
                                               .filter(AbstractFeature::isOpenTypeParameter)
                                               .collect(Collectors.toCollection(List::new));
    var freeTypeParameters     = typeParameters.stream()
                                               .filter(AbstractFeature::isFreeTypeParameter)
                                               .collect(Collectors.toCollection(List::new));

    // Fields
    var fields =  new List<AbstractFeature>();
    // it's not possible to get an instance of a function feature, so no fields can not be accessed from outside
    if (!signatureWithArrow(outer))
      {
        fields.addAll(map.getOrDefault(AbstractFeature.Kind.Field, new TreeSet<AbstractFeature>()));
        var valueArguments = outer.valueArguments().clone();
        valueArguments.removeIf(a->a.visibility().eraseTypeVisibility() != Visi.PUB);
        fields.addAll(valueArguments);
      }

    // Value arguments of function features
    // List them independent of visibility, they can not be called,
    // but they might contain a comment which otherwise is not shown..
    var functionArguments = signatureWithArrow(outer)
                              ? outer.valueArguments().clone()
                              : new List<AbstractFeature>();


    // Constructors
    var allConstructors =  new TreeSet<AbstractFeature>();
    allConstructors.addAll(map.getOrDefault(AbstractFeature.Kind.Constructor, new TreeSet<AbstractFeature>()));
    allConstructors.addAll(map.getOrDefault(AbstractFeature.Kind.RefConstructor, new TreeSet<AbstractFeature>()));

    var normalConstructors = allConstructors.stream().filter(f->!f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));
    var typeConstructors   = allConstructors.stream().filter(f->f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));

    // Functions
    var allFunctions = new TreeSet<AbstractFeature>();
    // it's not possible to get an instance of a function feature, so no features can be called on it
    if (!signatureWithArrow(outer))
      {
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Function, new TreeSet<AbstractFeature>()));
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Abstract, new TreeSet<AbstractFeature>()));
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Intrinsic, new TreeSet<AbstractFeature>()));
        allFunctions.addAll(map.getOrDefault(AbstractFeature.Kind.Native, new TreeSet<AbstractFeature>()));
      }

    var normalFunctions = allFunctions.stream().filter(f->!f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));
    var typeFeatures    = allFunctions.stream().filter(f->f.isTypeFeature()).collect(Collectors.toCollection(TreeSet::new));

    // Choice Types
    var choices = map.getOrDefault(AbstractFeature.Kind.Choice, new TreeSet<AbstractFeature>());

    // Applicable universe features
    var univFuncDesc = "These are features in universe, that have an argument with a type constraint "
                      + "that matches this features type and can therefore be used with it.";

    var universeFunctions = new TreeSet<AbstractFeature>();

    if (!signatureWithArrow(outer) && !outer.isUniverse())
      {
        var allUniverseFeat = mapOfDeclaredFeatures.get(lm.universe());
        universeFunctions.addAll(allUniverseFeat.getOrDefault(AbstractFeature.Kind.Function, new TreeSet<AbstractFeature>()));
        universeFunctions.addAll(allUniverseFeat.getOrDefault(AbstractFeature.Kind.Abstract, new TreeSet<AbstractFeature>()));
        universeFunctions.addAll(allUniverseFeat.getOrDefault(AbstractFeature.Kind.Intrinsic, new TreeSet<AbstractFeature>()));
        universeFunctions.addAll(allUniverseFeat.getOrDefault(AbstractFeature.Kind.Native, new TreeSet<AbstractFeature>()));

        // only keep features that have a matching type parameter with a type other than Any
        universeFunctions.removeIf(
          af->af.typeArguments().isEmpty()
          || af.typeArguments().stream().noneMatch(typeParam->typeParam.constraint().compareTo(Types.resolved.t_Any ) != 0
                                                              && typeParam.constraint().constraintSatisfiedBy(outer.resultType())));
      }

    return mainSection0("Type Parameters",              null,         explicitTypeParameters, outer, false)
         + mainSection0("Open Type Parameter",          null,         openTypeParameters,     outer, false)
         + mainSection0("Free Type Parameters",         null,         freeTypeParameters,     outer, false)
         + mainSection0("Arguments",                    null,         functionArguments,      outer, false)
         + mainSection0("Fields",                       null,         fields,                 outer, false)
         + mainSection0("Constructors",                 null,         normalConstructors,     outer, true)
         + mainSection0("Type Constructors",            null,         typeConstructors,       outer, true)
         + mainSection0("Functions",                    null,         normalFunctions,        outer, true)
         + mainSection0("Type Features",                null,         typeFeatures,           outer, true)
         + mainSection0("Choice Types",                 null,         choices,                outer, true)
         + mainSection0("Applicable universe features", univFuncDesc, universeFunctions,      outer, true);
  }


  /**
   * The summaries and the comments of the features
   *
   * @param heading the title for this section
   * @param description text block shown under the headline, can be null
   * @param set the features to be included in the summary
   * @param outer the outer feature of the features in the summary
   * @param filterAndSort should features from other modules (including not having a module) be removed and the list sorted?
   * @return
   */
  private String mainSection0(String heading, String description, Collection<AbstractFeature> set, AbstractFeature outer, boolean filterAndSort)
  {
    if (set == null) { return ""; }

    heading = "<h2 class=\"f-category\">" + heading + "</h2>\n";
    description = (description != null && !description.isEmpty()) ? "<div>" + description + "</div>\n" : "";
    var features = set.stream();

    // e.g. don't filter or sort type parameters and fields
    if (filterAndSort)
      {
        features = features
                    // filter out features of other modules which do not need to be shown for this module
                    .filter(af -> (af instanceof LibraryFeature lf ? lf.showInMod(lm) : false))
                    .sorted((af1, af2) -> af1.baseName().compareToIgnoreCase(af2.baseName()));
      }

    var content = features.map(af ->
      fillTemplate("""
        <details id='##id##' ##attributes##>
          <summary>##summary##</summary>
          <div class='fd-comment'>##comment##</div>
          ##redefines##
        </details>
        """, Map.of(
        "id", htmlID(af),
        // NYI: UNDER DEVELOPMENT: rename fd-private?
        "attributes", (config.ignoreVisibility() && !Util.isVisible(af)) ? "class='fd-private cursor-pointer' hidden" : "class='cursor-pointer'",
        "summary", summary(af, outer),
        "comment", commentOf(af),
        "redefines", redefines(af, (outer != null ? outer : af))))
    )
    .collect(Collectors.joining(System.lineSeparator()));

    return content.equals("") ? "" : heading + description + content;
  }


  /**
   * the heading section for feature
   * @param f
   * @return
   */
  private String headingSection(AbstractFeature f)
  {
    return fillTemplate("""
      <h1 hidden>##title##</h1>
      <h1>##heading##</h1>
      <div class='heading-summary'>##summary##</div>
      <div class='fd-comment'>##comment##</div>
      <div class='fd-contract'>##contract##</div>
      ##redefines##
      """, Map.of(
      "title",     f.isUniverse() ? lm.name() : htmlEncodedBasename(f), // short version of title for nav-title
      "heading",   f.isUniverse() ? """
        API-Documentation: module \
        <code class="fd-module-name">""" + lm.name() + "</code>"
        : anchorTags(f),
      "summary",   f.isUniverse() ? "" : summary(f),
      "comment",   commentOf(f),
      "contract",  contractOf(f),
      "redefines", redefines(f, f)));
  }


  /**
   * The features pre- and postconditions in HTML
   *
   * @param af the feature for which to generate the HTML
   * @return HTML showing the feature pre- and postconditions
   */
  private String contractOf(AbstractFeature af)
  {
    return contractFeatureSrc("Precondition" , "pre" , af, f->f.preFeature()) +
           contractFeatureSrc("Postcondition", "post", af, f->f.postFeature());
  }


  /**
   * Generate HTML for one type of a features contract, e.g. precondition
   *
   * @param contractType what the contract is called, e.g. "Precondition"
   * @param af the feature for which to generate the HTML for
   * @param contractFeature the function to obtain the contract feature from feature af
   * @return HTML sowing the specified contract of the feature
   */
  private String contractFeatureSrc(String contractType, String keyword, AbstractFeature af, Function<AbstractFeature, AbstractFeature> contractFeature)
  {
    record Tuple(AbstractFeature fst, AbstractFeature snd) {}

    // first tuple element is the feature from which the contract was inherited, second is the contract itself
    var res = Stream.concat(Stream.of(new Tuple(null, contractFeature.apply(af))),
                            af.redefinesFull().stream().map(f->new Tuple(f, contractFeature.apply(f))))
      .filter(t->t.snd != null && startsWithKeyword(keyword, t.snd.sourceText()))
      .map(t->(t.fst != null ? "<br>Inherited from <span class=fd-fname>" + relativeAnchor(t.fst, af) + "</span><br>" : "")
                + "<div class=fz-code><pre>" + contractSrc(t.snd)
                + "</pre></div>")
      .collect(Collectors.joining());

    return res.isBlank() ? "" : fillTemplate("""
      <details class="cursor-pointer" open>
        <summary><span class=fd-contract-title>##title##</span></summary>
        ##content##
      </details>
      """, Map.of(
      "title", contractType,
      "content", res));
  }

  /**
   * Does text start with keyword, ignoring whitespaces at the beginning
   *
   * NYI: OPTIMIZATION: This is a poor way to determine if a feature with a pre-/postcondition defines one itself or only
   *                    inherits one. If a precondition is inherited but not defined {@code .preFeature().sourceText()}
   *                    returns the source code of the feature itself, and not an empty string.
   *                    So there seems to be no simple and good way to do this at the moment.
   *
   * @param keyword the keyword to look for
   * @param text the text in which to look for the keyword
   * @return true iff the first word in the text is the keyword
   */
  private boolean startsWithKeyword(String keyword, String text)
  {
    int i = 0;
    int len = text.length();

    while (i < len && Character.isWhitespace(text.charAt(i)))
      {
        i++;
      }

    return text.startsWith(keyword + " ", i) || text.startsWith(keyword + "\n", i);
  }

  /**
   * Get the source of a contract feature from the beginning of the line instead of its byte start position
   * and strip indentation (i.e. unindent block without changing relative indentation)
   *
   * @param cf the contract feature for which to return the source code
   * @return source of the contract feature with relative indentation intact
   */
  private String contractSrc(AbstractFeature cf)
  {
    var sr = cf.sourceRange();
    var sf = sr._sourceFile;
    return sf.sourceRange(sf.lineStartPos(sr.line()), sr.byteEndPos()).sourceText().stripIndent();
  }


  /**
   * the comment belonging to this feature in HTML.
   * If the comment does not exist, the comment of a redefined feature.
   */
  String commentOf(AbstractFeature af)
  {
    if (af.isUniverse())
      {
        return Html.processComment(FuzionConstants.UNIVERSE_NAME, universeComment());
      }
    // arguments that are defined on same line as feature have no comments.
    if ((af.isArgument() || af.isTypeParameter()) && af.pos().line() == af.outer().pos().line())
      {
        return "";
      }
    var line = af.pos().line() - 1;
    var commentLines = new ArrayList<String>();

    // NYI: OPTIMIZATION: use lexer to retrieve comments
    while (line > 0 && af.pos()._sourceFile.line(line).matches("(?s)^\\s*#.*"))
      {
        commentLines.add(af.pos()._sourceFile.line(line));
        line = line - 1;
      }

    if (commentLines.stream().allMatch(l -> l.isBlank()))
      {
        if (!af.redefines().isEmpty())
          {
            return af.redefines().stream().flatMap(r ->
              {
                var c = commentOf(r);
                return c.isBlank()
                  ? Stream.empty()
                  : c.startsWith("<details open>")
                  ? Stream.of(c)
                  : Stream.of(
                    "<details class=\"cursor-pointer\" open><summary>Comment of <span class=fd-fname>" +
                    relativeAnchor(r, af.outer())
                    + "</span></summary>" + c + "</details>"
                  );
              }
            )
            .collect(Collectors.joining());
          }
        // NYI: UNDER DEVELOPMENT: need to find a way that does not spam the terminal with 2500 lines when just building the docs
        // say_err("Warning: No comment found for " + af.qualifiedNameHuman());
      }

    Collections.reverse(commentLines);

    var result = Html.processComment(af.qualifiedNameHuman() + af.featureName().argCount() + "_", commentLines
      .stream()
      .map(l -> l.trim())
      .map(l -> l
        .replaceAll("^#", "")
        .replaceAll("^ ", ""))
      .collect(Collectors.joining(System.lineSeparator())));
    return result;
  }



  private static String universeComment()
  {
    var uri = FuzionHome._fuzionHome.normalize().toAbsolutePath().resolve("modules/base/src/universe.fz").toUri();
    try
      {
        return Files.readAllLines(Path.of(uri), StandardCharsets.UTF_8)
          .stream()
          .dropWhile(l -> !l.startsWith("# universe is the mother"))
          .map(l -> l.trim())
          .map(l -> l
            .replaceAll("^#", "")
            .replaceAll("^ ", ""))
          .collect(Collectors.joining(System.lineSeparator()))
          .trim();
      }
    catch (IOException e)
      {
        Errors.fatal("File universe.fz not found");
        return "";
      }
  }



  /**
   * the html encoded basename of the feature af
   * @param af
   * @return
   *
   */
  private String htmlEncodedBasename(AbstractFeature af)
  {
    return htmlEncodeNbsp(af.baseNameHuman());
  }


  /**
   * the html encoded qualified name of the feature af
   * @param af
   * @return
   *
   */
  private String htmlEncodedQualifiedName(AbstractFeature af)
  {
    return htmlEncodeNbsp(af.qualifiedNameHuman());
  }


  /**
   * the link [src] to the source file
   */
  private String source(AbstractFeature feature)
  {
    return "<a class='fd-src-link pl-5' href='" + featureURL(feature) + "'>[src]</a>";
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
        resultLines.add(fillTemplate(RUNCODE_BOX_HTML, Map.of(
          "id", id,
          "rows", Integer.toString(Math.max(3, codeLines.size())),
          "code", code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))));
        codeLines.clear();
        codeNo[0]++;
      }
  }


  private static String htmlEncode(String s, boolean spacesNoneBreaking)
  {

    Pattern p = Pattern.compile("(&|\"|'|<|>|\r|\n|\t|\\s)");
    Matcher m = p.matcher(s);
    var sb = new StringBuilder();
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
   * @param qualifiedNameHuman()
   * @param bareHtml
   * @return
   */
  private static String fullHtml(String qualifiedName, String bareHtml)
  {
    int upDirCorrection = qualifiedName.equals("Modules") || qualifiedName.endsWith(".universe") ? 0 : 1;

    var root = upDirs((int) qualifiedName.chars().filter(c -> c == '.').count() + upDirCorrection);
    return fillTemplate("""
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="utf-8" />
        <title>##name## | Fuzion Docs</title>
        <link rel="icon" sizes="32x32" href="##root##32.png">
        <link rel="stylesheet" type="text/css" href="##root##style.css" />
        <script defer src="##root##ace.js"></script>
        <script defer src="##root##ace-mode-fuzion.js"></script>
        <script defer src="##root##standalone.js"></script>
      </head>
      <body class="standalone-docs">##body##</body>
      </html>
      """, Map.of(
        "name", String.join(" • ", java.util.List.of(qualifiedName.split("\\.")).reversed()),
        "root", root,
        "body", bareHtml));
  }


  /**
   * the unique id used in html for feature af
   * @param f
   * @return
   */
  private static String htmlID(AbstractFeature f)
  {
    return urlEncode(f.qualifiedNameHuman() + "_" + f.arguments().size());
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
   * arguments of this feature, rendered as a list of HTML strings
   * @param f
   * @return
   */
  private java.util.List<String> arguments(AbstractFeature f, AbstractFeature relativeTo)
  {
    // free type parameters are inferred from the type of the value argument
    // they are used in, so they are not shown as separate arguments but
    // directly after that argument.
    var freeTypes = f.typeArguments()
      .filter(AbstractFeature::isFreeTypeParameter);

    return f.arguments()
      .stream()
      .filter(a -> !freeTypes.contains(a))
      .filter(a -> a.isTypeParameter() || (f.visibility().eraseTypeVisibility() == Visi.PUB))
      .map(a -> htmlEncodedBasename(a) + (a.isTypeParameter() ? "" : "&nbsp;") +
        (a.isTypeParameter()             ? typeArgAsString(a, relativeTo) :
        (a.resultType().isParametricType() &&
         a.resultType().typeParameter() instanceof AbstractFeature freeType &&
         freeType.isFreeTypeParameter()) ? htmlEncodedBasename(freeType) +
                                           typeArgAsString(freeType, relativeTo)
                                         : anchorType(a, f, relativeTo)))
      .collect(Collectors.toList());
  }


  private String typeArgAsString(AbstractFeature f, AbstractFeature relativeTo)
  {
    var constraint = f.constraint();

    return (f.isOpenTypeParameter() ? "..." : "")
           + fillTemplate("<span class='mx-5'>:</span><a class='fd-feature fd-inherited' href='##url##'>##name##</a>", Map.of(
             "url", featureRelativeURL(constraint.feature(), relativeTo),
             "name", htmlEncodeNbsp(constraint.toString(true))));
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

        var innerFeatures = lm.declaredFeaturesShallow(f).values().stream()
                              .filter(ft -> ft.definesType()
                                            && ft.visibility().typeVisibility() == Visi.PUB)
                              .sorted(Comparator.comparing(ft -> ft.baseName(), String.CASE_INSENSITIVE_ORDER))
                              .collect(Collectors.toList());

        // addition to the tree structure prefix for current feature: universe / normal element / last element
        var featPrfx = f.isUniverse() ? ""
                                      : iter.hasNext() ? "├─<span class=space-1></span>"
                                                       : "└─<span class=space-1></span>";

        // addition to the tree structure prefix for inner features of current feature: universe / normal element / last element
        var subPrfx  = f.isUniverse() ? ""
                                      : iter.hasNext() ? "│<span class=space-2></span>"
                                                       : "<span class=space-3></span>";

        sb.append(fillTemplate("\n<li>##feature####children##</li>", Map.of(
          "feature", navFeatHtml(f, outerPrefix + featPrfx, relativeTo),
          "children", navigationFeatures(innerFeatures, outerPrefix + subPrfx, relativeTo))));
      }
    while (iter.hasNext());

    return "\n<ul class=\"white-space-no-wrap\">" + sb + "\n</ul>";
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
    return fillTemplate("<div>##prefix##<a href='##url##'>##name##</a></div>", Map.of(
      "prefix", prefix,
      "url", featureRelativeURL(f, relativeTo),
      "name", fName + args(f)));
  }


  /**
   * render list with modules for the navigation at the left side
   */
  private String navigationModules(AbstractFeature relativeTo)
  {
    String relativeToStr = relativeTo == null ? "" : featureAbsoluteURL(relativeTo);

    return fillTemplate("""
      <ul class="white-space-no-wrap">
        <li>
          <div><a href=##root##>Modules</a></div>
            <ul class="fd-module-list">
              ##modules##
            </ul>
        </li>
      </ul>
      """, Map.of(
      "modules", libModules.stream()
                               .map(m->m.name())
                               .map(s->fillTemplate("<li><a href=##url##>##name##</a></li>", Map.of(
                                 "url", config.bare() ? config.docsRoot() + "/" + s : relativePath("/" + s, relativeToStr),
                                 "name", s)))
                               .collect(Collectors.joining("\n")),
      "root", config.bare() ? config.docsRoot() + "/" : relativePath("", relativeToStr)));
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
    var main = mainSection(mapOfDeclaredFeatures.get(af), af);
    var toggleFeatureDetails = main.isEmpty() ? "" : """
      <button type="button" class="toggle-feature-details cursor-pointer" onclick="
        const entries = this.parentElement.querySelectorAll(':scope > details');
        const expand = this.textContent.trim() === 'Expand all';
        entries.forEach(entry => entry.open = expand);
        this.textContent = expand ? 'Collapse all' : 'Expand all';
      ">Expand all</button>
      """;

    var bareHtml =
      fillTemplate("""
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
              <nav style="display: none">##navigation##</nav>
            </div>
            <div class="container">
              <section>##heading##</section>
              <section>
                ##main##
              </section>
              ##expand_btn##
            </div>
          </div>
          <div class=version-hash>##version##</div>
        """, Map.of(
          "heading",    headingSection(af),
          "main",       toggleFeatureDetails + main,
          "navigation", navigation(lm.universe(), af),
          "expand_btn", config.ignoreVisibility()
                          ? """
                            <button onclick="
                              for (let element of document.getElementsByClassName('fd-private')) {
                                element.hidden = !element.hidden;
                              }
                            ">Toggle hidden features</button>
                            """
                          : "",
          "version", Tool.fullVersion()));

    return config.bare()
      ? bareHtml
      : fullHtml(lm.name() + "." + af.qualifiedNameHuman(), bareHtml);
  }

  /**
   * The Module Page
   * @return
   */
  String modulePage()
  {
    // NYI: BUG: some things (e.g. html id or links) might break if there are spaces in a module name
    StringBuilder modPage = new StringBuilder();
    modPage.append(fillTemplate("""
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
        <nav style="display: none">##navigation##</nav>
      </div>
      <div class="container">
        <section><h1 hidden>Library Modules</h1><h1>Fuzion Library Modules</h1>
          <div class='fd-comment'></div>
        </section>
        <section>
      """, Map.of("navigation", navigationModules(null))));

    for (LibraryModule mod : libModules)
      {
        modPage.append(fillTemplate("""
          <details id="##name##" class="cursor-pointer">
            <summary>
              <span class="overflow-wrap-break-word fz-code fd-signature">
                <a class="fd-feature font-weight-600 ml-2" href="##url##">##name##</a>
                <a class="fd-anchor-sign mr-2" href="###name##">¶</a>
              </span>
            </summary>
          </details>
          """, Map.of("url",  mod.name() + "/index.html",
                      "name", mod.name())));
      }

    modPage.append("""
        </section>
      </div>
      </div>
      """);

    return config.bare() ? modPage.toString(): fullHtml("Modules", modPage.toString());
  }


}
