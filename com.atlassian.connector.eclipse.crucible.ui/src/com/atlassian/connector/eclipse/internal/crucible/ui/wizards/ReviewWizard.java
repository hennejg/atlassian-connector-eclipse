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

package com.atlassian.connector.eclipse.internal.crucible.ui.wizards;

import com.atlassian.connector.commons.api.ConnectionCfg;
import com.atlassian.connector.commons.crucible.CrucibleServerFacade2;
import com.atlassian.connector.eclipse.internal.core.jobs.JobWithStatus;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleCorePlugin;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleRepositoryConnector;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleUtil;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleRemoteOperation;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.editor.CrucibleReviewChangeJob;
import com.atlassian.connector.eclipse.internal.crucible.ui.operations.AddResourcesToReviewJob;
import com.atlassian.connector.eclipse.team.ui.ICustomChangesetLogEntry;
import com.atlassian.connector.eclipse.team.ui.LocalStatus;
import com.atlassian.connector.eclipse.team.ui.ScmRepository;
import com.atlassian.connector.eclipse.team.ui.TeamUiUtils;
import com.atlassian.theplugin.commons.crucible.ValueNotYetInitialized;
import com.atlassian.theplugin.commons.crucible.api.CrucibleLoginException;
import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleAction;
import com.atlassian.theplugin.commons.crucible.api.model.NewReviewItemBean;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.SvnRepository;
import com.atlassian.theplugin.commons.exception.ServerPasswordNotProvidedException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;
import com.atlassian.theplugin.commons.util.MiscUtil;
import com.atlassian.theplugin.commons.util.StringUtil;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskMapping;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.mylyn.tasks.ui.TasksUiUtil;
import org.eclipse.mylyn.tasks.ui.wizards.NewTaskWizard;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * Wizard for creating a new review
 * 
 * @author Thomas Ehrnhoefer
 * @author Pawel Niewiadomski
 * @author Jacek Jaroczynski
 */
@SuppressWarnings("restriction")
public class ReviewWizard extends NewTaskWizard implements INewWizard {

	public enum Type {
		ADD_CHANGESET, ADD_PATCH, ADD_WORKSPACE_PATCH, ADD_SCM_FILE, ADD_SCM_RESOURCES;
	}

	private class AddChangesetsToReviewJob extends CrucibleReviewChangeJob {
		private final Map<ScmRepository, SortedSet<ICustomChangesetLogEntry>> selectedLogEntries;

		public AddChangesetsToReviewJob(String name, TaskRepository taskRepository,
				Map<ScmRepository, SortedSet<ICustomChangesetLogEntry>> selectedLogEntries) {
			super(name, taskRepository);
			this.selectedLogEntries = selectedLogEntries;
		}

		@Override
		protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
			Review review = client.execute(new CrucibleRemoteOperation<Review>(monitor, getTaskRepository()) {
				@Override
				public Review run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
						throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {
					Review review = null;
					for (ScmRepository repository : selectedLogEntries.keySet()) {
						monitor.beginTask("Adding revisions to repository " + repository.getScmPath(),
								selectedLogEntries.get(repository).size());
						//collect revisions
						ArrayList<String> revisions = new ArrayList<String>();
						for (ICustomChangesetLogEntry logEntry : selectedLogEntries.get(repository)) {
							revisions.add(logEntry.getRevision());
						}

						//add changeset to review
						review = server.addRevisionsToReview(serverCfg, crucibleReview.getPermId(),
								addChangeSetsPage.getRepositoryMappings().get(repository.getScmPath()), revisions);
					}
					return review;
				}
			});

