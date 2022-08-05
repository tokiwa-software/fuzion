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
 * Source of class OpExpr
 *
 *---------------------------------------------------------------------*/

package dev.flang.parser;

import java.util.ArrayList;

import dev.flang.ast.Call;
import dev.flang.ast.Expr;
import dev.flang.ast.NumLiteral;

import dev.flang.util.ANY;
import dev.flang.util.List;

/**
 * OpExpr <description>
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class OpExpr extends ANY
{

  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  ArrayList<Object> els = new ArrayList<>();


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor
   */
  OpExpr()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * add
   *
   * @param o
   */
  void add(Operator o)
  {
    els.add(o);
  }


  /**
   * add
   *
   * @param e
   */
  void add(Expr e)
  {
    els.add(e);
  }


  /**
   * add
   *
   * @param o
   */
  void add(OpExpr o)
  {
    for(int i=0; i<o.els.size(); i++)
      {
        els.add(o.els.get(i));
      }
  }


  /**
   * toExpr
   *
   * @return
   */
  Expr toExpr()
  {
    return toExprUsePrecedence();
  }


  /**
   * convert this to an expression using precendence, i.e., prefix,
   * postfix and infix that are ambiguous will be replaced in order of
   * precendence, else left to right.
   * <p>
   * Ex: -a* +b! --> -(a*(+(b!)))  -a*+(b!) -(a*)+(b!) (-(a*))+(b!) ((-(a*))+(b!))
   *
   * @return Expr an expression instance corresponding to this
   * operator expression.
   */
  Expr toExprUsePrecedence()
  {
    while (els.size() > 1)
      {
        // show();
        int max = -1;
        int pmax = -1;
        for(int i=0; i<els.size(); i++)
          {
            if (isOp(i))                           // i is an operator
              {
                var op = op(i);
                if (max < 0)
                  {
                    max = i;
                  }
                if (isExpr(i-1) && isExpr(i+1))  // an infix operator
                    {
                      if (precedence(i, Kind.infix) >  pmax && isLeftToRight(i) ||   // a left-to-right infix operator
                          precedence(i, Kind.infix) >= pmax && isRightToLeft(i)    ) // a right-to-left infix operator
                        {
                          max = i;
                          pmax = precedence(i, Kind.infix);
                        }
                    }
                else if (isExpr(i+1)) // a prefix operator
                  {
                    if (// 'a + + b' => '(a+) + b' and
                        // 'a + +b' => 'a + (+b)'
                        // 'a+ +b'  => 'a + (+b)' due to white space
                        isOp(i-1) && !op._whiteSpaceAfter && op._whiteSpaceBefore && op(i-1)._whiteSpaceBefore)
                      {
                        max = i;
                        pmax = Integer.MAX_VALUE;
                      }
                    else if (// 'a + * b' => 'a + (* b)' and
                             // 'a + + b' => 'a + (+ b)' and
                             // 'a * + b' => '(a *) + b' due to precedence
                             precedence(i, Kind.prefix)  >= pmax)
                      { // a prefix operator
                        max = i;
                        pmax = precedence(i, Kind.prefix);
                      }
                  }
                else if (isExpr(i-1))  // a postfix operator
                  {
                    if (// 'a+ + b' => '(a+) + b' and
                        // 'a + +b' => 'a + (+b)'
                        // 'a+ +b'  => 'a + (+b)' due to white space
                        isOp(i+1) && !op._whiteSpaceBefore && op._whiteSpaceAfter && op(i+1)._whiteSpaceAfter)
                      {
                        // in case white space suggests higher precedence for
                        // postfix op, then treat it as a postfix op.
                        max = i;
                        pmax = Integer.MAX_VALUE;
                      }
                    else if (// 'a + * b' => 'a + (* b)' and
                             // 'a * + b' => '(a *) + b' due to precedence
                             precedence(i, Kind.postfix) >= pmax)
                      {
                        max = i;
                        pmax = precedence(i, Kind.postfix);
                      }
                  }
              }
          }
        Operator op = op(max);
        if (isExpr(max-1) && isExpr(max+1))
          {             // infix op:
            Expr e1 = expr(max-1);
            Expr e2 = expr(max+1);
            Expr e = new Call(op.pos, e1, "infix "+op.text, null, null, new List<Expr>(e2));
            els.remove(max+1);
            els.remove(max);
            els.set(max-1, e);
          }
        else if (isExpr(max+1))
          {                       // prefix op:
            Expr e2 = expr(max+1);
            Expr e =
              (op.text.equals("+") && (e2 instanceof NumLiteral i2)) ? i2             :
              (op.text.equals("-") && (e2 instanceof NumLiteral i2)) ? i2.neg(op.pos) : new Call(op.pos, e2, "prefix "+op.text, null, null, Expr.NO_EXPRS);
            els.remove(max+1);
            els.set(max, e);
          }
        else
          {                                          // postfix op:
            Expr e1 = expr(max-1);
            Expr e = new Call(op.pos, e1, "postfix "+op.text, null, null, Expr.NO_EXPRS);
            els.remove(max);
            els.set(max-1, e);
          }
      }
    //    show();
    return expr(0);
  }


  /**
   * Check is element i exists and is an expression
   *
   * @param i an integer value
   *
   * @return true iff i is a valid index in els and els.get(i)
   * contains an expression.
   */
  boolean isExpr(int i)
  {
    return (i>=0) && (i<els.size()) && (els.get(i) instanceof Expr);
  }


  /**
   * Get the express at the given index.
   *
   * @param i valid index of an Operator in els
   *
   * @return the operator.
   */
  Expr expr(int i)
  {
    if (PRECONDITIONS) require
                         (isExpr(i));
    return (Expr) els.get(i);
  }


  /**
   * Check is element i exists and is an operator.
   *
   * @param i an integer value
   *
   * @return true iff i is a valid index in els and els.get(i)
   * contains an operator.
   */
  boolean isOp(int i)
  {
    return (i>=0) && (i<els.size()) && (els.get(i) instanceof Operator);
  }


  /**
   * Get the operator at the given index.
   *
   * @param i valid index of an Operator in els
   *
   * @return the operator.
   */
  Operator op(int i)
  {
    if (PRECONDITIONS) require
                         (isOp(i));
    return (Operator) els.get(i);
  }


  /**
   * get the precedence of operator at index i
   *
   * @param i an integer value
   *
   * @return -1 iff !isOp(i), else the precedence of the operator at index i.
   */
  int precedence(int i, Kind kind)
  {
    return
      isOp(i)
      ? precedence(op(i), kind)
      : -1;
  }


  /**
   * check if index i is an operator with left-to-right execution order
   *
   * @param i an integer value
   *
   * @return true iff isOp(i) and the operator at index i is handled left-to-right
   */
  boolean isLeftToRight(int i)
  {
    return isOp(i) && isLeftToRight(op(i));
  }


  /**
   * check if index i is an operator with right-to-left execution order
   *
   * @param i an integer value
   *
   * @return true iff isOp(i) and the operator at index i is handled right-to-left
   */
  boolean isRightToLeft(int i)
  {
    return isOp(i) && isRightToLeft(op(i));
  }


  /**
   * show
   */
  private void show()
  {
    System.out.print(""+els.size()+": ");
    for(int i=0; i<els.size(); i++)
      {
        Object o = els.get(i);
        if (o instanceof String) System.out.print(o);
        else if (o instanceof Call) System.out.print(((Call)o).name);
        else System.out.print("E");
      }
    System.out.println();
  }


  /**
   * determine the precendence of operator op.
   */
  int precedence(Operator op, Kind kind)
  {
    char c = op.text.charAt(0);
    int i=0;
    while ((   i<precedences.length             )
           && (precedences[i].chars.indexOf(c)<0))
      {
        i++;
      }
    return (i<precedences.length
            ? (kind == Kind.prefix ? precedences[i].prefix :
               kind == Kind.infix  ? precedences[i].infix :
               /* Kind.postfix */    precedences[i].postfix )
            : 0);
  }

  enum Kind { prefix, infix, postfix };

  /**
   *
   */
  public final Precedence[] precedences = { new Precedence(16,        "@"  ),
                                            new Precedence(15,        "^"  ),
                                            new Precedence(14, 5, 14, "!"  ),
                                            new Precedence(13,        "~"  ),
                                            new Precedence(12,        "⁄"  ),
                                            new Precedence(11,        "*/%⊛⊗⊘⦸⊝⊚⊙⦾⦿⨸⨁⨂⨷"),
                                            new Precedence(10,        "+-⊕⊖" ),
                                            new Precedence( 9,        "."  ),
                                            new Precedence( 8,        "#"  ),
                                            new Precedence(14, 7, 14, "$"  ),
                                            new Precedence( 6,        ""   ),
                                            new Precedence( 5,        "<>=⧁⧀⊜⩹⩺⩻⩼⩽⩾⩿⪀⪁⪂⪃⪄⪅⪆⪇⪈⪉⪊⪋⪌⪍⪎⪏⪐⪑⪒⪓⪔⪕⪖⪗⪘⪙⪚⪛⪜⪝⪞⪟⪠⪡⪢⪤⪥⪦⪧⪨⪩⪪⪫⪬⪭⪮⪯⪰⪱⪲⪴⪵⪶⪷⪸⪹⪺⪻⪼⫷⫸⫹⫺"),
                                            new Precedence( 4,        "&"  ),
                                            new Precedence( 3,        "|⦶⦷"),
                                            new Precedence( 2,        "∀"  ),
                                            new Precedence( 1,        "∃"  ),
                                            // all other operators: 0
                                            new Precedence( -1,       ":" ),
  };


  /**
   * Precedence represents the precedence of an operator
   */
  class Precedence
  {

    /**
     * chars the operator starts with
     */
    final String chars;


    /**
     * precedence if used as prefix, infix, or postfix operator
     */
    final int prefix, infix, postfix;


    /**
     * Constructor for opeartor with given initial chars and equal precedence
     * for prefix, infix, and postfix operator .
     *
     * @param p its precedence.
     *
     * @param c the chars the operator starts with
     */
    Precedence(int p, String c)
    {
      this(p,p,p,c);
    }

    /**
     * Constructor for opeartor with given initial chars and given precedence
     * for prefix, infix, and postfix operator .
     *
     * @param prefix the precedence if used as a prefix operator
     *
     * @param infix the precedence if used as a infix operator
     *
     * @param postfix the precedence if used as a postfix operator
     *
     * @param c the chars the operator starts with
     */
    Precedence(int prefix, int infix, int postfix, String c)
    {
      this.chars = c;
      this.prefix = prefix;
      this.infix = infix;
      this.postfix = postfix;
    }

  }

  /**
   * Does the given operator bind to the left?
   */
  boolean isLeftToRight(Operator op)
  {
    return !isRightToLeft(op);
  }

  /**
   * Does the given operator bind to the rightt?
   */
  boolean isRightToLeft(Operator op)
  {
    char c = op.text.charAt(0);
    return RIGHT_TO_LEFT_CHARS.indexOf(c) >= 0;
  }

  /**
   * Initial characters of operators that bind to the right.
   */
  public final String RIGHT_TO_LEFT_CHARS = "^";

}

/* end of file */
