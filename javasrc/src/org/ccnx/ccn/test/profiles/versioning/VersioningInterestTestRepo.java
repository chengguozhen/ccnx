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

package org.ccnx.ccn.test.profiles.versioning;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.TreeSet;
import java.util.logging.Level;

import junit.framework.Assert;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.impl.CCNFlowControl.SaveType;
import org.ccnx.ccn.impl.support.Log;
import org.ccnx.ccn.io.content.CCNStringObject;
import org.ccnx.ccn.profiles.VersioningProfile;
import org.ccnx.ccn.profiles.versioning.VersionNumber;
import org.ccnx.ccn.profiles.versioning.VersioningInterest;
import org.ccnx.ccn.protocol.CCNTime;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;
import org.ccnx.ccn.test.profiles.versioning.VersioningHelper.ReceivedData;
import org.ccnx.ccn.test.profiles.versioning.VersioningHelper.TestListener;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This is really a super-set of the VersioningInterestManager tests.
 * What we need to test here is that we can express interests for
 * multiple names and multiple listeners.  Then it comes down to
 * does VersioningInterestManager work (and that is tested
 * in VersioningInterestManagerTestRepo).
 */
public class VersioningInterestTestRepo {
	protected final Random _rnd = new Random();
	protected final ContentName prefix;
	protected CCNHandle realhandle = null;
	
	protected final static long TIMEOUT=30000;
	protected final static long SEND_PAUSE = 50;

	public VersioningInterestTestRepo() throws MalformedContentNameStringException {
		prefix  = ContentName.fromNative(String.format("/test_%016X", _rnd.nextLong()));
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Log.setLevel(Log.FAC_ALL, Level.WARNING);
		Log.setLevel(Log.FAC_ENCODING, Level.INFO);
	}

	@Before
	public void setUp() throws Exception {
		realhandle = CCNHandle.getHandle();
	}

	@After
	public void tearDown() throws Exception {
	}

	// ======================================================
	
	/**
	 * Two basenames going to one listener
	 */
	@Test
	public void testTwoNamesOneListener() throws Exception {
		ContentName base1 = ContentName.fromNative(prefix, String.format("content_%016X", _rnd.nextLong()));
		ContentName base2 = ContentName.fromNative(prefix, String.format("content_%016X", _rnd.nextLong()));

		ContentName [] names = new ContentName [] {base1, base2};

		
		TestListener listener = new TestListener();
		
		VersioningInterest vi = new VersioningInterest(realhandle);
		
		vi.expressInterest(base1, listener);
		vi.expressInterest(base2, listener);
		
		// send data
		CCNTime now = CCNTime.now();
		long start = now.getTime();
		long stop = start + 1000000L;
		
		int count = 100;
		TreeSet<CCNTime> sent = sendStreamUniform(realhandle, names, start, stop, count);
		
		// now make sure we get them all
		boolean b = listener.cl.waitForValue(count, TIMEOUT);
		System.out.println("Received: " + listener.cl.getValue());
		Assert.assertTrue(b);
		
		HashSet<CCNTime> recv = new HashSet<CCNTime>();
		for(ReceivedData data : listener.received) {
			ContentName name = data.object.name();
			CCNTime version = VersioningProfile.getLastVersionAsTimestamp(name);
			recv.add(version);
		}

		// and compare the received versions
		boolean missing = false;
		for(CCNTime version : sent) {
			if( !recv.contains(version) ) {
				System.out.println("recv missing version " + new VersionNumber(version));
				missing = true;
			}
		}
		Assert.assertFalse(missing);
	}
	
	/**
	 * Three basenames, each with own listener, plus one listener that gets them all
	 */
	@Test
	public void testThreeNamesFourListener() throws Exception {
		ContentName base1 = ContentName.fromNative(prefix, String.format("content_%016X", _rnd.nextLong()));
		ContentName base2 = ContentName.fromNative(prefix, String.format("content_%016X", _rnd.nextLong()));
		ContentName base3 = ContentName.fromNative(prefix, String.format("content_%016X", _rnd.nextLong()));

		ContentName [] names = new ContentName [] {base1, base2, base3};

		TestListener listener1 = new TestListener();
		TestListener listener2 = new TestListener();
		TestListener listener3 = new TestListener();
		TestListener listener4 = new TestListener();
		
		VersioningInterest vi = new VersioningInterest(realhandle);
		
		vi.expressInterest(base1, listener1);
		vi.expressInterest(base2, listener2);
		vi.expressInterest(base3, listener3);
		vi.expressInterest(base1, listener4);
		vi.expressInterest(base2, listener4);
		vi.expressInterest(base3, listener4);
		
		// send data
		CCNTime now = CCNTime.now();
		long start = now.getTime();
		long stop = start + 1000000L;
		
		int count = 100;
		TreeSet<CCNTime> sent = sendStreamUniform(realhandle, names, start, stop, count);
		
		// now make sure we get them all
		boolean b = listener4.cl.waitForValue(count, TIMEOUT);
		System.out.println("Received: " + listener4.cl.getValue());
		Assert.assertTrue(b);
		
		HashSet<CCNTime> recv = new HashSet<CCNTime>();
		for(ReceivedData data : listener4.received) {
			ContentName name = data.object.name();
			CCNTime version = VersioningProfile.getLastVersionAsTimestamp(name);
			recv.add(version);
		}

		// and compare the received versions
		boolean missing = false;
		for(CCNTime version : sent) {
			if( !recv.contains(version) ) {
				System.out.println("recv missing version " + new VersionNumber(version));
				missing = true;
			}
		}
		Assert.assertFalse(missing);
		
		// Make sure other listeners got their shares
		long sum = listener1.cl.getValue() + listener2.cl.getValue() + listener3.cl.getValue();
		System.out.println("Other listeners got " + sum);
		Assert.assertEquals(count, (int) sum);
	}
	
	// ========================================================
	
	private TreeSet<CCNTime> sendStreamUniform(CCNHandle handle, ContentName [] names, long start_time, long stop_time, int count) throws Exception {
		TreeSet<CCNTime> sent = new TreeSet<CCNTime>();

		long [] sends = new long[names.length];
		for(int i = 0; i < sends.length; i++ )
			sends[i] = 0;
		
		int width = (int) (stop_time - start_time + 1);
		for(int i = 0; i < count; i++) {
			CCNTime version;

			// pick the name
			int j = _rnd.nextInt(names.length);
			ContentName name = names[j];
			sends[j]++;
			
			// avoid sending duplicate version numbers
			do {
				int delta = _rnd.nextInt(width);
				version = new CCNTime(start_time + delta);
			} while( !sent.add(version));

			send(handle, name, version);
		}
		
		System.out.println("sends: " + Arrays.toString(sends));
		
		return sent;
	}
	
	private void send(CCNHandle handle, ContentName name, CCNTime version) throws IOException, InterruptedException {
		
		CCNStringObject so = new CCNStringObject(name, "Hello, World " + version, SaveType.LOCALREPOSITORY, handle);
		int trycount = 10;
		IOException error = null;

		do {
			error = null;
			trycount--;
			try {
				so.save(version);
				so.close();
				// do this every time, not just on errors.  The FlowController seems
				// to not like to receive a lot of objects all at once.
//				Thread.sleep(SEND_PAUSE);
			} catch(IOException e) {
				error = e;
				Thread.sleep(SEND_PAUSE);
			} 
			
		} while( trycount > 0 && null != error );
		
		if( null != error ) {
			throw error;
		}

//		ContentName versionedName = so.getVersionedName();
//		so = new CCNStringObject(name, handle);
		
	}

}
