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

import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.SummarizeReviewPart;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.SummarizeReviewPart.ISummarizeReviewPartListener;
import com.atlassian.theplugin.commons.crucible.api.model.Review;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * @author Thomas Ehrnhoefer
 */
public class CrucibleSummarizeReviewDialog extends Dialog implements ISummarizeReviewPartListener {

	private SummarizeReviewPart part;

	private final Review review;

	private final String userName;

	public CrucibleSummarizeReviewDialog(Shell parentShell, Review review, String userName) {
		super(parentShell);
		this.review = review;
		this.userName = userName;
	}

	@Override
	protected Control createContents(Composite parent) {
		//CHECKSTYLE:MAGIC:OFF
		part = new SummarizeReviewPart(review, userName);
		Composite composite = part.createControl(parent);
		part.setListener(this);
		GridDataFactory.fillDefaults().grab(true, true).hint(400, SWT.DEFAULT).applyTo(composite);
		return composite;
		//CHECKSTYLE:MAGIC:ON
	}

	public void cancelSummarizeReview() {
		setReturnCode(Window.CANCEL);
		close();
	}

	public void summarizeReview() {
		setReturnCode(Window.OK);
		close();
	}

	public boolean isDiscardDrafts() {
		return part.isDiscardDrafts();
	}

	public String getSummarizeText() {
		return part.getSummarizeText();
	}

}
