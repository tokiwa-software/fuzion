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
 * Source of class CExpr
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.c;

import dev.flang.util.ANY;

/**
 * CExpr provides infrastructure to generate C code expressions
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
abstract class CExpr extends CStmnt
{


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  producers  ----------------------------*/


  /**
   * Create a C expression from dummy text.  This will most likely not compile,
   * but the dummy text can be used to show what needs to be done to make this
   * compile.
   *
   * @return the resulting expression
   */
  static CExpr dummy(String text)
  {
    return new CExpr()
      {
        void code(StringBuilder sb) { sb.append("/* ").append(text).append(" */"); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a plain identifier
   *
   * @return the resulting expression
   */
  static CExpr ident(String name)
  {
    if (PRECONDITIONS) require
      (isAlphanumeric(name));

    return new CExpr()
      {
        void code(StringBuilder sb) { sb.append(name); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a int32_t constant
   *
   * @return the resulting expression
   */
  static CExpr int32const(int value)
  {
    return value >= 0
      ? new CExpr()
        {
          void code(StringBuilder sb) { sb.append(value); }
          int precedence() { return 0; }
        }
      : int32const(-value).neg();
  }


  /**
   * Create a C expression from a uint32_t constant
   *
   * @return the resulting expression
   */
  static CExpr uint32const(int value)
  {
    return new CExpr()
      {
        void code(StringBuilder sb) { sb.append(value & 0xFFFFffff).append('U'); }
        int precedence() { return 0; }
      };
  }


  /**
   * Create a C expression from a int64_t constant
   *
   * @return the resulting expression
   */
  static CExpr int64const(long value)
  {
    return value >= 0
      ? new CExpr()
        {
          void code(StringBuilder sb) { sb.append(value).append("LL"); }
          int precedence() { return 0; }
        }
      : int64const(-value).neg();
  }


  /**
   * Create a C expression from a uint642_t constant
   *
   * @return the resulting expression
   */
  static CExpr uint64const(long value)
  {
    return new CExpr()
      {
        void code(StringBuilder sb)
        {
          if (value >= 0)
            {
              sb.append(value);
            }
          else
            {
              sb.append("0x");
              sb.append(Long.toHexString(value));
            }
          sb.append("ULL");
        }
        int precedence() { return 0; }
      };
  }


  /**
   * Compound initializer such as (myStruct){ .fieldA = 3 }
   *
   * @return the resulting expression
   */
  static CExpr compoundLiteral(String type, String initializerList)
  {
    return new CExpr()
      {
        int precedence() { return 1; }
        void code(StringBuilder sb) { sb.append('(').append(type).append("){").append(initializerList).append("}"); }
      };
  }


  /*-------------------------  static methods  --------------------------*/


  /**
   * Check if given expression is alphanumeric, i.e., a C identifier or integer constant.
   *
   * @param expr a C expression
   *
   * @return true iff a only contains A..Za..z0..9
   */
  static boolean isAlphanumeric(String expr)
  {
    boolean alphaNumeric = true;
    for (int i=0; i < expr.length(); i++)
      {
        var c = expr.charAt(i);
        alphaNumeric = alphaNumeric && ('A' <= c && c <= 'Z' ||
                                        'a' <= c && c <= 'z' ||
                                        '0' <= c && c <= '9' ||
                                        c == '_');
      }
    return alphaNumeric;
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * What is the lowest (numerically highest) precedence in this expression.
   *
   * See Chapter 6.5 in http://www.open-std.org/jtc1/sc22/wg14/www/docs/n2596.pdf for detail:
   *
   *  0 for prim-expr: identifier, constant, string-literal, '(' expresion ')' and generic-selection
   *
   *  1 for postfix-expr: prim-expr | postfix-expr ('[' ']'| '(' arglist ')'| '.'| '->'| '++'| '--'| '(' type-name ')' '{' initializer-list [','] '}'
   *
   *  2 for unary-expr: postfix-expr | ({'++'|'--'|'&'|'*'|'+'|'-'|'~'|'!'} unary-expr) | 'sizeof'  | '_Alignof'
   *
   *  3 for cast-expr: {'(' type-name ')'} unary-expr
   *
   *  4 for mult-expr: cast-expr | mult-expr ('*' | '/' | '%') cast-expr
   *
   *  5 for add-expr: mult-expr | add-expr ('+' | '-') cast-expr
   *
   *  6 for shift-expr: add-expr | shift-expr ('<<' | '>>') add-expr
   *
   *  7 for rela-expr: shift-expr | rela-expr ('<' | '>' | '<=' | '>=') shift-expr
   *
   *  8 for eq-expr: rela-expr | eq-expr ('==' | '!=') rela-expr
   *
   *  9 for AND-expr: eq-expr | AND-expr '&' eq-expr
   *
   * 10 for XOR-expr: AND-expr | XOR-expr '^' AND-expr
   *
   * 11 for OR-expr: XOR-expr | OR-expr '|' XOR-expr
   *
   * 12 for logicAND-expr: OR-expr | logicAND-expr '&&' OR-expr
   *
   * 13 for logicOR-expr: logicAND-expr | logicOR-expr '||' logicAND-expr
   *
   * 14 for cond-expr: logicOR-expr | logicOR-expr '?' expr : cond-expr
   *
   * 15 for ass-expr; cond.expr | unary-expr ('='|'*='|'/='|'%='|'+='|'-='|'<<='|'>>='|'&='|'^='|'|=') ass-expr
   *
   * 16 expr: ass-exp | expr ',' ass-expr
   *
   * A good reference is from https://en.cppreference.com/w/c/language/operator_precedence,
   * only difference is that this source does not define a level for cast-expr, so levels >= 3 are off by one.
   *
   * @return the precedence used in this CExpr.
   */
  abstract int precedence();


  /**
   * Create C code for this CExpr such that it can be used with an operator with
   * given precedence.  If needed, the code will be wrapped with '(' and ')'.
   *
   * @param sb will be used to append the code to
   *
   * @param precedence the precedence of an operator that will be applied to the
   * result.
   */
  private void code(StringBuilder sb, int precedence)
  {
    if (precedence < precedence())
      {
        sb.append('(');
        code(sb);
        sb.append(')');
      }
    else
      {
        code(sb);
      }
  }


  /**
   * Helper clazz for unary-expr with precedence 2
   */
  private class Unary extends CExpr
  {
    CExpr _inner;
    char _op;

    Unary(CExpr inner, char op)
      {
        _inner = inner;
        _op = op;
      }

    int precedence()
    {
      return 2;
    }

    void code(StringBuilder sb)
    {
      sb.append(_op);
      _inner.code(sb, precedence());
    }
  }

  /**
   * Create CExpr that corresponds to C unary operator '&' with precedence 2
   * applied to this.
   *
   * @return the resulting expression
   */
  CExpr adrOf()
  {
    return new Unary(this, '&')
      {
        // redefine deref since inner.adrOf().deref() == inner
        CExpr deref() { return _inner; }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator '*' with precedence 2
   * applied to this.
   *
   * @return the resulting expression
   */
  CExpr deref()
  {
    return new Unary(this, '*')
      {
        // redefine adrOf since inner.deref().adrOf() == inner
        CExpr adrOf() { return _inner; }

        // Redefine field to generate inner->field and not (*inner).field.
        CExpr field(String name)
        {
          return new CExpr()
            {
              int precedence() { return 1; }
              void code(StringBuilder sb) { _inner.code(sb, precedence()); sb.append("->").append(name); }
          };
        }
    };
  }


  /**
   * Create CExpr that corresponds to C assignment using '=' of value to this.
   *
   * @param value the value to be assigned to this.
   *
   * @return the resulting expression
   */
  CExpr assign(CExpr value)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return 15; }
        void code(StringBuilder sb) { inner.code(sb, precedence()); sb.append(" = "); value.code(sb, precedence()); }
    };
  }


  /**
   * Create CExpr that corresponds to reading named field from struct given in this
   *
   * @param name the name of a field
   *
   * @return the resulting expression to read this.name
   */
  CExpr field(String name)
  {
    CExpr inner = this;
    return new CExpr()
      {
        int precedence() { return 1; }
        void code(StringBuilder sb) { inner.code(sb, precedence()); sb.append(".").append(name); }
    };
  }


  /**
   * Create CExpr that corresponds to C unary operator '-' with precedence 2
   *
   * @param name the name of a field
   *
   * @return the resulting expression to read this.name
   */
  CExpr neg()
  {
    return new Unary(this, '-');
  }


  /**
   * Create String for debugging-output.
   */
  public String toString()
  {
    return "C-Expression '" + code() + "'";
  }

}

/* end of file */
