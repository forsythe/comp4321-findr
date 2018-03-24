package com.findr.object;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a posting object in a list
 */
public class Posting implements Serializable {
    private Long id;
    private int frequency;

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
}
