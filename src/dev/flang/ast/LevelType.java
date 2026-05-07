package dev.flang.ast;

import dev.flang.util.List;

public class LevelType extends ResolvedType {

  private final AbstractFeature constraint;
  private final int level;

  public LevelType(AbstractFeature constraint, int level)
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
    return TypeKind.LevelType;
  }

  @Override
  public int outerLevel()
  {
    return level;
  }

  /**
   * This returns feature() unless this is an LevelType
   * Then it returns the feature in the constraint that is referenced
   * by the LevelType.
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
