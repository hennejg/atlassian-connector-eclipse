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

package com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts;

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleConstants;
import com.atlassian.connector.eclipse.internal.crucible.ui.IReviewAction;
import com.atlassian.connector.eclipse.internal.crucible.ui.actions.ReplyToCommentAction;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.CrucibleReviewEditorPage;
import com.atlassian.connector.eclipse.ui.team.CrucibleFile;
import com.atlassian.theplugin.commons.crucible.api.model.Comment;
import com.atlassian.theplugin.commons.crucible.api.model.CustomField;
import com.atlassian.theplugin.commons.crucible.api.model.Review;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mylyn.internal.tasks.ui.editors.EditorUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A UI part to represent a comment in a review
 * 
 * @author Shawn Minto
 * @author Thomas Ehrnhoefer
 */
public abstract class CommentPart extends ExpandablePart {

	protected final Comment comment;

	protected final Review crucibleReview;

	protected final CrucibleFile crucibleFile;

	public CommentPart(Comment comment, Review crucibleReview, CrucibleReviewEditorPage editor,
			CrucibleFile crucibleFile) {
		super(editor);
		this.comment = comment;
		this.crucibleReview = crucibleReview;
		this.crucibleFile = crucibleFile;
	}

	@Override
	protected Composite createSectionContents(Section section, FormToolkit toolkit) {
		//CHECKSTYLE:MAGIC:OFF
		section.clientVerticalSpacing = 0;

		Composite composite = toolkit.createComposite(section);
		GridLayout layout = new GridLayout(1, false);
		layout.marginTop = 0;
		layout.marginLeft = 9;
		composite.setLayout(layout);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);
		String commentString = getCommentText();

		Text commentText = createReadOnlyText(toolkit, composite, commentString);
		GridDataFactory.fillDefaults().hint(500, SWT.DEFAULT).applyTo(commentText);

		//CHECKSTYLE:MAGIC:ON
		return composite;
	}

	private String getCommentText() {
		String commentText = comment.getMessage();

		String customFieldsString = "";
		if (comment.getCustomFields() != null && comment.getCustomFields().size() > 0) {

			Map<String, CustomField> customFields = comment.getCustomFields();
			CustomField classificationField = customFields.get(CrucibleConstants.CLASSIFICATION_CUSTOM_FIELD_KEY);
			CustomField rankField = customFields.get(CrucibleConstants.RANK_CUSTOM_FIELD_KEY);

			String classification = null;
			if (classificationField != null) {
				classification = classificationField.getValue();
			}

			String rank = null;
			if (rankField != null) {
				rank = rankField.getValue();
			}

			if (rank != null || classification != null) {
				customFieldsString = "(";

				if (comment.isDefectApproved() || comment.isDefectRaised()) {
					customFieldsString += "Defect, ";
				}
			}

			if (classification != null) {
				customFieldsString += "Classification:" + classification;
				if (rank != null) {
					customFieldsString += ", ";
				}
			}

			if (rank != null) {
				customFieldsString += "Rank:" + rank;
			}

			if (customFieldsString.length() > 0) {
				customFieldsString += ")";
			}

		}
		if (customFieldsString.length() > 0) {
			commentText += "  " + customFieldsString;
		}
		return commentText;
	}

	@Override
	protected String getAnnotationText() {
		String text = "";
		if (comment.isDraft()) {
			text = "DRAFT ";
		}
		return text;
	}

	private Text createReadOnlyText(FormToolkit toolkit, Composite composite, String value) {

		int style = SWT.FLAT | SWT.READ_ONLY | SWT.MULTI | SWT.WRAP;

		final Text text = new Text(composite, style);
		text.setFont(EditorUtil.TEXT_FONT);
		text.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
		text.setText(value);
		toolkit.adapt(text, true, true);

		// HACK: this is to make sure that we can't have multiple things highlighted
		text.addFocusListener(new FocusListener() {

			public void focusGained(FocusEvent e) {
				// ignore

			}

			public void focusLost(FocusEvent e) {
				text.setSelection(0);
			}

		});

		return text;
	}

	@Override
	protected ImageDescriptor getAnnotationImage() {
		if (comment.isDefectRaised() || comment.isDefectApproved()) {

			// TODO get an image for a bug
			return null;
		}
		return null;
	}

	@Override
	protected List<IReviewAction> getToolbarActions(boolean isExpanded) {
		List<IReviewAction> actions = new ArrayList<IReviewAction>();
		if (isExpanded) {
			if (!comment.isReply()) {
				actions.add(new ReplyToCommentAction(comment, crucibleReview, crucibleFile));
			}
		}
		return actions;
	}
}