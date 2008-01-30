/*******************************************************************************
 * Copyright (c) 2004, 2007 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.ui;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.internal.jira.core.JiraCorePlugin;
import org.eclipse.mylyn.internal.jira.core.model.Issue;
import org.eclipse.mylyn.internal.jira.core.model.Priority;
import org.eclipse.mylyn.internal.jira.core.model.Project;
import org.eclipse.mylyn.internal.jira.core.model.Query;
import org.eclipse.mylyn.internal.jira.core.model.filter.DateRangeFilter;
import org.eclipse.mylyn.internal.jira.core.model.filter.FilterDefinition;
import org.eclipse.mylyn.internal.jira.core.model.filter.Order;
import org.eclipse.mylyn.internal.jira.core.model.filter.RelativeDateRangeFilter;
import org.eclipse.mylyn.internal.jira.core.model.filter.RelativeDateRangeFilter.RangeType;
import org.eclipse.mylyn.internal.jira.core.service.JiraClient;
import org.eclipse.mylyn.internal.jira.core.service.JiraException;
import org.eclipse.mylyn.tasks.core.AbstractAttachmentHandler;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryQuery;
import org.eclipse.mylyn.tasks.core.AbstractTask;
import org.eclipse.mylyn.tasks.core.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.ITaskCollector;
import org.eclipse.mylyn.tasks.core.QueryHitCollector;
import org.eclipse.mylyn.tasks.core.RepositoryTaskAttribute;
import org.eclipse.mylyn.tasks.core.RepositoryTaskData;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.AbstractTask.PriorityLevel;
import org.eclipse.mylyn.tasks.ui.TasksUiPlugin;

/**
 * @author Mik Kersten
 * @author Steffen Pingel
 * @author Eugene Kuleshov
 */
public class JiraRepositoryConnector extends AbstractRepositoryConnector {

	private static final String ERROR_REPOSITORY_CONFIGURATION = "The repository returned an unknown project. Please update the repository attributes.";

	private static final int MAX_MARK_STALE_QUERY_HITS = 500;

	private static final boolean TRACE_ENABLED = Boolean.valueOf(Platform.getDebugOption("org.eclipse.mylyn.internal.jira.ui/connector"));

	/** Repository address + Issue Prefix + Issue key = the issue's web address */
	public final static String ISSUE_URL_PREFIX = "/browse/";

	/** Repository address + Filter Prefix + Issue key = the filter's web address */
	public final static String FILTER_URL_PREFIX = "/secure/IssueNavigator.jspa?mode=hide";

	private JiraTaskDataHandler offlineHandler;

	private JiraAttachmentHandler attachmentHandler;

	private final TasksFacade mylynFacade;

	/** Name initially given to new tasks. Public for testing */
	public static final String NEW_TASK_DESC = "New Task";

	public static final int RETURN_ALL_HITS = -1;

	public JiraRepositoryConnector(TasksFacade mylynFacade) {
		this.mylynFacade = mylynFacade;

		offlineHandler = new JiraTaskDataHandler(JiraClientFactory.getDefault());
		attachmentHandler = new JiraAttachmentHandler();
	}

	public JiraRepositoryConnector() {
		this(new TasksFacade());
	}

	@Override
	public String getLabel() {
		return JiraUiPlugin.JIRA_CLIENT_LABEL;
	}

	@Override
	public String getConnectorKind() {
		return JiraUiPlugin.REPOSITORY_KIND;
	}

	@Override
	public AbstractAttachmentHandler getAttachmentHandler() {
		return attachmentHandler;
	}

	@Override
	public AbstractTaskDataHandler getTaskDataHandler() {
		return offlineHandler;
	}

	@Override
	public boolean canCreateTaskFromKey(TaskRepository repository) {
		return true;
	}

