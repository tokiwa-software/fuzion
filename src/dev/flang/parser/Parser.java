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
 * Source of class Parser
 *
 *---------------------------------------------------------------------*/

package dev.flang.parser;

import java.nio.file.Path;

import dev.flang.util.Errors;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.ast.*;


/**
 * Parser performs the syntactic analysis of Fuzion source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Parser extends Lexer
{


  /*-------------------------  classes / enums  -------------------------*/


  static enum FormalOrActual
  {
    formal,
    actual,
    both
  }


  /*----------------------------  constants  ----------------------------*/


  /*----------------------------  variables  ----------------------------*/


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a parser for the given file
   */
  public Parser(Path fname)
  {
    super(fname);
  }


  /**
   * Fork this parer, used by fork().
   */
  private Parser(Parser original)
  {
    super(original);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Fork this parser, i.e., allow speculative parsing without changing the
   * state of the current parser.
   *
   * @return a new parser instance for the same file and at the same position as
   * this.
   */
  Parser fork()
  {
    return new Parser(this);
  }


  /**
   * Convert current stack trace into a String that contains a list of the names
   * of rules that led to a parsing error.
   *
   * Ex., this might result in
   *
   *   "semi, stmnts (69 times), block, impl, feature, unit"
   *
   * if a semicolon was expected after parsing 69 statements in a block in the
   * impl of a feature in a unit.
   */
  static String parseStack()
  {
    StringBuilder sb = new StringBuilder();
    String lastMethod = null;
    int count = 0;
    for(StackTraceElement el: new Throwable().getStackTrace())
      {
        if (el.getClassName().equals(Parser.class.getName()))
          {
            String m = el.getMethodName();
            if (count != 0 && !m.equals(lastMethod))
              {
                sb.append(sb.length() == 0 ? "" : ", ")
                  .append(lastMethod)
                  .append(count > 1 ? " ("+count+" times)" : "");
                count = 0;
              }
            if (!m.equals("parseStack") &&
                !m.equals("parserDetail") &&
                !m.startsWith("lambda$"))
              {
                count++;
                lastMethod = m;
              }
          }
      }
    if (count != 0)
      {
        sb.append(sb.length() == 0 ? "" : ", ")
          .append(lastMethod);
      }
    return sb.toString();
  }


  /**
   * Create a detail message including the parse stack for a syntax error when
   * parsing the given rule.
   *
   * @param currentRule the rule that found an error.
   */
  static String parserDetail(String currentRule)
  {
    return "While parsing: " + currentRule + ", parse stack: " + parseStack();
  }


  /**
   * Parse a unit, i.e., stmnts followed by Token.t_eof.
   *
unit        : stmnts EOF
            ;
   */
  public List<Stmnt> unit()
  {
    var result = stmnts();
    if (Errors.count() == 0)
      {
        match(Token.t_eof, "Unit");
      }
    return result;
  }


  /**
   * Parse zero or more semicolons
   *
semiOrFlatLF: semi
            | LF
            ;

   */
  boolean semiOrFlatLF()
  {
    int last = lastPos();
    boolean result = last >= 0 && lineNum(last) != lineNum(pos());
    if (!result)
      {
        match(Token.t_semicolon, "semicolon or flat line break");
      }
    semi();
    return result;
  }


  /**
   * Parse zero or more semicolons
   *
semi        : SEMI semi
            |
            ;

   */
  void semi()
  {
    while (current() == Token.t_semicolon)
      {
        next();
      }
  }


  /**
   * Parse a feature:
   *
feature     : routine
            | field
            ;
routine     : visibility
              modifiers
              featNames
              formGens
              formArgs
              returnType
              inherits
              contract
              implRout
            ;
field       : visibility
              modifiers
              featNames
              returnType
              contract
              implFldOrRout
            ;
   */
  FList feature()
  {
    SourcePosition pos = posObject();
    Visi v = visibility();
    int m = modifiers();
    List<List<String>> n = featNames();
    FormalGenerics g = formGens();
    var a = (current() == Token.t_lparen) && fork().skipType() ? new List<Feature>() : formArgs();
    ReturnType r = returnType();
    var hasType = r != NoType.INSTANCE;
    var i = inherits();
    Contract c = contract(true);
    Impl p =
      g == FormalGenerics.NONE &&
      a.isEmpty()              &&
      i.isEmpty()                 ? implFldOrRout(hasType)
                                  : implRout();
    return new FList(pos, v,m,r,n,g,a,i,c,p);
  }


  /**
   * Check if the current position starts a feature declaration, and not an expr
   * such as a call.  Does not change the position of the parser.
   *
   * @return true iff a feature declaration should be parsed.
   */
  boolean isFeaturePrefix()
  {
    return
      isNonEmptyVisibilityPrefix() ||
      isModifiersPrefix() ||
      isNamePrefix() && fork().skipFeaturePrefix();
  }


  /**
   * Check if the current position starts a feature declaration, and not an expr
   * such as a call. Skip part of the declaration.
   *
   * @return true iff the next token(s) start a feature
   */
  boolean skipFeaturePrefix()
  {
    if (isNonEmptyVisibilityPrefix() || isModifiersPrefix())
      {
        return true;
      }
    if (!isNamePrefix())
      {
        return false;
      }
    int pos = pos();
    skipName();
    while (skipDot())
      {
        if (!isNamePrefix())
          {
            return false;
          }
        skipName();
      }
    if (skipComma())
      {
        return true;
      }
    switch (skipFormGensNotActualGens())
      {
      case formal: return true;
      case actual: return false;
      default    : break;
      }
    if ((current() == Token.t_lparen) && fork().skipType())
      {
        return true;
      }
    switch (skipFormArgsNotActualArgs())
      {
      case formal: return true;
      case actual: return false;
      default    : break;
      }
    if (isNonFuncReturnTypePrefix())
      {
        return true;
      }
    var p = this;
    if (isTypePrefix())
      {
        p = fork();
        p.skipType();
      }
    return
      p.isInheritPrefix   () ||
      p.isContractPrefix  () ||
      p.isImplPrefix      ();
  }


  /**
   * Parse visibility
   *
visibility  : visiFlag
            |
            ;
visiFlag    : "export" visiList
            | "private"
            | "protected"
            | "public"
            ;
visiList    : e=visi ( COMMA visiList
                     |
                     )
            ;
  */
  Visi visibility()
  {
    Visi v = Consts.VISIBILITY_LOCAL;
    if (isNonEmptyVisibilityPrefix())
      {
        if (skip(Token.t_export))
          {
            List<List<String>> l = new List<>(visi());
            while (skipComma())
              {
                l.add(visi());
              }
            // NYI: Do something with l
            v = null;
          }
        else if (skip(Token.t_private  )) { v = Consts.VISIBILITY_PRIVATE  ; }
        else if (skip(Token.t_protected)) { v = Consts.VISIBILITY_CHILDREN ; }
        else if (skip(Token.t_public   )) { v = Consts.VISIBILITY_PUBLIC   ; }
        else                              { check(false);                    }
      }
    return v;
  }

  /**
   * Check if the current position starts non-empty visibility flags.  Does not
   * change the position of the parser.
   *
   * @return true iff visibility flags are found
   */
  boolean isNonEmptyVisibilityPrefix()
  {
    switch (current())
      {
      case t_export   :
      case t_private  :
      case t_protected:
      case t_public   : return true;
      default         : return false;
      }
  }


  /**
   * Parse visi
   *
visi        : COLON qual
            | qual
            ;
   */
  List<String> visi()
  {
    if (skipColon())
      {
        // NYI: record ':', i.e., export to all heirs
      }
    List<String> result = qual();
    return result;
  }


  /**
   * Parse qualified name
   *
qual        : name ( dot qual
                   |
                   )
            ;
   */
  List<String> qual()
  {
    List<String> result = new List<>(name());
    while (skipDot())
      {
        result.add(name());
      }
    return result;
  }


  /**
   * Parse name
   *
name        : IDENT                            // all parts of name must be in same line
            | opName
            | "ternary" QUESTION COLON
            | "index" LBRACKET ( ".." RBRACKET
                               | RBRACKET
                               )
            | "set" ( LBRACKET RBRACKET
                    | IDENT
                    )
            ;
   *
   * @return the parsed name, Errors.ERROR_STRING if current location could not be identified as a name.
   */
  String name()
  {
    String result = Errors.ERROR_STRING;
    int pos = pos();
    if (isNamePrefix())
      {
        var oldLine = sameLine(line());
        switch (current())
          {
          case t_ident  : result = identifier(); next(); break;
          case t_infix  :
          case t_prefix :
          case t_postfix: result = opName();  break;
          case t_ternary:
            {
              next();
              if (skip('?'))
                {
                  if (!skipColon())
                    {
                      syntaxError(pos, "':' after 'ternary ?'", "name");
                    }
                }
              else
                {
                  syntaxError(pos, "'? :' after 'ternary'", "name");
                }
              result = "ternary ? :";
              break;
            }
          case t_index  :
            {
              next();
              match(Token.t_lcrochet, "name: index");
              if (isOperator(".."))
                {
                  next();
                  result = "index [..]";
                }
              else
                {
                  result = "index [ ]";
                }
              match(Token.t_rcrochet, "name: index");
              break;
            }
          case t_set    :
            {
              next();
              if (current() == Token.t_lcrochet)
                {
                  match(Token.t_lcrochet, "name: set");
                  match(Token.t_rcrochet, "name: set");
                  result = "index [ ] =";
                }
              else if (current() == Token.t_ident)
                {
                  result = identifier() + " =";
                  match(Token.t_ident, "name: set");
                }
              else
                {
                  syntaxError(pos, "'[ ]' or identifier after 'set'", "name");
                  result = Errors.ERROR_STRING;
                }
              break;
            }
          default: check(false);
          }
        sameLine(oldLine);
      }
    else
      {
        syntaxError(pos, "identifier name, infix/prefix/postfix operator, 'ternary ? :', 'index' or 'set' name", "name");
      }
    return result;
  }


  /**
   * Check if the current position starts name.  Does not change the position of
   * the parser.
   *
   * @return true iff the next token(s) start a name
   */
  boolean isNamePrefix()
  {
    switch (current())
      {
      case t_ident  :
      case t_infix  :
      case t_prefix :
      case t_postfix:
      case t_ternary:
      case t_index  :
      case t_set    : return true;
      default       : return false;
      }
  }


  /**
   * Check if the current position has a name and skip it.
   *
   * @return true iff the next token(s) form a name.
   */
  boolean skipName()
  {
    boolean result = isNamePrefix();
    if (result)
      {
        name();
      }
    return result;
  }


  /**
   * Parse opName
   *
opName      : "infix"   op
            | "prefix"  op
            | "postfix" op
            ;
   *
   */
  String opName()
  {
    String inPrePost = current().keyword();
    next();
    String n = inPrePost + " " + operatorOrError();
    match(Token.t_op, "infix/prefix/postfix name");
    return n;
  }


  /**
   * Parse modifiers flags
   *
modifiers   : modifier modifiers
            |
            ;
modifier    : "lazy"
            | "synchronized"
            | "redef"
            | "redefine"
            | "const"
            | "leaf"
            ;
   *
   * @return logically or'ed set of Consts.MODIFIER_* constants found.
   */
  int modifiers()
  {
    int ms = 0;
    int pos = pos();
    while (isModifiersPrefix())
      {
        int m;
        int p2 = pos();
        switch (current())
          {
          case t_lazy        : m = Consts.MODIFIER_LAZY        ; break;
          case t_synchronized: m = Consts.MODIFIER_SYNCHRONIZED; break;
          case t_redef       : m = Consts.MODIFIER_REDEFINE    ; break;
          case t_redefine    : m = Consts.MODIFIER_REDEFINE    ; break;
          case t_const       : m = Consts.MODIFIER_CONST       ; break;
          case t_leaf        : m = Consts.MODIFIER_LEAF        ; break;
          default            : check(false); m = -1; // not reached.
          }
        if ((ms & m) != 0)
          {
            Errors.error(posObject(pos),
                         "Syntax error: modifier '"+current().keyword()+"' specified repeatedly.",
                         "Within one feature declaration, each modifier may at most appear once.\n" +
                         "Second occurence of modifier at " + posObject(p2) + "\n" +
                         "Parse stack: " + parseStack());
          }
        ms = ms | m;
        next();
      }
    return ms;
  }


  /**
   * Check if the current position starts non-empty modifieres flags.  Does not
   * change the position of the parser.
   *
   * @return true iff the next token(s) start a name
   */
  boolean isModifiersPrefix()
  {
    switch (current())
      {
      case t_lazy        :
      case t_synchronized:
      case t_redef       :
      case t_redefine    :
      case t_const       :
      case t_leaf        : return true;
      default            : return false;
      }
  }


  /**
   * Parse featNames
   *
featNames   : qual (COMMA featNames
                   |
                   )
            ;
   */
  List<List<String>> featNames()
  {
    List<List<String>> result = new List<>(qual());
    while (skipComma())
      {
        result.add(qual());
      }
    return result;
  }


  /**
   * Parse formGens
   *
formGens    : "<" formGensBody ">"
            | "<" ">"
            |
            ;
formGensBody: l=genericList ( "..."
                            |
                            )
            |
            ;
genericList : e=generic  ( COMMA genericList
                         |
                         )
            ;
   */
  FormalGenerics formGens()
  {
    FormalGenerics result = FormalGenerics.NONE;
    if (splitSkip("<"))
      {
        if (!isOperator('>'))
          {
            List<Generic> lg = new List<>(generic(0));
            while (skipComma())
              {
                lg.add(generic(lg.size()));
              }
            result = new FormalGenerics(lg, splitSkip("..."));
          }
        matchOperator(">", "formGens");
      }
    return result;
  }


  /**
   * Check if the current position is a formGens or an actualGens.  Changes the
   * position of the parser: In case the Tokens encountered could be parsed both
   * as formGens or as actualGens, skip them and return FormalOrActual.both.
   *
   * Otherwise, this will stop parsing at an undefined position and return
   * FormalOrActual.formal or FormalOrActual.actual, depending on whether formal
   * or actual generics were found.
   *
   * @return FormalOrActual.formal/actual if this could be decided,
   * FormalOrActual.both if both could be parsed.
   */
  FormalOrActual skipFormGensNotActualGens()
  {
    if (splitSkip("<"))
      {
        if (!isOperator('>'))
          {
            do
              {
                if (!skip(Token.t_ident))
                  {
                    return FormalOrActual.actual;
                  }
                if (skipColon())
                  {
                    return FormalOrActual.formal;
                  }
              }
            while (skipComma());
            splitOperator("...");
            if (isOperator("..."))
              {
                return FormalOrActual.formal;
              }
            if (!skip('>'))
              {
                return FormalOrActual.actual;
              }
          }
      }
    return FormalOrActual.both;
  }


  /**
   * Parse generic
   *
generic     : IDENT
              ( COLON type
              |
              )
            ;
   */
  Generic generic(int index)
  {
    Generic g;
    SourcePosition pos = posObject();
    String i = identifierOrError();
    match(Token.t_ident, "generic");
    if (skipColon())
      {
        g = new Generic(pos, index, i, type());
      }
    else
      {
        g = new Generic(pos, index, i);
      }
    return g;
  }


  /**
   * Parse formal argument list
   *
formArgs    : LPAREN argLst RPAREN
            |
            ;
argLst      : argList
            |
            ;
argList     : argument ( COMMA argList
                       |
                       )
            ;
argument    : visibility
              modifiers
              argNames
              type
              contract
            ;
   */
  List<Feature> formArgs()
  {
    return relaxLineAndSpaceLimit(() -> {
        List<Feature> result = new List<>();
        if (skipLParen())
          {
            if (isNonEmptyVisibilityPrefix() || isModifiersPrefix() || isArgNamesPrefix())
              {
                do
                  {
                    SourcePosition pos = posObject();
                    Visi v = visibility();
                    int m = modifiers();
                    List<String> n = argNames();
                    Type t = type();
                    Contract c = contract();
                    for (String s : n)
                      {
                        result.add(new Feature(pos, v, m, t, s, c));
                      }
                  }
                 while (skipComma());
              }
            match(Token.t_rparen, "formArgs");
          }
        return result;
      });
  }


  /**
   * Check if the current position is a formArgs and skip it if this is the
   * case.
   *
   * @return true iff the next token(s) form a formArgs, otherwise no caseField
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipFormArgs()
  {
    return relaxLineAndSpaceLimit(() -> {
        boolean result = skipLParen();
        if (result)
          {
            if (isNonEmptyVisibilityPrefix() || isModifiersPrefix() || isArgNamesPrefix())
              {
                do
                  {
                    visibility();
                    modifiers();
                    result = skipArgNames() && skipType();
                    if (result)
                      {
                        contract();
                      }
                  }
                while (result && skipComma());
              }
            result = result && skip(Token.t_rparen);
          }
        return result;
      });
  }


  /**
   * Check if the current position is a formArgs or an actualArgs.  Changes the
   * position of the parser: In case the Tokens encountered could be parsed both
   * as formArgs or as actualArgs, skip them and return FormalOrActual.both.
   *
   * Otherwise, this will stop parsing at an undefined position and return
   * FormalOrActual.formal or FormalOrActual.actual, depending on whether formal
   * or actual arguments were found.
   *
   * @return FormalOrActual.formal/actual if this could be decided,
   * FormalOrActual.both if both could be parsed.
   */
  FormalOrActual skipFormArgsNotActualArgs()
  {
    return relaxLineAndSpaceLimit(() -> {
        if (skipLParen())
          {
            if (skip(Token.t_rparen))
              {
                return FormalOrActual.both;
              }
            do
              {
                if (isNonEmptyVisibilityPrefix() || isModifiersPrefix())
                  {
                    return FormalOrActual.formal;
                  }
                do
                  {
                    if (!isArgNamesPrefix())
                      {
                        return FormalOrActual.actual;
                      }
                    skipName();
                  }
                while (skipComma());
                if (!skipType())
                  {
                    return FormalOrActual.actual;
                  }
              }
            while (skipComma());
            if (!skip(Token.t_rparen))
              {
                return FormalOrActual.actual;
              }
          }
        return FormalOrActual.both;
      });
  }


  /**
   * Parse argNames
   *
argNames    : name ( COMMA argNames
                   |
                   )
            ;
   */
  List<String> argNames()
  {
    List<String> result = new List<>(name());
    while (skipComma())
      {
        result.add(name());
      }
    return result;
  }


  /**
   * Check if the current position starts argNames.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start argNames
   */
  boolean isArgNamesPrefix()
  {
    return isNamePrefix();
  }


  /**
   * Check if the current position is an argNames and skip it if this is the
   * case.
   *
   * @return true iff the next token(s) form an argNames, otherwise no argNames
   * was found and the parser/lexer is at an undefined position..
   */
  boolean skipArgNames()
  {
    boolean result;
    do
      {
        result = skipName();
      }
    while (result && skipComma());
    return result;
  }


  /**
   * Parse returnType
   *
returnType  : type
            | "value"
            | "ref"
            |
            ;
   */
  ReturnType returnType()
  {
    ReturnType result;
    if (isType())
      {
        result = new FunctionReturnType(type());
      }
    else
      {
        switch (current())
          {
          case t_value : next(); result = ValueType .INSTANCE; break;
          case t_ref   : next(); result = RefType   .INSTANCE; break;
          default      :         result = NoType    .INSTANCE; break;
          }
      }
    return result;
  }


  /**
   * Check if the current position starts a returnType that is not a
   * FunctioNReturnType.  Does not change the position of the parser.
   *
   * @return true iff the next token(s) start a constructor return type.
   */
  boolean isNonFuncReturnTypePrefix()
  {
    switch (current())
      {
      case t_value :
      case t_ref   : return true;
      default      : return false;
      }
  }


  /**
   * Check if the current position is a returnType that is not a
   * FunctionReturnType and skip it if this is the case.
   *
   * @return true iff the next token(s) start a constructor return type,
   * otherwise no functionReturnType was found and the parser/lexer is at an
   * undefined position.
   */
  boolean skipNonFuncReturnType()
  {
    return
      skip(Token.t_value ) ||
      skip(Token.t_ref   );
  }


  /**
   * Check if the current position starts a returnType.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a returnType.
   */
  boolean isReturnTypePrefix()
  {
    return isTypePrefix() || isNonFuncReturnTypePrefix();
  }


  /**
   * Check if the current position is a, possibly empty, returnType followed by
   * a ':'.  Does not change the position of the parser.
   *
   * @return true iff the next token(s) are a returnType followed by ':'
   */
  boolean isReturnTypeFollowedByColon()
  {
    return
      isOperator(':') ||
      isNonFuncReturnTypePrefix() && fork().skipReturnTypeFollowedByColonPrefix();
  }


  /**
   * Check if the current position is a, possibly empty, returnType followed by
   * a ':'. Skip an unspecified part of it.
   *
   * @return true iff the next token(s) are a returnType followed by ':'
   */
  boolean skipReturnTypeFollowedByColonPrefix()
  {
    return
      isOperator(':') ||
      skipNonFuncReturnType() && isOperator(':') ||
      isTypeFollowedByColon();
  }


  /**
   * Parse optional inherits clause
   *
inherits    : inherit
            |
            ;
   */
  List<AbstractCall> inherits()
  {
    return isInheritPrefix() ? inherit() : new List<>();
  }


  /**
   * Parse inherit clause
   *
inherit     : COLON callList
            ;
   */
  List<AbstractCall> inherit()
  {
    matchOperator(":", "inherit");
    return callList();
  }


  /**
   * Check if the current position starts an inherit.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an inherit.
   */
  boolean isInheritPrefix()
  {
    return isOperator(':');
  }


  /**
   * Parse callList
   *
callList    : call ( COMMA callList
                   |
                   )
            ;
   */
  List<AbstractCall> callList()
  {
    var result = new List<AbstractCall>(call(null));
    while (skipComma())
      {
        result.add(call(null));
      }
    return result;
  }


  /**
   * Parse call
   *
call        : name ( actualGens actualArgs callTail
                   | dot ( NUM_LITERAL callTail
                         | call
                         )
                   )
            ;
   */
  Call call(Expr target)
  {
    SourcePosition pos = posObject();
    var line = line();
    String n = name();
    Call result;
    if (skipDot())
      {
        if (current() == Token.t_numliteral)
          {
            var select = skipNumLiteral();
            result = new Call(pos, target, n, select == null ? "0" : select.plainInteger());
            result = callTail(result);
          }
        else
          {
            result = new Call(pos, target, n);
            result = call(result);
          }
      }
    else
      {
        // we must check isActualGens() to distinguish the less operator in 'a < b'
        // from the actual generics in 'a<b>'.
        var g = (!isActualGens()) ? Call.NO_GENERICS : actualGens();
        var l = actualArgs(line);
        result = new Call(pos, target, n, g, l);
        result = callTail(result);
      }
    return result;
  }


  /**
   * Parse indexcall
   *
indexCall   : ( LBRACKET exprList RBRACKET
                ( ":=" exprInLine
                |
                )
              )
            ;
   */
  Call indexCall(Expr target)
  {
    Call result;
    do
      {
        SourcePosition pos = posObject();
        next();
        var l = relaxLineAndSpaceLimit(() -> {
            var res = exprList();
            match(Token.t_rcrochet, "indexCall");
            return res;
          });
        if (skip(":="))
          {
            l.add(exprInLine());
            result = new Call(pos, target, "index [ ] =", null, l);
          }
        else
          {
            result = new Call(pos, target, "index [ ]"  , null, l);
          }
        target = result;
      }
    while (!ignoredTokenBefore() && current() == Token.t_lcrochet);
    return result;
  }


  /**
   * Parse callTail
   *
callTail    : ( indexCall
              |
              )
              ( dot call
              |
              )
            ;
   */
  Call callTail(Call target)
  {
    Call result = target;
    if (!ignoredTokenBefore() && current() == Token.t_lcrochet)
      {
        result = indexCall(result);
      }
    if (skipDot())
      {
        result = call(result);
      }
    return result;
  }


  /**
   * Parse actualGens
   *
actualGens  : "<" typeList ">"
            | "<" ">"
            |
            ;
   */
  List<AbstractType> actualGens()
  {
    var result = Call.NO_GENERICS;
    if (splitSkip("<"))
      {
        result = Type.NONE;
        splitOperator(">");
        if (!isOperator('>'))
          {
            result = typeList();
          }
        splitOperator(">");
        matchOperator(">", "formGens");
      }
    return result;
  }


  /**
   * Check if the current position has actualGens.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) form actualGens.
   */
  boolean isActualGens()
  {
    // NYI: isActualGensPrefix would be sufficient. This currently causes
    // confusing error message in case of a syntax error late in the actual
    // generics, as in
    //
    //  t := tuple<i32, bool, String, tuple < int < bool >>();
    return fork().skipActualGens();
  }


  /**
   * Check if the current position has actualGens and skip them.
   *
   * @return true iff the next token(s) form actualGens, otherwise no actualGens
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipActualGens()
  {
    boolean result = true;
    if (splitSkip("<"))
      {
        if (!splitSkip(">"))
          {
            result = skipTypeList() && splitSkip(">");
          }
      }
    return result;
  }


  /**
   * Parse typeList
   *
typeList    : type ( COMMA typeList
                   |
                   )
            ;
   */
  List<AbstractType> typeList()
  {
    List<AbstractType> result = new List<>(type());
    while (skipComma())
      {
        result.add(type());
      }
    return result;
  }


  /**
   * Check if the current position has typeList and skip it.
   *
   * @return true iff the next token(s) form typeList, otherwise no typeList was
   * found and the parser/lexer is at an undefined position.
   */
  boolean skipTypeList()
  {
    boolean result = skipType();
    while (skipComma())
      {
        result = result && skipType();
      }
    return result;
  }


  /**
   * Parse actualArgs
   *
   * @param line the line containing the name of the called feature
   *
actualArgs  : actualsList               // must be in same line as name of called feature
            | LPAREN exprList RPAREN
            | LPAREN RPAREN
            ;
   */
  List<Expr> actualArgs(int line)
  {
    List<Expr> result = relaxLineAndSpaceLimit(() -> {
        List<Expr> res = null;
        if (!ignoredTokenBefore() && skipLParen())
          {
            if (current() != Token.t_rparen)
              {
                res = exprList();
              }
            else
              {
                res = new List<>();
              }
            match(Token.t_rparen, "actualArgs");
          }
        return res;
      });
    if (result == null)
      {
        result = actualsList(line);
      }
    return result;
  }


  /**
   * Does the current symbol end a list of space separated actual arguments to a
   * call.
   *
   * @param in the indentation used for the actuals, null if none.
   *
   * @return true if the next symbold ends actual arguments or in!=null and the
   * next symbol is not properly indented.
   */
  boolean endsActuals(Indentation in)
  {
    return
      (in != null) ? currentAtMinIndent() == Token.t_indentationLimit ||
                     endsActuals(currentNoLimit()) ||
                     !in.ok()
                   : endsActuals(current());
  }


  /**
   * Does the given current tokenl end a list of space separated actual arguments to a
   * call.
   *
   * @param t the token
   *
   * @return true if t ends actual arguments
   */
  boolean endsActuals(Token t)
  {
    return
      switch (t)
      {
      case t_semicolon       ,
           t_comma           ,
           t_rparen          ,
           t_rcrochet        ,
           t_lbrace          ,
           t_rbrace          ,
           t_is              ,
           t_pre             ,
           t_post            ,
           t_inv             ,
           t_require         ,
           t_ensure          ,
           t_invariant       ,
           t_if              ,
           t_then            ,
           t_else            ,
           t_for             ,
           t_do              ,
           t_while           ,
           t_until           ,
           t_stringBD        ,
           t_stringBQ        ,
           t_stringBB        ,
           t_indentationLimit,
           t_lineLimit       ,
           t_spaceLimit      ,
           t_eof             -> true;

      // !ignoredTokenBefore(): We have an operator '-' like this 'f-xyz', 'f-
      // xyz', i.e, stuck to the called function, we do not parse it as part
      // of the args.
      //
      // ignoredTokenBefore(): An operator '-' like this 'f a b - xyz', so the
      // arg list ends with 'b' and '-' will be parsed as an infix operator on
      // 'f a b' and 'xyz'.
      case t_op            -> !ignoredTokenBefore() || ignoredTokenAfter();

      // No more actuals if we have a string continuation as in "value $x is
      // ok" for the string after '$x' or in "bla{f a b}blub" for the string
      // after 'f a b'.
      default              -> isContinuedString(t);
      };
  }


  /**
   * Parse
   *
exprList    : expr ( COMMA exprList
                   |
                   )
            ;
   */
  List<Expr> exprList()
  {
    List<Expr> result = new List<>(expr());
    while (skipComma())
      {
        result.add(expr());
      }
    return result;
  }


  /**
   * Parse
   *
   * @param line the line containing the name of the called feature
   *
actualsList : exprUntilSp actualsLst
            | exprUntilSp actualsLstC
            |
            ;
actualsLst  : exprUntilSp actualsLst
            |
            ;
actualsLstC : COMMA expr actualsLstC
            |
            ;
   */
  List<Expr> actualsList(int line)
  {
    Indentation in = null;
    if (line() != line && current() != Token.t_lineLimit)
      {
        line = -1;
        in = new Indentation();
      }
    var oldLine = sameLine(line);
    List<Expr> result = Call.NO_PARENTHESES;
    if (ignoredTokenBefore() && !endsActuals(in))
      {
        result = new List<>(exprUntilSpace());
        var hasComma = current() == Token.t_comma;
        if (hasComma)
          {
            while (skipComma())
              {
                result.add(expr());
              }
          }
        else
          {
            var done = false;
            while (!done)
              {
                if (in == null && line() != line && oldLine == -1)
                  { // indentation starts after the first argument:
                    line = -1;
                    sameLine(-1);
                    in = new Indentation();
                  }
                done = endsActuals(in);
                if (!done)
                  {
                    var p = pos();
                    result.add(exprUntilSpace());
                    done = p == pos(); /* make sure we do not get stuck on a syntax error */
                  }
              }
          }
      }
    sameLine(oldLine);
    if (in != null)
      {
        in.end();
      }
    return result;
  }


  /**
   * A bracketTerm
   *
bracketTerm : block
            | klammer
            | inlineArray
            ;
   */
  Expr bracketTerm(boolean mayBeAtMinIndent)
  {
    if (PRECONDITIONS) require
      (current() == Token.t_lbrace   ||
       current() == Token.t_lparen   ||
       current() == Token.t_lcrochet   );

    var c = current();
    switch (c)
      {
      case t_lbrace  : return block(mayBeAtMinIndent);
      case t_lparen  : return klammer();
      case t_lcrochet: return inlineArray();
      default: throw new Error("Unexpected case: "+c);
      }
  }


  /**
   * An Expr that ends in white space unless enclosed in { }, [ ], or ( ).
   *
exprUntilSp : expr         // no white space except enclosed in { }, [ ], or ( ).
            ;

   */
  Expr exprUntilSpace()
  {
    var eas = endAtSpace(pos());
    var result = expr();
    endAtSpace(eas);
    return result;
  }


  /**
   * An expr that does not exceed a single line unless it is enclosed by { } or
   * ( ).
   *
exprInLine  : expr             // within one line
            | bracketTerm      // stretching over one or several lines
            ;
   */
  Expr exprInLine()
  {
    Expr result;
    int line = line();
    int oldLine = sameLine(-1);
    var c = current();
    switch (c)
      {
      case t_lbrace:
      case t_lparen:
      case t_lcrochet:
        { // allow
          //
          //   { a; b } + c
          //
          //   { a; b
          //   }
          //   .f
          //
          // but not
          //
          //   { a; b
          //   }
          //   + c
          var f = fork();
          f.bracketTerm(false);
          if (f.line() != line && f.isOperator('.'))
            {
              line = -1;
            }
          break;
        }
      default:
        break;
      }
    sameLine(line);
    result = expr();
    sameLine(oldLine);
    return result;
  }


  /**
   * An expr() that, if it is a block, is permitted to start at minIndent.
   *
exprAtMinIndent : block
                | exprInLine
                ;
   */
  Expr exprAtMinIndent()
  {
    return
      currentAtMinIndent() == Token.t_lbrace ? block(true)
                                             : exprInLine();
  }


  /**
   * Parse
   *
expr        : opExpr
              ( QUESTION expr  COLON expr
              | QUESTION cases
              |
              )
            ;
   */
  Expr expr()
  {
    Expr result = opExpr();
    SourcePosition pos = posObject();
    if (skip('?'))
      {
        if (isCasesAndNotExpr())
          {
            result = new Match(pos, result, cases(false));
          }
        else
          {
            Expr f = expr();
            matchOperator(":", "expr of the form >>a ? b : c<<");
            Expr g = expr();
            result = new Call(pos, result, "ternary ? :", null, new List<Expr>(f, g));
          }
      }
    return result;
  }


  /**
   * Parse opExpr
   *
opExpr      : ( op
              )*
              opTail
            ;
   */
  Expr opExpr()
  {
    OpExpr oe = new OpExpr();
    if (isOpPrefix())
      {
        do
          {
            oe.add(op());
          }
        while (isOpPrefix());
      }
    oe.add(opTail());
    return oe.toExpr();
  }


  /**
   * Parse opTail
   *
opTail      : term
              ( ( op )+
                ( opTail
                |
                )
              |
              )
            ;
   */
  OpExpr opTail()
  {
    OpExpr oe = new OpExpr();
    oe.add(term());
    if (isOpPrefix())
      {
        do
          {
            oe.add(op());
          }
        while (isOpPrefix());
        if (isTermPrefix())
          {
            oe.add(opTail());
          }
      }
    return oe;
  }


  /**
   * Parse klammer is either a single parenthesized expression or a tuple
   *
klammer     : klammerExpr
            | tuple
            | klammerLambd
            ;
klammerExpr : LPAREN expr RPAREN
            ;
tuple       : LPAREN RPAREN
            | LPAREN expr (COMMA expr)+ RPAREN
            ;
klammerLambd: LPAREN argNamesOpt RPAREN lambda
            ;
   */
  Expr klammer()
  {
    SourcePosition pos = posObject();
    match(Token.t_lparen, "klammer");
    var f = fork();
    var tupleElements = relaxLineAndSpaceLimit(() -> {
        List<Expr> elements = new List<>();
        if (!skip(Token.t_rparen)) // not an empty tuple
          {
            do
              {
                elements.add(expr());
              }
            while (skipComma());
            match(Token.t_rparen, "klammer");
          }
        return elements;
      });
    return
      isLambdaPrefix()          ? lambda(f.relaxLineAndSpaceLimit(() ->
                                                                  { var r = f.argNamesOpt();
                                                                    if (!f.skip(Token.t_rparen) || f.pos() != pos())
                                                                      {
                                                                        f.syntaxError(f.pos(), "plain list of argument names (argNameOpt) before lambda", "klammer");
                                                                      }
                                                                    return r;
                                                                  })) :
      tupleElements.size() == 1 ? tupleElements.get(0) // a klammerexpr, not a tuple
                                : new Call(pos, null, "tuple", tupleElements);
  }


  /**
   * Parse argNamesOpt
   *
argNamesOpt : argNames
            |
            ;
   */
  List<String> argNamesOpt()
  {
    return (current() == Token.t_ident)
      ? argNames()
      : new List<>();
  }


  /**
   * Parse a simple lambda expression, i.e., one without parentheses around the
   * arguments.
   *
lambda      : contract "->" block
            ;
   */
  Expr lambda(List<String> n)
  {
    SourcePosition pos = posObject();
    var i = new List<AbstractCall>(); // inherits() is not supported for lambda, do we need it?
    Contract   c = contract();
    matchOperator("->", "lambda");
    return new Function(pos, n, i, c, (Expr) block(true));
  }


  /**
   * Check if the current position starts a lambda.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a plainLambda.
   *
   */
  boolean isLambdaPrefix()
  {
    var f = this;
    if (isContractPrefix()) // fork only if really needed
      {
        f = fork();
        f.contract();
      }
    return f.isOperator("->");
  }


  /**
   * Parse a simple lambda expression, i.e., one without parentheses around the
   * arguments.
   *
plainLambda : argNames lambda
            ;
   */
  Expr plainLambda()
  {
    return lambda(argNames());
  }


  /**
   * Check if the current position starts a plainLambda.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a plainLambda.
   *
   */
  boolean isPlainLambdaPrefix()
  {
    var f = fork();
    return f.skipArgNames() && f.isLambdaPrefix();
  }


  /**
   * Parse inlineArray
   *
inlineArray : LBRACKET expr (COMMA expr)+ RBRACKET
            | LBRACKET expr (SEMI  expr)+ RBRACKET
            ;
   */
  Expr inlineArray()
  {
    return relaxLineAndSpaceLimit(() -> {
        SourcePosition pos = posObject();
        match(Token.t_lcrochet, "inlineArray");
        List<Expr> elements = new List<>();
        if (!skip(Token.t_rcrochet)) // not empty array
          {
            elements.add(expr());
            var sep = current();
            var s = sep;
            var p1 = pos();
            boolean reportedMixed = false;
            while ((s == Token.t_comma || s == Token.t_semicolon) && skip(s))
              {
                elements.add(expr());
                s = current();
                if ((s == Token.t_comma || s == Token.t_semicolon) && s != sep && !reportedMixed)
                  {
                    AstErrors.arrayInitCommaAndSemiMixed(pos, posObject(p1), posObject());
                    reportedMixed = true;
                  }
              }
            match(Token.t_rcrochet, "inlineArray");
          }
        return new InlineArray(pos, elements);
      });
  }


  /**
   * Parse term
   *
term        : simpleterm ( indexCall
                         |
                         )           ( dot call
                                     |
                                     )
            ;
simpleterm  : bracketTerm
            | fun
            | stringTerm
            | NUM_LITERAL
            | "old" term
            | match
            | loop
            | ifstmnt
            | callOrFeatOrThis
            ;
   */
  Expr term()
  {
    Expr result;
    int p1 = pos();
    switch (current()) // even if this is t_lbrace, we want a term to be indented, so do not use currentAtMinIndent().
      {
      case t_lbrace    :
      case t_lparen    :
      case t_lcrochet  :         result = bracketTerm(true);                           break;
      case t_fun       :         result = fun();                                       break;
      case t_numliteral: var l = skipNumLiteral();
                         var m = l.mantissaValue();
                         var b = l.mantissaBase();
                         var d = l.mantissaDotAt();
                         var e = l.exponent();
                         var eb = l.exponentBase();
                         var o = l._originalString;
                         result = new NumLiteral(posObject(p1), o, b, m, d, e, eb); break;
      case t_old       : next(); result = new Old(term()                            ); break;
      case t_match     :         result = match();                                     break;
      case t_for       :
      case t_variant   :
      case t_while     :
      case t_do        :         result = loop();                                      break;
      case t_if        :         result = ifstmnt();                                   break;
      default          :
        if (isStartedString(current()))
          {
            result = stringTerm(null);
          }
        else
          {
            result = callOrFeatOrThis();
            if (result == null)
              {
                syntaxError(p1, "term (lbrace, lparen, lcrochet, fun, string, integer, old, match, or name)", "term");
                result = new Call(posObject(), null, Errors.ERROR_STRING);
              }
          }
        break;
      }
    if (!ignoredTokenBefore() && current() == Token.t_lcrochet)
      {
        result = indexCall(result);
      }
    if (skipDot())
      {
        result = call(result);
      }
    return result;
  }


  /**
   * Parse stringTerm
   *
stringTerm  : STRING
            // NYI string interpolation
            // | STRING$ ident stringTerm
            // | STRING{ block stringTerm
            ;
  */
  Expr stringTerm(Expr leftString)
  {
    return relaxLineAndSpaceLimit(() -> {
        Expr result = leftString;
        var t = current();
        if (isString(t))
          {
            var str = new StrConst(posObject(), "\""+string()+"\"" /* NYI: remove "\"" */);
            result = concatString(posObject(), leftString, str);
            next();
            if (isPartialString(t))
              {
                result = stringTerm(concatString(posObject(), result, block(false)));
              }
          }
        else
          {
            Errors.expectedStringContinuation(posObject(), currentAsString());
          }
        return result;
      });
  }


  /**
   * Concatenate two strings using a call to 'infix +' on the first string given
   * the second as an argument.
   *
   * @param string1 an expression or null in cas string2 should be the result
   *
   * @param string2 an expression that is not null.
   *
   * @return a call to 'infix +' on string1 with string2 as an argument, or, if
   * string1 is null, just string2.
   */
  Expr concatString(SourcePosition pos, Expr string1, Expr string2)
  {
    return string1 == null ? string2 : new Call(pos, string1, "infix +", new List<>(string2));
  }


  /**
   * Check if the current position starts a term.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) start a term.
   */
  boolean isTermPrefix()
  {
    switch (current()) // even if this is t_lbrace, we want a term to be indented, so do not use currentAtMinIndent().
      {
      case t_lparen    :
      case t_lcrochet  :
      case t_lbrace    :
      case t_fun       :
      case t_numliteral:
      case t_old       :
      case t_match     : return true;
      default          :
        return
          isStartedString(current())
          || isNamePrefix()    // Matches call and qualThis
          || isAnonymousPrefix() // matches anonymous inner feature declaration
          ;
      }
  }


  /**
   * Parse op
   *
op          : OPERATOR
            ;
   */
  Operator op()
  {
    if (PRECONDITIONS) require
      (isOpPrefix());

    Operator result = new Operator(posObject(), operator(), ignoredTokenBefore(), ignoredTokenAfter());
    match(Token.t_op, "op");
    return result;
  }


  /**
   * Check if the current position starts an op.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) start an op.
   */
  boolean isOpPrefix()
  {
    var result =
      current() == Token.t_op
      && !isOperator('?');  // NYI: create a token for '?'.

    return result;
  }


  /**
   * Parse fun
   *
fun         : "fun" function
            | "fun" c=call
            ;
   */
  Expr fun()
  {
    Expr result;
    int p1 = pos();
    SourcePosition pos = posObject();
    match(Token.t_fun, "fun");
    if (isFunctionPrefix())
      {
        result = function(pos);
      }
    else
      {
        result = new Function(pos, call(null));
      }
    return result;
  }


  /**
   * Parse function
   *
function    : formArgs
              typeOpt
              inherits
              contract
              ( block
              | "is" block
              | ARROW e=block
              )
            ;
   */
  Function function(SourcePosition pos)
  {
    List<Feature> a = formArgs();
    ReturnType r = NoType.INSTANCE;
    if (isType())
      {
        r = new FunctionReturnType(type());
      }
    var i = inherits();
    Contract   c = contract();
    Function result;
    if (isOperator("=>"))
      {
        next();
        result = new Function(pos, r, a, i, c, (Expr) block(true));
      }
    else
      {
        if (!skip(Token.t_is) && current() != Token.t_lbrace)
          {
            syntaxError(pos(), "'is', '{' or '=>' in inline function declaration", "function");
          }
        if (r == NoType.INSTANCE)
          {
            r = new FunctionReturnType(new Type("unit"));
          }
        result = new Function(pos, r, a, i, c, block(false));
      }
    return result;
  }


  /**
   * Check if the current position starts a function.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a function.
   */
  boolean isFunctionPrefix()
  {
    switch (current()) // even if this is t_lbrace, we want the
                       // require/invariant/ensure/{ in a "fun { .. }" to be
                       // indented, so do not use currentAtMinIndent().
      {
      case t_lparen   :
      case t_require  :
      case t_invariant:
      case t_ensure   :
      case t_pre      :
      case t_post     :
      case t_inv      :
      case t_lbrace   : return true;
      default         : return isOperator("=>") || isTypeFollowedByLBrace();
      }
  }


  /**
   * Parse match
   *
match       : "match" exprInLine BRACEL cases BRACER
            ;
   */
  Expr match()
  {
    return relaxLineAndSpaceLimit(() -> {
        SourcePosition pos = posObject();
        match(Token.t_match, "match");
        Expr e = exprInLine();
        boolean gotLBrace = skip(true, Token.t_lbrace);
        var c = cases(true);
        if (gotLBrace)
          {
            match(true, Token.t_rbrace, "block");
          }
        return new Match(pos, e, c);
      });
  }


  /**
   * Parse cases
   *
cases       : caze maybecomma ( '|' casesBars   // NYI: grammar not correct yet.
                              |     casesNoBars
                              )
            ;
casesBars   : caze maybecomma ( '|' casesBars
                              |
                              )
            ;
caseNoBars  : caze maybecomma ( casesNoBars
                              |
                              )
            ;
# NYI: grammar not correct yet.
casesNoBars : caseNoBars caseNoBars
            | caseNoBars
            ;
maybecomma  : comma
            |
            ;
   */
  List<AbstractCase> cases(boolean indent)
  {
    List<AbstractCase> result = new List<>();
    var in = indent ? new Indentation() : (Indentation) null;
    var sl = -1;
    var usesBars = false;
    while (!endOfStmnts() && (in == null || in.ok()))
      {
        if (result.size() == 0 && indent)
          {
            usesBars = skip('|');
          }
        else if (result.size() == 1 && !indent)
          {
            sl = sameLine(-1);
            in = new Indentation();
            usesBars = skip('|');
          }
        else if (usesBars)
          {
            matchOperator("|", "cases");
          }
        result.add(caze());
        skipComma();
        if (!endOfStmnts())
          {
            semiOrFlatLF();
          }
      }
    if (in != null)
      {
        in.end();
      }
    if (sl != -1)
      {
        sameLine(sl);
      }
    return result;
  }


  /**
   * Parse caze
   *
caze        : ( caseFldDcl
              | caseTypes
              | caseStar
              )
            ;
caseFldDcl  : IDENT type caseBlock
            ;
caseTypes   : typeList   caseBlock
            ;
caseStar    : STAR       caseBlock
            ;
   */
  Case caze()
  {
    Case result = null;
    SourcePosition pos = posObject();
    if (skip('*'))
      {
        result = new Case(pos, caseBlock());
      }
    else if (isCaseFldDcl())
      {
        String n = identifier();
        match(Token.t_ident, "caze");
        Type t = type();
        result = new Case(pos, t, n, caseBlock());
      }
    else
      {
        var l = typeList();
        result = new Case(pos, l, caseBlock());
      }
    // NYI: Cleanup: new abstract class CaseCondition with three implementations: star, fieldDecl, typeList.
    return result;
  }


  /**
   * Parse caseBlock
   *
caseBlock   : ARROW          // if followed by '|'
            | ARROW block    // if block does not start with '|'
            ;
   */
  Block caseBlock()
  {
    Block result;
    matchOperator("=>", "caseBlock");
    var oldLine = sameLine(-1);
    var bar = isOperator('|');
    sameLine(oldLine);
    if (bar)
      {
        SourcePosition pos1 = posObject();
        result = new Block(pos1, pos1, new List<>());
      }
    else
      {
        result = block(true);
      }
    return result;
  }


  /**
   * Check if the current position starts a caseField.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a caseField.
   */
  boolean isCaseFldDcl()
  {
    return
      (current() == Token.t_ident) &&
      fork().skipCaseFldDcl();
  }


  /**
   * Check if the current position can be parsed as a caseField and skip it if
   * this is the case.
   *
   * @return true iff a caseField was found and skipped, otherwise no caseField
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipCaseFldDcl()
  {
    return
      skip(Token.t_ident) &&
      skipTypeFollowedByArrow();
  }


  /**
   * Check if the current position starts a caze and not an expr.  Does not
   * change the position of the parser.
   *
   * @return true iff the next token(s) start a caze and not an expr.
   */
  boolean isCasesAndNotExpr()
  {
    return fork().skipCazePrefix();
  }


  /**
   * Check if the current position is starts caze and not an expr and skip an
   * unspecified part of it.
   *
   * @return true iff a caue was found
   */
  boolean skipCazePrefix()
  {
    return
      skip('*') && isOperator("=>") ||
      isCaseFldDcl() ||
      skipTypeList() && isOperator("=>");
  }


  /**
   * Parse block
   *
block       : BRACEL stmnts BRACER
            ;
   */
  Block block(boolean mayBeAtMinIndent)
  {
    SourcePosition pos1 = posObject();
    if (current() == Token.t_semicolon)
      { // we have code like
        //
        //   if cond;
        //
        // or
        //
        //   for x in set
        //   while cond(x);
        //
        // so there is an empty block.
        //
        return new Block(pos1, pos1, new List<>());
      }
    else
      {
        return relaxLineAndSpaceLimit(() -> {
            boolean gotLBrace = skip(mayBeAtMinIndent, Token.t_lbrace);
            var l = stmnts();
            var pos2 = l.size() > 0 ? l.getLast().pos() : pos1;
            if (gotLBrace)
              {
                pos2 = posObject();
                match(mayBeAtMinIndent, Token.t_rbrace, "block");
              }
            return new Block(pos1, pos2, l);
          });
      }
  }


  /**
   * As long as this is false and we make progress, we try to parse more
   * statements within stmnts.
   */
  boolean endOfStmnts()
  {
    return
      currentAtMinIndent() == Token.t_indentationLimit ||
      currentNoLimit() == Token.t_rbrace   ||
      currentNoLimit() == Token.t_rparen   ||
      currentNoLimit() == Token.t_rcrochet ||
      currentNoLimit() == Token.t_until    ||
      currentNoLimit() == Token.t_else     ||
      currentNoLimit() == Token.t_eof      ||
      isContinuedString(currentNoLimit());
  }


  /**
   * Parse stmnts
   *
stmnts      :
            | s=stmnt semiOrFlatLF l=stmnts (semiOrFlatLF | )
            ;
   */
  List<Stmnt> stmnts()
  {
    List<Stmnt> l = new List<>();
    var in = new Indentation()
      {
        boolean handleSurpriseIndentation()
        {
          var result = false;
          // NYI: check if this case may still occur:
          if (!l.isEmpty() && l.getLast() instanceof Feature f && f.impl() == Impl.FIELD)
            { // Let's be nice in the common case of a forgotten 'is'
              syntaxError(pos(), "'is' followed by routine code", "stmtns");
              block(true); // skip the code of the routine.
              result = true;
            }
          return result;
        }
      };
    while (!endOfStmnts() && in.ok())
      {
        Stmnt s = stmnt();
        if (s instanceof FList fl)
          {
            l.addAll(fl._list);
          }
        else
          {
            l.add(s);
          }
        if (!endOfStmnts())
          {
            semiOrFlatLF();
          }
      }
    in.end();
    return l;
  }


  /**
   * Class to handle a block of indented code.  The code should follow this pattern:
   *
   *    var in = new Indentation();
   *    while (!curTokenWouldTerminateListInSingleLine() && in.ok())
   *      {
   *        ... parse element ...
   *      }
   *    in.end();
   */
  class Indentation
  {
    int lastLineNum;    // line number of last call to ok, -1 at beginning
    int firstIndentPos; // source position of the first element
    int firstIndent;    // indentation of the first element
    int oldIndentPos;   // source position of the first element of outer indentation
    int oldIndent;      // indentation outside of this block
    int pos;            // pos of last call to ok(), -1 at beginning

    Indentation()
    {
      lastLineNum = -1;
      firstIndentPos = pos();
      firstIndent = indent(firstIndentPos);
      oldIndentPos = minIndent(firstIndentPos);
      oldIndent = indent(oldIndentPos);
      pos = -1;
    }

    /**
     * Is indentation still ok, i.e, we are still in the same line or in a new
     * line that is properly indented. Also checks if we have made progress, so
     * repeated calls to ok() will cause errors.
     */
    boolean ok()
    {
      var lastPos = pos;
      pos = pos();
      var progress = lastPos < pos;
      var indented = firstIndent > oldIndent;
      var ok = indented && progress;
      check
        (Errors.count() > 0 || progress);
      if (ok && lastLineNum != lineNum(pos))
        { // a new line, so check its indentation:
          var curIndent = indent(pos);
          // NYI: We currently do not check if there are differences in
          // whitespace, e.g. "\t\t" is a very different indentation than
          // "\ \ ", even though both have a length of 2 bytes.
          if (firstIndent < curIndent && !handleSurpriseIndentation() ||
              firstIndent > curIndent)
            {
              Errors.indentationProblemEncountered(posObject(), posObject(firstIndentPos), parserDetail("stmnts"));
            }
          minIndent(pos);
          lastLineNum = lineNum(pos);
        }
      return ok;
    }

    /**
     * Reset indentation to original level.
     */
    void end()
    {
      minIndent(oldIndentPos);
    }

    /**
     * Special handler called when line with deeper indentation is found by ok().
     *
     * @return true iff this was handled and can be ignored, false to produce a
     * syntax error.
     */
    boolean handleSurpriseIndentation()
    {
      return false;
    }

  }


  /**
   * Get the indentation, i.e., the byte-column of the given pos, or 0 for pos==-1
   */
  int indent(int pos)
  {
    return pos >= 0 ? codePointInLine(pos) : 0;
  }


  /**
   * Parse stmnt
   *
stmnt       : feature
            | assign
            | destructure
            | exprInLine
            | checkstmt
            ;
   */
  Stmnt stmnt()
  {
    return
      isCheckPrefix()       ? checkstmnt()  :
      isAssignPrefix()      ? assign()      :
      isDestructurePrefix() ? destructure() :
      isFeaturePrefix()     ? feature()     : exprInLine();
  }


  /**
   * Parse loop
   *
loop        : loopProlog loopBody loopEpilog
            |            loopBody loopEpilog
            | loopProlog loopBody
            |            loopBody
            | loopProlog          loopEpilog
            ;
loopProlog  : indexVars "variant" exprInLine
            | indexVars
            |           "variant" exprInLine
            ;
loopBody    : "while" exprAtMinIndent      block
            | "while" exprAtMinIndent "do" block
            |                         "do" block
            ;
loopEpilog  : "until" exprAtMinIndent thenPart elseBlockOpt
            |                                  elseBlock
            ;
   */
  Expr loop()
  {
    return relaxLineAndSpaceLimit(() -> {
        SourcePosition pos = posObject();
        List<Feature> indexVars  = new List<>();
        List<Feature> nextValues = new List<>();
        var hasFor   = current() == Token.t_for; if (hasFor) { indexVars(indexVars, nextValues); }
        var hasVar   = skip(true, Token.t_variant); var v   = hasVar              ? exprInLine()      : null;
                                                    var i   = hasFor || v != null ? invariant(true)   : null;
        var hasWhile = skip(true, Token.t_while  ); var w   = hasWhile            ? exprAtMinIndent() : null;
        var hasDo    = skip(true, Token.t_do     ); var b   = hasWhile || hasDo   ? block(true)       : null;
        var hasUntil = skip(true, Token.t_until  ); var u   = hasUntil            ? exprAtMinIndent() : null;
                                                    var ub  = hasUntil            ? thenPart(true)    : null;
                                                    var els1 =               fork().elseBlockOpt();
                                                    var els =                       elseBlockOpt();

        if (!hasWhile && !hasDo && !hasUntil && els == null)
          {
            syntaxError(pos(), "loopBody or loopEpilog: 'while', 'do', 'until' or 'else'", "loop");
          }
        return new Loop(pos, indexVars, nextValues, v, i, w, b, u, ub, els, els1).tailRecursiveLoop();
      });
  }


  /**
   * Parse IndexVars
   *
indexVars   : "for" indexVar (semi indexVars)
            |
            ;
   */
  void indexVars(List<Feature> indexVars, List<Feature> nextValues)
  {
    match(Token.t_for, "indexVars");
    var in = new Indentation();
    while (isIndexVarPrefix() && in.ok())
      {
        indexVar(indexVars, nextValues);
        semi();
      }
    in.end();
  }


  /**
   * Parse IndexVar
   *
indexVar    : visibility
              modifiers
              name
              ( type contract implFldInit nextValue
              |      contract implFldInit nextValue
              | type contract implFldIter
              |      contract implFldIter
              )
            ;
implFldIter : "in" exprInLine;
nextValue   : COMMA exprAtMinIndent
            |
            ;
   */
  void indexVar(List<Feature> indexVars, List<Feature> nextValues)
  {
    SourcePosition pos = posObject();
    Parser forked = fork();  // tricky: in case there is no nextValue, we
                             // re-parse the initial value expr and use it
                             // as nextValue
    Visi       v1  =        visibility();
    Visi       v2  = forked.visibility();
    int        m1  =        modifiers();
    int        m2  = forked.modifiers();
    String     n1  =        name();
    String     n2  = forked.name();
    boolean hasType = isType();
    ReturnType r1 = hasType ? new FunctionReturnType(       type()) : NoType.INSTANCE;
    ReturnType r2 = hasType ? new FunctionReturnType(forked.type()) : NoType.INSTANCE;
    Contract   c1 =        contract();
    Contract   c2 = forked.contract();
    Impl p1, p2;
    if (       skip(Token.t_in) &&
        forked.skip(Token.t_in)    )
      {
        p1 = new Impl(posObject(),        exprInLine() /* NYI: better exprAtMinIndent() to be same as FieldInit and FIeldDef? */, Impl.Kind.FieldIter);
        p2 = new Impl(posObject(), forked.exprInLine() /* NYI: better exprAtMinIndent() to be same as FieldInit and FIeldDef? */, Impl.Kind.FieldIter);
      }
    else
      {
        p1 =        implFldInitOrUndef(hasType, false);
        p2 = forked.implFldInitOrUndef(hasType, false);
        // up to here, this and forked parse the same, i.e, v1, m1, .. p1 is the
        // same as v2, m2, .. p2.  Now, we check if there is a comma, which
        // means there is a different value for the second and following
        // iterations:
        if (skipComma())
          {
            p2 = new Impl(pos, exprAtMinIndent(), p2.kind_);
          }
      }
    Feature f1 = new Feature(pos,v1,m1,r1,new List<>(n1),
                             FormalGenerics.NONE,
                             new List<Feature>(),
                             new List<>(),
                             c1,p1);
    Feature f2 = new Feature(pos,v2,m2,r2,new List<>(n2),
                             FormalGenerics.NONE,
                             new List<Feature>(),
                             new List<>(),
                             c2,p2);
    indexVars.add(f1);
    nextValues.add(f2);
  }


  /**
   * Check if the current position starts an indexVar declaration.  Does not
   * change the position of the parser.
   *
   * @return true iff an indexVar declaration should be parsed.
   */
  boolean isIndexVarPrefix()
  {
    var mi = minIndent(-1);
    var result =
      isNonEmptyVisibilityPrefix() ||
      isModifiersPrefix() ||
      isNamePrefix();
    minIndent(mi);
    return result;
  }


  /**
   * Parse cond
   *
cond        : exprInLine
            ;
   */
  Cond cond()
  {
    Expr e = exprInLine();
    return new Cond(e);
  }


  /**
   * Parse ifstmnt
   *
ifstmnt      : "if" exprInLine thenPart elseBlockOpt
            ;
   */
  If ifstmnt()
  {
    return relaxLineAndSpaceLimit(() -> {
        SourcePosition pos = posObject();
        match(Token.t_if, "ifstmnt");
        Expr e = exprInLine();
        Block b = thenPart(false);
        If result = new If(pos, e, b);
        Expr els = elseBlockOpt();
        if (els instanceof If i)
          {
            result.setElse(i);
          }
        else if (els instanceof Block blk)
          {
            result.setElse(blk);
          }
        else
          {
            check
              (els == null);
          }
        return result;
      });
  }


  /**
   * Parse thenPart
   *
thenPart    : "then" block
            |        block
            ;
   */
  Block thenPart(boolean emptyBlockIfNoBlockPresent)
  {
    var p = pos();
    skip(Token.t_then);
    var result = block(true);
    return emptyBlockIfNoBlockPresent && p == pos() ? null : result;
  }


  /**
   * Parse elseBlockOpt
   *
elseBlockOpt: elseBlock
            |
            ;
elseBlock   : "else" ( ifstmnt
                     | block
                     )
            ;
   */
  Expr elseBlockOpt()
  {
    Expr result = null;
    if (skip(true, Token.t_else))
      {
        if (isIfPrefix())
          {
            result = ifstmnt();
          }
        else
          {
            result = block(true);
          }
      }

    if (POSTCONDITIONS) ensure
      (result == null          ||
       result instanceof If    ||
       result instanceof Block    );

    return result;
  }


  /**
   * Check if the current position starts an ifstmnt.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an ifstmnt.
   */
  boolean isIfPrefix()
  {
    return current() == Token.t_if;
  }


  /**
   * Parse checksmnt
   *
checkstmt   : "check" cond
            ;
   */
  Stmnt checkstmnt()
  {
    match(Token.t_check, "checkstmnt");
    return new Check(posObject(), cond());
  }


  /**
   * Check if the current position starts a checkstmnt.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a checkstmnt.
   */
  boolean isCheckPrefix()
  {
    return current() == Token.t_check;
  }


  /**
   * Parse assign
   *
assign      : "set" name ":=" exprInLine
            ;
   */
  Stmnt assign()
  {
    match(Token.t_set, "assign");
    String n = name();
    SourcePosition pos = posObject();
    matchOperator(":=", "assign");
    return new Assign(pos, n, exprInLine());
  }


  /**
   * Check if the current position starts an assign.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an assign.
   */
  boolean isAssignPrefix()
  {
    return (current() == Token.t_set) && fork().skipAssignPrefix();
  }


  /**
   * Check if the current position starts an assign and skip an unspecified part
   * of it.
   *
   * @return true iff the next token(s) start an assign.
   */
  boolean skipAssignPrefix()
  {
    return skip(Token.t_set) && skipName() && isOperator(":=");
  }


  /**
   * Parse destructure
   *
destructure : destructr
            | destructrDcl
            | destructrSet
            ;
destructr   : "(" argNames ")"       ":=" exprInLine
            ;
destructrDcl: formArgs               ":=" exprInLine
            ;
destructrSet: "set" "(" argNames ")" ":=" exprInLine
            ;
   */
  Stmnt destructure()
  {
    if (fork().skipFormArgs())
      {
        var a = formArgs();
        var pos = posObject();
        matchOperator(":=", "destructure");
        return Destructure.create(pos, a, null, false, exprInLine());
      }
    else
      {
        var hasSet = skip(Token.t_set);
        match(Token.t_lparen, "destructure");
        var names = argNames();
        match(Token.t_rparen, "destructure");
        var pos = posObject();
        matchOperator(":=", "destructure");
        return Destructure.create(pos, null, names, !hasSet, exprInLine());
      }
  }


  /**
   * Check if the current position starts destructure.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a destructure.
   */
  boolean isDestructurePrefix()
  {
    return (current() == Token.t_lparen) && (fork().skipDestructrDclPrefix() ||
                                             fork().skipDestructrPrefix()        ) ||
      (current() == Token.t_set) && (fork().skipDestructrPrefix());
  }


  /**
   * Check if the current position starts a destructure using formArgs and skip an
   * unspecified part of it.
   *
   * @return true iff the next token(s) start a destructureDecl
   */
  boolean skipDestructrDclPrefix()
  {
    return skipFormArgs() && isOperator(":=");
  }


  /**
   * Check if the current position starts a destructr and skip an unspecified part
   * of it.
   *
   * @return true iff the next token(s) start a destructroe.
   */
  boolean skipDestructrPrefix()
  {
    boolean result = false;
    skip(Token.t_set);
    if (skip(Token.t_lparen))
      {
        do
          {
            result = skipName();
          }
        while (result && skipComma());
        result = result
          && skip(Token.t_rparen)
          && isOperator(":=");
      }
    return result;
  }


  /**
   * Parse call or anonymous feature or this
   *
callOrFeatOrThis  : anonymous
                  | qualThis
                  | plainLambda
                  | call
                  ;
   */
  Expr callOrFeatOrThis()
  {
    return
      isAnonymousPrefix()   ? anonymous()   : // starts with value/ref/:/fun/name
      isQualThisPrefix()    ? qualThis()    : // starts with name
      isPlainLambdaPrefix() ? plainLambda() : // x,y,z post result = x*y*z -> x*y*z
      isNamePrefix()        ? call(null)      // starts with name
                            : null;
  }


  /**
   * Parse anonymous
   *
anonymous   : returnType
              inherit
              contract
              block
            ;
   */
  Expr anonymous()
  {
    SourcePosition pos = posObject();
    ReturnType r = returnType();
    var        i = inherit();
    Contract   c = contract();
    Block      b = block(false);
    var f = Feature.anonymous(pos, r, i, c, b);
    var ca = new Call(pos, f);
    return ca;
    // NYI: This would simplify the code (in Feature.findFieldDefInScope that
    // has special handling for c.calledFeature().isAnonymousInnerFeature()) but
    // does not work yet, probably because of too much that is done explicitly
    // for anonymsous featues.
    //
    // return new Block(pos, b.closingBracePos_, new List<>(f, ca));
  }


  /**
   * Check if the current position starts an anonymous.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an anonymous.
   */
  boolean isAnonymousPrefix()
  {
    return isReturnTypeFollowedByColon();
  }


  /**
   * Parse qualThis
   *
qualThis    : name ( dot name )* dot "this"
            ;
   */
  This qualThis()
  {
    SourcePosition pos;
    List<String> q = new List<>();
    do
      {
        q.add(name());
        if (!skipDot())
          {
            syntaxError("'.'", "qualThis");
          }
        pos = posObject();
      }
    while (!skip(Token.t_this));
    return new This(pos, q);
  }


  /**
   * Check if the current position starts a qualThis.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a qualThis.
   */
  boolean isQualThisPrefix()
  {
    return isNamePrefix() && fork().skipQualThisPrefix();
  }


  /**
   * Check if the current position starts a qualThis.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a qualThis.
   */
  boolean skipQualThisPrefix()
  {
    boolean result = false;
    while (!result && skipName() && skipDot())
      {
        result = skip(Token.t_this);
      }
    return result;
  }


  /**
   * Parse contract
   */
  Contract contract()
  {
    return contract(false);
  }


  /**
   * Parse contract
   *
contract    : require
              ensure
              invariant
            ;
   */
  Contract contract(boolean atMinIndent)
  {
    return new Contract(requir   (atMinIndent),
                        ensur    (atMinIndent),
                        invariant(atMinIndent));
  }


  /**
   * Check if the current position starts a contract.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a contract.
   */
  boolean isContractPrefix()
  {
    switch (currentAtMinIndent())
      {
      case t_require  :
      case t_ensure   :
      case t_invariant:
      case t_pre      :
      case t_post     :
      case t_inv      : return true;
      default         : return false;
      }
  }


  /**
   * Parse require
   *
require     : "pre" condList
            |
            ;
   */
  List<Cond> requir(boolean atMinIndent)
  {
    List<Cond> result = null;
    if (skip(atMinIndent, Token.t_require) ||
        skip(atMinIndent, Token.t_pre    )    )
      {
        result = condList();
      }
    return result;
  }


  /**
   * Parse ensure
   *
ensure      : "post" condList
            |
            ;
   */
  List<Cond> ensur(boolean atMinIndent)
  {
    List<Cond> result = null;
    if (skip(atMinIndent, Token.t_ensure) ||
        skip(atMinIndent, Token.t_post  )    )
      {
        result = condList();
      }
    return result;
  }


  /**
   * Parse invariant
   *
invariant   : "inv" condList
            |
            ;
   */
  List<Cond> invariant(boolean atMinIndent)
  {
    List<Cond> result = null;
    if (skip(atMinIndent, Token.t_invariant) ||
        skip(atMinIndent, Token.t_inv      )    )
      {
        result = condList();
      }
    return result;
  }


  /**
   * Parse condList
   *
condList    : cond ( COMMA condList
                   |
                   )
              semi
            ;
   */
  List<Cond> condList()
  {
    List<Cond> result = new List<>(cond());
    while (skipComma())
      {
        result.add(cond());
      }
    semi();
    return result;
  }


  /**
   * Parse implRout
   *
implRout    : block
            | "is" "abstract"
            | "is" "intrinsic"
            | "is" block
            | ARROW e=block
            ;
   */
  Impl implRout()
  {
    SourcePosition pos = posObject();
    Impl result;
    var startRoutine = (currentAtMinIndent() == Token.t_lbrace || skip(true, Token.t_is));
    if (startRoutine)    { result = skip(Token.t_abstract ) ? Impl.ABSTRACT  :
                                    skip(Token.t_intrinsic) ? Impl.INTRINSIC :
                                    new Impl(pos, block(true)      , Impl.Kind.Routine   ); }
    else if (skip("=>")) { result = new Impl(pos, block(true)      , Impl.Kind.RoutineDef); }
    else
      {
        syntaxError(pos(), "'is', '{' or '=>' in routine declaration", "implRout");
        result = Impl.ERROR;
      }
    return result;
  }


  /**
   * Parse implFldOrRout
   *
implFldOrRout   : implRout
                | implFldInit
                | implFldUndef
                |
                ;
   */
  Impl implFldOrRout(boolean hasType)
  {
    if (currentAtMinIndent() == Token.t_lbrace ||
        currentAtMinIndent() == Token.t_is     ||
        isOperator("=>")                          )
      {
        return implRout();
      }
    else if (isOperator(":="))
      {
        return implFldInitOrUndef(hasType, true);
      }
    else
      {
        syntaxError(pos(), "'is', ':=' or '{'", "impl");
        return Impl.FIELD;
      }
  }


  /**
   * Parse implFldInitOrUndef
   *
implFldInit : ":=" exprAtMinIndent
            ;
implFldUndef: ":=" "?"
            ;
   */
  Impl implFldInitOrUndef(boolean hasType, boolean maybeUndefined)
  {
    SourcePosition pos = posObject();
    if (!skip(":="))
      {
        syntaxError(pos(), "':='", "implFldInit");
      }
    if (maybeUndefined && skip('?'))
      {
        return Impl.FIELD;
      }
    else
      {
        return new Impl(pos,
                        exprAtMinIndent(),
                        hasType ? Impl.Kind.FieldInit
                                : Impl.Kind.FieldDef);
      }
  }


  /**
   * Check if the current position starts an impl.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) start an impl.
   */
  boolean isImplPrefix()
  {
    return
      currentAtMinIndent() == Token.t_lbrace ||
      currentAtMinIndent() == Token.t_is ||
      isOperator(":=") ||
      isOperator("=>");
  }


  /**
   * Parse type
   *
type        : onetype ( PIPE onetype ) *
            ;
   */
  Type type()
  {
    Type result = onetype();
    if (isOperator('|'))
      {
        List<AbstractType> l = new List<>(result);
        while (skip('|'))
          {
            l.add(onetype());
          }
        result = new Type(result.pos, "choice", l, null);
      }
    return result;
  }


  /**
   * Check if the current position starts a type.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) starts a type.
   */
  boolean isTypePrefix()
  {
    switch (current())
      {
      case t_fun:
      case t_ref:
      case t_lparen: return true;
      default: return isNamePrefix();
      }
  }


  /**
   * Check if the current position is a type.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) form a type.
   */
  boolean isType()
  {
    return fork().skipType();
  }


  /**
   * Check if the current position can be parsed as a type and skip it if this is the case.
   *
   * @return true iff a type was found and skipped, otherwise no type was found
   * and the parser/lexer is at an undefined position.
   */
  boolean skipType()
  {
    if (!skipOneType())
      {
        return false;
      }
    while (skip('|'))
      {
        if (!skipOneType())
          {
            return false;
          }
      }
    return true;
  }


  /**
   * Parse onetype
   *
onetype     : "ref" simpletype
            | "fun" funArgsOpt typeOpt
            | simpletype "->" simpletype
            | funArgs "->" simpletype
            | t=simpletype
            ;
funArgs     : LPAREN a=typeList RPAREN
            ;
funArgsOpt  : funArgs
            |
            ;
typeOpt     : type
            |
            ;
   */
  Type onetype()
  {
    Type result;
    SourcePosition pos = posObject();
    if (skip(Token.t_ref))
      {
        result = simpletype();
        result.setRef();
      }
    else if (skip(Token.t_fun))
      {
        var a = Type.NONE;
        if (skipLParen())
          {
            if (current() != Token.t_rparen)
              {
                a = typeList();
              }
            match(Token.t_rparen, "funTypeArgs");
          }
        result = isTypePrefix()
          ? Type.funType(pos, type(), a)
          : Type.funType(pos, new Type("unit"), a);
      }
    else if (skip(Token.t_lparen))
      {
        var a = Type.NONE;
        if (current() != Token.t_rparen)
          {
            a = typeList();
          }
        match(Token.t_rparen, "funTypeArgs");
        matchOperator("->", "onetype");
        result = Type.funType(pos, type(), a);
      }
    else
      {
        result = simpletype();
        if (skip("->"))
          {
            result = Type.funType(pos, type(), new List<>(result));
          }
      }
    return result;
  }


  /**
   * Check if the current position starts a onetype and skip it.
   *
   * @return true iff the next token(s) is a onetype, otherwise no onetype was
   * found and the parser/lexer is at an undefined position.
   */
  boolean skipOneType()
  {
    boolean result;
    if (skip(Token.t_ref))
      {
        result = skipSimpletype();
      }
    else if (skip(Token.t_fun))
      {
        result = true;
        if (skipLParen())
          {
            if (current() != Token.t_rparen)
              {
                skipTypeList();
              }
            result = skip(Token.t_rparen);
          }
        result = result && !isTypePrefix() || skipType();
      }
    else if (skip(Token.t_lparen))
      {
        result = ((current() == Token.t_rparen) || skipTypeList()) &&
          skip(Token.t_rparen) &&
          skip("->") &&
          skipType();
      }
    else
      {
        result = skipSimpletype() && (!skip("->") || skipSimpletype());
      }
    return result;
  }


  /**
   * Parse simpletype
   *
simpletype  : name actualGens
              ( dot simpletype
              |
              )
            ;
   */
  Type simpletype()
  {
    Type result = null;
    do
      {
        result = new Type(posObject(),
                          name(),
                          actualGens(),
                          result);
      }
    while (skipDot());
    return result;
  }


  /**
   * Check if the current position is a simpletype and skip it.
   *
   * @return true iff the next token(s) is a simpletype, otherwise no simpletype
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipSimpletype()
  {
    boolean result;
    do
      {
        result = skipName() && skipActualGens();
      }
    while (result && skipDot());
    return result;
  }


  /**
   * Check if the current position is a type followed by Token next and, if op
   * != null, operator op.  If this is the case, skip it.
   *
   * @return true iff the next token(s) form type followed by next/op, otherwise
   * no type followed by next/op was found and the parser/lexer is at an
   * undefined position.
   */
  boolean skipTypeFollowedBy(Token next, String op)
  {
    return
      skipType() &&
      (next == Token.t_op || skip(next)) &&
      (next != Token.t_op || skip(op));
  }


  /**
   * Check if the current position is a type followed by '=>'.  If this is the
   * case, skip it.
   *
   * @return true iff the next token(s) form type followed by '=>', otherwise no
   * type followed by '=>' was found and the parser/lexer is at an undefined
   * position.
   */
  boolean skipTypeFollowedByArrow()
  {
    return skipTypeFollowedBy(Token.t_op, "=>");
  }


  /**
   * Check if the current position is a type followed by {.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) is a type followed by '{', otherwise no
   * type followed by '{' was found and the parser/lexer is at an undefined
   * position.
   */
  boolean isTypeFollowedByLBrace()
  {
    return fork().skipTypeFollowedBy(Token.t_lbrace, null);
  }


  /**
   * Check if the current position is a type followed by :.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) is a type followed by ':, otherwise no
   * type followed by ':' was found and the parser/lexer is at an undefined
   * position.
   */
  boolean isTypeFollowedByColon()
  {
    return fork().skipTypeFollowedBy(Token.t_op, ":");
  }


  /**
   * Parse comma if it is found
   *
comma       : COMMA
            ;
   *
   * @return true iff a comma was found and skipped.
   */
  boolean skipComma()
  {
    return skip(Token.t_comma);
  }


  /**
   * Parse colon if it is found
   *
colon       : ":"
            ;
   *
   * @return true iff a colon was found and skipped.
   */
  boolean skipColon()
  {
    return skip(':');
  }


  /**
   * Parse "." if it is found
   *
dot         : "."
            ;
   *
   * @return true iff a "." was found and skipped.
   */
  boolean skipDot()
  {
    boolean result = skip('.');
    if (!result)
      { // allow dot to appear in new line
        var oldLine = sameLine(-1);
        result = skip('.');
        sameLine(result ? line() : oldLine);
      }

    return result;
  }


  /**
   * Parse "(" if it is found
   *
   * @return true iff a "(" was found and skipped.
   */
  boolean skipLParen()
  {
    return skip(Token.t_lparen);
  }


  /**
   * Parse given token and skip it. if it was found.
   *
   * @param t a token.
   *
   * @return true iff the current token was t and was skipped, otherwise no
   * change is made.
   */
  boolean skip(Token t)
  {
    boolean result = false;
    if (current() == t)
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse given token and skip it. if it was found.
   *
   * @param t a token.
   *
   * @return true iff the current token was t and was skipped, otherwise no
   * change is made.
   */
  boolean skip(boolean atMinIndent, Token t)
  {
    boolean result = false;
    if (current(atMinIndent) == t)
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse singe-char t_op.
   *
   * @param op the single-char operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean skip(char op)
  {
    boolean result = false;
    if (isOperator(op))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse specific t_op.
   *
   * @param op the operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean skip(String op)
  {
    boolean result = false;
    if (isOperator(op))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Parse specific t_op after splitting it off from the current op.
   *
   * @param op the operator
   *
   * @return true iff an t_op op was found and skipped.
   */
  boolean splitSkip(String op)
  {
    boolean result = false;
    splitOperator(op);
    if (isOperator(op))
      {
        next();
        result = true;
      }
    return result;
  }


  /**
   * Return the details of the numeric literal of the current t_numliteral token.
   */
  Literal skipNumLiteral()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_numliteral);

    var result = curLiteral();
    next();
    return result;
  }

}

/* end of file */
