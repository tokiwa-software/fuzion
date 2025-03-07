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
 * Source of windows code of Fuzion C backend.
 *
 *---------------------------------------------------------------------*/

// "For example if you want to use winsock2.h you better make sure
// WIN32_LEAN_AND_MEAN is always defined because otherwise you will
// get conflicting declarations between the WinSock versions."
// https://stackoverflow.com/questions/11040133/what-does-defining-win32-lean-and-mean-exclude-exactly#comment108482188_11040230
#define WIN32_LEAN_AND_MEAN

#ifdef GC_THREADS
#define GC_DONT_INCLUDE_WINDOWS_H
#include <gc.h>
#endif

#include <stdio.h>
#include <stdlib.h>     // setenv, unsetenv
#include <errno.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <assert.h>
#include <time.h>

// NYI remove POSIX imports
#include <fcntl.h>      // fcntl

#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <winbase.h>
#include <synchapi.h> // WaitForSingleObject
#include <namedpipeapi.h>

#ifdef FUZION_ENABLE_THREADS
#include <pthread.h>
#endif

#include "fz.h"


// returns the latest error number of
// the current thread
int fzE_last_error(void){
  return (int)GetLastError();
}

// NYI missing set_last_error, see posix.c

// make directory, return zero on success
int fzE_mkdir(const char *pathname){
  return CreateDirectory(pathname, NULL)
    ? 0
    : -1;
}


// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite){
  return -1;
}


// unset environment variable, return zero on success
int fzE_unsetenv(const char *name){
  return -1;
}



typedef struct {
    HANDLE handle;
    WIN32_FIND_DATA findData;
} fzE_dir_struct;

void * fzE_opendir(const char *pathname, int64_t * result) {
  fzE_dir_struct *dir = (fzE_dir_struct *)fzE_malloc_safe(sizeof(fzE_dir_struct));

  /* NYI: UNDER DEVELOPMENT:
  int len = strlen(pathname) + strlen("\\\\?\\\\*") + 1;
  char searchPath[len];
  // By default, the name is limited to MAX_PATH characters.
  // To extend this limit to 32,767 wide characters, prepend "\\?\" to the path.
  // https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-findfirstfilea
  snprintf(searchPath, len, "\\\\?\\%s\\*", pathname);
 */

  char searchPath[MAX_PATH];
  snprintf(searchPath, MAX_PATH, "%s\\*", pathname);

  dir->handle = FindFirstFile(searchPath, &dir->findData);
  if (dir->handle == INVALID_HANDLE_VALUE) {
    // NYI: BUG: free(dir);
    result[0] = GetLastError();
    return dir;
  } else {
    result[0] = 0;
    return dir;
  }
}

int fzE_dir_read(intptr_t * dir, void * result) {
  fzE_dir_struct *d = (fzE_dir_struct *)dir;
  BOOL res = FALSE;
  while ((res = FindNextFile(d->handle, &d->findData)) &&
        // skip dot and dot-dot paths.
        (strcmp(d->findData.cFileName, ".") == 0 || strcmp(d->findData.cFileName, "..") == 0));

  if (!res) {
    return GetLastError() == ERROR_NO_MORE_FILES ? 0 : -1;
  }
  else {
    int len = (int)strlen(d->findData.cFileName);
    assert(len >= 0 && len<1024); // NYI:
    fzE_memcpy(result, d->findData.cFileName, len + 1);
    return len;
  }
}

int fzE_dir_close(intptr_t * dir) {
  fzE_dir_struct *d = (fzE_dir_struct *)dir;
  BOOL res = FindClose(d->handle);
  // NYI: BUG: free(dir);

  return res
    ? 0
    : -1;
}



// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking)
{
  u_long b = blocking;
  return ioctlsocket(sockfd, FIONBIO, &b);
}


// helper function to retrieve
// the last error that occurred.
int fzE_net_error()
{
  return WSAGetLastError();
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
  closesocket(sockfd);
  return fzE_net_error();
}


// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol){
  WSADATA wsaData;
  return WSAStartup(MAKEWORD(2,2), &wsaData) != 0
    ? -1
    : socket(fzE_get_family(family), fzE_get_socket_type(type), fzE_get_protocol(protocol));
}


