/*
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2011 Palo Alto Research Center, Inc.
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

package org.ccnx.ccn.profiles.versioning;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.CCNInterestListener;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.ContentObject;
import org.ccnx.ccn.protocol.Interest;

/**
 * Given a base name, retrieve all versions.  We have maintained a similar method
 * naming to CCNHandle (expressInterest, cancelInterest, close), except we take
 * a ContentName input instead of an Interest input.  A future extension might be to
 * take an Interest to make it more drop-in replacement for existing CCNHandle methods.
 * 
 * IMPORTANT: The CCNInterestListener that gets the data should not block for long
 * and should not hijack the incoming thread.
 * 
 * NOTE: The retry is not implemented.  Interests are re-expressed every 4 seconds
 * as per normal, and they continue to be expressed until canceled.
 * 
 * This object is meant to be private for one application, which provides the
 * CCNHandle to use.  It may be shared between multiple threads.  The way the
 * retry expression works is intended for one app to use that has an understanding
 * of its needs.
 * 
 * The class will:
 * - return all available versions of a base name, without duplicates
 * - allow the user to supply a list of versions to exclude (e.g. they
 *   have already been seen by the application)
 * - allow the user to supply a hard-cutoff starting time
 * - allow the user to supply the interest re-expression rate,
 *   which may be very slow and use our own timer not the one built in
 *   to ccnx.  
 *   
 * Because the list of excluded version can be very long, this
 * class manages expressing multiple interests.
 *    
 * All the work is done down in the inner class BasenameState, which is the state
 * stored per basename and tracks the interests issued for that basename.  It
 * is really just a holder for VersioningInterestManager plus state about the
 * set of listeners.
 */
public class VersioningInterest {
	
	// ==============================================================================
	// Public API

	/**
	 * @param handle
	 * @param listener
	 */
	public VersioningInterest(CCNHandle handle) {
		_handle = handle;
	}
	
	/**
	 * Express an interest for #name.  We will assume that #name does not
	 * include a version, and we construct an interest that will only match
	 * 3 additional components to #name (version/segment/digest).
	 * 
	 * When the default CCN timeout is exceeded, we stop responding.
	 * 
	 * If there is already an interest for the same (name, listener), no action is taken.
	 * 
	 * The return value from #listener is ignored, the listener does not need to re-express
	 * an interest.  Interests are re-expressed automatically until canceled.
	 * 
	 * @param name
	 * @param listener
	 * @throws IOException 
	 */
	public void expressInterest(ContentName name, CCNInterestListener listener) throws IOException {
		expressInterest(name, listener, null, null);
	}

	/**
	 * As above, and provide a set of versions to exclude
	 * The return value from #listener is ignored, the listener does not need to re-express
	 * an interest.  Interests are re-expressed automatically until canceled.
	 * 
	 * @param name
	 * @param listener
	 * @param retrySeconds
	 * @param exclusions
	 * @throws IOException 
	 */
	public void expressInterest(ContentName name, CCNInterestListener listener, Set<VersionNumber> exclusions) throws IOException {
		expressInterest(name, listener, exclusions, null);
	}
	
	/**
	 * As above, and provide a set of versions to exclude and a hard floor startingVersion, any version
	 * before that will be ignored.
	 * 
	 * The return value from #listener is ignored, the listener does not need to re-express
	 * an interest.  Interests are re-expressed automatically until canceled.
	 * 
	 * @param name
	 * @param listener
	 * @param retrySeconds
	 * @param exclusions
	 * @param startingVersion the minimum version to include
	 * @throws IOException 
	 */
	public void expressInterest(ContentName name, CCNInterestListener listener, Set<VersionNumber> exclusions, VersionNumber startingVeersion) throws IOException {
		addInterest(name, listener, exclusions, startingVeersion);
	}
	
	/**
	 * Kill off all interests.
	 */
	
	public void close() {
		removeAll();
	}

	/**
	 * Cancel a specific interest
	 * @param name
	 * @param listener
	 */
	public void cancelInterest(ContentName name, CCNInterestListener listener) {
		removeInterest(name, listener);
	}

	/**
	 * in case we're GC'd without a close().  Don't rely on this.
	 */
	public void finalize() {
		removeAll();
	}
	
	// ==============================================================================
	// Internal implementation
	private final CCNHandle _handle;
	private final Map<ContentName, BasenameState> _map = new HashMap<ContentName, BasenameState>();

	private void addInterest(ContentName name, CCNInterestListener listener, Set<VersionNumber> exclusions, VersionNumber startingVersion) throws IOException {
		BasenameState data;
		
		synchronized(_map) {
			data = _map.get(name);
			if( null == data ) {
				data = new BasenameState(_handle, name, exclusions, startingVersion);
				_map.put(name, data);
				data.addListener(listener);
				data.start();
			} else {
				data.addListener(listener);
			}
		}
	}
	
	/**
	 * Remove a listener.  If it is the last listener, remove from map and
	 * kill all interests.
	 * @param name
	 * @param listener
	 */
	private void removeInterest(ContentName name, CCNInterestListener listener) {
		BasenameState data;
		
		synchronized(_map) {
			data = _map.get(name);
			if( null != data ) {
				data.removeListener(listener);
				if( data.size() == 0 ) {
					data.stop();
					_map.remove(name);
				}
			}
		}
	}
	
	private void removeAll() {
		synchronized(_map) {
			Iterator<BasenameState> iter = _map.values().iterator();
			while( iter.hasNext() ) {
				BasenameState bns = iter.next();
				bns.stop();
				iter.remove();
			}
		}
	}
	
	// ======================================================================
	// This is the state stored per base name
	
	private static class BasenameState implements CCNInterestListener {
		
		public BasenameState(CCNHandle handle, ContentName basename, Set<VersionNumber> exclusions, VersionNumber startingVersion) {
			_vim = new VersioningInterestManager(handle, basename, exclusions, startingVersion, this);
		}
		
		/**
		 * @param listener
		 * @param retrySeconds IGNORED, not implemented
		 * @return true if added, false if existed or only retrySeconds updated
		 */
		public synchronized boolean addListener(CCNInterestListener listener) {
			return _listeners.add(listener);
		}
		
		/**
		 * @return true if removed, false if not found
		 */
		public synchronized boolean removeListener(CCNInterestListener listener) {
			return _listeners.remove(listener);
		}
				
		public synchronized int size() {
			return _listeners.size();
		}

		/**
		 * start issuing interests.  No data is passed to
		 * any listener in the stopped state
		 * @throws IOException 
		 */
		public void start() throws IOException {
			_running = true;
			_vim.start();
		}
		
		/**
		 * Cancel all interests for the name
		 */
		public void stop() {
			_running = false;
			_vim.stop();
		}
		
		/**
		 * Pass any received data up to the user.
		 * @param data
		 * @param interest
		 * @return null
		 */
		@Override
		public synchronized Interest handleContent(ContentObject data, Interest interest) {
			// when we're stopped, we do not pass any data
			if( ! _running )
				return null;
			
			for(CCNInterestListener listener : _listeners)
				try {
					listener.handleContent(data, interest);
				} catch(Exception e){
					e.printStackTrace();
				}
				return null;
		}

		// =======
		
		private final Set<CCNInterestListener> _listeners = new HashSet<CCNInterestListener>();
		private final VersioningInterestManager _vim;
		private boolean _running = false;
	}

}
