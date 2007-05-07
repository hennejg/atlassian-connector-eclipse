package org.eclipse.mylar.jira.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import junit.framework.AssertionFailedError;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylar.internal.jira.core.model.Issue;
import org.eclipse.mylar.internal.jira.core.model.Project;
import org.eclipse.mylar.internal.jira.core.model.Resolution;
import org.eclipse.mylar.internal.jira.core.service.JiraException;
import org.eclipse.mylar.internal.jira.core.service.JiraServer;

public class JiraTestUtils {

	public static String PROJECT1 = "PRONE";
	
	public static Resolution getFixedResolution(JiraServer server) throws JiraException {
		refreshDetails(server);
		
		Resolution[] resolutions = server.getResolutions();
		for (Resolution resolution : resolutions) {
			if (Resolution.FIXED_ID.equals(resolution.getId())) {
				return resolution;
			}
		}
		return resolutions[0];
	}

	public static Issue createIssue(JiraServer server, String summary) throws JiraException {
		refreshDetails(server);
		
		Issue issue = new Issue();
		issue.setProject(getProject1(server));
		issue.setType(server.getIssueTypes()[0]);
		issue.setSummary(summary);
		issue.setAssignee(server.getUserName());
		
		return server.createIssue(issue);
	}

	public static void refreshDetails(JiraServer server) throws JiraException {
		if (!server.hasDetails()) {
			server.refreshDetails(new NullProgressMonitor());
		}
	}

	public static Project getProject1(JiraServer server) {
		Project project = server.getProjectByKey(PROJECT1);
		if (project == null) {
			throw new AssertionFailedError("Project '" + PROJECT1 + "' not found");
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
