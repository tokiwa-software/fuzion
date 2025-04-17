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

#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>
#include <winbase.h>
#include <synchapi.h> // WaitForSingleObject
#include <namedpipeapi.h>

#ifdef FUZION_ENABLE_THREADS
// NYI remove POSIX imports
#include <pthread.h>
#endif

#include "fz.h"

static_assert(sizeof(L'\0') == 2, "wide char, unexpected bytes");


/**
 * convert utf-8 string to wide string
 * NOTE: caller is responsible for freeing the memory
 */
wchar_t* utf8_to_wide_str(const char* str)
{
  int wideCharLen = MultiByteToWideChar(CP_UTF8, 0, str, -1, NULL, 0);
  assert(wideCharLen != 0);
  wchar_t* wideStr = (wchar_t*)malloc(wideCharLen * sizeof(wchar_t));
  MultiByteToWideChar(CP_UTF8, 0, str, -1, wideStr, wideCharLen);
  return wideStr;
}

// zero memory
void fzE_mem_zero(void *dest, size_t sz)
{
  SecureZeroMemory(dest, sz);
}

// thread local to hold the last
// error that occurred in fuzion runtime.
_Thread_local int64_t last_error = 0;


// returns the latest error number of
// the current thread
int64_t fzE_last_error(void){
  // NYI: CLEANUP:
  return last_error == 0
    ? GetLastError()
    : last_error;
}

// NYI missing set_last_error, see posix.c

