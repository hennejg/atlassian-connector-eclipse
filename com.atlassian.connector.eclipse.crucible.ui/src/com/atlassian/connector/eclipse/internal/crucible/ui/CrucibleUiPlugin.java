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

import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleCorePlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.annotations.CrucibleEditorTracker;
import com.atlassian.connector.eclipse.internal.crucible.ui.annotations.EditorLinkWithReviewSelectionListener;
import com.atlassian.connector.eclipse.internal.crucible.ui.notifications.CrucibleNotificationProvider;

import org.eclipse.mylyn.monitor.ui.MonitorUi;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 * 
 * @author Shawn Minto
 */
public class CrucibleUiPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.atlassian.connector.eclipse.crucible.ui";

	// The shared instance
	private static CrucibleUiPlugin plugin;

	private CrucibleEditorTracker crucibleEditorTracker;

	private ActiveReviewManager activeReviewManager;

	private CrucibleNotificationProvider crucibleNotificationManager;

	private EditorLinkWithReviewSelectionListener editorLinkWithReviewSelectionListener;

	/**
	 * The constructor
	 */
	public CrucibleUiPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		TasksUi.getRepositoryManager().addListener(CrucibleCorePlugin.getRepositoryConnector().getClientManager());

		crucibleEditorTracker = new CrucibleEditorTracker();

		activeReviewManager = new ActiveReviewManager(true);

		enableActiveReviewManager();

		crucibleNotificationManager = new CrucibleNotificationProvider();
		CrucibleCorePlugin.getDefault().getReviewCache().addCacheChangedListener(crucibleNotificationManager);

		// TODO determine if we should be doing this differently and not through mylyn
		MonitorUi.addWindowPartListener(crucibleEditorTracker);
		editorLinkWithReviewSelectionListener = new EditorLinkWithReviewSelectionListener();
		MonitorUi.addWindowPostSelectionListener(editorLinkWithReviewSelectionListener);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		CrucibleCorePlugin.getDefault().getReviewCache().removeCacheChangedListener(crucibleNotificationManager);
		crucibleNotificationManager = null;

		disableActiveReviewManager();

		MonitorUi.removeWindowPostSelectionListener(editorLinkWithReviewSelectionListener);
		MonitorUi.removeWindowPartListener(crucibleEditorTracker);
		crucibleEditorTracker.dispose();
		crucibleEditorTracker = null;
		editorLinkWithReviewSelectionListener = null;

		activeReviewManager.dispose();
		activeReviewManager = null;
		plugin = null;
		super.stop(context);

	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static CrucibleUiPlugin getDefault() {
		return plugin;
	}

	public ActiveReviewManager getActiveReviewManager() {
		return activeReviewManager;
	}

	/**
	 * Method for testing purposes
	 */
	public void disableActiveReviewManager() {
		if (activeReviewManager != null) {
			TasksUi.getTaskActivityManager().removeActivationListener(activeReviewManager);
		}
	}

	/**
	 * Method for testing purposes
	 */
	public void enableActiveReviewManager() {
		if (activeReviewManager != null) {
			TasksUi.getTaskActivityManager().addActivationListener(activeReviewManager);
		}
	}
}
