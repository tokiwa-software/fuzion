package dev.flang.fe;

import dev.flang.ast.AbstractCall;
import dev.flang.ast.AbstractFeature;
import dev.flang.ast.AbstractType;
import dev.flang.ast.TypeKind;
import dev.flang.util.List;

public class OuterType extends LibraryType {

  private final AbstractFeature constraint;
  private final int lvl;

  public OuterType(LibraryModule libraryModule, int at, AbstractFeature constraint, int lvl)
  {
    super(libraryModule, at);
    this.constraint = constraint;
    this.lvl = lvl;
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
    var universe = constraint;
    while (!universe.isUniverse())
      {
        universe = universe.outer();
      }
    return universe.selfType();
  }

  @Override
  public TypeKind kind()
  {
    return TypeKind.OuterType;
  }

  @Override
  public int outerLevel()
  {
    return lvl;
  }


}
