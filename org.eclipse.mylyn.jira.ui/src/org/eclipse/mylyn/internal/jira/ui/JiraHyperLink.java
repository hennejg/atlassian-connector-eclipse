/*******************************************************************************
 * Copyright (c) 2004 - 2006 University Of British Columbia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     University Of British Columbia - initial API and implementation
 *******************************************************************************/
package org.eclipse.mylar.internal.jira.ui;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.mylar.tasks.core.ITask;
import org.eclipse.mylar.tasks.core.TaskRepository;
import org.eclipse.mylar.tasks.ui.TasksUiPlugin;
import org.eclipse.mylar.tasks.ui.TasksUiUtil;

/**
 * @author Eugene Kuleshov
 */
public class JiraHyperLink implements IHyperlink {

	private final IRegion region;

	private final TaskRepository repository;

	private final String key;

//	private final String taskUrl;

	public JiraHyperLink(IRegion nlsKeyRegion, TaskRepository repository, String key, String taskUrl) {
		this.region = nlsKeyRegion;
		this.repository = repository;
		this.key = key;
//		this.taskUrl = taskUrl;
	}

	public IRegion getHyperlinkRegion() {
		return region;
	}

	public String getTypeLabel() {
		return null;
	}

	public String getHyperlinkText() {
		return "Open Task " + key;
	}

	public void open() {
		if (repository != null) {
			// TODO: put this back when TaskUiUtil.open(..) methods are fixed
//			TasksUiUtil.openRepositoryTask(repository.getUrl(), key, taskUrl);
			for (ITask task : TasksUiPlugin.getTaskListManager().getTaskList().getAllTasks()) {
				if (task instanceof JiraTask) {
					JiraTask jiraTask = (JiraTask) task;
					if (jiraTask.getTaskKey() != null && jiraTask.getTaskKey().equals(key)) {
						TasksUiUtil.refreshAndOpenTaskListElement(jiraTask);
					}
				}
			}

		} else {
			MessageDialog.openError(null, "Mylar Jira Connector", "Could not determine repository for report");
		}
	}

}
