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

package com.atlassian.connector.eclipse.internal.crucible.ui;

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleCorePlugin;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleUtil;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClientData;
import com.atlassian.connector.eclipse.team.ui.CrucibleFile;
import com.atlassian.theplugin.commons.VersionedVirtualFile;
import com.atlassian.theplugin.commons.crucible.api.model.BasicProject;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleAction;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.ExtendedCrucibleProject;
import com.atlassian.theplugin.commons.crucible.api.model.GeneralComment;
import com.atlassian.theplugin.commons.crucible.api.model.PermId;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.Reviewer;
import com.atlassian.theplugin.commons.crucible.api.model.User;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.internal.tasks.core.TaskRepositoryManager;
import org.eclipse.mylyn.internal.tasks.core.TaskTask;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.internal.tasks.ui.views.TaskListView;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.TasksUi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * @author Shawn Minto
 */
public class CrucibleUiUtilTest extends TestCase {

	@Override
	protected void setUp() throws Exception {
		resetTaskListAndRepositories();
	}

	public static void resetTaskListAndRepositories() throws Exception {
		TasksUiPlugin.getRepositoryManager().clearRepositories(TasksUiPlugin.getDefault().getRepositoriesFilePath());
		TasksUiPlugin.getDefault().getLocalTaskRepository();
		resetTaskList();
	}

	public static void resetTaskList() throws Exception {
		TasksUi.getTaskActivityManager().deactivateActiveTask();
		TasksUiPlugin.getTaskListExternalizationParticipant().resetTaskList();
		TaskListView view = TaskListView.getFromActivePerspective();
		if (view != null) {
			view.refresh();
		}
	}

	public void testGetCrucibleTaskRepositoryFromString() {

		String repositoryUrl = "https://testServer.com";
		String anotherRepositoryUrl = "http://testServer.com";

		assertNull(CrucibleUiUtil.getCrucibleTaskRepository((String) null));
		assertNull(CrucibleUiUtil.getCrucibleTaskRepository(repositoryUrl));
		assertNull(CrucibleUiUtil.getCrucibleTaskRepository(anotherRepositoryUrl));

		TaskRepository taskRepository = new TaskRepository(CrucibleCorePlugin.CONNECTOR_KIND, repositoryUrl);

		TasksUi.getRepositoryManager().addRepository(taskRepository);

		TaskRepository retrievedRepository = CrucibleUiUtil.getCrucibleTaskRepository(repositoryUrl);
		assertNotNull(retrievedRepository);
		assertEquals(taskRepository, retrievedRepository);

		assertNull(CrucibleUiUtil.getCrucibleTaskRepository(anotherRepositoryUrl));

		((TaskRepositoryManager) TasksUi.getRepositoryManager()).removeRepository(taskRepository,
				TasksUiPlugin.getDefault().getRepositoriesFilePath());

	}

	public void testGetCrucibleTaskFromId() {
		String taskKey = "test-2";
		String taskId = CrucibleUtil.getTaskIdFromPermId(taskKey);

		String taskKey2 = "test-3";
		String taskId2 = CrucibleUtil.getTaskIdFromPermId(taskKey2);

		assertNull(CrucibleUiUtil.getCrucibleTask(null, null));
		assertNull(CrucibleUiUtil.getCrucibleTask(null, taskId));

		String repositoryUrl = "https://testServer.com";

		assertNull(CrucibleUiUtil.getCrucibleTaskRepository(repositoryUrl));

		TaskRepository taskRepository = new TaskRepository(CrucibleCorePlugin.CONNECTOR_KIND, repositoryUrl);
		TasksUi.getRepositoryManager().addRepository(taskRepository);

		assertNull(CrucibleUiUtil.getCrucibleTask(taskRepository, taskId));

		ITask createdTask = TasksUi.getRepositoryModel().createTask(taskRepository, taskId);
		ITask task = CrucibleUiUtil.getCrucibleTask(taskRepository, taskId);
		assertNotNull(task);
		assertEquals(createdTask, task);

		assertNull(CrucibleUiUtil.getCrucibleTask(taskRepository, taskId2));

		TasksUiPlugin.getTaskList().deleteTask(createdTask);
		((TaskRepositoryManager) TasksUi.getRepositoryManager()).removeRepository(taskRepository,
				TasksUiPlugin.getDefault().getRepositoriesFilePath());
	}

