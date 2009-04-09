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

package com.atlassian.connector.eclipse.internal.crucible.ui.dialogs;

import com.atlassian.connector.eclipse.internal.crucible.core.client.model.CrucibleCachedUser;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.ReviewersSelectionTreePart;
import com.atlassian.theplugin.commons.crucible.ValueNotYetInitialized;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.Reviewer;
import com.atlassian.theplugin.commons.crucible.api.model.ReviewerBean;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import java.util.HashSet;
import java.util.Set;

/**
 * Dialog for selecting reviewers
 * 
 * @author Thomas Ehrnhoefer
 */
public class ReviewerSelectionDialog extends Dialog {

	private final Set<Reviewer> selectedReviewers;

	private final Set<Reviewer> allReviewers;

	private final Review review;

	private ReviewersSelectionTreePart reviewersSelectionTreePart;

	public ReviewerSelectionDialog(Shell shell, Review review, Set<CrucibleCachedUser> users) {
		super(shell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.review = review;
		selectedReviewers = new HashSet<Reviewer>();
		allReviewers = new HashSet<Reviewer>();
		for (CrucibleCachedUser user : CrucibleUiUtil.getCachedUsers(review)) {
			ReviewerBean reviewer = createReviewerFromCachedUser(user);
			allReviewers.add(reviewer);
		}
	}

	private ReviewerBean createReviewerFromCachedUser(CrucibleCachedUser user) {
		ReviewerBean reviewer = new ReviewerBean();
		reviewer.setDisplayName(user.getDisplayName());
		reviewer.setUserName(user.getUserName());
		boolean completed = false;
		try {
			for (Reviewer r : review.getReviewers()) {
				if (r.getUserName().equals(reviewer.getUserName())) {
					completed = r.isCompleted();
					selectedReviewers.add(reviewer);
					break;
				}
			}
		} catch (ValueNotYetInitialized e) {
			// ignore
		}
		reviewer.setCompleted(completed);
		return reviewer;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		reviewersSelectionTreePart = new ReviewersSelectionTreePart(selectedReviewers, review);
		Composite composite = reviewersSelectionTreePart.createControl(parent);

		applyDialogFont(composite);

		return composite;
	}

	public Set<Reviewer> getSelectedReviewers() {
		return reviewersSelectionTreePart.getSelectedReviewers();
	}
}
