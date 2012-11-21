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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;

import com.atlassian.connector.eclipse.internal.jira.core.model.IssueType;
import com.atlassian.connector.eclipse.internal.jira.core.model.JiraAction;
import com.atlassian.connector.eclipse.internal.jira.core.model.JiraIssue;
import com.atlassian.connector.eclipse.internal.jira.core.model.JiraStatus;
import com.atlassian.connector.eclipse.internal.jira.core.model.JiraWorkLog;
import com.atlassian.connector.eclipse.internal.jira.core.model.NamedFilter;
import com.atlassian.connector.eclipse.internal.jira.core.model.Priority;
import com.atlassian.connector.eclipse.internal.jira.core.model.Project;
import com.atlassian.connector.eclipse.internal.jira.core.model.Resolution;
import com.atlassian.connector.eclipse.internal.jira.core.model.ServerInfo;
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

	private final String url;

	public JiraRestClientAdapter(String url, String userName, String password, JiraClientCache cache) {

		this.url = url;
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

	public void addAttachment(String issueKey, byte[] content, String filename) {
		restClient.getIssueClient().addAttachment(new NullProgressMonitor(), getIssue(issueKey).getAttachmentsUri(),
				new ByteArrayInputStream(content), filename);
	}

	public InputStream getAttachment(URI attachmentUri) {
		return restClient.getIssueClient().getAttachment(new NullProgressMonitor(), attachmentUri);
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
		return JiraRestConverter.convertIssue(getIssue(issueKey), cache, url, monitor);
	}

	public JiraStatus[] getStatuses() throws Exception {
		throw new Exception("not implemented");
	}

	public IssueType[] getIssueTypes() {
		return JiraRestConverter.convertIssueTypes(restClient.getMetadataClient().getIssueTypes(
				new NullProgressMonitor()));
	}

	public IssueType[] getIssueTypes(String projectKey) {
		return JiraRestConverter.convertIssueTypes(restClient.getProjectClient()
				.getProject(projectKey, new NullProgressMonitor())
				.getIssueTypes());
	}

	public JiraIssue getIssueById(String issueId, IProgressMonitor monitor) throws JiraException {

		// TODO rest remove once we have id in place
		// strip key from id = url_key
		String issueKey = issueId.split("_")[1].replace('*', '-');

		return getIssueByKey(issueKey, monitor);
	}

	public List<JiraIssue> getIssues(String jql, IProgressMonitor monitor) throws JiraException {
		List<JiraIssue> issues = JiraRestConverter.convertIssues(restClient.getSearchClient()
				.searchJql(jql, new NullProgressMonitor())
				.getIssues());

		List<JiraIssue> fullIssues = new ArrayList<JiraIssue>();

		for (JiraIssue issue : issues) {
			fullIssues.add(JiraRestConverter.convertIssue(getIssue(issue.getKey()), cache, url, monitor));
		}

		return fullIssues;
	}

//	public Component[] getComponents(String projectKey) {
//		return JiraRestConverter.convertComponents(restClient.getProjectClient()
//				.getProject(projectKey, new NullProgressMonitor())
//				.getComponents());
//	}
//
//	public Version[] getVersions(String projectKey) {
//		return JiraRestConverter.convertVersions(restClient.getProjectClient()
//				.getProject(projectKey, new NullProgressMonitor())
//				.getVersions());
//	}

	public void getProjectDetails(Project project) {

		com.atlassian.jira.rest.client.domain.Project projectWithDetails = restClient.getProjectClient().getProject(
				project.getKey(), new NullProgressMonitor());

		project.setComponents(JiraRestConverter.convertComponents(projectWithDetails.getComponents()));
		project.setVersions(JiraRestConverter.convertVersions(projectWithDetails.getVersions()));
		project.setIssueTypes(JiraRestConverter.convertIssueTypes(projectWithDetails.getIssueTypes()));
	}

	public void addWorklog(String issueKey, JiraWorkLog jiraWorklog) {
		Issue issue = getIssue(issueKey);
		restClient.getIssueClient().addWorklog(issue.getWorklogUri(),
				JiraRestConverter.convert(jiraWorklog, issue.getSelf()), new NullProgressMonitor());
	}

	public ServerInfo getServerInfo() {

//		GetCreateIssueMetadataOptionsBuilder builder = new GetCreateIssueMetadataOptionsBuilder();
//		builder.withExpandedIssueTypesFields().withProjectKeys("TEST");
//
//		Iterable<CimProject> createIssueMetadata = restClient.getIssueClient().getCreateIssueMetadata(builder.build(),
//				new NullProgressMonitor());

		return JiraRestConverter.convert(restClient.getMetadataClient().getServerInfo(new NullProgressMonitor()));
	}

	public JiraAction[] getTransitions(String issueKey) {

		return JiraRestConverter.convertTransitions(restClient.getIssueClient().getTransitions(getIssue(issueKey),
				new NullProgressMonitor()));
	}
}
