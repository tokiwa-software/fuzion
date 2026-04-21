package dev.flang.ast;

import dev.flang.util.List;

public class OuterType extends ResolvedType {

  private final AbstractFeature constraint;
  private final int level;

  public OuterType(AbstractFeature constraint, int level)
  {
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

}
