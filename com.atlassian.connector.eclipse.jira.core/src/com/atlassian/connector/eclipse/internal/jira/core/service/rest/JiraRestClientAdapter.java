/*******************************************************************************

 * Copyright (c) 2009 Atlassian and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlassian - initial API and implementation
 ******************************************************************************/

package com.atlassian.connector.eclipse.internal.jira.core.service.rest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.atlassian.connector.eclipse.internal.jira.core.model.JiraIssue;
import com.atlassian.connector.eclipse.internal.jira.core.model.JiraStatus;
import com.atlassian.connector.eclipse.internal.jira.core.model.NamedFilter;
import com.atlassian.connector.eclipse.internal.jira.core.model.Priority;
import com.atlassian.connector.eclipse.internal.jira.core.model.Project;
import com.atlassian.connector.eclipse.internal.jira.core.model.Resolution;
import com.atlassian.connector.eclipse.internal.jira.core.service.JiraClientCache;
import com.atlassian.connector.eclipse.internal.jira.core.service.JiraException;
import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.NullProgressMonitor;
import com.atlassian.jira.rest.client.domain.BasicProject;
import com.atlassian.jira.rest.client.domain.Comment;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

public class JiraRestClientAdapter {

	private JiraRestClient restClient;

	private final JiraClientCache cache;

	public JiraRestClientAdapter(String url, String userName, String password, JiraClientCache cache) {

		this.cache = cache;

		JerseyJiraRestClientFactory restFactory = new JerseyJiraRestClientFactory();
		try {
			this.restClient = restFactory.createWithBasicHttpAuthentication(new URI(url), userName, password);
		} catch (URISyntaxException e) {
			// TODO jiraRestClient not initialized
			e.printStackTrace();
		}
	}

	public void addComment(String issueKey, String comment) {
		restClient.getIssueClient().addComment(new NullProgressMonitor(), getIssue(issueKey).getCommentsUri(),
				Comment.valueOf(comment));

	}

	private Issue getIssue(String issueKey) {
		return restClient.getIssueClient().getIssue(issueKey, new NullProgressMonitor());
	}

	public void attAttachment(String issueKey, byte[] content, String filename) {
		restClient.getIssueClient().addAttachment(new NullProgressMonitor(), getIssue(issueKey).getAttachmentsUri(),
				new ByteArrayInputStream(content), filename);
	}

	public Project[] getProjects() {
		Iterable<BasicProject> allProjects = restClient.getProjectClient().getAllProjects(new NullProgressMonitor());

		return JiraRestConverter.convertProjects(allProjects);
	}

	public NamedFilter[] getFavouriteFilters() throws Exception {
		throw new Exception("not implemented");

	}

	public Resolution[] getResolutions() {
		return JiraRestConverter.convertResolutions(restClient.getMetadataClient().getResolutions(
				new NullProgressMonitor()));
	}

	public Priority[] getPriorities() {
		return JiraRestConverter.convertPriorities(restClient.getMetadataClient().getPriorities(
				new NullProgressMonitor()));
	}

	public JiraIssue getIssueByKey(String issueKey, IProgressMonitor monitor) throws JiraException {
		return JiraRestConverter.convertIssue(getIssue(issueKey), cache, monitor);
	}

	public JiraStatus[] getStatuses() throws Exception {
		throw new Exception("not implemented");
	}

}
