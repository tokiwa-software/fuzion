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
#include <stdbool.h>
#include <assert.h>

static_assert(sizeof(int)    == 4, "implementation restriction, int must be 4 bytes");
static_assert(sizeof(size_t) == 8, "implementation restriction, size_t must be 8 bytes");


/**
 * allocates memory and terminates the application
 * if the requested allocation is not possible.
 *
 * NYI: UNDER DEVELOPMENT: change the behaviour when allocation is not possible
 *
 * @return pointer to allocated memory of `size`-bytes.
 */
void * fzE_malloc_safe(size_t size);


/**
 * explicitly free allocated memory.
 *
 * NOTE: must only be called once per ptr!
 */
void fzE_free(void * ptr);


/**
 * Securely zero memory.
 * Specifically does not use memset since this can
 * be optimized away.
 *
 * @param dest pointer to the memory we want to zero.
 *
 * @param sz the size of the memory in bytes to be zeroed.
 */
void fzE_mem_zero_secure(void *dest, size_t sz);

void fzE_memcpy(void *restrict dest, const void *restrict src, size_t sz);

/**
 * returns the latest error code of
 * the current thread
 */
int64_t fzE_last_error(void);

// NYI: UNDER DEVELOPMENT: fzE_last_error_as_string, returning the error as a human readable string

/**
 * make directory
 *
 * @param pathname a pointer to zero terminated utf8 bytes.
 *
 * @return 0 on success, -1 on error.
 */
int fzE_mkdir(const char *pathname);

/**
 * open a directory for traversal.
 *
 * @param pathname a pointer to zero terminated utf8 bytes.
 *
 * @param result pointer to memory
 *    on success result[0]=0
 *    on error result[0]=-1
 *
 * @return pointer to directory
 *         NOTE: needs to be closed via fzE_dir_close
 */
void * fzE_opendir(const char *pathname, int64_t * result);

/**
 * read directory
 *
 * @param dir pointer to a directory
 *
 * NYI: UNDER DEVELOPMENT (max 1024)
 * @param result pointer to 1024-bytes of memory, contains the utf8 bytes if successful.
 *
 * @return -1 on error, 0 on end reached, length of result on success
 */
int fzE_dir_read(intptr_t * dir, int8_t * result);

/**
 * close directory
 *
 * @param dir pointer to a directory
 *
 * @return 0 if successful, -1 if not
 */
int fzE_dir_close(intptr_t * dir);

/**
 * set the socket descriptor sockfd
 * to (none_)blocking mode.
 *
 * @param blocking
 *          0 = blocking
 *          1 = none_blocking
 *
 * @return 0 if successful, -1 if not
 */
int fzE_set_blocking(int sockfd, int blocking);

/**
 * close a socket
 *
 * @return 0 if successful, -1 if not
 */
int fzE_close(int sockfd);

/**
 * create a new socket
 *
 * @param family
 *      ipv4  => 2
 *      ipv6  => 10
 *
 * @param type
 *      stream => 1
 *      datagram => 2
 *
 * @param protocol
 *      tcp  => 6
 *      udp  => 17
 *
 * @return 0 if successful, -1 if not
 */
int fzE_socket(int family, int type, int protocol);

/**
 * create a new socket and bind to given host:port
 *
 * @param family
 *      address family (e.g., ipv4 => 2)
 *
 * @param socktype
 *      socket type (e.g., stream => 1, datagram => 2)
 *
 * @param protocol
 *      protocol to be used (e.g., tcp => 6, udp => 17)
 *
 * @param host
 *      hostname or ip address to bind to
 *
 * @param port
 *      port to bind to
 *
 * @param result
 *      result[0] contains either an error code or a socket descriptor
 *      -1 on error, 0 on success
 *
 * @return 0 if successful, -1 if not
 */
int fzE_bind(int family, int socktype, int protocol, char * host, char * port, int32_t * result);

/**
 * set the given socket to listening
 *
 * @param sockfd
 *      socket file descriptor
 *
 * @param backlog
 *      queue length of pending connections
 *
 * @return 0 if successful, -1 if not
 */
int fzE_listen(int sockfd, int backlog);

/**
 * accept a new connection (blocks if socket is set to blocking)
 *
 * @param sockfd
 *      listening socket file descriptor
 *
 * @return new socket descriptor for the connection, or -1 on error
 */
int fzE_accept(int sockfd);

/**
 * create connection for given parameters
 *
 * @param family
 *      address family (e.g., ipv4 => 2)
 *
 * @param socktype
 *      socket type (e.g., stream => 1, datagram => 2)
 *
 * @param protocol
 *      protocol to be used (e.g., tcp => 6, udp => 17)
 *
 * @param host
 *      hostname or ip address to connect to
 *
 * @param port
 *      port to connect to
 *
 * @param result
 *      result[0] contains either an error code or a socket descriptor
 *      -1 on error, 0 on success
 *
 * @return 0 if successful, -1 if not
 */
int fzE_connect(int family, int socktype, int protocol, char * host, char * port, int32_t * result);

