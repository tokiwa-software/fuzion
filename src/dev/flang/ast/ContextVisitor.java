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
 * Source of class ContextVisitor
 *
 *---------------------------------------------------------------------*/

package dev.flang.ast;


/**
 * This is a FeatureVisitor that keeps track of the Context of the currently
 * visited code.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public abstract class ContextVisitor extends FeatureVisitor
{

  /*----------------------------  constants  ----------------------------*/

  /*----------------------------  variables  ----------------------------*/


  /**
   * The current Context
   */
  Context _context;


  /*---------------------------  constructors  --------------------------*/


  /**
   * Constructor for a ContextVisitor with the given initial context.
   */
  ContextVisitor(Context initialConext)
  {
    this._context = initialConext;
  }


  /*-----------------------------  methods  -----------------------------*/


  @Override public void actionBefore(AbstractCase c, AbstractMatch m)
  {
    var s = m.subject();
    if (s instanceof AbstractCall sc &&
        sc.calledFeature() == Types.resolved.f_Type_infix_colon && c.types().stream().anyMatch(x->x.compareTo(Types.resolved.f_TRUE .selfType())==0))
      {
        _context = _context.addTypeConstraint(sc);
        check(_context != null);
      }
  }

  @Override public void actionAfter(AbstractCase c, AbstractMatch m)
  {
    var s = m.subject();
    if (s instanceof AbstractCall sc &&
        sc.calledFeature() == Types.resolved.f_Type_infix_colon && c.types().stream().anyMatch(x->x.compareTo(Types.resolved.f_TRUE .selfType())==0))
      {
        _context = _context.exterior();
        check(_context != null);
      }
  }


  @Override public void actionBeforeIfThen(If i)
  {
    if (i.cond instanceof AbstractCall sc &&
        sc.calledFeature() == Types.resolved.f_Type_infix_colon)
      {
        _context = _context.addTypeConstraint(sc);
        check(_context != null);
      }
  }

  @Override public void actionBeforeIfElse(If i)
  {
    if (i.cond instanceof AbstractCall sc &&
        sc.calledFeature() == Types.resolved.f_Type_infix_colon)
      {
        _context = _context.exterior();
        check(_context != null);
      }
    // NYI: We might add support for `if !(T : x) then else ...treat T as x...`
  }

  @Override public void actionAfterIf(If i)
  {
  }

}

/* end of file */
