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
 * Source of shared code of Fuzion C backend.
 *
 *---------------------------------------------------------------------*/

#ifdef GC_THREADS
#include <gc.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <assert.h>
#include <stdatomic.h>


/**
 * Perform bitwise comparison of two float values. This is used by
 * concur.atmic.compare_and_swap/set to compare floats. In particular, this
 * results is unequality of +0 and -0 and equality of NaN unless undefined bits
 * differ, etc.
 *
 * @param f1, f2 two float values
 *
 * @return true iff f1 and f2 are represented in memory by the same bit patternsx.
 */
bool fzE_bitwise_compare_float(float f1, float f2)
{
  union
  {
    float f;
    int32_t bits;
  } v1, v2;
  v1.f = f1;
  v2.f = f2;
  return v1.bits == v2.bits;
}


/**
 * Perform bitwise comparison of two double values. This is used by
 * concur.atmic.compare_and_swap/set to compare floats. In particular, this
 * results is unequality of +0 and -0 and equality of NaN unless undefined bits
 * differ, etc.
 *
 * @param d1, d2 two double values
 *
 * @return true iff d1 and d2 are represented in memory by the same bit patterns.
 */
bool fzE_bitwise_compare_double(double d1, double d2)
{
  union
  {
    double d;
    int64_t bits;
  } v1, v2;
  v1.d = d1;
  v2.d = d2;
  return v1.bits == v2.bits;
}


void * fzE_malloc_safe(size_t size) {
#ifdef GC_THREADS
  void *p = GC_MALLOC(size);
#else
  void *p = malloc(size);
#endif
  if (p == NULL) {
    fprintf(stderr, "*** malloc(%zu) failed ***\n", size);
    exit(EXIT_FAILURE);
  }
  return p;
}

void fzE_memset(void *dest, int ch, size_t sz){
  // NYI: UNDER DEVELOPMENT: use bounds checked version, e.g. memset_s
  memset(dest, ch, sz);
}

void fzE_memcpy(void *restrict dest, const void *restrict src, size_t sz){
  // NYI: UNDER DEVELOPMENT: use bounds checked version, e.g. memcpy_s
  memcpy(dest, src, sz);
}


#ifdef FUZION_LINK_JVM

#include <jni.h>

// global instance of the jvm
JavaVM *fzE_jvm                = NULL;
// global instance of the jvm environment
__thread JNIEnv *fzE_jni_env   = NULL;
_Bool jvm_running              = false;

// cached jclasses and jmethods which are frequently used

jclass fzE_class_float         = NULL;
jclass fzE_class_double        = NULL;
jclass fzE_class_byte          = NULL;
jclass fzE_class_short         = NULL;
jclass fzE_class_character     = NULL;
jclass fzE_class_integer       = NULL;
jclass fzE_class_long          = NULL;
jclass fzE_class_boolean       = NULL;

jmethodID fzE_float_valueof     = NULL;
jmethodID fzE_double_valueof    = NULL;
jmethodID fzE_byte_valueof      = NULL;
jmethodID fzE_short_valueof     = NULL;
jmethodID fzE_character_valueof = NULL;
jmethodID fzE_integer_valueof   = NULL;
jmethodID fzE_long_valueof      = NULL;
jmethodID fzE_boolean_valueof   = NULL;

jmethodID fzE_float_value       = NULL;
jmethodID fzE_double_value      = NULL;
jmethodID fzE_byte_value        = NULL;
jmethodID fzE_short_value       = NULL;
jmethodID fzE_character_value   = NULL;
jmethodID fzE_integer_value     = NULL;
jmethodID fzE_long_value        = NULL;
jmethodID fzE_boolean_value     = NULL;

// definition of a struct for a jvm result
// in case of success v0 is used
// in case of exception v1 is used
typedef struct fzE_jvm_result fzE_jvm_result;
struct fzE_jvm_result
{
  int32_t fzTag;
  union
  {
    jvalue v0;
    jstring v1; // NYI should probably better be jthrowable
  }fzChoice;
};


