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


  /*-----------------------------  methods  -----------------------------*/


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
            var duplicates = new HashSet<StackTraceElement>();
            for (var s : t.getStackTrace())
              {
                if (!duplicates.contains(s))
                  {
                    duplicates.add(s);
                    _results_.put(s, _results_.getOrDefault(s, 0) + 1);
                  }
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
              System.out.println("Fuzion sample-based Java profiling results:");
              var format = "%" + _results_.get(s[s.length-1]).toString().length() + "d";
              for(var m : s)
                {
                  System.out.println("PROF: "+String.format(format, _results_.get(m)) + ": " + m);
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
    if (Profiler._samplingFrequency_ != 0)
      {
        installShutdownHook();
        startSampler();
      }
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
