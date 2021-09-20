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
 * @author Fridtjof Siebert (siebert@tokiwa.software)
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
   * Constructor that adds elements of given iterator.
   *
   * @param i
   */
  public List(Iterator<T> i)
  {
    super();
    addAll(i);
  }


  /**
   * Constructor that adds elements of given array
   *
   * @param i
   */
  public List(T... i)
  {
    super();
    addAll(i);
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * toString for a list A, B, C will create
   *
   *   "A, B, C"
   *
   * @return
   */
  public String toString()
  {
    return toString("", ", ", "");
  }


  /**
   * toString for a list A, B, C will create
   *
   *   "A term" +
   *   "B term" +
   *   "C term"
   *
   * @param term
   *
   * @return
   */
  public String toString(String term)
  {
    return toString("", term, term);
  }


  /**
   * toString for a list A, B, C will create
   *
   *   "pre A sep B sep C term"
   *
   * or for an empty list
   *
   *   ""
   *
   * @param sep
   *
   * @param term
   *
   * @return
   */
  public String toString(String pre, String sep, String term)
  {
    StringBuffer res = new StringBuffer();
    Iterator it = iterator();
    if (it.hasNext())
      {
        res.
          append(pre).
          append(it.next());
        while (it.hasNext())
          {
            res
              .append(sep)
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


  /**
   * addAll adds all elements in the given array
   *
   * @param i an iterator
   */
  public void addAll(T... i)
  {
    for (T x : i)
      {
        add(x);
      }
  }

}

/* end of file */
