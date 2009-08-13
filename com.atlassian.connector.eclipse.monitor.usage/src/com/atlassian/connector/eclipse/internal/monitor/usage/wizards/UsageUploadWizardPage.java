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

package com.atlassian.connector.eclipse.internal.monitor.usage.wizards;

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import com.atlassian.connector.eclipse.internal.monitor.usage.Messages;
import com.atlassian.connector.eclipse.internal.monitor.usage.UiUsageMonitorPlugin;
import com.atlassian.connector.eclipse.internal.monitor.usage.UsageCollector;
import com.atlassian.connector.eclipse.internal.monitor.usage.operations.UsageDataUploadJob;

/**
 * Page to upload the file to the server
 * 
 * @author Shawn Minto
 * @author Mik Kersten
 */
public class UsageUploadWizardPage extends WizardPage {

	// private static final int MAX_NUM_LINES = 1000;

	/** A text box to hold the location of the usage statistics file */
	private Text usageFileText;

	// /** A text box to hold the location of the log file */
	// private Text logFileText;

	private final UsageSubmissionWizard wizard;

	/**
	 * Constructor
	 */
	public UsageUploadWizardPage(UsageSubmissionWizard wizard) {
		super(Messages.UsageUploadWizardPage_page_title);

		setTitle(Messages.UsageUploadWizardPage_title);
		setDescription(Messages.UsageUploadWizardPage_description);

		this.wizard = wizard;
	}

	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */

	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		layout.numColumns = 1;

		Composite topContainer = new Composite(container, SWT.NULL);
		GridLayout topContainerLayout = new GridLayout();
		topContainer.setLayout(topContainerLayout);
		topContainerLayout.numColumns = 2;
		topContainerLayout.verticalSpacing = 9;

		Label label = new Label(topContainer, SWT.NULL);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(label);
		label.setText(Messages.UsageUploadWizardPage_recipients);

		for (UsageCollector collector : UiUsageMonitorPlugin.getDefault().getStudyParameters().getUsageCollectors()) {
			Composite uc = new Composite(topContainer, SWT.NONE);
			uc.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).create());
			GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(uc);

			new Label(uc, SWT.NONE).setImage(UiUsageMonitorPlugin.getDefault().getCollectorLogo(collector));

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

		label = new Label(topContainer, SWT.NULL | SWT.BEGINNING);
		label.setText(Messages.UsageUploadWizardPage_usage_file_location);

		usageFileText = new Text(topContainer, SWT.BORDER | SWT.SINGLE);
		usageFileText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		usageFileText.setEditable(false);

		usageFileText.setText(wizard.getMonitorFileName());

		final List<File> backupFiles = UsageDataUploadJob.getBackupFiles();
		if (backupFiles != null && backupFiles.size() > 0) {
			for (File backupFile : backupFiles) {
				label = new Label(topContainer, SWT.NULL);
				label.setText(Messages.UsageUploadWizardPage_archive_file);

				Text backupFileText = new Text(topContainer, SWT.BORDER | SWT.SINGLE);
				backupFileText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
				backupFileText.setEditable(false);

				backupFileText.setText(backupFile.toString());
			}
		}

		Composite bottomContainer = new Composite(container, SWT.NULL);
		GridLayout bottomContainerLayout = new GridLayout();
		bottomContainer.setLayout(bottomContainerLayout);
		bottomContainerLayout.numColumns = 2;

		setControl(container);
	}

	@Override
	public IWizardPage getNextPage() {
		return null;
	}

}
