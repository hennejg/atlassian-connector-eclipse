/*******************************************************************************
 * Copyright (c) 2004, 2007 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.core.service.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.text.html.HTML.Tag;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.FilePartSource;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.PartBase;
import org.apache.commons.httpclient.methods.multipart.PartSource;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.eclipse.mylyn.internal.jira.core.model.Attachment;
import org.eclipse.mylyn.internal.jira.core.model.Component;
import org.eclipse.mylyn.internal.jira.core.model.CustomField;
import org.eclipse.mylyn.internal.jira.core.model.Issue;
import org.eclipse.mylyn.internal.jira.core.model.Version;
import org.eclipse.mylyn.internal.jira.core.model.WebServerInfo;
import org.eclipse.mylyn.internal.jira.core.service.JiraClient;
import org.eclipse.mylyn.internal.jira.core.service.JiraException;
import org.eclipse.mylyn.internal.jira.core.service.JiraRemoteException;
import org.eclipse.mylyn.internal.jira.core.service.JiraRemoteMessageException;
import org.eclipse.mylyn.monitor.core.StatusHandler;
import org.eclipse.mylyn.web.core.HtmlStreamTokenizer;
import org.eclipse.mylyn.web.core.HtmlTag;
import org.eclipse.mylyn.web.core.HtmlStreamTokenizer.Token;

/**
 * TODO look at creation Operation classes to perform each of these actions
 * TODO extract field names into constants
 * 
 * @author Brock Janiczak
 * @author Steffen Pingel
 * @author Eugene Kuleshov
 */
public class JiraWebIssueService {

	public static final String DATE_FORMAT = "dd-MMM-yyyy"; //$NON-NLS-1$

	public static final String DUE_DATE_FORMAT = "dd/MMM/yy"; //$NON-NLS-1$

	private final JiraClient server;

	public JiraWebIssueService(JiraClient server) {
		this.server = server;
	}

	public void addCommentToIssue(final Issue issue, final String comment) throws JiraException {
		final JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer rssUrlBuffer = new StringBuffer(baseUrl);
				rssUrlBuffer.append("/secure/AddComment.jspa");

				PostMethod post = new PostMethod(rssUrlBuffer.toString());
				post.setRequestHeader("Content-Type", s.getContentType());
				post.addParameter("comment", comment);
				post.addParameter("commentLevel", "");
				post.addParameter("id", issue.getId());

				try {
					client.executeMethod(post);
					if (!s.expectRedirect(post, issue)) {
						handleErrorMessage(post);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					post.releaseConnection();
				}
			}

		});
	}

	// TODO refactor common parameter configuration with advanceIssueWorkflow() method
	public void updateIssue(final Issue issue, final String comment) throws JiraException {
		final JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer rssUrlBuffer = new StringBuffer(baseUrl);
				rssUrlBuffer.append("/secure/EditIssue.jspa");

				PostMethod post = new PostMethod(rssUrlBuffer.toString());
				post.setRequestHeader("Content-Type", s.getContentType());
				post.addParameter("summary", issue.getSummary());
				post.addParameter("issuetype", issue.getType().getId());
				if (issue.getPriority() != null) {
					post.addParameter("priority", issue.getPriority().getId());
				}
				if (issue.getDue() != null) {
					post.addParameter("duedate", new SimpleDateFormat(DUE_DATE_FORMAT, Locale.US).format(issue.getDue()));
				} else {
					post.addParameter("duedate", "");
				}
				post.addParameter("timetracking", Long.toString(issue.getInitialEstimate()/60)+"m");

				Component[] components = issue.getComponents();
				if (components != null) {
					if (components.length == 0) {
						post.addParameter("components", "-1");
					} else {
						for (int i = 0; i < components.length; i++) {
							post.addParameter("components", components[i].getId());
						}
					}
				}

				Version[] versions = issue.getReportedVersions();
				if (versions != null) {
					if (versions.length == 0) {
						post.addParameter("versions", "-1");
					} else {
						for (int i = 0; i < versions.length; i++) {
							post.addParameter("versions", versions[i].getId());
						}
					}
				}

				Version[] fixVersions = issue.getFixVersions();
				if (fixVersions != null) {
					if (fixVersions.length == 0) {
						post.addParameter("fixVersions", "-1");
					} else {
						for (int i = 0; i < fixVersions.length; i++) {
							post.addParameter("fixVersions", fixVersions[i].getId());
						}
					}
				}

				// TODO need to be able to choose unassigned and automatic
				if (issue.getAssignee() != null) {
					post.addParameter("assignee", issue.getAssignee());
				} else {
					post.addParameter("assignee", "-1");
				}
				post.addParameter("reporter", issue.getReporter());
				post.addParameter("environment", issue.getEnvironment());
				post.addParameter("description", issue.getDescription());

				if (comment != null) {
					post.addParameter("comment", comment);
				}
				post.addParameter("commentLevel", "");
				post.addParameter("id", issue.getId());

				// custom fields
				for (CustomField customField : issue.getCustomFields()) {
					for (String value : customField.getValues()) {
						String key = customField.getKey();
						if (key == null || !key.startsWith("com.atlassian.jira.toolkit")) {
							post.addParameter(customField.getId(), value == null ? "" : value);
						}
					}
				}

				try {
					client.executeMethod(post);
					if (!s.expectRedirect(post, issue)) {
						handleErrorMessage(post);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					post.releaseConnection();
				}
			}

		});
	}

	public void assignIssueTo(final Issue issue, final int assigneeType, final String user, final String comment)
			throws JiraException {
		final JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer rssUrlBuffer = new StringBuffer(baseUrl);
				rssUrlBuffer.append("/secure/AssignIssue.jspa");

				PostMethod post = new PostMethod(rssUrlBuffer.toString());
				post.setRequestHeader("Content-Type", s.getContentType());

				post.addParameter("assignee", getAssigneeParam(server, issue, assigneeType, user));

				if (comment != null) {
					post.addParameter("comment", comment);
				}
				post.addParameter("commentLevel", "");
				post.addParameter("id", issue.getId());

				try {
					client.executeMethod(post);
					if (!s.expectRedirect(post, issue)) {
						handleErrorMessage(post);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					post.releaseConnection();
				}
			}

		});
	}

