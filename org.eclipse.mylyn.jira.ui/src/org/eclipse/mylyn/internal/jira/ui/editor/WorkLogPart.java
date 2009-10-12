/*******************************************************************************
 * Copyright (c) 2004, 2009 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.ui.editor;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.mylyn.internal.jira.core.WorkLogConverter;
import org.eclipse.mylyn.internal.jira.core.model.JiraWorkLog;
import org.eclipse.mylyn.internal.jira.core.service.JiraTimeFormat;
import org.eclipse.mylyn.internal.jira.core.util.JiraUtil;
import org.eclipse.mylyn.internal.jira.ui.JiraConnectorUi;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPart;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;

/**
 * @author Steffen Pingel
 * @author Thomas Ehrnhoefer
 */
public class WorkLogPart extends AbstractTaskEditorPart {

	private static final String ID_POPUP_MENU = "org.eclipse.mylyn.jira.ui.editor.menu.worklog"; //$NON-NLS-1$

	private final String[] columns = { Messages.WorkLogPart_Creator, Messages.WorkLogPart_Date,
			Messages.WorkLogPart_Worked, Messages.WorkLogPart_Description };

	private final int[] columnWidths = { 130, 70, 100, 150 };

	private List<TaskAttribute> logEntries;

	private boolean hasIncoming;

	private MenuManager menuManager;

	private Composite composite;

	private long newWorkDoneAmount = 0;

	private Calendar newWorkDoneDate = new GregorianCalendar();

	private String newWorkDoneDescription = ""; //$NON-NLS-1$

	private boolean newWorkDoneAutoAdjust = true;

	private DateTime dateWidget;

	private DateTime timeWidget;

	private Text timeSpentText;

	private boolean includeWorklog;

	private JiraWorkLog newWorkLog;

	private Section newWorkLogSection;

	public WorkLogPart() {
		setPartName(Messages.WorkLogPart_Work_Log);
	}

