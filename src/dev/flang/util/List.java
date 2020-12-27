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
 * Tokiwa GmbH, Berlin
 *
 * Source of class List
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.util.LinkedList;
import java.util.Iterator;


/**
 * List provides a simple generic linked listed used throughout Fuzion, in
 * particular in the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.eu)
 */
public class List<T>
  extends LinkedList<T>
{

  /*--------------------------  constructors  ---------------------------*/


  /**
   * Constructor for empty list
   */
  public List()
  {
    super();
  }


  /**
   * Constructor for single-element list
   *
   * @param o1
   */
  public List(T o1)
  {
    super(); add(o1);
  }


  /**
   * Constructor for two-element list
   *
   * @param o1
   *
   * @param o2
   */
  public List(T o1, T o2)
  {
    super();
    add(o1);
    add(o2);
  }


  /**
   * Constructor for three-element list
   *
   * @param o1
   *
   * @param o2
   *
   * @param o3
   */
  public List(T o1, T o2, T o3)
  {
    super();
    add(o1);
    add(o2);
    add(o3);
  }



  /**
   * Constructor for list consisting of one head element o and a tail l
   *
   * @param o
   *
   * @param l
   *
   * @param o3
   */
  public List(T o, List<T> l)
  {
    super();
    add(o);
    for (T x : l)
      {
        add(x);
      }
  }


  /**
   * Constructor that adds eleemnts of given iterator.
   *
   * @param i
   */
  public List(Iterator<T> i)
  {
    super();
    addAll(i);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString
   *
   * @return
   */
  public String toString()
  {
    return toString(", ","");
  }


  /**
   * toString
   *
   * @param term
   *
   * @return
   */
  public String toString(String term)
  {
    return toString("",term);
  }


  /**
   * toString
   *
   * @param sep
   *
   * @param term
   *
   * @return
   */
  public String toString(String sep,String term)
  {
    StringBuffer res = new StringBuffer();
    Iterator it = iterator();
    if (it.hasNext())
      {
        res.append(it.next());
        while (it.hasNext())
          {
            res
              .append(sep)
              .append(term)
              .append(it.next());
          }
        res.append(term);
      }
    return res.toString();
  }


  /**
   * addH
   *
   * @param o
   */
  public void addH(T o)
  {
    addFirst(o);
  }


  /**
   * addAllH
   *
   * @param l
   */
  public <X extends T> void addAllH(List<X> l)
  {
    Object o;
    while (!l.isEmpty())
      {
        addH(l.removeLast());
      }
  }


  /**
   * addAll adds all elements produced by the given Iterator.
   *
   * @param i an iterator
   */
  public <X extends T> void addAll(Iterator<X> i)
  {
    while (i.hasNext())
      {
        add(i.next());
      }
  }

}

/* end of file */
