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
import java.util.Optional;

import dev.flang.ast.*;

import dev.flang.util.Errors;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.SourceRange;
import dev.flang.util.StringHelpers;


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
  static Parens BRACKETS = new Parens( Token.t_lbracket, Token.t_rbracket );
  static Parens ANGLES   = new Parens( "<"             , ">"              );


  /*----------------------------  variables  ----------------------------*/


  /**
   * Whether to allow the usage of the `set` keyword.
   *
   * Controlled by the `-XenableSetKeyword` option to `fz`, if false, the
   * parser will throw an `illegalUseOfSetKeyword` error when encountering
   * the `set` keyword.
   */
  public static boolean ENABLE_SET_KEYWORD = false;

  private boolean _nestedIf = false;
  private int _lastIfLine = -1;
  private boolean _elif = false;

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a parser for the given file or byte array
   */
  public Parser(Path fname, byte[] sf)
  {
    super(fname, sf);
  }


  /**
   * Create a parser for the given file
   */
  public Parser(Path fname)
  {
    this(fname, null);
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
   *   "semi, exprs (69 times), block, impl, feature, unit"
   *
   * if a semicolon was expected after parsing 69 expressions in a block in the
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
                  .append(count > 1 ? " (" + StringHelpers.times(count) + ")" : "");
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
   * Parse a unit, i.e., exprs followed by Token.t_eof.
   *
unit        : block EOF
            ;
   */
  public List<Expr> unit()
  {
    var result = block();
    if (!Errors.any())
      {
        match(Token.t_eof, "Unit");
      }
    return result._expressions;
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
    int last = lastTokenPos();
    boolean result = last >= 0 && lineNum(last) != lineNum(tokenPos());
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
    var pos = tokenSourcePos();
    var v = visibility();
    var m = modifiers();
    var n = featNames();
    return routOrField(pos, new List<Feature>(), v, m, n, 0);
  }


  /**
   * Parse routOrField:
   *
   * Note that this fork()s the parser repeatedly in case several feature names
   * are declared given as parameter n.
   *
   *
routOrField : routine
            | field
            ;
routine     : formArgsOpt
              returnType
              effects
              inherits
              contract
              implRout
            ;
field       : returnType
              contract
              implFldOrRout
            ;
   */
  FList routOrField(SourcePosition pos, List<Feature> l, Visi v, int m, List<List<ParsedName>> n, int i)
  {
    var name = n.get(i);
    var p2 = (i+1 < n.size()) ? fork() : null;
    var forkAtFormArgs = isEmptyFormArgs() ? null : fork();
    var a = formArgsOpt(false);
    var r = returnType();
    var eff = effects();
    var hasType = r instanceof FunctionReturnType;
    var inh = inherits();
    Contract c = contract(forkAtFormArgs);
    Impl p =
      a  .isEmpty()    &&
      eff == UnresolvedType.NONE &&
      inh.isEmpty()       ? implFldOrRout(hasType)
                          : implRout(hasType);
    p = handleImplKindOf(pos, p, i == 0, l, inh, v);
    l.add(new Feature(v,m,r,name,a,inh,c,p,eff));
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
   *
   * @param v the visibility to be used for the features defined in of <block>
   *
   */
  Impl handleImplKindOf(SourcePosition pos, Impl p, boolean first, List<Feature> l, List<AbstractCall> inh, Visi v)
  {
    if (p._kind == Impl.Kind.Of)
      {
        if (inh.isEmpty())
          {
            AstErrors.featureOfMustInherit(pos, p.pos);
          }
        else
          {
            var c = (Call) inh.getLast();
            var ng = new List<AbstractType>();
            ng.addAll(c.actualTypeParameters());
            addFeaturesFromBlock(first, l, p.expr(), ng, p, v);
            c._generics = ng;
          }
        p = new Impl(p.pos, emptyBlock(), Impl.Kind.Routine);
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
   * @param e the expressions containing the feature declarations to be added, in
   * this case "x, y, z."
   *
   * @param g the list of types to be collected, will be added as generic
   * arguments to 'choice' in this example
   *
   * @param p Impl that contains the position of 'of' for error messages.
   *
   * @param v the visibility to be used for the features defined in of <block>
   *
   */
  private void addFeaturesFromBlock(boolean first, List<Feature> list, Expr e, List<AbstractType> g, Impl p, Visi v)
  {
    if (e instanceof Block b)
      {
        b._expressions.forEach(x -> addFeaturesFromBlock(first, list, x, g, p, v));
      }
    else if (e instanceof Feature f)
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
                f.setVisbility(v);

                list.add(f);
              }
            g.add(new ParsedType(f.pos(), f.featureName().baseName(), new List<>(), new OuterType(f.pos())));
          }
      }
    else
      {
        AstErrors.featureOfMustContainOnlyDeclarations(e, p.pos);
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
    if (!skipQual())
      {
        return false;
      }
    if (skipComma())
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
    var p = fork();
    return
      (!p.isTypePrefix() || p.skipType(true, true)) &&
      (!p.isOperator("!") || p.skipEffects()) &&
      p.skipInherits() &&
      (p.isContractPrefix  () ||
       p.isImplPrefix      ());
  }


  /**
   * Parse visibility
   *
visibility  : visiFlag
            |
            ;
visiFlag    : "private" colon "module"
            | "private" colon "public"
            | "private"
            | "module" colon "public"
            | "module"
            | "public"
            ;
  */
  Visi visibility()
  {
    Visi v = Visi.UNSPECIFIED;
    if (isNonEmptyVisibilityPrefix())
      {
        if (skip(Token.t_private)) {
          if (skipColon())
            {
              if (skip(Token.t_module))
                {
                  v = Visi.PRIVMOD;
                }
              else if (skip(Token.t_public))
                {
                  v = Visi.PRIVPUB;
                }
              else
                {
                  syntaxError(tokenPos(), "'module' or 'public' after 'private :'", "visibility");
                }
            }
          else
            {
              v = Visi.PRIV;
            }
        }
        else if (skip(Token.t_module)) {
          if (skipColon())
            {
              if (skip(Token.t_public))
                {
                  v = Visi.MODPUB;
                }
              else
                {
                  syntaxError(tokenPos(), "'public' after 'module :'", "visibility");
                }
            }
          else
            {
              v = Visi.MOD;
            }
        }
        else if (skip(Token.t_public    )) { v = Visi.PUB; }
        else                            { throw new Error();     }
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
      case t_private     :
      case t_module      :
      case t_public      : return true;
      default         : return false;
      }
  }


  /**
   * Parse qualified name
   *
qual        : name
            | name dot qual
            | type dot qual
            ;
   */
  List<ParsedName> qual(boolean mayBeAtMinIndent)
  {
    List<ParsedName> result = new List<>();
    do
      {
        if (skip(mayBeAtMinIndent, Token.t_type))
          {
            result.add(new ParsedName(SourcePosition.builtIn, FuzionConstants.TYPE_NAME));
            if (!isDot())
              {
                matchOperator(".", "qual");
                result.add(ParsedName.ERROR_NAME);
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
            | "index" LBRACKET ".." RBRACKET
            | "index" LBRACKET RBRACKET
            | "set" LBRACKET RBRACKET
            | "set" IDENT
            ;
   *
   * @return the parsed name, Errors.ERROR_STRING if current location could not be identified as a name.
   */
  ParsedName name()
  {
    return name(false, false);
  }
  ParsedName name(boolean mayBeAtMinIndent, boolean ignoreError)
  {
    var result = ParsedName.ERROR_NAME;
    int pos = tokenPos();
    if (isNamePrefix(mayBeAtMinIndent))
      {
        var oldLine = sameLine(line());
        switch (current(mayBeAtMinIndent))
          {
          case t_ident  : result = ignoreError ? ParsedName.DUMMY
                                               : new ParsedName(tokenSourceRange(), identifier(mayBeAtMinIndent));
                          next();
                          break;
          case t_infix  :
          case t_prefix :
          case t_postfix: result = opName(mayBeAtMinIndent, ignoreError);  break;
          case t_ternary:
            {
              next();
              if (skip(Token.t_question))
                {
                  if (skipColon())
                    {
                      result = new ParsedName(sourceRange(pos), "ternary ? :");
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
              if (!ignoreError || current() == Token.t_lbracket)
                {
                  match(Token.t_lbracket, "name: index");
                  var dotdot = skip("..");
                  if (!ignoreError || current() == Token.t_rbracket)
                    {
                      match(Token.t_rbracket, "name: index");
                      result = new ParsedName(sourceRange(pos),
                                              dotdot ? FuzionConstants.FEATURE_NAME_INDEX_DOTDOT
                                                     : FuzionConstants.FEATURE_NAME_INDEX);
                    }
                }
              break;
            }
          case t_set    :
            {
              next();
              if (current() == Token.t_lbracket)
                {
                  match(Token.t_lbracket, "name: set");
                  if (!ignoreError || current() == Token.t_rbracket)
                    {
                      match(Token.t_rbracket, "name: set");
                      result = new ParsedName(sourceRange(pos), FuzionConstants.FEATURE_NAME_INDEX_ASSIGN);
                    }
                }
              else if (current() == Token.t_ident)
                {
                  var id = identifier();
                  match(Token.t_ident, "name: set");
                  result = new ParsedName(sourceRange(pos), id + " =");
                }
              else if (!ignoreError)
                {
                  if (ENABLE_SET_KEYWORD)
                    {
                      syntaxError(pos, "'[ ]' or identifier after 'set'", "name");
                    }
                  else
                    {
                      syntaxError(pos, "'[ ]' after 'set'", "name");
                    }
                }
              break;
            }
          default: throw new Error(current(mayBeAtMinIndent).toString());
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
        return name(false, true) != ParsedName.ERROR_NAME;
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
   * @param ignoreError to not report an error but just return
   * Errors.ERROR_STRING in case we did not find 'op'.
   *
   * @param mayBeAtMinIndent
   *
   */
  ParsedName opName(boolean mayBeAtMinIndent, boolean ignoreError)
  {
    int pos = tokenPos();
    String inPrePost = current(mayBeAtMinIndent).keyword();
    next();
    String res = operatorOrError();
    if (!ignoreError || res != Errors.ERROR_STRING)
      {
        match(Token.t_op, "infix/prefix/postfix name");
        res = inPrePost + " " + res;
      }
    return new ParsedName(sourceRange(pos), res);
  }


  /**
   * Parse modifiers flags
   *
modifiers   : modifier modifiers
            |
            ;
modifier    : "redef"
            | "fixed"
            ;
   *
   * @return logically or'ed set of FuzionConstants.MODIFIER_* constants found.
   */
  int modifiers()
  {
    int ms = 0;
    int pos = tokenPos();
    while (isModifiersPrefix())
      {
        int m;
        int p2 = tokenPos();
        switch (current())
          {
          case t_redef       : m = FuzionConstants.MODIFIER_REDEFINE    ; break;
          case t_fixed       : m = FuzionConstants.MODIFIER_FIXED       ; break;
          default            : throw new Error();
          }
        if ((ms & m) != 0)
          {
            Errors.error(sourcePos(pos),
                         "Syntax error: modifier '"+current().keyword()+"' specified repeatedly.",
                         "Within one feature declaration, each modifier may at most appear once.\n" +
                         "Second occurrence of modifier at " + sourcePos(p2) + "\n" +
                         "Parse stack: " + parseStack());
          }
        ms = ms | m;
        next();
      }
    return ms;
  }


  /**
   * Check if the current position starts non-empty modifiers flags.  Does not
   * change the position of the parser.
   *
   * @return true iff the next token(s) start a name
   */
  boolean isModifiersPrefix()
  {
    switch (current())
      {
      case t_redef       :
      case t_fixed       : return true;
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
  List<List<ParsedName>> featNames()
  {
    var result = new List<List<ParsedName>>(qual(true));
    while (skipComma())
      {
        result.add(qual(true));
      }
    return result;
  }


  /**
   * Parse optional formal argument list. Result is empty List in case no formArgs is found.
   *
   * @param forPreOrPostCondition if true, the redefine modifier will be removed
   * since pre or post features are not redefined.
   *
formArgsOpt : formArgs
            |
            ;
   */
  List<AbstractFeature> formArgsOpt(boolean forPreOrPostCondition)
  {
    return isEmptyFormArgs() ? new List<AbstractFeature>()
                             : formArgs(forPreOrPostCondition);
  }


  /**
   * Parse optional formal argument list. Result is true in case no formArgs is found.
   */
  boolean isEmptyFormArgs()
  {
    return
      current() != Token.t_lparen ||
      fork().skipType(false,
                      false); // result type such as '(i32)->bool' or
                              // '(a,b)|(c,d)|()' is parsed as resulttype, but
                              // a type in parentheses like '(list i32)', '(a,
                              // b i32)' is parsed as an args list.
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
            ;
argType     : type
            | typeType
            | typeType COLON type
            |
            ;
   */
  List<AbstractFeature> formArgs(boolean forPreOrPostCondition)
  {
    return bracketTermWithNLs(PARENS, "formArgs",
                              () -> {
                                var result = new List<AbstractFeature>();
                                do
                                  {
                                    Visi v = visibility();
                                    int m = modifiers();
                                    if (forPreOrPostCondition)
                                      {
                                        m = m & ~FuzionConstants.MODIFIER_REDEFINE;
                                      }
                                    var n = argNames();
                                    AbstractType t;
                                    Impl i;
                                    if (current() == Token.t_type)
                                      {
                                        i = typeType();
                                        t = skipColon() ? type()
                                                        : new BuiltInType(FuzionConstants.ANY_NAME);
                                      }
                                    else if (isTypePrefix())
                                      {
                                        i = Impl.FIELD;
                                        t = type();
                                      }
                                    else
                                      {
                                        i = null; // alloc one instance of Impl for each arg since they contain state
                                        t = null;
                                      }
                                    for (var s : n)
                                      {
                                        result.add(new Feature(s._pos, forPreOrPostCondition ? Visi.PRIV : v, m, t, s._name, Contract.EMPTY_CONTRACT,
                                                               i == null ? new Impl(s._pos, null, Impl.Kind.FieldActual)
                                                                         : i));
                                      }
                                  }
                                while (skipComma());
                                return result;
                              },
                              () -> new List<AbstractFeature>()
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
                        if (skipColon())
                          {
                            result = skipType();
                          }
                      }
                    else
                      {
                        result = skipType();
                      }
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
    var sr = isEmptyFormArgs() ||
      skipBracketTermWithNLs(PARENS, () -> {
        if (currentAtMinIndent() != Token.t_rparen)
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
                    if (skipColon())
                      {
                        skipType();
                      }
                    result[0] = FormalOrActual.formal;
                  }
                // tolerate missing type here
                else if ((skipType() || true) && skipDot())
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
        if (currentAtMinIndent() != Token.t_rparen)
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
  List<ParsedName> argNames()
  {
    List<ParsedName> result = new List<>(name());
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
    return skipArgNames(true);
  }
  boolean skipArgNames(boolean mayUseCommas)
  {
    boolean result;
    do
      {
        result = skipName();
      }
    while (result && mayUseCommas && skipComma());
    return result;
  }


  /**
   * Parse returnType
   *
returnType  : boundType
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
        result = new FunctionReturnType(boundType());
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
   * Parse effects
   *
effects     : EXCLAMATION typeList
            |
            ;
EXCLAMATION : "!"
            ;
   */
  List<AbstractType> effects()
  {
    var result = UnresolvedType.NONE;
    if (skip('!'))
      {
        result = typeList();
      }
    return result;
  }


  /**
   * Check if the current position is an effects and if so, skip it
   *
   * @return true iff the next token(s) start a constructor return type,
   * otherwise no functionReturnType was found and the parser/lexer is at an
   * undefined position.
   */
  boolean skipEffects()
  {
    return skip('!') && skipTypeList();
  }


  /**
   * Check if the current position starts a returnType that is not a
   * FunctionReturnType.  Does not change the position of the parser.
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
   * Check if the current position is a, possibly empty, inherits. If so, skip
   * it.
   *
   * @return true iff the next token(s) are an inherits clause and were skipped.
   *
   */
  boolean skipInherits()
  {
    // NOTE: this uses skipCallList instead of skipPureCallList
    // that the parser does not throw syntax errors when testing for isFeaturePrefix
    // for `debug` in expressions like: `pre debug: u128.this ≤ i8.max.as_u128`
    return !skipColon() || skipCallList();
  }


  /**
   * Parse inherit clause
   *
inherit     : COLON pureCallList
            ;
   */
  List<AbstractCall> inherit()
  {
    matchOperator(":", "inherit");
    return pureCallList();
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
   * Parse pureCallList
   *
pureCallList    : pureCall ( COMMA pureCallList
                           |
                           )
                ;
   */
  List<AbstractCall> pureCallList()
  {
    var result = new List<AbstractCall>(pureCall(null));
    while (skipComma())
      {
        result.add(pureCall(null));
      }
    return result;
  }


  /**
   * Parse callList
   *
callList    : call ( COMMA callList
                   |
                   )
            ;
   */
  List<Expr> callList()
  {
    var result = new List<Expr>(call(null));
    while (skipComma())
      {
        result.add(call(null));
      }
    return result;
  }


  /**
   * Check if the current position is a callList. If so, skip it.
   *
   * @return true iff the next token(s) are a callList.
   */
  boolean skipCallList()
  {
    var result = isNamePrefix();
    if (result)
      {
        var ignore = callList();
      }
    return result;
  }


  /**
   * Parse pure call, i.e. a call that is really a call and not a.b.env or x.y.type.
   *
   * @param target the target of the call or null if none.
   */
  Call pureCall(Call target)
  {
    return (Call) call(true, target);
  }


  /**
   * Parse call, including `.env` and `.type` calls
   *
   * @param target the target of the call or null if none.
   */
  Expr call(Expr target)
  {
    return call(false, target);
  }


  /**
   * Parse pure or non-pure call depending on `pure` argument.
   *
   * @param pure true iff `pureCall` is to be parsed, otherwise `call` is parsed.
   *
   * @param target the target of the call or null if none.
   *
pureCall    : name actuals pureCallTail
            ;
call        : name actuals callTail
            ;
actuals     : actualArgs
            | dot NUM_LITERAL
            ;
   */
  Expr call(boolean pure, Expr target)
  {
    SourcePosition pos = tokenSourcePos();
    var n = name();
    Call result;
    var skippedDot = false;
    if (skipDot())
      {
        if (current() == Token.t_numliteral)
          {
            var select = skipNumLiteral().plainInteger();
            int s = -1;
            try
              {
                s = Integer.parseInt(select);
                if (CHECKS) check
                  (s >= 0); // parser should not allow negative value
              }
            catch (NumberFormatException e)
              {
                AstErrors.illegalSelect(pos, select, e);
              }
            result = new ParsedCall(target, n, s);
          }
        else
          {
            result = new ParsedCall(target, n);
            skippedDot = true;
          }
      }
    else
      {
        var l = actualArgs();
        result = new ParsedCall(target, n, l);
      }

    // replace calls with erroneous name by ParsedCall.ERROR.
    result = n == ParsedName.ERROR_NAME ? ParsedCall.ERROR : result;

    return pure ? pureCallTail(skippedDot, result)
                : callTail(    skippedDot, result);
  }


  /**
   * Parse indexcall
   *
indexCall   : LBRACKET actualCommas RBRACKET indexTail
            ;
indexTail   : ":=" exprInLine
            |
            ;
   */
  Call indexCall(Expr target)
  {
    Call result;
    do
      {
        SourcePosition pos = tokenSourcePos();
        var l = bracketTermWithNLs(BRACKETS, "indexCall", () -> actualCommas());
        String n = FuzionConstants.FEATURE_NAME_INDEX;
        if (skip(":="))
          {
            l.add(exprInLine());
            n = FuzionConstants.FEATURE_NAME_INDEX_ASSIGN;
          }
        if (l.isEmpty())
          { // In case result is Function, avoid automatic conversion `a[i]`
            // into `a[i].call`
            l = ParsedCall.NO_PARENTHESES;
          }
        result = new ParsedCall(target, new ParsedName(pos, n), l);
        target = result;
      }
    while (!ignoredTokenBefore() && current() == Token.t_lbracket);
    return result;
  }


  /**
   * Parse callTail and pureCallTail
   *
   * @param skippedDot true if a dot was already skipDot()ed.
   *
   * @param target the target of the call
   *
pureCallTail: indexCall pureCallTail
            | dot call  pureCallTail
            |
            ;
   */
  Call pureCallTail(boolean skippedDot, Call target)
  {
    var result = target;
    if (!skippedDot && !ignoredTokenBefore() && current() == Token.t_lbracket)
      {
        result = pureCallTail(false, indexCall(result));
      }
    else if (skippedDot || skipDot())
      {
        result = pureCallTail(false, pureCall(result));
      }
    return result;
  }


  /**
   * Parse callTail
   *
   * @param skippedDot true if a dot was already skipDot()ed.
   *
   * @param target the target of the call
   *
callTail    : indexCall  callTail
            | dot call   callTail
            | dot "env"  callTail
            | dot "type" callTail
            | dot "this" callTail
            |
            ;
   */
  Expr callTail(boolean skippedDot, Expr target)
  {
    Expr result = target;
    if (!skippedDot && !ignoredTokenBefore() && current() == Token.t_lbracket)
      {
        result = callTail(false, indexCall(result));
      }
    else if (skippedDot || skipDot())
      {
        if (skip(Token.t_env))
          {
            AbstractType t = result.asParsedType();
            if (t == null)
              {
                AstErrors.noValidLHSInExpresssion(result, ".env");
                t = Types.t_ERROR;
              }
            result = callTail(false, new Env    (sourceRange(target.pos()), t));
          }
        else if (skip(Token.t_type))
          {
            AbstractType t = result.asParsedType();
            if (t == null)
              {
                AstErrors.noValidLHSInExpresssion(result, ".type");
                t = Types.t_ERROR;
              }
            result = callTail(false, new DotType(sourceRange(target.pos()), t));
          }
        else if (skip(Token.t_this))
          {
            var q = result.asQualifier();
            if (q == null)
              {
                AstErrors.qualifierExpectedForDotThis(tokenSourcePos(), result);
              }
            else
              {
                result = callTail(false, new This(q));
              }
          }
        else
          {
            result = call(result);
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
    boolean result = skipType(false, true);
    while (skipComma())
      {
        result = result && skipType(false, true);
      }
    return result;
  }


  /**
   * Parse actualArgs
   *
actualArgs  : actualSpaces
            | LPAREN actualCommas RPAREN
            ;
   */
  List<Expr> actualArgs()
  {
    return (ignoredTokenBefore() || current() != Token.t_lparen)
      ? actualSpaces()
      : bracketTermWithNLs(PARENS, "actualArgs", () -> actualCommas());
  }


  /**
   * Does the current symbol end a list of space separated actual arguments to a
   * call.
   *
   * @param atMinIndent the indentation used for the actuals, null if none.
   *
   * @return true if the next symbol ends actual arguments or in!=null and the
   * next symbol is not properly indented.
   */
  boolean endsActuals(boolean atMinIndent)
  {
    return
      // `.call` ends immediately
      isOperator('.') ||

      switch (current(atMinIndent))
      {
      case t_semicolon       ,
           t_comma           ,
           t_rparen          ,
           t_rbracket        ,
           t_lbrace          ,
           t_rbrace          ,
           t_is              ,
           t_of              ,
           t_pre             ,
           t_post            ,
           t_inv             ,
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
           t_question        ,
           t_indentationLimit,
           t_lineLimit       ,
           t_spaceOrSemiLimit,
           t_colonLimit      ,
           t_barLimit        ,
           t_eof             -> true;

      case t_op            ->
        {
          if (// !ignoredTokenBefore(): We have an operator '-' like this
              // 'f-xyz', 'f- xyz', i.e, stuck to the called function, we do not
              // parse it as part of the args.
              !ignoredTokenBefore() ||

              // ignoredTokenBefore() and ignoredTokenAfter(): An operator '-'
              // like this 'f a b - xyz', so the arg list ends with 'b' and '-'
              // will be parsed as an infix operator on 'f a b' and 'xyz'.
              ignoredTokenAfter())
            {
              yield true;
            }
          else
            { // ignoredTokenBefore() and !ignoredTokenAfter(): An operator '-'
              // like this '(... f a b -)', so the arg list ends with 'b' and '-'
              // will be parsed as an postfix operator on 'f a b' (see #2272).
              var f = fork();
              f.next();
              yield f.endsActuals(atMinIndent);
            }
        }

      // No more actuals if we have a string continuation as in "value $x is
      // ok" for the string after '$x' or in "bla{f a b}blub" for the string
      // after 'f a b'.
      default              -> isContinuedString(current(atMinIndent));
      };
  }


  /**
   * Parse actualCommas
   *
actualCommas: actualSome
            |
            ;
actualSome  : operatorExpr actualMore
            ;
actualMore  : COMMA actualSome
            |
            ;
   */
  List<Expr> actualCommas()
  {
    var result = new List<Expr>();
    if (current() != Token.t_rparen   &&
        current() != Token.t_rbracket   )
      {
        do
          {
            result.add(operatorExpr(false /* see #3978 for an example where this is necessary */));
          }
        while (skipComma());
      }
    return result;
  }


  /**
   * Parse space separated actual arguments
   *
actualSpaces: actualSpace actualSpaces
            |
            ;
   */
  List<Expr> actualSpaces()
  {
    List<Expr> result = ParsedCall.NO_PARENTHESES;
    if (ignoredTokenBefore() && !endsActuals(false))
      {
        var in = new Indentation();
        result = new List<>();
        while (!endsActuals(!result.isEmpty()) && in.ok())
          {
            result.add(actualSpace());
            in.next();
          }
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
       current() == Token.t_lbracket   );

    var c = current();
    switch (c)
      {
      case t_lbrace  : return block();
      case t_lparen  : return klammer();
      case t_lbracket: return inlineArray();
      default: throw new Error("Unexpected case: "+c);
      }
  }


  /**
   * An actual that ends in white space or semicolon unless enclosed in { }, [ ], or ( ).
   *
actualSpace :  operatorExpr         // no white space or semicolon except enclosed in { }, [ ], or ( ).
            ;

   */
  Expr actualSpace()
  {
    var eas = endAtSpaceOrSemi(tokenPos());
    var result = operatorExpr();
    endAtSpaceOrSemi(eas);
    return result;
  }


  /**
   * An expr that does not exceed a single line unless it is enclosed by { } or
   * ( ).
   *
exprInLine  : operatorExpr   // within one line
            ;
   */
  Expr exprInLine()
  {
    var oldLine = sameLine(line());
    var result = operatorExpr();
    sameLine(oldLine);
    return result;
  }


  /**
   * Parse
   *
operatorExpr  : opExpr
                ( QUESTION expr  COLON expr
                | QUESTION casesBars
                |
                )
              ;
   */
  Expr operatorExpr()
  {
    return operatorExpr(true);
  }
  Expr operatorExpr(boolean mayUseCommas)
  {
    Expr result = opExpr(mayUseCommas);
    if (current() == Token.t_question)
      {
        SourcePosition pos = tokenSourcePos();
        var i = new Indentation();
        skip(Token.t_question);
        var f0 = fork();
        if (f0.isCasesAndNotExpr())
          {
            result = new Match(pos, result, casesBars(i));
          }
        else
          {
            i.ok();
            var eac = endAtColon(true);
            Expr f = operatorExpr(mayUseCommas);
            endAtColon(eac);
            i.next();
            i.ok();
            matchOperator(":", "expr of the form >>a ? b : c<<");
            Expr g = operatorExpr(mayUseCommas);
            i.end();
            result = new ParsedCall(result, new ParsedName(pos, "ternary ? :"), new List<>(f, g));
          }
      }
    return result;
  }


  /**
   * Parse opExpr
   *
opExpr      :     opTail
            | ops opTail
            | op
            | dot call
            ;
   */
  Expr opExpr(boolean mayUseCommas)
  {
     if (!skipDot())
       {
         var oe = new OpExpr();
         skipOps(oe);
         if (oe.size() != 1 || isTermPrefix())
           {
             opTail(oe, mayUseCommas);
             return oe.toExpr();
           }
         else
           {
             return new Partial(oe.op(0)._pos,
                                oe.op(0)._text);
           }
       }
     else
       {
         return Partial.dotCall(tokenSourcePos(), a->pureCall(a));
       }
  }


  /**
   * Parse ops
   *
   * @param oe OpExpr instance the dotCallOrOp()s should be added to
   *
   * @return true iff ops was parsed and dotCallOrOp()s were added to oe
   *
ops         : dotCallOrOp ops
            | dotCallOrOp
            ;
   */
  boolean skipOps(OpExpr oe)
  {
    var oldcount = oe.size();
    while (current() == Token.t_op)
      {
        oe.add(dotCallOrOp());
      }
    return oldcount < oe.size();
  }


  /**
   * Parse opTail
   *
opTail      : term
            | term ops
            | term ops opTail
            ;
   */
  void opTail(OpExpr oe, boolean mayUseCommas)
  {
    oe.add(term(mayUseCommas));
    if (skipOps(oe) && isTermPrefix())
      {
        opTail(oe, mayUseCommas);
      }
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
            | LPAREN operatorExpr (COMMA operatorExpr)+ RPAREN
            ;
klammerLambd: tuple lambda
            ;
   */
  Expr klammer()
  {
    SourcePosition pos = tokenSourcePos();
    var tupleElements = new List<Expr>();
    bracketTermWithNLs(PARENS, "klammer",
                       () -> {
                         do
                           {
                             tupleElements.add(operatorExpr());
                           }
                         while (skipComma());
                         return Void.TYPE;
                       },
                       () -> Void.TYPE);


    if (isLambdaPrefix())                  // a lambda expression
      {
        return lambda(tupleElements);
      }
    else if (tupleElements.size() == 1)    // an expr wrapped in parentheses, not a tuple
      {
        var e = tupleElements.get(0);
        if (e instanceof ParsedOperatorCall oc)
          { // disable chained boolean optimization or partial application:
            oc.putInParentheses();
          }
        return e;
      }
    else                                   // a tuple
      {
        return new ParsedCall(null, new ParsedName(pos, "tuple"), tupleElements);
      }
  }


  /**
   * Parse the right hand side of a lambda expression including the `->`.
   *
lambda      : "->" block
            ;
   */
  Expr lambda(List<Expr> n)
  {
    var pos = tokenPos();
    matchOperator("->", "lambda");
    var startPos = n.isEmpty() ? pos : n.getFirst().pos().bytePos();
    var b = block();
    return new Function(sourceRange(startPos), n, b);
  }


  /**
   * Check if the current position starts a lambda.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a plainLambda.
   */
  boolean isLambdaPrefix()
  {
    return isOperator("->");
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
    return lambda(argNames().map2(n -> new ParsedCall(n)));
  }


  /**
   * Check if the current position starts a plainLambda.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a plainLambda.
   *
   */
  boolean isPlainLambdaPrefix(boolean mayUseCommas)
  {
    var f = fork();
    return f.skipArgNames(mayUseCommas) && f.isLambdaPrefix();
  }


  /**
   * Parse inlineArray
   *
inlineArray : LBRACKET RBRACKET
            | LBRACKET cmaSepElmts RBRACKET
            | LBRACKET semiSepElmts RBRACKET
            ;
cmaSepElmts : operatorExpr addCmaElmts
            ;
addCmaElmts : COMMA cmaSepElmts
            | COMMA
            |
            ;
semiSepElmts: operatorExpr addSemiElmts
            ;
addSemiElmts: SEMI semiSepElmts
            | SEMI
            |
            ;
   */
  Expr inlineArray()
  {
    SourcePosition pos = tokenSourcePos();
    var elements = new List<Expr>();
    bracketTermWithNLs(BRACKETS, "inlineArray",
                       () -> {
                         elements.add(operatorExpr());
                         var sep = current();
                         var s = sep;
                         var p1 = tokenPos();
                         boolean reportedMixed = false;
                         while ((s == Token.t_comma || s == Token.t_semicolon) && skip(s))
                           {
                             if (current() != Token.t_rbracket)
                               {
                                 elements.add(operatorExpr());
                               }
                             s = current();
                             if ((s == Token.t_comma || s == Token.t_semicolon) && s != sep && !reportedMixed)
                               {
                                 AstErrors.arrayInitCommaAndSemiMixed(pos, sourcePos(p1), tokenSourcePos());
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
term        : simpleterm callTail
            ;
simpleterm  : bracketTerm
            | stringTerm
            | NUM_LITERAL
            | match
            | loop
            | ifexpr
            | callOrFeatOrThis
            ;
   */
  Expr term(boolean mayUseCommas)
  {
    int pos = tokenPos();
    var result = switch (current()) // even if this is t_lbrace, we want a term to be indented, so do not use currentAtMinIndent().
      {
      case t_lbrace, t_lparen, t_lbracket ->  bracketTerm();
      case t_numliteral -> {
                             var l = skipNumLiteral();
                             var m = l.mantissaValue();
                             var b = l.mantissaBase();
                             var d = l.mantissaDotAt();
                             var e = l.exponent();
                             var eb = l.exponentBase();
                             var o = l._originalString;
                             yield new NumLiteral(sourceRange(pos), o, b, m, d, e, eb);
                           }
      case t_match     -> match();
      case t_for,
           t_variant,
           t_while,
           t_do        -> loop();
      case t_if        -> ifexpr();
      default          -> {
                            if (isStartedString(current()))
                              {
                                yield stringTerm(null, Optional.empty());
                              }
                            else
                              {
                                var res = callOrFeatOrThis(mayUseCommas);
                                if (res == null)
                                  {
                                    syntaxError(pos, "term (lbrace, lparen, lbracket, fun, string, integer, old, match, or name)", "term");
                                    res = Expr.ERROR_VALUE;
                                  }
                                yield res;
                              }
                          }
      };
    result = callTail(false, result);
    result.setSourceRange(sourceRange(pos));
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
  Expr stringTerm(Expr leftString, Optional<Integer> multiLineIndentation)
  {
    return relaxLineAndSpaceLimit(() -> {
        Expr result = leftString;
        var t = current();
        if (isString(t))
          {
            var ps = string(multiLineIndentation);
            var str = new StrConst(tokenSourcePos().rangeTo(tokenEndPos()), ps.v0());
            result = concatString(tokenSourcePos(), leftString, str);
            next();
            if (isPartialString(t))
              {
                var old = setMinIndent(-1);
                var b = block();
                setMinIndent(old);
                result = stringTerm(concatString(tokenSourcePos(), result, b), ps.v1());
              }
          }
        else
          {
            Errors.expectedStringContinuation(tokenSourcePos(), currentAsString());
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
    return string1 == null ? string2 : new ParsedCall(string1, new ParsedName(pos, "infix +"), new List<>(string2));
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
      case t_lbracket  :
      case t_lbrace    :
      case t_numliteral:
      case t_match     : return true;
      default          :
        return
          isStartedString(current())
          || isNamePrefix()      // Matches call
          || isAnonymousPrefix() // matches anonymous inner feature declaration
          ;
      }
  }


  /**
   * Parse dotCallOrOp
   *
dotCallOrOp : dot call
            | OPERATOR
            ;
   */
  Object dotCallOrOp()
  {
    if (PRECONDITIONS) require
      (current() == Token.t_op);

    Object result; // Function or Operator
    if (skipDot())
      {
        result = Partial.dotCall(tokenSourcePos(), a->pureCall(a));
      }
    else
      {
        result = new Operator(tokenSourceRange(), operator(), ignoredTokenBefore(), ignoredTokenAfter());
        match(Token.t_op, "op");
      }
    return result;
  }


  /**
   * Parse match
   *
match       : "match" exprInLine        cases
            | "match" exprInLine BRACEL cases BRACER
            ;
   */
  Expr match()
  {
    return relaxLineAndSpaceLimit(() -> {
        SourcePosition pos = tokenSourcePos();
        match(Token.t_match, "match");
        Expr e = exprInLine();
        var c = optionalBrackets(BRACES, "cases",() -> cases());
        // missing match cases are checked for when resolving types
        return new Match(pos, e, c);
      });
  }


  /**
   * Parse cases
   *
cases       : '|' casesBars
            | casesNoBars
            ;
casesNoBars : caze semiOrFlatLF casesNoBars
            |
            ;
   */
  List<AbstractCase> cases()
  {
    var in = new Indentation();
    List<AbstractCase> result;
    if (skip('|'))
      {
        result = casesBars(in);
      }
    else
      {
        result = new List<AbstractCase>();
        while (!endOfExprs() && in.ok())
          {
            result.add(caze());
            if (!endOfExprs())
              {
                semiOrFlatLF();
              }
            in.next();
          }
        in.end();
      }
    return result;
  }


  /**
   * Parse casesBars
   *
   * @param in the Indentation instance created at the position of '?' or at
   * current position (for a 'match'-expression).
   *
casesBars   : caze ( '|' casesBars
                   |
                   )
            ;
   */
  List<AbstractCase> casesBars(Indentation in)
  {
    List<AbstractCase> result = new List<>();
    while (!endOfExprs() && in.ok())
      {
        if (!result.isEmpty())
          {
            matchOperator("|", "cases");
          }
        var eab = endAtBar(true);
        result.add(caze());
        endAtBar(eab);
        in.next();
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
caseFldDcl  : IDENT type caseBlock
            ;
caseTypes   : typeList   caseBlock
            ;
caseStar    : STAR       caseBlock
            ;
   */
  Case caze()
  {
    SourcePosition pos = tokenSourcePos();
    if (skip('*'))
      {
        return new Case(pos, caseBlock());
      }
    else if (isCaseFldDcl())
      {
        String n = identifier();
        match(Token.t_ident, "caseFldDcl");
        return new Case(pos, type(), n, caseBlock());
      }
    else
      {
        return new Case(pos, typeList(), caseBlock());
      }
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
    matchOperator("=>", "caseBlock");
    var oldLine = sameLine(-1);
    var bar = current() == Token.t_barLimit;
    sameLine(oldLine);
    return bar ? emptyBlock()
               : block();
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
   * @return true iff a cause was found
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
block       : exprs
            | brblock
            ;
brblock     : BRACEL exprs BRACER
            ;
   */
  Block block()
  {
    var p0 = lastTokenEndPos();
    var p1 = tokenPos();
    var b = optionalBrackets(BRACES, "block", () -> new Block(exprs()));
    var p2 = lastTokenEndPos();
    b.setSourceRange(sourceRange(p0, p1, p2));
    return b;
  }


  Block emptyBlock()
  {
    if (PRECONDITIONS) require
      (lastTokenEndPos() >= 0);

    var b = new Block();
    var p0 = lastTokenEndPos();
    b.setSourceRange(sourceRange(p0, p0+1));
    return b;
  }

  /**
   * As long as this is false and we make progress, we try to parse more
   * expressions within exprs.
   */
  boolean endOfExprs()
  {
    return switch (currentAtMinIndent())
      {
      case
        t_comma,
        t_indentationLimit,
        t_lineLimit,
        t_spaceOrSemiLimit,
        t_colonLimit,
        t_barLimit,
        t_rbrace,
        t_rparen,
        t_rbracket,
        t_until,
        t_then,
        t_else,
        t_eof -> true;
      default -> isContinuedString(currentNoLimit());
      };
  }


  /**
   * Parse exprs
   *
exprs       : expr semiOrFlatLF exprs (semiOrFlatLF | )
            |
            ;
   */
  List<Expr> exprs()
  {
    List<Expr> l = new List<>();
    var in = new Indentation();
    while (!endOfExprs() && in.ok())
      {
        Expr e = expr();
        if (e instanceof FList fl)
          {
            l.addAll(fl._list);
          }
        else
          {
            l.add(e);
          }
        if (!endOfExprs())
          {
            semiOrFlatLF();
          }
        in.next();
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
    boolean mayIndent;
    int oldSameLine;
    int firstPos;           // source position of the first element
    int firstIndent  = -1;  // indentation of the first element,              -1 if indentation has not started (yet)
    int oldIndentPos = -1;  // original minIndent()  to be restored by end(), -1 if indentation has not started (yet)
    int oldEAS       = -1;  // original endAtSpace() to be restored by end(), -1 if indentation has not started (yet)
    int okLineNum    = -1;  // line number of last call to ok(), -1 at beginning
    int okPos        = -1;  // position    of last call to ok(), -1 at beginning
    SemiState oldSemiSt = SemiState.CONTINUE; // the semicolon state, it is used to detect ambiguous semicolons

    Indentation()
    {
      mayIndent      = !isRestrictedToLine();
      firstPos       = tokenPos();
      if (lastTokenPos() >= 0 && lineNum(lastTokenPos()) == line())  // code starts without LF, so set line limit to find end of line in next()
        {
          oldSameLine    = sameLine(line());
          next();
        }
      else
        {
          oldSameLine    = sameLine(-1);
          startIndent();
        }
    }

    /**
     * Check if a previously not indented line will be indented now. This is the
     * case for code like
     *
     *    for i := 0, i+1
     *        x in arr
     *
     * where the first line will be parsed as a 'sameLine', while the second
     * will be turned into an indented line.
     */
    void next()
    {
      if (mayIndent && current() == Token.t_lineLimit && indent(tokenPos()) >= indent(firstPos))
        {
          startIndent();
        }
    }

    /**
     * start indentation, used internally by constructor and next().
     */
    private void startIndent()
    {
      sameLine(-1);
      firstIndent  = indent(firstPos);
      oldEAS       = endAtSpaceOrSemi(Integer.MAX_VALUE);
      oldIndentPos = setMinIndent(tokenPos());
      oldSemiSt    = semiState(SemiState.CONTINUE);
    }


    /**
     * Is indentation still ok, i.e, we are still in the same line or in a new
     * line that is properly indented. Also checks if we have made progress, so
     * repeated calls to ok() will cause errors.
     */
    boolean ok()
    {
      var lastPos = okPos;
      okPos = tokenPos();
      var progress = lastPos < okPos;
      if (CHECKS) check
        (Errors.any() || progress);
      var ok = progress;
      if (ok && firstIndent != -1)
        {
          ok = firstIndent > indent(oldIndentPos); // new indentation must be deeper
          if (ok && okLineNum != lineNum(okPos))
            { // a new line, so check its indentation:
              var curIndent = indent(okPos);
              if (firstIndent != curIndent)
                {
                  Errors.indentationProblemEncountered(tokenSourcePos(), sourcePos(firstPos), parserDetail("exprs"));
                }
              setMinIndent(okPos);
              okLineNum = lineNum(okPos);
            }
        }
      return ok;
    }

    /**
     * Reset indentation to original level.
     */
    void end()
    {
      sameLine(oldSameLine);
      if (firstIndent != -1)
        {
          endAtSpaceOrSemi(oldEAS);
          setMinIndent(oldIndentPos);
          semiState(SemiState.CONTINUE);
        }
    }
  }


  /**
   * Get the indentation, i.e., the byte-column of the given pos, or 0 for pos==-1
   */
  int indent(int pos)
  {
    return pos >= 0 ? codePointIndentation(pos) : 0;
  }


  /**
   * Parse expr
   *
expr        : checkexpr
            | assign
            | destructure
            | feature
            | operatorExpr
            ;
   */
  Expr expr()
  {
    incrSemiState();
    var p0 = lastTokenEndPos();
    var p1 = tokenPos();
    var e =
      isCheckPrefix()       ? checkexpr()   :
      isAssignPrefix()      ? assign()      :
      isDestructurePrefix() ? destructure() :
      isFeaturePrefix()     ? feature()     : operatorExpr();
    var p2 = lastTokenEndPos();
    e.setSourceRange(sourceRange(p0, p1, p2));
    decrSemiState();
    return e;
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
loopBody    : "while" exprInLine      block
            | "while" exprInLine "do" block
            |                    "do" block
            ;
loopEpilog  : "until" exprInLine thenPart loopElseBlock
            |                             "else" block
            ;
   */
  Expr loop()
  {
    return relaxLineAndSpaceLimit(() -> {
        var old = setMinIndent(tokenPos());
        SourcePosition pos = tokenSourcePos();
        List<Feature> indexVars  = new List<>();
        List<Feature> nextValues = new List<>();
        var hasFor   = current() == Token.t_for; if (hasFor) { indexVars(indexVars, nextValues); }
        var hasVar   = skip(true, Token.t_variant); var v   = hasVar              ? exprInLine()    : null;
                                                    var i   = hasFor || v != null ? invariant(true) : null;
        var hasWhile = skip(true, Token.t_while  ); var w   = hasWhile            ? exprInLine()    : null;
        var hasDo    = skip(true, Token.t_do     ); var b   = hasWhile || hasDo   ? block()         : null;
        var hasUntil = skip(true, Token.t_until  ); var u   = hasUntil            ? exprInLine()    : null;
                                                    var ub  = hasUntil            ? thenPart(true)  : null;
                                                    var els1= fork().loopElseBlock();
                                                    var els2= fork().loopElseBlock();
                                                    var els =        loopElseBlock();
        setMinIndent(old);
        if (!hasWhile && !hasDo && !hasUntil && els == null)
          {
            syntaxError(tokenPos(), "loopBody or loopEpilog: 'while', 'do', 'until' or 'else'", "loop");
          }
        return new Loop(pos, indexVars, nextValues, v, i, w, b, u, ub, els, els1, els2).tailRecursiveLoop();
      });
  }


  /**
   * Parse IndexVars
   *
indexVars   : "for" indexVar (semi indexVars)
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
        in.next();
      }
    in.end();
  }


  /**
   * Parse IndexVar
   *
indexVar    : visibility
              modifiers
              name
              ( type implFldInit nextValue
              |      implFldInit nextValue
              | type implFldIter
              |      implFldIter
              )
            ;
implFldIter : "in" exprInLine
            ;
nextValue   : COMMA exprInLine
            |
            ;
   */
  void indexVar(List<Feature> indexVars, List<Feature> nextValues)
  {
    Parser forked = fork();  // tricky: in case there is no nextValue, we
                             // re-parse the initial value expr and use it
                             // as nextValue
    Visi       v1  =        visibility();
    Visi       v2  = forked.visibility();
    int        m1  =        modifiers();
    int        m2  = forked.modifiers();
    var        n1  =        name();
    var        n2  = forked.name();
    boolean hasType = isType();
    ReturnType r1 = hasType ? new FunctionReturnType(       type()) : NoType.INSTANCE;
    ReturnType r2 = hasType ? new FunctionReturnType(forked.type()) : NoType.INSTANCE;
    Impl p1, p2;
    if (       skip(Token.t_in) &&
        forked.skip(Token.t_in)    )
      {
        p1 = new Impl(tokenSourcePos(),        exprInLine(), Impl.Kind.FieldIter);
        p2 = new Impl(tokenSourcePos(), forked.exprInLine(), Impl.Kind.FieldIter);
      }
    else
      {
        p1 =        implFldInit(hasType);
        p2 = forked.implFldInit(hasType);
        // up to here, this and forked parse the same, i.e, v1, m1, .. p1 is the
        // same as v2, m2, .. p2.  Now, we check if there is a comma, which
        // means there is a different value for the second and following
        // iterations:
        if (skipComma())
          {
            p2 = new Impl(tokenSourcePos(), exprInLine(), p2._kind);
          }
      }
    Feature f1 = new Feature(v1,m1,r1,new List<>(n1),new List<>(),new List<>(),Contract.EMPTY_CONTRACT,p1,null);
    Feature f2 = new Feature(v2,m2,r2,new List<>(n2),new List<>(),new List<>(),Contract.EMPTY_CONTRACT,p2,null);
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
    var mi = setMinIndent(-1);
    var result =
      isNonEmptyVisibilityPrefix() ||
      isModifiersPrefix() ||
      isNamePrefix();
    setMinIndent(mi);
    return result;
  }


  /**
   * Parse ifexpr
   *
ifexpr      : "if" exprInLine thenPart elseBlock
            ;
   */
  If ifexpr()
  {
    return relaxLineAndSpaceLimit(() -> {
        SourcePosition pos = tokenSourcePos();

        var oldMinIdent = _elif ? null : setMinIndent(tokenPos());
        _elif = false;

        match(Token.t_if, "ifexpr");

        _nestedIf = _lastIfLine == line();
        _lastIfLine = line();
        Expr e = exprInLine();

        // semi error if in same line
        if (_nestedIf && _lastIfLine == line()) {semiState(SemiState.ERROR);}
        Block b = thenPart(false);

        // reset if new line
        if (_nestedIf && _lastIfLine != line()) {semiState(SemiState.CONTINUE);}
        var els = elseBlock();

        if (oldMinIdent != null) { setMinIndent(oldMinIdent); }

        return new If(pos, e, b,
          // do no use empty blocks as else blocks since the source position
          // of those block might be somewhere unexpected.
          els != null && els._expressions.size() > 0 ? els : null
        );
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
    var p = tokenPos();
    skip(true, Token.t_then);
    var result = block();
    return emptyBlockIfNoBlockPresent && p == tokenPos() ? null : result;
  }


  /**
   * Parse elseBlock
   *
elseBlock   : "else" block
            |
            ;
   */
  Block elseBlock()
  {
    Block result = null;

    if (skip(true, Token.t_else))
      {
        if (current() == Token.t_if)
          {
            _elif = true;
          }
        result = block();
      }

    if (POSTCONDITIONS) ensure
      (result == null          ||
       result instanceof Block    );

    return result;
  }


    /**
   * Parse loopElseBlock
   *
loopElseBlock : "else" block
              |
              ;
   */
  Block loopElseBlock()
  {
    var result = skip(true, Token.t_else) ? block()
                                          : null;

    if (POSTCONDITIONS) ensure
      (result == null          ||
       result instanceof Block    );

    return result;
  }


  /**
   * Check if the current position starts an ifexpr.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an ifexpr.
   */
  boolean isIfPrefix()
  {
    return current() == Token.t_if;
  }


  /**
   * Parse checkexpr
   *
checkexpr   : "check" block
            ;
   */
  Expr checkexpr()
  {
    match(Token.t_check, "checkexpr");
    return new Check(Cond.from(block())).asIfs();
  }


  /**
   * Check if the current position starts a checkexpr.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a checkexpr.
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
  Expr assign()
  {
    if (!ENABLE_SET_KEYWORD)
      {
        AstErrors.illegalUseOfSetKeyword(tokenSourcePos());
      }
    match(Token.t_set, "assign");
    var n = name();
    SourcePosition pos = tokenSourcePos();
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
            ;
destructr   : "(" argNames ")"       ":=" exprInLine
            ;
destructrDcl: formArgs               ":=" exprInLine
            ;
   */
  Expr destructure()
  {
    if (fork().skipFormArgs())
      {
        var a = formArgs(false);
        var pos = tokenSourcePos();
        matchOperator(":=", "destructure");
        return Destructure.create(pos, a, null, exprInLine());
      }
    else
      {
        match(Token.t_lparen, "destructure");
        var names = argNames();
        match(Token.t_rparen, "destructure");
        var pos = tokenSourcePos();
        matchOperator(":=", "destructure");
        return Destructure.create(pos, null, names, exprInLine());
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
   * @return true iff the next token(s) start a destructure.
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
                  | plainLambda
                  | universeCall
                  | call
                  ;
   */
  Expr callOrFeatOrThis(boolean mayUseCommas)
  {
    return
      isAnonymousPrefix()               ? anonymous()      : // starts with value/ref/:/fun/name
      isPlainLambdaPrefix(mayUseCommas) ? plainLambda()    : // x,y,z post result = x*y*z -> x*y*z
      current() == Token.t_universe     ? universeCall()   :
      isNamePrefix()                    ? call(null)         // starts with name
                                        : null;
  }


  /**
   * Parse universe
   *
   * Note that we do not allow `universe` which is not followed by `.`, i.e., it
   * is not possible to get the value of the `universe`.
   *
universe          : "universe"
                  ;
   */
  private Expr universe()
  {
    var pos = tokenSourcePos();
    match(Token.t_universe, "universe");
    return new Universe(pos);
  }


  /**
   * Parse universeCall
   *
   * Note that we do not allow `universe` which is not followed by `.`, i.e., it
   * is not possible to get the value of the `universe`.
   *
universeCall      : universe dot call
                  ;
   */
  Expr universeCall()
  {
    var universe = universe();
    matchOperator(".",      "universeCall");
    return call(universe);
  }


  /**
   * Parse anonymous
   *
anonymous   : "ref"
              inherit
              block
            ;
   */
  Expr anonymous()
  {
    var sl = sameLine(line());
    SourcePosition pos = tokenSourcePos();
    if (CHECKS) check
      (current() == Token.t_ref);
    ReturnType r = returnType();  // only `ref` return type allowed.
    var        i = inherit();
    Block      b = block();
    var f = Feature.anonymous(pos, r, i, Contract.EMPTY_CONTRACT, b);
    var ca = new Call(pos, f);
    sameLine(sl);
    return new Block(new List<>(f, ca));
  }


  /**
   * Check if the current position starts an anonymous.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start an anonymous.
   */
  boolean isAnonymousPrefix()
  {
    return current() == Token.t_ref;
  }


  /**
   * Parse contract
   *
   * @param forkAtFormArgs in case the feature this contract belongs to has a
   * non-empty `formArgsOpt`, this must give a fork of the parser position
   * before the `formArgsOpt`. Otherwise, this can be null.
   *
contract    : require ensure
            ;
require     : "pre"        block    // may start at min indent
            | "pre" "else" block    // may start at min indent
            |
            ;
ensure      : "post"        block   // may start at min indent
            | "post" "then" block   // may start at min indent
            |
            ;
   */
  Contract contract(Parser forkAtFormArgs)
  {
    SourceRange prePos = null;
    SourceRange postPos = null;
    SourceRange hasElse = null;
    SourceRange hasThen = null;
    List<Cond> pre0 = null;
    List<Cond> pre1 = null;
    List<Cond> post = null;
    if (skip(true, Token.t_pre))
      {
        var oldSemiSt = semiState(SemiState.CONTINUE);
        var f = fork();              // NYI: REMOVE!
        f.skip(Token.t_else);        // NYI: REMOVE!
        pre1 = Cond.from(f.block()); // NYI: REMOVE!

        var p = lastTokenPos();
        hasElse = skip(Token.t_else) ? lastTokenSourceRange() : null;
        pre0 = Cond.from(block());
        prePos = sourceRange(p);
        semiState(oldSemiSt);
      }
    if (skip(true, Token.t_post))
      {
        var oldSemiSt = semiState(SemiState.CONTINUE);
        var p = lastTokenPos();
        hasThen = skip(Token.t_then) ? lastTokenSourceRange() : null;
        post = Cond.from(block());
        postPos = sourceRange(p);
        semiState(oldSemiSt);
      }
    return pre0 == null && post == null
      && false // NYI: We cannot use EMPTY_CONTRACT since we might need the last
               // argument, formArgs supplier, in case we inherit a contract:
      ? Contract.EMPTY_CONTRACT
      : new Contract(pre0, pre1, prePos,  hasElse,
                     post,       postPos, hasThen,
                     () -> forkAtFormArgs == null ? new List<AbstractFeature>()
                                                  : forkAtFormArgs.fork().formArgsOpt(true));
  }


  /**
   * Check if the current position starts a contract.  Does not change the
   * position of the parser.
   *
   * @return true iff the next token(s) start a contract.
   */
  boolean isContractPrefix()
  {
    return switch (currentAtMinIndent())
      {
      case t_pre, t_post -> true;
      default            -> false;
      };
  }



  /**
   * Parse invariant
   *
invariant   : "inv" block
            |
            ;
   */
  List<Cond> invariant(boolean atMinIndent)
  {
    List<Cond> result = null;
    if (skip(atMinIndent, Token.t_inv))
      {
        result = Cond.from(block());
      }
    return result;
  }


  /**
   * Parse implRout
   *
implRout    : "is" "abstract"
            | ARROW "abstract"
            | "is" "intrinsic"
            | ARROW "intrinsic"
            | "is" "native"
            | ARROW "native"
            | "is" block
            | ARROW block
            | "of" block
            | fullStop
            ;
   */
  Impl implRout(boolean hasType)
  {
    SourcePosition pos = tokenSourcePos();
    Impl result;
    var has_is    = skip(true, Token.t_is);
    var has_arrow = !has_is && skip(true, "=>");
    if (hasType && has_is)
      {
        AstErrors.constructorWithReturnType(pos);
      }
    if      (has_is || has_arrow   ) { SemiState oldSemiSt = semiState(SemiState.END);
                                       result = skip(Token.t_abstract            ) ? Impl.ABSTRACT            :
                                                skip(Token.t_intrinsic           ) ? Impl.INTRINSIC           :
                                                skip(Token.t_native              ) ? Impl.NATIVE              :
                                                new Impl(pos, block()    , has_is  ? Impl.Kind.Routine        :
                                                                           hasType ? Impl.Kind.Routine
                                                                                   : Impl.Kind.RoutineDef);
                                        semiState(oldSemiSt);}
    else if (skip(true, Token.t_of)) { result = new Impl(pos, block()    , Impl.Kind.Of        ); }
    else if (skipFullStop()        ) { result = new Impl(pos, emptyBlock(),Impl.Kind.Routine   ); }
    else
      {
        syntaxError(tokenPos(), "'is', or '=>' in routine declaration", "implRout");
        result = Impl.ERROR;
      }
    return result;
  }


  /**
   * Parse implFldOrRout
   *
implFldOrRout   : implRout           // may start at min indent
                | implFldInit        // may start at min indent
                |
                ;
   */
  Impl implFldOrRout(boolean hasType)
  {
    if (currentAtMinIndent() == Token.t_lbrace ||
        currentAtMinIndent() == Token.t_is     ||
        currentAtMinIndent() == Token.t_of     ||
        isOperator(true, "=>")                 ||
        isFullStop()                              )
      {
        return implRout(hasType);
      }
    else if (isOperator(true, ":="))
      {
        return implFldInit(hasType);
      }
    else
      {
        syntaxError(tokenPos(), "'is', ':=' or '{'", "impl");
        return Impl.FIELD;
      }
  }


  /**
   * Parse implFldInit
   *
implFldInit : ":=" operatorExpr      // may start at min indent
            ;
   */
  Impl implFldInit(boolean hasType)
  {
    SourcePosition pos = tokenSourcePos();
    if (!skip(true, ":="))
      {
        syntaxError(tokenPos(), "':='", "implFldInit");
      }
    return new Impl(pos,
                    operatorExpr(), // block()?
                    hasType ? Impl.Kind.FieldInit
                            : Impl.Kind.FieldDef);
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
      isOperator(true, ":=") ||
      isOperator(true, "=>") ||
      isFullStop();
  }


  /**
   * Parse type
   *
type        : boundType
            | freeType
            ;
freeType    : name ":" type
            ;
   */
  UnresolvedType type()
  {
    boolean isName = isNamePrefix();
    UnresolvedType result = boundType();
    if (isName &&
        result.mayBeFreeType() &&
        skipColon())
      {
        result = new FreeType(result.pos(), result.freeTypeName(), type());
      }
    return result;
  }


  /**
   * Parse boundType
   *
boundType   : onetype ( PIPE onetype ) *
            ;
   */
  UnresolvedType boundType()
  {
    var result = onetype();
    if (isOperator('|'))
      {
        List<AbstractType> l = new List<>(result);
        while (skip('|'))
          {
            l.add(onetype());
          }
        result = new ParsedType(result.pos(), "choice", l, null);
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
      case t_lparen: return true;
      case t_universe: return true;
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
    return skipType(false, true);
  }


  /**
   * Check if the current position can be parsed as a type and skip it if this is the case.
   *
   * @param isFunctionReturnType true if this is a function return type. In this
   * case, a function type `(a,b)->c` may not be split into a new line after
   * `->`.
   *
   * @param allowTypeInParentheses true iff the type may be surrounded by
   * parentheses, i.e., '(i32, list bool)', '(stack f64)', '()'.
   *
   * @return true iff a type was found and skipped, otherwise no type was found
   * and the parser/lexer is at an undefined position.
   */
  boolean skipType(boolean isFunctionReturnType,
                   boolean allowTypeInParentheses)
  { // we forbid tuples like '(a,b)', '(a)', '()', but we allow lambdas '(a,b)->c' and choice
    // types '(a,b) | (d,e)'

    var hasForbiddenParentheses = !allowTypeInParentheses && !fork().skipOneType(isFunctionReturnType,
                                                                                     false);
    var res = skipOneType(isFunctionReturnType, true);
    while (res && skip('|'))
      {
        res = skipOneType(isFunctionReturnType, true);
        hasForbiddenParentheses = false;
      }
    return res && !hasForbiddenParentheses && (!skipColon() || skipType());
  }


  /**
   * Parse onetype
   *
onetype     : simpletype "->" simpletype    // if used as function return type, no line break allowed after `->`
            | pTypeList  "->" simpletype    // if used as function return type, no line break allowed after `->`
            | pTypeList
            | LPAREN type RPAREN typeTail
            | simpletype
            ;
pTypeList   : LPAREN typeList RPAREN
            ;
pTypeListOpt: pTypeList
            |
            ;
typeOpt     : type
            |
            ;
   */
  UnresolvedType onetype()
  {
    UnresolvedType result;
    SourcePosition pos = tokenSourcePos();
    if (current() == Token.t_lparen)
      {
        var a = bracketTermWithNLs(PARENS, "pTypeList", () -> current() != Token.t_rparen ? typeList() : UnresolvedType.NONE);
        if (skip("->"))
          {
            result = UnresolvedType.funType(pos, type(), a);
          }
        else if (a.size() == 1)
          {
            result = typeTail((UnresolvedType) a.getFirst());
          }
        else
          {
            result = new ParsedType(pos, "tuple", a, null);
          }
      }
    else
      {
        result = simpletype(null);
        if (skip("->"))
          {
            result = UnresolvedType.funType(pos, type(), new List<>(result));
          }
      }
    return result;
  }


  /**
   * Check if the current position starts a onetype and skip it.
   *
   * @param isFunctionReturnType true if this is a function return type. In this
   * case, a function type `(a,b)->c` may not be split into a new line after
   * `->`.
   *
   * @param allowTypeInParentheses true iff the type may be surrounded by
   * parentheses, i.e., '(i32, list bool)', '(stack f64)', '()'.
   *
   * @param allowTypeThatIsNotExpression false to forbid types that cannot be
   * parsed as expressions such as lambdas types with argument types that are
   * not just argNames.
   *
   * @return true iff the next token(s) is a onetype, otherwise no onetype was
   * found and the parser/lexer is at an undefined position.
   */
  boolean skipOneType(boolean isFunctionReturnType,
                      boolean allowTypeInParentheses)
  {
    boolean result;
    var f = fork();
    if (f.skipBracketTermWithNLs(PARENS, () -> f.current() == Token.t_rparen || f.skipTypeList()))
      {
        result = skipBracketTermWithNLs(PARENS, () -> current() == Token.t_rparen || skipTypeList());
        var p = tokenPos();
        var l = line();
        if (skip("->"))
          {
            result = (!isFunctionReturnType || l == line()) && skipType();
          }
        else
          {
            result = result && skipTypeTail();
          }
        result = result && (allowTypeInParentheses || p < tokenPos());
      }
    else
      {
        result = skipSimpletype();
        var l = line();
        if (result && skip("->"))
          {
            result =
              (!isFunctionReturnType || l == line()) &&
              skipType();
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
simpletype  : name typePars typeTail
            ;
   */
  UnresolvedType simpletype(UnresolvedType lhs)
  {
    if (lhs == null && current() == Token.t_universe)
      {
        lhs = universe().asParsedType();
      }
    else
      {
        var n = name();
        var a = typePars();
        lhs = new ParsedType(n._pos, n._name, a, lhs);
      }
    return typeTail(lhs);
  }


  /**
   * Check if the current position is a simpletype and skip it.
   *
   * @return true iff the next token(s) is a simpletype, otherwise no simpletype
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipSimpletype()
  {
    return (skip(Token.t_universe) || skipName() && skipTypePars()) &&
      skipTypeTail();
  }


  /**
   * Parse typeTail
   *
   * @param lhs the left hand side for this type that was already parsed, null
   * if none.
   *
typeTail    : dot simpletype
            | dot "this"
            |
            ;
   */
  UnresolvedType typeTail(UnresolvedType lhs)
  {
    var result = lhs;
    if (skipDot())
      {
        if (skip(Token.t_this))
          {
            var qn = lhs.asQualifier();
            if (qn == null)
              {
                AstErrors.qualifierExpectedForDotThis(sourceRange(lhs.pos()), lhs);
                result = new ParsedType(tokenSourcePos(), Errors.ERROR_STRING, new List<>(), null);
              }
            else
              {
                result = new QualThisType(qn);
              }
          }
        else
          {
            result = simpletype(lhs);
          }
      }
    return result;
  }


  /**
   * Check if the current position is a typeTail and skip it.
   *
   * @return true iff the next token(s) is a typeTail, otherwise no typeTail
   * was found and the parser/lexer is at an undefined position.
   */
  boolean skipTypeTail()
  {
    return
      !skipDot() || skip(Token.t_this) || skipSimpletype();
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
        var pos = tokenPos();
        var l = bracketTermWithNLs(PARENS, "typeInParens",
                                   () -> typeList(),
                                   () -> new List<AbstractType>());
        var eas = endAtSpaceOrSemi(tokenPos());
        if (!ignoredTokenBefore() && isOperator("->"))
          {
            matchOperator("->", "onetype");
            result = UnresolvedType.funType(sourcePos(pos), type(), l);
          }
        else if (l.size() == 1)
          {
            result = l.get(0);
            if (!ignoredTokenBefore())
              {
                result = typeTail((UnresolvedType) result);
              }
          }
        else
          {
            result = new ParsedType(sourcePos(pos), "tuple", l, null);
          }
        endAtSpaceOrSemi(eas);
      }
    else
      {
        var eas = endAtSpaceOrSemi(tokenPos());
        result = type();
        endAtSpaceOrSemi(eas);
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
   * @return true if a typeInParens was skipped
   */
  boolean skipTypeInParens()
  {
    if (isTypePrefix())
      {
        var f = fork();
        f.endAtSpaceOrSemi(tokenPos());
        if (f.skipType())
          {
            var eas = endAtSpaceOrSemi(tokenPos());
            skipType();
            endAtSpaceOrSemi(eas);
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
    return isOperator(true, '.') && !ignoredTokenBefore() && ignoredTokenAfter();
  }


  /**
   * Parse "." followed by white space if it is found
   *
fullStop    : "."        // not following white space but followed by white space
            ;
   *
   * @return true iff a "." followed by white space was found and skipped.
   */
  boolean skipFullStop()
  {
    return isFullStop() && skip('.');
  }

}

/* end of file */
