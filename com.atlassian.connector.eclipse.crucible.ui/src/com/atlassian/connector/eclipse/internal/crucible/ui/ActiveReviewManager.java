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

package com.atlassian.connector.eclipse.internal.crucible.ui;

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleConstants;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleCorePlugin;
import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;
import com.atlassian.connector.eclipse.internal.crucible.core.client.model.IReviewCacheListener;
import com.atlassian.connector.eclipse.internal.crucible.ui.annotations.CrucibleAnnotationModelManager;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.notification.CrucibleNotification;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.tasks.core.IRepositoryManager;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.ITaskActivationListener;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.sync.SynchronizationJob;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class to manage the currently active review for other models
 * 
 * @author sminto
 */
public class ActiveReviewManager implements ITaskActivationListener, IReviewCacheListener {

	private static final long ACTIVE_REVIEW_POLLING_INTERVAL = 120000L;

	private final JobChangeAdapter refreshJobRescheduler = new JobChangeAdapter() {
		@Override
		public void done(IJobChangeEvent event) {
			synchronizeJob = null;
			createAndScheduleRefreshJob();
		}
	};

	private Review activeReview;

	private ITask activeTask;

	private final IRepositoryManager repositoryManager;

	private SynchronizationJob synchronizeJob;

	public ActiveReviewManager(IRepositoryManager repositoryManager) {
		this.repositoryManager = repositoryManager;
		CrucibleCorePlugin.getDefault().getReviewCache().addCacheChangedListener(this);
	}

	public void dispose() {
		CrucibleCorePlugin.getDefault().getReviewCache().removeCacheChangedListener(this);
	}

	public synchronized void taskActivated(ITask task) {
		if (!task.getConnectorKind().equals(CrucibleCorePlugin.CONNECTOR_KIND)) {
			return;
		}

		System.setProperty(CrucibleConstants.REVIEW_ACTIVE_SYSTEM_PROPERTY, "true");

		this.activeTask = task;
		Review cachedReview = CrucibleCorePlugin.getDefault().getReviewCache().getServerReview(task.getRepositoryUrl(),
				task.getTaskId());
		if (cachedReview == null) {
			scheduleDownloadJob(task);
		} else {
			activeReviewUpdated(cachedReview, task);
		}
		startIncreasedChangePolling();
	}

	public synchronized void taskDeactivated(ITask task) {
		this.activeTask = null;
		this.activeReview = null;
		System.setProperty(CrucibleConstants.REVIEW_ACTIVE_SYSTEM_PROPERTY, "false");
		CrucibleAnnotationModelManager.dettachAllOpenEditors();
		stopIncreasedChangePolling();
	}

	public void preTaskActivated(ITask task) {
		// ignore
	}

	public synchronized void preTaskDeactivated(ITask task) {
		// ignore
	}

	private synchronized void activeReviewUpdated(Review cachedReview, ITask task) {
		if (activeTask != null && task != null && activeTask.equals(task)) {
			if (activeReview == null) {
				this.activeReview = cachedReview;
				CrucibleAnnotationModelManager.attachAllOpenEditors();
			} else {
				this.activeReview = cachedReview;
				CrucibleAnnotationModelManager.updateAllOpenEditors(activeReview);
			}
		}
	}

	public synchronized Review getActiveReview() {
		return activeReview;
	}

	public synchronized ITask getActiveTask() {
		return activeTask;
	}

	private void startIncreasedChangePolling() {
		createAndScheduleRefreshJob();
	}

	private synchronized void createAndScheduleRefreshJob() {
		if (synchronizeJob == null && getActiveTask() != null) {
			Set<ITask> tasks = new HashSet<ITask>();
			tasks.add(getActiveTask());
			synchronizeJob = TasksUiPlugin.getTaskJobFactory().createSynchronizeTasksJob(
					CrucibleCorePlugin.getRepositoryConnector(), tasks);
			synchronizeJob.addJobChangeListener(refreshJobRescheduler);
			synchronizeJob.schedule(ACTIVE_REVIEW_POLLING_INTERVAL);
		}
	}

	private void stopIncreasedChangePolling() {
		if (synchronizeJob != null) {
			synchronizeJob.removeJobChangeListener(refreshJobRescheduler);
			synchronizeJob.cancel();
			synchronizeJob = null;
		}
	}

	private void scheduleDownloadJob(final ITask task) {
		Job downloadJob = new Job("Retrieving review from server") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				try {
					TaskRepository repository = repositoryManager.getRepository(CrucibleCorePlugin.CONNECTOR_KIND,
							task.getRepositoryUrl());

					if (repository != null) {
						String taskId = task.getTaskId();

						CrucibleClient client = CrucibleCorePlugin.getRepositoryConnector()
								.getClientManager()
								.getClient(repository);
						if (client != null) {
							activeReviewUpdated(client.getReview(repository, taskId, false, monitor), task);
						} else {
							return new Status(IStatus.ERROR, CrucibleCorePlugin.PLUGIN_ID,
									"Unable to get crucible client for repository");
						}
					} else {
						return new Status(IStatus.ERROR, CrucibleCorePlugin.PLUGIN_ID,
								"Crucible repository does not exist");
					}
					return Status.OK_STATUS;
				} catch (CoreException e) {
					return e.getStatus();
				}
			}
		};
		downloadJob.setUser(true);
		downloadJob.schedule();
	}

	public synchronized boolean isReviewActive() {
		return activeTask != null && activeReview != null;
	}

	public void reviewAdded(String repositoryUrl, String taskId, Review review) {
		// ignore
	}

	public synchronized void reviewUpdated(String repositoryUrl, String taskId, Review review,
			List<CrucibleNotification> differences) {
		if (activeTask != null && activeReview != null) {
			if (activeTask.getRepositoryUrl().equals(repositoryUrl) && activeTask.getTaskId().equals(taskId)) {
				activeReviewUpdated(review, activeTask);
			}
		}
	}
}
