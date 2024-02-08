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
 * Source of main include of Fuzion C backend.
 *
 *---------------------------------------------------------------------*/


#ifndef _FUZION_H
#define _FUZION_H 1

#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>     // setenv, unsetenv
#include <sys/stat.h>   // mkdir
#include <sys/types.h>  // mkdir


#if _WIN32

// "For example if you want to use winsock2.h you better make sure
// WIN32_LEAN_AND_MEAN is always defined because otherwise you will
// get conflicting declarations between the WinSock versions."
// https://stackoverflow.com/questions/11040133/what-does-defining-win32-lean-and-mean-exclude-exactly#comment108482188_11040230
#define WIN32_LEAN_AND_MEAN

#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>

#else

#include <sys/socket.h> // socket, bind, listen, accept, connect
#include <sys/ioctl.h>  // ioctl, FIONREAD
#include <netinet/in.h> // AF_INET
#include <poll.h>       // poll
#include <sys/mman.h>   // mmap
#include <fcntl.h>      // fcntl
#include <unistd.h>     // close
#include <netdb.h>      // getaddrinfo

#endif


static inline void *fzE_malloc_safe(size_t size) {
#ifdef GC_H
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


#ifdef FUZION_LINK_JVM

#include <jni.h>

// global instance of the jvm
JavaVM *fzE_jvm              = NULL;
// global instance of the jvm environment
// NYI thread-safety
JNIEnv *fzE_jni_env          = NULL;

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

// initialize the JVM
// executed once at the start of the application
void fzE_init_jvm() {
  JavaVMInitArgs vm_args;

  vm_args.version = JNI_VERSION_10;
  vm_args.nOptions = 0;
  if (JNI_CreateJavaVM(&fzE_jvm, (void **)&fzE_jni_env, &vm_args) != JNI_OK) {
    printf("Failed to start Java VM");
    exit(EXIT_FAILURE);
  }
  fzE_class_float  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Float");
  fzE_class_double = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Double");
  fzE_class_byte  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Byte");
  fzE_class_short  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Short");
  fzE_class_character  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Character");
  fzE_class_integer  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Integer");
  fzE_class_long  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Long");
  fzE_class_boolean  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Boolean");

  fzE_float_valueof   = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_float, "valueOf", "(F)Ljava/lang/Float;");
  fzE_double_valueof = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_double, "valueOf", "(D)Ljava/lang/Double;");
  fzE_byte_valueof  = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_byte, "valueOf", "(B)Ljava/lang/Byte;");
  fzE_short_valueof  = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_short, "valueOf", "(S)Ljava/lang/Short;");
  fzE_character_valueof  = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_character, "valueOf", "(C)Ljava/lang/Character;");
  fzE_integer_valueof  = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_integer, "valueOf", "(I)Ljava/lang/Integer;");
  fzE_long_valueof  = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_long, "valueOf", "(J)Ljava/lang/Long;");
  fzE_boolean_valueof  = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, fzE_class_boolean, "valueOf", "(Z)Ljava/lang/Boolean;");

  fzE_float_value     = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_float, "floatValue", "()F");
  fzE_double_value    = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_double, "doubleValue", "()D");
  fzE_byte_value      = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_byte, "byteValue", "()B");
  fzE_short_value     = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_short, "shortValue", "()S");
  fzE_character_value = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_character, "charValue", "()C");
  fzE_integer_value   = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_integer, "intValue", "()I");
  fzE_long_value      = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_long, "longValue", "()J");
  fzE_boolean_value   = (*fzE_jni_env)->GetMethodID(fzE_jni_env, fzE_class_boolean, "booleanValue", "()Z");
}

// close the JVM.
void fzE_destroy_jvm()
{
  (*fzE_jvm)->DestroyJavaVM(fzE_jvm);
}