// convert 0-terminated utf-8 to modified utf-8 as
// used by the JVM.
void utf8_to_mod_utf8(const char *utf8, char *mod_utf8) {

  uint8_t *ch = (uint8_t *)utf8;

  while (ch[0]) {
    if ((ch[0] & 0x80) == 0) {
      *mod_utf8++ = ch[0];
      ch += 1;
    } else if ((ch[0] & 0xE0) == 0xC0) {
      *mod_utf8++ = ch[0];
      *mod_utf8++ = ch[1];
      ch += 2;
    } else if ((ch[0] & 0xF0) == 0xE0) {
      *mod_utf8++ = ch[0];
      *mod_utf8++ = ch[1];
      *mod_utf8++ = ch[2];
      ch += 3;
    } else if ((ch[0] & 0xF8) == 0xF0) {
      // modified utf-8 supports only 3-byte encoding
      // need to transform:

      // https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4.7
      // table 4.15
      unsigned cp = (ch[0] & 0x07) << 18;
      cp += (ch[1] & 0x3F) << 12;
      cp += (ch[2] & 0x3F) << 6;

      *mod_utf8++ = 0xED;
      *mod_utf8++ = 0xA0 + (((cp >> 16) - 1) & 0x0F);
      *mod_utf8++ = 0x80 + ((cp >> 10) & 0x3F);

      *mod_utf8++ = 0xED;
      *mod_utf8++ = 0xB0 + ((cp >> 6) & 0x0F);
      *mod_utf8++ = ch[3];

      ch += 4;
    } else {
      fprintf(stderr, "Invalid UTF-8\n");
      exit(EXIT_FAILURE);
    }
  }
  *mod_utf8 = '\0';
}

// get jni-env for current thread
JNIEnv * getJNIEnv()
{
  if (!jvm_running)
    {
      printf("JVM has not been started via: `fuzion.java.create_jvm0 ...`\n");
      exit(EXIT_FAILURE);
    }
  if (fzE_jni_env == NULL) {
    // NYI: DetachCurrentThread
    (*fzE_jvm)->AttachCurrentThread(fzE_jvm, (void **)&fzE_jni_env, NULL);
  }
  return fzE_jni_env;
}

// initialize the JVM
// executed once at the start of the application
void fzE_create_jvm(char * option_string) {
  JavaVMInitArgs vm_args;

  JavaVMOption options[1];
  options[0].optionString = option_string;

  vm_args.version = JNI_VERSION_10;
  vm_args.options = options;
  vm_args.nOptions = 1;
  if (JNI_CreateJavaVM(&fzE_jvm, (void **)&fzE_jni_env, &vm_args) != JNI_OK) {
    printf("Failed to start Java VM");
    exit(EXIT_FAILURE);
  }

  jvm_running = true;

  fzE_class_float  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Float");
  fzE_class_double = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Double");
  fzE_class_byte  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Byte");
  fzE_class_short  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Short");
  fzE_class_character  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Character");
  fzE_class_integer  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Integer");
  fzE_class_long  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Long");
  fzE_class_boolean  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Boolean");

  fzE_float_valueof   = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_float, "valueOf", "(F)Ljava/lang/Float;");
  fzE_double_valueof = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_double, "valueOf", "(D)Ljava/lang/Double;");
  fzE_byte_valueof  = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_byte, "valueOf", "(B)Ljava/lang/Byte;");
  fzE_short_valueof  = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_short, "valueOf", "(S)Ljava/lang/Short;");
  fzE_character_valueof  = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_character, "valueOf", "(C)Ljava/lang/Character;");
  fzE_integer_valueof  = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_integer, "valueOf", "(I)Ljava/lang/Integer;");
  fzE_long_valueof  = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_long, "valueOf", "(J)Ljava/lang/Long;");
  fzE_boolean_valueof  = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), fzE_class_boolean, "valueOf", "(Z)Ljava/lang/Boolean;");

  fzE_float_value     = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_float, "floatValue", "()F");
  fzE_double_value    = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_double, "doubleValue", "()D");
  fzE_byte_value      = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_byte, "byteValue", "()B");
  fzE_short_value     = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_short, "shortValue", "()S");
  fzE_character_value = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_character, "charValue", "()C");
  fzE_integer_value   = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_integer, "intValue", "()I");
  fzE_long_value      = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_long, "longValue", "()J");
  fzE_boolean_value   = (*getJNIEnv())->GetMethodID(getJNIEnv(), fzE_class_boolean, "booleanValue", "()Z");
}

