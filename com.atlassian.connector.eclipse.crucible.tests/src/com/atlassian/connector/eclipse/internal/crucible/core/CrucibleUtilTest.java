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

package com.atlassian.connector.eclipse.internal.crucible.core;

import com.atlassian.theplugin.commons.crucible.api.model.Action;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.CustomFilter;
import com.atlassian.theplugin.commons.crucible.api.model.CustomFilterBean;
import com.atlassian.theplugin.commons.crucible.api.model.GeneralComment;
import com.atlassian.theplugin.commons.crucible.api.model.GeneralCommentBean;
import com.atlassian.theplugin.commons.crucible.api.model.PermId;
import com.atlassian.theplugin.commons.crucible.api.model.PermIdBean;
import com.atlassian.theplugin.commons.crucible.api.model.PredefinedFilter;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.ReviewBean;
import com.atlassian.theplugin.commons.crucible.api.model.Reviewer;
import com.atlassian.theplugin.commons.crucible.api.model.ReviewerBean;
import com.atlassian.theplugin.commons.crucible.api.model.State;
import com.atlassian.theplugin.commons.crucible.api.model.UserBean;

import org.eclipse.mylyn.internal.tasks.core.RepositoryQuery;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * @author Shawn Minto
 */
public class CrucibleUtilTest extends TestCase {

	public void testGetPredefinedFilter() {
		for (PredefinedFilter filter : PredefinedFilter.values()) {
			PredefinedFilter predefinedFilter = CrucibleUtil.getPredefinedFilter(filter.getFilterUrl());
			assertNotNull(predefinedFilter);
			assertEquals(filter, predefinedFilter);
		}

		assertNull(CrucibleUtil.getPredefinedFilter("NON-EXISTANT-FILTER-URL"));
	}

	public void testGetPermIdFromTaskId() {
		String permId = "CR-5";
		String taskId = CrucibleUtil.getPermIdFromTaskId(permId);

		// ensure decoding somtehing that is decoded works
		assertEquals(permId, taskId);

		taskId = CrucibleUtil.getTaskIdFromPermId(permId);
		assertNotSame(permId, taskId);

		String newPermId = CrucibleUtil.getPermIdFromTaskId(permId);
		assertEquals(permId, newPermId);
	}

	public void testGetTaskIdFromPermId() {
		String permId = "CR-5";
		String expectedTaskId = "CR%2D_5";

		String taskId = CrucibleUtil.getTaskIdFromPermId(permId);
		assertEquals(expectedTaskId, taskId);

		taskId = CrucibleUtil.getTaskIdFromPermId(taskId);
		assertEquals(expectedTaskId, taskId);

		taskId = CrucibleUtil.getTaskIdFromPermId(permId.replace("-", ""));
		assertEquals(permId.replace("-", ""), taskId);
	}

	public void testGetPredefinedFilterWebUrl() {
		String repositoryUrl = "http://crucible.atlassian.com";
		String filterUrl = "test";
		String expectedFilterWebUrl = repositoryUrl + "/cru/?filter=" + filterUrl;

		String filterWebUrl = CrucibleUtil.getPredefinedFilterWebUrl(repositoryUrl + "/", filterUrl);
		assertEquals(expectedFilterWebUrl, filterWebUrl);

		filterWebUrl = CrucibleUtil.getPredefinedFilterWebUrl(repositoryUrl, filterUrl);
		assertEquals(expectedFilterWebUrl, filterWebUrl);
	}

	public void testAddTrailingSlash() {
		String expectedUrl = "http://crucible.atlassian.com";

		String url = CrucibleUtil.addTrailingSlash(expectedUrl);
		assertEquals(expectedUrl + "/", url);

		url = CrucibleUtil.addTrailingSlash(expectedUrl + "/");
		assertEquals(expectedUrl + "/", url);

		url = CrucibleUtil.addTrailingSlash(expectedUrl + "//");
		assertEquals(expectedUrl + "//", url);
	}

