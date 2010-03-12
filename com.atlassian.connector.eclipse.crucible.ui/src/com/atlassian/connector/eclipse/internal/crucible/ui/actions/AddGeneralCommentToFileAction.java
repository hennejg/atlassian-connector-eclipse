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

package com.atlassian.connector.eclipse.internal.crucible.ui.actions;

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.ICrucibleFileProvider;
import com.atlassian.connector.eclipse.internal.crucible.ui.IReviewAction;
import com.atlassian.connector.eclipse.internal.crucible.ui.IReviewActionListener;
import com.atlassian.connector.eclipse.team.ui.CrucibleFile;
import com.atlassian.theplugin.commons.crucible.api.model.Review;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.source.LineRange;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

/**
 * Action to add a general file comment to the active review
 * 
 * @author Shawn Minto
 * @author Thomas Ehrnhoefer
 */
public class AddGeneralCommentToFileAction extends AbstractAddCommentAction implements IReviewAction {

	private CrucibleFile crucibleFile;

	private IReviewActionListener actionListener;

	private IResource file;

	public AddGeneralCommentToFileAction() {
		super("Create General File Comment...");
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		super.selectionChanged(action, selection);
		//if file and review are already set, don't bother
//		if (crucibleFile != null && review != null) {
//			return;
//		}
		crucibleFile = null;
		file = null;

		// TODO jj check action for compare editor

//		//the following only applies if it is the action from the extension point
		if (action.isEnabled() && isEnabled()) {
			IEditorPart editorPart = getActiveEditor();
			IEditorInput editorInput = getEditorInputFromSelection(selection);
			if (editorPart != null && editorInput instanceof ICrucibleFileProvider) {
				if (crucibleFile == null) {
					crucibleFile = ((ICrucibleFileProvider) editorInput).getCrucibleFile();
				}
				if (crucibleFile != null && CrucibleUtil.canAddCommentToReview(getReview())
						&& CrucibleUiUtil.isFilePartOfActiveReview(crucibleFile)) {
					return;
				}
			} else if (getReview() != null && editorInput != null) {

				IResource resource = (IResource) editorInput.getAdapter(IResource.class);

				if (resource instanceof IFile) {
					CrucibleFile cruFile = CrucibleUiUtil.getCrucibleFileFromResource(resource, getReview());
					if (cruFile != null) {
						crucibleFile = cruFile;
					} else {
						file = resource;
					}
				} else {
					action.setEnabled(false);
					setEnabled(false);
					return;
				}
			}
		}

		if (crucibleFile == null && file == null) {
			action.setEnabled(false);
			setEnabled(false);
		}

	}

	@Override
	protected boolean updateSelection(IStructuredSelection selection) {
		if (crucibleFile != null && CrucibleUtil.canAddCommentToReview(getReview())
				&& CrucibleUiUtil.isFilePartOfActiveReview(crucibleFile)) {
			return true;
		}
		return false;
	}

	@Override
	protected Review getReview() {
		if (review != null) {
			return review;
		} else {
			return CrucibleUiPlugin.getDefault().getActiveReviewManager().getActiveReview();
		}
	}

	@Override
	protected CrucibleFile getCrucibleFile() {
		return crucibleFile;
	}

	@Override
	protected LineRange getSelectedRange() {
		return null;
	}

	@Override
	protected String getDialogTitle() {
		return "Create General File Comment";
	}

	@Override
	public final void run() {
		super.run();
		if (actionListener != null) {
			actionListener.actionRan(this);
		}
	}

	@Override
	public String getToolTipText() {
		return "Add General File Comment...";
	}

	public void setActionListener(IReviewActionListener listener) {
		this.actionListener = listener;
	}

	public void setCrucibleFile(CrucibleFile file) {
		this.crucibleFile = file;
	}

	public void setReview(Review review) {
		this.review = review;
	}

	@Override
	protected IResource getResource() {
		return file;
	}

}