// helper function to replace char `find`
// by `replace` in string `str`.
char* fzE_replace_char(const char* str, char find, char replace){
    char * result = fzE_malloc_safe(strlen(str));
    strcpy(result, str);
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
  const jclass cls = (*fzE_jni_env)->GetObjectClass(fzE_jni_env, jstr);
  const jmethodID getBytes = (*fzE_jni_env)->GetMethodID(fzE_jni_env, cls, "getBytes", "(Ljava/lang/String;)[B");
  const jstring charsetName = (*fzE_jni_env)->NewStringUTF(fzE_jni_env, "UTF-8");
  const jbyteArray arr = (jbyteArray) (*fzE_jni_env)->CallObjectMethod(fzE_jni_env, jstr, getBytes, charsetName);
  (*fzE_jni_env)->DeleteLocalRef(fzE_jni_env, charsetName);

  jbyte * bytes = (*fzE_jni_env)->GetByteArrayElements(fzE_jni_env, arr, NULL);
  const jsize length = (*fzE_jni_env)->GetArrayLength(fzE_jni_env, arr);
  char * result = fzE_malloc_safe(length);
  memcpy(result, bytes, length);

  (*fzE_jni_env)->ReleaseByteArrayElements(fzE_jni_env, arr, bytes, JNI_ABORT);
  (*fzE_jni_env)->DeleteLocalRef(fzE_jni_env, arr);

  return result;
}


// convert a jstring to modified utf-8 bytes
const char * fzE_java_string_to_modified_utf8(jstring jstr)
{
  const char * str = (*fzE_jni_env)->GetStringUTFChars(fzE_jni_env, jstr, JNI_FALSE);
  jsize sz = (*fzE_jni_env)->GetStringUTFLength(fzE_jni_env, jstr);
  char * result = fzE_malloc_safe(sz);
  memcpy(result, str, sz+1);
  (*fzE_jni_env)->ReleaseStringUTFChars(fzE_jni_env, jstr, str);
  return result;
}


jvalue fzE_f32_to_java_object(double arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_float, fzE_float_valueof, arg);
}
jvalue fzE_f64_to_java_object(float arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_double, fzE_double_valueof, arg);
}
jvalue fzE_i8_to_java_object(int8_t arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_byte, fzE_byte_valueof, arg);
}
jvalue fzE_i16_to_java_object(int16_t arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_short, fzE_short_valueof, arg);
}
jvalue fzE_u16_to_java_object(uint16_t arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_character, fzE_character_valueof, arg);
}
jvalue fzE_i32_to_java_object(int32_t arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_integer, fzE_integer_valueof, arg);
}
jvalue fzE_i64_to_java_object(int64_t arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_long, fzE_long_valueof, arg);
}
jvalue fzE_bool_to_java_object(bool arg)
{
  return (jvalue) (*fzE_jni_env)->CallStaticObjectMethod(fzE_jni_env, fzE_class_boolean, fzE_boolean_valueof, arg);
}