	@Override
	public IStatus performQuery(AbstractRepositoryQuery repositoryQuery, TaskRepository repository,
			IProgressMonitor monitor, ITaskCollector resultCollector) {
		monitor.beginTask("Running query", IProgressMonitor.UNKNOWN);
		try {
			JiraClient client = JiraClientFactory.getDefault().getJiraClient(repository);

			try {
				if (!client.hasDetails()) {
					client.refreshDetails(monitor);
				}
			} catch (JiraException e) {
				return JiraCorePlugin.toStatus(repository, e);
			}
			
			boolean isSearch = false;
			Query filter;
			if (repositoryQuery instanceof JiraRepositoryQuery) {
				filter = ((JiraRepositoryQuery) repositoryQuery).getNamedFilter();
			} else if (repositoryQuery instanceof JiraCustomQuery) {
				try {
					filter = ((JiraCustomQuery) repositoryQuery).getFilterDefinition(client, true);
					isSearch = ((JiraCustomQuery) repositoryQuery).isSearch();
				} catch (InvalidJiraQueryException e) {
					return new Status(IStatus.ERROR, JiraUiPlugin.PLUGIN_ID, 0,
							"The query parameters do not match the repository configuration, please check the query properties: "
									+ e.getMessage(), null);
				}
			} else {
				return new Status(IStatus.ERROR, JiraUiPlugin.PLUGIN_ID, 0, //
						"Invalid query type: " + repositoryQuery.getClass(), null);
			}

			try {
				List<Issue> issues = new ArrayList<Issue>();
				client.search(filter, new JiraIssueCollector(monitor, issues, QueryHitCollector.MAX_HITS));

				int n = 0;
				for (Issue issue : issues) {
					if (monitor.isCanceled()) {
						return Status.CANCEL_STATUS;
					}

					if (issue.getProject() == null) {
						return new Status(IStatus.ERROR, JiraUiPlugin.PLUGIN_ID, 0,
								ERROR_REPOSITORY_CONFIGURATION, null);						
					}
					
					monitor.subTask(++n + "/" + issues.size() + " " + issue.getKey() + " " + issue.getSummary());
					if (isSearch) {
						AbstractTask task = taskList.getTask(repository.getUrl(), issue.getId());
						if (!(task instanceof JiraTask)) {
							task = createTask(repository.getUrl(), issue.getId(), issue.getSummary());
							updateTaskFromIssue(repository.getUrl(), (JiraTask) task, issue);
						}
						// TODO we could update the task if it already exists in the task list
						resultCollector.accept(task);
					} else {
						RepositoryTaskData oldTaskData = mylynFacade.getNewTaskData(repository.getUrl(), issue.getId());
						resultCollector.accept(offlineHandler.createTaskData(repository, client, issue, oldTaskData));
					}
				}
				return Status.OK_STATUS;
			} catch (JiraException e) {
				IStatus status = JiraCorePlugin.toStatus(repository, e);
				trace(status);
				return status;
			} catch (CoreException e) {
				IStatus status = e.getStatus();
				trace(status);
				return status;
			}
		} finally {
			monitor.done();
		}
	}

	@Override
	public boolean markStaleTasks(TaskRepository repository, Set<AbstractTask> tasks, IProgressMonitor monitor)
			throws CoreException {
		Date now = new Date();
		FilterDefinition changedFilter = getSynchronizationFilter(repository, tasks, now);
		if (changedFilter == null) {
			// could not determine last time, rerun queries
			repository.setSynchronizationTimeStamp(JiraUtils.dateToString(now));
			return true;
		}

		List<Issue> issues = new ArrayList<Issue>();
		JiraClient client = JiraClientFactory.getDefault().getJiraClient(repository);
		// unlimited maxHits can create crazy amounts of traffic
		JiraIssueCollector issueCollector = new JiraIssueCollector(new NullProgressMonitor(), issues,
				MAX_MARK_STALE_QUERY_HITS);
		try {
			client.search(changedFilter, issueCollector);

			if (issues.isEmpty()) {
				// repository is unchanged
				repository.setSynchronizationTimeStamp(JiraUtils.dateToString(now));
				return false;
			}

			for (Issue issue : issues) {
				AbstractTask task = mylynFacade.getTask(repository.getUrl(), issue.getId());
				if (task != null) {
					if (issue.getProject() == null) {
						throw new CoreException(new Status(IStatus.ERROR, JiraUiPlugin.PLUGIN_ID, 0,
								ERROR_REPOSITORY_CONFIGURATION, null));						
					}
					
					// for JIRA sufficient information to create task data is returned by the query so no need to mark tasks as stale
					monitor.subTask(issue.getKey() + " " + issue.getSummary());
					RepositoryTaskData oldTaskData = mylynFacade.getNewTaskData(repository.getUrl(), issue.getId());
					RepositoryTaskData taskData = offlineHandler.createTaskData(repository, client, issue, oldTaskData);
					mylynFacade.saveIncoming(task, taskData);
					updateTaskFromTaskData(repository, task, taskData);
				}
			}

			repository.setSynchronizationTimeStamp(JiraUtils.dateToString(now));

			Date lastUpdate = issues.get(0).getUpdated();
			Date repositoryUpdateTimeStamp = JiraUtils.getLastUpdate(repository);
			if (repositoryUpdateTimeStamp != null && repositoryUpdateTimeStamp.equals(lastUpdate)) {
				// didn't see any new changes
				return false;
			} else {
				if (lastUpdate != null) {
					JiraUtils.setLastUpdate(repository, lastUpdate);
				}

				// updates may have caused tasks to match/not match a query therefore we need to rerun all queries  			
				return true;
			}
		} catch (JiraException e) {
			IStatus status = JiraCorePlugin.toStatus(repository, e);
			trace(status);
			throw new CoreException(status);
		}
	}

