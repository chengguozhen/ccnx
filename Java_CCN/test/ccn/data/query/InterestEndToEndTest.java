package test.ccn.data.query;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import test.ccn.library.LibraryTestBase;

import com.parc.ccn.data.ContentName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.MalformedContentNameStringException;
import com.parc.ccn.data.query.CCNFilterListener;
import com.parc.ccn.data.query.CCNInterestListener;
import com.parc.ccn.data.query.Interest;

/**
 * Test sending interests across ccnd
 * Requires a running ccnd
 * 
 * @author rasmusse
 *
 */

public class InterestEndToEndTest extends LibraryTestBase implements CCNFilterListener, CCNInterestListener {
	private Interest _interestSent;
	private String _prefix = "/interestEtoETest/test-" + rand.nextInt(10000);
	private final static int TIMEOUT = 3000;

	
	@Test
	public void testInterestEndToEnd() throws MalformedContentNameStringException, IOException, InterruptedException {
		getLibrary.registerFilter(ContentName.fromNative(_prefix), this);
		_interestSent = new Interest(ContentName.fromNative(_prefix + "/simpleTest"));
		doTest();
		_interestSent = new Interest(ContentName.fromNative(_prefix + "/simpleTest2"));
		_interestSent.maxSuffixComponents(4);
		_interestSent.minSuffixComponents(3);
		doTest();
		_interestSent = new Interest(ContentName.fromNative(_prefix + "/simpleTest2"));
		_interestSent.maxSuffixComponents(1);
		doTest();
	}

	public int handleInterests(ArrayList<Interest> interests) {
		Assert.assertTrue(_interestSent.equals(interests.get(0)));
		synchronized(this) {
			notify();
		}
		return 0;
	}
	
	private void doTest() throws IOException, InterruptedException {
		long startTime = System.currentTimeMillis();
		putLibrary.expressInterest(_interestSent, this);
		synchronized (this) {
			wait(TIMEOUT);
		}
		Assert.assertTrue((System.currentTimeMillis() - startTime) < TIMEOUT);
	}

	public Interest handleContent(ArrayList<ContentObject> results,
			Interest interest) {
		// TODO Auto-generated method stub
		return null;
	}

}