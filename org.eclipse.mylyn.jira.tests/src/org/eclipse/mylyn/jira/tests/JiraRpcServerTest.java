/*******************************************************************************
 * Copyright (c) 2004 - 2006 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylar.jira.tests;

import java.net.Proxy;

import junit.framework.TestCase;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylar.context.tests.support.MylarTestUtils;
import org.eclipse.mylar.context.tests.support.MylarTestUtils.Credentials;
import org.eclipse.mylar.context.tests.support.MylarTestUtils.PrivilegeLevel;
import org.eclipse.mylar.internal.jira.core.model.Issue;
import org.eclipse.mylar.internal.jira.core.service.AbstractJiraServer;
import org.eclipse.mylar.internal.jira.core.service.JiraException;
import org.eclipse.mylar.internal.jira.core.service.JiraRemoteMessageException;
import org.eclipse.mylar.internal.jira.core.service.soap.JiraRpcServer;

/**
 * @author Steffen Pingel
 */
public class JiraRpcServerTest extends TestCase {

	private AbstractJiraServer server;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		Credentials credentials = MylarTestUtils.readCredentials(PrivilegeLevel.USER);
		server = new JiraRpcServer("server", JiraTestConstants.JIRA_381_URL, false, credentials.username,
				credentials.password, Proxy.NO_PROXY, null, null);
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	@SuppressWarnings("deprecation")
	public void testLogin() throws Exception {
		server.login();
		server.logout();
		// should automatically login
		server.refreshDetails(new NullProgressMonitor());
	}

	public void testStartStopIssue() throws Exception {
		Issue issue = JiraTestUtils.createIssue(server, "testStartStopIssue");
		server.startIssue(issue);
		try {
			server.startIssue(issue);
			fail("Expected JiraRemoteMessageException");
		} catch (JiraRemoteMessageException e) {
			assertEquals("Workflow Action Invalid", e.getMessage());
		}
		server.stopIssue(issue);
		try {
			server.stopIssue(issue);
			fail("Expected JiraRemoteMessageException");
		} catch (JiraException e) {
			assertEquals("Workflow Action Invalid", e.getMessage());
		}
		server.startIssue(issue);
	}

// public void testResolveIssue() {
// fail("Not yet implemented");
// }
//
// public void testCloseIssue() {
// fail("Not yet implemented");
// }
//
// public void testReopenIssue() {
// fail("Not yet implemented");
// }

	public void testGetIdFromKey() throws Exception {
		Issue issue = JiraTestUtils.createIssue(server, "testStartStopIssue");
		String key = server.getKeyFromId(issue.getId());
		assertEquals(issue.getKey(), key);

		try {
			key = server.getKeyFromId("invalid");
			fail("Expected JiraException, got: " + key);
		} catch (JiraException e) {
		}

		try {
			key = server.getKeyFromId("1");
			fail("Expected JiraException, got: " + key);
		} catch (JiraException e) {
		}

	}
	
}
