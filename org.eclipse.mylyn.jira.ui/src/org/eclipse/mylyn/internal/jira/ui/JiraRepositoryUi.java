/*******************************************************************************
 * Copyright (c) 2004 - 2006 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.ui;

import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mylar.internal.jira.ui.wizards.EditJiraQueryWizard;
import org.eclipse.mylar.internal.jira.ui.wizards.JiraQueryPage;
import org.eclipse.mylar.internal.jira.ui.wizards.JiraRepositorySettingsPage;
import org.eclipse.mylar.internal.jira.ui.wizards.NewJiraQueryWizard;
import org.eclipse.mylar.internal.jira.ui.wizards.NewJiraTaskWizard;
import org.eclipse.mylar.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylar.tasks.core.AbstractRepositoryQuery;
import org.eclipse.mylar.tasks.core.AbstractRepositoryTask;
import org.eclipse.mylar.tasks.core.TaskRepository;
import org.eclipse.mylar.tasks.ui.AbstractRepositoryConnectorUi;
import org.eclipse.mylar.tasks.ui.TasksUiPlugin;
import org.eclipse.mylar.tasks.ui.wizards.AbstractRepositorySettingsPage;

/**
 * @author Mik Kersten
 * @author Eugene Kuleshov
 * @author Steffen Pingel
 */
public class JiraRepositoryUi extends AbstractRepositoryConnectorUi {

	public String getTaskKindLabel(AbstractRepositoryTask repositoryTask) {
		return "Issue";
	}
	
	@Override
	public WizardPage getSearchPage(TaskRepository repository, IStructuredSelection selection) {
		return new JiraQueryPage(repository);
	} 

	@Override
	public AbstractRepositorySettingsPage getSettingsPage() {
		return new JiraRepositorySettingsPage(this);
	}

	@Override
	public IWizard getQueryWizard(TaskRepository repository, AbstractRepositoryQuery query) {
		if (query instanceof JiraRepositoryQuery || query instanceof JiraCustomQuery) {
			return new EditJiraQueryWizard(repository, query);
		}
		return new NewJiraQueryWizard(repository);
	}
	
	@Override
	public IWizard getNewTaskWizard(TaskRepository taskRepository) {
		return new NewJiraTaskWizard(taskRepository);
	}

	@Override
	public boolean hasRichEditor() {
		return true;
	}

	@Override
	public String getRepositoryType() {
		return JiraUiPlugin.REPOSITORY_KIND;
	}

	@Override
	public boolean hasSearchPage() {
		return true;
	}

	@Override
	public IHyperlink[] findHyperlinks(TaskRepository repository, String text, int lineOffset, int regionOffset) {
		AbstractRepositoryConnector connector = TasksUiPlugin.getRepositoryManager().getRepositoryConnector(
				repository.getKind());
		
		int startPos = lineOffset;
		while (startPos > 0) {
			char c = text.charAt(startPos);
			if (Character.isWhitespace(c) || ",.;[](){}".indexOf(c) > -1)
				break;
			startPos--;
		}
		int endPos = lineOffset;
		while (endPos < text.length()) {
			char c = text.charAt(endPos);
			if (Character.isWhitespace(c) || ",.;[](){}".indexOf(c) > -1)
				break;
			endPos++;
		}

		String[] taskIds = connector.getTaskIdsFromComment(repository, text.substring(startPos, endPos));
		if (taskIds == null || taskIds.length == 0)
			return null;

		IHyperlink[] links = new IHyperlink[taskIds.length];
		for (int i = 0; i < taskIds.length; i++) {
			String taskId = taskIds[i];
			int startRegion = text.indexOf(taskId, startPos);
			links[i] = new JiraHyperLink(new Region(regionOffset + startRegion, taskId.length()), repository, taskId,
					connector.getTaskWebUrl(repository.getUrl(), taskId));
		}
		return links;
	}

}
