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

package com.atlassian.connector.eclipse.internal.crucible.ui.editor;

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleConstants;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleCorePlugin;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleUtil;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient.RemoteOperation;
import com.atlassian.connector.eclipse.internal.crucible.core.client.model.IReviewCacheListener;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleImages;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.actions.SummarizeReviewAction;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.AbstractCrucibleEditorFormPart;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.CrucibleDetailsPart;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.CrucibleGeneralCommentsPart;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.CrucibleReviewFilesPart;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts.ExpandablePart;
import com.atlassian.connector.eclipse.ui.forms.IReflowRespectingPart;
import com.atlassian.theplugin.commons.cfg.CrucibleServerCfg;
import com.atlassian.theplugin.commons.crucible.CrucibleServerFacade;
import com.atlassian.theplugin.commons.crucible.ValueNotYetInitialized;
import com.atlassian.theplugin.commons.crucible.api.CrucibleLoginException;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleAction;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.PermIdBean;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.VersionedComment;
import com.atlassian.theplugin.commons.crucible.api.model.notification.CrucibleNotification;
import com.atlassian.theplugin.commons.exception.ServerPasswordNotProvidedException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.provisional.commons.ui.CommonImages;
import org.eclipse.mylyn.internal.provisional.commons.ui.CommonThemes;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.internal.tasks.ui.editors.EditorUtil;
import org.eclipse.mylyn.internal.tasks.ui.util.SelectionProviderAdapter;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskFormPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.themes.IThemeManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The rich editor for crucible reviews
 * 
 * @author Shawn Minto
 * @author Thomas Ehrnhoefer
 */
public class CrucibleReviewEditorPage extends TaskFormPage implements IReflowRespectingPart {

	private static final String INITIALIZATION_FAILED_MESSAGE = "Unable to retrieve Review. {0} {1}";

	/**
	 * Causes the form page to reflow on resize.
	 */
	private final class ParentResizeHandler implements Listener {
		private static final int REFLOW_TIMER_DELAY = 300;

		private int generation;

		public void handleEvent(Event event) {
			++generation;

			Display.getCurrent().timerExec(REFLOW_TIMER_DELAY, new Runnable() {
				private final int scheduledGeneration = generation;

				public void run() {
					if (getManagedForm().getForm().isDisposed()) {
						return;
					}

					// only reflow if this is the latest generation to prevent
					// unnecessary reflows while the form is being resized
					if (scheduledGeneration == generation) {
						getManagedForm().reflow(true);
					}
				}
			});
		}
	}

