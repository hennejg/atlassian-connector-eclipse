/*******************************************************************************
 * Copyright (c) 2004 - 2006 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.ui;

import java.io.IOException;
import java.io.StringReader;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylar.internal.jira.core.JiraCorePlugin;
import org.eclipse.mylar.internal.jira.core.model.Comment;
import org.eclipse.mylar.internal.jira.core.model.Component;
import org.eclipse.mylar.internal.jira.core.model.Issue;
import org.eclipse.mylar.internal.jira.core.model.IssueType;
import org.eclipse.mylar.internal.jira.core.model.Priority;
import org.eclipse.mylar.internal.jira.core.model.Project;
import org.eclipse.mylar.internal.jira.core.model.Resolution;
import org.eclipse.mylar.internal.jira.core.model.Status;
import org.eclipse.mylar.internal.jira.core.model.Version;
import org.eclipse.mylar.internal.jira.core.service.JiraServer;
import org.eclipse.mylar.internal.jira.ui.html.HTML2TextReader;
import org.eclipse.mylar.tasks.core.AbstractAttributeFactory;
import org.eclipse.mylar.tasks.core.ITask;
import org.eclipse.mylar.tasks.core.ITaskDataHandler;
import org.eclipse.mylar.tasks.core.RepositoryOperation;
import org.eclipse.mylar.tasks.core.RepositoryTaskAttribute;
import org.eclipse.mylar.tasks.core.RepositoryTaskData;
import org.eclipse.mylar.tasks.core.Task;
import org.eclipse.mylar.tasks.core.TaskComment;
import org.eclipse.mylar.tasks.core.TaskRepository;
import org.eclipse.mylar.tasks.ui.TasksUiPlugin;

/**
 * @author Mik Kersten
 * @author Rob Elves
 * @author Eugene Kuleshov
 * @author Steffen Pingel
 */
public class JiraTaskDataHandler implements ITaskDataHandler {

	private AbstractAttributeFactory attributeFactory = new JiraAttributeFactory();

	private static final JiraAttributeFactory attributeFacotry = new JiraAttributeFactory();

	public JiraTaskDataHandler(JiraRepositoryConnector connector) {
		// this.connector = connector;
	}

