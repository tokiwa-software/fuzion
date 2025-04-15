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
 * Source of main include of the Fuzion runtime.
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


void * fzE_malloc_safe(size_t size);

void fzE_mem_zero(void *dest, size_t sz);

void fzE_memcpy(void *restrict dest, const void *restrict src, size_t sz);

// returns the latest error number of
// the current thread
int fzE_last_error(void);

// NYI: UNDER DEVELOPMENT: fzE_last_error_as_string, returning the error as a human readable string

// make directory, return zero on success
int fzE_mkdir(const char *pathname);

// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite);

// unset environment variable, return zero on success
int fzE_unsetenv(const char *name);

// on error result[0]!=0
// returns pointer to directory
void * fzE_opendir(const char *pathname, int64_t * result);

// NYI: UNDER DEVELOPMENT
// returns -1 on error, 0 on end reached, length of result on success
// result contains the bytes of the string, NYI: UNDER DEVELOPMENT (max 1024)
int fzE_dir_read(intptr_t * dir, void * result);

// close the dir
// return 0 if successful, -1 if not
int fzE_dir_close(intptr_t * dir);

// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking);

// close a socket descriptor
int fzE_close(int sockfd);

// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol);

// create a new socket and bind to given host:port
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_bind(int family, int socktype, int protocol, char * host, char * port, int32_t * result);

// set the given socket to listening
// backlog = queuelength of pending connections
int fzE_listen(int sockfd, int backlog);

// accept a new connection
// blocks if socket is blocking
int fzE_accept(int sockfd);

// create connection for given parameters
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_connect(int family, int socktype, int protocol, char * host, char * port, int32_t * result);

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
int fzE_socket_read(int sockfd, void * buf, size_t count);

// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_socket_write(int sockfd, const void * buf, size_t count);


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
void * fzE_mmap(void * file, uint64_t offset, size_t size, int * result);

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
uint64_t fzE_nanotime(void);

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
void fzE_init(void);

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
 * @param open_results [error number]
 *
 * @param mode 0 read, 1 write, 2 append
 *
 */
void * fzE_file_open(char * file_name, int64_t * open_results, int8_t mode);

/**
 * @param file the pointer to the file
 * @param buf pointer to a byte array
 * @param size the size of buf in bytes
 * @return amounts of bytes read, or negative number on error
 */
int32_t fzE_file_read(void * file, void * buf, int32_t size);

/**
 * @param file the pointer to the file
 * @param buf pointer to a byte array
 * @param size the size of buf in bytes
 * @return amounts of bytes writter, or negative number on error
 */
int32_t fzE_file_write(void * file, void * buf, int32_t size);

/**
 * @param oldpath
 * @param newpath
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_move(const char *oldpath, const char *newpath);

/**
 * @param file pointer to the open file
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_close(void * file);

/**
 * @param file pointer to the open file
 * @param offset amount of bytes to seek forward
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_seek(void * file, int64_t offset);

/**
 * @param file pointer to the open file
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
 * @return 0 on success, -1 on error
 */
int32_t fzE_file_flush(void * file);


/**
 * @param addr pointer to an address in memory
 * @param idx  the index at where to do the get
 * @return the addr[idx]
 */
uint8_t fzE_mapped_buffer_get(void * addr, int64_t idx);

/**
 * @param addr pointer to an address in memory
 * @param idx  the index at where to do the set
 * @param x    the byte to set
 */
void    fzE_mapped_buffer_set(void * addr, int64_t idx, uint8_t x);

/**
 * initialize a mutex
 * @return NULL on error or pointer to mutex
 */
void *  fzE_mtx_init     (void);
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
void *  fzE_cnd_init     (void);
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


/**
 * get a unique id > 0
 */
uint64_t fzE_unique_id(void);

/**
 * result is a 32-bit array
 *
 * result[0] = year
 * result[1] = day_in_year
 * result[2] = hour
 * result[3] = min
 * result[4] = sec
 * result[5] = nanosec;
 */
void fzE_date_time(void * result);


// returns NULL pointer
void * fzE_null(void);

// returns 0 if p is NULL
int fzE_is_null(void * p);


#endif /* fz.h  */
