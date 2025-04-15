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
 * Source of fz_jni include.
 *
 *---------------------------------------------------------------------*/


#ifndef _FUZION_JNI
#define _FUZION_JNI 1

#include <stdint.h>
#include <stdbool.h>
#include <jni.h>


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

// initialize the JVM
// executed once at the start of the application
void fzE_create_jvm(char * option_string);

// close the JVM.
void fzE_destroy_jvm(void);

// convert a jstring to a utf-8 byte array
const char * fzE_java_string_to_utf8_bytes(jstring jstr);

jvalue fzE_f32_to_java_object(double arg);
jvalue fzE_f64_to_java_object(float arg);
jvalue fzE_i8_to_java_object(int8_t arg);
jvalue fzE_i16_to_java_object(int16_t arg);
jvalue fzE_u16_to_java_object(uint16_t arg);
jvalue fzE_i32_to_java_object(int32_t arg);
jvalue fzE_i64_to_java_object(int64_t arg);
jvalue fzE_bool_to_java_object(bool arg);

// call a java constructor
fzE_jvm_result fzE_call_c0(jstring class_name, jstring signature, jvalue *args);
// call a java static method
fzE_jvm_result fzE_call_s0(jstring class_name, jstring name, jstring signature, jvalue *args);
// call a java virtual method
fzE_jvm_result fzE_call_v0(jstring class_name, jstring name, jstring signature, jobject thiz, jvalue *args);

// convert a 0-terminated utf8-bytes array to a jstring.
jvalue fzE_string_to_java_object(const void * utf8_bytes, int byte_length);

// test if jobj is null
bool fzE_java_object_is_null(jobject jobj);

// get length of the jarray
int32_t fzE_array_length(jarray array);
jvalue fzE_array_to_java_object0(jsize length, jvalue *args, const char * element_class_name);
// get element in array at index
jvalue fzE_array_get(jarray array, jsize index, const char *sig);

// get a non-static field on obj.
jvalue fzE_get_field0(jobject obj, jstring name, const char *sig);
// set a non-static field on obj.
jvalue fzE_set_field0(jobject obj, jstring name, jobject value, const char *sig);
// get a static field in class.
jvalue fzE_get_static_field0(jstring class_name, jstring name, const char *sig);
// set a static field in class.
jvalue fzE_set_static_field0(jstring class_name, jstring name, jobject value, const char *sig);

#endif /* fz_jni.h  */