// close the JVM.
void fzE_destroy_jvm()
{
  (*fzE_jvm)->DestroyJavaVM(fzE_jvm);
}

// helper function to replace char `find`
// by `replace` in string `str`.
char* fzE_replace_char(const char* str, char find, char replace){
    size_t len = strlen(str);
    char * result = fzE_malloc_safe(len+1);
    fzE_memcpy(result, str, len+1);
    char *pos = strchr(result,find);
    while (pos) {
        *pos = replace;
        pos = strchr(pos,find);
    }
    return result;
}

// convert a jstring to a utf-8 byte array
// NYI OPTIMIZATION do conversion in C not via the JVM.
const char * fzE_java_string_to_utf8_bytes(jstring jstr)
{
  if (jstr == NULL)
    {
      return "--null--";
    }
  const jclass cls = (*getJNIEnv())->GetObjectClass(getJNIEnv(), jstr);
  const jmethodID getBytes = (*getJNIEnv())->GetMethodID(getJNIEnv(), cls, "getBytes", "(Ljava/lang/String;)[B");
  const jstring charsetName = (*getJNIEnv())->NewStringUTF(getJNIEnv(), "UTF-8");
  const jbyteArray arr = (jbyteArray) (*getJNIEnv())->CallObjectMethod(getJNIEnv(), jstr, getBytes, charsetName);
  (*getJNIEnv())->DeleteLocalRef(getJNIEnv(), charsetName);

  jbyte * bytes = (*getJNIEnv())->GetByteArrayElements(getJNIEnv(), arr, NULL);
  const jsize length = (*getJNIEnv())->GetArrayLength(getJNIEnv(), arr);
  char * result = fzE_malloc_safe(length);
  fzE_memcpy(result, bytes, length);

  (*getJNIEnv())->ReleaseByteArrayElements(getJNIEnv(), arr, bytes, JNI_ABORT);
  (*getJNIEnv())->DeleteLocalRef(getJNIEnv(), arr);

  return result;
}


// convert a jstring to modified utf-8 bytes
const char * fzE_java_string_to_modified_utf8(jstring jstr)
{
  assert ( jstr != NULL );
  const char * str = (*getJNIEnv())->GetStringUTFChars(getJNIEnv(), jstr, JNI_FALSE);
  jsize sz = (*getJNIEnv())->GetStringUTFLength(getJNIEnv(), jstr);
  char * result = fzE_malloc_safe(sz);
  fzE_memcpy(result, str, sz+1);
  (*getJNIEnv())->ReleaseStringUTFChars(getJNIEnv(), jstr, str);
  return result;
}


jvalue fzE_f32_to_java_object(double arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_float, fzE_float_valueof, arg) };
}
jvalue fzE_f64_to_java_object(float arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_double, fzE_double_valueof, arg) };
}
jvalue fzE_i8_to_java_object(int8_t arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_byte, fzE_byte_valueof, arg) };
}
jvalue fzE_i16_to_java_object(int16_t arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_short, fzE_short_valueof, arg) };
}
jvalue fzE_u16_to_java_object(uint16_t arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_character, fzE_character_valueof, arg) };
}
jvalue fzE_i32_to_java_object(int32_t arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_integer, fzE_integer_valueof, arg) };
}
jvalue fzE_i64_to_java_object(int64_t arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_long, fzE_long_valueof, arg) };
}
jvalue fzE_bool_to_java_object(bool arg)
{
  return (jvalue){ .l = (*getJNIEnv())->CallStaticObjectMethod(getJNIEnv(), fzE_class_boolean, fzE_boolean_valueof, arg) };
}


