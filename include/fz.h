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

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

void * fzE_malloc_safe(size_t size);

void fzE_memset(void *dest, int ch, size_t sz);

void fzE_memcpy(void *restrict dest, const void *restrict src, size_t sz);

// make directory, return zero on success
int fzE_mkdir(const char *pathname);

// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite);

// unset environment variable, return zero on success
int fzE_unsetenv(const char *name);

void fzE_opendir(const char *pathname, int64_t * result);

char * fzE_readdir(intptr_t * dir);

int fzE_read_dir_has_next(intptr_t * dir);

int fzE_closedir(intptr_t * dir);

// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking);

// helper function to retrieve
// the last error that occurred.
int fzE_net_error();

// fuzion family number -> system family number
int get_family(int family);

// fuzion socket type number -> system socket type number
int get_socket_type(int socktype);

// fuzion protocol number -> system protocol number
int get_protocol(int protocol);

// close a socket descriptor
int fzE_close(int sockfd);

// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol);

// create a new socket and bind to given host:port
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_bind(int family, int socktype, int protocol, char * host, char * port, int64_t * result);

// set the given socket to listening
// backlog = queuelength of pending connections
int fzE_listen(int sockfd, int backlog);

// accept a new connection
// blocks if socket is blocking
int fzE_accept(int sockfd);

// create connection for given parameters
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_connect(int family, int socktype, int protocol, char * host, char * port, int64_t * result);

// get the peer's ip address
// result is the length of the ip address written to buf
// might return useless information when called on udp socket
int fzE_get_peer_address(int sockfd, void * buf);

// get the peer's port
// result is the port number
// might return useless infomrmation when called on udp socket
unsigned short fzE_get_peer_port(int sockfd);

// read up to count bytes bytes from sockfd
// into buf. may block if socket is  set to blocking.
// return -1 on error or number of bytes read
int fzE_read(int sockfd, void * buf, size_t count);

// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_write(int sockfd, const void * buf, size_t count);

// returns -1 on error, size of file in bytes otherwise
long fzE_get_file_size(FILE* file);

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
void * fzE_mmap(FILE * file, uint64_t offset, size_t size, int * result);

// unmap an address that was previously mapped by fzE_mmap
// -1 error, 0 success
int fzE_munmap(void * mapped_address, const int file_size);

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
bool fzE_bitwise_compare_float(float f1, float f2);

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
bool fzE_bitwise_compare_double(double d1, double d2);

/**
 * returns a monotonically increasing timestamp.
 */
uint64_t fzE_nanotime();

/**
 * Sleep for `n` nano seconds.
 */
void fzE_nanosleep(uint64_t n);

/**
 * remove a file or path
 */
int fzE_rm(char * path);

/**
 * Get file status (resolves symbolic links)
 */
int fzE_stat(const char *pathname, int64_t * metadata);

/**
 * Get file status (does not resolve symbolic links)
 */
int fzE_lstat(const char *pathname, int64_t * metadata);

/**
 * Run plattform specific initialisation code
 */
void fzE_init();

/**
 * Start a new thread, returns a pointer to the thread.
 */
int64_t fzE_thread_create(void *(*code)(void *),
                          void *restrict);

/**
 * Join with a running thread.
 */
// NYI add return value
void fzE_thread_join(int64_t thrd);

/**
 * Global lock
 */
void fzE_lock();
void fzE_unlock();

/**
 * @param args array of process + arguments
 *
 * @param argsLen the length of args
 *
 * @param env array of environment variables to pass to the process, e.g. ["PATH=/usr/bin", "VAR1=some_value"]
 *
 * @param envLen the length of env
 *
 * @param result array to be filled with descriptors [process_id, std_in, std_out, std_err]
 *
 * @param args_str the process+arguments as a string
 *
 * @param env_str the environment variables, 0-terminated string of 0-terminated strings, e.g.: "PATH=/usr/bin\0VAR1=some_value\0\0"
 *
 * @return -1 error, 0 success
 */
int fzE_process_create(char * args[], size_t argsLen, char * env[], size_t envLen, int64_t * result, char * args_str, char * env_str);

/**
 * wait for process `p` to exit
 *
 * @return -1 error, >=0 exit code
 */
int32_t fzE_process_wait(int64_t p);

/**
 * read nbytes bytes into `buf` from pipe `desc`.
 *
 * @return -1 error, bytes read on success
 */
int fzE_pipe_read(int64_t desc, char * buf, size_t nbytes);

/**
 * write nbytes bytes from `buf` to pipe `desc`.
 *
 * @return -1 error, bytes written on success
 */
int fzE_pipe_write(int64_t desc, char * buf, size_t nbytes);

/**
 * close a pipe.
 *
 * @return -1 error, 0 success
 */
int fzE_pipe_close(int64_t desc);

/**
 * open a file
 *
 * @param file_name the files name
 *
 * @param open_results [file descriptor, error number]
 *
 * @param mode 0 read, 1 write, 2 append
 *
 */
void fzE_file_open(char * file_name, int64_t * open_results, int8_t mode);


#ifdef FUZION_LINK_JVM

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
void fzE_destroy_jvm();

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

#endif


/**
 * initialize a mutex
 * @return NULL on error or pointer to mutex
 */
void *  fzE_mtx_init     ();
/**
 * lock a mutex, undefined behaviour if mutex already locked by current thread
 * @return -1 on error, 0 on success
 */
int32_t fzE_mtx_lock     (void * mtx);
/**
 * lock a mutex, success if mutex already locked
 * @return -1 on error, 0 on success
 */
int32_t fzE_mtx_trylock  (void * mtx);
/**
 * unlock a mutex, undefined behaviour if mutex not locked by current thread
 * @return -1 on error, 0 on success
 */
int32_t fzE_mtx_unlock   (void * mtx);
/**
 * destroys the mutex
 */
void    fzE_mtx_destroy  (void * mtx);
/**
 * initialize a condition
 * @return NULL on error or pointer to condition
 */
void *  fzE_cnd_init     ();
/**
 * unblocks one thread waiting on this condition
 * @return -1 on error, 0 on success
 */
int32_t fzE_cnd_signal   (void * cnd);
/**
 * unblocks all threads waiting on this condition
 * @return -1 on error, 0 on success
 */
int32_t fzE_cnd_broadcast(void * cnd);
/**
 * blocks thread until signal, broadcast or spurious wakeup
 * @return -1 on error, 0 on success
 */
int32_t fzE_cnd_wait     (void * cnd, void * mtx);
/**
 * destroys the condition
 */
void    fzE_cnd_destroy  (void * cnd);


#endif /* fz.h  */
