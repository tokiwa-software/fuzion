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
import java.util.Map;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;

public class Html
{
  final DocsOptions config;
  private final Map<AbstractFeature, SortedSet<AbstractFeature>> mapOfDeclaredFeatures;
  private final String navigation;

  /**
   * the constructor taking the options
   */
  public Html(DocsOptions config, Map<AbstractFeature, SortedSet<AbstractFeature>> mapOfDeclaredFeatures, AbstractFeature universe)
  {
    this.config = config;
    this.mapOfDeclaredFeatures = mapOfDeclaredFeatures;
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
    if (af.inherits().isEmpty())
      {
        return "";
      }
    return "<div class='fd-keyword mx-5'>:</div>" + af.inherits()
      .stream()
      .<String>map(c -> {
        var f = c.calledFeature();
        return "<a class='fd-feature fd-inherited' href='$1'>".replace("$1", featureAbsoluteURL(f))
          + htmlEncodedBasename(f) + "&nbsp;"
          + c.actualTypeParameters().stream().map(at -> htmlEncodeNbsp(at.asString())).collect(Collectors.joining(", ")) + "</a>";
      })
      .collect(Collectors.joining("<span class='mr-2 fd-keyword'>,</span>"));
  }


  /**
   * anchor tag for type
   * @param at
   * @return
   */
  private String anchor(AbstractType at)
  {
    if (at.isGenericArgument())
      {
        return htmlEncodeNbsp(at.name());
      }
    return "<a class='fd-type' href='$2'>$1</a>".replace("$1", htmlEncodeNbsp(at.asString()))
      .replace("$2", featureAbsoluteURL(at.featureOfType()));
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
      Stream.of("<a class='fd-feature font-weight-600' href='$2'>$1</a>".replace("$1", htmlEncodedBasename(f))
        .replace("$2", featureAbsoluteURL(f))));
  }


  /**
   * summary for feature af
   * @param af
   * @return
   */
  private String summary(AbstractFeature af)
  {
    var arguments = arguments(af);
    return "<div class='d-grid' style='grid-template-columns: 1fr min-content;'>"
      + "<div class='d-flex flex-wrap word-break-break-word'>"
      + "<a class='fd-anchor-sign mr-2' href='#" + htmlID(af) + "'>§</a>"
      + anchor(af)
      + arguments + "<div class='fd-keyword'>" + htmlEncodeNbsp(" => ") + "</div>"
      + anchor(af.resultType())
      + inherited(af)
      // fills remaining space and set cursor pointer
      // to indicate that this area expands revealing a summary
      + "<div class='cursor-pointer flex-grow-1'></div>"
      + "</div>"
      + source(af)
      + "</div>";
  }


  private String anchor(AbstractFeature af) {
    return "<a class='fd-feature font-weight-600' href='" + featureAbsoluteURL(af) + "'>"
            + htmlEncodedBasename(af)
          + "</a>";
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
   * the summaries and the comments of the features
   * @param set
   * @return
   */
  private String mainSection(SortedSet<AbstractFeature> set)
  {
    return set
      .stream()
      .map(af -> {
        // NYI summary tag must not contain div
        return "<details id='" + htmlID(af)
          + "'><summary>$1</summary><div class='fd-comment'>$2</div>$3</details>"
            .replace("$1",
              summary(af))
            .replace("$2", htmlEncodeNbsp(Util.commentOf(af)))
            .replace("$3", redefines(af));
      })
      .collect(Collectors.joining(System.lineSeparator()));
  }


  /**
   * the heading section for feature
   * @param f
   * @return
   */
  private String headingSection(AbstractFeature f)
  {
    return "<h1 class='$5'>$0</h1><h2>$3</h2><h3>$1</h3><div class='fd-comment'>$2</div>$6"
      .replace("$0", f.isUniverse() ? "API-Documentation": htmlEncodedBasename(f))
      .replace("$3", anchorTags(f))
      .replace("$1", f.isUniverse() ? "": summary(f))
      .replace("$2", htmlEncodeNbsp(Util.commentOf(f)))
      .replace("$5", f.isUniverse() ? "": "d-none")
      .replace("$6", redefines(f));
  }

  /**
   * the basename of the feature, replaces all internal names
   * starting with `@` by `_`
   * @param af
   * @return
   *
   */
  private String htmlEncodedBasename(AbstractFeature af)
  {
    return htmlEncodeNbsp(af.featureName().baseName().startsWith("@") ? "_": af.featureName().baseName());
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
    var codeNo = new ArrayList<Integer>();
    var codeLines = new ArrayList<String>();
    var resultLines = new ArrayList<String>();

    s.lines().forEach(l ->
      {
        if (l.startsWith("    "))
          {
            /* code comment */
            codeLines.add(l);
          }
        else if (l.length() == 0)
          {
            /* avoid adding lots of line breaks after code comments */
            if (codeLines.isEmpty())
              {
                resultLines.add(l);
              }
          }
        else
          {
            if (!codeLines.isEmpty())
              {
                /* dump codeLines into a flang.dev runcode box */
                var id = "fzdocs." + name + codeNo.size();
                var code = codeLines
                  .stream()
                  .map(cl -> { return cl.replaceAll("^    ", ""); })
                  .collect(Collectors.joining(System.lineSeparator()));
                resultLines.add(RUNCODE_BOX_HTML.replace("##ID##", id).replace("##CODE##", code));
                codeLines.clear();
                codeNo.add(1);
              }

            /* treat as normal line */
            var replacedLine = htmlEncode(l, false);

            resultLines.add(replacedLine);
          }
      });

    return resultLines.stream().collect(Collectors.joining("<br />"));
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
            throw new RuntimeException("unexpected match");
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
  private static String fullHtml(AbstractFeature af, String bareHtml)
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
        .replace("$qualifiedName", af.qualifiedName());
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
      .replace("$FUZION/lib", DocsOptions.baseApiDir)
      + "#l" + f.pos()._line;
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
    return config.docsRoot() + featureAbsoluteURL0(f) + "/";
  }

  private static String featureAbsoluteURL0(AbstractFeature f)
  {
    if (f.isUniverse())
      {
        return "";
      }
    return featureAbsoluteURL0(f.outer()) + "/" + urlEncode(f.featureName().toString());
  }


  /**
   * arguments of this feature
   * @param f
   * @return
   */
  private String arguments(AbstractFeature f)
  {
    if (f.arguments().isEmpty())
      {
        return "";
      }
    return "(" + f.arguments()
      .stream()
      .map(a ->
        htmlEncodedBasename(a) + "&nbsp;"
        + (a.isTypeParameter() ? typeArgAsString(a): anchor(a.resultType())))
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
   * render the the navigation at the left side
   */
  private String navigation(AbstractFeature start, int depth)
  {
    var declaredFeatures = mapOfDeclaredFeatures.get(start);
    if (declaredFeatures == null || Util.isArgument(start))
      {
        return "";
      }
    return """
      <ul class="white-space-no-wrap">
        <li>
          $3<a href='$2'>$0</a>
          $1
        </li>
      </ul>"""
      .replace("$3", IntStream.range(0, depth)
        .mapToObj(i -> "| ")
        .collect(Collectors.joining())
        .replaceAll("\s$", "―"))
      .replace("$2", featureAbsoluteURL(start))
      .replace("$0", htmlEncodedBasename(start) + args(start))
      .replace("$1",
        declaredFeatures.stream()
          .map(af -> navigation(af, depth + 1))
          .collect(Collectors.joining(System.lineSeparator())));
  }


  private String args(AbstractFeature start)
  {
    if (start.valueArguments().size() == 0)
      {
        return "";
      }
    if (start.valueArguments().size() == 1)
      {
        return " <small>(" + start.valueArguments().size() + " arg)</small>";
      }
    return " <small>(" + start.valueArguments().size() + " args)</small>";
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
            </div>
          </div>
        """
        .replace("$0", headingSection(af))
        .replace("$1", mainSection(mapOfDeclaredFeatures.get(af)))
        .replace("$2", navigation);
    return config.bare() ? bareHtml: fullHtml(af, bareHtml);
  }


}
