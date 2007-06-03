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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylar.core.MylarStatusHandler;
import org.eclipse.mylar.internal.jira.core.JiraCorePlugin;
import org.eclipse.mylar.internal.jira.core.model.Attachment;
import org.eclipse.mylar.internal.jira.core.model.Comment;
import org.eclipse.mylar.internal.jira.core.model.Component;
import org.eclipse.mylar.internal.jira.core.model.CustomField;
import org.eclipse.mylar.internal.jira.core.model.Issue;
import org.eclipse.mylar.internal.jira.core.model.IssueType;
import org.eclipse.mylar.internal.jira.core.model.Priority;
import org.eclipse.mylar.internal.jira.core.model.Project;
import org.eclipse.mylar.internal.jira.core.model.Resolution;
import org.eclipse.mylar.internal.jira.core.model.Status;
import org.eclipse.mylar.internal.jira.core.model.Subtask;
import org.eclipse.mylar.internal.jira.core.model.Version;
import org.eclipse.mylar.internal.jira.core.service.JiraClient;
import org.eclipse.mylar.internal.jira.core.service.JiraException;
import org.eclipse.mylar.internal.jira.core.service.JiraInsufficientPermissionException;
import org.eclipse.mylar.internal.jira.ui.html.HTML2TextReader;
import org.eclipse.mylar.tasks.core.AbstractAttributeFactory;
import org.eclipse.mylar.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylar.tasks.core.ITask;
import org.eclipse.mylar.tasks.core.ITaskDataHandler;
import org.eclipse.mylar.tasks.core.RepositoryAttachment;
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

	private static final JiraAttributeFactory attributeFactory = new JiraAttributeFactory();

	public JiraTaskDataHandler(JiraRepositoryConnector connector) {
	}

	public RepositoryTaskData getTaskData(TaskRepository repository, String taskId, IProgressMonitor monitor) throws CoreException {
		try {
			JiraClient server = JiraClientFacade.getDefault().getJiraClient(repository);
			if (!server.hasDetails()) {
				server.refreshDetails(new NullProgressMonitor());
			}
			Issue jiraIssue = getJiraIssue(server, taskId, repository.getUrl());
			if (jiraIssue == null) {
				throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR, JiraCorePlugin.ID,
						IStatus.OK, "JIRA ticket not found: " + taskId, null));
			}
			
			RepositoryTaskData data = new RepositoryTaskData(attributeFactory, JiraUiPlugin.REPOSITORY_KIND, repository
					.getUrl(), jiraIssue.getId(), Task.DEFAULT_TASK_KIND);
			initializeTaskData(data, server, jiraIssue.getProject());
			updateTaskData(data, jiraIssue, server);
			addOperations(data, jiraIssue, server);
			return data;
		} catch (JiraException e) {
			throw new CoreException(JiraCorePlugin.toStatus(repository, e));
		}
	}

	private Issue getJiraIssue(JiraClient server, String taskId, String repositoryUrl) throws CoreException,
			JiraException {
		try {
			int id = Integer.parseInt(taskId);
			ITask task = TasksUiPlugin.getTaskListManager().getTaskList().getTask(repositoryUrl, "" + id);
			if (task instanceof JiraTask) {
				JiraTask jiraTask = (JiraTask) task;
				return server.getIssueByKey(jiraTask.getTaskKey());
			} else {
				return server.getIssueById(taskId);
			}
		} catch (NumberFormatException e) {
			return server.getIssueByKey(taskId);
		}
	}

	public void initializeTaskData(RepositoryTaskData data, JiraClient server, Project project) {
		data.removeAllAttributes();

		addAttribute(data, RepositoryTaskAttribute.DATE_CREATION);
		addAttribute(data, RepositoryTaskAttribute.SUMMARY);
		addAttribute(data, RepositoryTaskAttribute.DESCRIPTION);
		addAttribute(data, RepositoryTaskAttribute.STATUS);
		addAttribute(data, RepositoryTaskAttribute.TASK_KEY);
		addAttribute(data, RepositoryTaskAttribute.RESOLUTION);
		addAttribute(data, RepositoryTaskAttribute.USER_ASSIGNED);
		addAttribute(data, RepositoryTaskAttribute.USER_REPORTER);
		addAttribute(data, RepositoryTaskAttribute.DATE_MODIFIED);
		
		addAttribute(data, RepositoryTaskAttribute.PRODUCT);
		
		RepositoryTaskAttribute priorities = addAttribute(data, RepositoryTaskAttribute.PRIORITY);
		for (Priority priority : server.getPriorities()) {
			priorities.addOption(priority.getName(), priority.getId());
		}
		
		RepositoryTaskAttribute types = addAttribute(data, JiraAttributeFactory.ATTRIBUTE_TYPE);
		for (IssueType type : server.getIssueTypes()) {
			types.addOption(type.getName(), type.getId());
		}
		
		addAttribute(data, JiraAttributeFactory.ATTRIBUTE_ISSUE_PARENT_KEY);
		
		addAttribute(data, JiraAttributeFactory.ATTRIBUTE_ESTIMATE);
		
		RepositoryTaskAttribute affectsVersions = addAttribute(data, JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS);
		for (Version version : project.getVersions()) {
			affectsVersions.addOption(version.getName(), version.getId());
		}
		
		RepositoryTaskAttribute components = addAttribute(data, JiraAttributeFactory.ATTRIBUTE_COMPONENTS);
		for (Component component : project.getComponents()) {
			components.addOption(component.getName(), component.getId());
		}

		RepositoryTaskAttribute fixVersions = addAttribute(data, JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS);
		for (Version version : project.getVersions()) {
			fixVersions.addOption(version.getName(), version.getId());
		}

		addAttribute(data, JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT);
	}

	private RepositoryTaskAttribute addAttribute(RepositoryTaskData data, String key) {
		data.addAttribute(key, attributeFactory.createAttribute(key));
		return data.getAttribute(key); 
	}

	private void updateTaskData(RepositoryTaskData data, Issue jiraIssue, JiraClient server) throws JiraException {
		String parentKey = jiraIssue.getParentKey();
		if(parentKey!=null) {
			data.setAttributeValue(JiraAttributeFactory.ATTRIBUTE_ISSUE_PARENT_KEY, parentKey);
		} else {
			data.removeAttribute(JiraAttributeFactory.ATTRIBUTE_ISSUE_PARENT_KEY);
		}
		
		Subtask[] subtasks = jiraIssue.getSubtasks();
		if(subtasks!=null && subtasks.length>0) {
			data.removeAttribute(JiraAttributeFactory.ATTRIBUTE_SUBTASK_IDS);
			data.removeAttribute(JiraAttributeFactory.ATTRIBUTE_SUBTASK_KEYS);
			for (Subtask subtask : subtasks) {
				data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_SUBTASK_IDS, subtask.getIssueId());
				data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_SUBTASK_KEYS, subtask.getIssueKey());
			}
		}
		
		data.addAttributeValue(RepositoryTaskAttribute.DATE_CREATION, dateToString(jiraIssue.getCreated()));
		data.addAttributeValue(RepositoryTaskAttribute.SUMMARY, convertHtml(jiraIssue.getSummary()));
		data.addAttributeValue(RepositoryTaskAttribute.DESCRIPTION, convertHtml(jiraIssue.getDescription()));
		data.addAttributeValue(RepositoryTaskAttribute.STATUS, convertHtml(jiraIssue.getStatus().getName()));
		data.addAttributeValue(RepositoryTaskAttribute.TASK_KEY, jiraIssue.getKey());
		data.addAttributeValue(RepositoryTaskAttribute.RESOLUTION, //
				jiraIssue.getResolution() == null ? "" : jiraIssue.getResolution().getName());
		data.addAttributeValue(RepositoryTaskAttribute.DATE_MODIFIED, dateToString(jiraIssue.getUpdated()));

		data.addAttributeValue(RepositoryTaskAttribute.USER_ASSIGNED, getAssignee(jiraIssue));
		data.addAttributeValue(RepositoryTaskAttribute.USER_REPORTER, jiraIssue.getReporter());

		data.addAttributeValue(RepositoryTaskAttribute.PRODUCT, jiraIssue.getProject().getName());
		
		if (jiraIssue.getPriority() != null) {
			data.addAttributeValue(RepositoryTaskAttribute.PRIORITY, jiraIssue.getPriority().getName());
		}
		
		data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_TYPE, jiraIssue.getType().getName());
		
		data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_ESTIMATE, Long.toString(jiraIssue.getEstimate()));
		
		if (jiraIssue.getComponents() != null) {
			for (Component component : jiraIssue.getComponents()) {
				data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_COMPONENTS, component.getName());
			}
		} else {
			if(!data.getAttribute(JiraAttributeFactory.ATTRIBUTE_COMPONENTS).hasOptions()) {
				data.removeAttribute(JiraAttributeFactory.ATTRIBUTE_COMPONENTS);
			}
		}

		if (jiraIssue.getReportedVersions() != null) {
			for (Version version : jiraIssue.getReportedVersions()) {
				data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS, version.getName());
			}
		} else {
			if(!data.getAttribute(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS).hasOptions()) {
				data.removeAttribute(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS);
			}
		}

		if (jiraIssue.getFixVersions() != null) {
			for (Version version : jiraIssue.getFixVersions()) {
				data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS, version.getName());
			}
		} else {
			if(!data.getAttribute(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS).hasOptions()) {
				data.removeAttribute(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS);
			}
		}
		
		if(jiraIssue.getEnvironment()!=null) {
			data.addAttributeValue(JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT, convertHtml(jiraIssue.getEnvironment()));
		}

		int x = 1;
		for (Comment comment : jiraIssue.getComments()) {
			TaskComment taskComment = new TaskComment(attributeFactory, x++);

			// XXX ugly because AbstractRepositoryTaskEditor is using USER_OWNER instead of COMMENT_AUTHOR
			taskComment.addAttribute(RepositoryTaskAttribute.USER_OWNER, createAttribute(comment.getAuthor()));
			
			taskComment.addAttributeValue(RepositoryTaskAttribute.COMMENT_TEXT, comment.getComment());
			taskComment.addAttributeValue(RepositoryTaskAttribute.COMMENT_DATE, dateToString(comment.getCreated()));
			data.addComment(taskComment);
		}

		for (Attachment attachment : jiraIssue.getAttachments()) {
			RepositoryAttachment taskAttachment = new RepositoryAttachment(attributeFactory);
			taskAttachment.setCreator(attachment.getAuthor());
			taskAttachment.setRepositoryKind(JiraUiPlugin.REPOSITORY_KIND);
			taskAttachment.setRepositoryUrl(server.getBaseUrl());
			taskAttachment.setTaskId(jiraIssue.getKey());

			taskAttachment.setAttributeValue(RepositoryTaskAttribute.ATTACHMENT_ID, attachment.getId());
			taskAttachment.setAttributeValue(RepositoryTaskAttribute.ATTACHMENT_FILENAME, attachment.getName());
			
			if (JiraAttachmentHandler.CONTEXT_ATTACHEMNT_FILENAME.equals(attachment.getName())) {
				taskAttachment.setAttributeValue(RepositoryTaskAttribute.DESCRIPTION, AbstractRepositoryConnector.MYLAR_CONTEXT_DESCRIPTION);
			} else {
				taskAttachment.setAttributeValue(RepositoryTaskAttribute.DESCRIPTION, attachment.getName());
			}
			
			taskAttachment.setAttributeValue(RepositoryTaskAttribute.USER_OWNER, attachment.getAuthor());
			
			taskAttachment.setAttributeValue(RepositoryTaskAttribute.ATTACHMENT_DATE, dateToString(attachment.getCreated()));
			taskAttachment.setAttributeValue(RepositoryTaskAttribute.ATTACHMENT_URL, //
					server.getBaseUrl() + "/secure/attachment/" + attachment.getId() + "/" + attachment.getName());
			data.addAttachment(taskAttachment);
		}
		
		for (CustomField field : jiraIssue.getCustomFields()) {
			String type = field.getKey();
			RepositoryTaskAttribute attribute = attributeFactory.createAttribute(field.getId());
			attribute.putMetaDataValue(JiraAttributeFactory.TYPE_KEY, type);
			for (String value : field.getValues()) {
				if(JiraFieldType.TEXTAREA.getKey().equals(type)) {
					attribute.addValue(convertHtml(value));
				} else {
					attribute.addValue(value);
				}
			}
			data.addAttribute(attribute.getID(), attribute);
		}
		
		// TODO move into server configuration and populate lazily
		HashSet<String> editableKeys = new HashSet<String>();
		try {
			RepositoryTaskAttribute[] editableAttributes = server.getEditableAttributes(jiraIssue.getKey());
			if (editableAttributes != null) {
				// System.err.println(data.getTaskKey());
				for (RepositoryTaskAttribute attribute : editableAttributes) {
					// System.err.println("  " + attribute.getID() + " : " + attribute.getName());
					editableKeys.add(attributeFactory.mapCommonAttributeKey(attribute.getID()));
				}
			}
		} catch(JiraInsufficientPermissionException ex) {
			// ignore
		}
		
		for (RepositoryTaskAttribute attribute : data.getAttributes()) {
			boolean editable = editableKeys.contains(attribute.getID().toLowerCase());
			attribute.setReadOnly(!editable);
			if (editable && !attributeFactory.getIsHidden(attribute.getID())) {
				attribute.setHidden(false);
			}

			// make attributes read-only if can't find editing options
			String key = attribute.getMetaDataValue(JiraAttributeFactory.TYPE_KEY);
			Collection<String> options = attribute.getOptions();
			if (JiraFieldType.SELECT.getKey().equals(key)
					&& (options == null || options.isEmpty() || attribute.isReadOnly())) {
				attribute.setReadOnly(true);
			} else if (JiraFieldType.MULTISELECT.getKey().equals(key) && (options == null || options.isEmpty())) {
				attribute.setReadOnly(true);
			}
		}
	}

	private RepositoryTaskAttribute createAttribute(String value) {
		RepositoryTaskAttribute attr = attributeFactory.createAttribute(RepositoryTaskAttribute.COMMENT_AUTHOR);
		attr.setHidden(true);
		attr.setReadOnly(true);
		attr.setValue(value);
		return attr;
	}

	private String dateToString(Date date) {
		return new SimpleDateFormat(JiraAttributeFactory.JIRA_DATE_FORMAT, Locale.US).format(date);
	}

	private String getAssignee(Issue jiraIssue) {
		String assignee = jiraIssue.getAssignee();
		return assignee == null || JiraTask.UNASSIGNED_USER.equals(assignee) ? "" : assignee;
	}

	public static String convertHtml(String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		StringReader stringReader = new StringReader(text);
		HTML2TextReader html2TextReader = new HTML2TextReader(stringReader, null);
		try {
			char[] chars = new char[text.length()];
			int len = html2TextReader.read(chars, 0, text.length());
			if (len == -1) {
				return "";
			}
			return new String(chars, 0, len);
		} catch (IOException e) {
			return text;
		}
	}

	private void addOperations(RepositoryTaskData data, Issue issue, JiraClient server) throws JiraException {
		Status status = issue.getStatus();

		RepositoryOperation leaveOperation = new RepositoryOperation("leave", "Leave as " + issue.getStatus().getName());
		leaveOperation.setChecked(true);
		data.addOperation(leaveOperation);

		// TODO need more accurate status matching
		if (status.getId().equals(Status.OPEN_ID) || status.getId().equals(Status.STARTED_ID)) {
			RepositoryOperation reassignOperation = new RepositoryOperation("reassign", "Reassign to");
			reassignOperation.setInputName("assignee");
			reassignOperation.setInputValue(server.getUserName());
			data.addOperation(reassignOperation);
		}
		
		RepositoryOperation[] availableOperations = server.getAvailableOperations(issue.getKey());
		for (RepositoryOperation operation : availableOperations) {
			String[] fields = server.getActionFields(issue.getKey(), operation.getKnobName());
			// System.err.println(issue.getKey() + " : "+ operation.getKnobName() + " " + operation.getOperationName() + " : " + Arrays.asList(fields));
			for (String field : fields) {
				if(RepositoryTaskAttribute.RESOLUTION.equals(attributeFactory.mapCommonAttributeKey(field))) {
					operation.setUpOptions("resolve");
					addResolutions(server, operation);
				}
				// TODO handle other action fields
			}
			data.addOperation(operation);
		}
	}

	private void addResolutions(JiraClient server, RepositoryOperation operation) {
		Resolution[] resolutions = server.getResolutions();
		if (resolutions.length > 0) {
			for (Resolution resolution : resolutions) {
				operation.addOption(resolution.getName(), resolution.getId());
				if (Resolution.FIXED_ID.equals(resolution.getId())) {
					operation.setOptionSelection(resolution.getName());
				}
			}
		} else {
			operation.addOption("Fixed", Resolution.FIXED_ID);
			operation.addOption("Won't Fix", Resolution.WONT_FIX_ID);
			operation.addOption("Duplicate", Resolution.DUPLICATE_ID);
			operation.addOption("Incomplete", Resolution.INCOMPLETE_ID);
			operation.addOption("Cannot Reproduce", Resolution.CANNOT_REPRODUCE_ID);
			operation.setOptionSelection("Fixed");
		}
	}

	public String postTaskData(TaskRepository repository, RepositoryTaskData taskData, IProgressMonitor monitor) throws CoreException {
		final JiraClient jiraServer = JiraClientFacade.getDefault().getJiraClient(repository);
		if (jiraServer == null) {
			throw new CoreException(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.Status.ERROR,
					JiraCorePlugin.ID, org.eclipse.core.runtime.Status.ERROR, "Unable to produce Jira Server", null));
		}

		try {
			Issue issue = buildJiraIssue(taskData, jiraServer);
			if (taskData.isNew()) {
				issue = jiraServer.createIssue(issue);
				if (issue == null) {
					throw new CoreException(new org.eclipse.core.runtime.Status(IStatus.ERROR, JiraCorePlugin.ID,
							IStatus.OK, "Could not create ticket.", null));
				}
				// this is severly broken: should return id instead
				return issue.getKey();
			} else {
				RepositoryOperation operation = taskData.getSelectedOperation();
				if (operation == null) {
					jiraServer.updateIssue(issue, taskData.getNewComment());
				} else {
					if ("leave".equals(operation.getKnobName()) || "reassign".equals(operation.getKnobName())) {
						if (!issue.getStatus().isClosed()) {
							jiraServer.updateIssue(issue, taskData.getNewComment());
						} else if (taskData.getNewComment() != null && taskData.getNewComment().length() > 0) {
							jiraServer.addCommentToIssue(issue, taskData.getNewComment());
						}
					} else {

//						Map<String, String[]> params = new HashMap<String, String[]>();
//						String[] fields = jiraServer.getActionFields(issue.getKey(), action);
//						
//						for (String field : fields) {
//							RepositoryTaskAttribute attribute = taskData.getAttribute(attributeFactory.mapCommonAttributeKey(field));
//							if(attribute!=null) {
//								List<String> values = attribute.getValues();
//								if(values!=null) {
//									params.put(field, values.toArray(new String[values.size()]));
//								}
//							}
//						}
						
						jiraServer.advanceIssueWorkflow(issue, operation.getKnobName(), taskData.getNewComment());
					}
/*						
					if (org.eclipse.mylar.internal.jira.core.model.Status.RESOLVED_ID.equals(operation
							.getKnobName())) {
						String value = operation.getOptionValue(operation.getOptionSelection());
						jiraServer.resolveIssue(issue, jiraServer.getResolutionById(value), issue.getFixVersions(),
								taskData.getNewComment(), JiraClient.ASSIGNEE_CURRENT, repository.getUserName());
					} else if (org.eclipse.mylar.internal.jira.core.model.Status.REOPENED_ID.equals(operation
							.getKnobName())) {
						jiraServer.reopenIssue(issue, taskData.getNewComment(), JiraClient.ASSIGNEE_CURRENT, repository
								.getUserName());
					} else if (org.eclipse.mylar.internal.jira.core.model.Status.STARTED_ID.equals(operation
							.getKnobName())) {
						// FIXME update attributes and comment
						jiraServer.startIssue(issue);
					} else if (org.eclipse.mylar.internal.jira.core.model.Status.OPEN_ID
							.equals(operation.getKnobName())) {
						// FIXME update attributes and comment
						jiraServer.startIssue(issue);
					} else if (org.eclipse.mylar.internal.jira.core.model.Status.CLOSED_ID.equals(operation
							.getKnobName())) {
						String value = operation.getOptionValue(operation.getOptionSelection());
						jiraServer.closeIssue(issue, jiraServer.getResolutionById(value), issue.getFixVersions(),
								taskData.getNewComment(), JiraClient.ASSIGNEE_CURRENT, repository.getUserName());
					}
*/					
				}
				return "";
			}
		} catch (JiraException e) {
			throw new CoreException(JiraCorePlugin.toStatus(repository, e));
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
	
	public AbstractAttributeFactory getAttributeFactory(RepositoryTaskData taskData) {
		return getAttributeFactory(taskData.getRepositoryUrl(), taskData.getRepositoryKind(), taskData.getTaskKind());
	}

	private Issue buildJiraIssue(RepositoryTaskData taskData, JiraClient client) {
		Issue issue = new Issue();
		issue.setId(taskData.getId());
		issue.setKey(taskData.getTaskKey());
		issue.setSummary(taskData.getAttributeValue(RepositoryTaskAttribute.SUMMARY));
		issue.setDescription(taskData.getAttributeValue(RepositoryTaskAttribute.DESCRIPTION));

		for (org.eclipse.mylar.internal.jira.core.model.Project project : client.getProjects()) {
			if (project.getName().equals(taskData.getAttributeValue(RepositoryTaskAttribute.PRODUCT))) {
				issue.setProject(project);
				break;
			}
		}
		
		// issue.setEstimate(Long.parseLong(taskData.getAttributeValue(JiraAttributeFactory.ATTRIBUTE_ESTIMATE)));
	
		for (IssueType type : client.getIssueTypes()) {
			if (type.getName().equals(taskData.getAttributeValue(JiraAttributeFactory.ATTRIBUTE_TYPE))) {
				issue.setType(type);
				break;
			}
		}
		for (org.eclipse.mylar.internal.jira.core.model.Status status : client.getStatuses()) {
			if (status.getName().equals(taskData.getAttributeValue(RepositoryTaskAttribute.STATUS))) {
				issue.setStatus(status);
				break;
			}
		}
		RepositoryTaskAttribute componentsAttr = taskData.getAttribute(JiraAttributeFactory.ATTRIBUTE_COMPONENTS);
		if(componentsAttr!=null) {
			ArrayList<Component> components = new ArrayList<Component>();
			for (String compStr : taskData.getAttributeValues(JiraAttributeFactory.ATTRIBUTE_COMPONENTS)) {
				if (componentsAttr.getOptionParameter(compStr) != null) {
					Component comp = new Component();
					comp.setId(componentsAttr.getOptionParameter(compStr));
					comp.setName(compStr);
					components.add(comp);
				} else {
					MylarStatusHandler.fail(null, "Error setting component for JIRA issue. Component id is null: "
							+ compStr, false);
				}
			}
			issue.setComponents(components.toArray(new Component[components.size()]));
		}
	
		RepositoryTaskAttribute fixVersionAttr = taskData.getAttribute(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS);
		if(fixVersionAttr!=null) {
			ArrayList<Version> fixversions = new ArrayList<Version>();
			for (String fixStr : taskData.getAttributeValues(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS)) {
				if (fixVersionAttr.getOptionParameter(fixStr) != null) {
					Version version = new Version();
					version.setId(fixVersionAttr.getOptionParameter(fixStr));
					version.setName(fixStr);
					fixversions.add(version);
				} else {
					MylarStatusHandler.fail(null,
							"Error setting fix version for JIRA issue. Version id is null: " + fixStr, false);
				}
			}
			issue.setFixVersions(fixversions.toArray(new Version[fixversions.size()]));
		}
	
		RepositoryTaskAttribute affectsVersionAttr = taskData.getAttribute(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS);
		if (affectsVersionAttr != null) {
			ArrayList<Version> affectsversions = new ArrayList<Version>();
			for (String fixStr : taskData.getAttributeValues(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS)) {
				if (affectsVersionAttr.getOptionParameter(fixStr) != null) {
					Version version = new Version();
					version.setId(affectsVersionAttr.getOptionParameter(fixStr));
					version.setName(fixStr);
					affectsversions.add(version);
				} else {
					MylarStatusHandler.fail(null, "Error setting affects version for JIRA issue. Version id is null: "
							+ fixStr, false);
				}
			}
			issue.setReportedVersions(affectsversions.toArray(new Version[affectsversions.size()]));
		}
		issue.setReporter(taskData.getAttributeValue(RepositoryTaskAttribute.USER_REPORTER));
		
		RepositoryOperation operation = taskData.getSelectedOperation();
		String assignee;
		if (operation != null && "reassign".equals(operation.getKnobName())) {
			assignee = operation.getInputValue();
		} else {
			assignee = taskData.getAttributeValue(RepositoryTaskAttribute.USER_ASSIGNED);
		}
		issue.setAssignee(JiraRepositoryConnector.getAssigneeFromAttribute(assignee));
		
		issue.setEnvironment(taskData.getAttributeValue(JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT));
		for (Priority priority : client.getPriorities()) {
			if (priority.getName().equals(taskData.getAttributeValue(RepositoryTaskAttribute.PRIORITY))) {
				issue.setPriority(priority);
				break;
			}
		}
		
		ArrayList<CustomField> customFields = new ArrayList<CustomField>();
		for (RepositoryTaskAttribute attr : taskData.getAttributes()) {
			if (attr.getID().startsWith(JiraAttributeFactory.ATTRIBUTE_CUSTOM_PREFIX)) {
				String id = attr.getID().substring(JiraAttributeFactory.ATTRIBUTE_CUSTOM_PREFIX.length());
				customFields.add(new CustomField(id, attr.getMetaDataValue(JiraAttributeFactory.TYPE_KEY), attr.getName(), attr.getValues()));
			}
		}
		issue.setCustomFields(customFields.toArray(new CustomField[customFields.size()]));
		
		return issue;
	}
}