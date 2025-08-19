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
#define GC_DONT_INCLUDE_WINDOWS_H
#include <gc.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <stdint.h>
#include <stdbool.h>
#include <fcntl.h>      // fcntl
#include <string.h>

#include <netdb.h>      // getaddrinfo
#include <netinet/in.h> // AF_INET
#include <poll.h>       // poll
#include <spawn.h>
#include <sys/ioctl.h>  // ioctl, FIONREAD
#include <sys/mman.h>   // mmap
#include <sys/socket.h> // socket, bind, listen, accept, connect
#include <sys/stat.h>   // mkdir
#include <sys/types.h>  // mkdir
#include <sys/wait.h>
#include <signal.h>
#include <unistd.h>     // close
#include <time.h>
#include <assert.h>
#include <dirent.h>
#ifdef FUZION_ENABLE_THREADS
#include <pthread.h>
#endif

#include "fz.h"


static_assert(SIGHUP == 1, "signal definition different than expected");
static_assert(SIGINT == 2, "signal definition different than expected");
static_assert(SIGQUIT == 3, "signal definition different than expected");
static_assert(SIGILL == 4, "signal definition different than expected");
static_assert(SIGTRAP == 5, "signal definition different than expected");
static_assert(SIGABRT == 6, "signal definition different than expected");
static_assert(SIGFPE == 8, "signal definition different than expected");
static_assert(SIGKILL == 9, "signal definition different than expected");
static_assert(SIGSEGV == 11, "signal definition different than expected");
static_assert(SIGPIPE == 13, "signal definition different than expected");
static_assert(SIGALRM == 14, "signal definition different than expected");
static_assert(SIGTERM == 15, "signal definition different than expected");


// thread local to hold the last
// error that occurred in fuzion runtime.
_Thread_local int64_t last_error = 0;


// returns the latest error number of
// the current thread
int64_t fzE_last_error(void){
  return last_error;
}

// helper to set last_error
// if return value of some function is -1.
int set_last_error(int ret_val)
{
  last_error = ret_val == -1 ? errno : 0;
  return ret_val;
}

// zero memory
void fzE_mem_zero_secure(void *dest, size_t sz)
{
#ifdef __STDC_LIB_EXT1__
  memset_s(dest, sz, 0, sz);
#else
  volatile unsigned char *p = dest;
  while (sz--) {
      *p++ = 0;
  }
#endif
}


// make directory, return zero on success
int fzE_mkdir(const char *pathname){
  return mkdir(pathname, S_IRWXU);
}

void * fzE_opendir(const char *pathname, int64_t * result) {
  errno = 0;
  void * res = opendir(pathname);
  result[0] = errno;
  return res;
}


int fzE_dir_read(intptr_t * dir, int8_t * result) {
  errno = 0;

  DIR * dir1 = (DIR *)dir;
  struct dirent * entry = NULL;

  while ((entry = readdir(dir1)) != NULL &&
         // skip dot and dot-dot paths.
         (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0));

  if ( entry == NULL ) {
    last_error = errno;
    return errno == 0
      // end reached
      ? 0
      // some error occurred
      : -1;
  }
  else {
    size_t len = strlen(entry->d_name);
    assert(len<1024); // NYI:
    fzE_memcpy(result, entry->d_name, len + 1);
    return len;
  }
}


int fzE_dir_close(intptr_t * dir) {
  return set_last_error(closedir((DIR *)dir));
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


// fuzion family number -> system family number
int fzE_get_family(int family)
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
int fzE_get_socket_type(int socktype)
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
int fzE_get_protocol(int protocol)
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
  return set_last_error(close(sockfd));
}


// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol){

  // make sure no fork is done while we open socket
  fzE_lock();

  int sockfd = set_last_error(socket(fzE_get_family(family), fzE_get_socket_type(type), fzE_get_protocol(protocol)));
  if (sockfd != -1)
  {
    fcntl(sockfd, F_SETFD, FD_CLOEXEC);
  }

  fzE_unlock();

  return sockfd;
}