	public void testGetCrucibleTaskRepositoryFromReview() {

		String repositoryUrl = "https://testServer.com";

		Review review = prepareReview(repositoryUrl);

		assertNull(CrucibleUiUtil.getCrucibleTaskRepository((Review) null));
		assertNull(CrucibleUiUtil.getCrucibleTaskRepository(review));

		TaskRepository taskRepository = new TaskRepository(CrucibleCorePlugin.CONNECTOR_KIND, repositoryUrl);

		TasksUi.getRepositoryManager().addRepository(taskRepository);

		TaskRepository reviewTaskRepository = CrucibleUiUtil.getCrucibleTaskRepository(review);
		assertNotNull(reviewTaskRepository);
		assertEquals(taskRepository, reviewTaskRepository);

		((TaskRepositoryManager) TasksUi.getRepositoryManager()).removeRepository(taskRepository,
				TasksUiPlugin.getDefault().getRepositoriesFilePath());
	}

	public void testGetCrucibleTaskFromReview() {

		String taskKey = "test-3";
		String taskId = CrucibleUtil.getTaskIdFromPermId(taskKey);

		String repositoryUrl = "https://testServer.com";

		Review review = prepareReview(repositoryUrl);
		review.setPermId(new PermId(taskKey));

		assertNull(CrucibleUiUtil.getCrucibleTask(null));
		assertNull(CrucibleUiUtil.getCrucibleTask(review));

		assertNull(CrucibleUiUtil.getCrucibleTaskRepository(repositoryUrl));

		TaskRepository taskRepository = new TaskRepository(CrucibleCorePlugin.CONNECTOR_KIND, repositoryUrl);
		TasksUi.getRepositoryManager().addRepository(taskRepository);

		assertNull(CrucibleUiUtil.getCrucibleTask(review));

		ITask createdTask = TasksUi.getRepositoryModel().createTask(taskRepository, taskId);
		ITask task = CrucibleUiUtil.getCrucibleTask(review);
		assertNotNull(task);
		assertEquals(createdTask, task);

		TasksUiPlugin.getTaskList().deleteTask(createdTask);
		((TaskRepositoryManager) TasksUi.getRepositoryManager()).removeRepository(taskRepository,
				TasksUiPlugin.getDefault().getRepositoriesFilePath());

	}