// get addrinfo structure used for binding/connection of a socket.
int fzE_getaddrinfo(int family, int socktype, int protocol, int flags, char * host, char * port, struct addrinfo ** result){
  struct addrinfo hints;

  ZeroMemory(&hints, sizeof(hints));

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
int fzE_connect(int family, int socktype, int protocol, char * host, char * port, int32_t * result){
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
  if (getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen) == -1) {
    return -1;
  } else if (peeraddr.ss_family == AF_INET) {
    fzE_memcpy(buf, &(((struct sockaddr_in *)&peeraddr)->sin_addr.s_addr), 4);
    return 4;
  } else if (peeraddr.ss_family == AF_INET6) {
    fzE_memcpy(buf, &(((struct sockaddr_in6 *)&peeraddr)->sin6_addr.s6_addr), 16);
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
  if (getpeername(sockfd, (struct sockaddr *)&peeraddr, &peeraddrlen) == -1) {
    return 0;
  } else if (peeraddr.ss_family == AF_INET) {
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
int fzE_socket_read(int sockfd, void * buf, size_t count){
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
}


// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_socket_write(int sockfd, const void * buf, size_t count){
return ( sendto( sockfd, buf, count, 0, NULL, 0 ) == -1 )
  ? fzE_net_error()
  : 0;
}


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
 *   - error   :  result[0]=-1 and NULL
 *   - success :  result[0]=0  and an address where the file was mapped to
 */
void * fzE_mmap(void * file, uint64_t offset, size_t size, int * result) {

  if ((unsigned long)fzE_get_file_size((FILE *)file) < (offset + size)){
    result[0] = -1;
    return NULL;
  }

  HANDLE file_handle = (HANDLE)_get_osfhandle(fileno((FILE *)file));

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
}


// unmap an address that was previously mapped by fzE_mmap
// -1 error, 0 success
int fzE_munmap(void * mapped_address, const int file_size){
  return UnmapViewOfFile(mapped_address)
    ? 0
    : -1;
}


/**
 * returns a monotonically increasing timestamp.
 */
uint64_t fzE_nanotime()
{
  static LARGE_INTEGER frequency = {0};
  if (frequency.QuadPart == 0) {
      if (!QueryPerformanceFrequency(&frequency)) {
          fprintf(stderr, "*** QueryPerformanceFrequency failed\n");
          exit(EXIT_FAILURE);
      }
  }

  LARGE_INTEGER counter;
  if (!QueryPerformanceCounter(&counter)) {
      fprintf(stderr, "*** QueryPerformanceCounter failed\n");
      exit(EXIT_FAILURE);
  }

  return (uint64_t)(counter.QuadPart * (1000000000ULL / frequency.QuadPart));
}


/**
 * Sleep for `n` nano seconds.
 */
void fzE_nanosleep(uint64_t n)
{
  uint64_t start = fzE_nanotime();
  uint64_t end = start + n;

  while (fzE_nanotime() < end) {
      uint64_t remaining_ns = end - fzE_nanotime();
      if (remaining_ns > 1000000ULL) {
          Sleep((DWORD)(remaining_ns / 1000000ULL));
      } else {
          YieldProcessor();
      }
  }
}


/**
 * remove a file or path
 */
int fzE_rm(char * path)
{
  if (DeleteFileA(path)) {
    return 0;
  }

  if (RemoveDirectoryA(path)) {
    return 0;
  }

  return -1;
}


/**
 * Get file status (resolves symbolic links)
 */
int fzE_stat(const char *pathname, int64_t * metadata)
{
  WIN32_FILE_ATTRIBUTE_DATA fileInfo;

  if (GetFileAttributesExA(pathname, GetFileExInfoStandard, &fileInfo)) {
    LARGE_INTEGER fileSize;
    fileSize.HighPart = fileInfo.nFileSizeHigh;
    fileSize.LowPart = fileInfo.nFileSizeLow;

    FILETIME ft = fileInfo.ftLastWriteTime;
    ULARGE_INTEGER ull;
    ull.LowPart = ft.dwLowDateTime;
    ull.HighPart = ft.dwHighDateTime;

    metadata[0] = fileSize.QuadPart;
    metadata[1] = (ull.QuadPart / 10000000ULL) - 11644473600ULL;
    metadata[2] = (fileInfo.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) ? 0 : 1;
    metadata[3] = (fileInfo.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) ? 1 : 0;

    return 0;
  }

  metadata[0] = (int64_t)GetLastError();
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
  return fzE_stat(pathname, metadata);
}

#ifdef FUZION_ENABLE_THREADS
pthread_mutex_t fzE_global_mutex;
#endif

/**
 * Run plattform specific initialisation code
 */
void fzE_init()
{
  _setmode( _fileno( stdout ), _O_BINARY ); // reopen stdout in binary mode
  _setmode( _fileno( stderr ), _O_BINARY ); // reopen stderr in binary mode

#ifdef FUZION_ENABLE_THREADS
  pthread_mutexattr_t attr;
  fzE_memset(&fzE_global_mutex, 0, sizeof(fzE_global_mutex));
  bool res = pthread_mutexattr_init(&attr) == 0 &&
            // NYI #1646 setprotocol returns EINVAL on windows.
            // pthread_mutexattr_setprotocol(&attr, PTHREAD_PRIO_INHERIT) == 0 &&
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
int64_t fzE_thread_create(void *(*code)(void *),
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


// NYI make this thread safe
// NYI option to pass stdin,stdout,stderr
// zero on success, -1 error
int fzE_process_create(char * args[], size_t argsLen, char * env[], size_t envLen, int64_t * result, char * args_str, char * env_str) {

  // create stdIn, stdOut, stdErr pipes
  HANDLE stdIn[2];
  HANDLE stdOut[2];
  HANDLE stdErr[2];

  SECURITY_ATTRIBUTES secAttr = { sizeof(SECURITY_ATTRIBUTES) , NULL, TRUE };

  // NYI cleanup on error
  if ( !CreatePipe(&stdIn[0], &stdIn[1], &secAttr, 0)
    || !CreatePipe(&stdOut[0], &stdOut[1],&secAttr, 0)
    || !CreatePipe(&stdErr[0], &stdErr[1], &secAttr, 0))
  {
    return -1;
  }

  // prepare create process args
  PROCESS_INFORMATION processInfo;
  ZeroMemory( &processInfo, sizeof(PROCESS_INFORMATION) );
  STARTUPINFO startupInfo;
  ZeroMemory( &startupInfo, sizeof(STARTUPINFO) );
  startupInfo.hStdInput = stdIn[0];
  startupInfo.hStdOutput = stdOut[1];
  startupInfo.hStdError = stdErr[1];
  startupInfo.dwFlags |= STARTF_USESTDHANDLES;

  // Programmatically controlling which handles are inherited by new processes in Win32
  // https://devblogs.microsoft.com/oldnewthing/20111216-00/?p=8873

  SIZE_T size = 0;
  LPPROC_THREAD_ATTRIBUTE_LIST lpAttributeList = NULL;
  if(! (InitializeProcThreadAttributeList(NULL, 1, 0, &size) ||
             GetLastError() == ERROR_INSUFFICIENT_BUFFER)){
    return -1;
  }
  lpAttributeList = (LPPROC_THREAD_ATTRIBUTE_LIST) HeapAlloc(GetProcessHeap(), 0, size);
  if(lpAttributeList == NULL){
    return -1;
  }
  if(!InitializeProcThreadAttributeList(lpAttributeList,
                      1, 0, &size)){
    HeapFree(GetProcessHeap(), 0, lpAttributeList);
    return -1;
  }
  HANDLE handlesToInherit[] =  { stdIn[0], stdOut[1], stdErr[1] };
  if(!UpdateProcThreadAttribute(lpAttributeList,
                      0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
                      handlesToInherit,
                      3 * sizeof(HANDLE), NULL, NULL)){
    DeleteProcThreadAttributeList(lpAttributeList);
    HeapFree(GetProcessHeap(), 0, lpAttributeList);
    return -1;
  }

  STARTUPINFOEX startupInfoEx;
  ZeroMemory( &startupInfoEx, sizeof(startupInfoEx) );
  startupInfoEx.StartupInfo = startupInfo;
  startupInfoEx.StartupInfo.cb = sizeof(startupInfoEx);
  startupInfoEx.lpAttributeList = lpAttributeList;

  // NYI use unicode?
  // int wchars_num = MultiByteToWideChar(CP_UTF8, 0, &str, -1, NULL, 0);
  // wchar_t* wstr = new wchar_t[wchars_num];
  // MultiByteToWideChar(CP_UTF8, 0, &str, -1, wstr, wchars_num);
  // Note that an ANSI environment block is terminated by two zero bytes: one for the last string, one more to terminate the block.
  // A Unicode environment block is terminated by four zero bytes: two for the last string, two more to terminate the block.


  if( !CreateProcess(NULL,
      TEXT(args_str),                // command line
      NULL,                          // process security attributes
      NULL,                          // primary thread security attributes
      TRUE,                          // inherit handles listed in startupInfo
      EXTENDED_STARTUPINFO_PRESENT,  // creation flags
      env_str,                       // environment
      NULL,                          // use parent's current directory
      &startupInfoEx.StartupInfo,    // STARTUPINFOEX pointer
      &processInfo))                 // receives PROCESS_INFORMATION
  {
    // cleanup all pipes
    CloseHandle(stdIn[0]);
    CloseHandle(stdIn[1]);
    CloseHandle(stdOut[0]);
    CloseHandle(stdOut[1]);
    CloseHandle(stdErr[0]);
    CloseHandle(stdErr[1]);
    return -1;
  }

   DeleteProcThreadAttributeList(lpAttributeList);
   HeapFree(GetProcessHeap(), 0, lpAttributeList);

  // no need for this handle, closing
  CloseHandle(processInfo.hThread);

  // close the handles given to child process.
  CloseHandle(stdIn[0]);
  CloseHandle(stdOut[1]);
  CloseHandle(stdErr[1]);

  result[0] = (int64_t) processInfo.hProcess;
  result[1] = (int64_t) stdIn[1];
  result[2] = (int64_t) stdOut[0];
  result[3] = (int64_t) stdErr[0];
  return 0;
}


// wait for process to finish
// returns exit code or -1 on wait-failure.
int32_t fzE_process_wait(int64_t p){
  DWORD status = 0;
  WaitForSingleObject((HANDLE)p, INFINITE);
  if (!GetExitCodeProcess((HANDLE)p, &status)){
    return -1;
  }
  CloseHandle((HANDLE)p);
  return (int32_t)status;
}


// returns -1 on error, 0 on pipe exhausted/closed
// otherwise the number of bytes read
int fzE_pipe_read(int64_t desc, char * buf, size_t nbytes){
  DWORD bytesRead;
  if (!ReadFile((HANDLE)desc, buf, nbytes, &bytesRead, NULL)){
    return GetLastError() == ERROR_BROKEN_PIPE
      ? 0
      : -1;
  }
  return bytesRead;
}


// return -1 on error, the number of written bytes otherwise
int fzE_pipe_write(int64_t desc, char * buf, size_t nbytes){
  DWORD bytesWritten;
  if (!WriteFile((HANDLE)desc, buf, nbytes, &bytesWritten, NULL)){
    return -1;
  }
  return bytesWritten;
}


// return -1 on error, 0 on success
int fzE_pipe_close(int64_t desc){
// NYI do we need to flush?
  return CloseHandle((HANDLE)desc)
    ? 0
    : -1;
}


// open_results[0] the error number
void * fzE_file_open(char * file_name, int64_t * open_results, int8_t mode)
{
  assert( mode >= 0 && mode <= 2 );
  // NYI use lock to make fopen and fcntl _atomic_.
  //"In  multithreaded programs, using fcntl() F_SETFD to set the close-on-exec flag
  // at the same time as another thread performs a fork(2) plus execve(2) is vulnerable
  // to a race condition that may unintentionally leak the file descriptor to the
  // program executed in the child process.  See the discussion of the O_CLOEXEC flag in open(2)
  // for details and a remedy to the problem."
  errno = 0;
  FILE * fp = fopen(file_name, mode==0 ? "rb" : "a+b");
  if (fp!=NULL)
  {
    open_results[0] = (int64_t)errno;
  }
  // NYI: UNDER DEVELOPMENT: not available on windows: fcntl(fileno(fp), F_SETFD, FD_CLOEXEC);
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

int32_t fzE_mtx_lock(void *mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_mutex_lock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_mtx_trylock(void *mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_mutex_trylock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_mtx_unlock(void *mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_mutex_unlock((pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

void fzE_mtx_destroy(void *mtx) {
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

int32_t fzE_cnd_signal(void *cnd) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_cond_signal((pthread_cond_t *)cnd) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_cnd_broadcast(void *cnd) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_cond_broadcast((pthread_cond_t *)cnd) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

int32_t fzE_cnd_wait(void *cnd, void *mtx) {
#ifdef FUZION_ENABLE_THREADS
  return pthread_cond_wait((pthread_cond_t *)cnd, (pthread_mutex_t *)mtx) == 0 ? 0 : -1;
#else
  return 0;
#endif
}

void fzE_cnd_destroy(void *cnd) {
#ifdef FUZION_ENABLE_THREADS
  pthread_cond_destroy((pthread_cond_t *)cnd);
  // NYI: free(cnd);
#else
#endif
}


int32_t fzE_file_read(void * file, void * buf, int32_t size)
{
  int fno = fileno(file);
  fcntl(fno, F_SETFL, O_NONBLOCK);

  struct pollfd fds;
  fds.fd = fno;
  fds.events = POLLIN;

  int ten_seconds = 10000;

  // NYI: poll is Posix only
  while(poll(&fds, 1, ten_seconds) == 0);

  size_t result = fread(buf, 1, size, (FILE*)file);

  return result > 0
    ? result
    : result == 0
    ? -1
    : -2;
}
