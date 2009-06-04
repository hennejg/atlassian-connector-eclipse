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

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleUtil;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient.RemoteOperation;
import com.atlassian.connector.eclipse.internal.crucible.core.client.model.CrucibleCachedUser;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleImages;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.commons.CrucibleUserContentProvider;
import com.atlassian.connector.eclipse.internal.crucible.ui.commons.CrucibleUserLabelProvider;
import com.atlassian.connector.eclipse.internal.crucible.ui.dialogs.ReviewerSelectionDialog;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.CrucibleReviewChangeJob;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.CrucibleReviewEditorPage;
import com.atlassian.theplugin.commons.crucible.CrucibleServerFacade;
import com.atlassian.theplugin.commons.crucible.ValueNotYetInitialized;
import com.atlassian.theplugin.commons.crucible.api.CrucibleLoginException;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleAction;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.Reviewer;
import com.atlassian.theplugin.commons.crucible.api.model.User;
import com.atlassian.theplugin.commons.exception.ServerPasswordNotProvidedException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;
import com.atlassian.theplugin.commons.remoteapi.ServerData;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.provisional.commons.ui.CommonUiUtil;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;

import java.text.DateFormat;
import java.util.Collection;
import java.util.Set;

/**
 * The form part that displays the details of the review
 * 
 * @author Shawn Minto
 */
public class CrucibleDetailsPart extends AbstractCrucibleEditorFormPart {

	private final class SetReviewersAction extends Action {

		private final JobChangeAdapter jobChangeAdapter;

		public SetReviewersAction() {
			this(null);
		}

		public SetReviewersAction(JobChangeAdapter jobChangeAdapter) {
			this.jobChangeAdapter = jobChangeAdapter;
		}

