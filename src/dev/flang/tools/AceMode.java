/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
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
 * Source of class AceMode
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;

import dev.flang.parser.Lexer;

import dev.flang.util.ANY;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class generates a mode for text editor ace.
 * A mode provides syntax highlighting, code folding, etc.
 */
public class AceMode extends ANY
{

  private final String AceModeTemplate = """
      // This File contains the fuzion mode for the editor ace.
      // generated file: fz -acemode
      const BaseFoldMode = function () { };

      (function ()
      {

        this.foldingStartMarker = null;
        this.foldingStopMarker = null;

        // must return "" if there's no fold, to enable caching
        this.getFoldWidget = function (session, foldStyle, row)
        {
          var line = session.getLine(row);
          if (this.foldingStartMarker.test(line))
            return "start";
          if (foldStyle == "markbeginend"
            && this.foldingStopMarker
            && this.foldingStopMarker.test(line))
            return "end";
          return "";
        };

        this.getFoldWidgetRange = function (session, foldStyle, row)
        {
          throw 'not implemented';
        };

        this.indentationBlock = function (session, row, column)
        {
          var re = /\\S/;
          var line = session.getLine(row);
          var startLevel = line.search(re);
          if (startLevel == -1)
            return;

          var startColumn = column || line.length;
          var maxRow = session.getLength();
          var startRow = row;
          var endRow = row;

          while (++row < maxRow)
          {
            var level = session.getLine(row).search(re);

            if (level == -1)
              continue;

            if (level <= startLevel)
            {
              var token = session.getTokenAt(row, 0);
              if (!token || token.type !== "string")
                break;
            }

            endRow = row;
          }

          if (endRow > startRow)
          {
            var endColumn = session.getLine(endRow).length;
            return new ace.Range(startRow, startColumn, endRow, endColumn);
          }
        };

        this.openingBracketBlock = function (session, bracket, row, column, typeRe)
        {
          var start = { row: row, column: column + 1 };
          var end = session.$findClosingBracket(bracket, start, typeRe);
          if (!end)
            return;

          var fw = session.foldWidgets[end.row];
          if (fw == null)
            fw = session.getFoldWidget(end.row);

          if (fw == "start" && end.row > start.row)
          {
            end.row--;
            end.column = session.getLine(end.row).length;
          }
          return ace.Range.fromPoints(start, end);
        };

        this.closingBracketBlock = function (session, bracket, row, column, typeRe)
        {
          var end = { row: row, column: column };
          var start = session.$findOpeningBracket(bracket, end);

          if (!start)
            return;

          start.column++;
          end.column--;

          return ace.Range.fromPoints(start, end);
        };
      }).call(BaseFoldMode.prototype);

      // This only defines high-level behaviour of the Mode like folding etc.
      ace.define('ace/mode/fuzion', ['require', 'exports', 'ace/lib/oop', 'ace/mode/text', 'ace/mode/fuzion_highlight_rules'], (acequire, exports) =>
      {
        const oop = acequire('ace/lib/oop');
        const TextMode = acequire('ace/mode/text').Mode;
        const CustomHighlightRules = acequire('ace/mode/fuzion_highlight_rules').CustomHighlightRules;

        const FoldMode = function (markers)
        {
          this.foldingStartMarker = new RegExp("([\\\\[{])(?:\\\\s*)$|(" + markers + ")(?:\\\\s*)(?:#.*)?$");
        };
        oop.inherits(FoldMode, BaseFoldMode);

        (function ()
        {
          this.getFoldWidgetRange = function (session, foldStyle, row)
          {
            var line = session.getLine(row);
            var match = line.match(this.foldingStartMarker);
            if (match)
            {
              if (match[1])
                return this.openingBracketBlock(session, match[1], row, match.index);
              if (match[2])
                return this.indentationBlock(session, row, match.index + match[2].length);
              return this.indentationBlock(session, row);
            }
          };

        }).call(FoldMode.prototype);

        const Mode = function ()
        {
          this.HighlightRules = CustomHighlightRules;
          this.foldingRules = new FoldMode("is|do");
          this.$behaviour = this.$defaultBehaviour;
        }

        oop.inherits(Mode, TextMode);

        exports.Mode = Mode;
      });

      // This is where we really create the highlighting rules
      ace.define('ace/mode/fuzion_highlight_rules', ['require', 'exports', 'ace/lib/oop', 'ace/mode/text_highlight_rules'], (acequire, exports) =>
      {
        const oop = acequire('ace/lib/oop');
        const TextHighlightRules = acequire('ace/mode/text_highlight_rules').TextHighlightRules;


        const CustomHighlightRules = function CustomHighlightRules()
        {
          var keywordMapper = this.createKeywordMapper({
            "keyword": "%s",
          }, "text", false);

          this.$rules = {
            "start": [
              {
                token: "comment.line",
                regex: /(#|\\/\\/).*$/,
              },
              {
                regex: "\\\\w+\\\\b",
                token: keywordMapper
              },
              {
                token: "string.quoted.double",
                regex: /"|\\}/,
                push: [{
                    token: "string.quoted.double",
                    regex: /"|\\{/,
                    next: "pop"
                }, {
                    token: "constant.character.escape",
                    regex: /\\\\./
                }, {
                    token: "variable.other",
                    regex: /\\$[A-Za-z0-9_]+/
                }, {
                    defaultToken: "string.quoted.double"
                }]
              },
              {
                token: "numbers",
                regex: /\\d+(?:[.](\\d)*)?|[.]\\d+/
              }],

            "static-method": [{
              token: "support.function",
              regex: /\\w+/,
              next: "start"
            }]
          };
          this.normalizeRules();
        };

        oop.inherits(CustomHighlightRules, TextHighlightRules);

        exports.CustomHighlightRules = CustomHighlightRules;
      });
      """;

  AceMode()
  {
    var keywords = Stream.of(Lexer.Token._keywords).map(token -> token.toString()).collect(Collectors.joining("|"));
    System.out.print(AceModeTemplate.formatted(keywords));
  }

}

/* end of file */
