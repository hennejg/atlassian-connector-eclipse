/*******************************************************************************
 * Copyright (c) 2004, 2007 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.jira.tests;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.mylyn.context.core.ContextCorePlugin;
import org.eclipse.mylyn.context.tests.support.TestUtil;
import org.eclipse.mylyn.context.tests.support.TestUtil.Credentials;
import org.eclipse.mylyn.context.tests.support.TestUtil.PrivilegeLevel;
import org.eclipse.mylyn.internal.jira.core.model.Issue;
import org.eclipse.mylyn.internal.jira.core.model.filter.DateFilter;
import org.eclipse.mylyn.internal.jira.core.model.filter.DateRangeFilter;
import org.eclipse.mylyn.internal.jira.core.model.filter.FilterDefinition;
import org.eclipse.mylyn.internal.jira.core.model.filter.RelativeDateRangeFilter;
import org.eclipse.mylyn.internal.jira.core.model.filter.RelativeDateRangeFilter.RangeType;
import org.eclipse.mylyn.internal.jira.core.service.JiraClient;
import org.eclipse.mylyn.internal.jira.ui.JiraClientFacade;
import org.eclipse.mylyn.internal.jira.ui.JiraCustomQuery;
import org.eclipse.mylyn.internal.jira.ui.JiraRepositoryConnector;
import org.eclipse.mylyn.internal.jira.ui.JiraTask;
import org.eclipse.mylyn.internal.jira.ui.JiraUiPlugin;
import org.eclipse.mylyn.internal.jira.ui.JiraUtils;
import org.eclipse.mylyn.internal.tasks.ui.wizards.EditRepositoryWizard;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryQuery;
import org.eclipse.mylyn.tasks.core.AbstractTask;
import org.eclipse.mylyn.tasks.core.ITaskCollector;
import org.eclipse.mylyn.tasks.core.QueryHitCollector;
import org.eclipse.mylyn.tasks.core.RepositoryAttachment;
import org.eclipse.mylyn.tasks.core.TaskList;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.TaskRepositoryManager;
import org.eclipse.mylyn.tasks.ui.TaskFactory;
import org.eclipse.mylyn.tasks.ui.TasksUiPlugin;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * @author Steffen Pingel
 * @author Eugene Kuleshov
 */
public class JiraRepositoryConnectorTest extends TestCase {

	private TaskRepository repository;

	private TaskRepositoryManager manager;

	private JiraRepositoryConnector connector;

	private JiraClient client;

	private TaskList taskList;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		manager = TasksUiPlugin.getRepositoryManager();
		manager.clearRepositories(TasksUiPlugin.getDefault().getRepositoriesFilePath());

		taskList = TasksUiPlugin.getTaskListManager().getTaskList();
		taskList.reset();

		JiraClientFacade.getDefault().clearClients();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	protected void init(String url) throws Exception {
		String kind = JiraUiPlugin.REPOSITORY_KIND;

		Credentials credentials = TestUtil.readCredentials(PrivilegeLevel.USER);
		repository = new TaskRepository(kind, url);
		repository.setAuthenticationCredentials(credentials.username, credentials.password);

		manager.addRepository(repository, TasksUiPlugin.getDefault().getRepositoriesFilePath());

		connector = (JiraRepositoryConnector) manager.getRepositoryConnector(kind);
		assertEquals(connector.getConnectorKind(), kind);

		TasksUiPlugin.getSynchronizationManager().setForceSyncExec(true);

		client = JiraClientFacade.getDefault().getJiraClient(repository);
	}

