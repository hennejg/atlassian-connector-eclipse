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

package com.atlassian.connector.eclipse.internal.crucible.core;

import com.atlassian.connector.eclipse.internal.crucible.core.client.CrucibleClient;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.tasks.core.AbstractRepositoryConnector;
import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskDataCollector;
import org.eclipse.mylyn.tasks.core.data.TaskMapper;
import org.eclipse.mylyn.tasks.core.sync.ISynchronizationSession;

import java.io.File;
import java.util.Date;

/**
 * Core class for integration with Mylyn tasks framework and synchronization
 * 
 * @author Shawn Minto
 */
public class CrucibleRepositoryConnector extends AbstractRepositoryConnector {

	private static final String REPOSITORY_LABEL = "Crucible";

	private CrucibleClientManager clientManager;

	private File repositoryConfigurationCacheFile;

	public CrucibleRepositoryConnector() {
		CrucibleCorePlugin.setRepositoryConnector(this);
		if (CrucibleCorePlugin.getDefault() != null) {
			this.repositoryConfigurationCacheFile = CrucibleCorePlugin.getDefault()
					.getRepositoryConfigurationCacheFile();
		}

	}

	public synchronized CrucibleClientManager getClientManager() {
		if (clientManager == null) {
			clientManager = new CrucibleClientManager(getRepositoryConfigurationCacheFile(),
					CrucibleCorePlugin.getDefault().getReviewCache());
		}
		return clientManager;
	}

	public File getRepositoryConfigurationCacheFile() {
		return repositoryConfigurationCacheFile;
	}

	@Override
	public String getConnectorKind() {
		return CrucibleCorePlugin.CONNECTOR_KIND;
	}

	@Override
	public String getLabel() {
		return REPOSITORY_LABEL;
	}

	@Override
	public boolean canCreateNewTask(TaskRepository repository) {
		return false;
	}

	@Override
	public boolean canCreateTaskFromKey(TaskRepository repository) {
		return true;
	}

	@Override
	public String getRepositoryUrlFromTaskUrl(String taskFullUrl) {
		return CrucibleUtil.getRepositoryUrlFromUrl(taskFullUrl);
	}

	@Override
	public TaskData getTaskData(TaskRepository taskRepository, String taskId, IProgressMonitor monitor)
			throws CoreException {
		CrucibleClient client = getClientManager().getClient(taskRepository);
		return client.getTaskData(taskRepository, CrucibleUtil.getTaskIdFromPermId(taskId), monitor);
	}

	@Override
	public String getTaskIdFromTaskUrl(String taskFullUrl) {
		return CrucibleUtil.getTaskIdFromUrl(taskFullUrl);
	}

	@Override
	public String getTaskUrl(String repositoryUrl, String taskId) {
		return CrucibleUtil.getReviewUrl(repositoryUrl, taskId);
	}

	@Override
	public boolean hasTaskChanged(TaskRepository taskRepository, ITask task, TaskData taskData) {

		if (taskData != null) {
			TaskAttribute hasChangedAttribute = taskData.getRoot().getAttribute(
					CrucibleConstants.HAS_CHANGED_TASKDATA_KEY);
			if (hasChangedAttribute != null) {
				if (!taskData.getAttributeMapper().getBooleanValue(hasChangedAttribute)) {

					TaskAttribute hashAttribute = taskData.getRoot().getAttribute(
							CrucibleConstants.CHANGED_HASH_CODE_KEY);
					if (hashAttribute != null) {
						int tdHash = taskData.getAttributeMapper().getIntegerValue(hashAttribute);
						int taskHash = tdHash;
						String taskHashString = task.getAttribute(CrucibleConstants.CHANGED_HASH_CODE_KEY);
						if (taskHashString != null) {
							try {
								taskHash = Integer.parseInt(taskHashString);
							} catch (NumberFormatException e) {
								//ignore
							}
						}
						/*else {
							return true;
						}*/

						return tdHash != taskHash;
					}

					return false;
				} else {
					return true;
				}
			}
		}

		// fall back for if we have a last modified date
		TaskMapper scheme = new TaskMapper(taskData);
		Date repositoryDate = scheme.getModificationDate();
		Date localDate = task.getModificationDate();
		if (repositoryDate != null && repositoryDate.equals(localDate)) {
			return false;
		}
		return true;
	}

	@Override
	public IStatus performQuery(TaskRepository repository, IRepositoryQuery query, TaskDataCollector resultCollector,
			ISynchronizationSession event, IProgressMonitor monitor) {
		CrucibleClient client = getClientManager().getClient(repository);
		try {
			client.performQuery(repository, query, resultCollector, monitor);
		} catch (CoreException e) {
			return e.getStatus();
		}
		return Status.OK_STATUS;
	}

	@Override
	public void updateRepositoryConfiguration(TaskRepository taskRepository, IProgressMonitor monitor)
			throws CoreException {
		CrucibleClient client = getClientManager().getClient(taskRepository);
		client.updateRepositoryData(monitor);

	}

	@Override
	public void updateTaskFromTaskData(TaskRepository taskRepository, ITask task, TaskData taskData) {
		TaskMapper scheme = new TaskMapper(taskData);
		scheme.applyTo(task);
		task.setCompletionDate(scheme.getCompletionDate());

		TaskAttribute hashAttribute = taskData.getRoot().getAttribute(CrucibleConstants.CHANGED_HASH_CODE_KEY);
		if (hashAttribute != null) {
			int hash = taskData.getAttributeMapper().getIntegerValue(hashAttribute);
			task.setAttribute(CrucibleConstants.CHANGED_HASH_CODE_KEY, String.valueOf(hash));
		}

		// TODO notify listeners if there was a change and make a popup happen
	}

	@Override
	public boolean canSynchronizeTask(TaskRepository taskRepository, ITask task) {
		return false;
	}
}
