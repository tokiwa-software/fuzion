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
 * Source of posix code of Fuzion C backend.
 *
 *---------------------------------------------------------------------*/

#define _POSIX_C_SOURCE 200809L

#ifdef GC_THREADS
#include <gc.h>
#endif

#include <stdio.h>
#include <stdlib.h>     // setenv, unsetenv
#include <errno.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <sys/stat.h>   // mkdir
#include <sys/types.h>  // mkdir
#include <sys/socket.h> // socket, bind, listen, accept, connect
#include <sys/ioctl.h>  // ioctl, FIONREAD
#include <netinet/in.h> // AF_INET
#include <poll.h>       // poll
#include <sys/mman.h>   // mmap
#include <fcntl.h>      // fcntl
#include <unistd.h>     // close
#include <netdb.h>      // getaddrinfo
#include <time.h>
#include <assert.h>
#ifdef FUZION_ENABLE_THREADS
#include <pthread.h>
#endif

// make directory, return zero on success
int fzE_mkdir(const char *pathname){
  return mkdir(pathname, S_IRWXU);
}


// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite){
  return setenv(name, value, overwrite);
}


// unset environment variable, return zero on success
int fzE_unsetenv(const char *name){
  return unsetenv(name);
}


// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking)
{
  int flag = blocking == 1
    ? fcntl(sockfd, F_GETFL, 0) | O_NONBLOCK
    : fcntl(sockfd, F_GETFL, 0) & ~O_NONBLOCK;

  return fcntl(sockfd, F_SETFL, flag);
}


// helper function to retrieve
// the last error that occurred.
int fzE_net_error()
{
  return errno;
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
  return ( close(sockfd) == - 1 )
    ? fzE_net_error()
    : 0;
}


// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol){
  return socket(get_family(family), get_socket_type(type), get_protocol(protocol));
}


// get addrinfo structure used for binding/connection of a socket.
int fzE_getaddrinfo(int family, int socktype, int protocol, int flags, char * host, char * port, struct addrinfo ** result){
  struct addrinfo hints;

  memset(&hints, 0, sizeof hints);

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
  return recvfrom( sockfd, buf, count, 0, NULL, NULL );
}


// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_write(int sockfd, const void * buf, size_t count){
return ( sendto( sockfd, buf, count, 0, NULL, 0 ) == -1 )
  ? fzE_net_error()
  : 0;
}


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
}


// unmap an address that was previously mapped by fzE_mmap
// -1 error, 0 success
int fzE_munmap(void * mapped_address, const int file_size){
  return munmap(mapped_address, file_size);
}


/**
 * returns a monotonically increasing timestamp.
 */
uint64_t fzE_nanotime()
{
  struct timespec result;
  if (clock_gettime(CLOCK_MONOTONIC,&result)!=0)
  {
    fprintf(stderr,"*** clock_gettime failed\012");
    exit(EXIT_FAILURE);
  }
  return result.tv_sec*1000000000ULL+result.tv_nsec;
}


/**
 * Sleep for `n` nano seconds.
 */
void fzE_nanosleep(uint64_t n)
{
  struct timespec req = (struct timespec){n/1000000000LL,n-n/1000000000LL*1000000000LL};
  // NYI while{}
  nanosleep(&req,&req);
}


/**
 * remove a file or path
 */
int fzE_rm(char * path)
{
  return unlink(path) == 0
    ? 0
    : rmdir(path) == 0
    ? 0
    : -1;
}


/**
 * Get file status (resolves symbolic links)
 */
int fzE_stat(const char *pathname, int64_t * metadata)
{
  struct stat statbuf;
  if (stat(pathname,&statbuf)==((int8_t) 0))
  {
    metadata[0] = statbuf.st_size;
    metadata[1] = statbuf.st_mtime;
    metadata[2] = S_ISREG(statbuf.st_mode);
    metadata[3] = S_ISDIR(statbuf.st_mode);
    return 0;
  }
  metadata[0] = errno;
  metadata[1] = 0LL;
  metadata[2] = 0LL;
  metadata[3] = 0LL;
  return -1;
}


/**
 * Get file status (does not resolve symbolic links)
 */
int fzE_lstat(const char *pathname, int64_t * metadata)
{
  struct stat statbuf;
  if (lstat(pathname,&statbuf)==((int8_t) 0))
  {
    metadata[0] = statbuf.st_size;
    metadata[1] = statbuf.st_mtime;
    metadata[2] = S_ISREG(statbuf.st_mode);
    metadata[3] = S_ISDIR(statbuf.st_mode);
    return 0;
  }
  metadata[0] = errno;
  metadata[1] = 0LL;
  metadata[2] = 0LL;
  metadata[3] = 0LL;
  return -1;
}

#ifdef FUZION_ENABLE_THREADS
pthread_mutex_t fzE_global_mutex;
#endif

/**
 * Run plattform specific initialisation code
 */
void fzE_init()
{
#ifdef FUZION_ENABLE_THREADS
  pthread_mutexattr_t attr;
  memset(&fzE_global_mutex, 0, sizeof(fzE_global_mutex));
  bool res = pthread_mutexattr_init(&attr) == 0 &&
            pthread_mutexattr_setprotocol(&attr, PTHREAD_PRIO_INHERIT) == 0 &&
            pthread_mutex_init(&fzE_global_mutex, &attr) == 0;
  assert(res);
#endif

#ifdef GC_THREADS
  GC_INIT();
#endif
}


/**
 * Start a new thread, returns a pointer to the thread.
 */
int64_t fzE_thread_create(void* code, void* args)
{
#ifdef FUZION_ENABLE_THREADS
  // NYI use fzE_malloc_safe
  pthread_t * pt = malloc(sizeof(pthread_t));;
#ifdef GC_THREADS
  int res = GC_pthread_create(pt,NULL,code,args);
#else
  int res = pthread_create(pt,NULL,code,args);
#endif
  if (res!=0)
  {
    fprintf(stderr,"*** pthread_create failed with return code %d\012",res);
    exit(EXIT_FAILURE);
  }
  // NYI free pt
  return (int64_t)pt;
#else
  printf("You discovered a severe bug. (fzE_thread_join)");
  exit(EXIT_FAILURE);
  return -1;
#endif
}


/**
 * Join with a running thread.
 */
void fzE_thread_join(int64_t thrd)
{
#ifdef FUZION_ENABLE_THREADS
#ifdef GC_THREADS
  GC_pthread_join(*(pthread_t *)thrd, NULL);
#else
  pthread_join(*(pthread_t *)thrd, NULL);
#endif
#endif
}


/**
 * Global lock
 */
void fzE_lock()
{
#ifdef FUZION_ENABLE_THREADS
  assert(pthread_mutex_lock(&fzE_global_mutex)==0);
#else
  printf("You discovered a severe bug. (fzE_lock)");
#endif
}


/**
 * Global lock
 */
void fzE_unlock()
{
#ifdef FUZION_ENABLE_THREADS
  pthread_mutex_unlock(&fzE_global_mutex);
#else
  printf("You discovered a severe bug. (fzE_unlock)");
#endif
}