	/* Public for testing. */
	public FilterDefinition getSynchronizationFilter(TaskRepository repository, Set<AbstractTask> tasks, Date now) {
		// there are no JIRA tasks in the task list, skip contacting the repository
		if (tasks.isEmpty()) {
			return null;
		}

		Date lastSyncDate = JiraUtils.stringToDate(repository.getSynchronizationTimeStamp());

		// repository was never synchronized, update all tasks
		if (lastSyncDate == null) {
			for (AbstractTask task : tasks) {
				task.setStale(true);
			}
			return null;
		}

		// use local time to determine time difference to last sync  
		long nowTime = now.getTime();
		long lastSyncTime = lastSyncDate.getTime();

		// check if time stamp is skewed
		if (lastSyncTime >= nowTime) {
			trace(new Status(Status.WARNING, JiraUiPlugin.PLUGIN_ID, 0,
					"Synchronization time stamp clock skew detected for " + repository.getUrl() + ": " + lastSyncTime
							+ " >= " + now, null));

			// use the timestamp on the task that was modified last
			lastSyncDate = null;
			for (AbstractTask task : tasks) {
				Date date = JiraUtils.stringToDate(task.getLastReadTimeStamp());
				if (lastSyncDate == null || (date != null && date.after(lastSyncDate))) {
					lastSyncDate = date;
				}
			}

			if (lastSyncDate == null) {
				// could not determine last synchronization point
				return null;
			}

			// get all tasks that were changed after the last known task modification
			FilterDefinition changedFilter = new FilterDefinition("Changed Tasks");
			changedFilter.setUpdatedDateFilter(new DateRangeFilter(lastSyncDate, null));
			// make sure it's sorted so the most recent changes are returned in case the query maximum is hit
			changedFilter.setOrdering(new Order[] { new Order(Order.Field.UPDATED, false) });
			return changedFilter;
		}

		FilterDefinition changedFilter = new FilterDefinition("Changed Tasks");
		// need to use RelativeDateRangeFilter since the granularity of DateRangeFilter is days
		// whereas this allows us to use minutes 
		long minutes = (now.getTime() - lastSyncDate.getTime()) / (60 * 1000) + 1;
		changedFilter.setUpdatedDateFilter(new RelativeDateRangeFilter(RangeType.MINUTE, -minutes));
		// make sure it's sorted so the most recent changes are returned in case the query maximum is hit
		changedFilter.setOrdering(new Order[] { new Order(Order.Field.UPDATED, false) });
		return changedFilter;
	}

	@Override
	public String getSynchronizationTimestamp(TaskRepository repository, Set<AbstractTask> changedTasks) {
		// the timestamp is updated in markStaleTasks()
		return repository.getSynchronizationTimeStamp();
	}

	@Override
	public boolean canCreateNewTask(TaskRepository repository) {
		return true;
	}

	@Override
	public void updateTaskFromRepository(TaskRepository repository, AbstractTask repositoryTask,
			IProgressMonitor monitor) {
	}

	@Override
	public String getRepositoryUrlFromTaskUrl(String url) {
		if (url == null) {
			return null;
		}
		int index = url.indexOf(ISSUE_URL_PREFIX);
		return index == -1 ? null : url.substring(0, index);
	}

