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
 * Source of class CStmnt
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;
import dev.flang.util.List;

/**
 * CStmnt provides infrastructure to generate C statements
 *
 * The idea here is to have a compiler that works backwards: Instead of parsing
 * source code into an AST, we create an AST for C and then create source code
 * from this.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
abstract class CStmnt extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * break statement
   */
  static final CStmnt BREAK = new CStmnt() { void code(CString sb) { sb.append("break"); } };


  /**
   * empty statement
   */
  static final CStmnt EMPTY = new CStmnt() {
      boolean isEmpty() { return true; }
      void code(CString sb) { }
    };


  /*----------------------------  producers  ----------------------------*/


  /**
   * C typedef such as 'typedef struct s t'
   *
   * @param modifier a modifier, e.g., "static", null for none.
   *
   * @param type the type of the defined entity
   *
   * @param ident the name of the defined entity
   *
   * @param init initial value or null if none.
   *
   * @return corresponding CStmnt
   */
  static CStmnt typedef(String type, String name)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append("typedef ")
            .append(type)
            .append(" ")
            .append(name);
        }
    };
  }


  /**
   * C struct such as 'struct s { x int; c char; }'
   *
   * @param name the name of the struct
   *
   * @param els the declaration in the struct
   *
   * @return corresponding CStmnt
   */
  static CStmnt struct(String name, List<CStmnt> els)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append("struct ")
            .append(name)
            .append("\n")
            .append("{\n");
          for (var d : els)
            {
              d.code(sb.indent());
              sb.indent().append(";\n");
            }
          sb.append("}");
        }
    };
  }


  /**
   * C union such as 'union { x int; c char; } name'
   *
   * @param name the name of the struct
   *
   * @param els the declaration in the struct
   *
   * @return corresponding CStmnt
   */
  static CStmnt unyon(List<CStmnt> els, CIdent name)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append("union\n")
            .append("{\n");
          for (var d : els)
            {
              d.code(sb.indent());
              sb.indent().append(";\n");
            }
          sb.append("}");
          name.code(sb);
        }
    };
  }


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
    return decl(modifier, type, ident, null);
  }


  /**
   * C declaration such as 'i32 i'
   *
   * @param type the type of the defined entity
   *
   * @param ident the name of the defined entity
   *
   * @param init initial value or null if none.
   *
   * @return corresponding CStmnt
   */
  static CStmnt decl(String type, CIdent ident, CExpr init)
  {
    return decl(null, type, ident, init);
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
   * @param init initial value or null if none.
   *
   * @return corresponding CStmnt
   */
  static CStmnt decl(String modifier, String type, CIdent ident, CExpr init)
  {
    return decl(modifier, type, ident, null, init);
  }


  /**
   * C declaration such as 'char c[4] = "123";'
   *
   * @param modifier a modifier, e.g., "static", null for none.
   *
   * @param type the type of the defined entity
   *
   * @param ident the name of the defined entity
   *
   * @param sz array size, null if none
   *
   * @param init initial value or null if none.
   *
   * @return corresponding CStmnt
   */
  static CStmnt decl(String modifier, String type, CIdent ident, CExpr sz, CExpr init)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append(modifier == null ? "" : modifier)
            .append(modifier == null ? "" : " ")
            .append(type)
            .append(" ");
          ident.code(sb);
          if (sz != null)
            {
              sb.append("[");
              sz.code(sb);
              sb.append("]");
            }
          if (init != null)
            {
              sb.append(" = ");
              init.code(sb);
            }
        }
      };
  }



  /**
   * C declaration such as 'void print(char *src)'
   *
   * @param resultType the type of the result
   *
   * @param ident the name of the function
   *
   * @param argsWithTypes the arguments, pairs of type and name.
   *
   * @param body the body of the function or null for forward declaration.
   *
   * @return corresponding CStmnt
   */
  static CStmnt functionDecl(String resultType,
                             CIdent ident,
                             List<String> argsWithTypes,
                             CStmnt body)
  {
    if (PRECONDITIONS) require
      (argsWithTypes.size() % 2 == 0);

    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append(resultType).append(" ");
          ident.code(sb);
          sb.append("(");
          for (int i = 0; i < argsWithTypes.size(); i += 2)
            {
              sb.append(i > 0 ? ", " : "")
                .append(argsWithTypes.get(i))
                .append(" ")
                .append(argsWithTypes.get(i+1));
            }
          sb.append(")");
          if (body != null)
            {
              sb.append("\n")
                .append("{\n");
              seq(new List<>(body)).code(sb.indent());
              sb.append("}\n");
            }
        }
        boolean needsSemi()
        {
          return body == null;
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
        void code(CString sb)
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
        void code(CString sb)
        {
          for (var cs : s)
            {
              cs.codeSemi(sb);
            }
        }
        boolean needsSemi()
        {
          return false;
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
  static CStmnt seq(List<CStmnt> s)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          for (var cs : s)
            {
              cs.codeSemi(sb);
            }
        }
        boolean needsSemi()
        {
          return false;
        }
      };
  }


  /**
   * Zero, one or several case labels followed by a statement
   *
   * @param vals the values checked in the case labels. might be an empty list,
   * which turns the result into a NOP.
   *
   * @param cmds the commands to be executed in case the values match.
   */
  static CStmnt caze(List<CExpr> vals, CStmnt cmds)
  {
    return vals.isEmpty() ? EMPTY :
      new CStmnt()
      {
        void code(CString sb)
        {
          for (var v : vals)
            {
              sb.append("case ");
              v.code(sb);
              sb.append(":\n");
            }
          sb.append("{\n");
          cmds.code(sb.indent());
          sb.append("}\n");
        }
        boolean needsSemi()
        {
          return false;
        }
    };
  }


  /**
   * A switch statement
   *
   * @param val the value to switch over
   *
   * @param cazes the case labels to be checked
   *
   * @param def the default case or null if none.
   */
  static CStmnt suitch(CExpr val, List<CStmnt> cazes, CStmnt def)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append("switch (");
          val.code(sb);
          sb.append(")\n")
            .append("{\n");
          var sbi = sb.indent();
          for (var cz : cazes)
            {
              cz.code(sbi);
            }
          if (def != null)
            {
              sbi.append("default:\n")
                .append("{\n");
              def.code(sbi.indent());
              sbi.append("}\n");
            }
          sb.append("}\n");
        }
        boolean needsSemi()
        {
          return false;
        }
    };
  }



  /**
   * An if statement
   *
   * @param cc the condition value
   *
   * @param s the code to execute if cc is TRUE
   *
   * @return the if statment
   */
  static CStmnt iff(CExpr cc, CStmnt s)
  {
    return new CStmnt()
      {
        void code(CString sb)
        {
          sb.append("if (");
          cc.code(sb);
          sb.append(")\n")
            .append("{\n");
          s.codeSemi(sb.indent());
          sb.append("}\n");
        }
        boolean needsSemi()
        {
          return false;
        }
    };
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Create the C code corresponding to this statement
   *
   * @param sb will be used to append the code to
   */
  abstract void code(CString sb);


  /**
   * Create the C code corresponding to this statement followed by a semicolon
   * if needed.
   *
   * @param sb will be used to append the code to
   */
  void codeSemi(CString sb)
  {
    code(sb);
    sb.append(needsSemi() ? ";\n" : "");
  }


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
   * code(CString).
   *
   * @return the C code of this CExpr
   */
  String code()
  {
    var sb = new CString();
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