// convert args that map to java primitives
jvalue *fzE_convert_args(const char *sig, jvalue *args) {
  int idx = 0;
  sig++;
  while (*sig != ')') {
    switch (*sig) {
    case 'F':
      args[idx] = (jvalue) (*fzE_jni_env)->CallFloatMethod(fzE_jni_env, args[idx].l, fzE_float_value);
      idx++;
      break;
    case 'D':
      args[idx] = (jvalue) (*fzE_jni_env)->CallDoubleMethod(fzE_jni_env, args[idx].l, fzE_double_value);
      idx++;
      break;
    case 'B':
      args[idx] = (jvalue) (*fzE_jni_env)->CallByteMethod(fzE_jni_env, args[idx].l, fzE_byte_value);
      idx++;
      break;
    case 'S':
      args[idx] = (jvalue) (*fzE_jni_env)->CallShortMethod(fzE_jni_env, args[idx].l, fzE_short_value);
      idx++;
      break;
    case 'C':
      args[idx] = (jvalue) (*fzE_jni_env)->CallCharMethod(fzE_jni_env, args[idx].l, fzE_character_value);
      idx++;
      break;
    case 'I':
      args[idx] = (jvalue) (*fzE_jni_env)->CallIntMethod(fzE_jni_env, args[idx].l, fzE_integer_value);
      idx++;
      break;
    case 'J':
      args[idx] = (jvalue) (*fzE_jni_env)->CallLongMethod(fzE_jni_env, args[idx].l, fzE_long_value);
      idx++;
      break;
    case 'Z':
      args[idx] = (jvalue) (*fzE_jni_env)->CallBooleanMethod(fzE_jni_env, args[idx].l, fzE_boolean_value);
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


// return result, check for exception
// return exception if there is any
fzE_jvm_result fzE_return_result(jvalue jv)
{
  if ( (*fzE_jni_env)->ExceptionCheck(fzE_jni_env) == JNI_FALSE )
  {
    return (fzE_jvm_result){ .fzTag = 0, .fzChoice = { .v0 = jv } };
  }
  else
  {
    jthrowable exc =  (*fzE_jni_env)->ExceptionOccurred(fzE_jni_env);
    assert (exc != NULL);
    jclass cl  = (*fzE_jni_env)->FindClass(fzE_jni_env, "java/lang/Throwable");
    assert( cl != NULL );
    jmethodID mid = (*fzE_jni_env)->GetMethodID(fzE_jni_env, cl, "getMessage", "()Ljava/lang/String;");
    assert( mid != NULL );
    jstring exc_message = (jstring) (*fzE_jni_env)->CallObjectMethod(fzE_jni_env, exc, mid);
    (*fzE_jni_env)->ExceptionClear(fzE_jni_env);
    return (fzE_jvm_result){ .fzTag = 1, .fzChoice = { .v1 = exc_message } };
  }
}


// call a java constructor
fzE_jvm_result fzE_call_c0(jstring class_name, jstring signature, jvalue *args)
{
  const char * sig = fzE_java_string_to_modified_utf8(signature);
  jclass cl  = (*fzE_jni_env)->FindClass(fzE_jni_env, fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  assert( cl != NULL );
  jmethodID mid = (*fzE_jni_env)->GetMethodID(fzE_jni_env, cl, "<init>", sig);
  assert( mid != NULL );
  jvalue result = (jvalue) (*fzE_jni_env)->NewObjectA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));

  return fzE_return_result(result);
}


// call a java static method
fzE_jvm_result fzE_call_s0(jstring class_name, jstring name, jstring signature, jvalue *args)
{
  const char * sig = fzE_java_string_to_modified_utf8(signature);
  jclass cl  = (*fzE_jni_env)->FindClass(fzE_jni_env, fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  assert( cl != NULL );
  jmethodID mid = (*fzE_jni_env)->GetStaticMethodID(fzE_jni_env, cl, fzE_java_string_to_modified_utf8(name), sig);
  assert( mid != NULL );
  jvalue result;
  switch (sig[strlen(sig)-1])
    {
      case 'B':
        result = (jvalue)  (*fzE_jni_env)->CallStaticByteMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'C':
        result = (jvalue)  (*fzE_jni_env)->CallStaticCharMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'S':
        result = (jvalue)  (*fzE_jni_env)->CallStaticShortMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'I':
        result = (jvalue)  (*fzE_jni_env)->CallStaticIntMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'J':
        result = (jvalue)  (*fzE_jni_env)->CallStaticLongMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'F':
        result = (jvalue)  (*fzE_jni_env)->CallStaticFloatMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'D':
        result = (jvalue)  (*fzE_jni_env)->CallStaticDoubleMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      case 'Z':
        result = (jvalue)  (*fzE_jni_env)->CallStaticBooleanMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
      default:
        result = (jvalue) (*fzE_jni_env)->CallStaticObjectMethodA(fzE_jni_env, cl, mid, fzE_convert_args(sig, args));
    }

  return fzE_return_result(result);
}


// call a java virtual method
fzE_jvm_result fzE_call_v0(jstring class_name, jstring name, jstring signature, jobject thiz, jvalue *args)
{
  const char * sig = fzE_java_string_to_modified_utf8(signature);
  jclass cl  = (*fzE_jni_env)->FindClass(fzE_jni_env, fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  assert( cl != NULL );
  jmethodID mid = (*fzE_jni_env)->GetMethodID(fzE_jni_env, cl, fzE_java_string_to_modified_utf8(name), sig);
  assert( mid != NULL );
  const char * sig2 = sig;
  while (*sig2 != ')') {
    sig2++;
  }
  jvalue result;
  switch (sig2[1])
    {
      case 'B':
        result = (jvalue) (*fzE_jni_env)->CallByteMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'C':
        result = (jvalue) (*fzE_jni_env)->CallCharMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'S':
        result = (jvalue) (*fzE_jni_env)->CallShortMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'I':
        result = (jvalue) (*fzE_jni_env)->CallIntMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'J':
        result = (jvalue) (*fzE_jni_env)->CallLongMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'F':
        result = (jvalue) (*fzE_jni_env)->CallFloatMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'D':
        result = (jvalue) (*fzE_jni_env)->CallDoubleMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      case 'Z':
        result = (jvalue) (*fzE_jni_env)->CallBooleanMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
      default:
        result = (jvalue) (*fzE_jni_env)->CallObjectMethodA(fzE_jni_env, thiz, mid, fzE_convert_args(sig, args));
    }

  return fzE_return_result(result);
}


// convert a 0-terminated utf8-bytes array to a jstring.
jvalue fzE_string_to_java_object(const char * utf8_bytes)
{
  int byte_length = strlen(utf8_bytes);
  // NYI we don't really need 4*byte_length, see modifiedUtf8LengthOfUtf8:
  // https://github.com/openjdk/jdk/blob/eb9e754b3a439cc3ce36c2c9393bc8b250343844/src/java.instrument/share/native/libinstrument/EncodingSupport.c#L98
  char * outstr = malloc(4*byte_length);
  utf8_to_mod_utf8(utf8_bytes, outstr);
  jvalue result = (jvalue) (*fzE_jni_env)->NewStringUTF(fzE_jni_env, outstr);
  free((void *) outstr);
  return result;
}


// test if jobj is null
bool fzE_java_object_is_null(jobject jobj)
{
  return (*fzE_jni_env)->IsSameObject(fzE_jni_env, jobj, NULL);
}


// get length of the jarray
int32_t fzE_array_length(jarray array)
{
  assert (array != NULL);
  return (*fzE_jni_env)->GetArrayLength(fzE_jni_env, array);
}


jvalue fzE_array_to_java_object0(jsize length, jvalue *args, char * element_class_name)
{
  if (strcmp(element_class_name, "bool") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewBooleanArray(fzE_jni_env, length);
    const jboolean f[] = {JNI_FALSE};
    const jboolean t[] = {JNI_TRUE};
    for (int i = 0; i < length; i++)
      {
        (*fzE_jni_env)->SetBooleanArrayRegion(fzE_jni_env, result, i, 1, ((int32_t *)args)[i] ? t : f);
      }
    return (jvalue) result;
  }
  if (strcmp(element_class_name, "i8") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewByteArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetByteArrayRegion(fzE_jni_env, result, 0, length, (jbyte *) args);
    return (jvalue) result;
  }
  else if (strcmp(element_class_name, "i16") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewShortArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetShortArrayRegion(fzE_jni_env, result, 0, length, (jshort *) args);
    return (jvalue) result;
  }
  else if (strcmp(element_class_name, "u16") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewCharArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetCharArrayRegion(fzE_jni_env, result, 0, length, (jchar *) args);
    return (jvalue) result;
  }
  else if (strcmp(element_class_name, "i32") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewIntArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetIntArrayRegion(fzE_jni_env, result, 0, length, (jint *) args);
    return (jvalue) result;
  }
  else if (strcmp(element_class_name, "i64") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewLongArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetLongArrayRegion(fzE_jni_env, result, 0, length, (jlong *) args);
    return (jvalue) result;
  }
  else if (strcmp(element_class_name, "f32") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewFloatArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetFloatArrayRegion(fzE_jni_env, result, 0, length, (jfloat *) args);
    return (jvalue) result;
  }
  else if (strcmp(element_class_name, "f64") == 0 )
  {
    jarray result = (*fzE_jni_env)->NewDoubleArray(fzE_jni_env, length);
    (*fzE_jni_env)->SetDoubleArrayRegion(fzE_jni_env, result, 0, length, (jdouble *) args);
    return (jvalue) result;
  }
  else
  {
    jclass cl = (*fzE_jni_env)->FindClass(fzE_jni_env, element_class_name);
    assert( cl != NULL );
    jobjectArray result = (*fzE_jni_env)->NewObjectArray(fzE_jni_env, length, cl, NULL);
    for (jsize i = 0; i < length; i++)
      {
        (*fzE_jni_env)->SetObjectArrayElement(fzE_jni_env, result, i, ((jobject *) args)[i]);
      }
    return (jvalue) result;
  }
}


// get element in array at index
jvalue fzE_array_get(jarray array, jsize index, const char *sig)
{
  switch (sig[0])
    {
      case 'B':
        return (jvalue) (*fzE_jni_env)->GetByteArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'C':
        return (jvalue) (*fzE_jni_env)->GetCharArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'S':
        return (jvalue) (*fzE_jni_env)->GetShortArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'I':
        return (jvalue) (*fzE_jni_env)->GetIntArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'J':
        return (jvalue) (*fzE_jni_env)->GetLongArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'F':
        return (jvalue) (*fzE_jni_env)->GetFloatArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'D':
        return (jvalue) (*fzE_jni_env)->GetDoubleArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      case 'Z':
        return (jvalue) (*fzE_jni_env)->GetBooleanArrayElements(fzE_jni_env, array, JNI_FALSE)[index];
      default:
        return (jvalue) (*fzE_jni_env)->GetObjectArrayElement(fzE_jni_env, array, index);
    }
}


// get a non-static field on obj.
jvalue fzE_get_field0(jobject obj, jstring name, const char *sig)
{
  jclass cl = (*fzE_jni_env)->GetObjectClass(fzE_jni_env, obj);
  assert( cl != NULL );
  jfieldID fieldID = (*fzE_jni_env)->GetFieldID(fzE_jni_env, cl, fzE_java_string_to_modified_utf8(name), sig);
  assert( fieldID != NULL );
  switch (sig[0])
    {
      case 'B':
        return (jvalue) (*fzE_jni_env)->GetByteField(fzE_jni_env, cl, fieldID);
      case 'C':
        return (jvalue) (*fzE_jni_env)->GetCharField(fzE_jni_env, cl, fieldID);
      case 'S':
        return (jvalue) (*fzE_jni_env)->GetShortField(fzE_jni_env, cl, fieldID);
      case 'I':
        return (jvalue) (*fzE_jni_env)->GetIntField(fzE_jni_env, cl, fieldID);
      case 'J':
        return (jvalue) (*fzE_jni_env)->GetLongField(fzE_jni_env, cl, fieldID);
      case 'F':
        return (jvalue) (*fzE_jni_env)->GetFloatField(fzE_jni_env, cl, fieldID);
      case 'D':
        return (jvalue) (*fzE_jni_env)->GetDoubleField(fzE_jni_env, cl, fieldID);
      case 'Z':
        return (jvalue) (*fzE_jni_env)->GetBooleanField(fzE_jni_env, cl, fieldID);
      default:
        return (jvalue) (*fzE_jni_env)->GetObjectField(fzE_jni_env, cl, fieldID);
    }
}


// get a static field in class.
jvalue fzE_get_static_field0(jstring class_name, jstring name, const char *sig)
{
  jclass cl  = (*fzE_jni_env)->FindClass(fzE_jni_env, fzE_replace_char(fzE_java_string_to_modified_utf8(class_name), '.', '/'));
  assert( cl != NULL );
  jfieldID fieldID = (*fzE_jni_env)->GetStaticFieldID(fzE_jni_env, cl, fzE_java_string_to_modified_utf8(name), sig);
  assert( fieldID != NULL );
  switch (sig[0])
    {
      case 'B':
        return (jvalue) (*fzE_jni_env)->GetStaticByteField(fzE_jni_env, cl, fieldID);
      case 'C':
        return (jvalue) (*fzE_jni_env)->GetStaticCharField(fzE_jni_env, cl, fieldID);
      case 'S':
        return (jvalue) (*fzE_jni_env)->GetStaticShortField(fzE_jni_env, cl, fieldID);
      case 'I':
        return (jvalue) (*fzE_jni_env)->GetStaticIntField(fzE_jni_env, cl, fieldID);
      case 'J':
        return (jvalue) (*fzE_jni_env)->GetStaticLongField(fzE_jni_env, cl, fieldID);
      case 'F':
        return (jvalue) (*fzE_jni_env)->GetStaticFloatField(fzE_jni_env, cl, fieldID);
      case 'D':
        return (jvalue) (*fzE_jni_env)->GetStaticDoubleField(fzE_jni_env, cl, fieldID);
      case 'Z':
        return (jvalue) (*fzE_jni_env)->GetStaticBooleanField(fzE_jni_env, cl, fieldID);
      default:
        return (jvalue) (*fzE_jni_env)->GetStaticObjectField(fzE_jni_env, cl, fieldID);
    }
}

#endif


// make directory, return zero on success
int fzE_mkdir(const char *pathname){
#if _WIN32
  // should we CreateDirectory here?
  return mkdir(pathname);
#else
  return mkdir(pathname, S_IRWXU);
#endif
}



// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite){
#if _WIN32
  // setenv is posix only
  return -1;
#else
  return setenv(name, value, overwrite);
#endif
}



// unset environment variable, return zero on success
int fzE_unsetenv(const char *name){
#if _WIN32
  // unsetenv is posix only
  return -1;
#else
  return unsetenv(name);
#endif
}

// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking)
{
#ifdef _WIN32
  u_long b = blocking;
  return ioctlsocket(sockfd, FIONBIO, &b);
#else
  int flag = blocking == 1
    ? fcntl(sockfd, F_GETFL, 0) | O_NONBLOCK
    : fcntl(sockfd, F_GETFL, 0) & ~O_NONBLOCK;

  return fcntl(sockfd, F_SETFL, flag);
#endif
}

// helper function to retrieve
// the last error that occurred.
int fzE_net_error()
{
#ifdef _WIN32
  return WSAGetLastError();
#else
  return errno;
#endif
}

// fuzion family number -> system family number
int get_family(int family)
{
  return family == 1
    ? AF_UNIX
    : family == 2
    ? AF_INET
    : family == 10
    ? AF_INET6
    : -1;
}

// fuzion socket type number -> system socket type number
int get_socket_type(int socktype)
{
  return socktype == 1
    ? SOCK_STREAM
    : socktype == 2
    ? SOCK_DGRAM
    : socktype == 3
    ? SOCK_RAW
    : -1;
}

// fuzion protocol number -> system protocol number
int get_protocol(int protocol)
{
  return protocol == 6
    ? IPPROTO_TCP
    : protocol == 17
    ? IPPROTO_UDP
    : protocol == 0
    ? IPPROTO_IP
    : protocol == 41
    ? IPPROTO_IPV6
    : -1;
}

// close a socket descriptor
int fzE_close(int sockfd)
{
#ifdef _WIN32
  closesocket(sockfd);
  return fzE_net_error();
#else
  return ( close(sockfd) == - 1 )
    ? fzE_net_error()
    : 0;
#endif
}

// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol){
#ifdef _WIN32
  WSADATA wsaData;
  if ( WSAStartup(MAKEWORD(2,2), &wsaData) != 0 ) {
    return -1;
  }
#endif
  return socket(get_family(family), get_socket_type(type), get_protocol(protocol));
}


// get addrinfo structure used for binding/connection of a socket.
int fzE_getaddrinfo(int family, int socktype, int protocol, int flags, char * host, char * port, struct addrinfo ** result){
  struct addrinfo hints;

#ifdef _WIN32
  ZeroMemory(&hints, sizeof(hints));
#else
  memset(&hints, 0, sizeof hints);
#endif
  hints.ai_family = get_family(family);
  hints.ai_socktype = get_socket_type(socktype);
  hints.ai_protocol = get_protocol(protocol);
  hints.ai_flags = flags;

  return getaddrinfo(host, port, &hints, result);
}


// create a new socket and bind to given host:port
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_bind(int family, int socktype, int protocol, char * host, char * port, int64_t * result){
  result[0] = fzE_socket(family, socktype, protocol);
  if (result[0] == -1)
  {
    result[0] = fzE_net_error();
    return -1;
  }
  struct addrinfo *addr_info = NULL;
  int addrRes = fzE_getaddrinfo(family, socktype, protocol, AI_PASSIVE, host, port, &addr_info);
  if (addrRes != 0)
  {
    fzE_close(result[0]);
    result[0] = addrRes;
    return -1;
  }
  int bind_res = bind(result[0], addr_info->ai_addr, (int)addr_info->ai_addrlen);

  if(bind_res == -1)
  {
    fzE_close(result[0]);
    result[0] = fzE_net_error();
    return -1;
  }
  freeaddrinfo(addr_info);
  return bind_res;
}

// set the given socket to listening
// backlog = queuelength of pending connections
int fzE_listen(int sockfd, int backlog){
  return ( listen(sockfd, backlog) == -1 )
    ? fzE_net_error()
    : 0;
}

// accept a new connection
// blocks if socket is blocking
int fzE_accept(int sockfd){
  return accept(sockfd, NULL, NULL);
}


// create connection for given parameters
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_connect(int family, int socktype, int protocol, char * host, char * port, int64_t * result){
  // get socket
  result[0] = fzE_socket(family, socktype, protocol);
  if (result[0] == -1)
  {
    result[0] = fzE_net_error();
    return -1;
  }
  struct addrinfo *addr_info = NULL;
  int addrRes = fzE_getaddrinfo(family, socktype, protocol, 0, host, port, &addr_info);
  if (addrRes != 0)
  {
    fzE_close(result[0]);
    result[0] = addrRes;
    return -1;
  }
  int con_res = connect(result[0], addr_info->ai_addr, addr_info->ai_addrlen);
  if(con_res == -1)
  {
    // NYI do we want to try another address in addr_info->ai_next?
    fzE_close(result[0]);
    result[0] = fzE_net_error();
  }
  freeaddrinfo(addr_info);
  return con_res;
}


// get the peer's ip address
// result is the length of the ip address written to buf
// might return useless information when called on udp socket
int fzE_get_peer_address(int sockfd, void * buf) {
  struct sockaddr_storage peeraddr;
  socklen_t peeraddrlen = sizeof(peeraddr);
  int res = getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen);
  if (peeraddr.ss_family == AF_INET) {
    memcpy(buf, &(((struct sockaddr_in *)&peeraddr)->sin_addr.s_addr), 4);
    return 4;
  } else if (peeraddr.ss_family == AF_INET6) {
    memcpy(buf, &(((struct sockaddr_in6 *)&peeraddr)->sin6_addr.s6_addr), 16);
    return 16;
  } else {
    return -1;
  }
  return -1;
}


// get the peer's port
// result is the port number
// might return useless infomrmation when called on udp socket
unsigned short fzE_get_peer_port(int sockfd) {
  struct sockaddr_storage peeraddr;
  socklen_t peeraddrlen = sizeof(peeraddr);
  int res = getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen);
  if (peeraddr.ss_family == AF_INET) {
    return ntohs(((struct sockaddr_in *)&peeraddr)->sin_port);
  } else if (peeraddr.ss_family == AF_INET6) {
    return ntohs(((struct sockaddr_in6 *)&peeraddr)->sin6_port);
  } else {
    return 0;
  }
}


