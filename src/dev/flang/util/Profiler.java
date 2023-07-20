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
 * Source of class Profiler
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.awt.Desktop;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


/**
 * Profiler measures the execution time in Java code by sampling
 * repeatedly at a fixed interval.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class Profiler extends ANY
{


  /*----------------------------  constants  ----------------------------*/


  /**
   * Sampling frequency, in Hz.
   */
  private static int DEFAULT_SAMPLING_FREQUENCY = 100;



  /**
   * Command to be excecuted to create flame graph
   */
  private static String FLAMEGRAPH_PL = "flamegraph.pl";


  /*----------------------------  variables  ----------------------------*/


  /**
   * Number of samples taken per second:
   */
  private static int _samplingFrequency_ = DEFAULT_SAMPLING_FREQUENCY;


  /**
   * Flag indicating that sampling should happen.  Set to false when printing
   * results.
   */
  private static volatile boolean _running_ = true;


  /**
   * Sample count for method with given name
   */
  private static Map<StackTraceElement, Integer> _results_ = new HashMap<>();


  /**
   * File to write data to, either a .prof file (for classic profile data), a
   * .txt file that serves as input to generate a flame graph of a .svg file
   * which is the flame graph.
   */
  private static String _file = null;


  /**
   * In case we collectFlameGraphData(), this collects the unique lines in
   * temporal order
   *
   * The lines consist of a ";"-separated string of "class.method" strings
   * created from the call stack.
   */
  private static ArrayList<String> _resultsForFlameGraphKeys_ = new ArrayList<>();


  /**
   * In case we collectFlameGraphData(), this collects the counts for each line
   * in unique order.
   *
   * The lines consist of a ";"-separated string of "class.method" strings
   * created from the call stack.
   */
  private static Map<String, Integer> _resultsForFlameGraph_ = new HashMap<>();


  /**
   * Desktop instance to display flamegraph results.  Since the flame graph is
   * created in the shutdown hook and desktop itself can not be created during
   * shutdown, we create this early.
   */
  static Desktop _desktop;


  /*-----------------------------  methods  -----------------------------*/


  /**
   * There are two modes of operation: creating flame graph data (which are
   * single lines for each sample of the form
   *
   *   main;abc;def 1345
   *
   * to be processed by flame graph.pl, or classic profile output showing a
   * single line for each source code that occurred during a sample sorted by
   * frequency.
   *
   * @return true to collect frame graph data, false to collect classic data.
   */
  static boolean collectFlameGraphData()
  {
    return _file == null || !_file.endsWith(".prof");
  }

  /**
   * Should the flame graph be open in the default application?
   */
  static boolean showFlameGraph()
  {
    return _file == null;
  }


  /**
   * takeSample checks what methods are currently running and increments their
   * counters.
   */
  private static void takeSample(ThreadGroup g)
  {
    var ac = g.activeCount();
    var list = new Thread[ac];
    g.enumerate(list);
    for (var t : list)
      {
        if (t != null                   &&
            t != Thread.currentThread() &&
            t.getState() == Thread.State.RUNNABLE)
          {
            var st = t.getStackTrace();
            if (!collectFlameGraphData())
              {
                var duplicates = new HashSet<StackTraceElement>();
                for (var s : st)
                  {
                    if (!duplicates.contains(s))
                      {
                        duplicates.add(s);
                        _results_.put(s, _results_.getOrDefault(s, 0) + 1);
                      }
                  }
              }
            else
              {
                StringBuilder sb = new StringBuilder();
                for (var i = st.length-1; i>0; i--)
                  {
                    var s = st[i];
                    if (sb.length() > 0)
                      {
                        sb.append(";");
                      }
                    sb.append(s.getClassName())
                      .append(".")
                      .append(s.getMethodName());
                  }
                var key = sb.toString();
                var c = _resultsForFlameGraph_.getOrDefault(key, 0);
                if (c == 0)
                  {
                    _resultsForFlameGraphKeys_.add(key);
                  }
                _resultsForFlameGraph_.put(key, c + 1);
              }
          }
      }
  }


  /**
   * startSampler creates and starts thread for sampling.
   */
  public static void startSampler() {
    new Thread("Fuzion Java Profiler")
    {
      public void run()
      {
        while (_running_)
          {
            try
              {
                var t = 1000000000L / _samplingFrequency_;
                Thread.sleep(t / 1000000L, (int) (t % 1000000));
              }
            catch (InterruptedException e)
              { // ignore
              }
            synchronized (_results_)
              {
                if (_running_)
                  {
                    takeSample(getThreadGroup());
                  }
              }
          }
      }

      {
        setDaemon(true);
        start();
      }

    };
  }


  /**
   * install a shutdown hook to output the profile results:
   */
  private static void installShutdownHook()
  {
    Runtime.getRuntime().addShutdownHook(new Thread()
      {
        public void run()
        {
          synchronized (_results_)
            {
              _running_ = false;
            }
          if (!collectFlameGraphData())
            {
              StackTraceElement[] s = (StackTraceElement[]) _results_.keySet().toArray(new StackTraceElement[0]);
              var c = new Comparator<>()
                {
                  public int compare(Object o1, Object o2)
                  {
                    return _results_.get(o1) - _results_.get(o2);
                  }
                };
              Arrays.sort(s,c);
              if (s.length > 0)
                {
                  System.out.println(" + " + _file);
                  try (PrintWriter out = new PrintWriter(_file))
                    {
                      out.println("Fuzion sample-based Java profiling results:");
                      var format = "%" + _results_.get(s[s.length-1]).toString().length() + "d";
                      for(var m : s)
                        {
                          out.println("PROF: "+String.format(format, _results_.get(m)) + ": " + m);
                        }
                    }
                  catch (IOException e)
                    {
                      Errors.error("could not save profile data to `" + _file + "`: " + e);
                    }
                }
            }
          else
            {
              StringBuilder result = new StringBuilder();
              for (var key : _resultsForFlameGraphKeys_)
                {
                  result.append(key)
                    .append(" ")
                    .append(_resultsForFlameGraph_.get(key))
                    .append("\n");
                }
              if (_file != null && _file.equals("-"))
                {
                  System.out.print(result);
                }
              else if (_file != null && !_file.endsWith(".svg"))
                {
                  System.out.println(" + " + _file);
                  try (PrintWriter out = new PrintWriter(_file))
                    {
                      out.print(result);
                    }
                  catch (IOException e)
                    {
                      Errors.error("could not save profile data to `" + _file + "`: " + e);
                    }
                }
              else
                {
                  var pid = ProcessHandle.current().pid();
                  try
                    {
                      var tempFile = File.createTempFile("pid"+pid+"-fuzion-XjavaProf-", ".txt");
                      try (PrintWriter out = new PrintWriter(new FileOutputStream(tempFile)))
                        {
                          out.print(result);
                        }
                      tempFile.deleteOnExit();
                      ProcessBuilder pb = new ProcessBuilder();
                      pb.redirectInput(tempFile);
                      var svg = _file != null
                        ? new File(_file)
                        : (false // we cannot use File.createTempFile since /tmp seems not to be accessible by browser
                           ? File.createTempFile("pid"+pid+"-fuzion-XjavaProf-", ".svg")
                           : new File("pid"+pid+"-fuzion-XjavaProf-flamegraph.svg"));
                      System.out.println(" + " + svg);
                      pb.redirectOutput(svg);
                      pb.command(FLAMEGRAPH_PL);
                      pb.start().waitFor();
                      if (_desktop != null && _desktop.isSupported(Desktop.Action.BROWSE))
                        {
                          _desktop.browse(svg.toURI());
                        }
                    }
                  catch (IOException e)
                    {
                      Errors.error("could not create flame graph: " + e + "\n" +
                                   "Check that `" + FLAMEGRAPH_PL + "` is present in current environment (see https://github.com/brendangregg/FlameGraph/).");
                    }
                  catch (InterruptedException e)
                    {
                      Errors.error("could not save profiling data: " + e);
                    }
                }
            }
        }
      });
  }


  /**
   * start profiling in a parallel thread and print results on VM shutdown.
   */
  public static void start()
  {
    if (showFlameGraph())
      { // We cannot create Desktop in in shutdown hook, so we have to do it early:
        _desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
      }
    if (Profiler._samplingFrequency_ != 0)
      {
        installShutdownHook();
        startSampler();
      }
  }


  /**
   * start profiling in a parallel thread and save the results as a text file in
   * the flamegraph.pl input format or, if file.endsWith(".svg"), directly
   * create a flame graph using "flamegraph.pl".
   */
  public static void start(String file)
  {
    _file = file;
    start();
  }


  /**
   * start profiling in a parallel thread and print results on VM shutdown.
   *
   * @param f the sampling frequency, Hz.
   */
  public static void start(int f)
  {
    if (PRECONDITIONS) require
      (f >= 0 && f < 10000);

    Profiler._samplingFrequency_ = f;
    start();
  }

}

/* end of file */
