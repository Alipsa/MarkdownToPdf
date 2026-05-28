package se.alipsa.md2pdf.gui;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A naive {@link java.util.List} implementation that silently rejects duplicate elements. Works
 * well for small lists; for larger collections use a {@link java.util.LinkedHashSet} instead, as
 * the {@code contains} check here is O(n).
 *
 * @param <T> the element type
 */
public class UniqueList<T> extends ArrayList<T> {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates an empty list that rejects duplicate elements. */
  public UniqueList() {}

  @Override
  public boolean add(T t) {
    if (!contains(t)) return super.add(t);
    return false;
  }

  @Override
  public void add(int index, T element) {
    if (!contains(element)) super.add(index, element);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    if (!containsAll(c)) return super.addAll(c);
    return false;
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    if (!containsAll(c)) return super.addAll(index, c);
    return false;
  }
}