	@Override
	public String getTaskIdFromTaskUrl(String url) {
		if (url == null) {
			return null;
		}
		int index = url.indexOf(ISSUE_URL_PREFIX);
		return index == -1 ? null : url.substring(index + ISSUE_URL_PREFIX.length());
	}

	@Override
	public String getTaskUrl(String repositoryUrl, String taskId) {
		return repositoryUrl + ISSUE_URL_PREFIX + taskId;
	}

	@Override
	public String[] getTaskIdsFromComment(TaskRepository repository, String comment) {
		JiraClient client = JiraClientFactory.getDefault().getJiraClient(repository);
		Project[] projects = client.getProjects();
		if (projects != null && projects.length > 0) {
			// (?:(MNGECLIPSE-\d+?)|(SPR-\d+?))\D
			StringBuffer sb = new StringBuffer("(");
			String sep = "";
			for (Project project : projects) {
				sb.append(sep).append("(?:" + project.getKey() + "\\-\\d+?)");
				sep = "|";
			}
			sb.append(")(?:\\D|\\z)");

			Pattern p = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
			Matcher m = p.matcher(comment);
			if (m.find()) {
				HashSet<String> ids = new HashSet<String>();
				do {
					ids.add(m.group(1));
				} while (m.find());
				return ids.toArray(new String[ids.size()]);
			}
		}

		return super.getTaskIdsFromComment(repository, comment);
	}

	public static void updateTaskFromIssue(String repositoryUrl, JiraTask task, Issue issue) {
		if (issue.getKey() != null) {
			task.setTaskKey(issue.getKey());
			task.setUrl(getTaskUrlFromKey(repositoryUrl, issue.getKey()));
			if (issue.getDescription() != null) {
				task.setSummary(issue.getSummary());
			}
		}
		task.setCreationDate(issue.getCreated());
		if (isCompleted(issue.getStatus())) {
			task.setCompleted(true);
			task.setCompletionDate(issue.getUpdated());
		} else {
			task.setCompleted(false);
			task.setCompletionDate(null);
		}
		if (issue.getType() != null) {
			task.setTaskKind(issue.getType().getName());
		}
		task.setPriority(getPriorityLevel(issue.getPriority()).toString());
		task.setOwner(issue.getAssignee());
	}

	public static String getTaskUrlFromKey(String repositoryUrl, String key) {
		return repositoryUrl + JiraRepositoryConnector.ISSUE_URL_PREFIX + key;
	}

	private static boolean isCompleted(org.eclipse.mylyn.internal.jira.core.model.Status status) {
		return status != null && (status.isClosed() || status.isResolved());
	}

	@Override
	public AbstractTask createTask(String repositoryUrl, String id, String summary) {
		JiraTask jiraTask = new JiraTask(repositoryUrl, id, summary);
		jiraTask.setCreationDate(new Date());
		return jiraTask;
	}

	@Override
	public void updateTaskFromTaskData(TaskRepository repository, AbstractTask repositoryTask,
			RepositoryTaskData taskData) {
		if (repositoryTask instanceof JiraTask) {
			JiraTask jiraTask = (JiraTask) repositoryTask;

			// subtasks
//			repositoryTask.dropSubTasks();
//			for (String subId : getSubTaskIds(taskData)) {
//				ITask subTask = taskList.getTask(repository.getUrl(), subId);
//				if (subTask == null && retrieveSubTasks) {
//					if (!subId.trim().equals(taskData.getId()) && !subId.equals("")) {
//						try {
//							subTask = createTaskFromExistingId(repository, subId, false, new NullProgressMonitor());
//						} catch (CoreException e) {
//							// ignore
//						}
//					}
//				}
//				if (subTask != null) {
//					repositoryTask.addSubTask(subTask);
//				}
//			}

			jiraTask.setSummary(taskData.getAttributeValue(RepositoryTaskAttribute.SUMMARY));
			jiraTask.setOwner(taskData.getAttributeValue(RepositoryTaskAttribute.USER_ASSIGNED));
			jiraTask.setTaskKey(taskData.getAttributeValue(RepositoryTaskAttribute.TASK_KEY));
			jiraTask.setTaskKind(taskData.getAttributeValue(JiraAttributeFactory.ATTRIBUTE_TYPE));
			jiraTask.setUrl(getTaskUrlFromKey(repository.getUrl(), repositoryTask.getTaskKey()));
			jiraTask.setCreationDate(JiraUtils.stringToDate(taskData.getAttributeValue(RepositoryTaskAttribute.DATE_CREATION)));
			jiraTask.setDueDate(JiraUtils.stringToDate(taskData.getAttributeValue(JiraAttributeFactory.ATTRIBUTE_DUE_DATE)));

			JiraClient client = JiraClientFactory.getDefault().getJiraClient(repository);
			jiraTask.setPriority(getPriorityLevel(client, taskData.getAttributeValue(RepositoryTaskAttribute.PRIORITY)).toString());
			for (org.eclipse.mylyn.internal.jira.core.model.Status status : client.getStatuses()) {
				if (status.getName().equals(taskData.getAttributeValue(RepositoryTaskAttribute.STATUS))) {
					if (isCompleted(status)) {
						jiraTask.setCompleted(true);
						jiraTask.setCompletionDate(JiraUtils.stringToDate(taskData.getAttributeValue(RepositoryTaskAttribute.DATE_MODIFIED)));
					} else {
						jiraTask.setCompleted(false);
						jiraTask.setCompletionDate(null);
					}
					break;
				}
			}
		}
	}

