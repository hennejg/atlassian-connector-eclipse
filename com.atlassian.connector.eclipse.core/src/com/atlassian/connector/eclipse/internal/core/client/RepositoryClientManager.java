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
package com.atlassian.connector.eclipse.internal.core.client;

import com.atlassian.connector.eclipse.internal.core.AtlassianCorePlugin;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.tasks.core.IRepositoryListener;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.TaskRepositoryLocationFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Steffen Pingel
 * @since 3.1
 */
public abstract class RepositoryClientManager<T, C extends Serializable> implements IRepositoryListener {

	private final Map<String, T> clientByUrl = new HashMap<String, T>();

	private final Map<String, C> clientDataByUrl = new HashMap<String, C>();

	private final File cacheFile;

	private TaskRepositoryLocationFactory taskRepositoryLocationFactory;

	public RepositoryClientManager(File cacheFile) {
		Assert.isNotNull(cacheFile);
		this.cacheFile = cacheFile;
		readCache();
	}

	public synchronized T getClient(TaskRepository taskRepository) {
		Assert.isNotNull(taskRepository);
		T client = clientByUrl.get(taskRepository.getRepositoryUrl());
		if (client == null) {
			C data = clientDataByUrl.get(taskRepository.getRepositoryUrl());
			if (data == null) {
				data = createRepositoryConfiguration();
				clientDataByUrl.put(taskRepository.getRepositoryUrl(), data);
			}

			client = createClient(taskRepository, data);
			clientByUrl.put(taskRepository.getRepositoryUrl(), client);
		}
		return client;
	}

	protected abstract C createRepositoryConfiguration();

	protected abstract T createClient(TaskRepository taskRepository, C data);

	public void repositoriesRead() {
		// ignore
	}

	public synchronized void repositoryAdded(TaskRepository repository) {
	}

	protected void removeClient(TaskRepository repository, Map<String, T> clientByUrl, Map<String, C> clientDataByUrl) {
		clientByUrl.remove(repository.getRepositoryUrl());
	}

	public synchronized void repositoryRemoved(TaskRepository repository) {
		removeClient(repository, clientByUrl, clientDataByUrl);
		clientDataByUrl.remove(repository.getRepositoryUrl());
	}

	public synchronized void repositorySettingsChanged(TaskRepository repository) {
		removeClient(repository, clientByUrl, clientDataByUrl);
	}

	@SuppressWarnings("unchecked")
	public void readCache() {
		if (cacheFile == null || !cacheFile.exists()) {
			return;
		}

		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new FileInputStream(cacheFile));
			int size = in.readInt();
			for (int i = 0; i < size; i++) {
				String url = (String) in.readObject();
				C data = (C) in.readObject();
				if (url != null && data != null) {
					clientDataByUrl.put(url, data);
				}
			}
		} catch (Throwable e) {
			StatusHandler.log(new Status(IStatus.WARNING, AtlassianCorePlugin.PLUGIN_ID,
					"The respository configuration cache could not be read", e));
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

	}

	public void writeCache() {
		updateClientDataMap(clientByUrl, clientDataByUrl);
		if (cacheFile == null) {
			return;
		}

		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(new FileOutputStream(cacheFile));
			out.writeInt(clientDataByUrl.size());
			for (String url : clientDataByUrl.keySet()) {
				out.writeObject(url);
				out.writeObject(clientDataByUrl.get(url));
			}
		} catch (IOException e) {
			StatusHandler.log(new Status(IStatus.WARNING, AtlassianCorePlugin.PLUGIN_ID,
					"The respository configuration cache could not be written", e));
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}

	/*
	 * temporary fix for the broken/not-working serialization mechanism 
	 */
	protected abstract void updateClientDataMap(Map<String, T> clientByUrl, Map<String, C> clientDataByUrl);

	public TaskRepositoryLocationFactory getTaskRepositoryLocationFactory() {
		return taskRepositoryLocationFactory;
	}

	public void setTaskRepositoryLocationFactory(TaskRepositoryLocationFactory taskRepositoryLocationFactory) {
		this.taskRepositoryLocationFactory = taskRepositoryLocationFactory;
	}

	public void repositoryUrlChanged(TaskRepository repository, String oldUrl) {
		// ignore
	}

}
