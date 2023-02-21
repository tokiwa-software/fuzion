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


#ifndef	_FUZION_H
#define	_FUZION_H	1

#include <stdlib.h>     // setenv, unsetenv
#include <sys/stat.h>   // mkdir
#include <sys/types.h>  // mkdir


#if _WIN32
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
// Need to link with Ws2_32.lib, Mswsock.lib, and Advapi32.lib
#pragma comment (lib, "Ws2_32.lib")
#pragma comment (lib, "Mswsock.lib")
#pragma comment (lib, "AdvApi32.lib")
#else
#include <sys/socket.h> // socket, bind, listen, accept, connect
#include <sys/ioctl.h>  // ioctl, FIONREAD
#include <netinet/in.h> // AF_INET
#include <poll.h>       // poll
#endif



// int set_socket_none_blocking(int fd)
// {
//   if (fd < 0){
//     printf("a");
//     return -1;
//   }
// #ifdef _WIN32
//    return (ioctlsocket(fd, FIONBIO, 1) == 0) ? fd : -1;
// #else
//    int flags = fcntl(fd, F_GETFL, 0);
//    if (flags == -1){
//       return -1;
//    }
//    printf("success");
//    return (fcntl(fd, F_SETFL, flags & ~O_NONBLOCK) == 0) ? fd : -1;
// #endif
// }

FILE * fzE_socket(int domain, int type, int protocol){
  return fdopen(socket(domain, type, protocol), "r+b");
}

int fzE_bind(FILE * sockfd, int family, char * data, int data_len){
  struct sockaddr sa;
  memset(&sa, 0, sizeof sa);
  sa.sa_family = family;
  memcpy(sa.sa_data, data, data_len);
  return bind(fileno(sockfd), &sa, sizeof sa);
}

int fzE_listen(FILE * sockfd, int backlog){
  return listen(fileno(sockfd), backlog);
}

FILE * fzE_accept(FILE * sockfd){
  return fdopen(accept(fileno(sockfd), NULL, NULL), "r+b");
}

int fzE_connect(FILE * sockfd, int family, char * data, int data_len){
  struct sockaddr sa;
  memset(&sa, 0, sizeof sa);
  sa.sa_family = family;
  memcpy(sa.sa_data, data, data_len);
  return connect(fileno(sockfd), &sa, sizeof sa);
}


size_t fzE_bytes_available(FILE * file, size_t buf_size)
{
  size_t bytes_count = 0;
#ifdef _WIN32
  ioctlsocket(fileno(file), FIONREAD, &bytes_count);
#else
  int fd = fileno(file);
  struct pollfd pfds = {fd, POLLIN, POLLIN};
  poll(&pfds, 1, -1);
  ioctl(fd, FIONREAD, &bytes_count);
#endif
  return (bytes_count < buf_size) ?  bytes_count : buf_size;
}


#endif /* fz.h  */