// convert args that map to java primitives
jvalue *fzE_convert_args(const char *sig, jvalue *args) {
  int idx = 0;
  sig++;
  while (*sig != ')') {
    switch (*sig) {
    case 'F':
      args[idx].f = (*getJNIEnv())->CallFloatMethod(getJNIEnv(), args[idx].l, fzE_float_value);
      idx++;
      break;
    case 'D':
      args[idx].d = (*getJNIEnv())->CallDoubleMethod(getJNIEnv(), args[idx].l, fzE_double_value);
      idx++;
      break;
    case 'B':
      args[idx].b = (*getJNIEnv())->CallByteMethod(getJNIEnv(), args[idx].l, fzE_byte_value);
      idx++;
      break;
    case 'S':
      args[idx].s = (*getJNIEnv())->CallShortMethod(getJNIEnv(), args[idx].l, fzE_short_value);
      idx++;
      break;
    case 'C':
      args[idx].c = (*getJNIEnv())->CallCharMethod(getJNIEnv(), args[idx].l, fzE_character_value);
      idx++;
      break;
    case 'I':
      args[idx].i = (*getJNIEnv())->CallIntMethod(getJNIEnv(), args[idx].l, fzE_integer_value);
      idx++;
      break;
    case 'J':
      args[idx].j = (*getJNIEnv())->CallLongMethod(getJNIEnv(), args[idx].l, fzE_long_value);
      idx++;
      break;
    case 'Z':
      args[idx].z = (*getJNIEnv())->CallBooleanMethod(getJNIEnv(), args[idx].l, fzE_boolean_value);
      idx++;
      break;
    case '[':
      if (sig[1] != 'L')
        {
          idx++;
          sig++;
        }
      break;
    case 'L':
      idx++;
      while (*++sig != ';') {
      }
      break;
    default:
      // NYI array
      printf("unhandled %c", *sig);
      exit(EXIT_FAILURE);
      break;
    }
    sig++;
  }
  return args;
}


// convert jstring to error result
fzE_jvm_result fzE_jvm_not_found(jstring jstr)
{
  assert ( jstr != NULL );
  return (fzE_jvm_result){ .fzTag = 1, .fzChoice = { .v1 = jstr /* NYI: should be: "Not found" + jv */ } };
}


// convert a 0-terminated utf8-bytes array to a jstring.
jvalue fzE_string_to_java_object(const void * utf8_bytes, int byte_length)
{
  // NYI we don't really need 4*byte_length, see modifiedUtf8LengthOfUtf8:
  // https://github.com/openjdk/jdk/blob/eb9e754b3a439cc3ce36c2c9393bc8b250343844/src/java.instrument/share/native/libinstrument/EncodingSupport.c#L98
  char outstr[4*byte_length];
  utf8_to_mod_utf8(utf8_bytes, outstr);
  jvalue result = (jvalue){ .l = (*getJNIEnv())->NewStringUTF(getJNIEnv(), outstr) };
  return result;
}

// convert c-string to error result
fzE_jvm_result fzE_jvm_error(const char * str)
{
  jvalue jstr = fzE_string_to_java_object(str, strlen(str));
  return (fzE_jvm_result){ .fzTag = 1, .fzChoice = { .v1 = jstr.l } };
}


// return result, check for exception
// return exception if there is any
fzE_jvm_result fzE_return_result(jvalue jv)
{
  if ( (*getJNIEnv())->ExceptionCheck(getJNIEnv()) == JNI_FALSE )
  {
    return (fzE_jvm_result){ .fzTag = 0, .fzChoice = { .v0 = jv } };
  }
  else
  {
    jthrowable exc =  (*getJNIEnv())->ExceptionOccurred(getJNIEnv());
    assert (exc != NULL);
    jclass cl  = (*getJNIEnv())->FindClass(getJNIEnv(), "java/lang/Throwable");
    assert( cl != NULL );
    jmethodID mid = (*getJNIEnv())->GetMethodID(getJNIEnv(), cl, "getMessage", "()Ljava/lang/String;");
    assert( mid != NULL );
    jstring exc_message = (jstring) (*getJNIEnv())->CallObjectMethod(getJNIEnv(), exc, mid);
    (*getJNIEnv())->ExceptionClear(getJNIEnv());
    return (fzE_jvm_result){ .fzTag = 1, .fzChoice = { .v1 = exc_message } };
  }
}


// call a java constructor
fzE_jvm_result fzE_call_c0(jstring class_name, jstring signature, jvalue *args)
{
  const char * sig = fzE_java_string_to_modified_utf8(signature);
  jclass cl  = (*getJNIEnv())->FindClass(getJNIEnv(), fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  if (cl == NULL) { return fzE_jvm_not_found(class_name); }
  jmethodID mid = (*getJNIEnv())->GetMethodID(getJNIEnv(), cl, "<init>", sig);
  if (mid == NULL) { return fzE_jvm_not_found(class_name); }
  jvalue result = { .l = (*getJNIEnv())->NewObjectA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)) };

  return fzE_return_result(result);
}


