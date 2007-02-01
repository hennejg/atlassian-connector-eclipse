/*******************************************************************************
 * Copyright (c) 2007 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Brock Janiczak - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.core.model;

import java.io.Serializable;
import java.util.Date;

/**
 * @author	Brock Janiczak
 */
public class Issue implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;

	private String key;

	private String summary;

	private String environment;

	private String description;

	private Project project;

	private IssueType type;

	private Priority priority;

	private Status status;

	private Resolution resolution;

	private String assigneeName;

	private String reporterName;

	private Date created;

	private Date updated;

	private Version[] reportedVersions = new Version[0];

	private Version[] fixVersions = new Version[0];

	private Component[] components = new Component[0];

	private Date due;

	private int votes;

	private Comment[] comments = new Comment[0];

	private long initialEstimate;

	private long estimate;

	private long actual;

	private boolean isWatched;

	private boolean hasVote;

	private String url;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAssignee() {
		return this.assigneeName;
	}

	public void setAssignee(String asignee) {
		this.assigneeName = asignee;
	}

	public Component[] getComponents() {
		return this.components;
	}

	public void setComponents(Component[] components) {
		this.components = components;
	}

	public Date getCreated() {
		return this.created;
	}

	public void setCreated(Date created) {
		this.created = created;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Date getDue() {
		return this.due;
	}

	public void setDue(Date due) {
		this.due = due;
	}

	public Version[] getFixVersions() {
		return this.fixVersions;
	}

	public void setFixVersions(Version[] fixVersions) {
		this.fixVersions = fixVersions;
	}

	public String getKey() {
		return this.key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public Priority getPriority() {
		return this.priority;
	}

	public void setPriority(Priority priority) {
		this.priority = priority;
	}

	public String getReporter() {
		return this.reporterName;
	}

	public void setReporter(String reporter) {
		this.reporterName = reporter;
	}

	public Resolution getResolution() {
		return this.resolution;
	}

	public void setResolution(Resolution resolution) {
		this.resolution = resolution;
	}

	public Status getStatus() {
		return this.status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getSummary() {
		return this.summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public IssueType getType() {
		return this.type;
	}

	public void setType(IssueType type) {
		this.type = type;
	}

	public Date getUpdated() {
		return this.updated;
	}

	public void setUpdated(Date updated) {
		this.updated = updated;
	}

	public Version[] getReportedVersions() {
		return this.reportedVersions;
	}

	public void setReportedVersions(Version[] reportedVersions) {
		this.reportedVersions = reportedVersions;
	}

	public int getVotes() {
		return this.votes;
	}

	public void setVotes(int votes) {
		this.votes = votes;
	}

	public Comment[] getComments() {
		return this.comments;
	}

	public void setComments(Comment[] comments) {
		this.comments = comments;
	}

	public String getEnvironment() {
		return this.environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public Project getProject() {
		return this.project;
	}

	public void setProject(Project project) {
		this.project = project;
	}

	public long getActual() {
		return this.actual;
	}

	public void setActual(long actual) {
		this.actual = actual;
	}

	public long getInitialEstimate() {
		return this.initialEstimate;
	}

	public void setInitialEstimate(long initialEstimate) {
		this.initialEstimate = initialEstimate;
	}

	public long getEstimate() {
		return this.estimate;
	}

	public void setEstimate(long estimate) {
		this.estimate = estimate;
	}

	public void setWatched(boolean isWatched) {
		this.isWatched = isWatched;
	}

	public boolean isWatched() {
		// XXX Requires new API to work
		return isWatched;
	}

	/**
	 * Determines if it is ok for the supplied user to vote on this issue. Users
	 * can not vote on an issue if the issue is resolved or the user is the
	 * reporter.
	 * 
	 * @return <code>true</code> if it is valid for <code>user</code> to
	 *         vote for the issue
	 */
	public boolean canUserVote(String user) {
		return (this.getResolution() == null) && !(user.equals(this.getReporter()));
	}

	public void setHasVote(boolean hasVote) {
		this.hasVote = hasVote;
	}

	/**
	 * Determines if this issue has been voted on by the current user
	 * 
	 * @return <code>true</code> if the current user is voting for this issue.
	 *         <code>false</code> otherwise.
	 */
	public boolean getHasVote() {
		// XXX Required new API to work
		return this.hasVote;
	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return this.url;
	}

	/**
	 * @param url
	 *            the url to set
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return this.key + " " + this.summary;
	}
}
