package com.findr.object;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a posting object in a list
 */
public class Posting implements Serializable, Comparable {
    final public Long id;
    final public int frequency;

    public Posting(Long id, int frequency) {
        this.id = id;
        this.frequency = frequency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Posting posting = (Posting) o;
        return frequency == posting.frequency &&
                Objects.equals(id, posting.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, frequency);
    }

	@Override
	public int compareTo(Object o) {
		if (o == null)
			throw new NullPointerException();
			
		Posting p = (Posting)o;
		if (this.id == null)
			return 1;
		else if (p.id == null)
			return -1;
	
		return ((this.id).compareTo(p.id));
	}
}
