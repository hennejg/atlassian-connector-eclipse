/*******************************************************************************
 * Copyright (c) 2006 - 2006 Mylar eclipse.org project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mylar project committers - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.ui;

import org.eclipse.mylyn.tasks.core.AbstractTask;

/**
 * @author Mik Kersten
 * @author Wesley Coelho (initial integration patch)
 * @author Steffen Pingel
 */
public class JiraTask extends AbstractTask {

	public static final String UNASSIGNED_USER = "-1";

	private String key = null;

	public enum Kind {
		BUG, FEATURE, TASK, IMPROVEMENT, CUSTOM_ISSUE;

		@Override
		public String toString() {
			switch (this) {
			case BUG:
				return "Bug";
			case FEATURE:
				return "New Feature";
			case TASK:
				return "Task";
			case IMPROVEMENT:
				return "Improvement";
			case CUSTOM_ISSUE:
				return "Custom Issue";
			default:
				return "";
			}
		}
	}

	/**
	 * The handle is also the task's Jira url
	 */
	public JiraTask(String repositoryUrl, String id, String label) {
		super(repositoryUrl, id, label);
	}

	@Override
	public String getConnectorKind() {
		return JiraUiPlugin.REPOSITORY_KIND;
	}

	@Override
	public String getTaskKey() {
		return key;
	}

	public void setTaskKey(String key) {
		this.key = key;
	}

	@Override
	public boolean isLocal() {
		// TODO Auto-generated method stub
		return false;
	}

}