			updateCrucibleReview(client, review, monitor);
			return Status.OK_STATUS;
		}
	}

	private class AddFileToReviewJob extends CrucibleReviewChangeJob {

		public AddFileToReviewJob(String name, TaskRepository taskRepository) {
			super(name, taskRepository);
		}

		@Override
		protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
			client.execute(new CrucibleRemoteOperation<Void>(monitor, getTaskRepository()) {

				@Override
				public Void run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
						throws RemoteApiException, ServerPasswordNotProvidedException {

					// monitor.beginTask should be called only once (it is already called in client.execute implementation)
//					monitor.beginTask("Adding file to review", 1);

					String crucibleRepositoryName = defineMappingPage.getRepositoryMappings().get(
							scmRepositoryInfo.getScmPath());

					// TODO add support for repos other than SVN 
					SvnRepository crucibleRepository = server.getRepository(serverCfg, crucibleRepositoryName);

					if (crucibleRepository == null) {
						StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID,
								"Adding file to review failed. Cannot get repository data for "
										+ crucibleRepositoryName));
						return null;
					}

					String filePath = scmResourceRevisionInfo.getScmPath();

					if (filePath.startsWith(crucibleRepository.getUrl())) {
						filePath = filePath.substring(crucibleRepository.getUrl().length());
					} else {
						StatusHandler.log(new Status(IStatus.WARNING, CrucibleUiPlugin.PLUGIN_ID,
								"Cannot match selected resource with Crucible repository url."));
					}

					filePath = StringUtil.removePrefixSlashes(filePath);
					String cruPath = StringUtil.removePrefixSlashes(crucibleRepository.getPath());
					if (filePath.startsWith(cruPath)) {
						filePath = filePath.substring(crucibleRepository.getPath().length());
					} else {
						StatusHandler.log(new Status(IStatus.WARNING, CrucibleUiPlugin.PLUGIN_ID,
								"Cannot match selected resource with Crucible repository path."));
					}

					filePath = StringUtil.removePrefixSlashes(filePath);

					NewReviewItemBean newReviewItem = new NewReviewItemBean();
					newReviewItem.setFromPath(filePath);
					newReviewItem.setToPath(filePath);
					newReviewItem.setFromRevision(scmResourceRevisionInfo.getRevision());
					newReviewItem.setToRevision(scmResourceRevisionInfo.getRevision());
					newReviewItem.setRepositoryName(crucibleRepositoryName);

					server.addFileToReview(serverCfg, crucibleReview.getPermId(), newReviewItem);

					return null;
				}
			});

			return Status.OK_STATUS;
		}
	}

	private class AddPatchToReviewJob extends CrucibleReviewChangeJob {
		private final String patchRepository;

		private final String patch;

		public AddPatchToReviewJob(String name, TaskRepository taskRepository, String patch, String patchRepository) {
			super(name, taskRepository);
			this.patch = patch;
			this.patchRepository = patchRepository;
		}

		@Override
		protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
			Review updatedReview = client.execute(new CrucibleRemoteOperation<Review>(monitor, getTaskRepository()) {
				@Override
				public Review run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
						throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {

					return server.addPatchToReview(serverCfg, crucibleReview.getPermId(), patchRepository, patch);
				}
			});

			updateCrucibleReview(client, updatedReview, monitor);
			return Status.OK_STATUS;
		}
	}

	private class AddItemsToReviewJob extends CrucibleReviewChangeJob {
		private final Collection<UploadItem> items;

		public AddItemsToReviewJob(String name, TaskRepository taskRepository, Collection<UploadItem> items) {
			super(name, taskRepository);
			this.items = items;
		}

		@Override
		protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
			Review updatedReview = client.execute(new CrucibleRemoteOperation<Review>(monitor, getTaskRepository()) {
				@Override
				public Review run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
						throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {

					return server.addItemsToReview(serverCfg, crucibleReview.getPermId(), items);
				}
			});

			updateCrucibleReview(client, updatedReview, monitor);
			return Status.OK_STATUS;
		}
	}

	private CrucibleReviewDetailsPage detailsPage;

	private Review crucibleReview;

	private CrucibleAddChangesetsPage addChangeSetsPage;

	private CrucibleAddPatchPage addPatchPage;

	private WorkspacePatchSelectionPage addWorkspacePatchPage;

	private final Set<Type> types;

	private SortedSet<ICustomChangesetLogEntry> preselectedLogEntries;

	private String previousPatch;

	private String previousPatchRepository;

	private final List<IResource> selectedWorkspaceResources = new ArrayList<IResource>();

	private IResource[] previousWorkspaceSelection;

	private LocalStatus scmResourceRevisionInfo;

	private CrucibleRepositoryMappingPageImpl defineMappingPage;

	private ScmRepository scmRepositoryInfo;

	public ReviewWizard(TaskRepository taskRepository, Set<Type> types) {
		super(taskRepository, null);
		setWindowTitle("New Crucible Review");
		setNeedsProgressMonitor(true);
		this.types = types;
		this.selectedWorkspaceResources.addAll(Arrays.asList((IResource[]) ResourcesPlugin.getWorkspace()
				.getRoot()
				.getProjects()));
	}

	public ReviewWizard(Review review, Set<Type> types) {
		this(CrucibleUiUtil.getCrucibleTaskRepository(review), types);
		this.crucibleReview = review;
	}

	public ReviewWizard(Review review, Type type) {
		this(review, new HashSet<Type>(Arrays.asList(type)));
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		// ignore
		super.init(workbench, selection);
	}

	@Override
	protected ITaskMapping getInitializationData() {
		// ignore
		return super.getInitializationData();
	}

	@Override
	public void addPages() {
		if (types.contains(Type.ADD_CHANGESET)) {
			addChangeSetsPage = new CrucibleAddChangesetsPage(getTaskRepository(), preselectedLogEntries);
			addPage(addChangeSetsPage);
		}
		if (types.contains(Type.ADD_PATCH)) {
			addPatchPage = new CrucibleAddPatchPage(getTaskRepository());
			addPage(addPatchPage);
		}

		if (types.contains(Type.ADD_WORKSPACE_PATCH)) {
			addWorkspacePatchPage = new WorkspacePatchSelectionPage(getTaskRepository(), selectedWorkspaceResources);
			addPage(addWorkspacePatchPage);
		}
		if (types.contains(Type.ADD_SCM_FILE)) {
			defineMappingPage = new CrucibleRepositoryMappingPageImpl(getTaskRepository(),
					Arrays.asList(scmRepositoryInfo.getScmPath()));
			addPage(defineMappingPage);
		}
		if (types.contains(Type.ADD_SCM_RESOURCES)) {
			Set<String> repos = MiscUtil.buildHashSet();

			for (IResource root : selectedWorkspaceResources) {
				ScmRepository repositoryInfo = TeamUiUtils.getApplicableRepository(root);
				if (repositoryInfo != null) {
					repos.add(repositoryInfo.getScmPath());
				} else {
					StatusHandler.log(new Status(IStatus.WARNING, CrucibleUiPlugin.PLUGIN_ID,
							"Cannot get repository info for resource " + root.getName()));
				}
			}

			defineMappingPage = new CrucibleRepositoryMappingPageImpl(getTaskRepository(), repos);
			addPage(defineMappingPage);
		}

		//only add details page if review is not already existing
		if (crucibleReview == null) {
			detailsPage = new CrucibleReviewDetailsPage(getTaskRepository());
			addPage(detailsPage);
		}
	}

	@Override
	public boolean canFinish() {
		if (detailsPage != null) {
			return detailsPage.isPageComplete();
		}
		return super.canFinish();
	}

	@Override
	public boolean performFinish() {

		setErrorMessage(null);

		// create review if not yet done, if it fails abort immediately
		if (crucibleReview == null && detailsPage != null) {
			IStatus result = createAndStoreReview(detailsPage.getReview(),
					CrucibleUiUtil.getUsernamesFromUsers(detailsPage.getReviewers()));

			if (!result.isOK()) {
				StatusHandler.log(result);
				setErrorMessage(result.getMessage());
				return false;
			}
		}

		if (detailsPage != null) {
			// save project selection
			CrucibleRepositoryConnector.updateLastSelectedProject(getTaskRepository(), detailsPage.getSelectedProject());

			// save checkbox selections
			CrucibleRepositoryConnector.updateAllowAnyoneOption(getTaskRepository(), detailsPage.isAllowAnyoneToJoin());
			CrucibleRepositoryConnector.updateStartReviewOption(getTaskRepository(),
					detailsPage.isStartReviewImmediately());
		}

		final MultiStatus creationProcessStatus = new MultiStatus(CrucibleUiPlugin.PLUGIN_ID, IStatus.OK,
				"Error during review creation. See Error Log for details.", null);

		if (addPatchPage != null) {
			String patchToAdd = addPatchPage.hasPatch() ? addPatchPage.getPatch() : null;
			String patchRepositoryToAdd = addPatchPage.hasPatch() ? addPatchPage.getPatchRepository() : null;

			if (patchToAdd != null && patchRepositoryToAdd != null && !patchToAdd.equals(previousPatch)
					&& !patchRepositoryToAdd.equals(previousPatchRepository)) {
				final JobWithStatus job = new AddPatchToReviewJob("Add patch to review", getTaskRepository(),
						patchToAdd, patchRepositoryToAdd);

				IStatus result = runJobInContainer(job);
				if (result.isOK()) {
					previousPatch = patchToAdd;
					previousPatchRepository = patchRepositoryToAdd;
				} else {
					creationProcessStatus.add(result);
				}
			}
		}

		if (addWorkspacePatchPage != null) {
			final IResource[] selection = addWorkspacePatchPage.getSelection();

			if (selection != null && selection.length > 0 && !Arrays.equals(selection, previousWorkspaceSelection)
					&& addWorkspacePatchPage.getSelectedTeamResourceConnector() != null) {
				final Collection<UploadItem> uploadItems = new ArrayList<UploadItem>();

				JobWithStatus getItemsJob = new JobWithStatus("Prepare upload items for review") {
					@Override
					public void runImpl(IProgressMonitor monitor) {
						try {
							uploadItems.addAll(addWorkspacePatchPage.getSelectedTeamResourceConnector()
									.getUploadItemsForResources(selection, monitor));
						} catch (CoreException e) {
							setStatus(e.getStatus());
							creationProcessStatus.add(e.getStatus());
						}
					}
				};

				IStatus result = runJobInContainer(getItemsJob);
				if (result.isOK() && uploadItems.size() > 0) {
					JobWithStatus job = new AddItemsToReviewJob("Add patch to review", getTaskRepository(), uploadItems);

					result = runJobInContainer(job);
					if (result.isOK()) {
						previousWorkspaceSelection = selection;
					} else {
						creationProcessStatus.add(result);
					}
				} else {
					creationProcessStatus.add(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID,
							"Can't create a patch, did you select modified files?"));
				}
			}
		}

		if (addChangeSetsPage != null) {
			final Map<ScmRepository, SortedSet<ICustomChangesetLogEntry>> changesetsToAdd = addChangeSetsPage.getSelectedChangesets();

			if (changesetsToAdd != null && changesetsToAdd.size() > 0) {
				final CrucibleReviewChangeJob job = new AddChangesetsToReviewJob("Add changesets to review",
						getTaskRepository(), changesetsToAdd);

				IStatus result = runJobInContainer(job);
				if (!result.isOK()) {
					creationProcessStatus.add(result);
				}
			}
		}

		// TODO jj fix or remove if statement after implementing package/directory review creation
		if (defineMappingPage != null && types.contains(Type.ADD_SCM_FILE) && !types.contains(Type.ADD_SCM_RESOURCES)) {
			if (scmRepositoryInfo != null && scmResourceRevisionInfo != null) {
				final CrucibleReviewChangeJob job = new AddFileToReviewJob("Add file to review", getTaskRepository());

				IStatus result = runJobInContainer(job);
				if (!result.isOK()) {
					creationProcessStatus.add(result);
				}
			}
		}

		// TODO jj fix/merge with single file review creation
		if (defineMappingPage != null && types.contains(Type.ADD_SCM_RESOURCES) && !types.contains(Type.ADD_SCM_FILE)) {
			if (selectedWorkspaceResources != null) {
				final JobWithStatus job = new AddResourcesToReviewJob(crucibleReview,
						selectedWorkspaceResources.toArray(new IResource[selectedWorkspaceResources.size()]));

				IStatus result = runJobInContainer(job);
				if (!result.isOK()) {
					creationProcessStatus.add(result);
				}
			}
		}

		EnumSet<CrucibleAction> crucibleActions = null;
		try {
			crucibleActions = crucibleReview.getActions();
		} catch (ValueNotYetInitialized e) {
			StatusHandler.log(new Status(IStatus.WARNING, CrucibleUiPlugin.PLUGIN_ID, "Failed to get allowed actions",
					e));
		}

		if (crucibleActions == null) {
			crucibleActions = EnumSet.noneOf(CrucibleAction.class);
		}

		if (crucibleReview != null && detailsPage != null && detailsPage.isStartReviewImmediately()
				&& crucibleActions.contains(CrucibleAction.SUBMIT)) {

			IStatus result = startAndUpdateReview();
			if (!result.isOK()) {
				creationProcessStatus.add(result);
			}

			if (crucibleActions.contains(CrucibleAction.APPROVE)) {
				result = approveAndUpdateReview();
				if (!result.isOK()) {
					creationProcessStatus.add(result);
				}
			}
		}

		if (creationProcessStatus.getSeverity() != IStatus.OK) {
			setErrorMessage(creationProcessStatus.getMessage());
			StatusHandler.log(creationProcessStatus);
			return false;
		}

		final IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				final ITask task = TasksUi.getRepositoryModel().createTask(getTaskRepository(),
						CrucibleUtil.getTaskIdFromPermId(crucibleReview.getPermId().getId()));
				try {
					TaskData taskData = CrucibleUiUtil.getClient(crucibleReview).getTaskData(getTaskRepository(),
							task.getTaskId(), monitor);
					CrucibleCorePlugin.getRepositoryConnector().updateTaskFromTaskData(getTaskRepository(), task,
							taskData);
					TasksUiInternal.getTaskList().addTask(task, null);
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							TasksUiUtil.openTask(task);
						}
					});
				} catch (CoreException e) {
					// ignore
				}
			}
		};
		try {
			getContainer().run(true, false, runnable);
		} catch (Exception e) {
			setErrorMessage("Could not open created review. Refresh tasklist.");
			StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, "Error opening review", e));
		}
		return crucibleReview != null && creationProcessStatus.getSeverity() == IStatus.OK;
	}

	private IStatus startAndUpdateReview() {
		final JobWithStatus startReviewJob = new CrucibleReviewChangeJob("Start review immediately",
				getTaskRepository()) {
			@Override
			protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
				Review updatedReview = client.execute(new CrucibleRemoteOperation<Review>(monitor, getTaskRepository()) {
					@Override
					public Review run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
							throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {
						return server.submitReview(serverCfg, crucibleReview.getPermId());
					}
				});

				updateCrucibleReview(client, updatedReview, monitor);
				return Status.OK_STATUS;
			}
		};

		return runJobInContainer(startReviewJob);
	}

	private IStatus approveAndUpdateReview() {
		// if possible, approve review
		final CrucibleReviewChangeJob approveReviewJob = new CrucibleReviewChangeJob("Approve Review",
				getTaskRepository()) {
			@Override
			protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
				Review updatedReview = client.execute(new CrucibleRemoteOperation<Review>(monitor, getTaskRepository()) {
					@Override
					public Review run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
							throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {
						return server.approveReview(serverCfg, crucibleReview.getPermId());
					}
				});

				updateCrucibleReview(client, updatedReview, monitor);
				return Status.OK_STATUS;
			}
		};

		return runJobInContainer(approveReviewJob);
	}

	private IStatus runJobInContainer(final JobWithStatus job) {
		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				job.run(monitor);
			}
		};
		try {
			getContainer().run(true, true, runnable);
		} catch (Exception e) {
			return new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, String.format("Job \"%s\" failed",
					job.getName()), e);
		}
		return job.getStatus();
	}

	private IStatus createAndStoreReview(final Review newReview, final Set<String> reviewers) {
		final CrucibleReviewChangeJob job = new CrucibleReviewChangeJob("Create new review", getTaskRepository()) {
			@Override
			protected IStatus execute(CrucibleClient client, IProgressMonitor monitor) throws CoreException {
				Review updatedReview = client.execute(new CrucibleRemoteOperation<Review>(monitor, getTaskRepository()) {
					@Override
					public Review run(CrucibleServerFacade2 server, ConnectionCfg serverCfg, IProgressMonitor monitor)
							throws CrucibleLoginException, RemoteApiException, ServerPasswordNotProvidedException {
						Review tempReview = server.createReview(serverCfg, newReview);
						if (tempReview != null) {
							server.setReviewers(serverCfg, tempReview.getPermId(), reviewers);
						}
						return tempReview;
					}
				});

				if (updatedReview == null) {
					// WTF? No error and null
					return new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, "Server didn't return review");
				}

				updateCrucibleReview(client, updatedReview, monitor);
				return Status.OK_STATUS;
			}
		};

		IRunnableWithProgress runnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				job.run(monitor);
			}
		};

		try {
			getContainer().run(true, true, runnable);
		} catch (Exception e) {
			return new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID, "Error creating Review", e);
		}

		return job.getStatus();
	}

	private void setErrorMessage(String message) {
		IWizardPage page = getContainer().getCurrentPage();
		if (page instanceof WizardPage) {
			((WizardPage) page).setErrorMessage(message);
		}
	}

	public void setLogEntries(SortedSet<ICustomChangesetLogEntry> logEntries) {
		this.preselectedLogEntries = logEntries;
	}

	public void setRoots(List<IResource> list) {
		this.selectedWorkspaceResources.clear();
		this.selectedWorkspaceResources.addAll(list);
	}

	private void updateCrucibleReview(CrucibleClient client, Review updatedReview, IProgressMonitor monitor)
			throws CoreException {
		// Update review after every change because otherwise some data will be missing (in this case we miss actions)
		crucibleReview = client.getReview(getTaskRepository(), CrucibleUtil.getTaskIdFromReview(updatedReview), true,
				monitor);
	}

	public void setSelectedScmResource(LocalStatus revisionInfo, ScmRepository repositoryInfo) {
		this.scmResourceRevisionInfo = revisionInfo;
		this.scmRepositoryInfo = repositoryInfo;
	}
}
