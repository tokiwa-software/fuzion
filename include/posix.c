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
#ifdef __linux__
#define _GNU_SOURCE
#endif

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
#include <math.h>

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
#include <limits.h>
#include <time.h>
#include <assert.h>
#include <dirent.h>
#include <pthread.h>
#ifdef __linux__
#include <sched.h>    // CPU_SET
#if defined(__has_include)
#  if __has_include(<sys/sdt.h>)
#    include <sys/sdt.h>  // dtrace_probe
#    define HAVE_SYS_SDT_H 1
#  endif
#endif
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
static_assert(sizeof(pthread_t) <= sizeof(void *), "pthread_t must be smaller or equal to pointer size");

// returns the latest error number of
// the current thread
int64_t fzE_last_error(void){
  return errno;
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
  return closedir((DIR *)dir);
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
int fzE_socket_close(int sockfd)
{
  return close(sockfd);
}


// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol){

  // make sure no fork is done while we open socket
  fzE_lock();

  int sockfd = socket(fzE_get_family(family), fzE_get_socket_type(type), fzE_get_protocol(protocol));
  if (sockfd != -1)
  {
    fcntl(sockfd, F_SETFD, FD_CLOEXEC);
    // NYI: UNDER DEVELOPMENT: we need fzE_setsocketopt eventually
    setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &(int){1}, sizeof(int));
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
// -1 error, 0 success
int fzE_bind(int sockfd, int family, int socktype, int protocol, char * host, char * port){
  struct addrinfo *addr_info = NULL;
  int addrRes = fzE_getaddrinfo(family, socktype, protocol, AI_PASSIVE, host, port, &addr_info);
  if (addrRes != 0)
  {
    return -1;
  }
  int bind_res = bind(sockfd, addr_info->ai_addr, (int)addr_info->ai_addrlen);

  if(bind_res == -1)
  {
    return -1;
  }
  freeaddrinfo(addr_info);
  return bind_res;
}


// set the given socket to listening
// backlog = queuelength of pending connections
int fzE_listen(int sockfd, int backlog){
  return listen(sockfd, backlog);
}


// accept a new connection
// blocks if socket is blocking
int fzE_accept(int sockfd){
  return accept(sockfd, NULL, NULL);
}


// create connection for given parameters
// -1 error, 0 success
int fzE_connect(int sockfd, int family, int socktype, int protocol, char * host, char * port){
  struct addrinfo *addr_info = NULL;
  int addrRes = fzE_getaddrinfo(family, socktype, protocol, 0, host, port, &addr_info);
  if (addrRes != 0)
  {
    return -1;
  }
  int con_res = connect(sockfd, addr_info->ai_addr, addr_info->ai_addrlen);
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
  if (getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen) == 0) {
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
  if (getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen) == 0) {
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
  return recvfrom( sockfd, buf, count, 0, NULL, NULL);
}


// write buf to sockfd
// may block if socket is set to blocking.
// return -1 or number of bytes written on success
int fzE_socket_write(int sockfd, const void * buf, size_t count){
  return sendto( sockfd, buf, count, 0, NULL, 0);
}


// returns -1 on error, size of file in bytes otherwise
long fzE_get_file_size(void * file) {
  // store current pos
  long cur_pos = ftell((FILE *)file);
  if(cur_pos == -1 || fseek((FILE *)file, 0, SEEK_END) == -1){
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
 *   - error   :  NULL
 *   - success :  address where the file was mapped to
 */
void * fzE_mmap(void * file, uint64_t offset, size_t size) {

  if ((unsigned long)fzE_get_file_size((FILE *)file) < (offset + size)){
    return NULL;
  }

  int file_descriptor = fileno((FILE *)file);

  assert (file_descriptor != -1);

  void * mapped_address = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, file_descriptor, offset);
  if (mapped_address == MAP_FAILED) {
    return NULL;
  }
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
  while (nanosleep(&req, &req));
}


/**
 * remove a file or path
 */
int fzE_rm(char * path)
{
  return unlink(path) == 0
    ? 0
    : rmdir(path);
}


/**
 * Get file status (resolves symbolic links)
 */
int fzE_stat(const char *pathname, int64_t * metadata)
{
  struct stat statbuf;
  int result = stat(pathname,&statbuf);
  if (result == 0)
  {
    metadata[0] = statbuf.st_size;
    metadata[1] = statbuf.st_atime;  /* Time of last access */
    metadata[2] = statbuf.st_mtime;  /* Time of last modification */
    metadata[3] = statbuf.st_ctime;  /* Time of last status change */
    metadata[4] = S_ISREG(statbuf.st_mode);
    metadata[5] = S_ISDIR(statbuf.st_mode);
    metadata[6] = S_ISLNK(statbuf.st_mode);
    metadata[7] = statbuf.st_uid;
    metadata[8] = statbuf.st_gid;
  }
  return result;
}


/**
 * Get file status (does not resolve symbolic links)
 */
int fzE_lstat(const char *pathname, int64_t * metadata)
{
  struct stat statbuf;
  int result = lstat(pathname,&statbuf);
  if (result == 0)
  {
    metadata[0] = statbuf.st_size;
    metadata[1] = statbuf.st_atime;  /* Time of last access */
    metadata[2] = statbuf.st_mtime;  /* Time of last modification */
    metadata[3] = statbuf.st_ctime;  /* Time of last status change */
    metadata[4] = S_ISREG(statbuf.st_mode);
    metadata[5] = S_ISDIR(statbuf.st_mode);
    metadata[6] = S_ISLNK(statbuf.st_mode);
    metadata[7] = statbuf.st_uid;
    metadata[8] = statbuf.st_gid;
  }
  return result;
}

static pthread_mutex_t fzE_global_mutex;

/**
 * Run plattform specific initialisation code
 */
void fzE_init()
{
  fcntl(STDIN_FILENO, F_SETFL, O_NONBLOCK);

  pthread_mutexattr_t attr;
  fzE_mem_zero_secure(&fzE_global_mutex, sizeof(fzE_global_mutex));
  bool res = pthread_mutexattr_init(&attr) == 0 &&
            pthread_mutexattr_setprotocol(&attr, PTHREAD_PRIO_INHERIT) == 0 &&
            pthread_mutex_init(&fzE_global_mutex, &attr) == 0;
  assert(res);

#ifdef GC_THREADS
  GC_INIT();
#endif
}

/**
 * Get pointer to current thread.
 */
void * fzE_thread_current()
{
  return (void *)pthread_self();
}


/**
 * Start a new thread, returns a pointer to the thread.
 */
void * fzE_thread_create(void *(*code)(void *),
                          void *restrict args)
{
  pthread_t pt = (pthread_t){0};
  pthread_attr_t attr;

  int s = pthread_attr_init(&attr);
  if (s != 0)
  {
    fprintf(stderr,"*** pthread_attr_init failed with return code %d\012",s);
    exit(EXIT_FAILURE);
  }

  struct sched_param default_schedparam;
  default_schedparam.sched_priority = 0;

  assert (pthread_attr_setschedparam(&attr, &default_schedparam) == 0);
  assert (pthread_attr_setschedpolicy(&attr, SCHED_OTHER) == 0);

#ifdef GC_THREADS
  int res = GC_pthread_create(&pt,NULL,code,args);
#else
  int res = pthread_create(&pt,NULL,code,args);
#endif
  if (res != 0)
  {
    fprintf(stderr,"*** pthread_create failed with return code %d\012",res);
    exit(EXIT_FAILURE);
  }

  s = pthread_attr_destroy(&attr);
  if (s != 0)
  {
    fprintf(stderr,"*** pthread_attr_destroy failed with return code %d\012",s);
    exit(EXIT_FAILURE);
  }

  return (void *)pt;
}


/**
 * Join with a running thread.
 */
void fzE_thread_join(void * thrd)
{
  // NYI: BUG: return error code on failure
#ifdef GC_THREADS
  int ret = GC_pthread_join((pthread_t)thrd, NULL);
  assert (ret == 0);
#else
  int ret = pthread_join((pthread_t)thrd, NULL);
  assert (ret == 0);
#endif
}


/*
 * Convert internal policy number to system policy number.
 */
int fzE_thread_setschedparam_convert_policy(int policy)
{
  switch (policy)
    {
      case 0:
        return SCHED_OTHER;
      case 1:
        return SCHED_FIFO;
      case 2:
        return SCHED_RR;
      default:
        assert(false);
    }
}


/*
 * Set the scheduling policy and priority of a running thread.
 */
int fzE_thread_setschedparam(void * thrd, int policy, int priority)
{
  struct sched_param param;
  param.sched_priority = priority;
  int ret = pthread_setschedparam((pthread_t)thrd, fzE_thread_setschedparam_convert_policy(policy), &param);
  return ret;
}


/*
 * Set the scheduling CPU affinity of a running thread.
 */
int fzE_thread_setaffinity(void * thrd, const void * cores, int length)
{
#ifdef __linux__
  cpu_set_t cpuset;
  CPU_ZERO(&cpuset);

  for (int i = 0; i < length; i++)
    {
      CPU_SET(((uint64_t *)cores)[i], &cpuset);
    }

  return pthread_setaffinity_np((pthread_t)thrd, sizeof(cpu_set_t), &cpuset);
#else
  return 38;
#endif
}


/**
 * Global lock
 */
void fzE_lock()
{
  int res = pthread_mutex_lock(&fzE_global_mutex);
  assert( res == 0 );
}


/**
 * Global lock
 */
void fzE_unlock()
{
  int res = pthread_mutex_unlock(&fzE_global_mutex);
  assert( res == 0 );
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
  if (pipe(stdIn) == -1)
  {
    ret = -1;
  }
  if (ret == 0 && pipe(stdOut) == -1)
  {
    close(stdIn[0]);
    close(stdIn[1]);
    ret = -1;
  }
  if (ret == 0 && pipe(stdErr) == -1)
  {
    close(stdIn[0]);
    close(stdIn[1]);
    close(stdOut[0]);
    close(stdOut[1]);
    ret = -1;
  }
  if (ret == 0)
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


// check the process status, does not wait for process to finish
//
// result
//   >=0 : the process exit code
//   -1  : process is still running
//   -2  : an error occurred when calling waitpid, check errno
//  <-100: process was terminated by a signal
//         -100-SIG, e.g., -109 for 9 (SIGKILL)
int64_t fzE_process_poll(int64_t p){

  assert(p>0);

  int status;
  pid_t ret = waitpid(p, &status, WNOHANG);

  int res = 0;

  if (ret == 0) {
      // Child is still running.
      res = -1;
  }
  else if (ret == -1) {
      // Error. Check errno.
      res = -2;
  }
  else if (WIFEXITED(status)) {
      // process exited
      res = WEXITSTATUS(status);
  }
  else if (WIFSIGNALED(status)) {
      // process was terminated by a signal
      res = -100 - WTERMSIG(status);
  }
  else {
    assert(false);
  }
  return res;
}


// returns -1 on error, 0 on pipe exhausted/closed
// otherwise the number of bytes read
int fzE_pipe_read(int64_t desc, char * buf, size_t nbytes){
  return read((int) desc, buf, nbytes);
}


// return -1 on error, thenumber of written bytes otherwise
int fzE_pipe_write(int64_t desc, char * buf, size_t nbytes){
  return write((int) desc, buf, nbytes);
}


// return -1 on error, 0 on success
int fzE_pipe_close(int64_t desc){
// NYI: UNDER DEVELOPMENT: do we need to flush?
  return close((int) desc);
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

  const char * m = "rb";
  if (mode == FZ_FILE_MODE_WRITE)
    {
      m = "w+b";
    }
  else if (mode == FZ_FILE_MODE_APPEND)
    {
      m = "a+b";
    }

  FILE * fp = fopen(file_name, m);
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
  pthread_mutex_t *mtx = (pthread_mutex_t *)fzE_malloc_safe(sizeof(pthread_mutex_t));
  return pthread_mutex_init(mtx, NULL) == 0 ? (void *)mtx : NULL;
}

int32_t fzE_mtx_lock(void * mtx) {
  return pthread_mutex_lock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
}

int32_t fzE_mtx_trylock(void * mtx) {
  return pthread_mutex_trylock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
}

int32_t fzE_mtx_unlock(void * mtx) {
  return pthread_mutex_unlock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
}

void fzE_mtx_destroy(void * mtx) {
  pthread_mutex_destroy((pthread_mutex_t *)mtx);
  fzE_free(mtx);
}

/**
 * initialize a condition
 *
 * @param clock the clock to be used:
 *
 *   - 0 for CLOCK_REALTIME (which is not a real-time clock, but wallclock time)
 *   - 1 for CLOCK_MONOTONIC (which does not jump for leap seconds are when system time is changed)
 *
 * other values taken from /use/include/x86_64-linux-gnu/bits/time.h:
 *
 *   ** High-resolution timer from the CPU.  **
 *   # define CLOCK_PROCESS_CPUTIME_ID	2
 *   ** Thread-specific CPU-time clock.  **
 *   # define CLOCK_THREAD_CPUTIME_ID	3
 *   ** Monotonic system-wide clock, not adjusted for frequency scaling.  **
 *   # define CLOCK_MONOTONIC_RAW		4
 *   ** Identifier for system-wide realtime clock, updated only on ticks.  **
 *   # define CLOCK_REALTIME_COARSE		5
 *   ** Monotonic system-wide clock, updated only on ticks.  **
 *   # define CLOCK_MONOTONIC_COARSE		6
 *   ** Monotonic system-wide clock that includes time spent in suspension.  **
 *   # define CLOCK_BOOTTIME			7
 *   ** Like CLOCK_REALTIME but also wakes suspended system.  **
 *   # define CLOCK_REALTIME_ALARM		8
 *   ** Like CLOCK_BOOTTIME but also wakes suspended system.  **
 *   # define CLOCK_BOOTTIME_ALARM		9
 *   ** Like CLOCK_REALTIME but in International Atomic Time.  **
 *   # define CLOCK_TAI			11
 *
 * @return NULL on error or pointer to condition
 *         NOTE: eventually needs to be destroyed via fzE_cnd_destroy.
 */
void * fzE_cnd_init(int clock)
{
  assert(CLOCK_REALTIME  == 0);
  assert(CLOCK_MONOTONIC == 1);

  pthread_cond_t *cnd = (pthread_cond_t *)fzE_malloc_safe(sizeof(pthread_cond_t));
  pthread_condattr_t attr;
  void * res = NULL;
  if (pthread_condattr_init(&attr) == 0) // may return ENOMEM
    {
      if (pthread_condattr_setclock(&attr, clock) == 0) // may return EINVAL in case clock not supported
        {
          if (pthread_cond_init(cnd, &attr) == 0) // may return EAGAIN or ENOMEM
            {
              res = (void *)cnd;
            }
        }
    }
  return res;
}

void fzE_cnd_signal(void * cnd) {
  pthread_cond_signal((pthread_cond_t *)cnd);
}

void fzE_cnd_broadcast(void * cnd) {
  pthread_cond_broadcast((pthread_cond_t *)cnd);
}

void fzE_cnd_wait(void * cnd, void * mtx) {
  pthread_cond_wait((pthread_cond_t *)cnd, (pthread_mutex_t *)mtx);
}

void fzE_cnd_destroy(void * cnd) {
  pthread_cond_destroy((pthread_cond_t *)cnd);
  fzE_free(cnd);
}


int32_t fzE_file_read(void * file, void * buf, int32_t size)
{
  int32_t result = -1; // ERROR, unless we succeed
  struct pollfd fds;
  fds.fd = fileno(file);
  fds.events = POLLIN;

  int res;
  do
    {
      res = poll(&fds, 1, -1);
    }
  while (res == 0 ||                  // timeout, should never happen, retry just in case
         (res < 0 && errno == EINTR)  // we got interrupted, so retry
         );

  if (res > 0)
    {
      size_t fread_result;
      do
        {
          fread_result = fread(buf, 1, size, (FILE*)file);
          // man pages of fread say:
          //
          //    If an error occurs, or the end of the file is reached, the return value is a
          //    short item count (or zero).
          //
          // so we cannot use fread_result to detect an error. Instead, it says
          //
          //    fread() does not distinguish between end-of-file and error, and callers must
          //    use feof(3) and ferror(3) to determine which occurred.
          //
          // So let's do that:
          //
          // We might get fread_result > 0 combined with an error like EAGAIN.  In this case, we
          // return fread_result and not indicate an error by returning -1.
        }
      while (fread_result == 0 && !feof((FILE*)file) && (ferror((FILE*)file) && errno == EAGAIN));  // if we got no data and no EOF, then repeat.
      if (!ferror((FILE*)file) || errno == EAGAIN)
        {
          result = fread_result;
        }
    }

  return result;
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

int32_t fzE_path_max(void)
{
  return PATH_MAX;
}

int64_t fzE_page_size(void)
{
  return sysconf(_SC_PAGESIZE);
}

int64_t fzE_mmap_offset_multiple(void)
{
  return sysconf(_SC_PAGESIZE);
}

int fzE_cwd(void * buf, size_t size)
{
  return getcwd(buf, size) == NULL
    ? -1
    : 0;
}

int fzE_isnan(double d)
{
  return isnan(d);
}

/**
 * wrapper around DTRACE_PROBE
 */
void fzE_dtrace_probe(char col, const char* msg)
{
#ifdef HAVE_SYS_SDT_H
  // we currently use DTRACE_PROBE5(fuzion, probe, col, a0, a1, a2, a3) where
  //
  // col is a char representing the color
  //
  // a0,a1,a2,a3 each have 8 chars from the msg, with the first characters using the lower bits, i.e,
  // a0@0..7 is msg[0], a0@1..15 is msg[1], etc.
  //
  #define N 4
  uint64_t args[N];
  int i = 0;
  int j = 0;
  for (i = 0; i<N; i++)
    {
      args[i] = 0;
    }
  i = 0;
  char c;
  do
    {
      c = *(msg++);
      args[i] = args[i] | ((((uint64_t) c) << (8*j)));
      j = j + 1;
      if (j == 8)
        {
          i = i + 1;
          j = 0;
        }
    }
  while (i < N && c);

  /*  we have do disable '-Wgnu-zero-variadic-macro-arguments', otherwise we get

/home/runner/work/fuzion/fuzion/build/include/posix.c:1049:3: error: must specify at least one argument for '...' parameter of variadic macro [-Werror,-Wgnu-zero-variadic-macro-arguments]
 1049 |   DTRACE_PROBE5(fuzion, probe, col, args[0], args[1], args[2], args[3]);
      |   ^
/usr/include/x86_64-linux-gnu/sys/sdt.h:492:3: note: expanded from macro 'DTRACE_PROBE5'
  492 |   STAP_PROBE5(provider,probe,parm1,parm2,parm3,parm4,parm5)
      |   ^
/usr/include/x86_64-linux-gnu/sys/sdt.h:378:3: note: expanded from macro 'STAP_PROBE5'
  378 |   _SDT_PROBE(provider, name, 5, (arg1, arg2, arg3, arg4, arg5))
      |   ^
/usr/include/x86_64-linux-gnu/sys/sdt.h:78:75: note: expanded from macro '_SDT_PROBE'
   78 |     __asm__ __volatile__ (_SDT_ASM_BODY(provider, name, _SDT_ASM_ARGS, (n)) \
      |                                                                           ^
/usr/include/x86_64-linux-gnu/sys/sdt.h:283:9: note: macro '_SDT_ASM_BODY' defined here
  283 | #define _SDT_ASM_BODY(provider, name, pack_args, args, ...)                   \
      |         ^
1 error generated.
  */
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wgnu-zero-variadic-macro-arguments"

  DTRACE_PROBE5(fuzion, probe, col, args[0], args[1], args[2], args[3]);

#pragma clang diagnostic pop

  #undef N
#endif
}
