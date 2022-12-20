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
import java.util.HashMap;
import java.util.Map.Entry;
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

  /**
   * the constructor taking the options
   */
  public Html(DocsOptions config)
  {
    this.config = config;
  }


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
          + htmlEncodeNbsp(basename(f) + " ")
          + c.generics().stream().map(at -> at.asString()).collect(Collectors.joining(htmlEncodeNbsp(", "))) + "</a>";
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
        return at.name();
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
      Stream.of("<a class='fd-feature' href='$2'>$1</a>".replace("$1", htmlEncodeNbsp(basename(f)))
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
      + "<a class='fd-feature' href='" + featureAbsoluteURL(af) + "'>"
      + (isEffect(af) ? "<span title='effect'>🎆&nbsp;</span>": "&nbsp;&nbsp;")
      + htmlEncodeNbsp(basename(af))
      + "</a>"
      + arguments + "<div class='fd-keyword'>" + htmlEncodeNbsp(" => ") + "</div>"
      + anchor(af.resultType())
      + inherited(af)
      + "</div>"
      + source(af)
      + "</div>";
  }


  /**
   * get directly and indirectly inherited features of af
   */
  private static Stream<AbstractFeature> inheritedRecursive(AbstractFeature af)
  {
    return Stream.concat(af.inherits().stream().map(x -> x.calledFeature()),
      af.inherits().stream().flatMap(c -> inheritedRecursive(c.calledFeature())));
  }


  /**
   * is the feature inherting from effect?
   * @param af
   * @return
   */
  private boolean isEffect(AbstractFeature af)
  {
    return inheritedRecursive(af).anyMatch(x -> x.qualifiedName().equals("effect"));
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
          + "'><summary>$1</summary><p class='fd-comment'>$2</p></details>"
            .replace("$1",
              summary(af))
            .replace("$2", htmlEncode(Util.commentOf(af), false));
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
    return "<h1 class='$5'>$0</h1><h2>$4$3</h2><h3>$1</h3><p class='fd-comment'>$2</p>"
      .replace("$0", f.isUniverse() ? "API-Documentation": basename(f))
      .replace("$3", f.isUniverse() ? "": anchorTags(f))
      .replace("$1", f.isUniverse() ? "": summary(f))
      .replace("$2", htmlEncode(Util.commentOf(f), false))
      .replace("$4", f.isUniverse() ? "": "<a class='mr-5' href='" + config.docsRoot() + "/'>🌌</a>")
      .replace("$5", f.isUniverse() ? "": "d-none");
  }

  /**
   * the basename of the feature, replaces all internal names
   * starting with `@` by `_`
   * @param af
   * @return
   *
   */
  private String basename(AbstractFeature af)
  {
    return af.featureName().baseName().startsWith("@") ? "_": af.featureName().baseName();
  }


  /**
   * the link [src] to the source file
   */
  private static String source(AbstractFeature feature)
  {
    return "<div class='pl-5'><a href='$1'>[src]</a></div>"
      .replace("$1", featureURL(feature));
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
   * @param entry
   * @param bareHtml
   * @return
   */
  private static String fullHtml(Entry<AbstractFeature, SortedSet<AbstractFeature>> entry, String bareHtml)
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
        .replace("$qualifiedName", entry.getKey().qualifiedName());
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
      .map(a -> htmlEncodeNbsp(
        basename(a) + " ")
        + (a.isTypeParameter() ? typeArgAsString(a): anchor(a.resultType())))
      .collect(Collectors.joining(htmlEncodeNbsp(", "))) + ")";
  }


  private String typeArgAsString(AbstractFeature f)
  {
    if (f.resultType().dependsOnGenerics())
      {
        return "(" + f.resultType().asString() + ")." + "<div class='fd-keyword'>type</div>";
      }
    return "<div class='fd-keyword'>type</div>";
  }


  /**
   * render the the navigation at the left side
   */
  private String navigation(AbstractFeature start,
    HashMap<AbstractFeature, SortedSet<AbstractFeature>> mapOfDeclaredFeatures, int depth)
  {
    var declaredFeatures = mapOfDeclaredFeatures.get(start);
    if (declaredFeatures == null || Util.isArgument(start))
      {
        return "";
      }
    return """
      <ul>
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
      .replace("$0", basename(start))
      .replace("$1",
        declaredFeatures.stream()
          .map(af -> navigation(af, mapOfDeclaredFeatures, depth + 1))
          .collect(Collectors.joining(System.lineSeparator())));
  }


  /*-----------------------------  public methods  -----------------------------*/

  /**
   * the full content
   * @return
   */
  String content(Entry<AbstractFeature, SortedSet<AbstractFeature>> entry, AbstractFeature universe,
    HashMap<AbstractFeature, SortedSet<AbstractFeature>> m)
  {
    var navigation = navigation(universe, m, 0);
    var bareHtml =
      """
          <!-- GENERATED BY FZDOCS -->
          <div class='fd'>
            <div class="sidenav">
              <div onclick="document.querySelector('.fd .sidenav nav').style.display = (document.querySelector('.fd .sidenav nav').style.display === 'none' ?  '' : 'none');" class="toggle-nav">☰</div>
              <nav style="display: none">$2</nav>
            </div>
            <div class="container">
              <section>$0</section>
              <section>$1</section>
            </div>
          </div>
        """
        .replace("$0", headingSection(entry.getKey()))
        .replace("$1", mainSection(entry.getValue()))
        .replace("$2", navigation);
    return config.bare() ? bareHtml: fullHtml(entry, bareHtml);
  }


}
