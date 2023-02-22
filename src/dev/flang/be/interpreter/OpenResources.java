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
 * Source of class OpenResources
 *
 *---------------------------------------------------------------------*/

package dev.flang.be.interpreter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This acts as container for open/active resources like files, sockets, threads etc.
 */
public abstract class OpenResources<T>
{
  private ConcurrentHashMap<Long, T> data = new ConcurrentHashMap<Long, T>();

  private AtomicLong cnt = new AtomicLong(0);

  /**
   * This abstract method allows closing a generic resource T.
   * @param t
   * @return
   */
  protected abstract boolean close(T t);

  /**
   * get a stored resource by id
   * @param id
   * @return
   */
  public T get(Long id)
  {
    return data.get(id);
  }

  /**
   * add a new resource
   * @param value
   * @return the key to identify the resource
   */
  public Long add(T value)
  {
    var id = cnt.getAndIncrement();
    while(data.containsKey(id))
      {
        id = cnt.getAndIncrement();
      }
    data.put(id, value);
    return id;
  }

  /**
   * remove a resource and close/finalize it
   * @param id
   * @return was the closing successful?
   */
  public boolean remove(Long id)
  {
    return close(data.remove(id));
  }

}

/* end of file */