	public void testGetReviewUrl() {
		String repositoryUrl = "http://crucible.atlassian.com";
		String permId = "CR-5";
		String expectedReviewWebUrl = repositoryUrl + "/cru/" + permId;

		String taskId = CrucibleUtil.getTaskIdFromPermId(permId);

		String reviewWebUrl = CrucibleUtil.getReviewUrl(repositoryUrl + "/", taskId);
		assertEquals(expectedReviewWebUrl, reviewWebUrl);

		reviewWebUrl = CrucibleUtil.getReviewUrl(repositoryUrl, taskId);
		assertEquals(expectedReviewWebUrl, reviewWebUrl);

		reviewWebUrl = CrucibleUtil.getReviewUrl(repositoryUrl + "/", permId);
		assertEquals(expectedReviewWebUrl, reviewWebUrl);

	}

	public void testGetTaskIdFromUrl() {
		String permId = "CR-5";
		String reviewWebUrl = "http://crucible.atlassian.com/cru/" + permId;

		String expectedTaskId = CrucibleUtil.getTaskIdFromPermId(permId);

		String taskId = CrucibleUtil.getTaskIdFromUrl(reviewWebUrl);
		assertEquals(expectedTaskId, taskId);

		reviewWebUrl = "http://crucible.atlassian.com/" + permId;
		taskId = CrucibleUtil.getTaskIdFromUrl(reviewWebUrl);
		assertNull(taskId);

		reviewWebUrl = "http://crucible.atlassian.com/" + permId + "/test";
		taskId = CrucibleUtil.getTaskIdFromUrl(reviewWebUrl);
		assertNull(taskId);

		reviewWebUrl = "http://crucible.atlassian.com/cru/" + permId + "/test";
		taskId = CrucibleUtil.getTaskIdFromUrl(reviewWebUrl);
		assertNull(taskId);

		reviewWebUrl = "http://crucible.atlassian.com/cru/";
		taskId = CrucibleUtil.getTaskIdFromUrl(reviewWebUrl);
		assertNull(taskId);

		reviewWebUrl = "http://crucible.atlassian.com/cru//";
		taskId = CrucibleUtil.getTaskIdFromUrl(reviewWebUrl);
		assertNull(taskId);

	}

	public void testGetRepositoryUrlFromUrl() {
		String expectedRepositoryUrl = "http://crucible.atlassian.com/";
		String permId = "CR-5";
		String url = expectedRepositoryUrl + "cru/" + permId;

		String repositoryUrl = CrucibleUtil.getRepositoryUrlFromUrl(url);
		assertEquals(expectedRepositoryUrl, repositoryUrl);

		url = expectedRepositoryUrl + permId;
		repositoryUrl = CrucibleUtil.getRepositoryUrlFromUrl(url);
		assertNull(repositoryUrl);

		url = expectedRepositoryUrl + permId + "/test";
		repositoryUrl = CrucibleUtil.getRepositoryUrlFromUrl(url);
		assertNull(repositoryUrl);

		url = expectedRepositoryUrl + "cru/" + permId + "/test";
		repositoryUrl = CrucibleUtil.getRepositoryUrlFromUrl(url);
		assertEquals(expectedRepositoryUrl, repositoryUrl);

		url = expectedRepositoryUrl + "cru/";
		repositoryUrl = CrucibleUtil.getRepositoryUrlFromUrl(url);
		assertEquals(expectedRepositoryUrl, repositoryUrl);

		url = expectedRepositoryUrl + "cru//";
		repositoryUrl = CrucibleUtil.getRepositoryUrlFromUrl(url);
		assertEquals(expectedRepositoryUrl, repositoryUrl);
	}

	public void testIsFilterDefinition() {
		RepositoryQuery query = new RepositoryQuery(CrucibleCorePlugin.CONNECTOR_KIND, "crucible-test");
		assertTrue(CrucibleUtil.isFilterDefinition(query));

		query.setAttribute("test", "tst");
		assertTrue(CrucibleUtil.isFilterDefinition(query));

		query.setAttribute(CrucibleConstants.KEY_FILTER_ID, "");
		assertTrue(CrucibleUtil.isFilterDefinition(query));

		query.setAttribute(CrucibleConstants.KEY_FILTER_ID, "someFilter");
		assertFalse(CrucibleUtil.isFilterDefinition(query));
	}

