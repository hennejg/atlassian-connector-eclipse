/*******************************************************************************
 * Copyright (c) 2004, 2009 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.jira.tests.core;

import junit.framework.TestCase;

import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.commons.net.WebLocation;
import org.eclipse.mylyn.internal.jira.core.JiraClientFactory;
import org.eclipse.mylyn.internal.jira.core.JiraCorePlugin;
import org.eclipse.mylyn.internal.jira.core.service.JiraAuthenticationException;
import org.eclipse.mylyn.internal.jira.core.service.JiraClient;
import org.eclipse.mylyn.internal.jira.core.service.JiraException;
import org.eclipse.mylyn.internal.jira.core.service.JiraServiceUnavailableException;
import org.eclipse.mylyn.internal.jira.core.util.JiraUtil;
import org.eclipse.mylyn.internal.tasks.core.TaskRepositoryChangeEvent;
import org.eclipse.mylyn.internal.tasks.core.TaskRepositoryDelta;
import org.eclipse.mylyn.internal.tasks.core.TaskRepositoryDelta.Type;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.jira.tests.util.JiraTestConstants;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tests.util.TestFixture;
import org.eclipse.mylyn.tests.util.TestUtil;
import org.eclipse.mylyn.tests.util.TestUtil.Credentials;
import org.eclipse.mylyn.tests.util.TestUtil.PrivilegeLevel;

/**
 * @author Wesley Coelho (initial integration patch)
 * @author Steffen Pingel
 */
public class JiraClientFactoryTest extends TestCase {

	private JiraClientFactory clientFactory;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		clientFactory = JiraClientFactory.getDefault();
		TestFixture.resetTaskListAndRepositories();
	}

	@Override
	protected void tearDown() throws Exception {
		clientFactory.logOutFromAll();
	}

	public void testValidate39() throws Exception {
		validate(JiraTestConstants.JIRA_LATEST_URL);
	}

	public void testValidate() throws Exception {
		// invalid URL		
		try {
			clientFactory.validateConnection(new WebLocation("http://non.existant/repository", "user", "password"),
					null);
			fail("Expected exception");
		} catch (JiraServiceUnavailableException e) {
		}

		// not found		
		try {
			clientFactory.validateConnection(new WebLocation("http://mylyn.eclipse.org/not-found", "user", "password"),
					null);
			fail("Expected exception");
		} catch (JiraServiceUnavailableException e) {
			assertEquals("No JIRA repository found at location.", e.getMessage());
		}

		// RPC not enabled
		try {
			clientFactory.validateConnection(new WebLocation("http://mylyn.eclipse.org/jira-invalid", "user",
					"password"), null);
			fail("Expected exception");
		} catch (JiraServiceUnavailableException e) {
			assertEquals("JIRA RPC services are not enabled. Please contact your JIRA administrator.", e.getMessage());
		}

		// HTTP error
		try {
			clientFactory.validateConnection(new WebLocation("http://mylyn.eclipse.org/jira-proxy-error", "user",
					"password"), null);
			fail("Expected exception");
		} catch (JiraServiceUnavailableException e) {
			assertEquals("JIRA RPC services are not enabled. Please contact your JIRA administrator.", e.getMessage());
		}
	}

	public void testChangeCredentials() throws Exception {
		Credentials credentials = TestUtil.readCredentials(PrivilegeLevel.USER);
		TaskRepository repository = new TaskRepository(JiraCorePlugin.CONNECTOR_KIND, JiraTestConstants.JIRA_LATEST_URL);
		repository.setCredentials(AuthenticationType.REPOSITORY, new AuthenticationCredentials(credentials.username,
				credentials.password), false);
		clientFactory.repositoryRemoved(repository);
		TasksUiPlugin.getRepositoryManager().addRepository(repository);

		repository.setCredentials(AuthenticationType.REPOSITORY, new AuthenticationCredentials("Bogus User",
				"Bogus Password"), false);
		clientFactory.repositoryRemoved(repository);

		try {
			clientFactory.getJiraClient(repository).getNamedFilters(null);
			fail("Expected to fail on bogus user");
		} catch (JiraException e) {
			// ignore
		}

		// check that it works after putting the right password in
		repository.setCredentials(AuthenticationType.REPOSITORY, new AuthenticationCredentials(credentials.username,
				credentials.password), false);
		clientFactory.repositoryRemoved(repository);
		clientFactory.getJiraClient(repository).getNamedFilters(null);
	}

	protected void validate(String url) throws Exception {
		Credentials credentials = TestUtil.readCredentials(PrivilegeLevel.USER);

		// standard connect
		clientFactory.validateConnection(new WebLocation(url, credentials.username, credentials.password), null);

		// invalid password
		try {
			clientFactory.validateConnection(new WebLocation(url, credentials.username, "wrongpassword"), null);
			fail("Expected exception");
		} catch (JiraAuthenticationException e) {
		}
	}

	public void testCharacterEncoding() throws Exception {
		Credentials credentials = TestUtil.readCredentials(PrivilegeLevel.USER);
		TaskRepository repository = new TaskRepository(JiraCorePlugin.CONNECTOR_KIND, JiraTestConstants.JIRA_LATEST_URL);
		repository.setCredentials(AuthenticationType.REPOSITORY, new AuthenticationCredentials(credentials.username,
				credentials.password), false);
		assertFalse(JiraUtil.getCharacterEncodingValidated(repository));

		JiraClient client = clientFactory.getJiraClient(repository);
		assertEquals("ISO-8859-1", client.getCharacterEncoding());

		repository.setCharacterEncoding("UTF-8");
		clientFactory.repositoryChanged(new TaskRepositoryChangeEvent(this, repository, new TaskRepositoryDelta(
				Type.PROPERTY)));
		client = clientFactory.getJiraClient(repository);
		assertEquals("ISO-8859-1", client.getCharacterEncoding());

		JiraUtil.setCharacterEncodingValidated(repository, true);
		clientFactory.repositoryChanged(new TaskRepositoryChangeEvent(this, repository, new TaskRepositoryDelta(
				Type.PROPERTY)));
		client = clientFactory.getJiraClient(repository);
		assertEquals("UTF-8", client.getCharacterEncoding());
	}

}
