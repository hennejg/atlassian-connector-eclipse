/*******************************************************************************
 * Copyright (c) 2004 - 2006 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.core.service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.mylar.internal.jira.core.model.Group;
import org.eclipse.mylar.internal.jira.core.model.IssueType;
import org.eclipse.mylar.internal.jira.core.model.Priority;
import org.eclipse.mylar.internal.jira.core.model.Project;
import org.eclipse.mylar.internal.jira.core.model.Resolution;
import org.eclipse.mylar.internal.jira.core.model.ServerInfo;
import org.eclipse.mylar.internal.jira.core.model.Status;
import org.eclipse.mylar.internal.jira.core.model.User;

/**
 * Caches repository configuration data.
 * 
 * @author Steffen Pingel
 */
public class JiraServerData implements Serializable {

	private static final long serialVersionUID = 1L;

	Group[] groups = new Group[0];

	IssueType[] issueTypes = new IssueType[0];

	Map<String, IssueType> issueTypesById = new HashMap<String, IssueType>();

	Priority[] priorities = new Priority[0];

	Map<String, Priority> prioritiesById = new HashMap<String, Priority>();

	Project[] projects = new Project[0];

	Map<String, Project> projectsById = new HashMap<String, Project>();

	Map<String, Project> projectsByKey = new HashMap<String, Project>();

	Resolution[] resolutions = new Resolution[0];

	Map<String, Resolution> resolutionsById = new HashMap<String, Resolution>();

	volatile ServerInfo serverInfo;

	Status[] statuses = new Status[0];

	Map<String, Status> statusesById = new HashMap<String, Status>();

	User[] users = new User[0];

	Map<String, User> usersByName = new HashMap<String, User>();

	long lastUpdate;

}
