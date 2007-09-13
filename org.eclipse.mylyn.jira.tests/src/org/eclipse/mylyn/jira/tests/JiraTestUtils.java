/*******************************************************************************
 * Copyright (c) 2004, 2007 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.jira.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import junit.framework.AssertionFailedError;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylyn.internal.jira.core.model.CustomField;
import org.eclipse.mylyn.internal.jira.core.model.Issue;
import org.eclipse.mylyn.internal.jira.core.model.Project;
import org.eclipse.mylyn.internal.jira.core.model.Resolution;
import org.eclipse.mylyn.internal.jira.core.service.JiraClient;
import org.eclipse.mylyn.internal.jira.core.service.JiraException;
import org.eclipse.mylyn.tasks.core.RepositoryOperation;

public class JiraTestUtils {

	public static String PROJECT1 = "PRONE";

	public static Resolution getFixedResolution(JiraClient server) throws JiraException {
		refreshDetails(server);

		Resolution[] resolutions = server.getResolutions();
		for (Resolution resolution : resolutions) {
			if (Resolution.FIXED_ID.equals(resolution.getId())) {
				return resolution;
			}
		}
		return resolutions[0];
	}

	public static String getOperation(JiraClient server, String issueKey, String name) throws JiraException {
		refreshDetails(server);

		ArrayList<String> names = new ArrayList<String>();
		RepositoryOperation[] operations = server.getAvailableOperations(issueKey);
		for (RepositoryOperation operation : operations) {
			names.add(operation.getOperationName());
			if (operation.getOperationName().toLowerCase().startsWith(name)) {
				return operation.getKnobName();
			}
		}

		throw new AssertionFailedError("Unable to find operation " + name + " in " + names);
	}

	public static String getCustomField(JiraClient server, String name) throws JiraException {
		refreshDetails(server);

		CustomField[] fields = server.getCustomAttributes();
		for (CustomField field : fields) {
			if (field.getName().toLowerCase().startsWith(name.toLowerCase())) {
				return field.getId();
			}
		}
		return null;
	}

	public static Issue createIssue(JiraClient client, String summary) throws JiraException {
		refreshDetails(client);

		Issue issue = new Issue();
		issue.setProject(getProject(client, PROJECT1));
		issue.setType(client.getIssueTypes()[0]);
		issue.setSummary(summary);
		issue.setAssignee(client.getUserName());

		return issue;
	}

	public static void refreshDetails(JiraClient client) throws JiraException {
		if (!client.hasDetails()) {
			client.refreshDetails(new NullProgressMonitor());
		}
	}

	public static Project getProject(JiraClient client, String projectKey) {
		Project project = client.getProjectByKey(projectKey);
		if (project == null) {
			throw new AssertionFailedError("Project '" + projectKey + "' not found");
		}
		return project;
	}

	public static byte[] readFile(File file) throws IOException {
		if (file.length() > 10000000) {
			throw new IOException("File too big: " + file.getAbsolutePath() + ", size: " + file.length());
		}

		byte[] data = new byte[(int) file.length()];
		InputStream in = new FileInputStream(file);
		try {
			in.read(data);
		} finally {
			in.close();
		}
		return data;
	}

	public static void writeFile(File file, byte[] data) throws IOException {
		OutputStream out = new FileOutputStream(file);
		try {
			out.write(data);
		} finally {
			out.close();
		}
	}

}