// get addrinfo structure used for binding/connection of a socket.
int fzE_getaddrinfo(int family, int socktype, int protocol, int flags, char * host, char * port, struct addrinfo ** result){
  struct addrinfo hints;

  fzE_mem_zero_secure(&hints, sizeof hints);

  hints.ai_family = fzE_get_family(family);
  hints.ai_socktype = fzE_get_socket_type(socktype);
  hints.ai_protocol = fzE_get_protocol(protocol);
  hints.ai_flags = flags;

  return getaddrinfo(host, port, &hints, result);
}


// create a new socket and bind to given host:port
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_bind(int family, int socktype, int protocol, char * host, char * port, int32_t * result){
  result[0] = fzE_socket(family, socktype, protocol);
  if (result[0] == -1)
  {
    result[0] = errno;
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
    result[0] = errno;
    return -1;
  }
  freeaddrinfo(addr_info);
  return bind_res;
}


// set the given socket to listening
// backlog = queuelength of pending connections
int fzE_listen(int sockfd, int backlog){
  return set_last_error(listen(sockfd, backlog));
}


// accept a new connection
// blocks if socket is blocking
int fzE_accept(int sockfd){
  return set_last_error(accept(sockfd, NULL, NULL));
}


// create connection for given parameters
// result[0] contains either an errorcode or a socket descriptor
// -1 error, 0 success
int fzE_connect(int family, int socktype, int protocol, char * host, char * port, int32_t * result){
  // get socket
  result[0] = fzE_socket(family, socktype, protocol);
  if (result[0] == -1)
  {
    result[0] = errno;
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
    // NYI: UNDER DEVELOPMENT: do we want to try another address in addr_info->ai_next?
    fzE_close(result[0]);
    result[0] = errno;
  }
  freeaddrinfo(addr_info);
  return con_res;
}


// get the peer's ip address
// result is the length of the ip address written to buf
// might return useless information when called on udp socket
int fzE_get_peer_address(int sockfd, void * buf) {
  // sockaddr_storage: A structure at least as large
  // as any other sockaddr_* address structures.
  struct sockaddr_storage peeraddr;
  fzE_mem_zero_secure(&peeraddr, sizeof(peeraddr));
  socklen_t peeraddrlen = sizeof(peeraddr);
  if (set_last_error(getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen)) == 0) {
    if (peeraddr.ss_family == AF_INET) {
      fzE_memcpy(buf, &(((struct sockaddr_in *)&peeraddr)->sin_addr.s_addr), 4);
      return 4;
    } else if (peeraddr.ss_family == AF_INET6) {
      fzE_memcpy(buf, &(((struct sockaddr_in6 *)&peeraddr)->sin6_addr.s6_addr), 16);
      return 16;
    }
  }
  return -1;
}


// get the peer's port
// result is the port number
// might return useless infomrmation when called on udp socket
unsigned short fzE_get_peer_port(int sockfd) {
  // sockaddr_storage: A structure at least as large
  // as any other sockaddr_* address structures.
  struct sockaddr_storage peeraddr;
  fzE_mem_zero_secure(&peeraddr, sizeof(peeraddr));
  socklen_t peeraddrlen = sizeof(peeraddr);
  if (set_last_error(getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen)) == 0) {
    if (peeraddr.ss_family == AF_INET) {
      return ntohs(((struct sockaddr_in *)&peeraddr)->sin_port);
    } else if (peeraddr.ss_family == AF_INET6) {
      return ntohs(((struct sockaddr_in6 *)&peeraddr)->sin6_port);
    }
  }
  return 0;
}


// read up to count bytes bytes from sockfd
// into buf. may block if socket is  set to blocking.
// return -1 on error or number of bytes read
int fzE_socket_read(int sockfd, void * buf, size_t count){
  return set_last_error(recvfrom( sockfd, buf, count, 0, NULL, NULL));
}


// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_socket_write(int sockfd, const void * buf, size_t count){
  return set_last_error(sendto( sockfd, buf, count, 0, NULL, 0));
}


