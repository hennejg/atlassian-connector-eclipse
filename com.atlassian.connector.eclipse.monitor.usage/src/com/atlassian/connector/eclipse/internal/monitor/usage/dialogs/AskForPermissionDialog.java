/*******************************************************************************
 * Copyright (c) 2004, 2008 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package com.atlassian.connector.eclipse.internal.monitor.usage.dialogs;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import com.atlassian.connector.eclipse.internal.monitor.usage.Messages;
import com.atlassian.connector.eclipse.internal.monitor.usage.UiUsageMonitorPlugin;
import com.atlassian.connector.eclipse.internal.monitor.usage.UsageCollector;

public class AskForPermissionDialog extends Dialog {

	private ImageRegistry imageRegistry;

	public AskForPermissionDialog(Shell parentShell) {
		super(parentShell);
	}

	public Image getImage(UsageCollector data) {
		if (imageRegistry == null) {
			imageRegistry = new ImageRegistry(getShell().getDisplay());
		}

		Image image = imageRegistry.get(data.getUploadUrl());
		if (image == null && data.getIcon() != null) {
			imageRegistry.put(data.getUploadUrl(), data.getIcon());
			image = imageRegistry.get(data.getUploadUrl());
		}
		return image;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText(Messages.UiUsageMonitorPlugin_send_usage_feedback);

		Composite composite = new Composite((Composite) super.createDialogArea(parent), SWT.NONE);
		composite.setLayout(GridLayoutFactory.fillDefaults().numColumns(1).create());
		GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);

		Label messageLabel = new Label(composite, SWT.WRAP);
		messageLabel.setText(Messages.AskForPermissionDialog_please_consider_uploading);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false).hint(
				convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH), SWT.DEFAULT).applyTo(
				messageLabel);

		for (UsageCollector collector : UiUsageMonitorPlugin.getDefault().getStudyParameters().getUsageCollectors()) {
			Composite uc = new Composite(composite, SWT.NONE);
			uc.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).create());
			GridDataFactory.fillDefaults().grab(true, false).applyTo(uc);

			new Label(uc, SWT.NONE).setImage(getImage(collector));

			final String detailsUrl = collector.getDetailsUrl();

			Link details = new Link(uc, SWT.NULL);
			details.setText(String.format("<A>%s</A>", (String) Platform.getBundle(collector.getBundle()) //$NON-NLS-1$
					.getHeaders()
					.get("Bundle-Name"))); //$NON-NLS-1$
			details.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					WorkbenchUtil.openUrl(detailsUrl, IWorkbenchBrowserSupport.AS_EXTERNAL);
				}
			});
		}

		new Label(composite, SWT.WRAP).setText(Messages.AskForPermissionDialog_to_see_what_will_be_tracked);

		applyDialogFont(composite);
		return composite;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		// create OK and Cancel buttons by default
		createButton(parent, IDialogConstants.YES_ID, IDialogConstants.YES_LABEL, true);
		createButton(parent, IDialogConstants.NO_ID, IDialogConstants.NO_LABEL, false);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		setReturnCode(buttonId);
		close();
	}

}
