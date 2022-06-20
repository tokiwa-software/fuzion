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

import dev.flang.ast.*;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;


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


  /**
   * Different kinds of opening / closing brackets
   */
  static Parens PARENS   = new Parens( Token.t_lparen  , Token.t_rparen   );
  static Parens BRACES   = new Parens( Token.t_lbrace  , Token.t_rbrace   );
  static Parens CROCHETS = new Parens( Token.t_lcrochet, Token.t_rcrochet );
  static Parens ANGLES   = new Parens( "<"             , ">"              );


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
feature     : modAndNames routOrField
            ;
modAndNames : visibility
              modifiers
              featNames
            ;
   */
  FList feature()
  {
    var pos = posObject();
    var v = visibility();
    var m = modifiers();
    var n = featNames();
    return routOrField(pos, new List<Feature>(), v, m, n, 0);
  }


  /**
   * Parse routOrField:
   *
   * Note that this fork()s the parser repeatedly in case several feature names
   * are declared given as parament n.
   *
   *
routOrField : routine
            | field
            ;
routine     : formGens
              formArgsOpt
              returnType
              inherits
              contract
              implRout
            ;
field       : returnType
              contract
              implFldOrRout
            ;
   */
  FList routOrField(SourcePosition pos, List<Feature> l, Visi v, int m, List<List<String>> n, int i)
  {
    var name = n.get(i);
    var p2 = (i+1 < n.size()) ? fork() : null;
    var g = formGens();
    var a = formArgsOpt();
    ReturnType r = returnType();
    var hasType = r != NoType.INSTANCE;
    var inh = inherits();
    Contract c = contract(true);
    if (!g.isEmpty())
      {
        g.addAll(a);
        a = g;
        g = new List<>();
      }
    Impl p =
      a  .isEmpty() &&
      inh.isEmpty()    ? implFldOrRout(hasType)
                       : implRout();
    p = handleImplKindOf(pos, p, i == 0, l, inh);
    l.add(new Feature(pos, v,m,r,name,a,inh,c,p));
    return p2 == null
      ? new FList(l)
      : p2.routOrField(pos, l, v, m, n, i+1);
  }


  /**
   * When parsing feature Implementation, convert 'of' syntax sugar as follows:
   *
   *   a,b,c : choice of
   *     x is p
   *     y is q
   *     z is r
    *
   * into
   *
   *   x is p
   *   y is q
   *   z is r
   *   a : choice x y z is
   *   b : choice x y z is
   *   c : choice x y z is
   *
   *
   * @param pos position of this feature (of 'a')
   *
   * @param p the Impl that was parsed
   *
   * @param first true if this was called for the first name ('a'), false for
   * later ones ('b', 'c').
   *
   * @param l list of features declared. Inner features ('x', 'y', 'z') will be
   * added to l if first is true.
   *
   * @param inh the inheritance call list.
   */
  Impl handleImplKindOf(SourcePosition pos, Impl p, boolean first, List<Feature> l, List<AbstractCall> inh)
  {
    if (p.kind_ == Impl.Kind.Of)
      {
        var ng = new List<AbstractType>();
        addFeaturesFromBlock(first, l, p._code, ng, p);
        if (inh.isEmpty())
          {
            AstErrors.featureOfMustInherit(pos, p.pos);
          }
        else
          {
            var ic = inh.getLast();
            if (!ic.generics().isEmpty())
              {
                ic.generics().addAll(ng);
              }
            else
              {
                ((Call)ic).generics = ng;
              }
          }
        p = new Impl(p.pos, new Block(p.pos, new List<>()), Impl.Kind.Routine);
      }
    return p;
  }


  /**
   * For a feature declaration of the form
   *
   *   a, b, c : choice of x, y, z.
   *
   * add features x, y, z to list and the types to g.
   *
   * @param first true if this is called for the first ('a') out of several
   * feature names.
   *
   * @param list list of features the inner features ('x', 'y', 'z') will be
   * added to provided that first is true.
   *
   * @param s the statements containing the feature declarations to be added, in
   * this case "x, y, z."
   *
   * @param g the list of type to be callected, will be added as generic
   * arguments to 'choice' in this example
   *
   * @param p Impl that contains the position of 'of' for error messages.
   */
  private void addFeaturesFromBlock(boolean first, List<Feature> list, Stmnt s, List<AbstractType> g, Impl p)
  {
    if (s instanceof Block b)
      {
        b.statements_.forEach(x -> addFeaturesFromBlock(first, list, x, g, p));
      }
    else if (s instanceof Feature f)
      {
        boolean ok = true;
        if (f._qname.size() > 1)
          {
            AstErrors.featureOfMustContainOnlyUnqualifiedNames(f, p.pos);
            ok = false;
          }
        if (!f.generics().list.isEmpty())
          {
            AstErrors.featureOfMustNotHaveFormalGenerics(f, p.pos);
            ok = false;
          }
        if (!f.isConstructor())
          {
            AstErrors.featureOfMustContainOnlyConstructors(f, p.pos);
            ok = false;
          }
        if (ok)
          {
            if (first)
              {
                list.add(f);
              }
            g.add(new Type(f.pos(), f.featureName().baseName(), new List<>(), null));
          }
      }
    else
      {
        AstErrors.featureOfMustContainOnlyDeclarations(s, p.pos);
      }
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
      (isNamePrefix() || current() == Token.t_type) && fork().skipFeaturePrefix();
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
    if (!skipQual())
      {
        return false;
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
visiList    : visi ( COMMA visiList
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
        else                              { throw new Error();               }
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
    List<String> result = qual(false);
    return result;
  }


  /**
   * Parse qualified name
   *
qual        : name
            | name dot qual
            | type dot qual
            ;
   */
  List<String> qual(boolean mayBeAtMinIndent)
  {
    List<String> result = new List<>();
    do
      {
        if (skip(mayBeAtMinIndent, Token.t_type))
          {
            result.add(FuzionConstants.TYPE_NAME);
            if (!isDot())
              {
                matchOperator(".", "qual");
                result.add(Errors.ERROR_STRING);
              }
          }
        else
          {
            result.add(name(mayBeAtMinIndent, false));
          }
        mayBeAtMinIndent = false;
      }
    while (skipDot());
    return result;
  }


  /**
   * Check if the current position is a 'qual'. If so, skip the 'qual'.
   *
   * @return true iff the next token(s) form 'qual', otherwise no 'qual' was
   * found and the parser/lexer is at an undefined position.
   */
  boolean skipQual()
  {
    if (skip(Token.t_type))
      {
        return skipDot() && skipQual();
      }
    else if (skipName())
      {
        return !skipDot() || skipQual();
      }
    return false;
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
    return name(false, false);
  }
  String name(boolean mayBeAtMinIndent, boolean ignoreError)
  {
    String result = Errors.ERROR_STRING;
    int pos = pos();
    if (isNamePrefix(mayBeAtMinIndent))
      {
        var oldLine = sameLine(line());
        switch (current(mayBeAtMinIndent))
          {
          case t_ident  : result = identifier(mayBeAtMinIndent); next(); break;
          case t_infix  :
          case t_prefix :
          case t_postfix: result = opName();  break;
          case t_ternary:
            {
              next();
              if (skip('?'))
                {
                  if (skipColon())
                    {
                      result = "ternary ? :";
                    }
                  else if (!ignoreError)
                    {
                      syntaxError(pos, "':' after 'ternary ?'", "name");
                    }
                }
              else if (!ignoreError)
                {
                  syntaxError(pos, "'? :' after 'ternary'", "name");
                }
              break;
            }
          case t_index  :
            {
              next();
              if (!ignoreError || current() == Token.t_lcrochet)
                {
                  match(Token.t_lcrochet, "name: index");
                  var dotdot = skip("..");
                  if (!ignoreError || current() == Token.t_rcrochet)
                    {
                      match(Token.t_rcrochet, "name: index");
                      result = dotdot ? "index [..]" : "index [ ]";
                    }
                }
              break;
            }
          case t_set    :
            {
              next();
              if (current() == Token.t_lcrochet)
                {
                  match(Token.t_lcrochet, "name: set");
                  if (!ignoreError || current() == Token.t_rcrochet)
                    {
                      match(Token.t_rcrochet, "name: set");
                      result = "index [ ] =";
                    }
                }
              else if (current() == Token.t_ident)
                {
                  result = identifier() + " =";
                  match(Token.t_ident, "name: set");
                }
              else if (!ignoreError)
                {
                  syntaxError(pos, "'[ ]' or identifier after 'set'", "name");
                }
              break;
            }
          default: throw new Error();
          }
        sameLine(oldLine);
      }
    else if (!ignoreError)
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
    return isNamePrefix(false);
  }
  boolean isNamePrefix(boolean mayBeAtMinIndent)
  {
    switch (current(mayBeAtMinIndent))
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
        return name(false, true) != Errors.ERROR_STRING;
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
          default            : throw new Error();
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
    List<List<String>> result = new List<>(qual(true));
    while (skipComma())
      {
        result.add(qual(true));
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
formGensBody: genericList ( "..."
                          |
                          )
            |
            ;
genericList : generic  ( COMMA genericList
                       |
                       )
            ;
   */
  List<Feature> formGens()
  {
    List<Feature> result = new List<>();
    if (splitSkip("<"))
      {
        if (!isOperator('>'))
          {
            do
              {
                result.add(generic());
              }
            while (skipComma());
            if (splitSkip("..."))
              {
                Errors.error(posObject(), "did not expect ...","");
              }
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
  Feature generic()
  {
    SourcePosition pos = posObject();
    String i = identifierOrError();
    match(Token.t_ident, "generic");
    var t = skipColon() ? type() : new Type("Object");
    return new Feature(pos, Consts.VISIBILITY_LOCAL, 0, t, i, Contract.EMPTY_CONTRACT, Impl.TYPE_PARAMETER);
  }


  /**
   * Parse optional formal argument list. Result is empty List in case no formArgs is found.
   *
formArgsOpt : formArgs
            |
            ;
   */
  List<Feature> formArgsOpt()
  {
    return  current() != Token.t_lparen || fork().skipType()
      ? new List<Feature>()
      : formArgs();
  }



  /**
   * Parse formal argument list
   *
formArgs    : LPAREN argLst RPAREN
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
              argType
              contract
            ;
argType     : typeType
            | type
            | type dot typeType
            ;
   */
  List<Feature> formArgs()
  {
    return bracketTermWithNLs(PARENS, "formArgs",
                              () -> {
                                var result = new List<Feature>();
                                do
                                  {
                                    SourcePosition pos = posObject();
                                    Visi v = visibility();
                                    int m = modifiers();
                                    List<String> n = argNames();
                                    Type t;
                                    Impl i;
                                    if (current() == Token.t_type)
                                      {
                                        t = new Type("Object");
                                        i =  typeType();
                                      }
                                    else
                                      {
                                        t = type();
                                        i = skipDot() ? typeType()
                                                      : Impl.FIELD;
                                      }
                                    Contract c = contract();
                                    for (String s : n)
                                      {
                                        result.add(new Feature(pos, v, m, t, s, c, i));
                                      }
                                  }
                                while (skipComma());
                                return result;
                              },
                              () -> new List<Feature>()
                              );
  }


  /**
   * Parse type parameter type suffix
   *
typeType    : "type"
            | "type" "..."
            ;
   */
  Impl typeType()
  {
    match(Token.t_type, "argType");
    return splitSkip("...") ? Impl.TYPE_PARAMETER_OPEN
                            : Impl.TYPE_PARAMETER;
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
    return skipBracketTermWithNLs(PARENS, () -> {
        var result = true;
        if (isNonEmptyVisibilityPrefix() || isModifiersPrefix() || isArgNamesPrefix())
          {
            do
              {
                visibility();
                modifiers();
                result = skipArgNames();
                if (result)
                  {
                    if (skip(Token.t_type))
                      {
                        splitSkip("...");
                      }
                    else
                      {
                        result = skipType();
                        if (result && skipDot() && skip(Token.t_type))
                          {
                            splitSkip("...");
                          }
                      }
                  }
                if (result)
                  {
                    contract();
                  }
              }
            while (result && skipComma());
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
    var result = new FormalOrActual[] { FormalOrActual.both };
    var sr = current() != Token.t_lparen ||
      skipBracketTermWithNLs(PARENS, () -> {
        if (current() != Token.t_rparen)
          {
            do
              {
                if (isNonEmptyVisibilityPrefix() || isModifiersPrefix())
                  {
                    result[0] = FormalOrActual.formal;
                    return false;
                  }
                do
                  {
                    if (!isArgNamesPrefix())
                      {
                        result[0] = FormalOrActual.actual;
                        return false;
                      }
                    skipName();
                  }
                while (skipComma());
                if (skip(Token.t_type))
                  {
                    splitSkip("...");
                  }
                else if (!skipType())
                  {
                    result[0] = FormalOrActual.actual;
                    return false;
                  }
                else if (skipDot())
                  {
                    if (!skip(Token.t_type))
                      {
                        result[0] = FormalOrActual.actual;
                        return false;
                      }
                    else
                      {
                        splitSkip("...");
                      }
                  }
              }
            while (skipComma());
          }
        if (current() != Token.t_rparen)
          {
            result[0] = FormalOrActual.actual;
            return false;
          }
        return true;
      });
    if (CHECKS) check
      (sr || result[0] != FormalOrActual.both); // in case skipBracketTerm failed, we better have a decision.
    return result[0];
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
call        : nameOrType actuals callTail
            ;
nameOrType  : name
            | "type"
            ;
actuals     : actualGens actualArgs
            | dot NUM_LITERAL
            ;
   */
  Call call(Expr target)
  {
    SourcePosition pos = posObject();
    var line = line();
    String n = skip(Token.t_type) ? FuzionConstants.TYPE_NAME : name();
    Call result;
    var skippedDot = false;
    if (skipDot())
      {
        if (current() == Token.t_numliteral)
          {
            result = new Call(pos, target, n, skipNumLiteral().plainInteger());
          }
        else
          {
            result = new Call(pos, target, n);
            skippedDot = true;
          }
      }
    else
      {
        // we must check isActualGens() to distinguish the less operator in 'a < b'
        // from the actual generics in 'a<b>'.
        var g = (!isActualGens()) ? Call.NO_GENERICS : actualGens();
        var l = actualArgs(line);
        result = new Call(pos, target, n, g, l);
      }
    result = callTail(skippedDot, result);
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
        var l = bracketTermWithNLs(CROCHETS, "indexCall", () -> exprList());
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
   * @param skippedDot true if a dot was already skipDot()ed.
   *
   * @param target the target of the call
   *
callTail    : indexCallOpt dotCallOpt
            ;
indexCallOpt: indexCall
            |
            ;
dotCallOpt  : dot call
            |
            ;
   */
  Call callTail(boolean skippedDot, Call target)
  {
    var result = target;
    if (!skippedDot && !ignoredTokenBefore() && current() == Token.t_lcrochet)
      {
        result = indexCall(result);
      }
    if (skippedDot || skipDot())
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
    splitOperator("<");
    if (isOperator('<'))
      {
        result = bracketTermWithNLs(ANGLES, "actualGens", () ->
                                    {
                                      var res = Type.NONE;
                                      splitOperator(">");
                                      if (!isOperator('>'))
                                        {
                                          res = typeList();
                                        }
                                      splitOperator(">");
                                      return res;
                                    });
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
    return (ignoredTokenBefore() || current() != Token.t_lparen)
      ? actualsList(line)
      : bracketTermWithNLs(PARENS, "actualArgs",
                           () -> exprList(),
                           () -> new List<>());
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
      isOperator('.') ||
      ((in != null) ? currentAtMinIndent() == Token.t_indentationLimit ||
                      endsActuals(currentNoLimit()) ||
                      !in.ok()
                    : endsActuals(current()));
  }


  /**
   * Does the given current token end a list of space separated actual arguments to a
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
           t_of              ,
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
            |
            ;
actualsLst  : exprUntilSp actualsLst
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
bracketTerm : brblock
            | klammer
            | inlineArray
            ;
   */
  Expr bracketTerm()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_lbrace   ||
       current() == Token.t_lparen   ||
       current() == Token.t_lcrochet   );

    var c = current();
    switch (c)
      {
      case t_lbrace  : return brblock(false);
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
          f.bracketTerm();
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
    var f = fork();
    var tupleElements = new List<Expr>();
    bracketTermWithNLs(PARENS, "klammer",
                       () -> {
                         do
                           {
                             tupleElements.add(expr());
                           }
                         while (skipComma());
                         return Void.TYPE;
                       },
                       () -> Void.TYPE);

    return
      isLambdaPrefix()          ? lambda(f.bracketTermWithNLs(PARENS, "argNamesOpt", () -> f.argNamesOpt())) :
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
inlineArray : LBRACKET RBRACKET
            | LBRACKET cmaSepElmts RBRACKET
            | LBRACKET semiSepElmts RBRACKET
            ;
cmaSepElmts : expr addCmaElmts
            ;
addCmaElmts : COMMA cmaSepElmts
            | COMMA
            |
            ;
semiSepElmts: expr addSemiElmts
            ;
addSemiElmts: SEMI semiSepElmts
            | SEMI
            |
            ;
   */
  Expr inlineArray()
  {
    SourcePosition pos = posObject();
    var elements = new List<Expr>();
    bracketTermWithNLs(CROCHETS, "inlineArray",
                       () -> {
                         elements.add(expr());
                         var sep = current();
                         var s = sep;
                         var p1 = pos();
                         boolean reportedMixed = false;
                         while ((s == Token.t_comma || s == Token.t_semicolon) && skip(s))
                           {
                             if (current() != Token.t_rcrochet)
                               {
                                 elements.add(expr());
                               }
                             s = current();
                             if ((s == Token.t_comma || s == Token.t_semicolon) && s != sep && !reportedMixed)
                               {
                                 AstErrors.arrayInitCommaAndSemiMixed(pos, posObject(p1), posObject());
                                 reportedMixed = true;
                               }
                           }
                         return Void.TYPE;
                       },
                       () -> Void.TYPE);
    return new InlineArray(pos, elements);
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
      case t_lcrochet  :         result = bracketTerm();                               break;
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
stringTerm  : '&quot;any chars&quot;'
            | '&quot; any chars &dollar;' IDENT stringTermD
            | '&quot; any chars{' block stringTermB
            ;
stringTermD : 'any chars&quot;'
            | 'any chars&dollar;' IDENT stringTermD
            | 'any chars{' block stringTermB
            ;
stringTermB : '}any chars&quot;'
            | '}any chars&dollar;' IDENT stringTermD
            | '}any chars{' block stringTermB
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
          || isNamePrefix()    // Matches call, qualThis and env
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
            | "fun" call
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
function    : formArgsOpt
              typeOpt
              inherits
              contract
              ( block
              | "is" block
              | ARROW block
              )
            ;
   */
  Function function(SourcePosition pos)
  {
    List<Feature> a = formArgsOpt();
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
            match(true, Token.t_rbrace, "match");
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
block       : stmnts
            | brblock
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
    else if (current(mayBeAtMinIndent) != Token.t_lbrace)
      {
        return relaxLineAndSpaceLimit(() -> {
            var l = stmnts();
            var pos2 = l.size() > 0 ? l.getLast().pos() : pos1;
            return new Block(pos1, pos2, l);
          });
      }
    else
      {
        return brblock(mayBeAtMinIndent);
      }
  }


  /**
   * Parse block
   *
brblock     : BRACEL stmnts BRACER
            ;
   */
  Block brblock(boolean mayBeAtMinIndent)
  {
    SourcePosition pos1 = posObject();
    return bracketTermWithNLs(mayBeAtMinIndent, BRACES, "block",
                              () -> {
                                var l = stmnts();
                                var pos2 = posObject();
                                return new Block(pos1, pos2, l);
                              });
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
            | stmnt semiOrFlatLF stmnts (semiOrFlatLF | )
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
      if (CHECKS) check
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
implFldIter : "in" exprInLine
            ;
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
                             new List<Feature>(),
                             new List<>(),
                             c1,p1);
    Feature f2 = new Feature(pos,v2,m2,r2,new List<>(n2),
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
            if (CHECKS) check
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
                  | env
                  | plainLambda
                  | call
                  ;
   */
  Expr callOrFeatOrThis()
  {
    return
      isAnonymousPrefix()   ? anonymous()   : // starts with value/ref/:/fun/name
      isQualThisPrefix()    ? qualThis()    : // starts with name
      isEnvPrefix()         ? env()         : // starts with name
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
            if (isFullStop())
              {
                syntaxError("'.' (not followed by white space)", "qualThis");
              }
            else
              {
                syntaxError("'.'", "qualThis");
              }
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
   * Parse env
   *
env         : simpletype dot "env"
            ;
   */
  Env env()
  {
    var t = simpletype(null);
    skipDot();
    var e = new Env(posObject(), t);
    match(Token.t_env, "env");
    return e;
  }


  /**
   * Check if the current position starts an env.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a env
   */
  boolean isEnvPrefix()
  {
    return isNamePrefix() && fork().skipEnvPrefix();
  }


  /**
   * Check if the current position starts an env.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an env.
   */
  boolean skipEnvPrefix()
  {
    return skipSimpletype() && skipDot() && skip(Token.t_env);
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
            | "is" "intrinsic_constructor"
            | "is" block
            | ARROW block
            | "of" block
            | fullStop
            ;
   */
  Impl implRout()
  {
    SourcePosition pos = posObject();
    Impl result;
    var startRoutine = (currentAtMinIndent() == Token.t_lbrace || skip(true, Token.t_is));
    if      (startRoutine    ) { result = skip(Token.t_abstract             ) ? Impl.ABSTRACT              :
                                          skip(Token.t_intrinsic            ) ? Impl.INTRINSIC             :
                                          skip(Token.t_intrinsic_constructor) ? Impl.INTRINSIC_CONSTRUCTOR :
                                          new Impl(pos, block(true)      , Impl.Kind.Routine   ); }
    else if (skip("=>")      ) { result = new Impl(pos, block(true)      , Impl.Kind.RoutineDef); }
    else if (skip(Token.t_of)) { result = new Impl(pos, block(true)      , Impl.Kind.Of        ); }
    else if (skipFullStop()  ) { result = new Impl(pos, new Block(pos, pos, new List<>()), Impl.Kind.Routine); }
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
        currentAtMinIndent() == Token.t_of     ||
        isOperator("=>")                       ||
        isFullStop()                              )
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
      currentAtMinIndent() == Token.t_of ||
      isOperator(":=") ||
      isOperator("=>") ||
      isFullStop();
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
            | simpletype
            ;
funArgs     : LPAREN typeList RPAREN
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
        result = simpletype(null);
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
    else if (current() == Token.t_lparen)
      {
        var a = bracketTermWithNLs(PARENS, "funArgs", () -> current() != Token.t_rparen ? typeList() : Type.NONE);
        matchOperator("->", "onetype");
        result = Type.funType(pos, type(), a);
      }
    else
      {
        result = simpletype(null);
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
    else
      {
        var f = fork();
        if (f.skipBracketTermWithNLs(PARENS, () -> f.current() == Token.t_rparen || f.skipTypeList()))
          {
            skipBracketTermWithNLs(PARENS, () -> current() == Token.t_rparen || skipTypeList());
            result = skip("->") && skipType();
          }
        else
          {
            result = skipSimpletype() && (!skip("->") || skipSimpletype());
          }
      }
    return result;
  }


  /**
   * Parse simpletype
   *
   * @param lhs the left hand side for this type that was already parsed, null
   * if none.
   *
simpletype  : name actualGens typeTail
            | name typePars typeTail
            ;
typeTail    : dot simpletype
            |
            ;
   */
  Type simpletype(Type lhs)
  {
    do
      {
        var p = posObject();
        var n = name();
        var a = actualGens();
        if (a.isEmpty())
          {
            a = typePars();
          }
        lhs = new Type(p, n, a, lhs);
      }
    while (!isDotEnvOrType() && skipDot());
    return lhs;
  }


  /**
   * Check if the current position is a dot followed by "env" or "type".  Does
   * not change the position of the parser.
   *
   * @return true iff the next token(s) is a dot followed by "env"
   */
  boolean isDotEnvOrType()
  {
    if (isDot())
      {
        var f = fork();
        return f.skipDot() && (f.skip(Token.t_env ) ||
                               f.skip(Token.t_type)    );
      }
    return false;
  }


  /**
   * Check if the current position is a simpletype and skip it.
   *
   * @return true iff the next token(s) is a simpletype, otherwise no simpletype
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipSimpletype()
  {
    boolean result = false;
    return skipName() && skipActualGens() && skipTypePars() && (isDotEnvOrType() || !skipDot() || skipSimpletype());
  }


  /**
   * Parse typePars
   *
typePars    : typeInParens typePars
            | "(" typeList ")"
            |
            ;
   */
  List<AbstractType> typePars()
  {
    if (ignoredTokenBefore() || current() != Token.t_lparen)
      {
        var res = new List<AbstractType>();
        while (isTypePrefix())
          {
            res.add(typeInParens());
          }
        return res;
      }
    else
      {
        return bracketTermWithNLs(PARENS, "typePars",
                                  () -> typeList(),
                                  () -> new List<>());
      }
  }


  /**
   * Parse typeInParens
   *
   * This is a little tricky since a lambda '()->i32' or '(u8,bool)->f64' is a type,
   * while '(u8)', '((()->i32))' or '((((u8,bool)->f64)))' are types in parentheses.
   *
typeInParens: "(" typeInParens ")"
            | type         // no white space except enclosed in { }, [ ], or ( ).
            ;
   */
  AbstractType typeInParens()
  {
    AbstractType result;
    if (current() == Token.t_lparen)
      {
        var pos = pos();
        var l = bracketTermWithNLs(PARENS, "typeInParens",
                                   () -> typeList(),
                                   () -> new List<AbstractType>());
        if (isOperator("->"))
          {
            matchOperator("->", "onetype");
            result = Type.funType(posObject(pos), type(), l);
          }
        else if (l.size() == 1)
          {
            result = l.get(0);
          }
        else
          {
            syntaxError(pos, "exaclty one type", "typeInParens");
            result = Types.t_ERROR;
          }
      }
    else
      {
        var eas = endAtSpace(pos());
        result = type();
        endAtSpace(eas);
      }
    return result;
  }


  /**
   * Check if the current position has typePars and skip them.
   *
   * @return true iff the next token(s) form typePars, otherwise no typePars
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipTypePars()
  {
    if (ignoredTokenBefore() || current() != Token.t_lparen)
      {
        while (skipTypeInParens())
          {
          }
        return true;
      }
    else
      {
        var f = fork();
        if (f.skipBracketTermWithNLs(PARENS, ()->f.skipTypeList()))
          {
            skipBracketTermWithNLs(PARENS, ()->skipTypeList());
            return true;
          }
      }
    return false;
  }


  /**
   * Check if the current position has typeInParens and skip them.
   *
   * @return true if a typeInPaens was skipped
   */
  boolean skipTypeInParens()
  {
    if (isTypePrefix())
      {
        var f = fork();
        f.endAtSpace(pos());
        if (f.skipType())
          {
            var eas = endAtSpace(pos());
            skipType();
            endAtSpace(eas);
            return true;
          }
      }
    var f = fork();
    if (f.skipBracketTermWithNLs(PARENS, ()->f.skipTypeList()))
      {
        return skipBracketTermWithNLs(PARENS, ()->skipTypeList());
      }
    return false;
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
   * Parse "." if it is found.
   *
dot         : "."      // either preceded by white space or not followed by white space
            ;
   *
   * @return true iff a "." was found and skipped.
   */
  boolean skipDot()
  {
    var result = !isFullStop();
    if (result)
      {
        result = skip('.');
        if (!result)
          { // allow dot to appear in new line
            var oldLine = sameLine(-1);
            result = skip('.');
            sameLine(result ? line() : oldLine);
          }
      }
    return result;
  }


  /**
   * Check if current is "." but not a fullStop.
   */
  boolean isDot()
  {
    var result = false;
    if (!isFullStop())
      {
        var oldLine = sameLine(-1);
        result = isOperator('.');
        sameLine(oldLine);
      }
    return result;
  }


  /**
   * Check if current is "." followed by white space.
   */
  boolean isFullStop()
  {
    return isOperator('.') && !ignoredTokenBefore() && ignoredTokenAfter();
  }


  /**
   * Parse "." followed by white space if it is found
   *
fullStop    : "."        // not following white space but followed by white space
            ;
   *
   * @return true iff a "." follwed by white space was found and skipped.
   */
  boolean skipFullStop()
  {
    return isFullStop() && skip('.');
  }

}

/* end of file */
