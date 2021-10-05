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
 * Source of class FeErrors
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import dev.flang.util.ANY;
import static dev.flang.util.Errors.*;
import dev.flang.util.List;
import dev.flang.util.SourcePosition;
import dev.flang.util.Terminal;


/**
 * Errors handles compilation error messages for Fuzion
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FeErrors extends ANY
{

  /*-----------------------------  methods  -----------------------------*/


  /**
   * Handy functions to convert common types to strings in error messages. Will
   * set a color and enclose the string in single quotes.
   */
  static String s(Feature f)
  {
    return sqn(f.qualifiedName());
  }
  static String skw(String s) // keyword
  {
    return code(s);
  }
  static String sbn(Feature f) // feature base name
  {
    return sbn(f._featureName.baseName());
  }
  static String sbn(FeatureName fn) // feature base name plus arg count and id string
  {
    return sbn(fn.baseName()) + fn.argCountAndIdString();
  }
  static String sbn(String s) // feature base name
  {
    return code(s);
  }
  static String sqn(String s) // feature qualified name
  {
    return code(s);
  }
  static String s(Type t)
  {
    return st(t.toString());
  }
  static String st(String t)
  {
    return type(t);
  }
  static String s(Generic g)
  {
    return st(g.toString());
  }
  static String s(Expr e)
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
  static String s(Feature f, FormalGenerics fg)
  {
    return code(f.qualifiedName() + fg);
  }
  static String s(List<Type> l)
  {
    return type(l.toString());
  }
  static String sn(List<String> names)
  {
    return ss(names.toString());
  }
  static String code(String s) { return "'" + Terminal.PURPLE + s + Terminal.REGULAR_COLOR + "'"; }
  static String type(String s) { return "'" + Terminal.YELLOW + s + Terminal.REGULAR_COLOR + "'"; }
  static String expr(String s) { return "'" + Terminal.CYAN   + s + Terminal.REGULAR_COLOR + "'"; }



  /**
   * Convert a list of features into a String of the feature's qualified names
   * followed by their position and separated by "and".
   */
  static String featureList(List<Feature> fs)
  {
    StringBuilder sb = new StringBuilder();
    for (var f : fs)
      {
        sb.append(sb.length() > 0 ? "and " : "");
        sb.append("" + s(f) + " defined at " + f.pos.show() + "\n");
      }
    return sb.toString();
  }


  /**
   * Create an error message for a declaration of a feature using
   * Feature.RESULT_NAME.
   *
   * @param pos the source code position
   */
  static void declarationOfResultFeature(SourcePosition pos)
  {
    error(pos,
          "Feature declaration may not declare a feature with name " + sbn(Feature.RESULT_NAME) + "",
          "" + sbn(Feature.RESULT_NAME) + " is an automatically declared field for a routine's result value.\n"+
          "To solve this, if your intention was to return a result value, use " + ss("set " + Feature.RESULT_NAME + " := <value>") + ".\n"+
          "Otherwise, you may chose a different name than " + sbn(Feature.RESULT_NAME) + " for your feature.");
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
   * @param frmlT the expected formal type
   *
   * @param actlT the actual type
   *
   * @param value the value whose type is actlT.
   */
  static void incompatibleType(SourcePosition pos,
                               String where,
                               String detail,
                               Type frmlT,
                               Type actlT,
                               Expr value)
  {
    var assignableTo = new TreeSet<String>();
    frmlT.isAssignableFrom(actlT, assignableTo);
    var assignableToSB = new StringBuilder();
    for (var ts : assignableTo)
      {
        assignableToSB
          .append(assignableToSB.length() == 0
                  ?    "assignable to       : "
                  : ",\n                      ")
          .append(st(ts));
      }
    error(pos,
          "Incompatible types " + where,
          detail +
          "expected formal type: " + s(frmlT) + "\n" +
          "actual type found   : " + s(actlT) + "\n" +
          assignableToSB + (assignableToSB.length() > 0 ? "\n" : "") +
          "for value assigned  : " + s(value) + "\n");
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
   * @param actlT the actual type
   *
   * @param value the value assigned to assignedField.
   */
  static void incompatibleTypeInAssignment(SourcePosition pos,
                                           Feature field,
                                           Type frmlT,
                                           Type actlT,
                                           Expr value)
  {
    incompatibleType(pos,
                     "in assignment",
                     "assignment to field : " + s(field) + "\n",
                     frmlT,
                     actlT,
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
   * @param actlT the actual type
   *
   * @param value the value whose type is actlT.
   */
  static void incompatibleArgumentTypeInCall(Feature calledFeature,
                                             int count,
                                             Type frmlT,
                                             Type actlT,
                                             Expr value)
  {
    Iterator<Feature> frmls = calledFeature.arguments.iterator();
    Feature frml = null;
    int c;
    for (c = 0; c <= count && frmls.hasNext(); c++)
      {
        frml = frmls.next();
      }
    var f = ((c == count+1) && (frml != null)) ? frml : null;
    incompatibleType(value.pos,
                     "when passing argument in a call",
                     "Actual type for argument #" + (count+1) + (f == null ? "" : " " + sbn(f)) + " does not match expected type.\n" +
                     "In call to          : " + s(calledFeature) + "\n",
                     frmlT,
                     actlT,
                     value);
  }


  /**
   * Create an error message for incompatible types when assigning an element e
   * during array initilization of the form '[a, b, ..., e, ... ]'.
   *
   * @param pos the source code position of the assignment.
   *
   * @param arrayTpye the type of the array that is initialized
   *
   * @param frmlT the expected formal type
   *
   * @param actlT the actual type
   *
   * @param value the value assigned to assignedField.
   */
  static void incompatibleTypeInArrayInitialization(SourcePosition pos,
                                                    Type arrayType,
                                                    Type frmlT,
                                                    Type actlT,
                                                    Expr value)
  {
    incompatibleType(pos,
                     "in array initialization",
                     "array type          : " + s(arrayType) + "\n",
                     frmlT,
                     actlT,
                     value);
  }

  public static void arrayInitCommaAndSemiMixed(SourcePosition pos, SourcePosition p1, SourcePosition p2)
  {
    error(pos,
          "Separator used in array initialization alters between ',' and ';'",
          "First separator defined at " + p1.show() + "\n" +
          "different separator used at " + p2.show());
  }

  static void assignmentTargetNotFound(Assign ass, Feature outer)
  {
    var solution = solutionDeclareReturnTypeIfResult(ass._name, 0);
    error(ass.pos(),
          "Could not find target field " + sbn(ass._name) + " in assignment",
          "Field not found: " + sbn(ass._name) + "\n" +
          "Within feature: " + s(outer) + "\n" +
          "For assignment: " + s(ass) + "\n" +
          solution);
  }

  static void assignmentToNonField(Assign ass, Feature f, Feature outer)
  {
    error(ass.pos(),
          "Target of assignment is not a field",
          "Target of assignement: " + s(f) + "\n" +
          "Within feature: " + s(outer) + "\n" +
          "For assignment: " + s(ass) + "\n");
  }

  static void assignmentToIndexVar(Assign ass, Feature f, Feature outer)
  {
    error(ass.pos(),
          "Target of assignment must not be a loop index variable",
          "Target of assignement: " + s(f) + "\n" +
          "Within feature: " + s(outer) + "\n" +
          "For assignment: " + s(ass) + "\n" +
          "Was defined as loop index variable at " + f.pos.show());
  }

  static void wrongNumberOfActualArguments(Call call)
  {
    int fsz = call.resolvedFormalArgumentTypes.length;
    boolean ferror = false;
    StringBuilder fstr = new StringBuilder();
    Iterator<Feature> fargs = call.calledFeature().arguments.iterator();
    Feature farg = null;
    for (Type t : call.resolvedFormalArgumentTypes)
      {
        ferror = t == Types.t_ERROR;
        fstr.append(fstr.length
                    () > 0 ? ", " : "");
        farg = fargs.hasNext() ? fargs.next() : farg;
        fstr.append(farg != null ? sbn(farg) + " " : "");
        fstr.append(s(t));
      }
    if (!ferror) // if something went wrong earlier, report no error here
      {
        error(call.pos,
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
   * information, like "Calling feature: xyz.f\n" or "Type: Stack<bool,int>\n".
   */
  static void wrongNumberOfGenericArguments(FormalGenerics fg,
                                            List<Type> actualGenerics,
                                            SourcePosition pos,
                                            String detail1,
                                            String detail2)
  {
    error(pos,
          "Wrong number of generic arguments",
          "Wrong number of actual generic arguments in " + detail1 + ":\n" +
          detail2 +
          "expected " + fg.sizeText() + (fg == FormalGenerics.NONE ? "" : " for " + s(fg.feature(), fg) + "") + "\n" +
          "found " + (actualGenerics.size() == 0 ? "none" : "" + actualGenerics.size() + ": " + s(actualGenerics) + "" ) + ".\n");
  }

  static void argumentTypeMismatchInRedefinition(Feature originalFeature, Feature originalArg,
                                                 Feature redefinedFeature, Feature redefinedArg)
  {
    error(redefinedArg.pos,
          "Wrong argument type in redefined feature",
          "In " + s(redefinedFeature) + " that redefines " + s(originalFeature) + " " +
          "argument type is " + s(redefinedArg.resultType()) + ", argument type should be " + s(originalArg.resultType()) + " " +
          "Original argument declared at " + originalArg.pos.show());
  }

  static void resultTypeMismatchInRedefinition(Feature originalFeature,
                                               Feature redefinedFeature)
  {
    error(redefinedFeature.pos,
          "Wrong result type in redefined feature",
          "In " + s(redefinedFeature) + " that redefines " + s(originalFeature) + " " +
          "result type is " + s(redefinedFeature.resultType()) + ", result type should be " + s(originalFeature.resultType()) + ". " +
          "Original feature declared at " + originalFeature.pos.show());
  }

  static void constructorResultMustBeUnit(Expr res)
  {
    var rt = res.typeOrNull();
    var srt = rt == null ? "an unknown type" : s(rt);
    error(res.posOfLast(), "Constructor code should result in type " + st("unit") + "",
          "Type returned by this constructor's implementation is " +srt + "\n" +
          "To solve this, you could turn this contstructor into a routine by adding a matching result type " +
          "compatible to " + srt + " or by using " + code("=>") + " instead of " + code("is") + " to "+
          "infer the result type from the result expression.\n" +
          "Alternatively, you could explicitly return " + st("unit") + " as the last statement or " +
          "explicitly ignore the result of the last expression by an assignment " + st("_ := <expression>") + ".");
  }

  static void argumentLengthsMismatch(Feature originalFeature, int originalNumArgs,
                                      Feature redefinedFeature, int actualNumArgs)
  {
    error(redefinedFeature.pos,
          "Wrong number of arguments in redefined feature",
          "In " + s(redefinedFeature) + " that redefines " + s(originalFeature) + " " +
          "argument count is " + actualNumArgs + ", argument count should be " + originalNumArgs + " " +
          "Original feature declared at " + originalFeature.pos.show());
  }

  public static void abstractFeatureNotImplemented(Feature featureThatDoesNotImplementAbstract,
                                                   Set<Feature> abstractFeature,
                                                   SourcePosition instantiatedAt)
  {
    var abs = new StringBuilder();
    var abstracts = new StringBuilder();
    for (Feature af : abstractFeature)
      {
        abs.append(abs.length() == 0 ? "" : ", ").append(af._featureName.baseName());
        abstracts.append((abstracts.length() == 0 ? "inherits or declares" : "and") + " abstract feature " +
                         s(af) + " declared at " + af.pos.show() + "\n" +
                         "which is called at " + af.isUsedAt().show() + "\n");
      }
    abstracts.append("without providing an implementation\n");
    error(featureThatDoesNotImplementAbstract.pos,
          "Used abstract " + (abstractFeature.size() > 1 ? "features " + abs + " are" : "feature " + abs + " is") + " not implemented",
          "Feature " + s(featureThatDoesNotImplementAbstract) + " " +
          "instantiated at " + instantiatedAt.show() + "\n" +
          abstracts);
  }

  /* NYI: currently unused, need to check if a "while (23)" produces a useful error message
  static void whileConditionMustBeBool(SourcePosition pos, Type type)
  {
    check
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
    check
      (count() > 0 || type != Types.t_ERROR);

    if (type != Types.t_ERROR)
      {
        error(pos,
              "Loop termination condition following 'until' must be assignable to type 'bool'",
              "Actual type is " + type);
      }
  }
  */

  static void ifConditionMustBeBool(SourcePosition pos, Type type)
  {
    check
      (count() > 0 || type != Types.t_ERROR);

    if (type != Types.t_ERROR)
      {
        error(pos,
              "If condition must be assignable to type " + s(Types.resolved.t_bool) + "",
              "Actual type is " + s(type) + "");
      }
  }

  static void matchSubjectMustNotBeTypeParameter(SourcePosition pos, Type t)
  {
    error(pos,
          "" + skw("match") + " subject type must not be a type parameter",
          "Matched type: " + s(t) + "\n" +
          "which is a type parameter declared at " + t.generic._pos.show());

  }

  static void matchSubjectMustBeChoice(SourcePosition pos, Type t)
  {
    error(pos,
          "" + skw("match") + " subject type must be a choice type",
          "Matched type: " + s(t) + ", which is not a choice type");

  }

  static void repeatedMatch(SourcePosition pos, SourcePosition earlierPos, Type t, List<Type> choiceGenerics)
  {
    error(pos,
          "" + skw("case") + " clause matches type that had been matched already",
          "Matched type: " + s(t) + "\n" +
          "Originally matched at " + earlierPos.show() + "\n" +
          subjectTypes(choiceGenerics));
  }


  static void matchCaseDoesNotMatchAny(SourcePosition pos, Type t, List<Type> choiceGenerics)
  {
    error(pos,
          "" + skw("case") + " clause in " + skw("match") + " statement does not match any type of the subject",
          "Case matches type " + s(t) + "\n" +
          subjectTypes(choiceGenerics));
  }

  static void matchCaseMatchesSeveral(SourcePosition pos, Type t, List<Type> choiceGenerics, List<Type> matches)
  {
    error(pos,
          "" + skw("case") + " clause in " + skw("match") + " statement matches several types of the subject",
          "Case matches type " + s(t) + "\n" +
          subjectTypes(choiceGenerics) +
          "matches are " + typeListConjunction(matches));
  }

  static void missingMatches(SourcePosition pos, List<Type> choiceGenerics, List<Type> missingMatches)
  {
    error(pos,
          "" + skw("match") + " statement does not cover all of the subject's types",
          "Missing cases for types: " + typeListConjunction(missingMatches) + "\n" +
          subjectTypes(choiceGenerics));
  }

  /**
   * Create list of the form "'i32', 'string' or 'bool'"
   */
  private static String typeListAlternatives(List<Type> tl)  { return typeList(tl, "or" ); }

  /**
   * Create list of the form "'i32', 'string' and 'bool'"
   */
  private static String typeListConjunction (List<Type> tl)  { return typeList(tl, "and"); }

  /**
   * Create list of the form "'i32', 'string' " + conj + " 'bool'"
   */
  private static String typeList(List<Type> tl, String conj)
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

  private static String subjectTypes(List<Type> choiceGenerics)
  {
    return choiceGenerics.isEmpty()
      ? "Subject type is an empty choice type that cannot match any case\n"
      : "Subject type is one of " + typeListAlternatives(choiceGenerics) + "\n";
  }

  static void internallyReferencedFeatureNotUnique(SourcePosition pos, String qname, Collection<Feature> set)
  {
    var sb = new StringBuilder();
    for (var f: set)
      {
        if (sb.length() != 0)
          {
            sb.append("\nand ");
          }
        sb.append("" + s(f) + " defined at " + f.pos.show());
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
  static void internallyReferencedFeatureNotFound(SourcePosition pos, String qname, Feature outer, String name)
  {
    error(pos,
          "Internally referenced feature " + sqn(qname) + " not found",
          "Feature not found: " + sbn(name) + "\n" +
          ((outer == null || outer.isUniverse()) ? "" : "Outer feature: " + s(outer) + "\n"));
  }

  static void repeatedInheritanceCannotBeResolved(SourcePosition pos, Feature heir, FeatureName fn, Feature f1, Feature f2)
  {
    error(pos,
          "Repeated inheritance of conflicting features",
          "Feature " + s(heir) + " inherits feature " + sbn(fn) + " repeatedly: " +
          "" + s(f1) + " defined at " + f1.pos.show() + "\n" + "and " +
          "" + s(f2) + " defined at " + f2.pos.show() + "\n" +
          "To solve this, you could add a redefintion of " + sbn(f1) + " to " + s(heir) + ".");
  }

  static void duplicateFeatureDeclaration(SourcePosition pos, Feature f, Feature existing)
  {
    error(pos,
          "Duplicate feature declaration",
          "Feature that was declared repeatedly: " + s(f) + "\n" +
          "originally declared at " + existing.pos.show() + "\n" +
          "To solve this, consider renaming one of these two features or changing its number of arguments");
  }

  static void cannotRedefine(SourcePosition pos, Feature f, Feature existing, String msg, String solution)
  {
    error(pos,
          msg,
          "Feature that redefines existing feature: " + s(f) + "\n" +
          "original feature: " + s(existing) + "\n" +
          "original feature defined in " + existing.pos.fileNameWithPosition()+ "\n" +
          solution);
  }

  static void cannotRedefineGeneric(SourcePosition pos, Feature f, Feature existing)
  {
    cannotRedefine(pos, f, existing, "Cannot redefine feature with generic arguments",
                   "To solve this, ask the Fuzion team to remove this restriction :-)."); // NYI: inheritance and generics
  }

  static void redefineModifierMissing(SourcePosition pos, Feature f, Feature existing)
  {
    cannotRedefine(pos, f, existing, "Redefinition must be declared using modifier " + skw("redef") + "",
                   "To solve this, if you did not intent to redefine an inherited feature, " +
                   "chose a different name for " + sbn(f) + ".  Otherwise, if you do " +
                   "want to redefine an inherited feature, add a " + skw("redef") + " modifier before the " +
                   "declaration of " + s(f) + ".");
  }

  static void redefineModifierDoesNotRedefine(Feature f)
  {
    error(f.pos,
          "Feature declared using modifier " + skw("redef") + " does not redefine another feature",
          "Redefining feature: " + s(f) + "\n" +
          "To solve this, check spelling and argument count against the feature you want to redefine or " +
          "remove " + skw("redef") + " modifier in the declaration of " + s(f) + ".");
  }

  static void ambiguousCallTargets(SourcePosition pos,
                                   FeatureName fn,
                                   List<Feature> targets)
  {
    error(pos,
          "Ambiguous call targets found for call to " + sbn(fn) + "",
          "Found several possible targets that match this call:\n" +
          featureList(targets));
  }

  /**
   * If name is Feature.RESULT_NAME and argcount is 0, return text that suggests
   * declaring a return type in the outer feature. Otherwise, return "".
   */
  static String solutionDeclareReturnTypeIfResult(String name, int argCount)
  {
    var solution = "";
    if (name.equals(Feature.RESULT_NAME) &&
        argCount == 0)
      {
        solution = "To solve this, make sure you declare a return type in the surrounding feature such that " +
          "the " + sbn(Feature.RESULT_NAME) + " field will be declared automatically.";
      }
    return solution;
  }

  static void calledFeatureNotFound(Call call,
                                    FeatureName calledName,
                                    Feature targetFeature)
  {
    var solution = solutionDeclareReturnTypeIfResult(calledName.baseName(),
                                                     calledName.argCount());
    error(call.pos,
          "Could not find called feature",
          "Feature not found: " + sbn(calledName) + "\n" +
          "Target feature: " + s( targetFeature) + "\n" +
          "In call: " + s(call) + "\n" +
          solution);
  }

  static void ambiguousType(Type t,
                            List<Feature> possibilities)
  {
    error(t.pos,
          "Ambiguous type",
          "For a type used in a declaration, overloading results in an ambiguity that cannot be resolved by the compiler.\n" +
          "Type that is ambiguous: " + s(t) + "\n" +
          "Possible features that match this type: \n" +
          featureList(possibilities) + "\n" +
          "To solve this, rename these features such that each one has a unique name.");
  }

  static void typeNotFound(Type t,
                           Feature outerfeat,
                           List<Feature> nontypes_found)
  {
    int n = nontypes_found.size();
    boolean hasAbstract = false;
    boolean hasReturnType = false;
    for (var f : nontypes_found)
      {
        hasAbstract = f.impl == Impl.ABSTRACT;
        hasReturnType = f.returnType != NoType.INSTANCE && !f.returnType.isConstructorType();
      }
    error(t.pos,
          "Type not found",
          "Type " + st(t.name) + " was not found, no corresponding feature nor formal generic argument exists\n" +
          "Type that was not found: " + s(t) + "\n" +
          "within feature: " + s(outerfeat) + "\n" +
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

  public static void mainFeatureMustNotHaveArguments(Feature m)
  {
    error(m.pos,
          "Main feature must not have arguments",
          "Main feature has " + argumentsString(m.arguments.size()) + m.arguments.size()+", but should have no arguments to be used as main feature in an application\n" +
          "To solve this, remove the arguments from feature " + s(m) + "\n");
  }

  public static void mainFeatureMustNotHaveTypeArguments(Feature m)
  {
    var g = m.generics.list;
    error(m.pos,
          "Main feature must not have type arguments",
          "Main feature has " + singularOrPlural(g.size(),"type argument") + " " + g + ", but should have no arguments to be used as main feature in an application\n" +
          "To solve this, remove the arguments from feature " + s(m) + "\n");
  }

  static void mainFeatureMustNot(Feature m, String what)
  {
    error(m.pos,
          "Main feature must not " +  what,
          "Main feature must be a non-abstract non-intrinsic routine\n" +
          "To solve this, use a non-abstract, non-intrinsic, non-generic routine as the main feature of your application.\n");
  }

  public static void mainFeatureMustNotBeField(Feature m)
  {
    mainFeatureMustNot(m, "be a field");
  }

  public static void mainFeatureMustNotBeAbstract(Feature m)
  {
    mainFeatureMustNot(m, "be abstract");
  }

  public static void mainFeatureMustNotBeIntrinsic(Feature m)
  {
    mainFeatureMustNot(m, "be intrinsic");
  }

  static void mainFeatureMustNotHaveGenericArguments(Feature m)
  {
    mainFeatureMustNot(m, "have generic arguments");
  }

  static void initialValueNotAllowed(Feature f)
  {
    error(f.pos,
          "Initial value not allowed for feature not embedded in outer feature",
          "Fuzion currently does not know when to execute this initializer, so it is forbidden.\n" +
          "To solve this, move the declaration inside another feature or ask the Fuzion team for help.");
  }

  static void missingResultTypeForField(Feature f)
  {
    check
      (count() > 0 || !f.featureName().baseName().equals(ERROR_STRING));

    if (!f.featureName().baseName().equals(ERROR_STRING))
      {
        error(f.pos,
              "Missing result type in field declaration with initializaton",
              "Field declared: " + s(f) + "");
      }
  }

  static void outerFeatureNotFoundInThis(This t, Feature feat, String qname, List<String> available)
  {
    error(t.pos,
          "Could not find outer feature in " + skw(".this") + "-expression",
          "Within feature: " + s(feat) + "\n" +
          "Outer feature that was not found: " + sqn(qname) + "\n" +
          "Outer features available: " + (available.size() == 0 ? "-- none! --" : available));
  }

  static void blockMustEndWithExpression(SourcePosition pos, Type expectedType)
  {
    check
      (count() > 0  || expectedType != Types.t_ERROR);

    if (expectedType != Types.t_ERROR)
      {
        error(pos,
              "Block must end with a result expression",
              "This block must produce a value since its result is used by the enclosing statement.\n" +
              "Expected type of value: " + s(expectedType) + "");
      }
  }

  static void constraintMustNotBeGenericArgument(Generic g)
  {
    error(g._pos,
          "Constraint for generic argument must not be generic type parameter",
          "Affected generic argument: " + st(g._name) + "\n" +
          "_constraint: " + s(g.constraint()) + " declared at " + g.constraint().generic._pos);
  }

  static void loopElseBlockRequiresWhileOrIterator(SourcePosition pos, Expr elseBlock)
  {
    error(pos, "Loop without while condition cannot have an else block",
          "Since the else block is executed if the while condition is false " +
          "or an iteration ended, it does not make sense " +
          "to have an else condition unless there is a while clause or an iterator " +
          "index variable.\n" +
          "The else block of this loop is declared at " + elseBlock.pos.show());
  }

  static void formalGenericAsOuterType(SourcePosition pos, Type t)
  {
    error(pos,
          "Formal generic cannot be used as outer type",
          "In a type >>a.b<<, the outer type >>a<< must not be a formal generic argument.\n" +
          "Type used: " + s(t) + "\n" +
          "Formal generic used " + s(t.outer()) + "\n" +
          "Formal generic declared in " + t.outer().genericArgument()._pos.show() + "\n");
  }

  static void formalGenericWithGenericArgs(SourcePosition pos, Type t, Generic generic)
  {
    error(pos,
          "Formal generic cannot have generic arguments",
          "In a type with generic arguments >>A<B><<, the base type >>A<< must not be a formal generic argument.\n" +
          "Type used: " + s(t) + "\n" +
          "Formal generic used " + s(generic) + "\n" +
          "Formal generic declared in " + generic._pos.show() + "\n");
  }

  static void refToChoice(SourcePosition pos)
  {
    error(pos,
          "ref to a choice type is not allowed",
          "a choice is always a value type");
  }

  static void genericsMustBeDisjoint(SourcePosition pos, Type t1, Type t2)
  {
    error(pos,
          "Generics arguments to choice type must be disjoint types",
          "The following types have overlapping values:\n" +
          "" + s(t1) + "" + /* " at " + t1.pos.show() + */ "\n" +  // NYI: use pos before Types were interned!
          "" + s(t2) + "" + /* " at " + t2.pos.show() + */ "\n");
  }

  static void illegalUseOfOpenFormalGeneric(SourcePosition pos, Generic generic)
  {
    error(pos,
          "Illegal use of open formal generic type",
          "Open formal generic type is permitted only as the type of the last argument in a formal arguments list of an abstract feature.\n" +
          "Open formal argument: " + s(generic) + "");
  }

  static void integerConstantOutOfLegalRange(SourcePosition pos, String constant, Type t, String from, String to)
  {
    error(pos,
          "Integer constant value outside of allowed range for target type",
          "Type propagation results in a type that is too small for the value represented by the given constant.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n" +
          "Acceptable range of values: " + ss(from) + " .. " + ss(to));
  }

  static void nonWholeNumberUsedAsIntegerConstant(SourcePosition pos, String constant, Type t)
  {
    error(pos,
          "Numeric literal used for integer type is not a whole number",
          "Type propagation results in an integer type that cannot whole a value that is not integer.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n");
  }

  static void floatConstantTooLarge(SourcePosition pos, String constant, Type t, String max, String maxH)
  {
    error(pos,
          "Float constant value outside of allowed range for target type",
          "Type propagation results in a type that is too small for the value represented by the given constant.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n" +
          "Max allowed value: " + ss("-"+max) + " .. " + ss(max) + " or " + ss("-" + maxH) + " .. " + ss(maxH));
  }

  static void floatConstantTooSmall(SourcePosition pos, String constant, Type t, String min, String minH)
  {
    error(pos,
          "Float constant value too small, would underflow to 0",
          "Type propagation results in a type whose precision does not allow to represented the given constant.\n" +
          "Numeric literal: " + ss(constant) + "\n" +
          "Assigned to type: " + s(t) + "\n" +
          "Min representable value > 0: " + ss(min) + " or " + ss(minH));
  }

  static void wrongNumberOfArgumentsInLambda(SourcePosition pos, List<String> names, Type funType)
  {
    int req = funType._generics.size() - 1;
    int delta = names.size() - req;
    var ns = sn(names);
    error(pos,
          "Wrong number of arguments in lambda expression",
          "Lambda expression has " + singularOrPlural(names.size(), "argument") + " while the target type expects " +
          singularOrPlural(funType._generics.size()-1, "argument") + ".\n" +
          "Arguments of lambda expression: " + ns + "\n" +
          "Expected function type: " + funType + "\n" +
          "To solve this, " +
          (req == 0 ? "replace the list " + ns + " by " + ss("()") + "."
                    : (delta < 0 ? "add "    + singularOrPlural(-delta, "argument") + " to the list "   + ns
                                 : "remove " + singularOrPlural( delta, "argument") + " from the list " + ns) +
                      " before the " + ss("->") + " of the lambda expression.")
          );
  }

  static void expectedFunctionTypeForLambda(SourcePosition pos, Type t)
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

}

/* end of file */