	public void testIsFilePartOfActiveReview() {
		String repositoryUrl = "http://crucible.atlassian.com/cru/";

		Review reviewA = prepareReview(repositoryUrl);
		Review reviewB = prepareReview(repositoryUrl);

		ITask task = new TaskTask("kind", "url", "A");

		CrucibleFile file1 = new CrucibleFile(new CrucibleFileInfo(new VersionedVirtualFile("abc", "123"), null,
				new PermId("1")), false);
		CrucibleFile file2 = new CrucibleFile(new CrucibleFileInfo(new VersionedVirtualFile("def", "456"), null,
				new PermId("2")), false);
		CrucibleFile file3 = new CrucibleFile(new CrucibleFileInfo(new VersionedVirtualFile("ghi", "789"), null,
				new PermId("3")), false);
		CrucibleFile file4 = new CrucibleFile(new CrucibleFileInfo(new VersionedVirtualFile(null, null), null,
				new PermId("4")), false);
		CrucibleFile file5 = new CrucibleFile(new CrucibleFileInfo(null, null, new PermId("5")), false);

		Set<CrucibleFileInfo> setA = new HashSet<CrucibleFileInfo>();
		setA.add(file1.getCrucibleFileInfo());
		setA.add(file2.getCrucibleFileInfo());
		Set<CrucibleFileInfo> setB = new HashSet<CrucibleFileInfo>();
		setB.add(file2.getCrucibleFileInfo());
		setB.add(file3.getCrucibleFileInfo());
		reviewA.setFiles(setA);
		reviewB.setFiles(setB);

		CrucibleUiPlugin.getDefault().getActiveReviewManager().setActiveReview(reviewA, task);

		assertTrue(CrucibleUiUtil.isFilePartOfActiveReview(file1));
		assertTrue(CrucibleUiUtil.isFilePartOfActiveReview(file2));
		assertFalse(CrucibleUiUtil.isFilePartOfActiveReview(file3));

		CrucibleUiPlugin.getDefault().getActiveReviewManager().setActiveReview(reviewB, task);

		assertFalse(CrucibleUiUtil.isFilePartOfActiveReview(file1));
		assertTrue(CrucibleUiUtil.isFilePartOfActiveReview(file2));
		assertTrue(CrucibleUiUtil.isFilePartOfActiveReview(file3));

		assertFalse(CrucibleUiUtil.isFilePartOfActiveReview(file4));
		assertFalse(CrucibleUiUtil.isFilePartOfActiveReview(file5));
	}

	public void testGetCachedUsersReview() {
		Review review = createMockReview(createMockRepository());

		final CrucibleClientData clientData = CrucibleCorePlugin.getRepositoryConnector()
				.getClientManager()
				.getCrucibleClientData(CrucibleUiUtil.getCrucibleTaskRepository(review));

		assertNotNull(clientData);

		List<User> users = new ArrayList<User>();
		users.add(new User("uA", "userA"));
		users.add(new User("uB", "userB"));
		users.add(new User("uC", "userC"));
		clientData.setUsers(users);

		Set<User> usersReceivedSet = CrucibleUiUtil.getCachedUsers(review);

		Set<User> usersExpectedSet = new HashSet<User>(users);
		assertEquals(usersExpectedSet.size(), usersReceivedSet.size());

		for (User cachedUser : usersReceivedSet) {
			for (User user : users) {
				if (user.getUsername().equals(cachedUser.getUsername())) {
					assertEquals(user.getDisplayName(), cachedUser.getDisplayName());
					assertTrue("Expected user " + user.getUsername() + " not found in set",
							usersExpectedSet.remove(user));
				}
			}
		}
		assertEquals(0, usersExpectedSet.size());
	}

	private Review createMockReview(TaskRepository repo) {
		TasksUi.getRepositoryManager().addRepository(repo);
		Review review = new Review(repo.getRepositoryUrl(), "prj-key", new User("anauthor"), null);
		return review;
	}

	private TaskRepository createMockRepository() {
		TaskRepository repo = new TaskRepository(CrucibleCorePlugin.CONNECTOR_KIND, "http://crucible.atlassian.com");
		repo.setCredentials(AuthenticationType.REPOSITORY, new AuthenticationCredentials("user", "pass"), false);
		return repo;
	}

	public void testGetCurrentUsernameReview() {
		Review review = createMockReview(createMockRepository());

		assertEquals("user", CrucibleUiUtil.getCurrentUsername(review));
	}

	public void testGetCurrentUsernameRepository() {
		TaskRepository repository = createMockRepository();

		assertEquals("user", CrucibleUiUtil.getCurrentUsername(repository));
	}

	public void testGetCurrentCachedUserRepository() {
		TaskRepository repo = createMockRepository();
		createMockReview(repo);

		final CrucibleClientData clientData = CrucibleCorePlugin.getRepositoryConnector()
				.getClientManager()
				.getCrucibleClientData(repo);
		assertNotNull(clientData);

		User userA = new User("userA", "a");
		User userB = new User("user", "u");
		List<User> users = new ArrayList<User>();
		users.add(userA);
		users.add(userB);
		clientData.setUsers(users);

		assertFalse(new User("userA", "a").equals(CrucibleUiUtil.getCurrentCachedUser(repo)));
		assertFalse(userA.equals(CrucibleUiUtil.getCurrentCachedUser(repo)));
		assertEquals(new User("user", "u"), CrucibleUiUtil.getCurrentCachedUser(repo));
		assertEquals(userB, CrucibleUiUtil.getCurrentCachedUser(repo));
	}