	private void createTable(FormToolkit toolkit, final Composite composite) {
		Table table = toolkit.createTable(composite, SWT.MULTI | SWT.FULL_SELECTION);
		table.setLinesVisible(true);
		table.setHeaderVisible(true);
		table.setLayout(new GridLayout());
		GridDataFactory.fillDefaults()
				.align(SWT.FILL, SWT.FILL)
				.grab(true, false)
				.hint(500, SWT.DEFAULT)
				.applyTo(table);

		for (int i = 0; i < columns.length; i++) {
			TableColumn column = new TableColumn(table, SWT.LEFT, i);
			column.setText(columns[i]);
			column.setWidth(columnWidths[i]);
		}
		table.getColumn(2).setAlignment(SWT.RIGHT);

		TableViewer attachmentsViewer = new TableViewer(table);
		attachmentsViewer.setUseHashlookup(true);
		attachmentsViewer.setColumnProperties(columns);
		ColumnViewerToolTipSupport.enableFor(attachmentsViewer, ToolTip.NO_RECREATE);

		attachmentsViewer.setSorter(new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				JiraWorkLog item1 = (JiraWorkLog) e1;
				JiraWorkLog item2 = (JiraWorkLog) e2;
				Date created1 = item1.getCreated();
				Date created2 = item2.getCreated();
				if (created1 != null && created2 != null) {
					return created1.compareTo(created2);
				} else if (created1 == null && created2 != null) {
					return -1;
				} else if (created1 != null && created2 == null) {
					return 1;
				} else {
					return 0;
				}
			}
		});

		List<JiraWorkLog> workLogList = new ArrayList<JiraWorkLog>(logEntries.size());
		for (TaskAttribute attribute : logEntries) {
			JiraWorkLog log = new WorkLogConverter().createFrom(attribute);
			workLogList.add(log);
		}
		attachmentsViewer.setContentProvider(new ArrayContentProvider());
		attachmentsViewer.setLabelProvider(new WorkLogTableLabelProvider(getJiraTimeFormat()));
		attachmentsViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				TasksUiUtil.openUrl(JiraConnectorUi.getTaskWorkLogUrl(getModel().getTaskRepository(),
						getModel().getTask()));
			}
		});
		attachmentsViewer.addSelectionChangedListener(getTaskEditorPage());
		attachmentsViewer.setInput(workLogList);

		menuManager = new MenuManager();
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				// TODO provide popup menu
			}
		});
		getTaskEditorPage().getEditorSite().registerContextMenu(ID_POPUP_MENU, menuManager, attachmentsViewer, false);
		Menu menu = menuManager.createContextMenu(table);
		table.setMenu(menu);
	}

	@Override
	public void createControl(Composite parent, final FormToolkit toolkit) {
		initialize();

		int style = ExpandableComposite.SHORT_TITLE_BAR | ExpandableComposite.TWISTIE;
		if (hasIncoming) {
			style |= ExpandableComposite.EXPANDED;
		}
		final Section section = createSection(parent, toolkit, style);
		section.setText(getPartName() + " (" + logEntries.size() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		if (hasIncoming) {
			expandSection(toolkit, section);
		} else {
			section.addExpansionListener(new ExpansionAdapter() {
				@Override
				public void expansionStateChanged(ExpansionEvent event) {
					if (composite == null) {
						expandSection(toolkit, section);
						getTaskEditorPage().reflow();
					}
				}
			});
		}
		setSection(toolkit, section);
	}

	private void expandSection(FormToolkit toolkit, Section section) {
		composite = toolkit.createComposite(section);
		composite.setLayout(new GridLayout(1, false));
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		getTaskEditorPage().registerDefaultDropListener(section);

		if (logEntries.size() > 0) {
			createTable(toolkit, composite);
		} else {
			Label label = toolkit.createLabel(composite, Messages.WorkLogPart_No_work_logged);
			getTaskEditorPage().registerDefaultDropListener(label);
		}
		newWorkLogSection = createNewWorkLogSection(toolkit, composite);
		newWorkLogSection.addExpansionListener(new ExpansionAdapter() {
			private boolean firstTime = true;

			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				if (firstTime) {
					firstTime = false;
				} else {
					setTimeSpendDecorator();
				}
			}
		});
		GridDataFactory.fillDefaults().grab(true, false).applyTo(newWorkLogSection);
		section.setClient(composite);

		toolkit.paintBordersFor(composite);
	}

	@Override
	public void dispose() {
		if (menuManager != null) {
			menuManager.dispose();
		}
		super.dispose();
	}

	private void initialize() {
		logEntries = getTaskData().getAttributeMapper().getAttributesByType(getTaskData(),
				WorkLogConverter.TYPE_WORKLOG);
		for (TaskAttribute attachmentAttribute : logEntries) {
			if (getModel().hasIncomingChanges(attachmentAttribute)) {
				hasIncoming = true;
				break;
			}
		}

		TaskAttribute newWorkLogAttribute = getTaskData().getRoot()
				.getAttribute(WorkLogConverter.ATTRIBUTE_WORKLOG_NEW);
		JiraWorkLog log = null;
		if (newWorkLogAttribute != null) {
			log = new WorkLogConverter().createFrom(newWorkLogAttribute);
		}

		if (newWorkLogAttribute != null && log != null) {
			newWorkDoneAmount = log.getTimeSpent();
			newWorkDoneDate = new GregorianCalendar();
			if (log.getStartDate() != null) {
				newWorkDoneDate.setTime(log.getStartDate());
			}
			newWorkDoneDescription = log.getComment();
			newWorkDoneAutoAdjust = log.isAutoAdjustEstimate();
			TaskAttribute newWorkLogSubmitAttribute = newWorkLogAttribute.getAttribute(WorkLogConverter.ATTRIBUTE_WORKLOG_NEW_SUBMIT_FLAG);
			if (newWorkLogSubmitAttribute != null && newWorkLogSubmitAttribute.getValue().equals(String.valueOf(true))) {
				includeWorklog = true;
			}
		}
	}

	private JiraTimeFormat getJiraTimeFormat() {
		return JiraUtil.getTimeFormat(getTaskEditorPage().getTaskRepository());
	}

	private Section createNewWorkLogSection(FormToolkit toolkit, Composite parent) {
		final Section newWorkLogSection = toolkit.createSection(parent, ExpandableComposite.LEFT_TEXT_CLIENT_ALIGNMENT
				| ExpandableComposite.TWISTIE | ExpandableComposite.CLIENT_INDENT);
		newWorkLogSection.setText(""); //$NON-NLS-1$

		final Button createNewWorkLog = toolkit.createButton(newWorkLogSection, Messages.WorkLogPart_Log_Work_Done,
				SWT.CHECK);
		GridDataFactory.fillDefaults().grab(true, false).align(SWT.LEFT, SWT.CENTER).applyTo(createNewWorkLog);
		createNewWorkLog.setSelection(includeWorklog);
		newWorkLogSection.setExpanded(includeWorklog);
		createNewWorkLog.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.getSource().equals(createNewWorkLog)) {
					newWorkLogSection.setExpanded(createNewWorkLog.getSelection());
					addWorkLogToModel();
					includeWorklog = createNewWorkLog.getSelection();
					setSubmitWorklog();
				}
			}
		});
		newWorkLogSection.setTextClient(createNewWorkLog);

		Composite newWorkLogComposite = toolkit.createComposite(newWorkLogSection, SWT.NONE);
		newWorkLogSection.setClient(newWorkLogComposite);
		newWorkLogComposite.setLayout(GridLayoutFactory.swtDefaults().spacing(10, 5).numColumns(3).create());

		toolkit.createLabel(newWorkLogComposite, Messages.WorkLogPart_Time_Spent);
		timeSpentText = toolkit.createText(newWorkLogComposite, getJiraTimeFormat().format(new Long(newWorkDoneAmount)));
		timeSpentText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				setTimeSpendDecorator();

				addWorkLogToModel();
			}
		});
		timeSpentText.setToolTipText(NLS.bind(Messages.WorkLogPart_Time_Spent_Explanation_Tooltip,
				JiraUtil.getWorkDaysPerWeek(getTaskEditorPage().getTaskRepository()),
				JiraUtil.getWorkHoursPerDay(getTaskEditorPage().getTaskRepository())));
		GridDataFactory.fillDefaults().span(2, 1).hint(135, SWT.DEFAULT).align(SWT.BEGINNING, SWT.FILL).applyTo(
				timeSpentText);

		toolkit.createLabel(newWorkLogComposite, Messages.WorkLogPart_Start_Date);
		dateWidget = new DateTime(newWorkLogComposite, SWT.DATE);
		dateWidget.setYear(newWorkDoneDate.get(Calendar.YEAR));
		dateWidget.setMonth(newWorkDoneDate.get(Calendar.MONTH));
		dateWidget.setDay(newWorkDoneDate.get(Calendar.DAY_OF_MONTH));
		dateWidget.setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TEXT_BORDER);
		toolkit.adapt(dateWidget, true, true);
		GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.FILL).applyTo(dateWidget);
		dateWidget.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				collectDate();
				addWorkLogToModel();
			}
		});

		timeWidget = new DateTime(newWorkLogComposite, SWT.TIME);
		timeWidget.setHours(newWorkDoneDate.get(Calendar.HOUR_OF_DAY));
		timeWidget.setMinutes(newWorkDoneDate.get(Calendar.MINUTE));
		timeWidget.setSeconds(newWorkDoneDate.get(Calendar.SECOND));
		timeWidget.setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TEXT_BORDER);
		toolkit.adapt(timeWidget, true, true);
		GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.FILL).applyTo(timeWidget);
		timeWidget.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				collectDate();
				addWorkLogToModel();
			}
		});

		toolkit.createLabel(newWorkLogComposite, Messages.WorkLogPart_Adjust_Estimate);
		Composite adjustComposite = toolkit.createComposite(newWorkLogComposite);
		adjustComposite.setLayout(GridLayoutFactory.fillDefaults().spacing(0, 0).create());
		GridDataFactory.fillDefaults().span(2, 1).applyTo(adjustComposite);
		final Button autoAdjustButton = toolkit.createButton(adjustComposite, Messages.WorkLogPart_Auto_Adjust,
				SWT.RADIO);
		autoAdjustButton.setSelection(newWorkDoneAutoAdjust);
		autoAdjustButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				newWorkDoneAutoAdjust = autoAdjustButton.getSelection();
				addWorkLogToModel();
			}
		});
		autoAdjustButton.setToolTipText(Messages.WorkLogPart_Auto_Adjust_Explanation_Tooltip);
		Button leaveAdjustButton = toolkit.createButton(adjustComposite, Messages.WorkLogPart_Leave_Existing_Estimate,
				SWT.RADIO);
		leaveAdjustButton.setSelection(!newWorkDoneAutoAdjust);
		leaveAdjustButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				newWorkDoneAutoAdjust = autoAdjustButton.getSelection();
				addWorkLogToModel();
			}
		});
		leaveAdjustButton.setToolTipText(Messages.WorkLogPart_Leave_Existing_Explanation_Tooltip);

		toolkit.createLabel(newWorkLogComposite, Messages.WorkLogPart_Work_Description);
		final Text descriptionText = toolkit.createText(newWorkLogComposite, newWorkDoneDescription, SWT.WRAP
				| SWT.MULTI | SWT.V_SCROLL);
		descriptionText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				newWorkDoneDescription = descriptionText.getText();
				addWorkLogToModel();
			}
		});
		GridDataFactory.fillDefaults().grab(true, true).hint(150, 100).span(2, 1).applyTo(descriptionText);

		toolkit.paintBordersFor(newWorkLogComposite);
		return newWorkLogSection;
	}

	protected void collectDate() {
		if (newWorkDoneDate == null) {
			newWorkDoneDate = new GregorianCalendar();
		}
		newWorkDoneDate.set(dateWidget.getYear(), dateWidget.getMonth(), dateWidget.getDay(), timeWidget.getHours(),
				timeWidget.getMinutes(), timeWidget.getSeconds());
	}

	/**
	 * Updates the worklog and returns true if it has changed
	 */
	private boolean updateNewWorkLog() {
		JiraWorkLog tempworkLog = new JiraWorkLog();
		tempworkLog.setAuthor(getTaskEditorPage().getTaskRepository().getUserName());
		tempworkLog.setComment(newWorkDoneDescription);
		tempworkLog.setStartDate(newWorkDoneDate.getTime());
		tempworkLog.setTimeSpent(newWorkDoneAmount);
		tempworkLog.setAutoAdjustEstimate(newWorkDoneAutoAdjust);
		if (tempworkLog.equals(newWorkLog)) {
			return false;
		}
		newWorkLog = tempworkLog;
		return true;
	}

	private void addWorkLogToModel() {
		if (updateNewWorkLog()) {
			TaskAttribute attribute = getTaskData().getRoot().getAttribute(WorkLogConverter.ATTRIBUTE_WORKLOG_NEW);
			if (attribute == null) {
				attribute = getTaskData().getRoot().createAttribute(WorkLogConverter.ATTRIBUTE_WORKLOG_NEW);
			}
			new WorkLogConverter().applyTo(newWorkLog, attribute);
			getModel().attributeChanged(attribute);
		}
	}

	private void setSubmitWorklog() {
		TaskAttribute attribute = getTaskData().getRoot().getAttribute(WorkLogConverter.ATTRIBUTE_WORKLOG_NEW);
		if (attribute != null) {
			attribute.createAttribute(WorkLogConverter.ATTRIBUTE_WORKLOG_NEW_SUBMIT_FLAG).setValue(
					String.valueOf(includeWorklog));
			getModel().attributeChanged(attribute);
		}
	}

	private void setTimeSpendDecorator() {
		try {
			newWorkDoneAmount = getJiraTimeFormat().parse(timeSpentText.getText());
		} catch (ParseException e) {
			//ignore
		}
		JiraEditorUtil.setTimeSpentDecorator(timeSpentText, false, getTaskEditorPage().getTaskRepository());
	}
}
