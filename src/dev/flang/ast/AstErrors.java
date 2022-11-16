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
 * Source of class AstErrors
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import dev.flang.util.ANY;
import static dev.flang.util.Errors.*;
import dev.flang.util.FuzionConstants;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.Terminal;


/**
 * Errors handles compilation error messages for Fuzion
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class AstErrors extends ANY
{

  /*-----------------------------  methods  -----------------------------*/


  /**
   * Handy functions to convert common types to strings in error messages. Will
   * set a color and enclose the string in single quotes.
   */
  public static String s(AbstractFeature f)
  {
    return sqn(f.qualifiedName());
  }
  public static String s(Feature f)
  {
    return s((AbstractFeature) f);
  }
  static String skw(String s) // keyword
  {
    return code(s);
  }
  static String sbn(AbstractFeature f) // feature base name
  {
    return sbn(f.featureName().baseName());
  }
  static String sbn(FeatureName fn) // feature base name plus arg count and id string
  {
    return sbn(fn.baseName()) + fn.argCountAndIdString();
  }
  static String sbn(String s) // feature base name
  {
    return code(s);
  }
  public static String sqn(String s) // feature qualified name
  {
    return code(s);
  }
  static String s(AbstractType t)
  {
    return st(t == null ? "--null--" : t.toString());
  }
  static String s(ReturnType rt)
  {
    return st(rt.toString());
  }
  static String st(String t)
  {
    return type(t);
  }
  static String s(Generic g)
  {
    return st(g.toString());
  }
  static String slg(List<Generic> g)
  {
    var sl = new List<String>();
    for (var e : g)
      {
        sl.add(s(e));
      }
    return sl.toString();
  }
  static String s(FormalGenerics fg)
  {
    return st(fg.toString());
  }
  static String s(Expr e)
  {
    return expr(e.toString());
  }
  static String sle(List<Expr> e)
  {
    return expr(e.toString());
  }
  static String s(Stmnt s)
  {
    return ss(s.toString());
  }
  static String ss(String s) // statement or expression
  {
    return expr(s);
  }
  public static String sfn(List<AbstractFeature> fs)
  {
    int c = 0;
    StringBuilder sb = new StringBuilder();
    for (var f : fs)
      {
        c++;
        sb.append(c == 1         ? "" :
                  c == fs.size() ? " and "
                                 : ", ");
        sb.append(s(f));
      }
    return sb.toString();
  }
  static String s(List<AbstractType> l)
  {
    return type(l.toString());
  }
  static String sn(List<String> names) // names as list "a, b, c"
  {
    return ss(names.toString());
  }
  static String sqn(List<String> names) // names as qualified name "a.b.c"
  {
    return ss(names.toString("", ".", ""));
  }
  static String code(String s) { return "'" + Terminal.PURPLE + s + Terminal.REGULAR_COLOR + "'"; }
  static String type(String s) { return "'" + Terminal.YELLOW + s + Terminal.REGULAR_COLOR + "'"; }
  static String expr(String s) { return "'" + Terminal.CYAN   + s + Terminal.REGULAR_COLOR + "'"; }



  /**
   * Convert a list of features into a String of the feature's qualified names
   * followed by their position and separated by "and".
   */
  static String featureList(List<AbstractFeature> fs)
  {
    StringBuilder sb = new StringBuilder();
    for (var f : fs)
      {
        sb.append(sb.length() > 0 ? "and " : "");
        sb.append("" + s(f) + " defined at " + f.pos().show() + "\n");
      }
    return sb.toString();
  }


  public static void statementNotAllowedOutsideOfFeatureDeclaration(Stmnt s)
  {
    error(s.pos(),
          "Statements other than feature declarations not allowed here",
          "Statements require a surrounding feature declaration.  The statements " +
          "are executed when that surrounding feature is called.  Without a surrounding " +
          "feature, is it not clear when and in which order statements should be executed. " +
          "The only exception to this is the main source file given as an argument directly " +
          "to the 'fz' command.");
  }


  public static void featureOfMustInherit(SourcePosition pos, SourcePosition ofPos)
  {
    error(pos,
          "Feature declaration that is implemented using " + code("of") + " must have inherit clause. ",
          "Feature implementation starting at " + ofPos.show() + "\n" +
          "To solve this, you may add an inherits clause like " + code(": choice ") + " before " + code("of") + "\n");
  }


  public static void featureOfMustContainOnlyDeclarations(Stmnt s, SourcePosition ofPos)
  {
    error(s.pos(),
          "Feature implementation using " + code("of") + " must contain only feature declarations. ",
          "Declaration started at " + ofPos.show() + "\n"
          );
  }


  public static void featureOfMustContainOnlyUnqualifiedNames(Feature f, SourcePosition ofPos)
  {
    error(f.pos(),
          "Feature implementation using " + code("of") + " must contain only unqualified declarations. ",
          "Qualified feature name " + sqn(f._qname) + " is not permitted.\n" +
          "Declaration started at " + ofPos.show() + "\n" +
          "To solve this, you may replace the qualified name " + sqn(f._qname) + " by an unqualified name such as " +
          ss(f._qname.size() > 0 ? f._qname.getLast() : "feature_name") + ".\n");
  }


  public static void featureOfMustNotHaveFormalGenerics(Feature f, SourcePosition ofPos)
  {
    error(f.pos(),
          "Feature implementation using " + code("of") + " must contain only features without type parameters. ",
          "Type parameters " + s(f.generics()) + " is not permitted.\n" +
          "Declaration started at " + ofPos.show() + "\n" +
          "To solve this, you may remove the formal generics " + s(f.generics()) + ".\n");
  }


  public static void featureOfMustContainOnlyConstructors(Feature f, SourcePosition ofPos)
  {
    error(f.pos(),
          "Feature implementation using " + code("of") + " must contain only constructors. ",
          "Feature " + sqn(f._qname) + " is not a constructor.\n" +
          "Declaration started at " + ofPos.show() + "\n" +
          (f.impl()._kind == Impl.Kind.RoutineDef
           ? ("To solve this, you may replace " + code("=>") + " by " + code("is") + " and " +
              "ensure that the code results in a value of type " + st("unit") + " " +
              "in the declaration of " + sqn(f._qname) + ".\n")
           : ("To solve this, you may remove the return type " + s(f._returnType) + " " +
              "from the declaration of " + sqn(f._qname) + ".\n")));
  }


  /**
   * Create an error message for a declaration of a feature using
   * FuzionConstants.RESULT_NAME.
   *
   * @param pos the source code position
   */
  static void declarationOfResultFeature(SourcePosition pos)
  {
    error(pos,
          "Feature declaration may not declare a feature with name " + sbn(FuzionConstants.RESULT_NAME) + "",
          "" + sbn(FuzionConstants.RESULT_NAME) + " is an automatically declared field for a routine's result value.\n"+
          "To solve this, if your intention was to return a result value, use " + ss("set " + FuzionConstants.RESULT_NAME + " := <value>") + ".\n"+
          "Otherwise, you may chose a different name than " + sbn(FuzionConstants.RESULT_NAME) + " for your feature.");
  }


  /**
   * is t an integer type i8..i128 or u8..u128.
   */
  private static boolean integerType(AbstractType t)
  {
    return
      t.compareTo(Types.resolved.t_i8 ) == 0 ||
      t.compareTo(Types.resolved.t_i16) == 0 ||
      t.compareTo(Types.resolved.t_i32) == 0 ||
      t.compareTo(Types.resolved.t_i64) == 0 ||
      t.compareTo(Types.resolved.t_u8 ) == 0 ||
      t.compareTo(Types.resolved.t_u16) == 0 ||
      t.compareTo(Types.resolved.t_u32) == 0 ||
      t.compareTo(Types.resolved.t_u64) == 0;
  }


  /**
   * Create an error message for incompatible types, e.g., in an assignment to a
   * field or in passing an argument to a call.
   *
   * @param pos the source code position
   *
   * @param where location of the incompaible types, e.g, "in assignment".
   *
   * @param detail detail on the use of incompatible types, e.g., "assignent to field abc.fgh\n".
   *
   * @param target string representing the target of the assignment, e.g., "field abc.fgh".
   *
   * @param frmlT the expected formal type
   *
   * @param value the value to be assigned.
   */
  static void incompatibleType(SourcePosition pos,
                               String where,
                               String detail,
                               String target,
                               AbstractType frmlT,
                               Expr value)
  {
    String remedy = null;
    var assignableToSB = new StringBuilder();
    var actlT = value.type();
    var valueThisOrOuter = !actlT.isRef() && (value.isCallToOuterRef() || value instanceof Current);
    if (valueThisOrOuter)
      {
        assignableToSB
          .append("assignable to       : ref ")
          .append(st(actlT.asRef().toString()));
        if (frmlT.isAssignableFromOrContainsError(actlT))
          {
            remedy = "To solve this, you could create a new value instance by calling the constructor of " + s(actlT) + ".\n";
          }
      }
    else
      {
        var assignableTo = new TreeSet<String>();
        frmlT.isAssignableFrom(actlT, assignableTo);
        for (var ts : assignableTo)
          {
            assignableToSB
              .append(assignableToSB.length() == 0
                      ?    "assignable to       : "
                      : ",\n                      ")
              .append(st(ts));
          }
      }
    if (remedy == null && frmlT.asRef().isAssignableFrom(value))
      {
        remedy = "To solve this, you could change the type of " + ss(target) + " to a " + st("ref")+ " type like " + s(frmlT.asRef()) + ".\n";
      }
    else if (integerType(frmlT) && integerType(actlT))
      {
        var fs =
          frmlT.compareTo(Types.resolved.t_i8 ) == 0  ? "i8"   :
          frmlT.compareTo(Types.resolved.t_i16) == 0  ? "i16"  :
          frmlT.compareTo(Types.resolved.t_i32) == 0  ? "i32 " :
          frmlT.compareTo(Types.resolved.t_i64) == 0  ? "i64"  :
          frmlT.compareTo(Types.resolved.t_u8 ) == 0  ? "u8"   :
          frmlT.compareTo(Types.resolved.t_u16) == 0  ? "u16"  :
          frmlT.compareTo(Types.resolved.t_u32) == 0  ? "u32"  :
          frmlT.compareTo(Types.resolved.t_u64) == 0  ? "u64"  : ERROR_STRING;
        remedy = "To solve this, you could convert the value using + " + ss(".as_" + fs) + ".\n";
      }
    else
      {
        remedy = "To solve this, you could change the type of the target " + ss(target) + " to " + s(actlT) + " or convert the type of the assigned value to " + s(frmlT) + ".\n";
      }

    error(pos,
          "Incompatible types " + where,
          detail +
          "expected formal type: " + s(frmlT) + "\n" +
          "actual type found   : " + s(actlT) + (valueThisOrOuter ? " or any subtype" : "") + "\n" +
          assignableToSB + (assignableToSB.length() > 0 ? "\n" : "") +
          "for value assigned  : " + s(value) + "\n" +
          remedy);
  }


  /**
   * Create an error message for incompatible types when assigning a value to a
   * field.
   *
   * @param pos the source code position of the assignment.
   *
   * @param field the field that is being assign to.
   *
   * @param frmlT the expected formal type
   *
   * @param value the value assigned to assignedField.
   */
  static void incompatibleTypeInAssignment(SourcePosition pos,
                                           AbstractFeature field,
                                           AbstractType frmlT,
                                           Expr value)
  {
    incompatibleType(pos,
                     "in assignment",
                     "assignment to field : " + s(field) + "\n",
                     field.qualifiedName(),
                     frmlT,
                     value);
  }


  /**
   * Create an error message for incompatible types when passing an argument to
   * a call.
   *
   * @param calledFeature the feature that is called
   *
   * @param count the number of the actual argument (0 == first argument, 1 ==
   * second argument, etc.)
   *
   * @param frmlT the expected formal type
   *
   * @param value the value to be assigned.
   */
  static void incompatibleArgumentTypeInCall(AbstractFeature calledFeature,
                                             int count,
                                             AbstractType frmlT,
                                             Expr value)
  {
    var frmls = calledFeature.valueArguments().iterator();
    AbstractFeature frml = null;
    int c;
    for (c = 0; c <= count && frmls.hasNext(); c++)
      {
        frml = frmls.next();
      }
    var f = ((c == count+1) && (frml != null)) ? frml : null;
    incompatibleType(value.pos(),
                     "when passing argument in a call",
                     "Actual type for argument #" + (count+1) + (f == null ? "" : " " + sbn(f)) + " does not match expected type.\n" +
                     "In call to          : " + s(calledFeature) + "\n",
                     (f == null ? "argument #" + (count+1) : f.featureName().baseName()),
                     frmlT,
                     value);
  }


  /**
   * Create an error message for incompatible types when assigning an element e
   * during array initilization of the form '[a, b, ..., e, ... ]'.
   *
   * @param pos the source code position of the assignment.
   *
   * @param arrayType the type of the array that is initialized
   *
   * @param frmlT the expected formal type
   *
   * @param value the value assigned to arrayType's elements.
   */
  static void incompatibleTypeInArrayInitialization(SourcePosition pos,
                                                    AbstractType arrayType,
                                                    AbstractType frmlT,
                                                    Expr value)
  {
    incompatibleType(pos,
                     "in array initialization",
                     "array type          : " + s(arrayType) + "\n",
                     "array element",
                     frmlT,
                     value);
  }

  public static void arrayInitCommaAndSemiMixed(SourcePosition pos, SourcePosition p1, SourcePosition p2)
  {
    error(pos,
          "Separator used in array initialization alters between ',' and ';'",
          "First separator defined at " + p1.show() + "\n" +
          "different separator used at " + p2.show());
  }

  static void assignmentTargetNotFound(Assign ass, AbstractFeature outer)
  {
    var solution = solutionDeclareReturnTypeIfResult(ass._name, 0);
    error(ass.pos(),
          "Could not find target field " + sbn(ass._name) + " in assignment",
          "Field not found: " + sbn(ass._name) + "\n" +
          "Within feature: " + s(outer) + "\n" +
          "For assignment: " + s(ass) + "\n" +
          solution);
  }

  static void assignmentToNonField(AbstractAssign ass, AbstractFeature f, AbstractFeature outer)
  {
    error(ass.pos(),
          "Target of assignment is not a field",
          "Target of assignement: " + s(f) + "\n" +
          "Within feature: " + s(outer) + "\n" +
          "For assignment: " + s(ass) + "\n");
  }

  static void assignmentToIndexVar(AbstractAssign ass, AbstractFeature f, AbstractFeature outer)
  {
    error(ass.pos(),
          "Target of assignment must not be a loop index variable",
          "Target of assignement: " + s(f) + "\n" +
          "Within feature: " + s(outer) + "\n" +
          "For assignment: " + s(ass) + "\n" +
          "Was defined as loop index variable at " + f.pos().show());
  }

  static void wrongNumberOfActualArguments(Call call)
  {
    int fsz = call.resolvedFormalArgumentTypes.length;
    boolean ferror = false;
    StringBuilder fstr = new StringBuilder();
    var fargs = call.calledFeature().valueArguments().iterator();
    AbstractFeature farg = null;
    for (var t : call.resolvedFormalArgumentTypes)
      {
        if (CHECKS) check
          (t != null);
        ferror = t == Types.t_ERROR;
        fstr.append(fstr.length
                    () > 0 ? ", " : "");
        farg = fargs.hasNext() ? fargs.next() : farg;
        fstr.append(farg != null ? sbn(farg) + " " : "");
        fstr.append(s(t));
      }
    if (!ferror) // if something went wrong earlier, report no error here
      {
        error(call.pos(),
              "Wrong number of actual arguments in call",
              "Number of actual arguments is " + call._actuals.size() + ", while call expects " + argumentsString(fsz) + ".\n" +
              "Called feature: " + s(call.calledFeature())+ "\n"+
              "Formal arguments: " + fstr + "");
      }
  }

  /**
   * Report that the given actualGenerics does not match the number of formal generics.
   *
   * @param fg the formal generics
   *
   * @param actualGenerics the actual generics
   *
   * @param pos the source code position at which the error should be reported
   *
   * @param detail1 part of the detail message to indicate where this happened,
   * i.e., "call" or "type".
   *
   * @param detail2 optional extra lines of detail message giving further
   * information, like "Calling feature: xyz.f\n" or "Type: Stack bool int\n".
   */
  static void wrongNumberOfGenericArguments(FormalGenerics fg,
                                            List<AbstractType> actualGenerics,
                                            SourcePosition pos,
                                            String detail1,
                                            String detail2)
  {
    error(pos,
          "Wrong number of generic arguments",
          "Wrong number of actual generic arguments in " + detail1 + ":\n" +
          detail2 +
          "expected " + fg.sizeText() + (fg == FormalGenerics.NONE ? "" : " for " + s(fg) + "") + "\n" +
          "found " + (actualGenerics.size() == 0 ? "none" : "" + actualGenerics.size() + ": " + s(actualGenerics) + "" ) + ".\n");
  }

  /**
   * A type that might originally be a generic argument could be a concrete type
   * when we detect an error. So if we have both, the original type and the
   * concrete type, we include both in the error message. If both are the same,
   * only one is shown.
   *
   * @param t the concrete type we found a problem with
   *
   * @param from the declared type that has become t when generics were
   * replaced. Might be equal to t.
   *
   * @return s(t) if t equals from, s(t) + " (from " + s(from + ")" otherwise.
   */
  static String typeWithFrom(AbstractType t, AbstractType from)
  {
    return t.compareTo(from) == 0
      ? s(t)
      : s(t) + " (from " + s(from) + ")";
  }

  public static void argumentTypeMismatchInRedefinition(AbstractFeature originalFeature, AbstractFeature originalArg, AbstractType originalArgType,
                                                        AbstractFeature redefinedFeature, AbstractFeature redefinedArg)
  {
    error(redefinedArg.pos(),
          "Wrong argument type in redefined feature",
          "In " + s(redefinedFeature) + " that redefines " + s(originalFeature) + " " +
          "argument type is " + s(redefinedArg.resultType()) + ", argument type should be " +
          // originalArg.resultType() might be a generic argument that has been replaced by originalArgType:
          typeWithFrom(originalArgType, originalArg.resultType()) + ".  " +
          "Original argument declared at " + originalArg.pos().show());
  }

  public static void resultTypeMismatchInRedefinition(AbstractFeature originalFeature, AbstractType originalType,
                                                      AbstractFeature redefinedFeature)
  {
    error(redefinedFeature.pos(),
          "Wrong result type in redefined feature",
          "In " + s(redefinedFeature) + " that redefines " + s(originalFeature) + " " +
          "result type is " + s(redefinedFeature.resultType()) + ", result type should be " +
          // originalFeature.resultType() might be a generic argument that has been replaced by originalType:
          typeWithFrom(originalType, originalFeature.resultType()) + ".  " +
          "Original feature declared at " + originalFeature.pos().show());
  }

  public static void constructorResultMustBeUnit(Expr res)
  {
    var rt = res.type();
    var srt = rt == null ? "an unknown type" : s(rt);
    error(res.posOfLast(), "Constructor code should result in type " + st("unit") + "",
          "Type returned by this constructor's implementation is " +srt + "\n" +
          "To solve this, you could turn this constructor into a routine by adding a matching result type " +
          "compatible to " + srt + " or by using " + code("=>") + " instead of " + code("is") + " to "+
          "infer the result type from the result expression.\n" +
          "Alternatively, you could explicitly return " + st("unit") + " as the last statement or " +
          "explicitly ignore the result of the last expression by an assignment " + st("_ := <expression>") + ".");
  }

  public static void argumentLengthsMismatch(AbstractFeature originalFeature, int originalNumArgs,
                                             AbstractFeature redefinedFeature, int actualNumArgs)
  {
    error(redefinedFeature.pos(),
          "Wrong number of arguments in redefined feature",
          "In " + s(redefinedFeature) + " that redefines " + s(originalFeature) + " " +
          "argument count is " + actualNumArgs + ", argument count should be " + originalNumArgs + " " +
          "Original feature declared at " + originalFeature.pos().show());
  }

  /* NYI: currently unused, need to check if a "while (23)" produces a useful error message
  static void whileConditionMustBeBool(SourcePosition pos, Type type)
  {
    if (CHECKS) check
      (count() > 0 || type != Types.t_ERROR);

    if (type != Types.t_ERROR)
      {
        error(pos,
              "Loop termination condition following 'while' must be assignable to type 'bool'",
              "Actual type is " + type);
      }
  }
  */

  /* NYI: currently unused, need to check if a "do until (23)" produces a useful error message
  static void untilConditionMustBeBool(SourcePosition pos, Type type)
  {
    if (CHECKS) check
      (count() > 0 || type != Types.t_ERROR);

    if (type != Types.t_ERROR)
      {
        error(pos,
              "Loop termination condition following 'until' must be assignable to type 'bool'",
              "Actual type is " + type);
      }
  }
  */

  static void ifConditionMustBeBool(SourcePosition pos, AbstractType type)
  {
    if (CHECKS) check
      (count() > 0 || type != Types.t_ERROR);

    if (type != Types.t_ERROR)
      {
        error(pos,
              "If condition must be assignable to type " + s(Types.resolved.t_bool) + "",
              "Actual type is " + s(type) + "");
      }
  }

  public static void matchCasesMissing(SourcePosition pos, SourcePosition mpos)
  {
    error(pos,
          "" + skw("match") + " expression requires at least one case",
          "Match statement at " + mpos.show() + "\n" +
          "To solve this, add a case.  If a case exists, check that the indentation is deeper than that of the surrounding " + skw("match") + " expression");
  }

  static void matchSubjectMustNotBeTypeParameter(SourcePosition pos, AbstractType t)
  {
    error(pos,
          "" + skw("match") + " subject type must not be a type parameter",
          "Matched type: " + s(t) + "\n" +
          "which is a type parameter declared at " + t.genericArgument().typeParameter().pos().show());

  }

  static void matchSubjectMustBeChoice(SourcePosition pos, AbstractType t)
  {
    error(pos,
          "" + skw("match") + " subject type must be a choice type",
          "Matched type: " + s(t) + ", which is not a choice type");

  }

  static void repeatedMatch(SourcePosition pos, SourcePosition[] earlierPos, AbstractType typeOrNull, List<AbstractType> choiceGenerics)
  {
    StringBuilder earlierPosString = new StringBuilder();
    TreeSet<SourcePosition> processed = new TreeSet<>();
    for (var ep : earlierPos)
      {
        if (!processed.contains(ep))
          {
            processed.add(ep);
            if (earlierPosString.length() > 0)
              {
                earlierPosString.append(", and at \n");
              }
            earlierPosString.append(ep.show());
          }
      }
    error(pos,
          "" + skw("case") + " clause matches type that had been matched already.",
          caseMatches(typeOrNull) +
          "Originally matched at " + earlierPosString + ".\n" +
          subjectTypes(choiceGenerics));
  }

  static void repeatedMatch(SourcePosition pos, SourcePosition earlierPos, AbstractType t, List<AbstractType> choiceGenerics)
  {
    repeatedMatch(pos, new SourcePosition[] { earlierPos }, t, choiceGenerics);
  }


  static void matchCaseDoesNotMatchAny(SourcePosition pos, AbstractType typeOrNull, List<AbstractType> choiceGenerics)
  {
    error(pos,
          "" + skw("case") + " clause in " + skw("match") + " statement does not match any type of the subject.",
          caseMatches(typeOrNull) +
          subjectTypes(choiceGenerics));
  }

  static void matchCaseMatchesSeveral(SourcePosition pos, AbstractType t, List<AbstractType> choiceGenerics, List<AbstractType> matches)
  {
    error(pos,
          "" + skw("case") + " clause in " + skw("match") + " statement matches several types of the subject",
          caseMatches(t) +
          subjectTypes(choiceGenerics) +
          "matches are " + typeListConjunction(matches));
  }

  static void missingMatches(SourcePosition pos, List<AbstractType> choiceGenerics, List<AbstractType> missingMatches)
  {
    error(pos,
          "" + skw("match") + " statement does not cover all of the subject's types",
          "Missing cases for types: " + typeListConjunction(missingMatches) + "\n" +
          subjectTypes(choiceGenerics));
  }

  /**
   * Create list of the form "'i32', 'string' or 'bool'"
   */
  private static String typeListAlternatives(List<AbstractType> tl)  { return typeList(tl, "or" ); }

  /**
   * Create list of the form "'i32', 'string' and 'bool'"
   */
  private static String typeListConjunction (List<AbstractType> tl)  { return typeList(tl, "and"); }

  /**
   * Create list of the form "'i32', 'string' " + conj + " 'bool'"
   */
  private static String typeList(List<AbstractType> tl, String conj)
  {
    StringBuilder mt = new StringBuilder();
    String comma = "", last = "";
    for (var t : tl)
      {
        if (last != "")
          {
            mt.append(comma).append(last);
            comma = ", ";
          }
        last = s(t);
      }
    mt.append(switch (tl.size()) {
      case 0, 1 -> "";
      case 2    -> " " + conj + " ";
      default   -> ", " + conj + " ";})
      .append(last);
    return mt.toString();
  }

  private static String typeOrAnyType(AbstractType typeOrNull)
  {
    return typeOrNull == null ? "any type" : "type " + s(typeOrNull);

  }
  private static String caseMatches(AbstractType typeOrNull)
  {
    return "Case matches " + typeOrAnyType(typeOrNull) + ".\n";
  }

  private static String subjectTypes(List<AbstractType> choiceGenerics)
  {
    return choiceGenerics.isEmpty()
      ? "Subject type is an empty choice type that cannot match any case.\n"
      : "Subject type is one of " + typeListAlternatives(choiceGenerics) + ".\n";
  }

  public static void internallyReferencedFeatureNotUnique(SourcePosition pos, String qname, Collection<AbstractFeature> set)
  {
    var sb = new StringBuilder();
    for (var f: set)
      {
        if (sb.length() != 0)
          {
            sb.append("\nand ");
          }
        sb.append("" + s(f) + " defined at " + f.pos().show());
      }
    error(pos,
          "Internally referenced feature not unique",
          "While searching for internally used feature " + sqn(qname) + " found " + sb);
  }

  /**
   * This shows an incompatibility between front end and API.
   *
   * @param qname something like "fuzion.java.JavaObject.javaRef"
   *
   * @param outer the outer feature of what cause a problem, e.g. fuzion.java
   *
   * @param name name of the feature that caused a problem, e.g., "JavaObject"
   */
  public static void internallyReferencedFeatureNotFound(SourcePosition pos, String qname, AbstractFeature outer, String name)
  {
    error(pos,
          "Internally referenced feature " + sqn(qname) + " not found",
          "Feature not found: " + sbn(name) + "\n" +
          ((outer == null || outer.isUniverse()) ? "" : "Outer feature: " + s(outer) + "\n"));
  }

  public static void repeatedInheritanceCannotBeResolved(SourcePosition pos, AbstractFeature heir, FeatureName fn, AbstractFeature f1, AbstractFeature f2)
  {
    error(pos,
          "Repeated inheritance of conflicting features",
          "Feature " + s(heir) + " inherits feature " + sbn(fn) + " repeatedly: " +
          "" + s(f1) + " defined at " + f1.pos().show() + "\n" + "and " +
          "" + s(f2) + " defined at " + f2.pos().show() + "\n" +
          "To solve this, you could add a redefintion of " + sbn(f1) + " to " + s(heir) + ".");
  }

  public static void duplicateFeatureDeclaration(SourcePosition pos, AbstractFeature f, AbstractFeature existing)
  {
    // suppress error message if errors were reported already and any feature
    // involved is f_ERROR
    if (count() == 0 || (f                != Types.f_ERROR &&
                         f       .outer() != Types.f_ERROR &&
                         existing         != Types.f_ERROR &&
                         existing.outer() != Types.f_ERROR    ))
      {
        error(pos,
              "Duplicate feature declaration",
              "Feature that was declared repeatedly: " + s(f) + "\n" +
              "originally declared at " + existing.pos().show() + "\n" +
              "To solve this, consider renaming one of these two features or changing its number of arguments");
      }
  }

  public static void qualifiedDeclarationNotAllowedForField(Feature f)
  {
    var q = f._qname;
    var o = new List<>(q.subList(0, f._qname.size()-1).iterator());
    error(f.pos(),
          "Qualified declaration not allowed for field",
          "All fields have to be declared textually within the source of their outer features.\n" +
          "Field declared: " + sqn(q) + "\n" +
          "To solve this, you could move the declaration into the implementation of feature " + sqn(o) +
          ".  Alternatively, you can declare a routine instead.");
  }

  static void cannotRedefine(SourcePosition pos, AbstractFeature f, AbstractFeature existing, String msg, String solution)
  {
    error(pos,
          msg,
          "Feature that redefines existing feature: " + s(f) + "\n" +
          "original feature: " + s(existing) + "\n" +
          "original feature defined in " + existing.pos().fileNameWithPosition()+ "\n" +
          solution);
  }

  public static void cannotRedefineGeneric(SourcePosition pos, AbstractFeature f, AbstractFeature existing)
  {
    cannotRedefine(pos, f, existing, "Cannot redefine feature with generic arguments",
                   "To solve this, ask the Fuzion team to remove this restriction :-)."); // NYI: inheritance and generics
  }

  public static void redefineModifierMissing(SourcePosition pos, AbstractFeature f, AbstractFeature existing)
  {
    cannotRedefine(pos, f, existing, "Redefinition must be declared using modifier " + skw("redef") + "",
                   "To solve this, if you did not intent to redefine an inherited feature, " +
                   "chose a different name for " + sbn(f) + ".  Otherwise, if you do " +
                   "want to redefine an inherited feature, add a " + skw("redef") + " modifier before the " +
                   "declaration of " + s(f) + ".");
  }

  public static void redefineModifierDoesNotRedefine(AbstractFeature f)
  {
    error(f.pos(),
          "Feature declared using modifier " + skw("redef") + " does not redefine another feature",
          "Redefining feature: " + s(f) + "\n" +
          "To solve this, check spelling and argument count against the feature you want to redefine or " +
          "remove " + skw("redef") + " modifier in the declaration of " + s(f) + ".");
  }

  static void ambiguousCallTargets(SourcePosition pos,
                                   FeatureName fn,
                                   List<AbstractFeature> targets)
  {
    error(pos,
          "Ambiguous call targets found for call to " + sbn(fn) + "",
          "Found several possible targets that match this call:\n" +
          featureList(targets));
  }

  /**
   * If name is FuzionConstants.RESULT_NAME and argcount is 0, return text that suggests
   * declaring a return type in the outer feature. Otherwise, return "".
   */
  static String solutionDeclareReturnTypeIfResult(String name, int argCount)
  {
    var solution = "";
    if (name.equals(FuzionConstants.RESULT_NAME) &&
        argCount == 0)
      {
        solution = "To solve this, make sure you declare a return type in the surrounding feature such that " +
          "the " + sbn(FuzionConstants.RESULT_NAME) + " field will be declared automatically.";
      }
    return solution;
  }

  static boolean errorInOuterFeatures(AbstractFeature f)
  {
    while (f != null && f != Types.f_ERROR)
      {
        f = f.outer();
      }
    return f == Types.f_ERROR;
  }

  static void calledFeatureNotFound(Call call,
                                    FeatureName calledName,
                                    AbstractFeature targetFeature)
  {
    if (count() == 0 || !errorInOuterFeatures(targetFeature))
      {
        var solution = solutionDeclareReturnTypeIfResult(calledName.baseName(),
                                                         calledName.argCount());
        error(call.pos(),
              "Could not find called feature",
              "Feature not found: " + sbn(calledName) + "\n" +
              "Target feature: " + s(targetFeature) + "\n" +
              "In call: " + s(call) + "\n" +
              solution);
      }
  }

  static void expectedActualTypeInCall(SourcePosition pos,
                                       AbstractFeature typeParameter)
  {
    var calledFeature = typeParameter.outer();
    error(pos,
          "Expected actual type parameter in call",
          "Call to " + s(calledFeature) + " expects type parameter " + s(typeParameter) + " at this position.\n" +
          "To solve this, provide a type such as " + type("i32") + " or " + type("Object") + " as an argument to this call.\n");
  }

  static void expectedTypeExpression(SourcePosition pos, Expr e)
  {
    error(pos,
          "Expected type in 'xyz.type' expression",
          "Expression of the form 'xyz.type' must use a type for 'xyz'\n" +
          "Expression found: " + s(e) + "\n"+
          "To solve this, provide a type such as " + type("i32") + " or " + type("Object") + " instead of " + s(e) + ".\n");
  }


  public static void ambiguousType(SourcePosition pos,
                                   String t,
                                   List<AbstractFeature> possibilities)
  {
    error(pos,
          "Ambiguous type",
          "For a type used in a declaration, overloading results in an ambiguity that cannot be resolved by the compiler.\n" +
          "Type that is ambiguous: " + st(t) + "\n" +
          "Possible features that match this type: \n" +
          featureList(possibilities) + "\n" +
          "To solve this, rename these features such that each one has a unique name.");
  }

  public static void typeNotFound(SourcePosition pos,
                                  String t,
                                  AbstractFeature outer,
                                  List<AbstractFeature> nontypes_found)
  {
    int n = nontypes_found.size();
    boolean hasAbstract = false;
    boolean hasReturnType = false;
    for (var f : nontypes_found)
      {
        hasAbstract = f.isAbstract();
        hasReturnType =  (!(f instanceof Feature ff) || ff._returnType != NoType.INSTANCE) && !f.isConstructor();
      }
    error(pos,
          "Type not found",
          "Type " + st(t) + " was not found, no corresponding feature nor formal generic argument exists\n" +
          "Type that was not found: " + st(t) + "\n" +
          "in feature: " + s(outer) + "\n" +
          (n == 0 ? "" :
           "However, " + singularOrPlural(n, "feature") + " " +
           (n == 1 ? "has been found that matches the type name but that does not define a type:\n"
                   : "have been found that match the type name but that do not define a type:\n") +
           featureList(nontypes_found) + "\n") +
          "To solve this, " + (!hasAbstract && !hasReturnType
                               ? "check the spelling of the type you have used"
                               : ((hasAbstract ? "implement (make non-abstract) " : "") +
                                  (hasAbstract && hasReturnType ? "and " : "") +
                                  (hasReturnType ? "remove the return type (or replace it by " + skw("ref") +") of " : "") + "one of these features")
                               ) + ".");
  }

  public static void initialValueNotAllowed(Feature f)
  {
    error(f.pos(),
          "Initial value not allowed for feature not embedded in outer feature",
          "Fuzion currently does not know when to execute this initializer, so it is forbidden.\n" +
          "To solve this, move the declaration inside another feature or ask the Fuzion team for help.");
  }

  static void missingResultTypeForField(Feature f)
  {
    if (CHECKS) check
      (count() > 0 || !f.featureName().baseName().equals(ERROR_STRING));

    if (!f.featureName().baseName().equals(ERROR_STRING))
      {
        error(f.pos(),
              "Missing result type in field declaration with initializaton",
              "Field declared: " + s(f) + "");
      }
  }

  static void outerFeatureNotFoundInThis(This t, AbstractFeature feat, String qname, List<String> available)
  {
    error(t.pos(),
          "Could not find outer feature in " + skw(".this") + "-expression",
          "Within feature: " + s(feat) + "\n" +
          "Outer feature that was not found: " + sqn(qname) + "\n" +
          "Outer features available: " + (available.size() == 0 ? "-- none! --" : available));
  }

  static void blockMustEndWithExpression(SourcePosition pos, AbstractType expectedType)
  {
    if (CHECKS) check
      (count() > 0  || expectedType != Types.t_ERROR);

    if (expectedType != Types.t_ERROR)
      {
        error(pos,
              "Block must end with a result expression",
              "This block must produce a value since its result is used by the enclosing statement.\n" +
              "Expected type of value: " + s(expectedType) + "");
      }
  }

  public static void constraintMustNotBeGenericArgument(AbstractFeature tp)
  {
    error(tp.pos(),
          "Constraint for type parameter must not be a type parameter",
          "Affected type parameter: " + s(tp) + "\n" +
          "_constraint: " + s(tp.resultType()) + "\n" +
          "To solve this, change the type provided, e.g. to the unconstraint " + st("type") + ".\n");
  }

  static void loopElseBlockRequiresWhileOrIterator(SourcePosition pos, Expr elseBlock)
  {
    error(pos, "Loop without while condition cannot have an else block",
          "Since the else block is executed if the while condition is false " +
          "or an iteration ended, it does not make sense " +
          "to have an else condition unless there is a while clause or an iterator " +
          "index variable.\n" +
          "The else block of this loop is declared at " + elseBlock.pos().show());
  }

  static void formalGenericAsOuterType(SourcePosition pos, Type t)
  {
    error(pos,
          "Formal generic cannot be used as outer type",
          "In a type >>a.b<<, the outer type >>a<< must not be a formal generic argument.\n" +
          "Type used: " + s(t) + "\n" +
          "Formal generic used " + s(t.outer()) + "\n" +
          "Formal generic declared in " + t.outer().genericArgument().typeParameter().pos().show() + "\n");
  }

  static void formalGenericWithGenericArgs(SourcePosition pos, Type t, Generic generic)
  {
    error(pos,
          "Formal generic cannot have generic arguments",
          "In a type with generic arguments >>A B<<, the base type >>A<< must not be a formal generic argument.\n" +
          "Type used: " + s(t) + "\n" +
          "Formal generic used " + s(generic) + "\n" +
          "Formal generic declared in " + generic.typeParameter().pos().show() + "\n");
  }

  static void refToChoice(SourcePosition pos)
  {
    error(pos,
          "ref to a choice type is not allowed",
          "a choice is always a value type");
  }

  static void genericsMustBeDisjoint(SourcePosition pos, AbstractType t1, AbstractType t2)
  {
    error(pos,
          "Generics arguments to choice type must be disjoint types",
          "The following types have overlapping values:\n" +
          "" + s(t1) + "" + /* " at " + t1.pos().show() + */ "\n" +  // NYI: use pos before Types were interned!
          "" + s(t2) + "" + /* " at " + t2.pos().show() + */ "\n");
  }

  static void illegalUseOfOpenFormalGeneric(SourcePosition pos, Generic generic)
  {
    error(pos,
          "Illegal use of open formal generic type",
          "Open formal generic type is permitted only as the type of the last argument in a formal arguments list of an abstract feature.\n" +
          "Open formal argument: " + s(generic) + "");
  }

  static void integerConstantOutOfLegalRange(SourcePosition pos, String constant, AbstractType t, String from, String to)
  {
    error(pos,
          "Integer constant value outside of allowed range for target type",
          "Type propagation results in a type that is too small for the value represented by the given constant.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n" +
          "Acceptable range of values: " + ss(from) + " .. " + ss(to));
  }

  static void nonWholeNumberUsedAsIntegerConstant(SourcePosition pos, String constant, AbstractType t)
  {
    error(pos,
          "Numeric literal used for integer type is not a whole number",
          "Type propagation results in an integer type that cannot whole a value that is not integer.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n");
  }

  static void floatConstantTooLarge(SourcePosition pos, String constant, AbstractType t, String max, String maxH)
  {
    error(pos,
          "Float constant value outside of allowed range for target type",
          "Type propagation results in a type that is too small for the value represented by the given constant.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n" +
          "Max allowed value: " + ss("-"+max) + " .. " + ss(max) + " or " + ss("-" + maxH) + " .. " + ss(maxH));
  }

  static void floatConstantTooSmall(SourcePosition pos, String constant, AbstractType t, String min, String minH)
  {
    error(pos,
          "Float constant value too small, would underflow to 0",
          "Type propagation results in a type whose precision does not allow to represented the given constant.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n" +
          "Min representable value > 0: " + ss(min) + " or " + ss(minH));
  }

  static void wrongNumberOfArgumentsInLambda(SourcePosition pos, List<String> names, AbstractType funType)
  {
    int req = funType.generics().size() - 1;
    int delta = names.size() - req;
    var ns = sn(names);
    error(pos,
          "Wrong number of arguments in lambda expression",
          "Lambda expression has " + singularOrPlural(names.size(), "argument") + " while the target type expects " +
          singularOrPlural(funType.generics().size()-1, "argument") + ".\n" +
          "Arguments of lambda expression: " + ns + "\n" +
          "Expected function type: " + funType + "\n" +
          "To solve this, " +
          (req == 0 ? "replace the list " + ns + " by " + ss("()") + "."
                    : (delta < 0 ? "add "    + singularOrPlural(-delta, "argument") + " to the list "   + ns
                                 : "remove " + singularOrPlural( delta, "argument") + " from the list " + ns) +
                      " before the " + ss("->") + " of the lambda expression.")
          );
  }

  static void expectedFunctionTypeForLambda(SourcePosition pos, AbstractType t)
  {
    error(pos,
          "Target type of a lambda expression must be " + s(Types.resolved.f_function) + ".",
          "A lambda expression can only be used if assigned to a field or argument of type "+ s(Types.resolved.f_function) + "\n" +
          "with argument count of the lambda expression equal to the number of generic parameters of the type.\n" +
          "Target type: " + s(t) + "\n" +
          "To solve this, assign the lambda expression to a field of function type, e.g., " + ss("f (i32, i32) -> bool := x, y -> x > y") + ".");
  }

  static void noTypeInferenceFromLambda(SourcePosition pos)
  {
    error(pos,
          "No type information can be inferred from a lambda expression",
          "A lambda expression can only be used if assigned to a field or argument of type "+ s(Types.resolved.f_function) + "\n" +
          "with argument count of the lambda expression equal to the number of generic parameters of the type.  The type of the\n" +
          "assigned field must be given explicitly.\n" +
          "To solve this, declare an explicit type for the target field, e.g., " + ss("f (i32, i32) -> bool := x, y -> x > y") + ".");
  }

  static void repeatedInheritanceOfChoice(SourcePosition pos, SourcePosition lastP)
  {
    error(pos,
          "Repeated inheritance of choice is not permitted",
          "A choice feature must inherit directly from choice exactly once.\n" +
          "Previous inheritance from choice at " + lastP);
  }

  static void cannotInheritFromChoice(SourcePosition pos)
  {
    error(pos,
          "Cannot inherit from choice feature",
          "Choice must be leaf.");
  }

  static void parentMustBeConstructor(SourcePosition pos, Feature heir, AbstractFeature parent)
  {
    error(pos,
          "Cannot inherit from non-constructor feature",
          "The parents of feature "+s(heir)+" include "+s(parent)+", which is not a constructor but a "+
          "'" + parent.kind() + "'.\n"+
          "Parent declared at " + parent.pos().show() +
          "To solve this, you might remove the inheritance cal to " + s(parent) + " or you could change " + s(parent) +
          " into a constructor, i.e., a feature without explicit result type declared using "+code("is")+".");
  }

  static void recursiveInheritance(SourcePosition pos, Feature f, String cycle)
  {
    error(pos,
          "Recursive inheritance in feature " + s(f),
          cycle);
  }

  static void choiceMustNotAccessSurroundingScope(SourcePosition pos, String accesses)
  {
    error(pos,
          "Choice type must not access fields of surrounding scope.",
          "A closure cannot be built for a choice type. Forbidden accesses occur at \n" +
          accesses);
  }

  static void choiceMustNotBeRef(SourcePosition pos)
  {
    error(pos,
          "choice feature must not be ref",
          "A choice feature must be a value type since it is not constructed ");
  }

  static void choiceMustNotContainFields(SourcePosition pos, AbstractFeature f)
  {
    error(pos,
          "Choice must not contain any fields",
          "Field " + s(f) + " is not permitted in choice.\n" +
          "Field declared at "+ f.pos().show());
  }

  static void choiceMustNotBeField(SourcePosition pos)
  {
    error(pos,
          "Choice feature must not be a field",
          "A choice feature must be a normal feature with empty code section");
  }

  static void choiceMustNotBeRoutine(SourcePosition pos)
  {
    error(pos,
          "Choice feature must not be defined as a routine with a result",
          "A choice feature must be a normal feature with empty code section");
  }

  static void choiceMustNotContainCode(SourcePosition pos)
  {
    error(pos,
          "Choice feature must not contain any code",
          "A choice feature must be a normal feature with empty code section");
  }

  static void choiceMustNotBeAbstract(SourcePosition pos)
  {
    error(pos,
          "Choice feature must not be abstract",
          "A choice feature must be a normal feature with empty code section");
  }

  static void choiceMustNotBeIntrinsic(SourcePosition pos)
  {
    error(pos,
          "Choice feature must not be intrinsic",
          "A choice feature must be a normal feature with empty code section");
  }

  static void choiceMustNotReferToOwnValueType(SourcePosition pos, AbstractType t)
  {
    error(pos,
          "Choice cannot refer to its own value type as one of the choice alternatives",
          "Embedding a choice type in itself would result in an infinitely large type.\n" +
          "Faulty generic argument: " + s(t) + " at " + t.pos().show());
  }

  static void choiceMustNotReferToOuterValueType(SourcePosition pos, AbstractType t)
  {
    error(pos,
          "Choice cannot refer to an outer value type as one of the choice alternatives",
          "Embedding an outer value in a choice type would result in infinitely large type.\n" +
          "Faulty generic argument: " + s(t) + " at " + t.pos().show());
  }

  static void fieldDefMustNotHaveType(SourcePosition pos, AbstractFeature f, ReturnType rt, Expr initialValue)
  {
    error(pos,
          "Field definition using " + ss(":=")+ " must not specify an explicit type",
          "Definition of field: " + s(f) + "\n" +
          "Explicit type given: " + s(rt) + "\n" +
          "Defining expression: " + s(initialValue));
  }

  static void routineDefMustNotHaveType(SourcePosition pos, AbstractFeature f, ReturnType rt, Expr code)
  {
    error(pos,
          "Function definition using " + ss("=>") + " must not specify an explicit type",
          "Definition of function: " + s(f) + "\n" +
          "Explicit type given: " + s(rt) + "\n" +
          "Defining expression: " + s(code));
  }

  static void forwardTypeInference(SourcePosition pos, AbstractFeature f, SourcePosition at)
  {
    // NYI: It would be nice to output the whole cycle here as part of the detail message
    error(pos,
          "Illegal forward or cyclic type inference",
          "The definition of a field using " + ss(":=") + ", or of a feature or function\n" +
          "using " + ss("=>") + " must not create cyclic type dependencies.\n"+
          "Referenced feature: " + s(f) + " at " + at.show());
  }

  static void illegalSelect(SourcePosition pos, String select, NumberFormatException e)
  {
    error(pos,
          "Illegal select clause",
          "Failed to parse integer " + ss(select) + ": " + e);
  }

  static void cannotAccessValueOfOpenGeneric(SourcePosition pos, AbstractFeature f, AbstractType t)
  {
    error(pos,
          "Cannot access value of open generic type",
          "When calling " + s(f) + " result type " + s(t) + " is open generic, " +
          "which cannot be accessed directly.  You might try to access one specific generic parameter " +
          "by adding '.0', '.1', etc.");
  }

  static void useOfSelectorRequiresCallWithOpenGeneric(SourcePosition pos, AbstractFeature f, String name, int select, AbstractType t)
  {
    error(pos,
          "Use of selector requires call to feature with open generic type",
          "In call to " + s(f) + "\n" +
          "Selected variant " + ss(name + "." + select) + "\n" +
          "Type of called feature: " + s(t));
  }

  static void selectorRange(SourcePosition pos, int sz, AbstractFeature f, String name, int select, List<AbstractType> types)
  {
    error(pos,
          "" +
          (sz > 1  ? "Selector must be in the range of 0.." + (sz - 1) + " for " + sz +" actual generic arguments" :
           sz == 1 ? "Selector must be 0 for one actual generic argument"
           : "Selector not permitted since no actual genenric arguments are")+
          " given for the open generic type",
          "In call to " + s(f) + "\n" +
          "Selected variant " + ss(name + "." + select) + "\n" +
          "Number of actual generic arguments: " + types.size() + "\n" +
          "Actual generic arguments: " + (sz == 0 ? "none" : s(types)) + "\n");
  }

  static void failedToInferOpenGenericArg(SourcePosition pos, int count, Expr actual)
  {
    error(pos,
          "Failed to infer open generic argument type from actual argument.",
          "Type of " + ordinal(count) + " actual argument could not be inferred at " + actual.pos().show());
  }

  static void incompatibleTypesDuringTypeInference(SourcePosition pos, Generic g, String foundAt)
  {
    error(pos,
          "Incompatible types found during type inference for generic arguments",
          "Types inferred for " + ordinal(g.index()+1) + " generic argument " + s(g) + ":\n" +
          foundAt);
  }

  static void failedToInferActualGeneric(SourcePosition pos, AbstractFeature cf, List<Generic> missing)
  {
    error(pos,
          "Failed to infer actual generic parameters",
          "In call to " + s(cf) + ", no actual generic parameters are given and inference of the generic parameters failed.\n" +
          "Expected generic parameters: " + s(cf.generics()) + "\n"+
          "Type inference failed for " + singularOrPlural(missing.size(), "generic argument") + " " + slg(missing) + "\n");
  }

  static void functionMustNotProvideActuals(SourcePosition pos, Call c, List<Expr> actuals)
  {
    error(pos,
          "Function declaration of the form " + ss("fun a.b") + " must not provide any actual arguments to " + ss("b") + ", " + ss("b") + " is not called here",
          "Call that followed " + ss("fun") + ": " + s(c) + "\n" +
          "Actual arguments: " + sle(actuals) + "\n");
  }

  static void functionMustNotProvideParentheses(SourcePosition pos, Call c)
  {
    error(pos,
          "Function declaration of the form " + ss("fun a.b") + " must not provide any parentheses " + ss("b()") + ", " + ss("b") +" is not called here",
          "Call that followed " + ss("fun") + ": " + s(c) + "\n");
  }

  static void cannotCallChoice(SourcePosition pos, AbstractFeature cf)
  {
    error(pos,
          "Cannot call choice feature",
          "A choice feature is only used as a type, values are created by assignments only.\n"+
          "Choice feature that is called: " + s(cf) + "\n" +
          "Declared at " + cf.pos().show());
  }

  static void incompatibleActualGeneric(SourcePosition pos, Generic f, AbstractType g)
  {
    error(pos,
          "Incompatible type parameter",
          "formal type parameter " + s(f) + " with constraint " + s(f.constraint()) + "\n"+
          "actual type parameter " + s(g) + "\n");
  }

  static void destructuringForGeneric(SourcePosition pos, AbstractType t, List<String> names)
  {
    error(pos,
          "Destructuring not possible for value whose type is a generic argument.",
          "Type of expression is " + s(t) + "\n" +
          "Cannot destructure value of generic argument type into (" + sn(names) + ")");
  }

  static void destructuringRepeatedEntry(SourcePosition pos, String n, int count)
  {
    error(pos,
          "Repeated entry in destructuring",
          "Variable " + ss(n) + " appears " + count + " times.");
  }


  static void destructuringMisMatch(SourcePosition pos, List<String> fieldNames, List<String> names)
  {
    int fn = fieldNames.size();
    int nn = names.size();
    error(pos,
          "Destructuring mismatch between number of visible fields and number of target variables.",
          "Found " + ((fn == 0) ? "no visible argument fields" :
                      (fn == 1) ? "one visible argument field" :
                      "" + fn + " visible argument fields"     ) + " " + sn(fieldNames) + "\n" +
          (nn == 0 ? "while there are no destructuring variables" :
           nn == 1 ? "while there is one destructuring variable: " + sn(names)
           : "while there are " + nn + " destructuring variables: " + sn(names)) + ".\n"
          );
  }

  static void illegalResultType(AbstractFeature f, ReturnType rt)
  {
    error(f.pos(),
          "Illegal result type " + s(rt) + " in field declaration with initialization using " + ss(":="),
          "Field declared: " + s(f));
  }

  static void illegalResultTypeDef(AbstractFeature f, ReturnType rt)
  {
    error(f.pos(),
          "Illegal result type " + s(rt) + " in field definition using " + ss(":="),
          "For field definition using " + ss(":=") + ", the type is determined automatically, " +
          "it must not be given explicitly.\n" +
          "Field declared: " + s(f));
  }

  static void illegalResultTypeNoInit(AbstractFeature f, ReturnType rt)
  {
    error(f.pos(),
          "Illegal result type " + s(rt) + " in field declaration using " + ss(":= ?"),
          "Field declared: " + s(f));
  }

  static void illegalResultTypeRoutineDef(AbstractFeature f, ReturnType rt)
  {
    error(f.pos(),
          "Illegal result type " + s(rt) + " in feature definition using " + ss("=>"),
          "For function definition using " + ss("=>") + ", the type is determined automatically, " +
          "it must not be given explicitly.\n" +
          "Feature declared: " + s(f));
  }

  static void failedToInferType(Expr e)
  {
    error(e.pos(),
          "Failed to infer type of expression.",
          "Expression with unknown type: " + s(e));
  }

  static void incompatibleResultsOnBranches(SourcePosition pos, String msg, List<AbstractType> types, Map<AbstractType, List<SourcePosition>> positions)
  {
    StringBuilder typesMsg = new StringBuilder();
    for (var t : types)
      {
        var l = positions.get(t);
        typesMsg.append(( l.size() == 1 ? "block returns" : "blocks return") + " value of type " + s(t) + " at ");
        boolean first = true;
        for (SourcePosition p : l)
          {
            typesMsg.append((first ? "" : "and at ") + p.show() + "\n");
            first = false;
          }
      }
    error(pos,
          msg,
          "Incompatible result types in different branches:\n" +
          typesMsg);
  }

  static void lossOfPrecision(SourcePosition pos, String _originalString, int _base, AbstractType _type)
  {
    error(pos,
      "Loss of precision for: " + _originalString,
      "Expected number given in base " + _base + " to fit into " + _type + " without loss of precision.");
  }

  public static void ambiguousAssignmentToChoice(AbstractType frmlT, Expr value)
  {
    error(value.pos(),
      "Ambiguous assignment to " + s(frmlT) + " from " + s(value.type()), s(value.type()) + " is assignable to " + frmlT.choiceGenerics().stream()
          .filter(cg -> cg.isAssignableFrom(value.type()))
          .map(cg -> s(cg))
          .collect(Collectors.joining(", "))
      );
  }
}

/* end of file */