	public void testGetCurrentCachedUserReview() {
		TaskRepository repo = createMockRepository();
		Review review = createMockReview(repo);

		final CrucibleClientData clientData = CrucibleCorePlugin.getRepositoryConnector()
				.getClientManager()
				.getCrucibleClientData(repo);
		assertNotNull(clientData);

		User userA = new User("userA", "a");
		User userB = new User("user", "u");
		List<User> users = new ArrayList<User>();
		users.add(userA);
		users.add(userB);
		clientData.setUsers(users);

		assertFalse(new User("userA", "a").equals(CrucibleUiUtil.getCurrentCachedUser(review)));
		assertFalse(userA.equals(CrucibleUiUtil.getCurrentCachedUser(review)));
		assertEquals(new User("user", "u"), CrucibleUiUtil.getCurrentCachedUser(review));
		assertEquals(userB, CrucibleUiUtil.getCurrentCachedUser(review));
	}

	public void testGetCachedUser() {
		TaskRepository repo = createMockRepository();
		createMockReview(repo);
		CrucibleClientData clientData = CrucibleCorePlugin.getRepositoryConnector()
				.getClientManager()
				.getCrucibleClientData(repo);
		User userA = new User("userA", "a");
		User userB = new User("userB", "b");
		List<User> users = new ArrayList<User>();
		users.add(userA);
		users.add(userB);
		clientData.setUsers(users);

		assertEquals(new User("userA", "a"), CrucibleUiUtil.getCachedUser("userA", repo));
		assertEquals(userA, CrucibleUiUtil.getCachedUser("userA", repo));
		assertEquals(new User("userB", "b"), CrucibleUiUtil.getCachedUser("userB", repo));
		assertEquals(userB, CrucibleUiUtil.getCachedUser("userB", repo));
	}

	public void testGetCachedUsersRepository() {
		TaskRepository repository = createMockRepository();
		Review review = createMockReview(repository);

		CrucibleClientData clientData = CrucibleCorePlugin.getRepositoryConnector()
				.getClientManager()
				.getCrucibleClientData(CrucibleUiUtil.getCrucibleTaskRepository(review));

		assertNotNull(clientData);

		List<User> users = new ArrayList<User>();
		users.add(new User("userA", "uA"));
		users.add(new User("userB", "uB"));
		users.add(new User("userC", "uC"));
		clientData.setUsers(users);

		Set<User> usersReceivedSet = CrucibleUiUtil.getCachedUsers(repository);

		Set<User> usersExpectedSet = new HashSet<User>(users);
		assertEquals(usersExpectedSet.size(), usersReceivedSet.size());

		for (User cachedUser : usersReceivedSet) {
			for (User user : users) {
				if (user.getUsername().equals(cachedUser.getUsername())) {
					assertEquals(user.getDisplayName(), cachedUser.getDisplayName());
					assertTrue("Expected user " + user.getUsername() + " not found in set",
							usersExpectedSet.remove(user));
				}
			}
		}
		assertEquals(0, usersExpectedSet.size());
	}