//	public void advanceIssueWorkflow(final Issue issue, final String action, final Resolution resolution,
//			final Version[] fixVersions, final String comment, final int assigneeType, final String user)
//			throws JiraException {
//		JiraWebSession s = new JiraWebSession(server);
//		s.doInSession(new JiraWebSessionCallback() {
//
//			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
//				PostMethod post = new PostMethod(baseUrl + "/secure/CommentAssignIssue.jspa");
//				post.setRequestHeader("Content-Type", s.getContentType());
//
//				if (resolution != null) {
//					post.addParameter("resolution", resolution.getId());
//					if (fixVersions == null || fixVersions.length == 0) {
//						post.addParameter("fixVersions", "-1");
//					} else {
//						for (int i = 0; i < fixVersions.length; i++) {
//							post.addParameter("fixVersions", fixVersions[i].getId());
//						}
//					}
//				}
//
//				post.addParameter("assignee", getAssigneeParam(server, issue, assigneeType, user));
//
//				if (comment != null) {
//					post.addParameter("comment", comment);
//				}
//				post.addParameter("commentLevel", "");
//				post.addParameter("action", action);
//				post.addParameter("id", issue.getId());
//
//				try {
//					int result = client.executeMethod(post);
//					if (result != HttpStatus.SC_MOVED_TEMPORARILY) {
//						handleErrorMessage(post, result);
//					}
//				} catch (IOException e) {
//					throw new JiraException(e);
//				} finally {
//					post.releaseConnection();
//				}
//			}
//		});
//	}

	public void advanceIssueWorkflow(final Issue issue, final String actionKey, final String comment, final String[] fields)
			throws JiraException {
		final JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {
			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				PostMethod post = new PostMethod(baseUrl + "/secure/CommentAssignIssue.jspa");
				post.setRequestHeader("Content-Type", s.getContentType());

				post.addParameter("id", issue.getId());
				post.addParameter("action", actionKey);
				// method.addParameter("assignee", issue.getAssignee());

				if (comment != null) {
					post.addParameter("comment", comment);
				}
				post.addParameter("commentLevel", "");

				for (String field : fields) {
					String[] values = issue.getFieldValues(field);
					if (values == null) {
						// method.addParameter(field, "");
					} else {
						for (String value : values) {
							post.addParameter(field, value);
						}
					}
				}

				try {
					client.executeMethod(post);
					if (!s.expectRedirect(post, issue)) {
						handleErrorMessage(post);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					post.releaseConnection();
				}
			}
		});
	}

	public void attachFile(final Issue issue, final String comment, final PartSource partSource,
			final String contentType) throws JiraException {
		attachFile(issue, comment, new FilePart("filename.1", partSource), contentType);
	}

	public void attachFile(final Issue issue, final String comment, final String filename, final byte[] contents,
			final String contentType) throws JiraException {
		attachFile(issue, comment, new FilePart("filename.1", new ByteArrayPartSource(filename, contents)), contentType);
	}

	public void attachFile(final Issue issue, final String comment, final String filename, final File file,
			final String contentType) throws JiraException {
		try {
			FilePartSource partSource = new FilePartSource(filename, file);
			attachFile(issue, comment, new FilePart("filename.1", partSource), contentType);
		} catch (FileNotFoundException e) {
			throw new JiraException(e);
		}
	}

	public void attachFile(final Issue issue, final String comment, final FilePart filePart, final String contentType)
			throws JiraException {
		final JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer attachFileURLBuffer = new StringBuffer(baseUrl);
				attachFileURLBuffer.append("/secure/AttachFile.jspa");

				PostMethod post = new PostMethod(attachFileURLBuffer.toString());

				List<PartBase> parts = new ArrayList<PartBase>();

				StringPart idPart = new StringPart("id", issue.getId());
				StringPart commentLevelPart = new StringPart("commentLevel", "");

				// The transfer encodings have to be removed for some reason
				// There is no need to send the content types for the strings,
				// as they should be in the correct format
				idPart.setTransferEncoding(null);
				idPart.setContentType(null);

				if (comment != null) {
					StringPart commentPart = new StringPart("comment", comment);
					commentPart.setTransferEncoding(null);
					commentPart.setContentType(null);
					parts.add(commentPart);
				}

				commentLevelPart.setTransferEncoding(null);
				commentLevelPart.setContentType(null);

				filePart.setTransferEncoding(null);
				if (contentType != null) {
					filePart.setContentType(contentType);
				}
				parts.add(filePart);
				parts.add(idPart);
				parts.add(commentLevelPart);

				post.setRequestEntity(new MultipartRequestEntity(parts.toArray(new Part[parts.size()]),
						post.getParams()));

				try {
					client.executeMethod(post);
					if (!s.expectRedirect(post, "/secure/ManageAttachments.jspa?id=" + issue.getId())) {
						handleErrorMessage(post);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					post.releaseConnection();
				}
			}
		});
	}

	public void retrieveFile(final Issue issue, final Attachment attachment, final byte[] attachmentData)
			throws JiraException {
		JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer rssUrlBuffer = new StringBuffer(baseUrl);
				rssUrlBuffer.append("/secure/attachment/");
				rssUrlBuffer.append(attachment.getId());
				rssUrlBuffer.append("/");
				try {
					rssUrlBuffer.append(URLEncoder.encode(attachment.getName(), server.getCharacterEncoding()));
				} catch (UnsupportedEncodingException e) {
					throw new JiraException(e);
				}

				GetMethod get = new GetMethod(rssUrlBuffer.toString());
				try {
					int result = client.executeMethod(get);
					if (result != HttpStatus.SC_OK) {
						handleErrorMessage(get);
					} else {
						byte[] data = get.getResponseBody();
						if (data.length != attachmentData.length) {
							throw new IOException("Unexpected attachment size (got " + data.length + ", expected "
									+ attachmentData.length + ")");
						}
						System.arraycopy(data, 0, attachmentData, 0, attachmentData.length);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					get.releaseConnection();
				}
			}

		});
	}

	public void retrieveFile(final Issue issue, final Attachment attachment, final OutputStream out)
			throws JiraException {
		JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer rssUrlBuffer = new StringBuffer(baseUrl);
				rssUrlBuffer.append("/secure/attachment/");
				rssUrlBuffer.append(attachment.getId());
				rssUrlBuffer.append("/");
				try {
					rssUrlBuffer.append(URLEncoder.encode(attachment.getName(), server.getCharacterEncoding()));
				} catch (UnsupportedEncodingException e) {
					throw new JiraException(e);
				}

				GetMethod get = new GetMethod(rssUrlBuffer.toString());
				try {
					int result = client.executeMethod(get);
					if (result != HttpStatus.SC_OK) {
						handleErrorMessage(get);
					} else {
						out.write(get.getResponseBody());
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					get.releaseConnection();
				}
			}

		});
	}

	public String createIssue(final Issue issue) throws JiraException {
		return createIssue("/secure/CreateIssueDetails.jspa", issue);
	}
	
	public String createSubTask(final Issue issue) throws JiraException {
		return createIssue("/secure/CreateSubTaskIssueDetails.jspa", issue);
	}
	
	// TODO refactor common parameter configuration with advanceIssueWorkflow() method
	private String createIssue(final String url, final Issue issue) throws JiraException {
		final String[] issueKey = new String[1];
		final JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer attachFileURLBuffer = new StringBuffer(baseUrl);
				attachFileURLBuffer.append(url);

				PostMethod post = new PostMethod(attachFileURLBuffer.toString());
				post.setRequestHeader("Content-Type", s.getContentType());

				post.addParameter("pid", issue.getProject().getId());
				post.addParameter("issuetype", issue.getType().getId());
				post.addParameter("summary", issue.getSummary());
				if (issue.getPriority() != null) {
					post.addParameter("priority", issue.getPriority().getId());
				}
				if (issue.getDue() != null) {
					post.addParameter("duedate", new SimpleDateFormat(DUE_DATE_FORMAT, Locale.US).format(issue.getDue()));
				}

				if (issue.getComponents() != null) {
					for (int i = 0; i < issue.getComponents().length; i++) {
						post.addParameter("components", issue.getComponents()[i].getId());
					}
				} else {
					post.addParameter("components", "-1");
				}

				if (issue.getReportedVersions() != null) {
					for (int i = 0; i < issue.getReportedVersions().length; i++) {
						post.addParameter("versions", issue.getReportedVersions()[i].getId());
					}
				} else {
					post.addParameter("versions", "-1");
				}

				if (issue.getFixVersions() != null) {
					for (int i = 0; i < issue.getFixVersions().length; i++) {
						post.addParameter("fixVersions", issue.getFixVersions()[i].getId());
					}
				} else {
					post.addParameter("fixVersions", "-1");
				}

				if (issue.getAssignee() == null) {
					post.addParameter("assignee", "-1"); // Default assignee
				} else if (issue.getAssignee().length() == 0) {
					post.addParameter("assignee", ""); // nobody
				} else {
					post.addParameter("assignee", issue.getAssignee());
				}

				post.addParameter("reporter", server.getUserName());

				post.addParameter("environment", issue.getEnvironment() != null ? issue.getEnvironment() : "");
				post.addParameter("description", issue.getDescription() != null ? issue.getDescription() : "");

				if (issue.getParentId() != null) {
					post.addParameter("parentIssueId", issue.getParentId());
				}
				
				try {
					client.executeMethod(post);
					if (!s.expectRedirect(post, "/browse/")) {
						handleErrorMessage(post);
					} else {
						final Header locationHeader = post.getResponseHeader("location");
						// parse issue key from issue URL 
						String location = locationHeader.getValue();
						int i = location.lastIndexOf("/");
						if (i != -1) {
							issueKey[0] = location.substring(i);
						} else {
							throw new JiraException("The server redirected to an unexpected location while creating an issue: " + location);
						}
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					post.releaseConnection();
				}
			}
		});
		assert issueKey[0] != null;
		return issueKey[0];
	}

	public void watchIssue(final Issue issue) throws JiraException {
		watchUnwatchIssue(issue, true);
	}

	public void unwatchIssue(final Issue issue) throws JiraException {
		watchUnwatchIssue(issue, false);
	}

	private void watchUnwatchIssue(final Issue issue, final boolean watch) throws JiraException {
		JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer urlBuffer = new StringBuffer(baseUrl);
				urlBuffer.append("/browse/").append(issue.getKey());
				urlBuffer.append("?watch=").append(Boolean.toString(watch));

				HeadMethod head = new HeadMethod(urlBuffer.toString());
				try {
					int result = client.executeMethod(head);
					if (result != HttpStatus.SC_OK) {
						throw new JiraException("Changing watch status failed. Return code: " + result);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					head.releaseConnection();
				}
			}

		});
	}

	public void voteIssue(final Issue issue) throws JiraException {
		voteUnvoteIssue(issue, true);
	}

	public void unvoteIssue(final Issue issue) throws JiraException {
		voteUnvoteIssue(issue, false);
	}

	private void voteUnvoteIssue(final Issue issue, final boolean vote) throws JiraException {
		if (!issue.canUserVote(this.server.getUserName())) {
			return;
		}

		JiraWebSession s = new JiraWebSession(server);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				StringBuffer urlBuffer = new StringBuffer(baseUrl);
				urlBuffer.append("/browse/").append(issue.getKey());
				urlBuffer.append("?vote=").append(vote ? "vote" : "unvote");

				HeadMethod head = new HeadMethod(urlBuffer.toString());
				try {
					int result = client.executeMethod(head);
					if (result != HttpStatus.SC_OK) {
						throw new JiraException("Changing vote failed. Return code: " + result);
					}
				} catch (IOException e) {
					throw new JiraException(e);
				} finally {
					head.releaseConnection();
				}
			}

		});
	}

	public WebServerInfo getWebServerInfo() throws JiraException {
		final WebServerInfo webServerInfo = new WebServerInfo();
		final JiraWebSession s = new JiraWebSession(server);
		s.setLogEnabled(true);
		s.doInSession(new JiraWebSessionCallback() {

			public void execute(HttpClient client, JiraClient server, String baseUrl) throws JiraException {
				webServerInfo.setCharacterEncoding(s.getBaseURL());
				webServerInfo.setCharacterEncoding(s.getCharacterEncoding());
				webServerInfo.setInsecureRedirect(s.isInsecureRedirect());
			}
		});
		return webServerInfo;
	}
	
	private String getAssigneeParam(JiraClient server, Issue issue, int assigneeType, String user) {
		switch (assigneeType) {
		case JiraClient.ASSIGNEE_CURRENT:
			return issue.getAssignee();
		case JiraClient.ASSIGNEE_DEFAULT:
			return "-1"; //$NON-NLS-1$
		case JiraClient.ASSIGNEE_NONE:
			return ""; //$NON-NLS-1$
		case JiraClient.ASSIGNEE_SELF:
			return server.getUserName();
		case JiraClient.ASSIGNEE_USER:
			return user;
		default:
			return user;
		}
	}

	protected void handleErrorMessage(HttpMethodBase method) throws IOException, JiraException {
		String response = method.getResponseBodyAsString();
		StatusHandler.log("JIRA error\n"+response, null);
		
		if (method.getStatusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE) {
			throw new JiraRemoteException("JIRA system error", null);
		}

		StringReader reader = new StringReader(response);
		try {
			StringBuilder msg = new StringBuilder();
			HtmlStreamTokenizer tokenizer = new HtmlStreamTokenizer(reader, null);
			for (Token token = tokenizer.nextToken(); token.getType() != Token.EOF; token = tokenizer.nextToken()) {
				if (token.getType() == Token.TAG) {
					HtmlTag tag = (HtmlTag) token.getValue();

					String classValue = tag.getAttribute("class");
					if (classValue != null) {
						if (tag.getTagType() == HtmlTag.Type.DIV) {
							if (classValue.startsWith("infoBox")) {
								throw new JiraRemoteMessageException(getContent(tokenizer, HtmlTag.Type.DIV));
							} else if (classValue.startsWith("errorArea")) {
								throw new JiraRemoteMessageException(getContent(tokenizer, HtmlTag.Type.DIV));
							}
						} else if (tag.getTagType() == HtmlTag.Type.SPAN) {
							if (classValue.startsWith("errMsg")) {
								msg.append(getContent(tokenizer, HtmlTag.Type.SPAN));
							}
						}
					}
				}
			}
			if (msg.length() == 0) {
				throw new JiraRemoteMessageException(response);
			} else {
				throw new JiraRemoteMessageException(msg.toString());
			}
		} catch (ParseException e) {
			throw new JiraRemoteMessageException("An error has while parsing JIRA response: " + method.getStatusCode(), "");
		} finally {
			reader.close();
		}
	}

	private String getContent(HtmlStreamTokenizer tokenizer, Tag closingTag) throws IOException, ParseException {
		StringBuffer sb = new StringBuffer();
		int count = 0;
		for (Token token = tokenizer.nextToken(); token.getType() != Token.EOF; token = tokenizer.nextToken()) {
			if (token.getType() == Token.TAG) {
				HtmlTag tag = (HtmlTag) token.getValue();
				if (tag.getTagType() == closingTag) {
					if (tag.isEndTag()) {
						if (count == 0) {
							break;
						} else {
							count--;
						}
					} else {
						count++;
					}
				}
			}

			sb.append(token.toString());
		}
		return sb.toString();
	}

}