// read up to count bytes bytes from sockfd
// into buf. may block if socket is  set to blocking.
// return -1 on error or number of bytes read
int fzE_read(int sockfd, void * buf, size_t count){
#ifdef _WIN32
  int rec_res = recvfrom( sockfd, buf, count, 0, NULL, NULL );
  if (rec_res == -1)
  {
    // silently discard rest to
    // match behaviour on linux
    return fzE_net_error() == WSAEMSGSIZE
      ? count
      : rec_res;
  }
  return rec_res;
#else
  return recvfrom( sockfd, buf, count, 0, NULL, NULL );
#endif
}

// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_write(int sockfd, const void * buf, size_t count){
return ( sendto( sockfd, buf, count, 0, NULL, 0 ) == -1 )
  ? fzE_net_error()
  : 0;
}

#ifdef _WIN32
// for 64-bit offset returns the 32 highest bits as a DWORD
DWORD high_word(off_t value) {
  return sizeof(off_t) == 4
    ? 0
    : (DWORD)(value >> 32);
}
// for 64-bit offset returns the 32 lowest bits as a DWORD
DWORD low_word(off_t value) {
  return (DWORD)(value & ((1ULL << 32) - 1));
}
#endif


// returns -1 on error, size of file in bytes otherwise
long fzE_get_file_size(FILE* file) {
  // store current pos
  long cur_pos = ftell(file);
  if(cur_pos == -1 || fseek(file, 0, SEEK_END) == -1){
    return -1;
  }

  long size = ftell(file);

  // reset seek position
  fseek(file, cur_pos, SEEK_SET);

  return size;
}