// call a java static method
fzE_jvm_result fzE_call_s0(jstring class_name, jstring name, jstring signature, jvalue *args)
{
  const char * sig = fzE_java_string_to_modified_utf8(signature);
  jclass cl  = (*getJNIEnv())->FindClass(getJNIEnv(), fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  if (cl == NULL) { return fzE_jvm_not_found(class_name); }
  jmethodID mid = (*getJNIEnv())->GetStaticMethodID(getJNIEnv(), cl, fzE_java_string_to_modified_utf8(name), sig);
  if (mid == NULL) { return fzE_jvm_not_found(name); }
  const char * sig2 = sig;
  while (*sig2 != ')') {
    if (*sig2 == '\0') { return fzE_jvm_error("unexpected signature format"); }
    sig2++;
  }
  jvalue result;
  switch (sig2[1])
    {
      case 'B':
        result.b = (*getJNIEnv())->CallStaticByteMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'C':
        result.c = (*getJNIEnv())->CallStaticCharMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'S':
        result.s = (*getJNIEnv())->CallStaticShortMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'I':
        result.i = (*getJNIEnv())->CallStaticIntMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'J':
        result.j = (*getJNIEnv())->CallStaticLongMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'F':
        result.f = (*getJNIEnv())->CallStaticFloatMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'D':
        result.d = (*getJNIEnv())->CallStaticDoubleMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'Z':
        result.z = (*getJNIEnv())->CallStaticBooleanMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'V' :
        result.l = NULL;
        (*getJNIEnv())->CallStaticObjectMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      case 'L' :
      case '[' :
        result.l = (*getJNIEnv())->CallStaticObjectMethodA(getJNIEnv(), cl, mid, fzE_convert_args(sig, args)); break;
      default:
        assert(false);
    }

  return fzE_return_result(result);
}


// call a java virtual method
fzE_jvm_result fzE_call_v0(jstring class_name, jstring name, jstring signature, jobject thiz, jvalue *args)
{
  const char * sig = fzE_java_string_to_modified_utf8(signature);
  jclass cl  = (*getJNIEnv())->FindClass(getJNIEnv(), fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  if (cl == NULL) { return fzE_jvm_not_found(class_name); }
  jmethodID mid = (*getJNIEnv())->GetMethodID(getJNIEnv(), cl, fzE_java_string_to_modified_utf8(name), sig);
  if (mid == NULL) { return fzE_jvm_not_found(name); }
  const char * sig2 = sig;
  while (*sig2 != ')') {
    if (*sig2 == '\0') { return fzE_jvm_error("unexpected signature format"); }
    sig2++;
  }
  jvalue result;
  switch (sig2[1])
    {
      case 'B':
        result = (jvalue){ .b = (*getJNIEnv())->CallByteMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'C':
        result = (jvalue){ .c = (*getJNIEnv())->CallCharMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'S':
        result = (jvalue){ .s = (*getJNIEnv())->CallShortMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'I':
        result = (jvalue){ .i = (*getJNIEnv())->CallIntMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'J':
        result = (jvalue){ .j = (*getJNIEnv())->CallLongMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'F':
        result = (jvalue){ .f = (*getJNIEnv())->CallFloatMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'D':
        result = (jvalue){ .d = (*getJNIEnv())->CallDoubleMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'Z':
        result = (jvalue){ .z = (*getJNIEnv())->CallBooleanMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      case 'V':
        result.l = NULL;
        (*getJNIEnv())->CallObjectMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)); break;
      case 'L' :
      case '[' :
        result = (jvalue){ .l = (*getJNIEnv())->CallObjectMethodA(getJNIEnv(), thiz, mid, fzE_convert_args(sig, args)) }; break;
      default:
        assert(false);
    }

  return fzE_return_result(result);
}



// test if jobj is null
bool fzE_java_object_is_null(jobject jobj)
{
  return (*getJNIEnv())->IsSameObject(getJNIEnv(), jobj, NULL);
}


// get length of the jarray
int32_t fzE_array_length(jarray array)
{
  assert (array != NULL);
  return (*getJNIEnv())->GetArrayLength(getJNIEnv(), array);
}


jvalue fzE_array_to_java_object0(jsize length, jvalue *args, const char * element_class_name)
{
  if (strcmp(element_class_name, "Z") == 0 )
  {
    jarray result = (*getJNIEnv())->NewBooleanArray(getJNIEnv(), length);
    const jboolean f[] = {JNI_FALSE};
    const jboolean t[] = {JNI_TRUE};
    for (int i = 0; i < length; i++)
      {
        (*getJNIEnv())->SetBooleanArrayRegion(getJNIEnv(), result, i, 1, ((int32_t *)args)[i] ? t : f);
      }
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "B") == 0 )
  {
    jarray result = (*getJNIEnv())->NewByteArray(getJNIEnv(), length);
    (*getJNIEnv())->SetByteArrayRegion(getJNIEnv(), result, 0, length, (jbyte *) args);
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "S") == 0 )
  {
    jarray result = (*getJNIEnv())->NewShortArray(getJNIEnv(), length);
    (*getJNIEnv())->SetShortArrayRegion(getJNIEnv(), result, 0, length, (jshort *) args);
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "C") == 0 )
  {
    jarray result = (*getJNIEnv())->NewCharArray(getJNIEnv(), length);
    (*getJNIEnv())->SetCharArrayRegion(getJNIEnv(), result, 0, length, (jchar *) args);
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "I") == 0 )
  {
    jarray result = (*getJNIEnv())->NewIntArray(getJNIEnv(), length);
    (*getJNIEnv())->SetIntArrayRegion(getJNIEnv(), result, 0, length, (jint *) args);
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "J") == 0 )
  {
    jarray result = (*getJNIEnv())->NewLongArray(getJNIEnv(), length);
    (*getJNIEnv())->SetLongArrayRegion(getJNIEnv(), result, 0, length, (jlong *) args);
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "F") == 0 )
  {
    jarray result = (*getJNIEnv())->NewFloatArray(getJNIEnv(), length);
    (*getJNIEnv())->SetFloatArrayRegion(getJNIEnv(), result, 0, length, (jfloat *) args);
    return (jvalue){ .l = result };
  }
  else if (strcmp(element_class_name, "D") == 0 )
  {
    jarray result = (*getJNIEnv())->NewDoubleArray(getJNIEnv(), length);
    (*getJNIEnv())->SetDoubleArrayRegion(getJNIEnv(), result, 0, length, (jdouble *) args);
    return (jvalue){ .l = result };
  }
  else
  {
    jclass cl = (*getJNIEnv())->FindClass(getJNIEnv(), element_class_name);
    // NYI: UNDER DEVELOPMENT: crash more gracefully
    assert( cl != NULL );
    jobjectArray result = (*getJNIEnv())->NewObjectArray(getJNIEnv(), length, cl, NULL);
    for (jsize i = 0; i < length; i++)
      {
        (*getJNIEnv())->SetObjectArrayElement(getJNIEnv(), result, i, ((jobject *) args)[i]);
      }
    return (jvalue){ .l = result };
  }
}


// get element in array at index
jvalue fzE_array_get(jarray array, jsize index, const char *sig)
{
  switch (sig[0])
    {
      case 'B':
        return (jvalue){ .b = (*getJNIEnv())->GetByteArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'C':
        return (jvalue){ .c = (*getJNIEnv())->GetCharArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'S':
        return (jvalue){ .s = (*getJNIEnv())->GetShortArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'I':
        return (jvalue){ .i = (*getJNIEnv())->GetIntArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'J':
        return (jvalue){ .j = (*getJNIEnv())->GetLongArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'F':
        return (jvalue){ .f = (*getJNIEnv())->GetFloatArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'D':
        return (jvalue){ .d = (*getJNIEnv())->GetDoubleArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      case 'Z':
        return (jvalue){ .z = (*getJNIEnv())->GetBooleanArrayElements(getJNIEnv(), array, JNI_FALSE)[index] };
      default:
        return (jvalue){ .l = (*getJNIEnv())->GetObjectArrayElement(getJNIEnv(), array, index) };
    }
}


// get a non-static field on obj.
jvalue fzE_get_field0(jobject obj, jstring name, const char *sig)
{
  jclass cl = (*getJNIEnv())->GetObjectClass(getJNIEnv(), obj);
  assert( cl != NULL );
  jfieldID fieldID = (*getJNIEnv())->GetFieldID(getJNIEnv(), cl, fzE_java_string_to_modified_utf8(name), sig);
  // NYI: UNDER DEVELOPMENT: crash more gracefully
  assert( fieldID != NULL );
  switch (sig[0])
    {
      case 'B':
        return (jvalue){ .b = (*getJNIEnv())->GetByteField(getJNIEnv(), obj, fieldID) };
      case 'C':
        return (jvalue){ .c = (*getJNIEnv())->GetCharField(getJNIEnv(), obj, fieldID) };
      case 'S':
        return (jvalue){ .s = (*getJNIEnv())->GetShortField(getJNIEnv(), obj, fieldID) };
      case 'I':
        return (jvalue){ .i = (*getJNIEnv())->GetIntField(getJNIEnv(), obj, fieldID) };
      case 'J':
        return (jvalue){ .j = (*getJNIEnv())->GetLongField(getJNIEnv(), obj, fieldID) };
      case 'F':
        return (jvalue){ .f = (*getJNIEnv())->GetFloatField(getJNIEnv(), obj, fieldID) };
      case 'D':
        return (jvalue){ .d = (*getJNIEnv())->GetDoubleField(getJNIEnv(), obj, fieldID) };
      case 'Z':
        return (jvalue){ .z = (*getJNIEnv())->GetBooleanField(getJNIEnv(), obj, fieldID) };
      default:
        return (jvalue){ .l = (*getJNIEnv())->GetObjectField(getJNIEnv(), obj, fieldID) };
    }
}


// set a non-static field on obj.
void fzE_set_field0(jobject obj, jstring name, jvalue value, const char *sig)
{
  jclass cl = (*getJNIEnv())->GetObjectClass(getJNIEnv(), obj);
  assert( cl != NULL );
  jfieldID fieldID = (*getJNIEnv())->GetFieldID(getJNIEnv(), cl, fzE_java_string_to_modified_utf8(name), sig);
  // NYI: UNDER DEVELOPMENT: crash more gracefully
  assert( fieldID != NULL );
  switch (sig[0])
    {
      case 'B':
        (*getJNIEnv())->SetByteField(getJNIEnv(), obj, fieldID, value.b);
        break;
      case 'C':
        (*getJNIEnv())->SetCharField(getJNIEnv(), obj, fieldID, value.c);
        break;
      case 'S':
        (*getJNIEnv())->SetShortField(getJNIEnv(), obj, fieldID, value.s);
        break;
      case 'I':
        (*getJNIEnv())->SetIntField(getJNIEnv(), obj, fieldID, value.i);
        break;
      case 'J':
        (*getJNIEnv())->SetLongField(getJNIEnv(), obj, fieldID, value.j);
        break;
      case 'F':
        (*getJNIEnv())->SetFloatField(getJNIEnv(), obj, fieldID, value.f);
        break;
      case 'D':
        (*getJNIEnv())->SetDoubleField(getJNIEnv(), obj, fieldID, value.d);
        break;
      case 'Z':
        (*getJNIEnv())->SetBooleanField(getJNIEnv(), obj, fieldID, value.z);
        break;
      default:
        (*getJNIEnv())->SetObjectField(getJNIEnv(), obj, fieldID, value.l);
        break;
    }
}


// get a static field in class.
jvalue fzE_get_static_field0(jstring class_name, jstring name, const char *sig)
{
  jclass cl  = (*getJNIEnv())->FindClass(getJNIEnv(), fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  assert( cl != NULL );
  jfieldID fieldID = (*getJNIEnv())->GetStaticFieldID(getJNIEnv(), cl, fzE_java_string_to_modified_utf8(name), sig);
  // NYI: UNDER DEVELOPMENT: crash more gracefully
  assert( fieldID != NULL );
  switch (sig[0])
    {
      case 'B':
        return (jvalue){ .b = (*getJNIEnv())->GetStaticByteField(getJNIEnv(), cl, fieldID) };
      case 'C':
        return (jvalue){ .c = (*getJNIEnv())->GetStaticCharField(getJNIEnv(), cl, fieldID) };
      case 'S':
        return (jvalue){ .s = (*getJNIEnv())->GetStaticShortField(getJNIEnv(), cl, fieldID) };
      case 'I':
        return (jvalue){ .i = (*getJNIEnv())->GetStaticIntField(getJNIEnv(), cl, fieldID) };
      case 'J':
        return (jvalue){ .j = (*getJNIEnv())->GetStaticLongField(getJNIEnv(), cl, fieldID) };
      case 'F':
        return (jvalue){ .f = (*getJNIEnv())->GetStaticFloatField(getJNIEnv(), cl, fieldID) };
      case 'D':
        return (jvalue){ .d = (*getJNIEnv())->GetStaticDoubleField(getJNIEnv(), cl, fieldID) };
      case 'Z':
        return (jvalue){ .z = (*getJNIEnv())->GetStaticBooleanField(getJNIEnv(), cl, fieldID) };
      default:
        return (jvalue){ .l = (*getJNIEnv())->GetStaticObjectField(getJNIEnv(), cl, fieldID) };
    }
}


// set a static field in class.
void fzE_set_static_field0(jstring class_name, jstring name, jvalue value, const char *sig) // TODO:FIXME:
{
  jclass cl  = (*getJNIEnv())->FindClass(getJNIEnv(), fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  assert( cl != NULL );
  jfieldID fieldID = (*getJNIEnv())->GetStaticFieldID(getJNIEnv(), cl, fzE_java_string_to_modified_utf8(name), sig);
  // NYI: UNDER DEVELOPMENT: crash more gracefully
  assert( fieldID != NULL );
  switch (sig[0])
    {
      case 'B':
        (*getJNIEnv())->SetStaticByteField(getJNIEnv(), cl, fieldID, value.b);
        break;
      case 'C':
        (*getJNIEnv())->SetStaticCharField(getJNIEnv(), cl, fieldID, value.c);
        break;
      case 'S':
        (*getJNIEnv())->SetStaticShortField(getJNIEnv(), cl, fieldID, value.s);
        break;
      case 'I':
        (*getJNIEnv())->SetStaticIntField(getJNIEnv(), cl, fieldID, value.i);
        break;
      case 'J':
        (*getJNIEnv())->SetStaticLongField(getJNIEnv(), cl, fieldID, value.j);
        break;
      case 'F':
        (*getJNIEnv())->SetStaticFloatField(getJNIEnv(), cl, fieldID, value.f);
        break;
      case 'D':
        (*getJNIEnv())->SetStaticDoubleField(getJNIEnv(), cl, fieldID, value.d);
        break;
      case 'Z':
        (*getJNIEnv())->SetStaticBooleanField(getJNIEnv(), cl, fieldID, value.z);
        break;
      default:
        (*getJNIEnv())->SetStaticObjectField(getJNIEnv(), cl, fieldID, value.l);
        break;
    }
}

#endif

/*

C11 support is still limited on e.g. macOS etc.

void * fzE_mtx_init()
{
  mtx_t * mtx = fzE_malloc_safe(sizeof(mtx_t));
  return mtx_init(mtx, mtx_plain) == thrd_success
    ? (void *)mtx
    : NULL;
}

int32_t fzE_mtx_lock(void * mtx)
{
  return mtx_lock((mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_mtx_trylock(void * mtx)
{
  return mtx_trylock((mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_mtx_unlock(void * mtx)
{
  return mtx_unlock((mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

void fzE_mtx_destroy(void * mtx)
{
  mtx_destroy((mtx_t *) mtx);
  // NYI: free(mtx)
}

void * fzE_cnd_init()
{
  cnd_t * cnd = fzE_malloc_safe(sizeof(cnd));
  return cnd_init(cnd) == thrd_success
    ? (void *)cnd
    : NULL;
}

int32_t fzE_cnd_signal(void * cnd)
{
  return cnd_signal((cnd_t *) cnd) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_cnd_broadcast(void * cnd)
{
  return cnd_broadcast((cnd_t *) cnd) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_cnd_wait(void * cnd, void * mtx)
{
  return cnd_wait((cnd_t *) cnd, (mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

void fzE_cnd_destroy(void * cnd)
{
  cnd_destroy((cnd_t *) cnd);
  // NYI: free(cnd)
}

*/
