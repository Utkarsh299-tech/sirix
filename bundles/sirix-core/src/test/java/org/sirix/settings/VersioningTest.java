/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sirix.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.brackit.xquery.atomic.QNm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sirix.TestHelper;
import org.sirix.access.Databases;
import org.sirix.access.conf.DatabaseConfiguration;
import org.sirix.access.conf.ResourceConfiguration;
import org.sirix.access.conf.SessionConfiguration;
import org.sirix.api.Database;
import org.sirix.api.NodeReadTrx;
import org.sirix.api.NodeWriteTrx;
import org.sirix.api.Session;
import org.sirix.exception.SirixException;

/** Test revisioning. */
public class VersioningTest {

	/** {@link Database} instance. */
	private Database mDatabase;

	@Before
	public void setUp() throws SirixException {
		TestHelper.deleteEverything();
		Databases.createDatabase(new DatabaseConfiguration(TestHelper.PATHS.PATH1
				.getFile()));
		mDatabase = Databases.openDatabase(TestHelper.PATHS.PATH1.getFile());
	}

	@After
	public void tearDown() throws SirixException {
		mDatabase.close();
	}

	@Test
	public void testIncremental() throws SirixException {
		mDatabase.createResource(new ResourceConfiguration.Builder(
				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
				.versioningApproach(Versioning.INCREMENTAL).revisionsToRestore(3)
				.build());
		test();
	}

	@Test
	public void testIncremental1() throws SirixException {
		mDatabase.createResource(new ResourceConfiguration.Builder(
				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
				.versioningApproach(Versioning.INCREMENTAL).revisionsToRestore(3)
				.build());
		test1();
	}

	@Test
	public void testDifferential() throws SirixException {
		mDatabase.createResource(new ResourceConfiguration.Builder(
				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
				.versioningApproach(Versioning.DIFFERENTIAL).revisionsToRestore(3)
				.build());
		test();
	}

	@Test
	public void testDifferential1() throws SirixException {
		mDatabase.createResource(new ResourceConfiguration.Builder(
				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
				.versioningApproach(Versioning.DIFFERENTIAL).revisionsToRestore(3)
				.build());
		test1();
	}

	@Test
	public void testFull() throws SirixException {
		mDatabase.createResource(new ResourceConfiguration.Builder(
				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
				.versioningApproach(Versioning.FULL).revisionsToRestore(3).build());
		test();
	}

	@Test
	public void testFull1() throws SirixException {
		mDatabase.createResource(new ResourceConfiguration.Builder(
				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
				.versioningApproach(Versioning.FULL).revisionsToRestore(3).build());
		test1();
	}

//	@Test
//	public void testSlidingSnapshot() throws SirixException {
//		mDatabase.createResource(new ResourceConfiguration.Builder(
//				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
//				.versioningApproach(Versioning.SLIDING_SNAPSHOT).revisionsToRestore(3)
//				.build());
//		test();
//	}
//
//	@Test
//	public void testSlidingSnapshot1() throws SirixException {
//		mDatabase.createResource(new ResourceConfiguration.Builder(
//				TestHelper.RESOURCE, mDatabase.getDatabaseConfig())
//				.versioningApproach(Versioning.SLIDING_SNAPSHOT).revisionsToRestore(3)
//				.build());
//		test1();
//	}

	/**
	 * Test revisioning.
	 * 
	 * @throws SirixException
	 *           if anything in Sirix fails
	 */
	public void test() throws SirixException {
		try (final Session session = mDatabase
				.getSession(new SessionConfiguration.Builder(TestHelper.RESOURCE)
						.build())) {
			try (final NodeWriteTrx wtx = session.beginNodeWriteTrx()) {
				wtx.insertElementAsFirstChild(new QNm("foo"));
				for (int i = 0; i < Constants.NDP_NODE_COUNT - 2; i++) {
					wtx.insertElementAsRightSibling(new QNm("foo"));
				}
				wtx.commit();
				assertTrue(wtx.getNodeKey() == Constants.NDP_NODE_COUNT - 1);
				fillNodePage(wtx);
				wtx.commit();
				assertTrue(wtx.getNodeKey() == (Constants.NDP_NODE_COUNT << 1) - 1);
				fillNodePage(wtx);
				wtx.commit();
				assertTrue(wtx.getNodeKey() == (Constants.NDP_NODE_COUNT * 3) - 1);
				fillNodePage(wtx);
				wtx.commit();
				assertTrue(wtx.getNodeKey() == (Constants.NDP_NODE_COUNT << 2) - 1);
				try (final NodeReadTrx rtx = session.beginNodeReadTrx()) {
					assertTrue(rtx.moveToFirstChild().hasMoved());
					for (int i = 0; i < Constants.NDP_NODE_COUNT - 2; i++) {
						assertTrue(rtx.moveToRightSibling().hasMoved());
					}
					move(rtx);
					move(rtx);
					move(rtx);
				}
			}
		}
	}

	/**
	 * Test revisioning.
	 * 
	 * @throws SirixException
	 *           if anything in Sirix fails
	 */
	public void test1() throws SirixException {
		try (final Session session = mDatabase
				.getSession(new SessionConfiguration.Builder(TestHelper.RESOURCE)
						.build())) {
			NodeWriteTrx wtx = session.beginNodeWriteTrx();
			wtx.insertElementAsFirstChild(new QNm("foo"));
			for (int i = 0; i < Constants.NDP_NODE_COUNT - 2; i++) {
				wtx.insertElementAsRightSibling(new QNm("foo"));
			}
			wtx.commit();
			assertTrue(wtx.getNodeKey() == Constants.NDP_NODE_COUNT - 1);
			wtx.close();
			wtx = session.beginNodeWriteTrx();
			setBaaaz(wtx);
			// wtx.commit();
			// wtx.close();
			// wtx = session.beginNodeWriteTrx();
			setFooBar(wtx);
			setFoooo(wtx);
			wtx.moveTo(Constants.NDP_NODE_COUNT - 1);
			fillNodePage(wtx);
			wtx.commit();
			wtx.close();
			wtx = session.beginNodeWriteTrx();
			wtx.moveTo((Constants.NDP_NODE_COUNT << 1) - 1);
			fillNodePage(wtx);
			wtx.commit();
			wtx.close();
			wtx = session.beginNodeWriteTrx();
			wtx.moveTo((Constants.NDP_NODE_COUNT * 3) - 1);
			fillNodePage(wtx);
			wtx.commit();
			wtx.close();
			wtx = session.beginNodeWriteTrx();
			wtx.moveTo((Constants.NDP_NODE_COUNT << 2) - 1);
			fillNodePage(wtx);
			wtx.commit();
			wtx.close();
			try (final NodeReadTrx rtx = session.beginNodeReadTrx()) {
				assertTrue(rtx.moveToFirstChild().hasMoved());
				assertEquals(new QNm("foobar"), rtx.getName());
				assertTrue(rtx.moveToRightSibling().hasMoved());
				assertEquals(new QNm("foooo"), rtx.getName());
				for (int i = 0; i < Constants.NDP_NODE_COUNT - 4; i++) {
					assertTrue(rtx.moveToRightSibling().hasMoved());
				}
				assertEquals(new QNm("baaaz"), rtx.getName());
			}
		}
	}

	/**
	 * Set the second {@link QNm} in the first node page.
	 * 
	 * @param wtx
	 *          {@link NodeWriteTrx} instance
	 * @throws SirixException
	 *           if inserting elements fails
	 */
	private void setFoooo(final NodeWriteTrx wtx) throws SirixException {
		wtx.moveToDocumentRoot();
		wtx.moveToFirstChild();
		wtx.moveToRightSibling();
		wtx.setName(new QNm("foooo"));
	}

	/**
	 * Set the first {@link QNm} in the first node page.
	 * 
	 * @param wtx
	 *          {@link NodeWriteTrx} instance
	 * @throws SirixException
	 *           if inserting elements fails
	 */
	private void setFooBar(final NodeWriteTrx wtx) throws SirixException {
		wtx.moveToDocumentRoot();
		wtx.moveToFirstChild();
		wtx.setName(new QNm("foobar"));
	}

	/**
	 * Set the last {@link QNm} in the first node page.
	 * 
	 * @param wtx
	 *          {@link NodeWriteTrx} instance
	 * @throws SirixException
	 *           if inserting elements fails
	 */
	private void setBaaaz(final NodeWriteTrx wtx) throws SirixException {
		wtx.moveToDocumentRoot();
		wtx.moveToFirstChild();
		for (int i = 0; i < Constants.NDP_NODE_COUNT - 3; i++) {
			wtx.moveToRightSibling();
		}
		wtx.setName(new QNm("baaaz"));
	}

	/**
	 * Fill node page.
	 * 
	 * @param wtx
	 *          {@link NodeWriteTrx} instance
	 * @throws SirixException
	 *           if inserting elements fails
	 */
	private void fillNodePage(final NodeWriteTrx wtx)
			throws SirixException {
		for (int i = 0; i < Constants.NDP_NODE_COUNT; i++) {
			wtx.insertElementAsRightSibling(new QNm("foo"));
		}
	}

	/**
	 * Move through all nodes in a node page.
	 * 
	 * @param rtx
	 *          {@link NodeReadTrx} instance
	 * @throws SirixException
	 *           if movement fails
	 */
	private void move(final NodeReadTrx rtx) throws SirixException {
		for (int i = 0; i < Constants.NDP_NODE_COUNT; i++) {
			assertTrue(rtx.moveToRightSibling().hasMoved());
		}
	}

	// // @Test(expected = AssertionError.class)
	// @Test
	// public void testFulldumpCombinePages() {
	// final NodePage[] pages = new NodePage[1];
	// pages[0] = getNodePage(1, 0, 128, 0);
	// // pages[1] = getNodePage(0, 0, 128, 0);
	//
	// final NodePage page = ERevisioning.FULL.combineNodePages(pages,
	// ResourceConfiguration.VERSIONSTORESTORE);
	//
	// for (int j = 0; j < page.getNodes().length; j++) {
	// assertEquals(pages[0].getNode(j), page.getNode(j));
	// }
	//
	// }
	//
	// @Test
	// public void testDifferentialCombinePages() {
	// final NodePage[] pages = prepareNormal(2);
	// final NodePage page =
	// ERevisioning.DIFFERENTIAL.combineNodePages(pages,
	// ResourceConfiguration.VERSIONSTORESTORE);
	//
	// for (int j = 0; j < 32; j++) {
	// assertEquals(pages[0].getNode(j), page.getNode(j));
	// }
	// for (int j = 32; j < page.getNodes().length; j++) {
	// assertEquals(pages[1].getNode(j), page.getNode(j));
	// }
	//
	// }
	//
	// @Test
	// public void testIncrementalCombinePages() {
	// final NodePage[] pages = prepareNormal(4);
	// final NodePage page =
	// ERevisioning.INCREMENTAL.combineNodePages(pages, 4);
	// checkCombined(pages, page);
	// }
	//
	// private static NodePage[] prepareNormal(final int length) {
	// final NodePage[] pages = new NodePage[length];
	// pages[pages.length - 1] = getNodePage(0, 0, 128, 0);
	// for (int i = 0; i < pages.length - 1; i++) {
	// pages[i] = getNodePage(pages.length - i - 1, i * 32, (i * 32) + 32, 0);
	// }
	// return pages;
	// }
	//
	// // private static NodePage[] prepareOverlapping(final int length) {
	// // final NodePage[] pages = new NodePage[length];
	// // final int[] borders = new int[4];
	// // pages[pages.length - 1] = getNodePage(0, 0, 128);
	// // for (int i = 0; i < pages.length - 1; i++) {
	// // borders[i] = random.nextInt(32) + ((i) * 32);
	// // pages[i] = getNodePage(pages.length - i, borders[i], (i * 32) + 32);
	// // }
	// // return pages;
	// //
	// // }
	//
	// private static void checkCombined(final NodePage[] toCheck, final NodePage
	// page) {
	// for (int i = 0; i < 4; i++) {
	// for (int j = i * 32; j < (i * 32) + 32; j++) {
	// assertEquals(toCheck[i].getNode(j), page.getNode(j));
	// }
	// }
	// }

}