/**
 * get the peer's ip address
 *
 * @param sockfd
 *      connected socket file descriptor
 *
 * @param buf
 *      buffer to store the ip address
 *
 * @return length of the ip address written to buf
 *         may return invalid info when called on a udp socket
 */
int fzE_get_peer_address(int sockfd, void * buf);

/**
 * get the peer's port
 *
 * @param sockfd
 *      connected socket file descriptor
 *
 * @return the port number
 *         may return invalid info when called on a udp socket
 */
unsigned short fzE_get_peer_port(int sockfd);

/**
 * read up to count bytes from sockfd into buf
 *
 * @param sockfd
 *      socket file descriptor to read from
 *
 * @param buf
 *      buffer to store read data
 *
 * @param count
 *      maximum number of bytes to read
 *
 * @return number of bytes read, or -1 on error
 *         may block if socket is set to blocking
 */
int fzE_socket_read(int sockfd, void * buf, size_t count);

/**
 * write buf to sockfd
 *
 * @param sockfd
 *      socket file descriptor to write to
 *
 * @param buf
 *      buffer containing data to write
 *
 * @param count
 *      number of bytes to write
 *
 * @return 0 on success, or error code
 *         may block if socket is set to blocking
 */
int fzE_socket_write(int sockfd, const void * buf, size_t count);


/**
 * create a memory map of a file at an offset.
 * unix:    the offset must be a multiple of the page size, usually 4096 bytes.
 * windows: the offset must be a multiple of the memory allocation granularity, usually 65536 bytes
 *          see also, https://devblogs.microsoft.com/oldnewthing/20031008-00/?p=42223
 *
 * @return
 *   - error   :  result[0]=-1 and NULL
 *   - success :  result[0]=0  and an address where the file was mapped to
 * NOTE: needs to be unmapped via fzE_munmap
 */
void * fzE_mmap(void * file, uint64_t offset, size_t size, int * result);

/**
 * unmap an address that was previously mapped by fzE_mmap
 *
 * @return -1 error, 0 success
 */
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
 * @return a monotonically increasing timestamp.
 */
uint64_t fzE_nanotime(void);

/**
 * Sleep for at least `n` nano seconds.
 *
 * @param n the amount of nano seconds to sleep for.
 */
void fzE_nanosleep(uint64_t n);

/**
 * remove a file or path
 *
 * @param path a pointer to zero terminated utf8 bytes.
 */
int fzE_rm(char * path);

/**
 * Get file status (resolves symbolic links)
 *
 * @param pathname a pointer to zero terminated utf8 bytes.
 */
int fzE_stat(const char *pathname, int64_t * metadata);

/**
 * Get file status (does not resolve symbolic links)
 *
 * @param pathname a pointer to zero terminated utf8 bytes.
 */
int fzE_lstat(const char *pathname, int64_t * metadata);

/**
 * Run plattform specific initialisation code
 */
void fzE_init(void);

/**
 * Start a new thread, returns a pointer to the thread.
 */
void * fzE_thread_create(void *(*code)(void *),
                          void *restrict);

/**
 * Join with a running thread.
 */
// NYI: UNDER DEVELOPMENT:  add return value
void fzE_thread_join(void * thrd);

/**
 * Global lock
 *
 * This is used in several cases:
 *
 * - to implement compare_and_swap/set/exchange for values that are larger than
 *     what atomic_compare_* usually supports.
 * - Prevent leaking of file and other descriptors when starting processes.
 *     see also comments in fzE_process_create
 *
 */
void fzE_lock(void);
void fzE_unlock(void);

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
 * @return -1 error, 0 success
 */
int fzE_process_create(char * args[], size_t argsLen, char * env[], size_t envLen, int64_t * result);

/**
 * wait for process `p` to exit
 *
 * @return -1 error, >=0 exit code
 */
int64_t fzE_process_wait(int64_t p);

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
 * the mode in which to open file
 * NYI: UNDER DEVELOPMENT: maybe flag is the better term?,
 *                         missing support for O_CREAT, O_SYNC etc.
 *                         Might be better to keep fzE_file_open simple and
 *                         provide some kind of fzE_fctln?
 */
typedef enum file_open_mode {
  FZ_FILE_MODE_READ = 0,
  FZ_FILE_MODE_WRITE = 1,
  FZ_FILE_MODE_APPEND = 2
}file_open_mode;

/**
 * open a file
 *
 * @param file_name the files name
 *
 * @param open_results [error number]
 *
 * @param mode 0 read, 1 write, 2 append
 *
 * @return pointer to the open file or undefined on error.
 *         NOTE: the file needs to closed again via fzE_file_close.
 */
void * fzE_file_open(char * file_name, int64_t * open_results, file_open_mode mode);

/**
 * @param file the pointer to the file
 *
 * @param buf pointer to a byte array
 *
 * @param size the size of buf in bytes
 *
 * @return amounts of bytes read, or negative number on error
 */
int32_t fzE_file_read(void * file, void * buf, int32_t size);

/**
 * @param file the pointer to the file
 *
 * @param buf pointer to a byte array
 *
 * @param size the size of buf in bytes
 *
 * @return amounts of bytes writter, or negative number on error
 */
