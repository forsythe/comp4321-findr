package com.findr.object;

import java.io.Serializable;
import java.util.Objects;

public class Entry implements Comparable, Serializable {
	final public Long key;
	final public Posting posting;
	
	public Entry(Long key, Posting posting) {
		this.key = key;
		this.posting = posting;
	}
	
	@Override
	public int compareTo(Object o) {
		if (o == null)
			throw new NullPointerException();
		
		Entry entry = (Entry)o;
		int keyComp = (this.key).compareTo(entry.key);
		if (keyComp == 0) {
			if (this.posting == null)
				return 1;
			else if (entry.posting == null)
				return -1;
			return ((this.posting).id).compareTo(entry.posting.id);
		}	
		return keyComp;
	}
	
	@Override 
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Entry entry = (Entry)o;
		return ((this.key).equals(entry.key) && (this.posting).equals(entry.posting));
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(key, posting);
	}
}