	public void testGetStatesFromString() {
		char validSep = ',';
		char invalidSep = ';';
		StringBuilder builder = new StringBuilder();
		for (State state : State.values()) {
			builder.append(state.value());
			builder.append(validSep);
		}
		assertTrue(containEqualStates(State.values(), CrucibleUtil.getStatesFromString(builder.toString())));

		assertTrue(containEqualStates(new State[0],
				CrucibleUtil.getStatesFromString("thisisaninvalidstringnoseparator")));

		assertTrue(containEqualStates(new State[0], CrucibleUtil.getStatesFromString(State.CLOSED.value() + "invalid"
				+ State.ABANDONED.value() + "stringnoseparatorwithsomestatenamesinbetween" + State.DRAFT.value())));

		builder = new StringBuilder();
		for (State state : State.values()) {
			builder.append(state.value());
			builder.append(invalidSep);
		}
		assertTrue(containEqualStates(new State[0], CrucibleUtil.getStatesFromString(builder.toString())));

		builder = new StringBuilder();
		State[] states = State.values();
		for (int i = 0; i < states.length; i++) {
			builder.append(states[i].value());
			if (i % 2 == 0) {
				builder.append(invalidSep);
			} else {
				builder.append(validSep);
			}
		}
		assertTrue(containEqualStates(new State[0], CrucibleUtil.getStatesFromString(builder.toString())));

		builder = new StringBuilder();
		builder.append(State.ABANDONED.value());
		builder.append(validSep);
		builder.append(State.ABANDONED.value());
		builder.append(validSep);
		builder.append(State.ABANDONED.value());
		builder.append(invalidSep);
		builder.append(State.ABANDONED.value());
		builder.append(validSep);
		builder.append(State.ABANDONED.value());
		builder.append(invalidSep);
		builder.append(State.ABANDONED.value());
		builder.append(validSep);
		builder.append(State.ABANDONED.value());

		assertTrue(containEqualStates(new State[] { State.ABANDONED },
				CrucibleUtil.getStatesFromString(builder.toString())));

		builder = new StringBuilder();
		builder.append(State.ABANDONED.value());
		builder.append(validSep);
		builder.append(State.APPROVAL.value());
		builder.append(validSep);
		builder.append(State.CLOSED.value());
		builder.append(invalidSep);
		builder.append(State.DRAFT.value());
		builder.append(validSep);
		builder.append(State.REJECTED.value());
		builder.append(invalidSep);
		builder.append(State.REVIEW.value());
		builder.append(validSep);
		builder.append(State.ABANDONED.value());

		assertTrue(containEqualStates(new State[] { State.ABANDONED, State.APPROVAL },
				CrucibleUtil.getStatesFromString(builder.toString())));
	}

	private boolean containEqualStates(State[] expected, State[] actual) {
		if (expected == null || actual == null) {
			return false;
		}
		if (expected.length != actual.length) {
			return false;
		}
		ArrayList<State> expectedList = new ArrayList<State>();
		Collections.addAll(expectedList, expected);
		for (State state : actual) {
			if (expectedList.contains(state)) {
				expectedList.remove(state);
			} else {
				return false;
			}
		}
		return expectedList.size() == 0;
	}

