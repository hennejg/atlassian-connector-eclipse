/*******************************************************************************
 * Copyright (c) 2004, 2007 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.core;

import org.eclipse.mylyn.tasks.core.data.TaskAttribute;

/**
 * @author Eugene Kuleshov
 * @author Steffen Pingel
 */
public enum JiraAttribute {

	ACTUAL(JiraAttributeFactory.ATTRIBUTE_ACTUAL, JiraFieldType.TEXTFIELD, "Time Spent:", false, true, "timespent"),

	AFFECTSVERSIONS(JiraAttributeFactory.ATTRIBUTE_AFFECTSVERSIONS, JiraFieldType.MULTISELECT, "Affects Versions:",
			false, false, "versions"),

	COMMENT_NEW(TaskAttribute.COMMENT_NEW, JiraFieldType.TEXTAREA, "New Comment:", true, false, "comment"),

	COMPONENTS(JiraAttributeFactory.ATTRIBUTE_COMPONENTS, JiraFieldType.MULTISELECT, "Components:", false, false,
			"components"),

	CREATION_DATE(TaskAttribute.DATE_CREATION, JiraFieldType.DATEPICKER, "Created:"),

	DESCRIPTION(TaskAttribute.DESCRIPTION, JiraFieldType.TEXTFIELD, "Description:", true, false, "description"),

	DUE_DATE(JiraAttributeFactory.ATTRIBUTE_DUE_DATE, JiraFieldType.DATEPICKER, "Due Date:", true, false, "duedate"),

	ENVIRONMENT(JiraAttributeFactory.ATTRIBUTE_ENVIRONMENT, JiraFieldType.TEXTAREA, "Environment:", false, false,
			"environment"),

	ESTIMATE(JiraAttributeFactory.ATTRIBUTE_ESTIMATE, JiraFieldType.TEXTFIELD, "Estimate:", false, false,
			"timetracking"),

	FIXVERSIONS(JiraAttributeFactory.ATTRIBUTE_FIXVERSIONS, JiraFieldType.MULTISELECT, "Fix Versions:", false, false,
			"fixVersions"),

	INITIAL_ESTIMATE(JiraAttributeFactory.ATTRIBUTE_INITIAL_ESTIMATE, JiraFieldType.TEXTFIELD, "Original Estimate:",
			false, true),

	ISSUE_KEY(TaskAttribute.TASK_KEY, JiraFieldType.TEXTFIELD, "Issue ID:"),

	LINKED_IDS(JiraAttributeFactory.ATTRIBUTE_LINKED_IDS, JiraFieldType.TEXTFIELD, "Linked ids:", true, true),

	MODIFICATION_DATE(TaskAttribute.DATE_MODIFIED, JiraFieldType.DATEPICKER, "Modified:"),

	PARENT_ID(JiraAttributeFactory.ATTRIBUTE_ISSUE_PARENT_ID, JiraFieldType.ISSUELINK, "Parent ID:", true, true),

	PARENT_KEY(JiraAttributeFactory.ATTRIBUTE_ISSUE_PARENT_KEY, JiraFieldType.ISSUELINK, "Parent:", false, true),

	PRIORITY(TaskAttribute.PRIORITY, JiraFieldType.SELECT, "Priority:", false, false, "priority"),

	PROJECT(TaskAttribute.PRODUCT, JiraFieldType.PROJECT, "Project:", false, true),

	RESOLUTION(TaskAttribute.RESOLUTION, JiraFieldType.SELECT, "Resolution:", true, false, "resolution"),

	SECURITY_LEVEL(JiraAttributeFactory.ATTRIBUTE_SECURITY_LEVEL, JiraFieldType.SELECT, "Security Level:", false, true),

	STATUS(TaskAttribute.STATUS, JiraFieldType.SELECT, "Status:"),

	SUBTASK_IDS(JiraAttributeFactory.ATTRIBUTE_SUBTASK_IDS, JiraFieldType.TEXTFIELD, "Subtask ids:", true, true),

	SUBTASK_KEYS(JiraAttributeFactory.ATTRIBUTE_SUBTASK_KEYS, JiraFieldType.ISSUELINKS, "Subtasks:", false, true),

	SUMMARY(TaskAttribute.SUMMARY, JiraFieldType.TEXTFIELD, "Summary:", true, false, "summary"),

	TYPE(JiraAttributeFactory.ATTRIBUTE_TYPE, JiraFieldType.SELECT, "Type:", false, false, "issuetype"),

	UNKNOWN(null, JiraFieldType.UNKNOWN, "unknown:", true, true),

	USER_ASSIGNED(TaskAttribute.USER_ASSIGNED, JiraFieldType.USERPICKER, "Assigned to:", true, false, "assignee"),

	USER_REPORTER(TaskAttribute.USER_REPORTER, JiraFieldType.USERPICKER, "Reported by:"),

	TASK_KEY(TaskAttribute.TASK_KEY, JiraFieldType.TEXTFIELD, "Task ID:"),

	TASK_URL(TaskAttribute.TASK_URL, JiraFieldType.URL, "URL:");

	public static JiraAttribute valueById(String id) {
		for (JiraAttribute attribute : values()) {
			if (id.equals(attribute.getId())) {
				return attribute;
			}
		}
		return UNKNOWN;
	}

	private final String id;

	private final boolean isHidden;

	private final boolean isReadOnly;

	private final String name;

	private final String paramName;

	private final JiraFieldType type;

	private JiraAttribute(String id, JiraFieldType type, String name) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.isHidden = true;
		this.isReadOnly = true;
		this.paramName = null;
	}

	private JiraAttribute(String id, JiraFieldType type, String name, boolean isHidden, boolean isReadOnly) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.isHidden = isHidden;
		this.isReadOnly = isReadOnly;
		this.paramName = null;
	}

	private JiraAttribute(String id, JiraFieldType type, String name, boolean isHidden, boolean isReadOnly,
			String paramName) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.isHidden = isHidden;
		this.isReadOnly = isReadOnly;
		this.paramName = paramName;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getParamName() {
		return paramName;
	}

	public JiraFieldType getType() {
		return type;
	}

	public boolean isHidden() {
		return isHidden;
	}

	public boolean isReadOnly() {
		return isReadOnly;
	}

	public String getKind() {
		return (isHidden) ? null : TaskAttribute.KIND_DEFAULT;
	}

}
