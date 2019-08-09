package playwell.util;

public final class VariableHolder<T> {

  private T var;

  public VariableHolder(T var) {
    this.var = var;
  }

  public T getVar() {
    return this.var;
  }

  public void setVar(T var) {
    this.var = var;
  }
}
