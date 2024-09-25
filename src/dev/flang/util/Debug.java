package dev.flang.util;

import java.util.TreeSet;

public class Debug
{

  public static TreeSet<String> _uprinted_ = new TreeSet<>();


  /**
   * Print `s` to `System.err` unless this was printed before.
   *
   * This is useful to reduce noise in debug output.
   */
  public static synchronized void uprintln(String s)
  {
    if (!_uprinted_.contains(s))
      {
        _uprinted_.add(s);
        System.err.println(s);
      }
  }

  /**
   * Print caller's method name followed by `": " + s` using uprintln.
   */
  public static synchronized void umprintln(String s)
  {
    uprintln(getCallerMethodName()+": "+s);
  }


  public static String getCallerMethodName() {
    return StackWalker.getInstance()
      .walk(s -> s.skip(2).findFirst())
      .get()
      .toString();
  }

}