int32_t fzE_file_write(void * file, void * buf, int32_t size);

/**
 * @param oldpath
 *
 * @param newpath
 *
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_move(const char *oldpath, const char *newpath);

/**
 * @param file pointer to the open file
 *
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_close(void * file);

/**
 * @param file pointer to the open file
 *
 * @param offset amount of bytes to seek forward
 *
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_seek(void * file, int64_t offset);

/**
 * @param file pointer to the open file
 *
 * @return -1 on error, the (byte-)position in the file
 */
int64_t fzE_file_position(void * file);

/**
 * @return the pointer to handle/FILE of stdin
 */
void *  fzE_file_stdin(void);

/**
 * @return the pointer to handle/FILE of stdout
 */
void *  fzE_file_stdout(void);

/**
 * @return the pointer to handle/FILE of stderr
 */
void *  fzE_file_stderr(void);

/**
 * flush user-space buffers for file
 *
 * @param file pointer the the opened file
 *
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_flush(void * file);


/**
 * @param addr pointer to an address in memory
 *
 * @param idx  the index at where to do the get
 *
 * @return the addr[idx]
 */
uint8_t fzE_mapped_buffer_get(void * addr, int64_t idx);

/**
 * @param addr pointer to an address in memory
 *
 * @param idx  the index at where to do the set
 *
 * @param x    the byte to set
 */
void    fzE_mapped_buffer_set(void * addr, int64_t idx, uint8_t x);


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
    jstring v1; // NYI: UNDER DEVELOPMENT: should probably better be jthrowable
  }fzChoice;
};

// initialize the JVM
// executed once at the start of the application
int32_t fzE_create_jvm(void * options, int32_t len);

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
void fzE_set_field0(jobject obj, jstring name, jvalue value, const char *sig);
// get a static field in class.
jvalue fzE_get_static_field0(jstring class_name, jstring name, const char *sig);
// set a static field in class.
void fzE_set_static_field0(jstring class_name, jstring name, jvalue value, const char *sig);

#endif


/**
 * initialize a mutex
 *
 * @return NULL on error or pointer to mutex
 *         NOTE: eventually needs to be destroyed via fzE_mtx_destroy.
 */
void *  fzE_mtx_init     (void);

/**
 * lock a mutex, undefined behaviour if mutex already locked by current thread
 *
 * @param mtx pointer to a mutex
 *
 * @return -1 on error, 0 on success
 */
int32_t fzE_mtx_lock     (void * mtx);

/**
 * lock a mutex, success if mutex already locked
 *
 * @param mtx pointer to a mutex
 *
 * @return -1 on error, 0 on success
 */
int32_t fzE_mtx_trylock  (void * mtx);

/**
 * unlock a mutex, undefined behaviour if mutex not locked by current thread
 *
 * @param mtx pointer to a mutex
 *
 * @return -1 on error, 0 on success
 */
int32_t fzE_mtx_unlock   (void * mtx);

/**
 * destroys the mutex
 *
 * @param mtx pointer to a mutex
 */
void    fzE_mtx_destroy  (void * mtx);

/**
 * initialize a condition
 *
 * @return NULL on error or pointer to condition
 *         NOTE: eventually needs to be destroyed via fzE_cnd_destroy.
 */
void *  fzE_cnd_init     (void);

/**
 * unblocks one thread waiting on this condition
 *
 * @param cnd pointer to a condition
 *
 * @return -1 on error, 0 on success
 */
int32_t fzE_cnd_signal   (void * cnd);

/**
 * unblocks all threads waiting on this condition
 *
 * @param cnd pointer to a condition
 *
 * @return -1 on error, 0 on success
 */
int32_t fzE_cnd_broadcast(void * cnd);

/**
 * blocks thread until signal, broadcast or spurious wakeup
 *
 * @param cnd pointer to a condition
 *
 * @param mtx pointer to a mutex
 *
 * @return -1 on error, 0 on success
 */
int32_t fzE_cnd_wait     (void * cnd, void * mtx);

/**
 * destroys the condition
 *
 * @param cnd to the condition to be destroyed. Must not be NULL.
 *
 */
void    fzE_cnd_destroy  (void * cnd);


/**
 * get an id that is guaranteed to
 * be unique for an execution of this program.
 */
uint64_t fzE_unique_id(void);

/**
 * @param result a pointer to 7 bytes of memory.
 *
 * result[0] = year
 * result[1] = month
 * result[2] = day_in_month
 * result[3] = hour
 * result[4] = min
 * result[5] = sec
 * result[6] = nanosec;
 */
void fzE_date_time(int32_t * result);


/**
 * @return the NULL pointer
 */
void * fzE_null(void);

/**
 * @param p a pointer
 *
 * @return 0 if p is NULL, -1 otherwise
 */
int fzE_is_null(void * p);

int fzE_send_signal(int64_t pid, int sig);

int32_t fzE_path_max(void);

int64_t fzE_page_size(void);

int64_t fzE_mmap_offset_multiple(void);

int fzE_cwd(void * buf, size_t size);

#endif /* fz.h  */
