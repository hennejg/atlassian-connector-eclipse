/*******************************************************************************
 * Copyright (c) 2006 - 2006 Mylar eclipse.org project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mylar project committers - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.ui.wizards;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.mylar.internal.jira.core.model.NamedFilter;
import org.eclipse.mylar.internal.jira.core.model.filter.FilterDefinition;
import org.eclipse.mylar.internal.jira.core.service.JiraServer;
import org.eclipse.mylar.internal.jira.ui.JiraCustomQuery;
import org.eclipse.mylar.internal.jira.ui.JiraRepositoryQuery;
import org.eclipse.mylar.internal.jira.ui.JiraServerFacade;
import org.eclipse.mylar.tasks.core.AbstractRepositoryQuery;
import org.eclipse.mylar.tasks.core.TaskRepository;
import org.eclipse.mylar.tasks.ui.TasksUiPlugin;
import org.eclipse.mylar.tasks.ui.search.AbstractRepositoryQueryPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;

/**
 * Wizard page that allows the user to select a named Jira filter they have
 * defined on the server.
 * 
 * @author Mik Kersten
 * @author Wesley Coelho (initial integration patch)
 * @author Eugene Kuleshov (layout and other improvements)
 */
public class JiraQueryWizardPage extends AbstractRepositoryQueryPage {

	private static final String TITLE = "New Jira Query";

	private static final String DESCRIPTION = "Please select a query type.";

	private static final String WAIT_MESSAGE = "Downloading...";

	private static final String JOB_LABEL = "Downloading Filter Names";

	private NamedFilter[] filters = null;

	private List filterCombo;

	private Button updateButton = null;

	private Button buttonCustom;

	private Button buttonSaved;

	private JiraQueryPage filterSummaryPage;

	private AbstractRepositoryQuery query;

	public JiraQueryWizardPage(TaskRepository repository) {
		this(repository, null);
	}

	public JiraQueryWizardPage(TaskRepository repository, AbstractRepositoryQuery query) {
		super(TITLE);
		this.repository = repository;
		this.query = query;
		setTitle(TITLE);
		setDescription(DESCRIPTION);
		setPageComplete(false);
	}

	@Override
	public void createControl(Composite parent) {
		boolean isCustom = query==null || query instanceof JiraCustomQuery;
		boolean isRepository = query instanceof JiraRepositoryQuery;

		final Composite innerComposite = new Composite(parent, SWT.NONE);
		innerComposite.setLayoutData(new GridData());
		GridLayout gl = new GridLayout();
		gl.numColumns = 2;
		innerComposite.setLayout(gl);

		buttonCustom = new Button(innerComposite, SWT.RADIO);
		buttonCustom.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		buttonCustom.setText("&Create query using form");
		buttonCustom.setSelection(isCustom);

		buttonSaved = new Button(innerComposite, SWT.RADIO);
		buttonSaved.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		buttonSaved.setText("Use saved &filter from the repository");
		buttonSaved.setSelection(isRepository);
		
		buttonSaved.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean selection = buttonSaved.getSelection();
				filterCombo.setEnabled(selection);
				updateButton.setEnabled(selection);
				setPageComplete(selection);
			}
		});

		filterCombo = new List(innerComposite, SWT.V_SCROLL | SWT.BORDER);
		filterCombo.add(WAIT_MESSAGE);
		filterCombo.deselectAll();
		
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		data.horizontalIndent = 15;
		filterCombo.setLayoutData(data);
		filterCombo.setEnabled(false);

		updateButton = new Button(innerComposite, SWT.LEFT | SWT.PUSH);
		final GridData gridData = new GridData(SWT.FILL, SWT.TOP, false, true);
		updateButton.setLayoutData(gridData);
		updateButton.setText("Update from &Repository");
		updateButton.setEnabled(isRepository);
		updateButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				filterCombo.setEnabled(false);
				filterCombo.removeAll();
				filterCombo.add(WAIT_MESSAGE);
				filterCombo.deselectAll();
				// FIXME run in job
				JiraServerFacade.getDefault().refreshServerSettings(repository, new NullProgressMonitor());
				downloadFilters();
			}

		});

		setControl(innerComposite);
		downloadFilters();
	}
	
	@Override
	public boolean isPageComplete() {
		return buttonCustom.getSelection() ? super.isPageComplete() : filterCombo.getSelectionCount()==1;
	}

	@Override
	public IWizardPage getNextPage() {
		if (!buttonCustom.getSelection()) {
			return null;
		}
		if (filterSummaryPage == null) {
			FilterDefinition workingCopy;
			boolean isNew;
			if(query instanceof JiraCustomQuery) {
				JiraServer jiraServer = JiraServerFacade.getDefault().getJiraServer(repository);
				workingCopy = ((JiraCustomQuery) query).getFilterDefinition(jiraServer);
				isNew = false;
			} else {
				workingCopy = new FilterDefinition();
				isNew = true;
			}

			filterSummaryPage = new JiraQueryPage(repository, workingCopy, isNew, true);
			filterSummaryPage.setWizard(getWizard());
		}
		return filterSummaryPage;
	}

	@Override
	public boolean canFlipToNextPage() {
		return buttonCustom.getSelection();
	}

	protected void downloadFilters() {
		Job job = new Job(JOB_LABEL) {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				try {
					JiraServer jiraServer = JiraServerFacade.getDefault().getJiraServer(repository);
					filters = jiraServer.getNamedFilters();

					monitor.worked(1);
					monitor.done();
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							if (!filterCombo.isDisposed()) {
								displayFilters(filters);
							}
						}
					});
				} catch (Exception e) {
					JiraServerFacade.handleConnectionException(e);
					return Status.CANCEL_STATUS;
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	/** Called by the download job when the filters have been downloaded */
	public void displayFilters(NamedFilter[] filters) {
		filterCombo.removeAll();

		if (filters.length == 0) {
			filterCombo.setEnabled(false);
			filterCombo.add("No filters found");
			filterCombo.deselectAll();
			
			setMessage("No saved filters found. Please create filters using JIRA web interface or" +
					" follow to the next page to create custom query.", IMessageProvider.WARNING);
			setPageComplete(false);
			return;
		}

		String id = null;
		if(query instanceof JiraRepositoryQuery) {
			id = ((JiraRepositoryQuery) query).getNamedFilter().getId();
		}

		int n = 0;
		for (int i = 0; i < filters.length; i++) {
			filterCombo.add(filters[i].getName());
			if(filters[i].getId().equals(id)) {
				n = i;
			}
		}

		filterCombo.select(n);
		filterCombo.showSelection();
		filterCombo.setEnabled(true);
		setPageComplete(true);
	}

	/** Returns the filter selected by the user or null on failure */
	private NamedFilter getSelectedFilter() {
		if (filters != null && filters.length > 0) {
			return filters[filterCombo.getSelectionIndex()];
		}
		return null;
	}

	@Override
	public AbstractRepositoryQuery getQuery() {
		if (buttonSaved.getSelection()) {
			return new JiraRepositoryQuery(repository.getUrl(), getSelectedFilter(), TasksUiPlugin
					.getTaskListManager().getTaskList());
		}

		if (filterSummaryPage != null) {
			return filterSummaryPage.getQuery();
		}

		return null;
	}

}