	@Override
	public boolean updateTaskFromQueryHit(TaskRepository repository, AbstractTask existingTask, AbstractTask newTask) {
		// updating of tasks is done by TaskFacotory which invokes updateFromTaskData()
		return false;
	}

	private static PriorityLevel getPriorityLevel(JiraClient client, String jiraPriority) {
		if (jiraPriority != null) {
			for (Priority priority : client.getPriorities()) {
				if (jiraPriority.equals(priority.getName())) {
					return getPriorityLevel(priority);
				}
			}
		}
		return PriorityLevel.getDefault();
	}

	public static PriorityLevel getPriorityLevel(Priority jiraPriority) {
		if (jiraPriority != null) {
			String priorityId = jiraPriority.getId();
			if (Priority.BLOCKER_ID.equals(priorityId)) {
				return PriorityLevel.P1;
			} else if (Priority.CRITICAL_ID.equals(priorityId)) {
				return PriorityLevel.P2;
			} else if (Priority.MAJOR_ID.equals(priorityId)) {
				return PriorityLevel.P3;
			} else if (Priority.MINOR_ID.equals(priorityId)) {
				return PriorityLevel.P4;
			} else if (Priority.TRIVIAL_ID.equals(priorityId)) {
				return PriorityLevel.P5;
			}
		}
		return PriorityLevel.getDefault();
	}

	@Override
	public String toString() {
		return getLabel();
	}

	@Override
	public void updateAttributes(TaskRepository repository, IProgressMonitor monitor) throws CoreException {
		try {
			JiraClient client = JiraClientFactory.getDefault().getJiraClient(repository);
			client.refreshDetails(monitor);
		} catch (JiraException e) {
			IStatus status = JiraCorePlugin.toStatus(repository, e);
			trace(status);
			throw new CoreException(status);
		}
	}

	@Override
	public String getTaskIdPrefix() {
		return "issue";
	}

	public static String getAssigneeFromAttribute(String assignee) {
		return "".equals(assignee) ? JiraTask.UNASSIGNED_USER : assignee;
	}

	private void trace(IStatus status) {
		if (TRACE_ENABLED) {
			JiraUiPlugin.getDefault().getLog().log(status);
		}
	}

	@Override
	public boolean isRepositoryConfigurationStale(TaskRepository repository) throws CoreException {
		return JiraUtils.getAutoRefreshConfiguration(repository);
	}

	@Override
	public boolean hasCredentialsManagement() {
		return true;
	}
	
	public static class TasksFacade {

		public AbstractTask getTask(String repositoryUrl, String taskId) {
			return TasksUiPlugin.getTaskListManager().getTaskList().getTask(repositoryUrl, taskId);
		}

		@SuppressWarnings("restriction")
		public RepositoryTaskData getNewTaskData(String repositoryUrl, String id) {
			return TasksUiPlugin.getTaskDataManager().getNewTaskData(repositoryUrl, id);
		}

		public void saveIncoming(AbstractTask task, RepositoryTaskData taskData) {
			TasksUiPlugin.getSynchronizationManager().saveIncoming(task, taskData, false);
		}

	}

}