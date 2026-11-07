package io.yesly;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * One page of a list. Iterating it yields every item from this page on,
 * fetching later pages as needed; {@link #getData()} holds this page only.
 */
public final class Page<T> implements Iterable<T> {
  private final String url;
  private final boolean hasMore;
  private final List<T> data;
  private final String lastId;
  private final Function<String, Page<T>> fetchAfter;

  Page(String url, boolean hasMore, List<T> data, String lastId, Function<String, Page<T>> fetchAfter) {
    this.url = url;
    this.hasMore = hasMore;
    this.data = Collections.unmodifiableList(data);
    this.lastId = lastId;
    this.fetchAfter = fetchAfter;
  }

  public String getUrl() {
    return url;
  }

  public boolean getHasMore() {
    return hasMore;
  }

  public List<T> getData() {
    return data;
  }

  public boolean hasNextPage() {
    return hasMore && lastId != null;
  }

  /** The page after this one. Check {@link #hasNextPage()} first. */
  public Page<T> nextPage() {
    if (!hasNextPage()) throw new NoSuchElementException("There is no next page.");
    return fetchAfter.apply(lastId);
  }

  /** Every item from this page on, fetching later pages as needed. */
  public Iterable<T> autoPagingIterable() {
    return this;
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      private Page<T> page = Page.this;
      private int i = 0;

      @Override
      public boolean hasNext() {
        while (i >= page.data.size()) {
          if (!page.hasNextPage()) return false;
          page = page.nextPage();
          i = 0;
        }
        return true;
      }

      @Override
      public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        return page.data.get(i++);
      }
    };
  }
}