		@Override
		public void run() {
			ReviewerSelectionDialog dialog = new ReviewerSelectionDialog(WorkbenchUtil.getShell(), crucibleReview,
					CrucibleUiUtil.getCachedUsers(crucibleReview));
			if (dialog.open() == Window.OK) {
				Set<Reviewer> reviewers = dialog.getSelectedReviewers();
				final Set<String> reviewerUserNames = CrucibleUiUtil.getUserNamesFromUsers(reviewers);

				boolean unchanged;
				try {
					unchanged = reviewers.size() == crucibleReview.getReviewers().size()
							&& reviewers.containsAll(crucibleReview.getReviewers());
				} catch (ValueNotYetInitialized e) {
					unchanged = false;
				}
				if (unchanged) {
					changedAttributes.remove(ReviewAttributeType.REVIEWERS);
				} else {
					changedAttributes.put(ReviewAttributeType.REVIEWERS, reviewers);
				}
				final TaskRepository repository = CrucibleUiUtil.getCrucibleTaskRepository(crucibleReview);
				CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Set Reviewers", repository) {
					@Override
					protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
						final MultiStatus status = new MultiStatus(CrucibleUiPlugin.PLUGIN_ID, IStatus.ERROR,
								"Failed to set reviewers", null);
						client.execute(new RemoteOperation<Object>(monitor, repository) {
							@Override
							public Object run(CrucibleServerFacade server, ServerData serverCfg,
									IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
									ServerPasswordNotProvidedException {
								try {
									server.setReviewers(serverCfg, crucibleReview.getPermId(), reviewerUserNames);
								} catch (Exception e) {
									status.add(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID,
											"Failed to set reviewers.", e));
								}
								return null;
							}
						});
						crucibleReview = client.getReview(repository, CrucibleUtil.getTaskIdFromReview(crucibleReview),
								true, monitor);
						if (status.getChildren().length > 0) {
							StatusHandler.log(status);
							crucibleEditor.getEditor()
									.setMessage("Error while setting reviewers. See error log for details.",
											IMessageProvider.ERROR);
						}
						return Status.OK_STATUS;
					}
				};
				job.schedule();
				if (jobChangeAdapter != null) {
					job.addJobChangeListener(jobChangeAdapter);
				}
			}
		}
	}

	private Review crucibleReview;

	private CrucibleReviewEditorPage crucibleEditor;

	private Composite parentComposite;

	private Composite reviewersComp;

	private Section reviewersSection;

	private IAction setReviewersAction;

	private boolean newReview;

	private Text reviewTitleText;

	private Composite reviewersPart;

	@Override
	public void initialize(CrucibleReviewEditorPage editor, Review review, boolean isNewReview) {
		this.crucibleReview = review;
		this.crucibleEditor = editor;
		this.newReview = isNewReview;
	}

	@Override
	public Collection<? extends ExpandablePart<?, ?>> getExpandableParts() {
		return null;
	}

	@Override
	public CrucibleReviewEditorPage getReviewEditor() {
		return crucibleEditor;
	}

	@Override
	public Control createControl(Composite parent, FormToolkit toolkit) {
		//CHECKSTYLE:MAGIC:OFF

		parentComposite = new Composite(parent, SWT.NONE);
		toolkit.adapt(parentComposite);
		parentComposite.setLayout(GridLayoutFactory.fillDefaults()
				.spacing(10, 10)
				.equalWidth(true)
				.numColumns(2)
				.create());
		GridDataFactory.fillDefaults().grab(true, false).applyTo(parentComposite);

		updateControl(this.crucibleReview, parent, toolkit);

		return parentComposite;
	}

	private Text createReadOnlyText(FormToolkit toolkit, Composite parent, String value, String labelString,
			boolean isMultiline) {
		return createText(toolkit, parent, value, labelString, isMultiline, true);
	}

	private Text createText(FormToolkit toolkit, Composite parent, String value, String labelString,
			boolean isMultiline, boolean isReadOnly) {

		if (labelString != null) {
			createLabelControl(toolkit, parent, labelString);
		}
		int style = SWT.FLAT;
		if (isReadOnly) {
			style |= SWT.READ_ONLY;
		} else {
			style |= SWT.BORDER;
		}
		if (isMultiline) {
			style |= SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL;
		}
		Text text = new Text(parent, style);
		text.setFont(JFaceResources.getDefaultFont());
		text.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
		text.setText(value);
		toolkit.adapt(text, true, true);

		return text;
	}

	private Label createLabelControl(FormToolkit toolkit, Composite parent, String labelString) {
		Label labelControl = toolkit.createLabel(parent, labelString);
		labelControl.setForeground(toolkit.getColors().getColor(IFormColors.TITLE));
		return labelControl;
	}

	private Control createUserComboControl(FormToolkit toolkit, Composite parent, String labelString,
			final User selectedUser, boolean readOnly, final ReviewAttributeType reviewAttributeType) {
		if (labelString != null) {
			createLabelControl(toolkit, parent, labelString);
		}

		Set<CrucibleCachedUser> users = CrucibleUiUtil.getCachedUsers(crucibleReview);

		Control control;
		if (readOnly) {
			final Text text = new Text(parent, SWT.READ_ONLY);
			text.setFont(JFaceResources.getTextFont());
			text.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
			text.setText(selectedUser.getDisplayName());
			text.setData(selectedUser);
			text.setEditable(false);
			control = text;
			toolkit.adapt(text, true, true);
		} else {
			final CCombo combo = new CCombo(parent, SWT.BORDER);
			combo.setEditable(false);
			ComboViewer comboViewer = new ComboViewer(combo);
			comboViewer.setLabelProvider(new CrucibleUserLabelProvider());
			comboViewer.setContentProvider(new CrucibleUserContentProvider());
			comboViewer.setInput(users);
			comboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
				public void selectionChanged(SelectionChangedEvent event) {
					ISelection selection = event.getSelection();
					if (selection instanceof IStructuredSelection) {
						CrucibleCachedUser user = ((CrucibleCachedUser) ((IStructuredSelection) selection).getFirstElement());
						if (user.getUserName().equals(selectedUser.getUserName())) {
							changedAttributes.remove(reviewAttributeType);
						} else {
							changedAttributes.put(reviewAttributeType, user);
						}
						crucibleEditor.attributesModified();
					}
				}
			});

			comboViewer.setSelection(new StructuredSelection(new CrucibleCachedUser(selectedUser)));
			control = combo;
		}
		control.setFont(JFaceResources.getDefaultFont());
		control.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
		toolkit.adapt(control, true, true);
		return control;
	}

	@Override
	public void updateControl(Review review, Composite parent, final FormToolkit toolkit) {
		this.crucibleReview = review;
		if (parentComposite == null) {
			createControl(parent, toolkit);
		}

		for (Control c : parentComposite.getChildren()) {
			c.dispose();
			//TODO disposing not necessary, simply updating labels and a re-layout should be sufficient; low priority though
		}
		parentComposite.setMenu(null);

		boolean hasModifyFilesAction = false;
		try {
			Set<CrucibleAction> actions = crucibleReview.getActions();
			if (actions != null && actions.contains(CrucibleAction.MODIFY_FILES)) {
				hasModifyFilesAction = true;
			}
		} catch (ValueNotYetInitialized e) {
			StatusHandler.log(new Status(IStatus.WARNING, CrucibleUiPlugin.PLUGIN_ID,
					"Could not retrieve available Crucible actions", e));
		}

		reviewTitleText = createText(toolkit, parentComposite, crucibleReview.getName(), null, false, !newReview);
		reviewTitleText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				String modifiedName = ((Text) e.widget).getText();
				if (modifiedName.equals(crucibleReview.getName())) {
					changedAttributes.remove(ReviewAttributeType.TITLE);
				} else {
					changedAttributes.put(ReviewAttributeType.TITLE, modifiedName);
				}
				crucibleEditor.attributesModified();
			}
		});
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(reviewTitleText);

		Composite statusComp = toolkit.createComposite(parentComposite);
		statusComp.setLayout(GridLayoutFactory.fillDefaults().numColumns(4).spacing(10, 0).create());
		Text stateText = createReadOnlyText(toolkit, statusComp, crucibleReview.getState().getDisplayName(), "State: ",
				false);
		GridDataFactory.fillDefaults().applyTo(stateText);

		Text openSinceText = createReadOnlyText(toolkit, statusComp, DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
				DateFormat.SHORT).format(crucibleReview.getCreateDate()), "Open Since: ", false);
		GridDataFactory.fillDefaults().applyTo(openSinceText);

		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(statusComp);

		reviewersSection = toolkit.createSection(parentComposite, ExpandableComposite.TWISTIE
				| ExpandableComposite.TITLE_BAR | ExpandableComposite.EXPANDED);
		reviewersSection.setText("Participants");
		final Composite participantsComp = toolkit.createComposite(reviewersSection);

		Control authorControl = createUserComboControl(toolkit, participantsComp, "Author: ",
				crucibleReview.getAuthor(), !newReview, ReviewAttributeType.AUTHOR);
		GridDataFactory.fillDefaults().grab(false, false).align(SWT.BEGINNING, SWT.TOP).applyTo(authorControl);

		Control moderatorControl = createUserComboControl(toolkit, participantsComp, "Moderator: ",
				crucibleReview.getModerator(), !newReview, ReviewAttributeType.MODERATOR);
		GridDataFactory.fillDefaults().grab(false, false).align(SWT.BEGINNING, SWT.TOP).applyTo(moderatorControl);

		Composite reviewersPartComp = toolkit.createComposite(participantsComp);
		reviewersPartComp.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).spacing(15, 0).create());
		GridDataFactory.fillDefaults().span(2, 1).grab(true, true).applyTo(reviewersPartComp);

		createReviewersPart(toolkit, reviewersPartComp, hasModifyFilesAction);

		GridDataFactory.fillDefaults().grab(true, false).applyTo(reviewersSection);
		participantsComp.setLayout(GridLayoutFactory.fillDefaults().margins(2, 2).numColumns(2).create());
		GridDataFactory.fillDefaults().grab(true, false).hint(250, SWT.DEFAULT).applyTo(participantsComp);
		reviewersSection.setClient(participantsComp);

		Section objectivesSection = toolkit.createSection(parentComposite, ExpandableComposite.TWISTIE
				| ExpandableComposite.TITLE_BAR | ExpandableComposite.EXPANDED);
		objectivesSection.setText("Statement of Objectives");
		Composite objectivesComp = toolkit.createComposite(objectivesSection);
		Text descriptionText = createText(toolkit, objectivesComp, crucibleReview.getDescription(), null, true,
				!newReview);
		descriptionText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				String modifiedName = ((Text) e.widget).getText();
				if (modifiedName.equals(crucibleReview.getDescription())) {
					changedAttributes.remove(ReviewAttributeType.OBJECTIVE);
				} else {
					changedAttributes.put(ReviewAttributeType.OBJECTIVE, modifiedName);
				}
				crucibleEditor.attributesModified();
			}
		});
		GridDataFactory.fillDefaults().grab(true, false).applyTo(objectivesSection);
		objectivesComp.setLayout(GridLayoutFactory.fillDefaults().margins(2, 2).numColumns(1).create());
		GridDataFactory.fillDefaults().grab(true, true).hint(250, -1).applyTo(descriptionText);
		objectivesSection.setClient(objectivesComp);
		//CHECKSTYLE:MAGIC:ON

		toolkit.paintBordersFor(parentComposite);
	}

	private void createReviewersPart(final FormToolkit toolkit, final Composite parent, boolean canEditReviewers) {
		if (reviewersComp == null || reviewersComp.isDisposed()) {
			reviewersComp = toolkit.createComposite(parent);
			reviewersComp.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).spacing(15, 0).create());
			GridDataFactory.fillDefaults().grab(true, true).applyTo(reviewersComp);
		}

		try {
			if (canEditReviewers) {
				setReviewersAction = new SetReviewersAction();
				setReviewersAction.setToolTipText("Add/Remove Reviewers");
				setReviewersAction.setImageDescriptor(CrucibleImages.SET_REVIEWERS);
			}

			Set<Reviewer> reviewers = crucibleReview.getReviewers();
			CrucibleReviewersPart crucibleReviewersPart = new CrucibleReviewersPart(reviewers);
			crucibleReviewersPart.setMenu(parent.getMenu());
			reviewersPart = crucibleReviewersPart.createControl(toolkit, reviewersComp, setReviewersAction);

		} catch (ValueNotYetInitialized e) {
			StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, e.getMessage(), e));
		}
	}

	@Override
	public void dispose() {
		if (reviewersPart != null && !reviewersPart.isDisposed()) {
			CommonUiUtil.setMenu(reviewersPart, null);
			reviewersPart.dispose();
		}

		super.dispose();
	}

	@Override
	public void setFocus() {
		reviewTitleText.setFocus();
	}
}