	public void testChangeTaskRepositorySettings() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);
		assertEquals(repository.getUserName(), repository.getUserName());

		EditRepositoryWizard wizard = new EditRepositoryWizard(repository);
		Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.create();

		wizard.getSettingsPage().setUserId("newuser");
		assertTrue(wizard.performFinish());

		client = JiraClientFacade.getDefault().getJiraClient(repository);
		assertEquals("newuser", client.getUserName());
	}

	public void testAttachContext() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);

		Issue issue = JiraTestUtils.createIssue(client, "testAttachContext");
		issue = client.createIssue(issue);

		AbstractTask task = connector.createTaskFromExistingId(repository, issue.getKey(), new NullProgressMonitor());
		assertEquals("testAttachContext", task.getSummary());

		File sourceContextFile = ContextCorePlugin.getContextManager().getFileForContext(task.getHandleIdentifier());
		JiraTestUtils.writeFile(sourceContextFile, "Mylyn".getBytes());
		sourceContextFile.deleteOnExit();

		assertTrue(connector.getAttachmentHandler().attachContext(repository, task, "", new NullProgressMonitor()));

		TasksUiPlugin.getSynchronizationManager().synchronize(connector, task, true, null);

		Set<RepositoryAttachment> contextAttachments = connector.getAttachmentHandler().getContextAttachments(
				repository, task);
		assertEquals(1, contextAttachments.size());

		RepositoryAttachment attachment = contextAttachments.iterator().next();
		assertTrue(connector.getAttachmentHandler().retrieveContext(repository, task, attachment,
				System.getProperty("java.io.tmpdir"), new NullProgressMonitor()));
	}

	public void testPerformQueryDueDateFilter() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);

		GregorianCalendar c = new GregorianCalendar();
		c.add(Calendar.MONTH, 1);
		Date fromDate = c.getTime();
		Date toDate = c.getTime();
		
		DateFilter dueDateFilter = new DateRangeFilter(fromDate, toDate);

		FilterDefinition filter = new FilterDefinition("test query");
		filter.setDueDateFilter(dueDateFilter);
		
		// AbstractRepositoryQuery query = new JiraCustomQuery("test query", queryUrl, repository.getUrl(), repository.getCharacterEncoding());
		AbstractRepositoryQuery query = new JiraCustomQuery(repository.getUrl(), filter, repository.getCharacterEncoding());

		TaskFactory taskFactory = new TaskFactory(repository, false, false);
		
		ITaskCollector collector1 = new QueryHitCollector(taskFactory);
		connector.performQuery(query, repository, new NullProgressMonitor(), collector1);

		Set<AbstractTask> tasks1 = collector1.getTasks();
		// assertEquals(-1, tasks.size());

		Issue issue = JiraTestUtils.createIssue(client, "testDueDateFilter");
		issue.setDue(fromDate);
		issue = client.createIssue(issue);
		assertNotNull(issue);
		
		ITaskCollector collector2 = new QueryHitCollector(taskFactory);
		connector.performQuery(query, repository, new NullProgressMonitor(), collector2);
		Set<AbstractTask> tasks2 = collector2.getTasks();
		
		assertEquals(tasks1.size() + 1, tasks2.size());
		
		for (AbstractTask task : tasks2) {
			assertNotNull(task.getOwner());
			assertTrue("Expected ", task.getOwner().length()>0);
		}
	}

	public void testMarkStaleNoTasks() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);

		repository.setSynchronizationTimeStamp(null);
		boolean changed = connector.markStaleTasks(repository, new HashSet<AbstractTask>(), new NullProgressMonitor());
		assertTrue(changed);
		assertNull(repository.getSynchronizationTimeStamp());
	}
	
	public void testMarkStaleOneTask() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);
		
		Issue issue = JiraTestUtils.createIssue(client, "testMarkStale");
		issue = client.createIssue(issue);

		Date start = new Date();
		repository.setSynchronizationTimeStamp(JiraUtils.dateToString(start));
		AbstractTask task = connector.createTaskFromExistingId(repository, issue.getKey(), false, new NullProgressMonitor());
		taskList.addTask(task);
		Thread.sleep(5); // make sure markStaleTasks() finds a difference 
		assertNull(JiraUtils.getLastUpdate(repository));
		
		boolean changed = connector.markStaleTasks(repository, new HashSet<AbstractTask>(), new NullProgressMonitor());
		assertTrue(changed);
		assertFalse(task.isStale());
		assertNotNull(repository.getSynchronizationTimeStamp());
		Date timestamp = JiraUtils.stringToDate(repository.getSynchronizationTimeStamp());
		assertTrue(timestamp.after(start));
		assertTrue(timestamp.before(new Date()));
		assertEquals(issue.getUpdated(), JiraUtils.getLastUpdate(repository));
		
		Thread.sleep(5); // make sure markStaleTasks() finds a difference
		
		changed = connector.markStaleTasks(repository, new HashSet<AbstractTask>(), new NullProgressMonitor());
		assertFalse(changed);
		assertNotNull(repository.getSynchronizationTimeStamp());
		assertFalse(task.isStale());
		assertFalse("Expected updated synchronization timestamp", JiraUtils.dateToString(timestamp).equals(repository.getSynchronizationTimeStamp()));
		assertEquals(issue.getUpdated(), JiraUtils.getLastUpdate(repository));
	}

	public void testMarkStaleRepositoryChanged() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);
		
		// create two issues, the first one is added to the task list
		Date start = new Date();
		Issue issue = JiraTestUtils.createIssue(client, "testMarkStale");
		issue = client.createIssue(issue);
		AbstractTask task = connector.createTaskFromExistingId(repository, issue.getKey(), false, new NullProgressMonitor());
		taskList.addTask(task);	
		Issue issue2 = JiraTestUtils.createIssue(client, "testMarkStale2");
		issue2 = client.createIssue(issue);		
		assertTrue(issue2.getUpdated().after(issue.getUpdated()));
		repository.setSynchronizationTimeStamp(JiraUtils.dateToString(start));
		
		boolean changed = connector.markStaleTasks(repository, new HashSet<AbstractTask>(), new NullProgressMonitor());
		assertTrue(changed);
		assertFalse("Expected updated synchronization timestamp", JiraUtils.dateToString(start).equals(repository.getSynchronizationTimeStamp()));
		assertEquals(issue2.getUpdated(), JiraUtils.getLastUpdate(repository));
	}

	public void testGetSynchronizationFilter() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);
		
		Date now = new Date();
		repository.setSynchronizationTimeStamp(JiraUtils.dateToString(now));
		FilterDefinition filter = connector.getSynchronizationFilter(repository, new HashSet<AbstractTask>(), now);
		assertNotNull(filter);
		assertTrue(filter.getUpdatedDateFilter() instanceof RelativeDateRangeFilter);
		RelativeDateRangeFilter dateFilter = (RelativeDateRangeFilter) filter.getUpdatedDateFilter();
		assertEquals(RangeType.MINUTE, dateFilter.getPreviousRangeType());
		assertEquals(-1, dateFilter.getPreviousCount());
		assertEquals(RangeType.NONE, dateFilter.getNextRangeType());
		assertEquals(0, dateFilter.getNextCount());

		
		repository.setSynchronizationTimeStamp(JiraUtils.dateToString(addSecondsToDate(now, -121)));
		filter = connector.getSynchronizationFilter(repository, new HashSet<AbstractTask>(), now);
		assertNotNull(filter);
		assertTrue(filter.getUpdatedDateFilter() instanceof RelativeDateRangeFilter);
		dateFilter = (RelativeDateRangeFilter) filter.getUpdatedDateFilter();
		assertEquals(RangeType.MINUTE, dateFilter.getPreviousRangeType());
		assertEquals(-3, dateFilter.getPreviousCount());
		assertEquals(RangeType.NONE, dateFilter.getNextRangeType());
		assertEquals(0, dateFilter.getNextCount());
	}

	public void testGetSynchronizationFilterTimeStampInTheFuture() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);
		
		Date now = new Date();
		repository.setSynchronizationTimeStamp(JiraUtils.dateToString(addSecondsToDate(now, 20)));
		FilterDefinition filter = connector.getSynchronizationFilter(repository, new HashSet<AbstractTask>(), now);
		assertEquals("Expected updated timestamp", JiraUtils.dateToString(now), repository.getSynchronizationTimeStamp());
		assertNull(filter);
	}
	
	public void testGetSynchronizationFilterTimeStampInTheFutureWithTask() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);
		
		Date now = new Date();
		HashSet<AbstractTask> tasks = new HashSet<AbstractTask>();
		JiraTask task = new JiraTask(repository.getUrl(), "1", "");
		task.setLastReadTimeStamp(JiraUtils.dateToString(now));
		tasks.add(task);
		
		repository.setSynchronizationTimeStamp(JiraUtils.dateToString(addSecondsToDate(new Date(), 121)));
		FilterDefinition filter = connector.getSynchronizationFilter(repository, tasks, now);
		assertNotNull(filter);
		assertTrue(filter.getUpdatedDateFilter() instanceof DateRangeFilter);
		DateRangeFilter dateFilter = (DateRangeFilter) filter.getUpdatedDateFilter();
		assertEquals(JiraUtils.stringToDate(task.getLastReadTimeStamp()), dateFilter.getFromDate());
		assertEquals(null, dateFilter.getToDate());
	}

	public void testCreateTask() throws Exception {
		init(JiraTestConstants.JIRA_39_URL);

		Issue issue = JiraTestUtils.createIssue(client, "testAttachContext");
		issue = client.createIssue(issue);

		AbstractTask task = connector.createTaskFromExistingId(repository, issue.getKey(), new NullProgressMonitor());
		assertEquals("testAttachContext", task.getSummary());
		assertEquals(null, task.getCompletionDate());
		assertFalse(task.isCompleted());
		assertEquals(issue.getCreated(), task.getCreationDate());
		
		client.advanceIssueWorkflow(issue, "close", "");
		issue = client.getIssueByKey(issue.getKey());
		assertEquals(issue.getUpdated(), task.getCompletionDate());
		assertTrue(task.isCompleted());
		assertEquals(issue.getCreated(), task.getCreationDate());
	}

	private Date addSecondsToDate(Date updated, int i) {
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(updated.getTime() + i * 1000);
		return cal.getTime();
	}

//	private void waitForRepositoryTimeSync(Date repositoryTime) throws InterruptedException {
//		Date now = new Date();
//		long diff = now.getTime() - repositoryTime.getTime();
//		if (diff > 10 * 1000) {
//			fail("Local time is too far ahead of repository time: " + now + " > " + repositoryTime);
//		} else if (diff < -10 * 1000) {
//			fail("Repository time is too far ahead of local time: " + repositoryTime + " > " + now);
//		} 
//		
//		if (diff < 0) {
//			// wait a little bit so local time can catch up
//			Thread.sleep(-diff);
//		}
//	}
//	

}