// make directory, return zero on success
int fzE_mkdir(const char *pathname){
  wchar_t* wideStr = utf8_to_wide_str(pathname);
  int result = CreateDirectoryW(wideStr, NULL)
    ? 0
    : -1;
  free(wideStr);
  return result;
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
    WIN32_FIND_DATAW findData;
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

  wchar_t* wideStr = utf8_to_wide_str(searchPath);
  dir->handle = FindFirstFileW(wideStr, &dir->findData);
  free(wideStr);

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
  while ((res = FindNextFileW(d->handle, &d->findData)) &&
        // skip dot and dot-dot paths.
        (wcscmp(d->findData.cFileName, L".") == 0 || wcscmp(d->findData.cFileName, L"..") == 0));

  if (!res) {
    return GetLastError() == ERROR_NO_MORE_FILES ? 0 : -1;
  }
  else {
    int sizeNeeded = WideCharToMultiByte(CP_UTF8, 0, d->findData.cFileName, -1, NULL, 0, NULL, NULL);
    assert (sizeNeeded != 0);
    assert(sizeNeeded >= 0 && sizeNeeded<1024); // NYI:

    int len = WideCharToMultiByte(CP_UTF8, 0, d->findData.cFileName, -1, result, sizeNeeded, NULL, NULL) - 1;

    return len == 0
      ? -1
      : len;
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
  LARGE_INTEGER size;
  if (!GetFileSizeEx((HANDLE)file, &size) || size.QuadPart > LONG_MAX) {
      return -1;
  }
  return (long)size.QuadPart;
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

  if ((unsigned long)fzE_get_file_size(file) < (offset + size)){
    result[0] = -1;
    return NULL;
  }

  /* "If dwMaximumSizeLow and dwMaximumSizeHigh are 0 (zero), the maximum size of the file mapping
      object is equal to the current size of the file that hFile identifies.
      An attempt to map a file with a length of 0 (zero) fails with an error code
      of ERROR_FILE_INVALID. Applications should test for files with a length of 0 (zero) and reject those files."
  */
  HANDLE file_mapping_handle = CreateFileMapping(file, NULL, PAGE_READWRITE, 0, 0, NULL);
  if (file_mapping_handle == NULL) {
    result[0] = -1;
    return NULL;
  }

  void * mapped_address = MapViewOfFile(file_mapping_handle, FILE_MAP_ALL_ACCESS, high_word(offset), low_word(offset), size);

  CloseHandle(file_mapping_handle);

  result[0] = mapped_address == NULL ? -1 : 0;

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
  int result = -1;

  wchar_t* wideStr = utf8_to_wide_str(path);

  if (DeleteFileW(wideStr)) {
    result = 0;
  }
  else if (RemoveDirectoryW(wideStr)) {
    result = 0;
  }

  free(wideStr);

  return result;
}


/**
 * Get file status (resolves symbolic links)
 */
int fzE_stat(const char *pathname, int64_t * metadata)
{
  int result = -1;

  WIN32_FILE_ATTRIBUTE_DATA fileInfo;

  wchar_t* wideStr = utf8_to_wide_str(pathname);

  if (GetFileAttributesExW(wideStr, GetFileExInfoStandard, &fileInfo)) {
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

    result = 0;
  }
  else {
    metadata[0] = (int64_t)GetLastError();
    metadata[1] = 0LL;
    metadata[2] = 0LL;
    metadata[3] = 0LL;
    result = -1;
  }

  free(wideStr);

  return result;
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
#ifdef FUZION_ENABLE_THREADS
  pthread_mutexattr_t attr;
  fzE_mem_zero(&fzE_global_mutex, sizeof(fzE_global_mutex));
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


// combine NULL-terminated UTF-8 string array into wide string
wchar_t *build_unicode_args(char *args[], size_t argsLen) {
  size_t totalLen = 2;
  for (size_t i = 0; i < argsLen - 1; ++i) {
    totalLen += MultiByteToWideChar(CP_UTF8, 0, args[i], -1, NULL, 0)+1;
  }
  wchar_t *cmd = (wchar_t *)malloc(totalLen * sizeof(wchar_t));
  assert(!!cmd);

  // wcscat requires the destination string to be null-terminated.
  cmd[0] = L'\0';

  for (size_t i = 0; i < argsLen - 1; ++i) {
    wchar_t *warg = utf8_to_wide_str(args[i]);
    if (!warg) {
      free(cmd);
      return NULL;
    }
    wcscat(cmd, L"\"");
    wcscat(cmd, warg);
    wcscat(cmd, L"\" ");
    free(warg);
  }

  return cmd;
}



wchar_t *build_unicode_environment_block(char *env[], size_t envLen) {
  size_t totalLen = 2; // Final null terminators
  for (size_t i = 0; i < envLen - 1; ++i) {
    totalLen += MultiByteToWideChar(CP_UTF8, 0, env[i], -1, NULL, 0)+1;
  }

  wchar_t *envBlock = (wchar_t *)malloc(totalLen * sizeof(wchar_t));
  assert(!!envBlock);

  wchar_t *ptr = envBlock;
  for (size_t i = 0; i < envLen - 1; ++i) {
    wchar_t *wenv = utf8_to_wide_str(env[i]);
    if (!wenv) {
      free(envBlock);
      return NULL;
    }
    size_t len = wcslen(wenv);
    wcscpy(ptr, wenv);
    ptr += len + 1;
    free(wenv);
  }
  // A Unicode environment block is terminated by four zero bytes:
  // two for the last string, two more to terminate the block.
  ptr[0] = 0;
  ptr[1] = 0;

  return envBlock;
}


int fzE_process_create(char *args[], size_t argsLen, char *env[], size_t envLen, int64_t *result) {

  // Programmatically controlling which handles are inherited by new processes in Win32
  // https://devblogs.microsoft.com/oldnewthing/20111216-00/?p=8873

  HANDLE hStdinRead, hStdinWrite;
  HANDLE hStdoutRead, hStdoutWrite;
  HANDLE hStderrRead, hStderrWrite;

  SECURITY_ATTRIBUTES saAttr = {
      .nLength = sizeof(SECURITY_ATTRIBUTES),
      .bInheritHandle = TRUE,
      .lpSecurityDescriptor = NULL
  };

  if (!CreatePipe(&hStdinRead, &hStdinWrite, &saAttr, 0) ||
      !CreatePipe(&hStdoutRead, &hStdoutWrite, &saAttr, 0) ||
      !CreatePipe(&hStderrRead, &hStderrWrite, &saAttr, 0)) {
      return -1;
  }

  SetHandleInformation(hStdinWrite, HANDLE_FLAG_INHERIT, 0);
  SetHandleInformation(hStdoutRead, HANDLE_FLAG_INHERIT, 0);
  SetHandleInformation(hStderrRead, HANDLE_FLAG_INHERIT, 0);

  // we resolve app path manually, because we
  // want to pass empty env where PATH is not set
  // so app would not be found when passing
  // via args
  wchar_t *app = utf8_to_wide_str(args[0]);
  WCHAR resolvedPath[MAX_PATH];
  DWORD spw = SearchPathW(
    NULL,
    app,
    L".exe",
    MAX_PATH,
    resolvedPath,
    NULL
  );
  free(app);
  if (spw == 0) {
    last_error = ERROR_FILE_NOT_FOUND;
    return -1;
  }

  wchar_t *args_w = build_unicode_args(args, argsLen);
  wchar_t *envBlock = build_unicode_environment_block(env, envLen);

  if (!args_w) {
    last_error = ERROR_INVALID_NAME;
    return -1;
  }

  PROCESS_INFORMATION pi;
  STARTUPINFOW si = {0};
  si.cb = sizeof(STARTUPINFOW);
  si.dwFlags |= STARTF_USESTDHANDLES;
  si.hStdInput = hStdinRead;
  si.hStdOutput = hStdoutWrite;
  si.hStdError = hStderrWrite;

  BOOL success = CreateProcessW(
    resolvedPath,
    args_w,
    NULL,
    NULL,
    TRUE,
    CREATE_UNICODE_ENVIRONMENT,
    envBlock,
    NULL,
    &si,
    &pi
  );

  CloseHandle(hStdinRead);
  CloseHandle(hStdoutWrite);
  CloseHandle(hStderrWrite);

  free(args_w);
  free(envBlock);

  if (!success) {
    last_error = GetLastError();
    return -1;
  }

  CloseHandle(pi.hThread);

  result[0] = (int64_t)pi.hProcess;
  result[1] = (int64_t)hStdinWrite;
  result[2] = (int64_t)hStdoutRead;
  result[3] = (int64_t)hStderrRead;

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
  assert(mode >= 0 && mode <= 2);

  SECURITY_ATTRIBUTES sa = {0};
  sa.nLength = sizeof(SECURITY_ATTRIBUTES);
  sa.bInheritHandle = FALSE;
  sa.lpSecurityDescriptor = NULL;

  wchar_t* file_name_w = utf8_to_wide_str(file_name);

  HANDLE hFile = CreateFileW(
      file_name_w,
      GENERIC_READ | GENERIC_WRITE,
      FILE_SHARE_READ,
      &sa,
      OPEN_ALWAYS,
      FILE_ATTRIBUTE_NORMAL | FILE_FLAG_SEQUENTIAL_SCAN,
      NULL
  );

  open_results[0] = hFile == INVALID_HANDLE_VALUE
    ? (int64_t)GetLastError()
    : 0;
  return (void *)hFile;
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
  DWORD bytesRead = 0;
  BOOL success = ReadFile(file, buf, (DWORD)size, &bytesRead, NULL);

  return !success
    ? -2 // ERROR
    : bytesRead == 0
    ? -1 // EOF
    : (int32_t)bytesRead;
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
void fzE_date_time(void * result)
{
  time_t rawtime;
  LARGE_INTEGER counter;
  time(&rawtime);
  QueryPerformanceCounter(&counter);
  struct tm * ptm = gmtime(&rawtime);
  ((int32_t *)result)[0] = ptm->tm_year+1900;
  ((int32_t *)result)[1] = ptm->tm_mon+1;
  ((int32_t *)result)[2] = ptm->tm_mday;
  ((int32_t *)result)[3] = ptm->tm_hour;
  ((int32_t *)result)[4] = ptm->tm_min;
  ((int32_t *)result)[5] = ptm->tm_sec;
  long long billion = 1000000000;
  // NYI: UNDER DEVELOPMENT: check if there is a better way
  ((int32_t *)result)[6] = (int32_t)(counter.QuadPart % billion);
}


int32_t fzE_file_write(void * file, void * buf, int32_t size)
{
  DWORD written = 0;
  BOOL result = WriteFile((HANDLE)file, buf, (DWORD)size, &written, NULL);
  return result
    ? (int32_t)written
    : -1;
}

int32_t fzE_file_move(const char *oldpath, const char *newpath)
{
  wchar_t* oldpath_w = utf8_to_wide_str(oldpath);
  wchar_t* newpath_w = utf8_to_wide_str(newpath);
  int32_t result = MoveFileW(oldpath_w, newpath_w) ? 0 : -1;
  free(newpath_w);
  free(oldpath_w);
  return result;
}



int32_t fzE_file_close(void *file)
{
  return CloseHandle((HANDLE)file)
    ? 0
    : -1;
}

int32_t fzE_file_seek(void *file, int64_t offset)
{
  LARGE_INTEGER li;
  li.QuadPart = offset;
  return SetFilePointerEx((HANDLE)file, li, NULL, FILE_BEGIN)
    ? 0
    : -1;
}

int64_t fzE_file_position(void *file)
{
  LARGE_INTEGER pos;
  LARGE_INTEGER zero = {0};
  return (SetFilePointerEx((HANDLE)file, zero, &pos, FILE_CURRENT))
    ? pos.QuadPart
    : -1;
}

int32_t fzE_file_flush(void *file)
{
  return FlushFileBuffers((HANDLE)file)
    ? 0
    : -1;
}

void * fzE_file_stdin (void) { return GetStdHandle(STD_INPUT_HANDLE); }
void * fzE_file_stdout(void) { return GetStdHandle(STD_OUTPUT_HANDLE); }
void * fzE_file_stderr(void) { return GetStdHandle(STD_ERROR_HANDLE); }