	public void testCreateCustomFilterFromQuery() {
		CustomFilterBean filter = new CustomFilterBean();
		String urlPrefix = "https://sub.domain.tld/folder/?";
		Boolean allReviewersComplete = new Boolean(true);
		String author = "aut1";
		Boolean isComplete = new Boolean(true);
		String creator = "cre1";
		String moderator = "mod1";
		boolean isOrRoles = false;
		String projectKey = "pro1";
		String reviewer = "rev1";
		State[] states = new State[] { State.ABANDONED, State.CLOSED, State.SUMMARIZE };

		IRepositoryQuery query = new RepositoryQuery(CrucibleCorePlugin.CONNECTOR_KIND, "mock query");
		StringBuilder b = new StringBuilder();
		addQueryParam(CustomFilter.ALLCOMPLETE, allReviewersComplete.toString(), b, query);
		addQueryParam(CustomFilter.AUTHOR, author, b, query);
		addQueryParam(CustomFilter.COMPLETE, isComplete.toString(), b, query);
		addQueryParam(CustomFilter.CREATOR, creator, b, query);
		addQueryParam(CustomFilter.MODERATOR, moderator, b, query);
		addQueryParam(CustomFilter.ORROLES, String.valueOf(isOrRoles), b, query);
		addQueryParam(CustomFilter.PROJECT, projectKey, b, query);
		addQueryParam(CustomFilter.REVIEWER, reviewer, b, query);
		addQueryParam(CustomFilter.STATES, getStatesString(states), b, query);
		b.insert(0, urlPrefix);

		filter.setAllReviewersComplete(allReviewersComplete);
		filter.setAuthor(author);
		filter.setComplete(isComplete);
		filter.setCreator(creator);
		filter.setModerator(moderator);
		filter.setOrRoles(isOrRoles);
		filter.setProjectKey(projectKey);
		filter.setReviewer(reviewer);
		filter.setState(states);

		CustomFilter actualFilter = CrucibleUtil.createCustomFilterFromQuery(query);

		assertTrue(equalFilters(filter, actualFilter));

		filter.setAuthor(creator);

		assertFalse(equalFilters(filter, actualFilter));
	}

	private static void addQueryParam(String name, String value, StringBuilder builder, IRepositoryQuery query) {
		if (builder != null) {
			if (builder.length() > 0) {
				builder.append("&");
			}
			builder.append(name).append("=").append(value);
		}
		if (query != null) {
			query.setAttribute(name, value);
		}
	}

	private String getStatesString(State[] states) {
		StringBuilder b = new StringBuilder();
		for (State state : states) {
			b.append(state.value());
			b.append(',');
		}
		return b.toString();
	}

	private boolean equalFilters(CustomFilter expected, CustomFilter actual) {
		return expected.isAllReviewersComplete().equals(actual.isAllReviewersComplete())
				&& expected.getAuthor().equals(actual.getAuthor()) && expected.isComplete().equals(actual.isComplete())
				&& expected.getCreator().equals(actual.getCreator())
				&& expected.getModerator().equals(actual.getModerator()) && expected.isOrRoles() == actual.isOrRoles()
				&& expected.getProjectKey().equals(actual.getProjectKey())
				&& expected.getReviewer().equals(actual.getReviewer())
				&& containEqualStates(expected.getState(), actual.getState());
	}

	public void testCreateFilterWebUrl() {
		String urlPrefix = "https://sub.domain.tld/folder";
		Boolean allReviewersComplete = new Boolean(true);
		String author = "aut1";
		Boolean isComplete = new Boolean(true);
		String creator = "cre1";
		String moderator = "mod1";
		boolean isOrRoles = false;
		String projectKey = "pro1";
		String reviewer = "rev1";
		State[] states = new State[] { State.SUMMARIZE };

		IRepositoryQuery query = new RepositoryQuery(CrucibleCorePlugin.CONNECTOR_KIND, "mock query");
		StringBuilder b = new StringBuilder();
		addQueryParam(CustomFilter.AUTHOR, author, b, query);
		addQueryParam(CustomFilter.CREATOR, creator, b, query);
		addQueryParam(CustomFilter.MODERATOR, moderator, b, query);
		addQueryParam(CustomFilter.REVIEWER, reviewer, b, query);
		addQueryParam(CustomFilter.PROJECT, projectKey, b, query);
		addQueryParam(CustomFilter.STATES, getStatesString(states), null, query);
		for (State state : states) {
			addQueryParam("state", state.value(), b, null);
		}
		addQueryParam(CustomFilter.COMPLETE, isComplete.toString(), b, query);
		addQueryParam(CustomFilter.ORROLES, String.valueOf(isOrRoles), b, query);
		addQueryParam(CustomFilter.ALLCOMPLETE, allReviewersComplete.toString(), b, query);

		String actual = CrucibleUtil.createFilterWebUrl(urlPrefix, query);
		String expected = urlPrefix + "/" + CrucibleConstants.CUSTOM_FILER_START + "&" + b.toString();

		assertEquals(expected, actual);
	}

