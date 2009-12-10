/**
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ccnx.ccn.io.content;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.ccnx.ccn.impl.encoding.GenericXMLEncodable;
import org.ccnx.ccn.impl.encoding.XMLDecoder;
import org.ccnx.ccn.impl.encoding.XMLEncodable;
import org.ccnx.ccn.impl.encoding.XMLEncoder;

public class KeyValueSet extends GenericXMLEncodable implements XMLEncodable, Map<String, Object>{
	protected static final String KEY_VALUE_SET_ELEMENT = "KeyValueSet";
	 
	protected TreeMap<String, KeyValuePair> _set = new TreeMap<String, KeyValuePair>();
	
	/**
	 * Add a new key value pair to the set
	 * @param key key for the value
	 * @param value the value - may be Integer, Float, String, byte[], or ContentName
	 */
	public Object put(String key, Object value) {
		KeyValuePair kvp = _set.get(key);
		if (kvp != null) {
			throw new InvalidParameterException("Duplicate keys not allowed");
		}
		kvp = new KeyValuePair(key, value);
		_set.put(key, kvp);
		return value;
	}
	
	/**
	 * @param key key of the Object to get
	 * @return the value
	 */
	public Object get(String key) {
		KeyValuePair kvp = _set.get(key);
		return kvp == null ? null : _set.get(key).getValue();
	}
	
	public int size() {
		return _set.size();
	}
	
	public void clear() {
		_set.clear();
	}

	public boolean containsKey(Object key) {
		return _set.containsKey(key);
	}

	public boolean containsValue(Object value) {
		for (KeyValuePair kvp : _set.values()) {
			if (value.equals(kvp.getValue()))
				return true;
		}
		return false;
	}

	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		HashSet<Map.Entry<String, Object>> hs = new HashSet<Map.Entry<String, Object>>();
		for (KeyValuePair kvp : _set.values()) {
			hs.add(kvp);
		}
		return hs;
	}

	public Object get(Object key) {
		return _set.get(key).getValue();
	}

	public boolean isEmpty() {
		return _set.isEmpty();
	}

	public Set<String> keySet() {
		return _set.keySet();
	}

	public void putAll(Map<? extends String, ? extends Object> t) {
		for (String k : t.keySet()) {
			put(k, t.get(k));
		}
	}

	public Object remove(Object key) {
		KeyValuePair kvp = _set.remove(key);
		return kvp.getValue();
	}

	public Collection<Object> values() {
		ArrayList<Object> al = new ArrayList<Object>();
		for (String k : _set.keySet()) {
			al.add(_set.get(k).getValue());
		}
		return al;
	}
	
	@Override
	public void decode(XMLDecoder decoder) throws ContentDecodingException {
		
		decoder.readStartElement(getElementLabel());
		
		synchronized (_set) {
			while (decoder.peekStartElement(KeyValuePair.ENTRY)) {
				KeyValuePair kvp = new KeyValuePair();
				kvp.decode(decoder);
				_set.put(kvp.getKey(), kvp);
			}
		}
		decoder.readEndElement();
	}

	@Override
	public void encode(XMLEncoder encoder) throws ContentEncodingException {
		if (!validate()) {
			throw new ContentEncodingException("Cannot encode " + this.getClass().getName() + ": field values missing.");
		}
		encoder.writeStartElement(getElementLabel());
		for (KeyValuePair kvp : _set.values()) {
			kvp.encode(encoder);
		}
		encoder.writeEndElement();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final KeyValueSet other = (KeyValueSet) obj;
		if (!other.keySet().equals(keySet()))
			return false;
		for (String key : keySet()) {
			if (! _set.get(key).equals(other._set.get(key)))
				return false;
		}
		return true;
	}

	@Override
	public String getElementLabel() {return KEY_VALUE_SET_ELEMENT;}

	@Override
	public boolean validate() {
		return true;
	}
}
