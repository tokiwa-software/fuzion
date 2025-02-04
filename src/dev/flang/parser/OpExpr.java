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
import dev.flang.ast.ParsedOperatorCall;
import dev.flang.ast.ParsedName;

import dev.flang.util.ANY;
import dev.flang.util.FuzionConstants;

/**
 * OpExpr
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class OpExpr extends ANY
{

  /*----------------------------  variables  ----------------------------*/


  /**
   *
   */
  final private ArrayList<Object> _els = new ArrayList<>();


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
  void add(Object o)
  {
    if (PRECONDITIONS) require
      (o instanceof Expr || o instanceof Operator);

    _els.add(o);
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
   * convert this to an expression using precedence, i.e., prefix,
   * postfix and infix that are ambiguous will be replaced in order of
   * precedence, else left to right.
   * <p>
   * Ex: -a* +b! --> -(a*(+(b!)))  -a*+(b!) -(a*)+(b!) (-(a*))+(b!) ((-(a*))+(b!))
   *
   * @return Expr an expression instance corresponding to this
   * operator expression.
   */
  Expr toExprUsePrecedence()
  {
    while (_els.size() > 1 && _els.get(0) != Call.ERROR)
      {
        // show();
        int max = -1;
        int pmax = -1;
        for(int i=0; i<_els.size(); i++)
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
                    if (// 'a+ + b'  => '(a+) + b' and
                        // 'a + +b'  => 'a + (+b)' and
                        // 'a+ +b'   => 'a + (+b)' and
                        // 'a+ + b+' => '(a+) + (b+)' due to white space
                        (isOp(i+1) && op(i+1)._whiteSpaceAfter || isOp(i-2) && op(i-2)._whiteSpaceAfter) && !op._whiteSpaceBefore && op._whiteSpaceAfter )
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
          { // infix op:
            Expr e1 = expr(max-1);
            Expr e2 = expr(max+1);
            Expr e = new ParsedOperatorCall(e1, new ParsedName(op._pos, FuzionConstants.INFIX_OPERATOR_PREFIX + op._text), e2);
            _els.remove(max+1);
            _els.remove(max);
            _els.set(max-1, e);
          }
        else if (isExpr(max+1))
          { // prefix op:
            Expr e2 = expr(max+1);
            Expr e =
              (op._text.equals("+") && (e2 instanceof NumLiteral i2) && op._pos.byteEndPos() == i2.pos().bytePos()) ? i2.addSign("+", op._pos) :
              (op._text.equals("-") && (e2 instanceof NumLiteral i2) && op._pos.byteEndPos() == i2.pos().bytePos()) ? i2.addSign("-", op._pos) :
              new ParsedOperatorCall(e2, new ParsedName(op._pos, FuzionConstants.PREFIX_OPERATOR_PREFIX + op._text));
            _els.remove(max+1);
            _els.set(max, e);
          }
        else
          { // postfix op:
            Expr e1 = expr(max-1);
            Expr e = new ParsedOperatorCall( e1, new ParsedName(op._pos, FuzionConstants.POSTFIX_OPERATOR_PREFIX + op._text));
            _els.remove(max);
            _els.set(max-1, e);
          }
      }
    //    show();
    return expr(0);
  }


  /**
   * Get the number of operators and expressions
   *
   * @return The size, never negative
   */
  int size()
  {
    return _els.size();
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
    return (i>=0) && (i<_els.size()) && (_els.get(i) instanceof Expr);
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
    return (Expr) _els.get(i);
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
    return (i>=0) && (i<_els.size()) && (_els.get(i) instanceof Operator);
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
    return (Operator) _els.get(i);
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
   * show (only for debugging)
   */
  private void show()
  {
    System.out.print(""+_els.size()+": ");
    for(int i=0; i<_els.size(); i++)
      {
        Object o = _els.get(i);
        if (o instanceof String) System.out.print(o);
        else if (o instanceof Call c) System.out.print(c.name());
        else System.out.print("E");
      }
    say();
  }


  /**
   * determine the precedence of operator op.
   */
  int precedence(Operator op, Kind kind)
  {
    char c = op._text.charAt(0);
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
                                            new Precedence( 5,        "<>=⧁⧀⊜⩹⩺⩻⩼⩽⩾⩿⪀⪁⪂⪃⪄⪅⪆⪇⪈⪉⪊⪋⪌⪍⪎⪏⪐⪑⪒⪓⪔⪕⪖⪗⪘⪙⪚⪛⪜⪝⪞⪟⪠⪡⪢⪤⪥⪦⪧⪨⪩⪪⪫⪬⪭⪮⪯⪰⪱⪲⪴⪵⪶⪷⪸⪹⪺⪻⪼⫷⫸⫹⫺≟≤≥"),
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
     * Constructor for operator with given initial chars and equal precedence
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
     * Constructor for operator with given initial chars and given precedence
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
   * Does the given operator bind to the right?
   */
  boolean isRightToLeft(Operator op)
  {
    char c = op._text.charAt(0);
    return RIGHT_TO_LEFT_CHARS.indexOf(c) >= 0;
  }

  /**
   * Initial characters of operators that bind to the right.
   */
  public final String RIGHT_TO_LEFT_CHARS = "^";

}

/* end of file */
