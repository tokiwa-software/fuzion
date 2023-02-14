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

  // size of three small circles relative to their surrounding circles
  static double SMALL_CIRCLE_PERCENTAGE = 0.25D;


  /* the following values are geometry values that are fixed and should not be
   * changed:
   */
  static class Geometry
  {

    static final double PIXEL_PER_CM = 37.8;

    // geometry in pixels: bleed, total size, radius with bleed r0, radius
    // without bleed r, radius of main wedges r1, radius of three circles r2,
    // radius of three small circles r3, width of black lines b.
    final double bleed, sz, r0, r, r1, r2, r3, b;

    Geometry(int size_in_cm,
             double bleed_in_cm)
    {
      var size  = size_in_cm * PIXEL_PER_CM;
      bleed = bleed_in_cm * PIXEL_PER_CM;

      // diameter including bleed
      sz = size + 2*bleed;

      // main radius including bleed
      r0 = sz / 2;

      // main radius
      r = size / 2;

      // width of black lines
      b = (int) (r * BLACK_LINE_WIDTH);

      // outer radius of three main wedges
      r1 = r-b;

      // side length of triangle of the centers of the three main circles with radius r2:
      //
      //  l = 2*r2 + b;
      //
      // distance from center to center of three main circles
      //
      //  h = r - r2 - b               -- using radius r
      //
      //  h =  (2*r2+b)/ (2*(cos 30))  -- using trigonometry
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
      r2 = (r - b * (1+Math.sqrt(1/3.0D)))/(1 + 2*Math.sqrt(1/3.0D));

      // radius of three small circles
      r3 = r2 * SMALL_CIRCLE_PERCENTAGE;
    }
  }


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
  static void drawLogo(Graphics2D g2, Geometry geo, boolean crop)
  {
    var b = geo.b;
    var r = geo.r;
    var ri = (int) r;
    var r1 = geo.r1;
    var r2 = geo.r2;
    var r3 = geo.r3;

    // black background circle
    cir(g2, BACKGROUND, 0, 0, geo.r0);
    if (crop)
      {
        g2.setPaint(Color.MAGENTA);
        g2.drawOval(-ri,-ri,2*ri,2*ri);
      }

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
    var name = FILE_NAME;
    var crop = false;
    var bleed = 0.0;
    for (var a : args)
      {
        if (a.equals("-b"))
          {
            bleed = 3;
          }
        else if (a.equals("-c"))
          {
            bleed = 3;
            crop = true;
          }
        else if (name != FILE_NAME || a.startsWith("-"))
          {
            System.err.println("Usage: java FuzionLogo {-b|-c} <svg-filename>");
          }
        else
          {
            name = a;
          }
      }
    var geo = new Geometry(40, bleed);
    SVGGraphics2D g2 = new SVGGraphics2D(geo.sz, geo.sz);
    g2.translate(geo.r0, geo.r0);
    drawLogo(g2, geo, crop);
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
