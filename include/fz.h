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

#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>     // setenv, unsetenv
#include <sys/stat.h>   // mkdir
#include <sys/types.h>  // mkdir


#if _WIN32

// "For example if you want to use winsock2.h you better make sure
// WIN32_LEAN_AND_MEAN is always defined because otherwise you will
// get conflicting declarations between the WinSock versions."
// https://stackoverflow.com/questions/11040133/what-does-defining-win32-lean-and-mean-exclude-exactly#comment108482188_11040230
#define WIN32_LEAN_AND_MEAN

#include <winsock2.h>
#include <windows.h>
#include <ws2tcpip.h>

#else

#include <sys/socket.h> // socket, bind, listen, accept, connect
#include <sys/ioctl.h>  // ioctl, FIONREAD
#include <netinet/in.h> // AF_INET
#include <poll.h>       // poll
#include <sys/mman.h>   // mmap
#include <fcntl.h>      // fcntl
#include <unistd.h>     // close
#include <netdb.h>      // getaddrinfo

#endif


static inline void *fzE_malloc_safe(size_t size) {
#ifdef GC_H
  void *p = GC_MALLOC(size);
#else
  void *p = malloc(size);
#endif
  if (p == NULL) {
    fprintf(stderr, "*** malloc(%zu) failed ***\n", size);
    exit(1);
  }
  return p;
}


// make directory, return zero on success
int fzE_mkdir(const char *pathname){
#if _WIN32
  // should we CreateDirectory here?
  return mkdir(pathname);
#else
  return mkdir(pathname, S_IRWXU);
#endif
}



// set environment variable, return zero on success
int fzE_setenv(const char *name, const char *value, int overwrite){
#if _WIN32
  // setenv is posix only
  return -1;
#else
  return setenv(name, value, overwrite);
#endif
}



// unset environment variable, return zero on success
int fzE_unsetenv(const char *name){
#if _WIN32
  // unsetenv is posix only
  return -1;
#else
  return unsetenv(name);
#endif
}

// 0 = blocking
// 1 = none_blocking
int fzE_set_blocking(int sockfd, int blocking)
{
#ifdef _WIN32
  u_long b = blocking;
  return ioctlsocket(sockfd, FIONBIO, &b);
#else
  int flag = blocking == 1
    ? fcntl(sockfd, F_GETFL, 0) | O_NONBLOCK
    : fcntl(sockfd, F_GETFL, 0) & ~O_NONBLOCK;

  return fcntl(sockfd, F_SETFL, flag);
#endif
}

// helper function to retrieve
// the last error that occurred.
int fzE_net_error()
{
#ifdef _WIN32
  return WSAGetLastError();
#else
  return errno;
#endif
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
#ifdef _WIN32
  closesocket(sockfd);
  WSACleanup();
  return fzE_net_error();
#else
  return ( close(sockfd) == - 1 )
    ? fzE_net_error()
    : 0;
#endif
}

// initialize a new socket for given
// family, socket_type, protocol
int fzE_socket(int family, int type, int protocol){
#ifdef _WIN32
  WSADATA wsaData;
  if ( WSAStartup(MAKEWORD(2,2), &wsaData) != 0 ) {
    return -1;
  }
#endif
  return socket(get_family(family), get_socket_type(type), get_protocol(protocol));
}


// get addrinfo structure used for binding/connection of a socket.
int fzE_getaddrinfo(int family, int socktype, int protocol, int flags, char * host, char * port, struct addrinfo ** result){
  struct addrinfo hints;

#ifdef _WIN32
  ZeroMemory(&hints, sizeof(hints));
#else
  memset(&hints, 0, sizeof hints);
#endif
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
#ifdef _WIN32
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
#else
  return recvfrom( sockfd, buf, count, 0, NULL, NULL );
#endif
}

// write buf to sockfd
// may block if socket is set to blocking.
// return error code or zero on success
int fzE_write(int sockfd, const void * buf, size_t count){
return ( sendto( sockfd, buf, count, 0, NULL, 0 ) == -1 )
  ? fzE_net_error()
  : 0;
}

#ifdef _WIN32
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
#endif


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

#ifdef _WIN32
  HANDLE file_handle = (HANDLE)_get_osfhandle(fileno(file));

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
#else
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
#endif
}


// unmap an address that was previously mapped by fzE_mmap
// -1 error, 0 success
int fzE_munmap(void * mapped_address, const int file_size){
#ifdef _WIN32
  return UnmapViewOfFile(mapped_address)
    ? 0
    : -1;
#else
  return munmap(mapped_address, file_size);
#endif
}


/**
 * Perform bitwise comparison of two float values. This is used by
 * concur.atmic.compare_and_swap/set to compare floats. In particular, this
 * results is unequality of +0 and -0 and equality of NaN unless undefined bits
 * differ, etc.
 *
 * NYI: CLEANUP #2122: Move impleementation to fz.c / fzlib.o or similar!
 *
 * @param f1, f2 two float values
 *
 * @return true iff f1 and f2 are represented in memory by the same bit patternsx.
 */
bool fzE_bitwise_compare_float(float f1, float f2)
{
  union
  {
    float f;
    int32_t bits;
  } v1, v2;
  v1.f = f1;
  v2.f = f2;
  return v1.bits == v2.bits;
}


/**
 * Perform bitwise comparison of two double values. This is used by
 * concur.atmic.compare_and_swap/set to compare floats. In particular, this
 * results is unequality of +0 and -0 and equality of NaN unless undefined bits
 * differ, etc.
 *
 * NYI: CLEANUP: #2122: Move impleementation to fz.c / fzlib.o or similar!
 *
 * @param d1, d2 two double values
 *
 * @return true iff d1 and d2 are represented in memory by the same bit patterns.
 */
bool fzE_bitwise_compare_double(double d1, double d2)
{
  union
  {
    double d;
    int64_t bits;
  } v1, v2;
  v1.d = d1;
  v2.d = d2;
  return v1.bits == v2.bits;
}


#endif /* fz.h  */
