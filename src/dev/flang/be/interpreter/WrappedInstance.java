package dev.flang.be.interpreter;

import dev.flang.air.Clazz;

public class WrappedInstance extends Instance {

  public final Object _obj;

  public WrappedInstance(Clazz c, Object obj)
  {
    super(c);
    _obj = obj;
  }

}