	public void testGetCachedProjects() {

		TaskRepository repository = createMockRepository();
		Review review = createMockReview(repository);

		final CrucibleClientData clientData = CrucibleCorePlugin.getRepositoryConnector()
				.getClientManager()
				.getCrucibleClientData(CrucibleUiUtil.getCrucibleTaskRepository(review));
		assertNotNull(clientData);

		List<BasicProject> projects = new ArrayList<BasicProject>();
		BasicProject projectA = new BasicProject("a", "AA", "AaA");
		BasicProject projectB = new BasicProject("b", "BB", "BbB");
		projects.add(projectA);
		projects.add(projectB);
		clientData.setProjects(projects);

		Collection<BasicProject> usersReceivedSet = CrucibleUiUtil.getCachedProjects(repository);

		assertEquals(2, usersReceivedSet.size());

		BasicProject cachedProjectA = new BasicProject("a", "AA", "AaA");
		BasicProject cachedProjectB = new BasicProject("b", "BB", "BbB");
		assertTrue(usersReceivedSet.contains(cachedProjectA));
		assertTrue(usersReceivedSet.contains(cachedProjectB));
		assertEquals(BasicProject.class, clientData.getCrucibleProject("BB").getClass());
		assertEquals(BasicProject.class, clientData.getCrucibleProject("AA").getClass());

		clientData.updateProject(new ExtendedCrucibleProject("b", "BB", "My extended Project", MiscUtil.buildArrayList(
				"userA", "userB")));
		assertEquals(ExtendedCrucibleProject.class, clientData.getCrucibleProject("BB").getClass());
		assertEquals(BasicProject.class, clientData.getCrucibleProject("AA").getClass());
	}

	public void testGetUsernamesFromUsers() {
		List<User> users = new ArrayList<User>();
		users.add(new User("uA", "userA"));
		users.add(new User("uB", "userB"));
		users.add(new User("uC", "userC"));
		users.add(new User("uC", "userC"));
		users.add(new User("uD", "userD"));

		Set<String> userNames = CrucibleUiUtil.getUsernamesFromUsers(users);
		assertEquals(4, userNames.size());
		assertTrue(userNames.contains("uA"));
		assertTrue(userNames.contains("uB"));
		assertTrue(userNames.contains("uC"));
		assertTrue(userNames.contains("uD"));
	}

	public void testHasCurrentUserCompletedReview() {
		Review review = createMockReview(createMockRepository());
		review.setReviewers(Collections.singleton(new Reviewer("user", true)));

		assertTrue(CrucibleUiUtil.hasCurrentUserCompletedReview(review));
	}

	public void testIsUserReviewer() {
		Review review = createMockReview(createMockRepository());
		review.setReviewers(Collections.singleton((Reviewer) new Reviewer("user", true)));

		assertTrue(CrucibleUiUtil.isUserReviewer("user", review));
	}

	public void testIsCurrentUserReviewer() {
		Review review = createMockReview(createMockRepository());
		review.setReviewers(Collections.singleton((Reviewer) new Reviewer("user", true)));

		assertTrue(CrucibleUiUtil.isCurrentUserReviewer(review));
	}

	public void testCanModifyComment() {
		TaskRepository repo = createMockRepository();
		TasksUi.getRepositoryManager().addRepository(repo);

		Review review = prepareReview(repo.getUrl());
		review.setActions(MiscUtil.buildHashSet(CrucibleAction.COMMENT));
		GeneralComment comment = new GeneralComment(review, null);
		final String connUserName = repo.getCredentials(AuthenticationType.REPOSITORY).getUserName();
		User me = new User("not" + connUserName);
		comment.setAuthor(me);
		assertFalse(CrucibleUiUtil.canModifyComment(review, comment));
		comment.setAuthor(new User(connUserName));
		assertTrue(CrucibleUiUtil.canModifyComment(review, comment));
		review.setActions(MiscUtil.buildHashSet(CrucibleAction.VIEW, CrucibleAction.COMMENT, CrucibleAction.CLOSE));
		assertTrue(CrucibleUiUtil.canModifyComment(review, comment));
		review.setActions(MiscUtil.buildHashSet(CrucibleAction.VIEW, CrucibleAction.CLOSE));
		assertFalse(CrucibleUiUtil.canModifyComment(review, comment));
	}

	private Review prepareReview(String url) {
		return new Review(url, "prkey", new User("myauthor"), new User("mymoderator"));
	}

}
