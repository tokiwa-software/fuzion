package dev.flang.ast;

import dev.flang.util.List;

public class OuterType extends ResolvedType {

  private final AbstractFeature constraint;
  private final int level;

  public OuterType(AbstractFeature constraint, int level)
  {
    if (PRECONDITIONS) require
      (constraint.isTypeParameter());

    this.constraint = constraint;
    this.level = level;
  }

  @Override
  protected AbstractFeature backingFeature()
  {
    return constraint;
  }

  @Override
  public List<AbstractType> generics()
  {
    return AbstractCall.NO_GENERICS;
  }

  @Override
  public AbstractType outer()
  {
    return Types.resolved.universe.selfType();
  }

  @Override
  public TypeKind kind()
  {
    return TypeKind.OuterType;
  }

  @Override
  public int outerLevel()
  {
    return level;
  }

  /**
   * This returns feature() unless this is an OuterType
   * Then it returns the feature in the constraint that is referenced
   * by the OuterType.
   */
  @Override
  public AbstractFeature effectiveFeature()
  {
    var l = outerLevel();
    var t = feature().constraint();
    while (l>0)
      {
        t = t.outer();
        l--;
      }
    return t.feature();
  }

}
