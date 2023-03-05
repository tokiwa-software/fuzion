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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

import java.util.stream.Collector;


/**
 * List provides a simple generic linked list used throughout Fuzion, in
 * particular in the AST.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class List<T>
  extends ArrayList<T>
{

  /*----------------------------  variables  ----------------------------*/


  /**
   * Flag that indicates that this list must no longer be mutated, e.g., because
   * it is used as a key in a cache.
   */
  private boolean _isFrozen = false;


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
   * Constructor that adds elements of given List.
   *
   * @param i
   */
  public List(List<T> l)
  {
    super();
    addAll(l);
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
    Iterator<T> it = iterator();
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
   * @param itemToString
   *
   * @return
   */
  public String toString(String pre, String sep, String term, Function<T, String> itemToString)
  {
    StringBuffer res = new StringBuffer();
    Iterator<T> it = iterator();
    if (it.hasNext())
      {
        res.
          append(pre).
          append(itemToString.apply(it.next()));
        while (it.hasNext())
          {
            res
              .append(sep)
              .append(itemToString.apply(it.next()));
          }
        res.append(term);
      }
    return res.toString();
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


  /**
   * Get first element of the list.
   */
  public T getFirst()
  {
    return get(0);
  }


  /**
   * Get last element of the list.
   */
  public T getLast()
  {
    return get(size()-1);
  }


  /**
   * A collector for this List to be used in Stream.collect(...)
   * @param <U>
   * @return
   */
  public static <U> Collector<U, List<U>, List<U>> collector()
  {
    return new Collector<U, List<U>, List<U>>() {

      public Supplier<List<U>> supplier()
      {
        return List::new;
      }

      public BiConsumer<List<U>, U> accumulator()
      {
        return (list, type) -> list.add(type);
      }

      public BinaryOperator<List<U>> combiner()
      {
        return (list1, list2) -> {
          list1.addAll(list2);
          return list1;
        };
      }

      @Override
      public java.util.function.Function<List<U>, List<U>> finisher()
      {
        return (l) -> l;
      }

      @Override
      public Set<Characteristics> characteristics()
      {
        return Set.of(Characteristics.UNORDERED);
      }
    };
  }


  /**
   * Are modifications to this list forbidden?
   */
  public boolean isFrozen()
  {
    return _isFrozen;
  }


  /**
   * Forbid modifications to this list.  This should be called to ensure that a
   * list that is used as a key in a map or similar is no longer modified.
   */
  public void freeze()
  {
    _isFrozen = true;
  }


  /**
   * Set an element of this list, but check that the element is either the same
   * as before or this list is not frozen.
   *
   * @param i index of element to set
   *
   * @param x the new value for the element
   *
   * @return the previous value of the element
   */
  public T set(int i, T x)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (!isFrozen() || get(i) == x);

    return super.set(i, x);
  }


  /**
   * Set an element of this list. For a frozen list, create a clone with that
   * element changed and leave the original list unchanged.
   *
   * @param i index of element to set
   *
   * @param x the new value for the element
   *
   * @return this in case !this.isFrozen() or get(i) == x, otherwise a clone
   * of this with element at index i set to x.
   */
  public List<T> setOrClone(int i, T x)
  {
    var result = this;
    if (get(i) != x)
      {
        result = isFrozen() ? clone() : this;
        result.set(i, x);
      }
    return result;
  }


  /**
   * Create a non-frozen clone of this list. This can be redefined in
   * sub-classes to return an instance of the same sub-class of List.
   */
  public List<T> clone()
  {
    return new List<>(this);
  }


  /**
   * Create a mapping of this list by applying f to all elements
   *
   * @return this in case !this.isFrozen or f.apply(e) == e for all elements,
   * otherwise clone with result.get(i) == f.apply(get(i)).
   */
  public List<T> map(Function<T,T> f)
  {
    var g = this;
    for (var i = 0; i < size(); i++)
      {
        g = g.setOrClone(i, f.apply(get(i)));
      }
    return g;
  }

}

/* end of file */