/*
 * create a memory map of a file at an offset.
 * unix:    the offset must be a multiple of the page size, usually 4096 bytes.
 * windows: the offset must be a multiple of the memory allocation granularity, usually 65536 bytes
 *          see also, https://devblogs.microsoft.com/oldnewthing/20031008-00/?p=42223
 *
 * returns:
 *   - error   :  result[0]=-1 and NULL
 *   - success :  result[0]=0  and an address where the file was mapped to
 */
void * fzE_mmap(FILE * file, off_t offset, size_t size, int * result) {

  if (fzE_get_file_size(file) < (offset + size)){
    result[0] = -1;
    return NULL;
  }

#ifdef _WIN32
  HANDLE file_handle = (HANDLE)_get_osfhandle(fileno(file));

  /* "If dwMaximumSizeLow and dwMaximumSizeHigh are 0 (zero), the maximum size of the file mapping
      object is equal to the current size of the file that hFile identifies.
      An attempt to map a file with a length of 0 (zero) fails with an error code
      of ERROR_FILE_INVALID. Applications should test for files with a length of 0 (zero) and reject those files."
  */
  HANDLE file_mapping_handle = CreateFileMapping(file_handle, NULL, PAGE_READWRITE, 0, 0, NULL);
  if (file_mapping_handle == NULL) {
    result[0] = -1;
    return NULL;
  }

  void * mapped_address = MapViewOfFile(file_mapping_handle, FILE_MAP_ALL_ACCESS, high_word(offset), low_word(offset), size);
  if (mapped_address == NULL) {
    CloseHandle(file_mapping_handle);
    result[0] = -1;
    return NULL;
  }
  result[0] = 0;
  return mapped_address;
#else
  int file_descriptor = fileno(file);

  if (file_descriptor == -1) {
    result[0] = -1;
    return NULL;
  }

  void * mapped_address = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, file_descriptor, offset);
  if (mapped_address == MAP_FAILED) {
    result[0] = -1;
    return NULL;
  }
  result[0] = 0;
  return mapped_address;
#endif
}


// unmap an address that was previously mapped by fzE_mmap
// -1 error, 0 success
int fzE_munmap(void * mapped_address, const int file_size){
#ifdef _WIN32
  return UnmapViewOfFile(mapped_address)
    ? 0
    : -1;
#else
  return munmap(mapped_address, file_size);
#endif
}


/**
 * Perform bitwise comparison of two float values. This is used by
 * concur.atmic.compare_and_swap/set to compare floats. In particular, this
 * results is unequality of +0 and -0 and equality of NaN unless undefined bits
 * differ, etc.
 *
 * NYI: CLEANUP #2122: Move impleementation to fz.c / fzlib.o or similar!
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
 * NYI: CLEANUP: #2122: Move impleementation to fz.c / fzlib.o or similar!
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


#endif /* fz.h  */