	public void testGetTaskIdFromReview() {
		Review review = new ReviewBean("http://crucible.atlassian.com/cru/");
		String permId = "CR-5";
		String expected = "CR%2D_5";
		PermId id = new PermIdBean(permId);
		review.setPermId(id);
		assertEquals(expected, CrucibleUtil.getTaskIdFromReview(review));
	}

	public void testIsPartialReview() {
		Review review = new ReviewBean("http://crucible.atlassian.com/cru/");
		assertTrue(CrucibleUtil.isPartialReview(review));
		Set<CrucibleFileInfo> files = new LinkedHashSet<CrucibleFileInfo>();
		review.setFiles(files);
		assertFalse(CrucibleUtil.isPartialReview(review));
	}

	public void testCreateHash() {
		Review review1 = new ReviewBean("http://crucible.atlassian.com/cru/");
		Set<Action> actions = new LinkedHashSet<Action>();
		actions.add(Action.ABANDON);
		actions.add(Action.APPROVE);
		review1.setActions(actions);
		review1.setAllowReviewerToJoin(true);
		review1.setAuthor(new UserBean("aut"));
		review1.setCloseDate(new Date(1L));
		review1.setCreateDate(new Date(1L));
		review1.setCreator(new UserBean("cre"));
		review1.setProjectKey("pro");
		review1.setDescription("des");
		Set<CrucibleFileInfo> files = new LinkedHashSet<CrucibleFileInfo>();
		review1.setFiles(files);
		List<GeneralComment> genC = new ArrayList<GeneralComment>();
		GeneralCommentBean genCBean = new GeneralCommentBean();
		genCBean.setCreateDate(new Date(2L));
		genC.add(genCBean);
		review1.setGeneralComments(genC);
		review1.setMetricsVersion(5);
		review1.setModerator(new UserBean("mod"));
		review1.setName("nam");
		review1.setPermId(new PermIdBean("per"));
		review1.setProjectKey("prj");
		review1.setRepoName("rep");
		Set<Reviewer> reviewers = new LinkedHashSet<Reviewer>();
		ReviewerBean reviewer = new ReviewerBean();
		reviewer.setUserName("use");
		reviewer.setCompleted(false);
		reviewers.add(reviewer);
		review1.setReviewers(reviewers);
		review1.setState(State.CLOSED);

		Review review = new ReviewBean("http://crucible.atlassian.com/cru/");
		actions = new LinkedHashSet<Action>();
		actions.add(Action.ABANDON);
		actions.add(Action.APPROVE);
		review.setActions(actions);
		review.setAllowReviewerToJoin(true);
		review.setAuthor(new UserBean("aut"));
		review.setCloseDate(new Date(1L));
		review.setCreateDate(new Date(1L));
		review.setCreator(new UserBean("cre"));
		review.setProjectKey("pro");
		review.setDescription("des");
		files = new LinkedHashSet<CrucibleFileInfo>();
		review.setFiles(files);
		genC = new ArrayList<GeneralComment>();
		genCBean = new GeneralCommentBean();
		genCBean.setCreateDate(new Date(2L));
		genC.add(genCBean);
		review.setGeneralComments(genC);
		review.setMetricsVersion(5);
		review.setModerator(new UserBean("mod"));
		review.setName("nam");
		review.setPermId(new PermIdBean("per"));
		review.setProjectKey("prj");
		review.setRepoName("rep");
		reviewers = new LinkedHashSet<Reviewer>();
		reviewer = new ReviewerBean();
		reviewer.setUserName("use");
		reviewer.setCompleted(false);
		reviewers.add(reviewer);
		review.setReviewers(reviewers);
		review.setState(State.CLOSED);

		//test for incomplete reviews
		assertTrue(-1 == CrucibleUtil.createHash(review));
		assertTrue(CrucibleUtil.createHash(review) == CrucibleUtil.createHash(review1));

		List<Action> transitions = new ArrayList<Action>();
		transitions.add(Action.CLOSE);
		review1.setTransitions(transitions);

		transitions = new ArrayList<Action>();
		transitions.add(Action.CLOSE);
		review.setTransitions(transitions);

		//test for same object
		assertTrue(CrucibleUtil.createHash(review) == CrucibleUtil.createHash(review));

		//test for equal reviews
		assertTrue(CrucibleUtil.createHash(review) == CrucibleUtil.createHash(review1));

		//test for unequal reviews
		review1.setAuthor(new UserBean("new"));
		assertTrue(CrucibleUtil.createHash(review) != CrucibleUtil.createHash(review1));
	}

