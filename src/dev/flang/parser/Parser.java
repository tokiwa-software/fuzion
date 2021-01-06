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
 * Parser performs the syntactic analysis of Fusion source code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
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
        String m = el.getMethodName();
        boolean parser = el.getClassName().equals(Parser.class.getName());
        if (count != 0 && !m.equals(lastMethod) && parser)
          {
            sb.append(sb.length() == 0 ? "" : ", ")
              .append(lastMethod)
              .append(count > 1 ? " ("+count+" times)" : "");
            count = 0;
          }
        if (parser && !m.equals("parseStack") && !m.equals("parserDetail"))
          {
            count++;
            lastMethod = m;
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
   * Parse a unit.
   *
unit        : feature semi EOF
            ;
   */
  public Feature unit()
  {
    Feature result = feature().feature();
    semi();
    if (Errors.count() == 0)
      {
        match(Token.t_eof, "unit");
      }
    return result;
  }


  /**
   * Parse stmnts followed by Token.t_eof.
   *
stmntsEof   : stmnts EOF
            ;
   */
  public List<Stmnt> stmntsEof()
  {
    var result = stmnts();
    if (Errors.count() == 0)
      {
        match(Token.t_eof, "stmntsAsUnit");
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
feature     : visibility
              modifiers
              featNames
              formGens
              formArgs
              returnType
              inherits
              contract
              impl
            ;
   */
  FList feature()
  {
    SourcePosition pos = posObject();
    Visi v = visibility();
    int m = modifiers();
    List<List<String>> n = featNames();
    FormalGenerics g = formGens();
    List<Feature> a = formArgs();
    ReturnType r = returnType();
    List<Call> i = inherits();
    Contract c = contract(true);
    Impl p = impl();
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
    switch (skipFormArgsNotActualArgs())
      {
      case formal: return true;
      case actual: return false;
      default    : break;
      }
    return
      isReturnTypePrefix() ||
      isInheritPrefix   () ||
      isContractPrefix  () ||
      isImplPrefix      ();
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
  visi      : COLON qual
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
name        : IDENT
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
                      syntaxError(pos, ": after 'ternary ?'", "name");
                    }
                }
              else
                {
                  syntaxError(pos, "? : after 'ternary'", "name");
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
                  syntaxError(pos, "[ ] or identifier after 'set'", "name");
                  result = Errors.ERROR_STRING;
                }
              break;
            }
          default: check(false);
          }
      }
    else
      {
        syntaxError(pos, "identifier name, infix/prefix/postfix operator, ternary ? :, index or set name", "name");
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
    if (skipLParen())
      {
        if (skip(Token.t_rparen))
          {
            return FormalOrActual.both;
          }
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
        if (isTypePrefix())
          {
            return FormalOrActual.formal;
          }
        else
          {
            return FormalOrActual.actual;
          }
      }
    return FormalOrActual.both;
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
            | "single"
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
          case t_single: next(); result = SingleType.INSTANCE; break;
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
      case t_single:
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
      skip(Token.t_single) ||
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
  List<Call> inherits()
  {
    return isInheritPrefix() ? inherit() : new List<>();
  }


  /**
   * Parse inherit clause
   *
inherit     : COLON callList
            ;
   */
  List<Call> inherit()
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
  List<Call> callList()
  {
    List<Call> result = new List<Call>(call(null));
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
                   | dot ( INTEGER callTail
                         | call
                   )
            ;
   */
  Call call(Expr target)
  {
    SourcePosition pos = posObject();
    String n = name();
    Call result;
    if (skipDot())
      {
        if (current() == Token.t_integer)
          {
            result = new Call(pos, target, n, skipInteger());
            result = callTail(result);
          }
        else
          {
            result = new Call(pos, target, n, Call.NO_GENERICS, Call.NO_PARENTHESES);
            result = call(result);
          }
      }
    else
      {
        // we must check isActualGens() to distinguish the less operator in 'a < b'
        // from the actual generics in 'a<b>'.
        List<Type> g = isActualGens() ? actualGens() : Call.NO_GENERICS;

        List<Expr> l = actualArgs();
        result = new Call(pos, target, n, g, l);
        result = callTail(result);
      }
    return result;
  }


  /**
   * Parse indexcall
   *
indexCall   : ( LBRACKET exprList RBRACKET
                ( EQ exprInLine
                |
                )
              )+
            ;
   */
  Call indexCall(Expr target)
  {
    Call result;
    do
      {
        SourcePosition pos = posObject();
        next();
        int oldLine = sameLine(-1);
        List<Expr> l = exprList();
        match(Token.t_rcrochet, "indexCall");
        sameLine(oldLine);
        if (skip('='))
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
    while (current() == Token.t_lcrochet);
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
    if (current() == Token.t_lcrochet)
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
  List<Type> actualGens()
  {
    List<Type> result = Call.NO_GENERICS;
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
    //  t := Tuple<i32, bool, String, Tuple < int < bool >>();
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
  List<Type> typeList()
  {
    List<Type> result = new List<>(type());
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
actualArgs  : LPAREN exprLst RPAREN
            | LPAREN RPAREN
            |
            ;
   */
  List<Expr> actualArgs()
  {
    List<Expr> result = Call.NO_PARENTHESES;
    int oldLine = sameLine(-1);
    if (skipLParen())
      {
        if (current() != Token.t_rparen)
          {
            result = exprList();
          }
        else
          {
            result = new List<>();
          }
        match(Token.t_rparen, "actualArgs");
      }
    sameLine(oldLine);
    return result;
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
   * An expr that does not exceed a single line unless it is enclosed by { } or
   * ( ).
   *
exprInLine  : expr
            ;
   */
  Expr exprInLine()
  {
    Expr result;
    int line = line();
    int oldLine = sameLine(-1);
    if (current() == Token.t_lbrace)
      {
        var f = fork();
        f.block(false);
        result = f.line() == line || f.isOperator('.') ? expr() : block(false /* should be indented */);
      }
    else if (current() == Token.t_lparen)
      {
        var f = fork();
        f.klammer();
        result = f.line() == line || f.isOperator('.') ? expr() : klammer();
      }
    else
      {
        sameLine(line);
        result = expr();
      }
    sameLine(oldLine);
    return result;
  }


  /**
   * An expr() that, if it is a block, is permitted to start at minIndent.
   *
exprAtMinIndent
              : block
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
            result = new Match(pos, result, cases());
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
    while (isOpPrefix())
      {
        oe.add(op());
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
klammer     : klammerexpr
            | tuple
            ;
klammerexpr : LPAREN expr RPAREN
            ;
tuple       : LPAREN RPAREN
            | LPAREN expr (COMMA expr)+ RPAREN
            ;
   */
  Expr klammer()
  {
    Expr result;
    SourcePosition pos = posObject();
    match(Token.t_lparen, "term");
    int oldLine = sameLine(-1);
    if (skip(Token.t_rparen)) // an empty tuple
      {
        result = new Call(pos, null, "Tuple", Call.NO_GENERICS, Call.NO_PARENTHESES);
      }
    else
      {
        result = expr(); // a klammerexpr
        if (skipComma()) // a tuple
          {
            List<Expr> elements = new List<>(result);
            do
              {
                elements.add(expr());
              }
            while (skipComma());
            result = new Call(pos, null, "Tuple", Call.NO_GENERICS, elements);
          }
        match(Token.t_rparen, "term");
      }
    sameLine(oldLine);
    return result;
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
simpleterm  : klammer
            | fun
            | STRING
            | INTEGER
            | "old" term
            | match
            | loop
            | ifstmnt
            | block
            | callOrFeatOrThis
            ;
   */
  Expr term()
  {
    Expr result;
    int p1 = pos();
    switch (current()) // even if this is t_lbrace, we want a term to be indented, so do not use currentAtMinIndent().
      {
      case t_lparen :         result = klammer();                                break;
      case t_fun    :         result = fun();                                    break;
      case t_string :         result = new StrConst(posObject(), "\""+string()+"\"" /* NYI: remove "\"" */); next(); break;
      case t_integer:         result = new IntConst(posObject(), skipInteger()); break;
      case t_old    : next(); result = new Old(term()                         ); break;
      case t_match  :         result = match();                                  break;
      case t_for    :
      case t_variant:
      case t_while  :
      case t_do     :         result = loop();                                   break;
      case t_if     :         result = ifstmnt();                                break;
      case t_lbrace :         result = block(true);                              break;
      default       :         result = callOrFeatOrThis();
        if (result == null)
          {
            syntaxError(p1, "term (lparen, fun, string, integer, old, match, lbrace, or name)", "term");
            result = new Call(posObject(), null, Errors.ERROR_STRING, Call.NO_GENERICS, Call.NO_PARENTHESES);
          }
        break;
      }
    if (current() == Token.t_lcrochet)
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
   * Check if the current position starts a term.  Does not change the position
   * of the parser.
   *
   * @return true iff the next token(s) start a term.
   */
  boolean isTermPrefix()
  {
    switch (current()) // even if this is t_lbrace, we want a term to be indented, so do not use currentAtMinIndent().
      {
      case t_lparen :
      case t_fun    :
      case t_string :
      case t_integer:
      case t_old    :
      case t_match  :
      case t_lbrace : return true;
      default       :
        return isNamePrefix()    // Matches call and qualThis
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

    Operator result = new Operator(posObject(), operator());
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
    return current() == Token.t_op && !isOperator('?');  // NYI: create a token for '?'.
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
              ( type | )
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
    List<Call> i = inherits();
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
            syntaxError(pos(), "Expected 'is', '{' or '=>' in inline function declaration", "function");
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
    SourcePosition pos = posObject();
    match(Token.t_match, "match");
    Expr e = exprInLine();
    int oldLine = sameLine(-1);
    boolean gotLBrace = skip(true, Token.t_lbrace);
    List<Case> c = cases();
    if (gotLBrace)
      {
        match(true, Token.t_rbrace, "block");
      }
    sameLine(oldLine);
    return new Match(pos, e, c);
  }


  /**
   * Parse cases
   *
cases       : caze maybecomma ( cases
                              |
                              )
            ;
maybecomma  : comma
            |
            ;
   */
  List<Case> cases()
  {
    List<Case> result = new List<>();
    var in = new Indentation();
    while (!endOfStmnts() && in.ok())
      {
        result.add(caze());
        skipComma();
        if (!endOfStmnts())
          {
            semiOrFlatLF();
          }
      }
    in.end();
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
caseFldDcl  : IDENT type ARROW block
            ;
caseTypes   : typeList   ARROW block
            ;
caseStar    : STAR       ARROW block
            ;
   */
  Case caze()
  {
    Case result = null;
    SourcePosition pos = posObject();
    if (skip('*'))
      {
        matchOperator("=>", "caze");
        result = new Case(pos, block(true));
      }
    else if (isCaseFldDcl())
      {
        String n = identifier();
        match(Token.t_ident, "caze");
        Type t = type();
        matchOperator("=>", "caze");
        result = new Case(pos, t, n, block(true));
      }
    else
      {
        List<Type> l = typeList();
        matchOperator("=>", "caze");
        result = new Case(pos, l, block(true));
      }
    // NYI: Cleanup: new abstract class CaseCondition with three implementations: star, fieldDecl, typeList.
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
    int oldLine = sameLine(-1);
    boolean gotLBrace = skip(mayBeAtMinIndent, Token.t_lbrace);
    var l = stmnts();
    var pos2 = l.size() > 0 ? l.getLast().pos() : pos1;
    if (gotLBrace)
      {
        pos2 = posObject();
        match(mayBeAtMinIndent, Token.t_rbrace, "block");
      }
    sameLine(oldLine);
    return new Block(pos1, pos2, l);
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
      currentNoLimit() == Token.t_eof;
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
          if (!l.isEmpty() && l.getLast() instanceof Feature && ((Feature)l.getLast()).impl == Impl.FIELD)
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
        if (s instanceof FList)
          {
            l.addAll(((FList) s)._list);
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
   * Parse blockOpt, i.e. a block or nothing.
   *
blockOpt    : block
            |
            ;
   */
  Block blockOpt()
  {
    Block result;
    if (currentAtMinIndent() == Token.t_lbrace           ||
        current()            != Token.t_indentationLimit &&
        current()            != Token.t_rbrace &&
        current()            != Token.t_semicolon &&
        current()            != Token.t_until &&
        current()            != Token.t_else)
      {
        result = block(true);
      }
    else
      {
        result = null;
      }
    return result;
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
            | decompose
            | exprInLine
            | checkstmt
            ;
   */
  Stmnt stmnt()
  {
    return
      isCheckPrefix()     ? checkstmnt() :
      isAssignPrefix()    ? assign()     :
      isDecomposePrefix() ? decompose()  :
      isFeaturePrefix()   ? feature()    : exprInLine();
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
loopProlog  : "for" indexVars "variant" exprInLine
            | "for" indexVars
            |                 "variant" exprInLine
            ;
loopBody    : "while" exprAtMinIndent blockOpt
            | "do"                    blockOpt
            ;
loopEpilog  : "until" exprAtMinIndent blockOpt elseBlockOpt
            |                                  elseBlock
            ;
   */
  Expr loop()
  {
    SourcePosition pos = posObject();
    int oldLine = sameLine(-1);
    List<Feature> indexVars  = new List<>();
    List<Expr>    nextValues = new List<>();
    var hasFor   =              skip(      Token.t_for    ); if (hasFor) { indexVars(indexVars, nextValues); }
    var hasVar   =              skip(true, Token.t_variant); var v   = hasVar              ? exprInLine()      : null;
                                                             var i   = hasFor || v != null ? invariant(true)   : null;
    var hasWhile =              skip(true, Token.t_while  ); var w   = hasWhile            ? exprAtMinIndent() : null;
    var hasDo    = !hasWhile && skip(true, Token.t_do     ); var b   = hasWhile || hasDo   ? blockOpt()        : null;
    var hasUntil =              skip(true, Token.t_until  ); var u   = hasUntil            ? exprAtMinIndent() : null;
                                                             var ub  = hasUntil            ? blockOpt()        : null;
                                                             var els =                       elseBlockOpt();
    if (!hasWhile && !hasDo && !hasUntil && els == null)
      {
        syntaxError(pos(), "Expected loopBody or loopEpilog: 'while', 'do', 'until' or 'else'", "loop");
      }
    sameLine(oldLine);
    return new Loop(pos, indexVars, nextValues, v, i, w, b, u, ub, els).tailRec();
  }


  /**
   * Parse IndexVars
   *
indexVars   : indexVar (semi indexVars)
            |
            ;
   */
  void indexVars(List<Feature> indexVars, List<Expr> nextValues)
  {
    while (isIndexVarPrefix())
      {
        indexVar(indexVars, nextValues);
        semi();
      }
  }


  /**
   * Parse IndexVar
   *
indexVar    : visibility
              modifiers
              name
              ( type contract implFldInit nextValue
              |      contract implFldDef  nextValue
              | type contract implFldIter
              |      contract implFldIter
              )
            ;
implFldIter : "in" exprInLine
nextValue   : COMMA exprInLine
            |
            ;
   */
  void indexVar(List<Feature> indexVars, List<Expr> nextValues)
  {
    SourcePosition pos = posObject();
    Visi v = visibility();
    int m = modifiers();
    String n = name();
    ReturnType r;
    Contract c;
    Impl p;
    boolean hasType = isType();
    r = hasType ? new FunctionReturnType(type())
                : NoType.INSTANCE;
    c = contract();
    Expr nextValue = null;
    if (skip(Token.t_in))
      {
        p = new Impl(posObject(), exprInLine(), Impl.Kind.FieldIter);
      }
    else
      {
        Parser forked = fork();  // tricky: in case there is no nextValue, we
                                 // re-parse the initial value expr and use it
                                 // as nextValue
        forked.skip(Token.t_op);
        p = hasType ? implFldInit()
                    : implFldDef();
        nextValue = (skipComma() ? this : forked).exprInLine();
      }
    Feature f = new Feature(pos,v,m,r,new List<>(n),
                            FormalGenerics.NONE,
                            new List<Feature>(),
                            new List<Call>(),
                            c,p);
    indexVars.add(f);
    nextValues.add(nextValue);
  }


  /**
   * Check if the current position starts an indexVar declaration.  Does not
   * change the position of the parser.
   *
   * @return true iff an indexVar declaration should be parsed.
   */
  boolean isIndexVarPrefix()
  {
    return
      isNonEmptyVisibilityPrefix() ||
      isModifiersPrefix() ||
      isNamePrefix();
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
ifstmt      : "if" exprInLine block elseBlock
            ;
   */
  If ifstmnt()
  {
    SourcePosition pos = posObject();
    int oldLine = sameLine(-1);
    match(Token.t_if, "ifstmnt");
    Expr e = exprInLine();
    Block b = block(true);
    If result = new If(pos, e, b);
    Expr els = elseBlockOpt();
    if (els instanceof If)
      {
        result.setElse((If) els);
      }
    else if (els instanceof Block)
      {
        result.setElse((Block) els);
      }
    else
      {
        check
          (els == null);
      }
    sameLine(oldLine);

    return result;
  }


  /**
   * Parse elseBlockOpt
   *
elseBlockOpt: elseBLock
            |
            ;
elseBlock   : "else" ( ifstmt
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
assign      : name EQ exprInLine
            ;
   */
  Stmnt assign()
  {
    String n = name();
    SourcePosition pos = posObject();
    matchOperator("=", "assign");
    Expr e = exprInLine();
    return new Assign(pos, n, e);
  }


  /**
   * Check if the current position starts an assign.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an assign.
   */
  boolean isAssignPrefix()
  {
    return isNamePrefix() && fork().skipAssignPrefix();
  }


  /**
   * Check if the current position starts an assign and skip an unspecified part
   * of it.
   *
   * @return true iff the next token(s) start an assign.
   */
  boolean skipAssignPrefix()
  {
    return skipName() && isOperator('=');
  }


  /**
   * Parse decompose
   *
decompose   : decomp
            | decompDecl
            ;
decomp      : "(" argNames ")" ("=" | ":=" ) exprInLine
decompDecl  : formArgs          "="          exprInLine
            ;
   */
  Stmnt decompose()
  {
    Stmnt result;
    if (fork().skipFormArgs())
      {
        List<Feature> a = formArgs();
        SourcePosition pos = posObject();
        matchOperator("=", "decompose");
        result = Decompose.create(pos, a, null, false, exprInLine());
      }
    else
      {
        match(Token.t_lparen, "decompose");
        List<String> names = argNames();
        match(Token.t_rparen, "decompose");
        SourcePosition pos = posObject();
        boolean def = skip(":=");
        if (!def)
          {
            matchOperator("=", "decompose");
          }
        result = Decompose.create(pos, null, names, def, exprInLine());
      }
    return result;
  }


  /**
   * Check if the current position starts decompose.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a decompose.
   */
  boolean isDecomposePrefix()
  {
    return (current() == Token.t_lparen) && (fork().skipDecompDeclPrefix() ||
                                             fork().skipDecompPrefix()        );
  }


  /**
   * Check if the current position starts a decompose using formArgs and skip an
   * unspecified part of it.
   *
   * @return true iff the next token(s) start a decomposeDecl
   */
  boolean skipDecompDeclPrefix()
  {
    return skipFormArgs() && isOperator('=');
  }


  /**
   * Check if the current position starts a decomp and skip an unspecified part
   * of it.
   *
   * @return true iff the next token(s) start a decompoe.
   */
  boolean skipDecompPrefix()
  {
    boolean result = false;
    if (skip(Token.t_lparen))
      {
        do
          {
            result = skipName();
          }
        while (result && skipComma());
        result = result
          && skip(Token.t_rparen)
          && (isOperator('=') || isOperator(":="));
      }
    return result;
  }


  /**
   * Parse call or anonymous feature or this
   *
callOrFeatOrThis
            : anonymous
            : qualThis
            | call
            ;
   */
  Expr callOrFeatOrThis()
  {
    return
      isAnonymousPrefix() ? anonymous() : // starts with single/value/ref/:/fun/name
      isQualThisPrefix()  ? qualThis()  : // starts with name
      isNamePrefix()      ? call(null)    // starts with name
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
    List<Call> i = inherit();
    Contract   c = contract();
    Block      b = block(false);
    var f = new Feature(pos, r, i, c, b);
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
            syntaxError("operator '.'", "qualThis");
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
    switch (current())
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
   * Parse impl
   *
impl        : block
            | "is" "abstract"
            | "is" "intrinsic"
            | "is" block
            | ARROW e=block
            | implFldInit
            | implFldDef
            |
            ;
   */
  Impl impl()
  {
    SourcePosition pos = posObject();
    Impl result;
    var startRoutine = (currentAtMinIndent() == Token.t_lbrace || skip(true, Token.t_is));
    if (startRoutine)    { result = skip(Token.t_abstract ) ? Impl.ABSTRACT  :
                                    skip(Token.t_intrinsic) ? Impl.INTRINSIC :
                                    new Impl(pos, block(true)      , Impl.Kind.Routine   ); }
    else if (skip("=>")) { result = new Impl(pos, block(true)      , Impl.Kind.RoutineDef); }
    else if (skip('=') ) { result = new Impl(pos, exprAtMinIndent(), Impl.Kind.FieldInit ); }
    else if (skip(":=")) { result = new Impl(pos, exprAtMinIndent(), Impl.Kind.FieldDef  ); }
    else                 { result = Impl.FIELD;                                             }
    return result;
  }


  /**
   * Parse implFldInit
   *
implFldInit : EQ exprAtMinIndent
            ;
   */
  Impl implFldInit()
  {
    fork().matchOperator("=", "implInit");
    return impl();
  }


  /**
   * Parse implFldDef
   *
implFldDef  : DEF exprAtMinIndent
            ;
   */
  Impl implFldDef()
  {
    fork().matchOperator(":=", "implDef");
    return impl();
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
      isOperator('=' ) ||
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
        List<Type> l = new List<>(result);
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
      case t_ref: return true;
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
            | "fun" funTypeArgs ( type
                                |
                                )
            | t=simpletype
            ;
funTypeArgs : LPAREN a=typeLst RPAREN
            |
            ;
   */
  Type onetype()
  {
    Type result;
    SourcePosition pos = posObject();
    if (skip(Token.t_fun))
      {
        List<Type> a = Type.NONE;
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
          : Type.funType(pos,         a);
      }
    else if (skip(Token.t_ref))
      {
        result = simpletype();
        result.setRef();
      }
    else
      {
        result = simpletype();
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
    if (skip(Token.t_fun))
      {
        result = true;
        if (skipLParen())
          {
            if (current() != Token.t_rparen)
              {
                skipTypeList();
              }
            match(Token.t_rparen, "funTypeArgs");
          }
        if (isTypePrefix())
          {
            skipType();
          }
      }
    else
      {
        skip(Token.t_ref);
        result = skipSimpletype();
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
LPAREN      : "("
            ;
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
   * Return the actual integer constant of the current t_integer token as a
   * string.
   */
  String skipInteger()
  {
    String result = integer();
    next();
    return result;
  }

}

/* end of file */