	public RepositoryTaskData getTaskData(TaskRepository repository, String taskId) throws CoreException {
		JiraServer server = JiraServerFacade.getDefault().getJiraServer(repository);
		Issue jiraIssue = getJiraIssue(server, taskId, repository.getUrl());
		if (jiraIssue == null) {
			throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR, JiraCorePlugin.ID, IStatus.OK, "JIRA ticket not found: " + taskId, null));
		}
		RepositoryTaskData data = new RepositoryTaskData(attributeFactory, JiraUiPlugin.REPOSITORY_KIND, repository
				.getUrl(), jiraIssue.getId(), Task.DEFAULT_TASK_KIND);
		initializeTaskData(data, server, jiraIssue.getProject());
		updateTaskData(data, jiraIssue, server);
		addOperations(repository, jiraIssue, data);
		return data;
	}

	private Issue getJiraIssue(JiraServer server, String taskId, String repositoryUrl) throws CoreException {
		try {
			int id = Integer.parseInt(taskId);
			ITask task = TasksUiPlugin.getTaskListManager().getTaskList().getTask(repositoryUrl, "" + id);
			if (task instanceof JiraTask) {
				JiraTask jiraTask = (JiraTask) task;
				return server.getIssue(jiraTask.getKey());
			}
		} catch (NumberFormatException e) {
			return server.getIssue(taskId);
		}
		throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR, JiraCorePlugin.ID, IStatus.OK, "JIRA ticket not found: " + taskId, null));
	}

	@SuppressWarnings("deprecation")
	public void initializeTaskData(RepositoryTaskData data, JiraServer server, Project project) {
		data.removeAllAttributes();
		
		RepositoryTaskAttribute attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.DATE_CREATION,
				"Created: ", true);
		data.addAttribute(RepositoryTaskAttribute.DATE_CREATION, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.SUMMARY, "Summary: ", true);
		data.addAttribute(RepositoryTaskAttribute.SUMMARY, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.DESCRIPTION, "Description: ", true);
		attribute.setReadOnly(true);
		data.addAttribute(RepositoryTaskAttribute.DESCRIPTION, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.STATUS, "Status: ", true);
		data.addAttribute(RepositoryTaskAttribute.STATUS, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_ISSUE_KEY, "Issue ID: ", true);
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_ISSUE_KEY, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.RESOLUTION, "Resolution: ", true);
		data.addAttribute(RepositoryTaskAttribute.RESOLUTION, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.USER_ASSIGNED, "Assigned to: ", true);
		data.addAttribute(RepositoryTaskAttribute.USER_ASSIGNED, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.USER_REPORTER, "Reported by: ", true);
		data.addAttribute(RepositoryTaskAttribute.USER_REPORTER, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.DATE_MODIFIED, "Date modified: ", true);
		data.addAttribute(RepositoryTaskAttribute.DATE_MODIFIED, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_COMPONENTS, "Components: ", true);
		for (Component component : project.getComponents()) {
			attribute.addOption(component.getName(), component.getId());

		}
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_COMPONENTS, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS, "Fix Versions: ", true);
		for (Version version : project.getVersions()) {
			attribute.addOption(version.getName(), version.getId());
		}
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS, "Affects Versions: ",
				true);
		for (Version version : project.getVersions()) {
			attribute.addOption(version.getName(), version.getId());
		}
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_ESTIMATE, "Estimate: ", true);
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_ESTIMATE, attribute);

		// VISIBLE FIELDS (order added = order in layout)

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.PRODUCT, "Project: ", false);
		attribute.setReadOnly(true);
		data.addAttribute(RepositoryTaskAttribute.PRODUCT, attribute);

		attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.PRIORITY, "Priority: ", false);
		for (Priority priority : server.getPriorities()) {
			attribute.addOption(priority.getName(), priority.getId());
		}
		data.addAttribute(RepositoryTaskAttribute.PRIORITY, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_TYPE, "Type: ", false);
		for (IssueType type : server.getIssueTypes()) {
			attribute.addOption(type.getName(), type.getId());
		}
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_TYPE, attribute);

		attribute = new RepositoryTaskAttribute(JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT, "Environment: ", false);
		data.addAttribute(JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT, attribute);
	}

	@SuppressWarnings("deprecation")
	private void updateTaskData(RepositoryTaskData data, Issue jiraIssue, JiraServer server) {
		data.setAttributeValue(RepositoryTaskAttribute.DATE_CREATION, jiraIssue.getCreated().toGMTString());
		data.setAttributeValue(RepositoryTaskAttribute.SUMMARY, convertHtml(jiraIssue.getSummary()));
		data.setAttributeValue(RepositoryTaskAttribute.DESCRIPTION, convertHtml(jiraIssue.getDescription()));
		data.setAttributeValue(RepositoryTaskAttribute.STATUS, convertHtml(jiraIssue.getStatus().getName()));
		data.setAttributeValue(JiraAttributeFactory.ATTRIBUTE_ISSUE_KEY, jiraIssue.getKey());
		data.setAttributeValue(RepositoryTaskAttribute.RESOLUTION, jiraIssue.getResolution() == null ? "" : jiraIssue.getResolution().getName());
		data.setAttributeValue(RepositoryTaskAttribute.USER_ASSIGNED, getAssignee(jiraIssue));
		data.setAttributeValue(RepositoryTaskAttribute.USER_REPORTER, jiraIssue.getReporter());
		data.setAttributeValue(RepositoryTaskAttribute.DATE_MODIFIED, jiraIssue.getUpdated().toGMTString());
		for (Component component : jiraIssue.getComponents()) {
			data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_COMPONENTS, component.getName());
		}
		for (Version version : jiraIssue.getFixVersions()) {
			data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS, version.getName());
		}
		for (Version version : jiraIssue.getReportedVersions()) {
			data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS, version.getName());
		}
		data.setAttributeValue(JiraAttributeFactory.ATTRIBUTE_ESTIMATE, String.valueOf(jiraIssue.getEstimate()));

		// VISIBLE FIELDS (order added = order in layout)

		data.setAttributeValue(RepositoryTaskAttribute.PRODUCT, jiraIssue.getProject().getName());
		data.setAttributeValue(RepositoryTaskAttribute.PRIORITY, jiraIssue.getPriority().getName());
		data.setAttributeValue(JiraAttributeFactory.ATTRIBUTE_TYPE, jiraIssue.getType().getName());
		data.setAttributeValue(JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT, convertHtml(jiraIssue.getEnvironment()));

		int x = 1;
		for (Comment comment : jiraIssue.getComments()) {
			if (comment != null) {
				TaskComment taskComment = new TaskComment(attributeFacotry, x++);

				RepositoryTaskAttribute attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.USER_OWNER, "Commenter: ", true);
				attribute.setValue(comment.getAuthor());
				taskComment.addAttribute(RepositoryTaskAttribute.USER_OWNER, attribute);

				attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.COMMENT_TEXT, "Text: ", true);
				attribute.setValue(convertHtml(comment.getComment()));
				attribute.setReadOnly(true);
				taskComment.addAttribute(RepositoryTaskAttribute.COMMENT_TEXT, attribute);

				attribute = new RepositoryTaskAttribute(RepositoryTaskAttribute.COMMENT_DATE, "Text: ", true);
				attribute.setValue(comment.getCreated().toGMTString());
				taskComment.addAttribute(RepositoryTaskAttribute.COMMENT_DATE, attribute);

				data.addComment(taskComment);

			}
		}

	}

	private String getAssignee(Issue jiraIssue) {
		String assignee = jiraIssue.getAssignee(); 
		return assignee == null || JiraTask.UNASSIGNED_USER.equals(assignee) ? "" : assignee;
	}
	
	@SuppressWarnings("restriction")
	private String convertHtml(String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		StringReader stringReader = new StringReader(text);
		HTML2TextReader html2TextReader = new HTML2TextReader(stringReader, null);
		try {
			char[] chars = new char[text.length()];
			int len = html2TextReader.read(chars, 0, text.length());
			return new String(chars, 0, len).trim();
		} catch (IOException e) {
			return text;
		}
	}
	
	private void addOperations(TaskRepository repository, Issue issue, RepositoryTaskData data) {
		Status status = issue.getStatus();

		RepositoryOperation opLeave = new RepositoryOperation("leave", "Leave as " + issue.getStatus().getName());
		// RepositoryOperation opStart = new
		// RepositoryOperation(Status.STARTED_ID, "Start");
		// RepositoryOperation opStop = new RepositoryOperation(Status.OPEN_ID,
		// "Stop (open)");
		RepositoryOperation opReopen = new RepositoryOperation(Status.REOPENED_ID, "Reopen");

		RepositoryOperation opResolve = new RepositoryOperation(Status.RESOLVED_ID, "Resolve");
		opResolve.setUpOptions("resolution");
		opResolve.addOption("Fixed", Resolution.FIXED_ID);
		opResolve.addOption("Won't Fix", Resolution.WONT_FIX_ID);
		opResolve.addOption("Duplicate", Resolution.DUPLICATE_ID);
		opResolve.addOption("Incomplete", Resolution.INCOMPLETE_ID);
		opResolve.addOption("Cannot Reproduce", Resolution.CANNOT_REPRODUCE_ID);

		RepositoryOperation opClose = new RepositoryOperation(Status.CLOSED_ID, "Close");
		opClose.setUpOptions("resolution");
		opClose.addOption("Fixed", Resolution.FIXED_ID);
		opClose.addOption("Won't Fix", Resolution.WONT_FIX_ID);
		opClose.addOption("Duplicate", Resolution.DUPLICATE_ID);
		opClose.addOption("Incomplete", Resolution.INCOMPLETE_ID);
		opClose.addOption("Cannot Reproduce", Resolution.CANNOT_REPRODUCE_ID);

		RepositoryOperation reassignOperation = new RepositoryOperation("reassign", "Reassign to");
		reassignOperation.setInputName("assignee");
		reassignOperation.setInputValue(repository.getUserName());

		opLeave.setChecked(true);
		data.addOperation(opLeave);
		if (status.getId().equals(Status.OPEN_ID) || status.getId().equals(Status.STARTED_ID)) {
			data.addOperation(opResolve);
			data.addOperation(opClose);
			data.addOperation(reassignOperation);
			// //data.addOperation(opStart);
			// } else if (status.getId().equals(Status.STARTED_ID)) {
			// data.addOperation(opResolve);
			// data.addOperation(opStop);
		} else if (status.getId().equals(Status.RESOLVED_ID)) {
			data.addOperation(opReopen);
			data.addOperation(opClose);
		} else if (status.getId().equals(Status.REOPENED_ID)) {
			data.addOperation(opResolve);
			data.addOperation(opClose);
			// data.addOperation(opStart);
			data.addOperation(reassignOperation);
		} else if (status.getId().equals(Status.CLOSED_ID)) {
			data.addOperation(opReopen);
		}
	}

	public String postTaskData(TaskRepository repository, RepositoryTaskData taskData) throws CoreException {
		final JiraServer jiraServer = JiraServerFacade.getDefault().getJiraServer(repository);
		if (jiraServer == null) {
			throw new CoreException(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.Status.ERROR,
					JiraCorePlugin.ID, org.eclipse.core.runtime.Status.ERROR, "Unable to produce Jira Server", null));
		}

		Issue issue = JiraRepositoryConnector.buildJiraIssue(taskData, jiraServer);
		if (taskData.isNew()) {
			issue = jiraServer.createIssue(issue);
			if (issue == null ){
				throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR, JiraCorePlugin.ID, IStatus.OK, "Could not create ticket.", null));
			}
			// this is severly broken: should return id instead
			return issue.getKey();
		} else {
			RepositoryOperation operation = taskData.getSelectedOperation();
			if (operation != null) {
				if ("leave".equals(operation.getKnobName()) || "reassign".equals(operation.getKnobName())) {
					if (!issue.getStatus().isClosed()) {
						jiraServer.updateIssue(issue, taskData.getNewComment());
					} else if (taskData.getNewComment() != null && taskData.getNewComment().length() > 0) {
						jiraServer.addCommentToIssue(issue, taskData.getNewComment());
					}
				} else if (org.eclipse.mylar.internal.jira.core.model.Status.RESOLVED_ID.equals(operation.getKnobName())) {
					String value = operation.getOptionValue(operation.getOptionSelection());
					jiraServer.resolveIssue(issue, jiraServer.getResolutionById(value), issue.getFixVersions(), taskData
							.getNewComment(), JiraServer.ASSIGNEE_CURRENT, repository.getUserName());
				} else if (org.eclipse.mylar.internal.jira.core.model.Status.REOPENED_ID.equals(operation.getKnobName())) {
					jiraServer.reopenIssue(issue, taskData.getNewComment(), JiraServer.ASSIGNEE_CURRENT, repository
							.getUserName());
				} else if (org.eclipse.mylar.internal.jira.core.model.Status.STARTED_ID.equals(operation.getKnobName())) {
					jiraServer.startIssue(issue, taskData.getNewComment(), repository.getUserName());
				} else if (org.eclipse.mylar.internal.jira.core.model.Status.OPEN_ID.equals(operation.getKnobName())) {
					jiraServer.startIssue(issue, taskData.getNewComment(), repository.getUserName());
				} else if (org.eclipse.mylar.internal.jira.core.model.Status.CLOSED_ID.equals(operation.getKnobName())) {
					String value = operation.getOptionValue(operation.getOptionSelection());
					jiraServer.closeIssue(issue, jiraServer.getResolutionById(value), issue.getFixVersions(), taskData
							.getNewComment(), JiraServer.ASSIGNEE_CURRENT, repository.getUserName());
				}
			} else {
				jiraServer.updateIssue(issue, taskData.getNewComment());
			}
			return "";
		}
	}

	public boolean initializeTaskData(TaskRepository repository, RepositoryTaskData data, IProgressMonitor monitor)
			throws CoreException {
		// JIRA needs a project to create task data
		return false;
	}

	public AbstractAttributeFactory getAttributeFactory(String repositoryUrl, String repositoryKind, String taskKind) {
		// we don't care about the repository information right now
		return attributeFactory;
	}
}