// returns -1 on error, size of file in bytes otherwise
long fzE_get_file_size(void * file) {
  // store current pos
  long cur_pos = set_last_error(ftell((FILE *)file));
  if(cur_pos == -1 || set_last_error(fseek((FILE *)file, 0, SEEK_END)) == -1){
    return -1;
  }

  long size = ftell((FILE *)file);

  // reset seek position
  fseek((FILE *)file, cur_pos, SEEK_SET);

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
void * fzE_mmap(void * file, uint64_t offset, size_t size, int * result) {

  if ((unsigned long)fzE_get_file_size((FILE *)file) < (offset + size)){
    result[0] = -1;
    return NULL;
  }

  int file_descriptor = fileno((FILE *)file);

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
  return set_last_error(munmap(mapped_address, file_size));
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
  while (nanosleep(&req, &req));
}


/**
 * remove a file or path
 */
int fzE_rm(char * path)
{
  return unlink(path) == 0
    ? 0
    : set_last_error(rmdir(path)) == 0
    ? 0
    : -1;
}


/**
 * Get file status (resolves symbolic links)
 */
int fzE_stat(const char *pathname, int64_t * metadata)
{
  struct stat statbuf;
  int result = set_last_error(stat(pathname,&statbuf));
  if (result == 0)
  {
    metadata[0] = statbuf.st_size;
    metadata[1] = statbuf.st_mtime;
    metadata[2] = S_ISREG(statbuf.st_mode);
    metadata[3] = S_ISDIR(statbuf.st_mode);
  }
  return result;
}


/**
 * Get file status (does not resolve symbolic links)
 */
int fzE_lstat(const char *pathname, int64_t * metadata)
{
  struct stat statbuf;
  int result = set_last_error(lstat(pathname,&statbuf));
  if (result == 0)
  {
    metadata[0] = statbuf.st_size;
    metadata[1] = statbuf.st_mtime;
    metadata[2] = S_ISREG(statbuf.st_mode);
    metadata[3] = S_ISDIR(statbuf.st_mode);
  }
  return result;
}

#ifdef FUZION_ENABLE_THREADS
static pthread_mutex_t fzE_global_mutex;
#endif

/**
 * Run plattform specific initialisation code
 */
void fzE_init()
{
  fcntl(STDIN_FILENO, F_SETFL, O_NONBLOCK);

#ifdef FUZION_ENABLE_THREADS
  pthread_mutexattr_t attr;
  fzE_mem_zero_secure(&fzE_global_mutex, sizeof(fzE_global_mutex));
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
void * fzE_thread_create(void *(*code)(void *),
                          void *restrict args)
{
#ifdef FUZION_ENABLE_THREADS
  pthread_t * pt = fzE_malloc_safe(sizeof(pthread_t));
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
  return pt;
#else
  printf("You discovered a severe bug. (fzE_thread_join)");
  exit(EXIT_FAILURE);
  return NULL;
#endif
}


/**
 * Join with a running thread.
 */
void fzE_thread_join(void * thrd)
{
#ifdef FUZION_ENABLE_THREADS
#ifdef GC_THREADS
  GC_pthread_join(*(pthread_t *)thrd, NULL);
#else
  pthread_join(*(pthread_t *)thrd, NULL);
#endif
  fzE_free(thrd);
#endif
}


/**
 * Global lock
 */
void fzE_lock()
{
#ifdef FUZION_ENABLE_THREADS
  int res = pthread_mutex_lock(&fzE_global_mutex);
  assert( res == 0 );
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
  int res = pthread_mutex_unlock(&fzE_global_mutex);
  assert( res == 0 );
#else
  printf("You discovered a severe bug. (fzE_unlock)");
#endif
}


// NYI: UNDER DEVELOPMENT: make this thread safe
// NYI: UNDER DEVELOPMENT: option to pass stdin,stdout,stderr
// zero on success, -1 error
int fzE_process_create(char * args[], size_t argsLen, char * env[], size_t envLen, int64_t * result)
{

  // Describes the how and why
  // of making file descriptors, handlers, sockets
  // none inheritable (CLOEXEC, HANDLE_FLAG_INHERIT):
  // https://peps.python.org/pep-0446/

  // Some problems with fork, exec:
  // https://www.microsoft.com/en-us/research/publication/a-fork-in-the-road/

  // how it is done in jdk:
  // https://github.com/openjdk/jdk/blob/c2d9fa26ce903be7c86a47db5ff289cdb9de3a62/src/java.base/unix/native/libjava/ProcessImpl_md.c#L53

  // make sure no files are opened while we start child process
  fzE_lock();

  errno = 0;

  int stdIn[2];
  int stdOut[2];
  int stdErr[2];
  int ret = 0;
  if (set_last_error(pipe(stdIn)) == -1)
  {
    ret = -1;
  }
  if (ret == 0 && set_last_error(pipe(stdOut)) == -1)
  {
    close(stdIn[0]);
    close(stdIn[1]);
    ret = -1;
  }
  if (ret == 0 && set_last_error(pipe(stdErr)) == -1)
  {
    close(stdIn[0]);
    close(stdIn[1]);
    close(stdOut[0]);
    close(stdOut[1]);
    ret = -1;
  }
  if (set_last_error(ret) == 0)
  {
    fcntl(stdIn[1], F_SETFD, FD_CLOEXEC);
    fcntl(stdOut[0], F_SETFD, FD_CLOEXEC);
    fcntl(stdErr[0], F_SETFD, FD_CLOEXEC);

    pid_t processId;

    posix_spawn_file_actions_t file_actions;

    if (posix_spawn_file_actions_init(&file_actions) != 0)
    {
      exit(1);
    }

    posix_spawn_file_actions_adddup2(&file_actions, stdIn[0], 0);
    posix_spawn_file_actions_adddup2(&file_actions, stdOut[1], 1);
    posix_spawn_file_actions_adddup2(&file_actions, stdErr[1], 2);
    posix_spawn_file_actions_addclose(&file_actions, stdIn[0]);
    posix_spawn_file_actions_addclose(&file_actions, stdOut[1]);
    posix_spawn_file_actions_addclose(&file_actions, stdErr[1]);

    args[argsLen -1] = NULL;
    env[envLen -1] = NULL;

    int s = posix_spawnp(
          &processId,
          args[0],
          &file_actions,
          NULL,
          args, // args
          env  // environment
          );

    close(stdIn[0]);
    close(stdOut[1]);
    close(stdErr[1]);

    posix_spawn_file_actions_destroy(&file_actions);

    if(s != 0)
    {
      last_error = s;
      close(stdIn[0]);
      close(stdIn[1]);
      close(stdOut[0]);
      close(stdOut[1]);
      close(stdErr[0]);
      close(stdErr[1]);
      ret = -1;
    }
    else
    {
      result[0] = processId;
      result[1] = (int64_t) stdIn[1];
      result[2] = (int64_t) stdOut[0];
      result[3] = (int64_t) stdErr[0];
    }
  }

  fzE_unlock();

  return ret;
}


// wait for process to finish
// returns exit code or -1 on wait-failure.
int64_t fzE_process_wait(int64_t p){
  int status;
  return set_last_error(waitpid(p, &status, WUNTRACED | WCONTINUED)) == -1
    ? -1
    : WIFEXITED(status)
    // man waitpid: "This macro should be employed only if WIFEXITED returned true."
    ? WEXITSTATUS(status)
    : 1;
}


// returns -1 on error, 0 on pipe exhausted/closed
// otherwise the number of bytes read
int fzE_pipe_read(int64_t desc, char * buf, size_t nbytes){
  return set_last_error(read((int) desc, buf, nbytes));
}


// return -1 on error, the number of written bytes otherwise
int fzE_pipe_write(int64_t desc, char * buf, size_t nbytes){
  return set_last_error(write((int) desc, buf, nbytes));
}


// return -1 on error, 0 on success
int fzE_pipe_close(int64_t desc){
// NYI: UNDER DEVELOPMENT: do we need to flush?
  return set_last_error(close((int) desc));
}


// open_results[0] the error number
void * fzE_file_open(char * file_name, int64_t * open_results, file_open_mode mode)
{
  assert( mode >= 0 && mode <= 2 );
  //"In  multithreaded programs, using fcntl() F_SETFD to set the close-on-exec flag
  // at the same time as another thread performs a fork(2) plus execve(2) is vulnerable
  // to a race condition that may unintentionally leak the file descriptor to the
  // program executed in the child process.  See the discussion of the O_CLOEXEC flag in open(2)
  // for details and a remedy to the problem."
  errno = 0;

  // make sure no fork is done while we open file
  fzE_lock();

  FILE * fp = fopen(file_name, mode==FZ_FILE_MODE_READ ? "rb" : "a+b");
  if (fp!=NULL)
  {
    fcntl(fileno(fp), F_SETFD, FD_CLOEXEC);
  }
  else
  {
    open_results[0] = (int64_t)errno;
  }

  fzE_unlock();

  return fp;
}



void * fzE_mtx_init() {
#ifdef FUZION_ENABLE_THREADS
  pthread_mutex_t *mtx = (pthread_mutex_t *)fzE_malloc_safe(sizeof(pthread_mutex_t));
  return pthread_mutex_init(mtx, NULL) == 0 ? (void *)mtx : NULL;
#else
  return NULL;
#endif
}

int32_t fzE_mtx_lock(void * mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_mutex_lock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_mtx_trylock(void * mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_mutex_trylock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_mtx_unlock(void * mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_mutex_unlock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

void fzE_mtx_destroy(void * mtx) {
#ifdef FUZION_ENABLE_THREADS
  pthread_mutex_destroy((pthread_mutex_t *)mtx);
  // NYI: free(mtx);
#else
#endif
}

void * fzE_cnd_init() {
#ifdef FUZION_ENABLE_THREADS
  pthread_cond_t *cnd = (pthread_cond_t *)fzE_malloc_safe(sizeof(pthread_cond_t));
  return pthread_cond_init(cnd, NULL) == 0 ? (void *)cnd : NULL;
#else
 return NULL;
#endif
}

int32_t fzE_cnd_signal(void * cnd) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_cond_signal((pthread_cond_t *)cnd) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_cnd_broadcast(void * cnd) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_cond_broadcast((pthread_cond_t *)cnd) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_cnd_wait(void * cnd, void * mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_cond_wait((pthread_cond_t *)cnd, (pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

void fzE_cnd_destroy(void * cnd) {
#ifdef FUZION_ENABLE_THREADS
  pthread_cond_destroy((pthread_cond_t *)cnd);
  // NYI: free(cnd);
#else
#endif
}


int32_t fzE_file_read(void * file, void * buf, int32_t size)
{
  struct pollfd fds;
  fds.fd = fileno(file);
  fds.events = POLLIN;

  while(poll(&fds, 1, -1) == 0);

  size_t result = fread(buf, 1, size, (FILE*)file);

  return result > 0
    ? result
    : result == 0
    ? -1  // EOF
    : -2; // ERROR
}


/**
 * result is a 32-bit array
 *
 * result[0] = year
 * result[1] = month
 * result[2] = day_in_month
 * result[3] = hour
 * result[4] = min
 * result[5] = sec
 * result[6] = nanosec;
 */
void fzE_date_time(int32_t * result)
{
  struct timespec ts;
  struct tm ptm;

  clock_gettime(CLOCK_REALTIME, &ts);
  gmtime_r(&ts.tv_sec, &ptm);

  ((int32_t *)result)[0] = ptm.tm_year + 1900;
  ((int32_t *)result)[1] = ptm.tm_mon + 1;
  ((int32_t *)result)[2] = ptm.tm_mday;
  ((int32_t *)result)[3] = ptm.tm_hour;
  ((int32_t *)result)[4] = ptm.tm_min;
  ((int32_t *)result)[5] = ptm.tm_sec;
  ((int32_t *)result)[6] = ts.tv_nsec;
}


int32_t fzE_file_write(void * file, void * buf, int32_t size)
{
  size_t result = fwrite(buf, 1, size, (FILE*)file);
  return ferror((FILE*)file)!=0
    ? -1
    : result;
}

int32_t fzE_file_move(const char *oldpath, const char *newpath)
{
  return rename(oldpath, newpath);
}

int32_t fzE_file_close(void * file)
{
  return fclose((FILE*)file);
}

int32_t fzE_file_seek(void * file, int64_t offset)
{
  return fseek((FILE*)file, offset, SEEK_SET);
}

int64_t fzE_file_position(void * file)
{
  return ftell((FILE*)file);
}

void * fzE_file_stdin(void) { return stdin; }
void * fzE_file_stdout(void) { return stdout; }
void * fzE_file_stderr(void) { return stderr; }

int32_t fzE_file_flush(void * file)
{
  return fflush(file) == 0 ? 0 : -1;
}

int fzE_send_signal(int64_t pid, int sig)
{
  return kill(pid, sig);
}

int64_t fzE_page_size(void)
{
  return sysconf(_SC_PAGESIZE);
}