	public void testCanAddCommentToReview() {

		Review review = new ReviewBean("http://crucible.atlassian.com/cru/");
		assertFalse(CrucibleUtil.canAddCommentToReview(review));

		Set<Action> actions = new LinkedHashSet<Action>();
		actions.add(Action.ABANDON);
		actions.add(Action.APPROVE);
		review.setActions(actions);

		assertFalse(CrucibleUtil.canAddCommentToReview(review));

		actions.add(Action.COMMENT);
		review.setActions(actions);
		assertTrue(CrucibleUtil.canAddCommentToReview(review));
	}

	public void testIsReviewComplete() {
		Review review = new ReviewBean("http://crucible.atlassian.com/cru/");
		review.setState(State.ABANDONED);
		assertTrue(CrucibleUtil.isCompleted(review));

		review.setState(State.APPROVAL);
		assertFalse(CrucibleUtil.isCompleted(review));

		review.setState(State.CLOSED);
		assertTrue(CrucibleUtil.isCompleted(review));

		review.setState(State.DEAD);
		assertTrue(CrucibleUtil.isCompleted(review));

		review.setState(State.DRAFT);
		assertFalse(CrucibleUtil.isCompleted(review));

		review.setState(State.REJECTED);
		assertTrue(CrucibleUtil.isCompleted(review));

		review.setState(State.REVIEW);
		assertFalse(CrucibleUtil.isCompleted(review));

		review.setState(State.SUMMARIZE);
		assertFalse(CrucibleUtil.isCompleted(review));

		review.setState(State.UNKNOWN);
		assertFalse(CrucibleUtil.isCompleted(review));
	}

	public void testIsReviewerComplete() {
		String repositoryUrl = "http://crucible.atlassian.com/cru/";
		String username = "username";
		String username2 = "username2";

		Review review = new ReviewBean(repositoryUrl);

		Set<Reviewer> reviewers = new HashSet<Reviewer>();
		review.setReviewers(reviewers);

		assertFalse(CrucibleUtil.isUserCompleted(username, review));

		ReviewerBean reviewer = new ReviewerBean();
		reviewer.setUserName(username);
		reviewers.add(reviewer);
		review.setReviewers(reviewers);

		assertFalse(CrucibleUtil.isUserCompleted(username, review));

		reviewer.setCompleted(true);

		assertTrue(CrucibleUtil.isUserCompleted(username, review));

		assertFalse(CrucibleUtil.isUserCompleted(username2, review));

		ReviewerBean reviewer2 = new ReviewerBean();
		reviewer2.setUserName(username2);
		reviewers.add(reviewer);
		reviewers.add(reviewer2);
		review.setReviewers(reviewers);

		assertTrue(CrucibleUtil.isUserCompleted(username, review));

		assertFalse(CrucibleUtil.isUserCompleted(username2, review));

	}
}
