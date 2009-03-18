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

package com.atlassian.connector.eclipse.internal.bamboo.ui.actions;

import com.atlassian.connector.eclipse.internal.bamboo.core.BambooCorePlugin;
import com.atlassian.connector.eclipse.internal.bamboo.ui.BambooImages;
import com.atlassian.connector.eclipse.internal.bamboo.ui.operations.RunBuildJob;
import com.atlassian.theplugin.commons.bamboo.BambooBuild;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.mylyn.tasks.ui.TasksUi;
import org.eclipse.swt.widgets.Display;

/**
 * Action for invoking a build run on the server
 * 
 * @author Thomas Ehrnhoefer
 */
public class RunBuildAction extends AbstractBambooAction {

	public RunBuildAction(ISelectionProvider selectionProvider) {
		super(selectionProvider);
		initialize();
	}

	public RunBuildAction(BambooBuild build) {
		super(build);
		initialize();
	}

	private void initialize() {
		setText("Run Build");
		setToolTipText("Run Build on Server");
		setImageDescriptor(BambooImages.RUN_BUILD);
	}

	@Override
	public void run() {
		final BambooBuild build = getBuild();
		if (build != null) {
			RunBuildJob job = new RunBuildJob(build, TasksUi.getRepositoryManager().getRepository(
					BambooCorePlugin.CONNECTOR_KIND, build.getServerUrl()));
			job.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent event) {
					if (event.getResult().getCode() == IStatus.ERROR) {
						Display.getDefault().syncExec(new Runnable() {
							public void run() {
								MessageDialog.openError(null, getText(), "Running build " + build.getPlanKey()
										+ " failed. See error log for details.");
							}
						});
					}
				}
			});
			job.schedule();
		}
	}

	@Override
	protected boolean updateSelection(IStructuredSelection selection) {
		if (selection.size() != 1) {
			return false;
		}
		BambooBuild build = (BambooBuild) selection.iterator().next();
		if (build != null) {
			try {
				build.getNumber();
				return build.getEnabled();
			} catch (UnsupportedOperationException e) {
				return false;
			}
		}
		return false;
	}
}