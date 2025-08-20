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


static_assert(sizeof(int)    == 4, "implementation restriction, int must be 4 bytes");

// returns the latest error number of
// the current thread
int fzE_last_error(void);

// make directory, return zero on success
int fzE_mkdir(const char *pathname);

// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite);

// unset environment variable, return zero on success
int fzE_unsetenv(const char *name);

// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking);

// close a socket descriptor
int fzE_close(int sockfd);

// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol);

// set the given socket to listening
// backlog = queuelength of pending connections
int fzE_listen(int sockfd, int backlog);

// accept a new connection
// blocks if socket is blocking
int fzE_accept(int sockfd);

// get the peer's ip address
// result is the length of the ip address written to buf
// might return useless information when called on udp socket
int fzE_get_peer_address(int sockfd, void * buf);

// get the peer's port
// result is the port number
// might return useless infomrmation when called on udp socket
unsigned short fzE_get_peer_port(int sockfd);

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
 * remove a file or path
 */
int fzE_rm(char * path);

/**
 * Run plattform specific initialisation code
 */
void fzE_init(void);

/**
 * Global lock
 */
void fzE_lock(void);
void fzE_unlock(void);

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
 * initialize a mutex
 * @return NULL on error or pointer to mutex
 */
void *  fzE_mtx_init     (void);

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
 * destroys the condition
 */
void    fzE_cnd_destroy  (void * cnd);

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
