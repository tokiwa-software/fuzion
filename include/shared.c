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




/**
 * get a unique id > 0
 */
uint64_t fzE_unique_id()
{
  static atomic_uint_least64_t last_id = 0;
  return ++last_id;
}


extern inline uint8_t fzE_mapped_buffer_get(void * addr, int64_t idx)
{
  return ((uint8_t *)addr)[idx];
}

extern inline void fzE_mapped_buffer_set(void * addr, int64_t idx, uint8_t x)
{
  ((uint8_t *)addr)[idx] = x;
}

extern inline void * fzE_null(void)
{
  return NULL;
}

extern inline int fzE_is_null(void * p)
{
  return p == NULL
    ? 0
    : -1;
}

