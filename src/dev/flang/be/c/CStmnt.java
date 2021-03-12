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
 * Tokiwa GmbH, Berlin
 *
 * Source of class CStmnt
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;

/**
 * CSmnt provides infrastructure to generate C statements
 *
 * The idea here is to have a compiler that works backwards: Instead of parsing
 * source code into an AST, we create an AST for C and then create source code
 * from this.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
abstract class CStmnt extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * break statement
   */
  static final CStmnt BREAK = new CStmnt() { void code(StringBuilder sb) { sb.append("break"); } };


  /**
   * empty statement
   */
  static final CStmnt EMPTY = new CStmnt() {
      boolean isEmpty() { return true; }
      void code(StringBuilder sb) { }
    };


  /*----------------------------  producers  ----------------------------*/


  /**
   * C declaration such as 'i32 i'
   *
   * @param type the type of the defined entity
   *
   * @param ident the name of the defined entity
   *
   * @return corresponding CStmnt
   */
  static CStmnt decl(String type, CIdent ident)
  {
    return decl(null, type, ident);
  }


  /**
   * C declaration such as 'i32 i'
   *
   * @param modifier a modifier, e.g., "static", null for none.
   *
   * @param type the type of the defined entity
   *
   * @param ident the name of the defined entity
   *
   * @return corresponding CStmnt
   */
  static CStmnt decl(String modifier, String type, CIdent ident)
  {
    return new CStmnt()
      {
        void code(StringBuilder sb)
        {
          sb.append(modifier == null ? "" : modifier)
            .append(modifier == null ? "" : " ")
            .append(type)
            .append(" ");
          ident.code(sb);
        }
      };
  }


  /**
   * not really a statement, but a comment
   */
  static CStmnt lineComment(String s)
  {
    return new CStmnt()
      {
        boolean isEmpty()
        {
          return true;
        }
        void code(StringBuilder sb)
        {
          sb.append("// ").append(s).append("\n");
        }
      };
  }


  /**
   * A sequence of C statements, separated by semicolons.
   *
   * @param s the statements.
   *
   * @return corresponding statements sequence
   */
  static CStmnt seq(CStmnt... s)
  {
    return new CStmnt()
      {
        void code(StringBuilder sb)
        {
          for (var cs : s)
            {
              cs.code(sb);
              sb.append(cs.needsSemi() ? ";\n" : "");
            }
        }
        boolean needsSemi()
        {
          return false;
        }
      };
  }


  /*-------------------------  static methods  --------------------------*/


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code corresponding to this statement
   *
   * @param sb will be used to append the code to
   */
  abstract void code(StringBuilder sb);


  /**
   * Is this statement empty, i.e., it produces nothing for the C parser. This
   * is the case for comments or for a NOP that produces nothing.
   */
  boolean isEmpty()
  {
    return false;
  }


  boolean needsSemi()
  {
    return !isEmpty();
  }


  /**
   * Convenience function to create the C code as a string. Try to avoid since
   * this causes additional allocation and copying.  Prefer to use
   * code(StringBuilder).
   *
   * @return the C code of this CExpr
   */
  String code()
  {
    var sb = new StringBuilder();
    code(sb);
    return sb.toString();
  }


  /**
   * Create String for debugging-output.
   */
  public String toString()
  {
    return "C-Statement '" + code() + "'";
  }

}

/* end of file */
