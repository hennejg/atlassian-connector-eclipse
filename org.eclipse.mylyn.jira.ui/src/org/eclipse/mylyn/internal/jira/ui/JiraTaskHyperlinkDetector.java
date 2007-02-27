/*******************************************************************************
 * Copyright (c) 2004 - 2006 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.ui;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.mylar.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylar.tasks.core.TaskRepository;
import org.eclipse.mylar.tasks.ui.TasksUiPlugin;
import org.eclipse.mylar.tasks.ui.editors.RepositoryTextViewer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

/**
 * @author Eugene Kuleshov
 */
public class JiraTaskHyperlinkDetector extends AbstractHyperlinkDetector {

	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		if (region == null || textViewer == null) {
			return null;
		}

		TaskRepository repository = null;
		if (textViewer instanceof RepositoryTextViewer) {
			// Get repository from repository text viewer
			RepositoryTextViewer viewer = (RepositoryTextViewer) textViewer;

			repository = viewer.getRepository();

			if (repository == null)
				return null;

		} else {
			// Get repository from files associated project -> repository
			// mapping
			IEditorPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
			IEditorInput input = part.getEditorInput();
			IResource resource = (IResource) input.getAdapter(IResource.class);
			if (resource != null) {
				repository = TasksUiPlugin.getDefault().getRepositoryForResource(resource, true);
				if (repository == null) {
					return null;
				}
			}
		}

		if (repository == null)
			return null;

		IDocument document = textViewer.getDocument();
		if (document == null)
			return null;

		int offset = region.getOffset();
		IRegion lineInfo;
		String line;
		try {
			lineInfo = document.getLineInformationOfOffset(offset);
			line = document.get(lineInfo.getOffset(), lineInfo.getLength());
		} catch (BadLocationException ex) {
			return null;
		}

		int offsetInLine = offset - lineInfo.getOffset();

		AbstractRepositoryConnector connector = TasksUiPlugin.getRepositoryManager().getRepositoryConnector(
				repository.getKind());

		int startPos = offsetInLine;
		while (startPos > 0) {
			char c = line.charAt(startPos);
			if (Character.isWhitespace(c) || ",.;[](){}".indexOf(c) > -1)
				break;
			startPos--;
		}
		int endPos = offsetInLine;
		while (endPos < lineInfo.getLength()) {
			char c = line.charAt(endPos);
			if (Character.isWhitespace(c) || ",.;[](){}".indexOf(c) > -1)
				break;
			endPos++;
		}

		String[] taskIds = connector.getTaskIdsFromComment(repository, line.substring(startPos, endPos));
		if (taskIds == null || taskIds.length == 0)
			return null;

		IHyperlink[] links = new IHyperlink[taskIds.length];
		for (int i = 0; i < taskIds.length; i++) {
			String taskId = taskIds[i];
			int startRegion = line.indexOf(taskId, startPos);
			links[i] = new JiraHyperLink(new Region(lineInfo.getOffset() + startRegion, taskId.length()), repository,
					taskId, connector.getTaskWebUrl(repository.getUrl(), taskId));
		}
		return links;
	}

}
