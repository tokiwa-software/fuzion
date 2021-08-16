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
 * Source of class FuzionLogo
 *
 *---------------------------------------------------------------------*/

package dev.flang.misc.logo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import org.jfree.svg.SVGGraphics2D;
import org.jfree.svg.SVGUtils;


/**
 * FuzionLogo is a small tool to create an SVG file of the Fuzion logo.
 *
 * It requires jfree.svg.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class FuzionLogo
{

  // file to generate
  static String FILE_NAME = "fuzionlogo.svg";

  // background and wedge colors:
  static Color BACKGROUND = Color.BLACK;
  static Color C1 = Color.WHITE;
  static Color C2 = new Color(81,36,128);
  static Color C3 = new Color(130,106,175);

  /* the following two values are geometry values that may be modified to alter
   * the appearance:
   */

  // Width of black lines as percentage of main radius
  static double BLACK_LINE_WIDTH = 1.0D/15;

  // size of three small cicles relative to their surrounding circles
  static double SMALL_CIRCLE_PERCENTAGE = 0.25D;


  /* the following values are geometry values that are fixed and should not be
   * changed:
   */

  // total size
  static int SIZE = 2048;

  // main radius
  static int r = SIZE / 2;

  // width of black lines
  static double b = r * BLACK_LINE_WIDTH;

  // outer radius of three main wedges
  static double r1 = r-b;

  // side length of triangle of the centers of the three main circles with radius r2:
  //
  //  l = 2*r2 + b;
  //
  // distance from center to center of three main circles
  //
  //  h = r - r2 - b               -- using radius r
  //
  //  h =  (2*r2+b)/ (2*(cos 30))  -- using trigonomy
  //
  // so we get for r2
  //
  //  r - r2 - b = (2*r2+b)/ (2*(cos 30))
  //
  //  r - (b * (1 + 1/(2*(cos 30)))) = r2 * (1 + 1 / (cos 30))
  //
  //  r2 = (r - (b * (1 + 1/(2*(cos 30))))) / (1 + 1 / (cos 30))
  //     = (r - (b * (1 + 1/(2*sqrt(3/4))))) / (1 + 1 / sqrt(3/4))
  //     = (r - (b * (1 + 1/sqrt(3)))) / (1 + 2 / sqrt(3))
  //
  // radius of three main circles:
  static double r2 = (r - b * (1+Math.sqrt(1/3.0D)))/(1 + 2*Math.sqrt(1/3.0D));

  // radius of three small circles
  static double r3 = r2 * SMALL_CIRCLE_PERCENTAGE;

  /**
   * Functional interface used to draw repeatedly.
   */
  static interface Draw
  {
    void draw(Color c);
  }

  /**
   * Call d.draw three times, rotating g2 by 120°.
   */
  static void threeTimes(Graphics2D g2, Draw d)
  {
    d.draw(C1); g2.rotate(-2*Math.PI/3);
    d.draw(C2); g2.rotate(-2*Math.PI/3);
    d.draw(C3); g2.rotate(-2*Math.PI/3);
  }

  /**
   * Draw a filled arc of color c, center at (x,y), radius r, starting at angle
   * start and extending of len degrees.
   */
  static void arc(Graphics2D g2, Color c, double x, double y, double r, int start, int len)
  {
    g2.setPaint(c);
    g2.fillArc((int)(x-r), (int)(y-r), (int)(2*r), (int)(2*r), start, len);
  }

  /**
   * Draw a filled circle of color c, center at (x,y), radius r
   */
  static void cir(Graphics2D g2, Color c, double x, double y, double r)
  {
    arc(g2, c, x, y, r, 0, 360);
  }


  /**
   * Draw the Fuzion logo to the given g2 instance
   */
  static void drawLogo(Graphics2D g2)
  {
    // black background circle
    cir(g2, BACKGROUND, 0, 0, r);

    // three arcs
    threeTimes(g2, c -> { arc(g2, c, 0,0,r1, -30, 120);});

    // black circle in the center, radius to touch the sides of the inner triangle
    cir(g2, BACKGROUND, 0, 0, (r - r2 - b) / 2);

    // black arcs around inner circles
    threeTimes(g2, c -> arc(g2, BACKGROUND, 0,-r+r2+b, r2+b, 90, 210));

    // inner circles
    threeTimes(g2, c -> cir(g2, c, 0,-r+b+r2, r2));

    // small circles
    g2.rotate(-2*Math.PI/3);
    threeTimes(g2, c -> cir(g2, c, 0,-r+b+r2, r3));
  }


  /**
   * main routine.
   */
  public static void main(String[] args)
  {
    if (args.length > 1 || args.length == 1 && args[0].startsWith("-"))
      {
        System.err.println("Usage: java FuzionLogo <svg-filename>");
      }
    SVGGraphics2D g2 = new SVGGraphics2D(SIZE, SIZE);
    g2.translate(r, r);
    drawLogo(g2);
    var name = args.length > 0 ? args[0] : FILE_NAME;
    try
      {
        System.out.println(" + " + name);
        SVGUtils.writeToSVG(new File(name), g2.getSVGElement());
      }
    catch (IOException e)
      {
        System.err.println("*** failed to create file: " + e);
      }
  }

}

/* end of file */
