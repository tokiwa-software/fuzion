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
 * Source of class Latex
 *
 *---------------------------------------------------------------------*/

package dev.flang.tools;


import dev.flang.parser.Lexer;

import dev.flang.util.ANY;


/**
 * Latex creates a latex .sty file for formatting Fuzion source code
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Latex extends ANY
{

  /*----------------------------  constants  ----------------------------*/


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for the latex style generator
   */
  Latex()
  {
    StringBuilder sb = new StringBuilder();
    String comma = "    ";
    for (var kw : Lexer.Token._keywords)
      {
        sb.append(comma).append(kw);
        comma = ",\n    ";
      }
    System.out.print("""
                     %% Latex style for fuzion source code
                     %%
                     %% To use this style, save it as 'fuzion.sty' and do, e.g.,
                     %%
                     %%   \\documentclass{article}
                     %%
                     %%   \\usepackage{listings}
                     %%   \\usepackage{fuzion}
                     %%
                     %%   \\begin{document}
                     %%   \\begin{lstlisting}
                     %%   sqrt(a i32) i32
                     %%     pre
                     %%       a >= 0
                     %%     post
                     %%       result * result <= a,
                     %%       (result + 1) > a / (result + 1),
                     %%       result >= 0
                     %%   is
                     %%     if a == 0
                     %%       0
                     %%     else
                     %%       for
                     %%         last := 0, r
                     %%         r    := 1, (last +^ a / last) / 2
                     %%       until r == last
                     %%   \\end{lstlisting}
                     %%
                     %%   \\end{document}
                     %%
                     %%
                     \\lstdefinelanguage{fuzion}{
                       keywords = {
                     %s},
                       sensitive=true, %% keywords are case-sensitive
                       morecomment=[l]{\\#}, %% l is for line comment
                       morecomment=[l]{//}, %% l is for line comment
                       morecomment=[s]{/*}{*/}, %% s is for start and end delimiter
                       morestring=[b]" %% defines that strings are enclosed in double quotes
                     }

                     \\lstset{
                       language=fuzion,
                       commentstyle=\\textit,
                       basicstyle=\\sffamily,
                       numbers=none,
                       keywordstyle=\\bfseries,
                       identifierstyle=,
                       stringstyle=\\ttfamily,
                       showstringspaces=false
                     }
                     """.formatted(sb));
  }

}

/* end of file */
