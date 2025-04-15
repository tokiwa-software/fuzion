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
 * Source of shared code of Fuzion C backend.
 *
 *---------------------------------------------------------------------*/

#ifdef GC_THREADS
#define GC_DONT_INCLUDE_WINDOWS_H
#include <gc.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <assert.h>
#include <stdatomic.h>
#include <time.h>


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


void * fzE_malloc_safe(size_t size) {
#ifdef GC_THREADS
  void *p = GC_MALLOC(size);
#else
  void *p = malloc(size);
#endif
  if (p == NULL) {
    fprintf(stderr, "*** malloc(%zu) failed ***\n", size);
    exit(EXIT_FAILURE);
  }
  return p;
}


void fzE_memcpy(void *restrict dest, const void *restrict src, size_t sz){
  // NYI: UNDER DEVELOPMENT: use bounds checked version, e.g. memcpy_s
  memcpy(dest, src, sz);
}



/*

C11 support is still limited on e.g. macOS etc.

void * fzE_mtx_init()
{
  mtx_t * mtx = fzE_malloc_safe(sizeof(mtx_t));
  return mtx_init(mtx, mtx_plain) == thrd_success
    ? (void *)mtx
    : NULL;
}

int32_t fzE_mtx_lock(void * mtx)
{
  return mtx_lock((mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_mtx_trylock(void * mtx)
{
  return mtx_trylock((mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_mtx_unlock(void * mtx)
{
  return mtx_unlock((mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

void fzE_mtx_destroy(void * mtx)
{
  mtx_destroy((mtx_t *) mtx);
  // NYI: free(mtx)
}

void * fzE_cnd_init()
{
  cnd_t * cnd = fzE_malloc_safe(sizeof(cnd));
  return cnd_init(cnd) == thrd_success
    ? (void *)cnd
    : NULL;
}

int32_t fzE_cnd_signal(void * cnd)
{
  return cnd_signal((cnd_t *) cnd) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_cnd_broadcast(void * cnd)
{
  return cnd_broadcast((cnd_t *) cnd) == thrd_success
    ? 0
    : -1;
}

int32_t fzE_cnd_wait(void * cnd, void * mtx)
{
  return cnd_wait((cnd_t *) cnd, (mtx_t *) mtx) == thrd_success
    ? 0
    : -1;
}

void fzE_cnd_destroy(void * cnd)
{
  cnd_destroy((cnd_t *) cnd);
  // NYI: free(cnd)
}

*/


/**
 * get a unique id > 0
 */
uint64_t fzE_unique_id()
{
  static atomic_uint_least64_t last_id = 0;
  return ++last_id;
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
  time(&rawtime);
  struct tm * ptm = gmtime(&rawtime);
  ((int32_t *)result)[0] = ptm->tm_year+1900;
  ((int32_t *)result)[1] = ptm->tm_mon+1;
  ((int32_t *)result)[2] = ptm->tm_mday;
  ((int32_t *)result)[3] = ptm->tm_hour;
  ((int32_t *)result)[4] = ptm->tm_min;
  ((int32_t *)result)[5] = ptm->tm_sec;
  ((int32_t *)result)[6] = 0;
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


uint8_t fzE_mapped_buffer_get(void * addr, int64_t idx)
{
  return ((uint8_t *)addr)[idx];
}

void fzE_mapped_buffer_set(void * addr, int64_t idx, uint8_t x)
{
  ((uint8_t *)addr)[idx] = x;
}

void * fzE_null(void)
{
  return NULL;
}

int fzE_is_null(void * p)
{
  return p == NULL
    ? 0
    : -1;
}