	private final IReviewCacheListener reviewCacheListener = new IReviewCacheListener() {

		public void reviewAdded(String repositoryUrl, String taskId, Review addedReview) {
			// ignore
		}

		public void reviewUpdated(String repositoryUrl, String taskId, Review updatedReview,
				List<CrucibleNotification> differences) {

			if (getTask() != null) {
				ITask task = getTask();
				if (task.getRepositoryUrl().equals(repositoryUrl) && task.getTaskId().equals(taskId)) {
					PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
						public void run() {
							downloadReviewAndRefresh(0, false);
						}
					});
				}
			}
		}

	};

	private static final int OPEN_DOWNLOAD_DELAY = 0;

	private static final int VERTICAL_BAR_WIDTH = 15;

	private FormToolkit toolkit;

	private ScrolledForm form;

	private boolean reflow;

	protected Review review;

	private Composite editorComposite;

	private final List<AbstractCrucibleEditorFormPart> parts;

	private ISelectionProvider selectionProviderAdapter;

	private Control highlightedControl;

	private CrucibleFileInfo selectedCrucibleFile;

	private VersionedComment selectedComment;

	private Color selectionColor;

	private final Color colorIncoming;

	private Label initiaizingLabel;

	public CrucibleReviewEditorPage(FormEditor editor, String title) {
		super(editor, CrucibleConstants.CRUCIBLE_EDITOR_PAGE_ID, title);
		parts = new ArrayList<AbstractCrucibleEditorFormPart>();
		IThemeManager themeManager = PlatformUI.getWorkbench().getThemeManager();
		colorIncoming = themeManager.getCurrentTheme().getColorRegistry().get(CommonThemes.COLOR_INCOMING_BACKGROUND);
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) {
		super.init(site, input);

		selectionProviderAdapter = new SelectionProviderAdapter();
		selectionProviderAdapter.setSelection(new StructuredSelection(getTask()));
		site.setSelectionProvider(selectionProviderAdapter);
		CrucibleCorePlugin.getDefault().getReviewCache().addCacheChangedListener(reviewCacheListener);
	}

	@Override
	public void dispose() {
		CrucibleCorePlugin.getDefault().getReviewCache().removeCacheChangedListener(reviewCacheListener);
		super.dispose();
		editorComposite = null;
		toolkit = null;
		selectionColor.dispose();
		selectionColor = null;
	}

	@Override
	public TaskEditor getEditor() {
		return (TaskEditor) super.getEditor();
	}

	@Override
	protected void createFormContent(final IManagedForm managedForm) {

		form = managedForm.getForm();

		toolkit = managedForm.getToolkit();

		selectionColor = new Color(getSite().getShell().getDisplay(), 255, 231, 198);

		EditorUtil.disableScrollingOnFocus(form);

		try {
			setReflow(false);

			editorComposite = form.getBody();
			// TODO consider using TableWrapLayout, it makes resizing much faster
			GridLayout editorLayout = new GridLayout();
			editorComposite.setLayout(editorLayout);
			editorComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

			editorComposite.setMenu(getEditor().getMenu());

			Composite createComposite = toolkit.createComposite(editorComposite);
			createComposite.setLayout(new GridLayout());
			GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER).applyTo(createComposite);

			initiaizingLabel = toolkit.createLabel(createComposite, "Initializing review editor...");
			initiaizingLabel.setFont(JFaceResources.getBannerFont());

			GridDataFactory.fillDefaults().grab(true, false).align(SWT.CENTER, SWT.CENTER).applyTo(initiaizingLabel);

			Display.getCurrent().asyncExec(new Runnable() {

				public void run() {
					downloadReviewAndRefresh(OPEN_DOWNLOAD_DELAY, false);
				}

			});

		} finally {
			setReflow(true);
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		parent.addListener(SWT.Resize, new ParentResizeHandler());
		super.createPartControl(parent);
	}

	TaskRepository getTaskRepository() {
		return getEditor().getTaskEditorInput().getTaskRepository();
	}

	public ITask getTask() {
		return getEditor().getTaskEditorInput().getTask();
	}

	private void downloadReviewAndRefresh(long delay, final boolean force) {
		if (initiaizingLabel != null && !initiaizingLabel.isDisposed()) {
			initiaizingLabel.setText("Initializing review editor...");
		}
		CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Retrieving Crucible Review "
				+ getTask().getTaskKey(), getTaskRepository()) {
			@Override
			protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {

				// TODO clean this up
				Review cachedReview = CrucibleCorePlugin.getDefault().getReviewCache().getWorkingCopyReview(
						getTask().getRepositoryUrl(), getTask().getTaskId());

				if (cachedReview == null || force) {

					review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
					return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, null);
				} else {
					review = cachedReview;
					return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, null);
				}
			}
		};
		schedule(job, delay, force);

	}

	private synchronized void updateFormContent() {
		if (editorComposite == null) {
			return;
		}

		assert (review != null);

		if (parts == null || parts.size() == 0) {
			createInitialFormContent();
		} else {

			try {

				for (Control child : editorComposite.getChildren()) {
					child.setMenu(null);
					if (child instanceof Composite) {
						setMenu((Composite) child, null);
					}
				}

				setReflow(false);

				for (AbstractCrucibleEditorFormPart part : parts) {
					part.updateControl(review, editorComposite, toolkit);
				}

				setMenu(editorComposite, editorComposite.getMenu());

				if (selectedComment != null && selectedCrucibleFile != null) {
					selectAndReveal(selectedCrucibleFile, selectedComment);
				}

			} finally {
				setReflow(true);
				reflow(true);
			}
		}
	}

	private void createInitialFormContent() {
		if (editorComposite == null) {
			return;
		}

		assert (review != null);
		try {

			setReflow(false);
			clearFormContent();

			createFormParts();

			for (AbstractCrucibleEditorFormPart part : parts) {
				getManagedForm().addPart(part);
				part.initialize(this, review);
				part.createControl(editorComposite, toolkit);
			}

			setMenu(editorComposite, editorComposite.getMenu());

			if (selectedComment != null && selectedCrucibleFile != null) {
				selectAndReveal(selectedCrucibleFile, selectedComment);
			}

		} finally {
			setReflow(true);
			reflow(false);
		}

	}

	public Menu getMenu() {
		if (editorComposite != null) {
			return editorComposite.getMenu();
		}
		return null;
	}

	public void setMenu(Composite composite, Menu menu) {
		if (!composite.isDisposed()) {
			composite.setMenu(menu);
			for (Control child : composite.getChildren()) {
				child.setMenu(menu);
				if (child instanceof Composite) {
					setMenu((Composite) child, menu);
				}
			}
		}
	}

	private void createFormParts() {
		parts.add(new CrucibleDetailsPart());
		parts.add(new CrucibleGeneralCommentsPart());
		parts.add(new CrucibleReviewFilesPart());
	}

	private void clearFormContent() {

		for (AbstractCrucibleEditorFormPart part : parts) {
			getManagedForm().removePart(part);
		}

		parts.clear();

		highlightedControl = null;

		Menu menu = editorComposite.getMenu();
		// preserve context menu
		EditorUtil.setMenu(editorComposite, null);

		// remove all of the old widgets so that we can redraw the editor
		for (Control child : editorComposite.getChildren()) {
			child.dispose();
		}

		editorComposite.setMenu(menu);

		// FIXME dispose parts
	}

	private void setBusy(boolean busy) {
		getEditor().showBusy(busy);
	}

	public void setReflow(boolean reflow) {
		this.reflow = reflow;
		form.setRedraw(reflow);
	}

	/**
	 * Force a re-layout of entire form.
	 */
	public void reflow(boolean all) {

		if (reflow) {
			try {
				form.setRedraw(false);
				// help the layout managers: ensure that the form width always matches
				// the parent client area width.
				Rectangle parentClientArea = form.getParent().getClientArea();
				Point formSize = form.getSize();
				if (formSize.x != parentClientArea.width) {
					ScrollBar verticalBar = form.getVerticalBar();
					int verticalBarWidth = verticalBar != null ? verticalBar.getSize().x : VERTICAL_BAR_WIDTH;
					form.setSize(parentClientArea.width - verticalBarWidth, formSize.y);
				}

				// TODO set this to true true?

				form.layout(true, all);
				form.reflow(true);
			} finally {
				form.setRedraw(true);
			}
		}

	}

	@Override
	protected void fillToolBar(IToolBarManager manager) {
		if (review != null) {
			try {
				EnumSet<CrucibleAction> transitions = review.getTransitions();
				EnumSet<CrucibleAction> actions = review.getActions();
				boolean needsSeparator = false;
				if (actions.contains(CrucibleAction.COMPLETE) && !CrucibleUiUtil.hasCurrentUserCompletedReview(review)) {
					createCompleteReviewAction(manager);
					needsSeparator = true;
				}
				if (actions.contains(CrucibleAction.UNCOMPLETE) && CrucibleUiUtil.hasCurrentUserCompletedReview(review)) {
					createUncompleteReviewAction(manager);
					needsSeparator = true;
				}
				if (needsSeparator) {
					manager.add(new Separator());
					needsSeparator = false;
				}
				if (transitions.contains(CrucibleAction.SUMMARIZE)) {
					createSummarizeReviewAction(manager);
					needsSeparator = true;
				}
				if (transitions.contains(CrucibleAction.REOPEN)) {
					createReopenReviewAction(manager);
					needsSeparator = true;
				}
				if (transitions.contains(CrucibleAction.ABANDON)) {
					createAbandonReviewAction(manager);
					needsSeparator = true;
				}
				if (transitions.contains(CrucibleAction.RECOVER)) {
					createRecoverReviewAction(manager);
					needsSeparator = true;
				}
				if (transitions.contains(CrucibleAction.SUBMIT)) {
					createSubmitReviewAction(manager);
					needsSeparator = true;
				}
				if (needsSeparator) {
					manager.add(new Separator());
					needsSeparator = false;
				}
			} catch (ValueNotYetInitialized e) {
				StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, "Unexpected error", e));
			}
		}

		Action refreshAction = new Action() {
			@Override
			public void run() {
				downloadReviewAndRefresh(0L, true);
			}
		};
		refreshAction.setImageDescriptor(CommonImages.REFRESH);
		refreshAction.setToolTipText("Refresh");
		manager.add(refreshAction);
	}

	private void createSubmitReviewAction(IToolBarManager manager) {
		Action submitAction = new Action() {
			@Override
			public void run() {
				CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Submit Crucible Review "
						+ getTask().getTaskKey(), getTaskRepository()) {
					@Override
					protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
						client.execute(new RemoteOperation<Review>(monitor, getTaskRepository()) {
							@Override
							public Review run(CrucibleServerFacade server, CrucibleServerCfg serverCfg,
									IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
									ServerPasswordNotProvidedException {
								String permId = CrucibleUtil.getPermIdFromTaskId(getTask().getTaskId());
								return server.submitReview(serverCfg, new PermIdBean(permId));
							}
						});
						review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
						return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, "Review was submitted.");
					}
				};
				schedule(job, 0L);
			}
		};
		submitAction.setText("Submit");
		submitAction.setToolTipText("Submit review");
		submitAction.setImageDescriptor(CrucibleImages.SUBMIT);
		manager.add(submitAction);
	}

	private void createUncompleteReviewAction(IToolBarManager manager) {
		Action uncompleteAction = new Action() {
			@Override
			public void run() {
				CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Uncomplete Crucible Review "
						+ getTask().getTaskKey(), getTaskRepository()) {
					@Override
					protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
						client.execute(new RemoteOperation<Object>(monitor, getTaskRepository()) {
							@Override
							public Object run(CrucibleServerFacade server, CrucibleServerCfg serverCfg,
									IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
									ServerPasswordNotProvidedException {
								String permId = CrucibleUtil.getPermIdFromTaskId(getTask().getTaskId());
								server.completeReview(serverCfg, new PermIdBean(permId), false);
								return null;
							}
						});
						review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
						return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, "Review was uncompleted.");
					}
				};
				schedule(job, 0L);
			}
		};
		uncompleteAction.setText("Uncomplete");
		uncompleteAction.setToolTipText("Uncomplete review");
		uncompleteAction.setImageDescriptor(CrucibleImages.UNCOMPLETE);
		manager.add(uncompleteAction);
	}

	private void createCompleteReviewAction(IToolBarManager manager) {
		Action completeAction = new Action() {
			@Override
			public void run() {
				CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Complete Crucible Review "
						+ getTask().getTaskKey(), getTaskRepository()) {
					@Override
					protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
						client.execute(new RemoteOperation<Object>(monitor, getTaskRepository()) {
							@Override
							public Object run(CrucibleServerFacade server, CrucibleServerCfg serverCfg,
									IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
									ServerPasswordNotProvidedException {
								String permId = CrucibleUtil.getPermIdFromTaskId(getTask().getTaskId());
								server.completeReview(serverCfg, new PermIdBean(permId), true);
								return null;
							}
						});
						review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
						return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, "Review was completed.");
					}
				};
				schedule(job, 0L);
			}
		};
		completeAction.setText("Complete");
		completeAction.setToolTipText("Complete review");
		completeAction.setImageDescriptor(CrucibleImages.COMPLETE);
		manager.add(completeAction);
	}

	private void createRecoverReviewAction(IToolBarManager manager) {
		Action recoverAction = new Action() {
			@Override
			public void run() {
				CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Recover Crucible Review "
						+ getTask().getTaskKey(), getTaskRepository()) {
					@Override
					protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
						client.execute(new RemoteOperation<Review>(monitor, getTaskRepository()) {
							@Override
							public Review run(CrucibleServerFacade server, CrucibleServerCfg serverCfg,
									IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
									ServerPasswordNotProvidedException {
								String permId = CrucibleUtil.getPermIdFromTaskId(getTask().getTaskId());
								return server.recoverReview(serverCfg, new PermIdBean(permId));
							}
						});
						review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
						return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, "Review was recovered.");
					}
				};
				schedule(job, 0L);
			}
		};
		recoverAction.setText("Recover");
		recoverAction.setToolTipText("Recover Review");
		recoverAction.setImageDescriptor(CrucibleImages.RECOVER);
		manager.add(recoverAction);
	}

	private void createAbandonReviewAction(IToolBarManager manager) {
		Action abandonAction = new Action() {
			@Override
			public void run() {
				String message = "Warning - About to delete a Review" + System.getProperty("line.separator")
						+ "You can recover an abandoned review using the Abandoned filter."
						+ System.getProperty("line.separator") + System.getProperty("line.separator")
						+ "Are you sure you want to abandon this review? ";
				if (MessageDialog.openConfirm(getSite().getShell(), "Abandon", message)) {
					CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Abandon Crucible Review "
							+ getTask().getTaskKey(), getTaskRepository()) {
						@Override
						protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
							client.execute(new RemoteOperation<Review>(monitor, getTaskRepository()) {
								@Override
								public Review run(CrucibleServerFacade server, CrucibleServerCfg serverCfg,
										IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
										ServerPasswordNotProvidedException {
									String permId = CrucibleUtil.getPermIdFromTaskId(getTask().getTaskId());
									return server.abandonReview(serverCfg, new PermIdBean(permId));
								}
							});
							review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
							return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, "Review was abandoned.");
						}
					};
					schedule(job, 0L);
				}
			}
		};
		abandonAction.setText("Abandon");
		abandonAction.setToolTipText("Abandon review");
		abandonAction.setImageDescriptor(CrucibleImages.ABANDON);
		manager.add(abandonAction);
	}

	private void createReopenReviewAction(IToolBarManager manager) {
		Action abandonAction = new Action() {
			@Override
			public void run() {
				CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Reopen Crucible Review "
						+ getTask().getTaskKey(), getTaskRepository()) {
					@Override
					protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
						client.execute(new RemoteOperation<Review>(monitor, getTaskRepository()) {
							@Override
							public Review run(CrucibleServerFacade server, CrucibleServerCfg serverCfg,
									IProgressMonitor monitor) throws CrucibleLoginException, RemoteApiException,
									ServerPasswordNotProvidedException {
								String permId = CrucibleUtil.getPermIdFromTaskId(getTask().getTaskId());
								return server.reopenReview(serverCfg, new PermIdBean(permId));
							}
						});
						review = client.getReview(getTaskRepository(), getTask().getTaskId(), true, monitor);
						return new Status(IStatus.OK, CrucibleUiPlugin.PLUGIN_ID, "Review was reopened.");
					}
				};
				schedule(job, 0L);
			}
		};
		abandonAction.setText("Reopen");
		abandonAction.setImageDescriptor(CrucibleImages.REOPEN);
		abandonAction.setToolTipText("Reopen review");
		manager.add(abandonAction);
	}

	private void createSummarizeReviewAction(IToolBarManager manager) {
		Action summarizeAction = new SummarizeReviewAction(review, "Summarize and Close Crucible Review "
				+ getTask().getTaskKey());
		summarizeAction.setText("Summarize and Close");
		summarizeAction.setToolTipText("Summarize and Close Review");
		summarizeAction.setImageDescriptor(CrucibleImages.SUMMARIZE);
		manager.add(summarizeAction);
	}

	private void schedule(final CrucibleReviewChangeJob job, long delay) {
		schedule(job, delay, false);
	}

	private void schedule(final CrucibleReviewChangeJob job, long delay, final boolean force) {
		job.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(final IJobChangeEvent event) {
				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						reviewUpdateCompleted(job.getStatus(), force);
					}
				});
			}
		});
		job.schedule(delay);
		setBusy(true);
	}

	public void selectAndReveal(CrucibleFileInfo crucibleFile, VersionedComment comment) {
		this.selectedCrucibleFile = crucibleFile;
		this.selectedComment = comment;
		if (selectedCrucibleFile == null || selectedComment == null) {
			this.selectedCrucibleFile = null;
			this.selectedComment = null;
			setHighlightedPart(null);
		} else {
			for (AbstractCrucibleEditorFormPart part : parts) {
				if (part instanceof CrucibleReviewFilesPart) {
					((CrucibleReviewFilesPart) part).selectAndReveal(crucibleFile, comment);
				}
			}
		}
	}

	public void setHighlightedPart(ExpandablePart part) {
		if (highlightedControl != null) {
			setControlHighlighted(highlightedControl, false);
			highlightedControl = null;
		}

		if (part != null) {
			Section highlightedSection = part.getSection();
			if (highlightedSection != null) {
				Control client = highlightedSection.getClient();
				if (client != null) {
					setControlHighlighted(client, true);
					highlightedControl = client;
				}
			}
		}

	}

	private void setControlHighlighted(Control client, boolean shouldHighlight) {
		if (toolkit != null && !client.isDisposed() && selectionColor != null) {
			if (shouldHighlight) {
				client.setBackground(selectionColor);
			} else {
				client.setBackground(toolkit.getColors().getBackground());
			}
			if (client instanceof Composite) {
				for (Control child : ((Composite) client).getChildren()) {
					setControlHighlighted(child, shouldHighlight);
				}
			}
		}
	}

	public boolean canReflow() {
		return reflow;
	}

	public Color getColorIncoming() {
		return colorIncoming;
	}

	public String getUserName() {
		return getTaskRepository().getUserName();
	}

	private void reviewUpdateCompleted(final IStatus status, final boolean force) {
		setBusy(false);

		// TODO setup the image descriptor properly too 

		if (editorComposite != null) {
			if (status == null || review == null) {
				String message = "Unable to retrieve Review";
				if (status != null && status.getMessage().contains("HTTP 401")) {
					message += " (HTTP 401 - Unauthorized)";
				}
				message += ". Click to try again.";
				getEditor().setMessage(message, IMessageProvider.WARNING, new HyperlinkAdapter() {
					@Override
					public void linkActivated(HyperlinkEvent e) {
						downloadReviewAndRefresh(0, true);
					}
				});

				if (status != null && force) {
					StatusHandler.log(status);
				}

				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						if (initiaizingLabel != null && !initiaizingLabel.isDisposed()) {
							String message0 = "";
							String message1 = "";
							if (status != null) {
								message0 = status.getMessage().trim() + ".";
								if (force) {
									message1 = "\nSee Error log for more details.";
								}
							}
							String message = NLS.bind(INITIALIZATION_FAILED_MESSAGE, message0, message1);
							initiaizingLabel.setText(message);
							reflow(false);
						}
					}
				});

			} else if (status.isOK()) {
				// TODO use the status?
				getEditor().setMessage(status.getMessage(), IMessageProvider.NONE, null);
				if (force) {
					createInitialFormContent();
				} else {
					updateFormContent();
				}
				getEditor().updateHeaderToolBar();
				TasksUiPlugin.getTaskDataManager().setTaskRead(getTask(), true);
			} else {
				// TODO improve the message?
				getEditor().setMessage(status.getMessage(), IMessageProvider.ERROR, null);
			}
		}
	}
}
