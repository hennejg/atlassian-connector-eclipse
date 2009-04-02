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

import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleImages;
import com.atlassian.connector.eclipse.ui.AtlassianImages;
import com.atlassian.connector.eclipse.ui.forms.SizeCachingComposite;
import com.atlassian.theplugin.commons.crucible.api.model.Reviewer;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.mylyn.internal.tasks.ui.editors.EditorUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;

import java.util.Iterator;
import java.util.Set;

/**
 * Form part that displays the details of a reviewer
 * 
 * @author Thomas Ehrnhoefer
 */
public class CrucibleReviewersPart {

	private final Set<Reviewer> reviewers;

	private Menu menu;

	public CrucibleReviewersPart(Set<Reviewer> reviewers) {
		super();
		this.reviewers = reviewers;
	}

	public Composite createControl(FormToolkit toolkit, Composite parent) {
		return createControl(toolkit, parent, "Reviewers:   ");
	}

	public Composite createControl(FormToolkit toolkit, Composite parent, final IAction contributedAction) {
		return createControl(toolkit, parent, "Reviewers:   ", contributedAction);
	}

	public Composite createControl(FormToolkit toolkit, Composite parent, String labelText) {
		return createControl(toolkit, parent, labelText, null);
	}

	public Composite createControl(FormToolkit toolkit, Composite parent, String labelText,
			final IAction contributedAction) {
		//CHECKSTYLE:MAGIC:OFF

		Composite reviewersPartComposite = createComposite(toolkit, parent);
		reviewersPartComposite.setLayout(GridLayoutFactory.fillDefaults().numColumns(3).create());

//		// use indent to make up for forms border gap
//		GridDataFactory.fillDefaults().grab(true, false).hint(300, SWT.DEFAULT).applyTo(reviewersComposite);
//		RowLayout layout = new RowLayout();
//		layout.marginBottom = 0;
//		layout.marginTop = 0;
//		layout.marginRight = 0;
//		layout.marginLeft = 0;
//		layout.marginWidth = 0;
//		layout.spacing = 0;
//		reviewersComposite.setLayout(layout);

		Label reviewersLabel = createLabelControl(toolkit, reviewersPartComposite, labelText);
		GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.TOP).applyTo(reviewersLabel);

		if (reviewers.isEmpty()) {
			// avoid blank gap on Linux
			createLabelControl(toolkit, reviewersPartComposite, " ");
		} else {
			Composite reviewersComposite = createComposite(toolkit, reviewersPartComposite);
			RowLayout layout = new RowLayout();
			layout.marginBottom = 0;
			layout.marginTop = 0;
			layout.marginRight = 0;
			layout.marginLeft = 0;
			layout.marginWidth = 0;
			layout.spacing = 0;
			layout.wrap = true;
			layout.fill = true;
			reviewersComposite.setLayout(layout);

			Iterator<Reviewer> iterator = reviewers.iterator();
			while (iterator.hasNext()) {
				Composite singleReviewersComposite = createComposite(toolkit, reviewersComposite);
				singleReviewersComposite.setLayout(GridLayoutFactory.fillDefaults()
						.numColumns(3)
						.spacing(0, 0)
						.create());

				Reviewer reviewer = iterator.next();
				Text text = createReadOnlyText(toolkit, singleReviewersComposite, reviewer.getDisplayName(), null,
						false);
				GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.TOP).applyTo(text);
				text.setBackground(parent.getBackground());

				if (reviewer.isCompleted()) {
					Label imageLabel = createLabelControl(toolkit, singleReviewersComposite, "");
					imageLabel.setImage(CrucibleImages.getImage(CrucibleImages.REVIEWER_COMPLETE));
					GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.TOP).applyTo(imageLabel);
				}

				if (iterator.hasNext()) {
					text = createReadOnlyText(toolkit, singleReviewersComposite, ", ", null, false);
					GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.TOP).applyTo(text);
				}
			}
			GridDataFactory.fillDefaults().hint(250, SWT.DEFAULT).grab(true, true).align(SWT.FILL, SWT.FILL).applyTo(
					reviewersComposite);
		}

		if (contributedAction != null) {
			ImageHyperlink hyperlink = toolkit.createImageHyperlink(reviewersPartComposite, SWT.NONE);
			if (contributedAction.getImageDescriptor() != null) {
				hyperlink.setImage(AtlassianImages.getImage(contributedAction.getImageDescriptor()));
			} else {
				hyperlink.setText(contributedAction.getText());
			}
			hyperlink.setToolTipText(contributedAction.getToolTipText());
			hyperlink.addHyperlinkListener(new HyperlinkAdapter() {
				@Override
				public void linkActivated(HyperlinkEvent e) {
					contributedAction.run();
				}
			});
			GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).applyTo(hyperlink);
		}

		GridDataFactory.fillDefaults().grab(true, true).applyTo(reviewersPartComposite);
		return reviewersPartComposite;
		//CHECKSTYLE:MAGIC:ON
	}

	private Composite createComposite(FormToolkit toolkit, Composite parent) {
		if (toolkit != null) {
			Composite composite = new SizeCachingComposite(parent, SWT.NONE);
			if (this.menu != null) {
				EditorUtil.setMenu(composite, null);
			}
			toolkit.adapt(composite);
			return composite;
		} else {
			return new SizeCachingComposite(parent, SWT.NONE);
		}
	}

	private Text createReadOnlyText(FormToolkit toolkit, Composite composite, String value, String labelString,
			boolean isMultiline) {

		if (labelString != null) {
			createLabelControl(toolkit, composite, labelString);
		}
		int style = SWT.FLAT | SWT.READ_ONLY;
		if (isMultiline) {
			style |= SWT.MULTI | SWT.WRAP;
		}
		Text text = new Text(composite, style);
		text.setFont(EditorUtil.TEXT_FONT);
		text.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
		text.setText(value);

		if (toolkit != null) {
			toolkit.adapt(text, true, true);
		}

		return text;
	}

	private Label createLabelControl(FormToolkit toolkit, Composite composite, String labelString) {

		Label labelControl = null;
		if (toolkit != null) {
			labelControl = toolkit.createLabel(composite, labelString);
			labelControl.setForeground(toolkit.getColors().getColor(IFormColors.TITLE));
		} else {
			labelControl = new Label(composite, SWT.NONE);
			labelControl.setText(labelString);
		}

		return labelControl;
	}

	public void setMenu(Menu menu) {
		this.menu = menu;
	}
}
