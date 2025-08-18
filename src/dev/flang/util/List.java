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
import java.util.function.Predicate;
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
   */
  public List(T o, List<T> l)
  {
    super();
    add(o);
    addAll(l);
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
   * @param l
   */
  public List(List<T> l)
  {
    super();
    addAll(l);
  }


  /**
   * Constructor that adds elements of given List.
   *
   * @param l
   *
   * @param t
   */
  public List(T[] l, T t)
  {
    super();
    addAll(l);
    add(t);
  }


  /**
   * Constructor that adds elements of given array
   *
   * @param i
   */
  @SuppressWarnings("unchecked")
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
   * add adds given element to the list
   *
   * @param e element to add
   */
  @Override
  public boolean add(T e)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (!isFrozen());

    return super.add(e);
  }


  /**
   * addAfterUnfreeze adds given element to the list after possibly cloning the
   * list in case it was frozen.
   *
   * This permits use of pre-allocated lists that are shared and frozen (e.g.,
   * empty lists), and create a local clone in case one such instance is
   * changed.
   *
   * @param e element to add
   *
   * @return this if !isFrozen(), a clone of this otherwise, in any case with e
   * added.
   */
  public List<T> addAfterUnfreeze(T e)
  {
    var result = isFrozen() ? clone() : this;
    result.add(e);
    return result;
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
  @SuppressWarnings("unchecked")
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
   * Get first element of the list, null if list is empty.
   */
  public T getFirstOrNull()
  {
    return size() == 0 ? null : get(0);
  }


  /**
   * Get last element of the list.
   */
  public T getLast()
  {
    return get(size()-1);
  }


  /**
   * Remove the last element of the list.
   */
  public T removeLast()
  {
    return remove(size()-1);
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
   * Get an element form given index in this list if that List is long enough.
   *
   * @param i index of element to set
   *
   * @return the element at index i or null if size() {@literal <=} i.
   *
   * @throws IndexOutOfBoundsException if i &lt; 0.
   */
  public T getIfExists(int i)
  {
    return size() > i ? get(i) : null;
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
   * Set an element of this list using {@code set(i,x)}, but first make sure the
   * list's capacity is sufficient.
   *
   * @param i index of element to set
   *
   * @param x the new value for the element
   *
   * @return the previous value of the element
   */
  public T force(int i, T x)
  {
    if (ANY.PRECONDITIONS) ANY.require
      (!isFrozen() || get(i) == x);

    ensureCapacity(i+1);
    while (size() <= i)
      {
        add(null);
      }
    return set(i, x);
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


  /**
   * Create a mapping of this list by applying f to all elements and
   * concatenating the resulting lists.
   *
   * @return a new list that is the concatenation of the results of applying f
   * to all elements.
   */
  public List<T> flatMap(Function<T,List<T>> f)
  {
    var result = this;
    for (var i = 0; i < size(); i++)
      {
        var e = get(i);
        var l = f.apply(e);
        if (result != this)
          {
            result.addAll(l);
          }
        else if (l.size() != 1 || e != l.getFirst())
          {
            result = take(i);
            result.addAll(l);
          }
      }
    return result;
  }


  /**
   * Create a mapping of this list by applying f to all elements
   *
   * @return A new list of the same length, containing the result of f applied to each element.
   */
  public <V> List<V> map2(Function<T,V> f)
  {
    var result = new List<V>();
    for (var i = 0; i < size(); i++)
      {
        result.add(f.apply(get(i)));
      }
    return result;
  }


  /**
   * Filter elements that match a given predicate.
   *
   * @return this (if the predicate holds for all elements) or a new list with
   * only those elements that tested true.
   */
  public List<T> filter(Predicate<T> f)
  {
    var result = this;
    for (var i = 0; i < size(); i++)
      {
        var e = get(i);
        var pass = f.test(e);
        if (pass && result != this)
          {
            result.add(e);
          }
        else if (!pass && result == this)
          {
            result = take(i);
          }
      }
    return result;
  }


  /**
   * Create a new list of the first n elements
   *
   * @param n the number of elements to put into new list
   *
   * @return new list of the length max(n, this.length()), containing get(0) .. get(n-1).
   */
  public List<T> take(int n)
  {
    var result = new List<T>();
    for (var i = 0; i < n; i++)
      {
        result.add(get(i));
      }
    return result;
  }


  /**
   * Create a new list without the first n elements
   *
   * @param n the number of elements to drop
   *
   * @return new list of the length max(0, this.length()-n), containing get(n) .. get(length()-1).
   */
  public List<T> drop(int n)
  {
    var result = new List<T>();
    for (var i = n; i < size(); i++)
      {
        result.add(get(i));
      }
    return result;
  }


  /**
   * Create a String by applying f to all elements and concatenating the result
   * in order.
   *
   * @param f function that maps an element to a string
   *
   * @return "" + f.apply(get(0)) +  f.apply(get(1)) + ... + f.apply(get(size()-1))
   */
  public String toString(Function<T,String> f)
  {
    var result = new StringBuilder();
    for (var e : this)
      {
        result.append(f.apply(e));
      }
    return result.toString();
  }

}

/* end of file */
