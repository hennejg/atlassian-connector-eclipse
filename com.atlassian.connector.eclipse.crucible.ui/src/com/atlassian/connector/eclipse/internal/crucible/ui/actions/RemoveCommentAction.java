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

import com.atlassian.connector.commons.api.ConnectionCfg;
import com.atlassian.connector.commons.crucible.CrucibleServerFacade2;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleImages;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.IReviewAction;
import com.atlassian.connector.eclipse.internal.crucible.ui.IReviewActionListener;
import com.atlassian.connector.eclipse.internal.crucible.ui.actions.AbstractBackgroundJobReviewAction.RemoteCrucibleOperation;
import com.atlassian.theplugin.commons.crucible.api.CrucibleLoginException;
import com.atlassian.theplugin.commons.crucible.api.model.Comment;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.VersionedComment;
import com.atlassian.theplugin.commons.exception.ServerPasswordNotProvidedException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.ui.actions.BaseSelectionListenerAction;

public class RemoveCommentAction extends BaseSelectionListenerAction implements IReviewAction {
	private static String REMOVE_COMMENT = "Remove Comment";

	private Review review;

	private IReviewActionListener actionListener;

	public RemoveCommentAction() {
		super(REMOVE_COMMENT);
	}

	public void run() {
		final Comment comment = (Comment) getStructuredSelection().getFirstElement();
		IAction action = new AbstractBackgroundJobReviewAction(getText(), review, comment, WorkbenchUtil.getShell(),
				"Removing a comment from review " + review.getPermId().getId(), CrucibleImages.COMMENT_DELETE,
				new RemoteCrucibleOperation() {
					public void run(CrucibleServerFacade2 server, ConnectionCfg serverCfg)
							throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {
						server.removeComment(serverCfg, review.getPermId(), comment);
					}
				}, true) {
			// nothing needed here
		};
		action.run();

		if (actionListener != null) {
			actionListener.actionRan(this);
		}
	};

	@Override
	protected boolean updateSelection(IStructuredSelection selection) {
		this.review = null;

		Object element = selection.getFirstElement();
		if (element instanceof Comment && selection.size() == 1) {
			this.review = CrucibleUiPlugin.getDefault().getActiveReviewManager().getActiveReview();
			if (this.review != null && CrucibleUiUtil.canModifyComment(review, (Comment) element)) {
				if (element instanceof VersionedComment) {
					// only allow to remove a comment if it doesn't have replies
					return ((VersionedComment) element).getReplies().size() == 0;
				} else {
					return true;
				}
			}
		}
		return false;
	}

	public void setActionListener(IReviewActionListener listener) {
		this.actionListener = listener;
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return CrucibleImages.COMMENT_DELETE;
	}

	@Override
	public String getToolTipText() {
		return REMOVE_COMMENT;
	}
}
