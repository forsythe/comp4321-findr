package com.findr.object;

import com.findr.object.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;

import org.mapdb.*;
import org.mapdb.serializer.*;

public class EntrySerializer implements GroupSerializer<Entry>, Serializable {

	@Override
	public void serialize(DataOutput2 out, Entry value) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Entry deserialize(DataInput2 input, int available) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int valueArraySearch(Object keys, Entry key) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int valueArraySearch(Object keys, Entry key, Comparator comparator) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void valueArraySerialize(DataOutput2 out, Object vals) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object valueArrayDeserialize(DataInput2 in, int size) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Entry valueArrayGet(Object vals, int pos) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int valueArraySize(Object vals) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Object valueArrayEmpty() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object valueArrayPut(Object vals, int pos, Entry newValue) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object valueArrayUpdateVal(Object vals, int pos, Entry newValue) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object valueArrayFromArray(Object[] objects) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object valueArrayCopyOfRange(Object vals, int from, int to) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object valueArrayDeleteValue(Object vals, int pos) {
		// TODO Auto-generated method stub
		return null;
	}


	
}
