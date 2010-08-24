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

package com.atlassian.connector.eclipse.internal.monitor.core.operations;

import java.io.File;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.commons.core.ZipFileUtil;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Constants;

import com.atlassian.connector.eclipse.internal.monitor.core.InteractionEventLogger;
import com.atlassian.connector.eclipse.internal.monitor.core.Messages;
import com.atlassian.connector.eclipse.internal.monitor.core.MonitorCorePlugin;
import com.atlassian.connector.eclipse.internal.monitor.core.StudyParameters;
import com.atlassian.connector.eclipse.internal.ui.AtlassianBundlesInfo;
import com.atlassian.connector.eclipse.monitor.core.InteractionEvent;

public final class UsageDataUploadJob extends Job {

	public static final String SUBMISSION_LOG_FILE_NAME = "submittedUsageLogs.txt"; //$NON-NLS-1$

	private final boolean ifTimeElapsed;

	private static int processedFileCount = 1;

	public UsageDataUploadJob(boolean ignoreLastTransmit) {
		super(Messages.UsageSubmissionWizard_upload_usage_data);
		this.ifTimeElapsed = ignoreLastTransmit;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			monitor.beginTask(Messages.UsageSubmissionWizard_uploading_usage_stats, 3);
			if (ifTimeElapsed) {
				performUpload(monitor);
			} else {
				checkLastTransmitTimeAndRun(monitor);
			}
			monitor.done();
			return Status.OK_STATUS;
		} catch (Exception e) {
			Status status = new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN, IStatus.ERROR,
					Messages.UsageSubmissionWizard_error_uploading, e);
			StatusHandler.log(status);
			return status;
		}
	}

	private void checkLastTransmitTimeAndRun(IProgressMonitor monitor) {
		final MonitorCorePlugin plugin = MonitorCorePlugin.getDefault();

		Date lastTransmit = plugin.getPreviousTransmitDate();
		if (lastTransmit == null) {
			lastTransmit = new Date();
			plugin.setPreviousTransmitDate(lastTransmit);
		}

		final Date currentTime = new Date();

		if (currentTime.getTime() > lastTransmit.getTime() + plugin.getTransmitPromptPeriod()
				&& plugin.isMonitoringEnabled()) {

			// time must be stored right away into preferences, to prevent
			// other threads
			plugin.setPreviousTransmitDate(currentTime);

			performUpload(monitor);
		}
	}

	private static final String SYSTEM_INFO_PREFIX = "system info: ";

	private void logPlatformDetails(InteractionEventLogger log) {
		log.interactionObserved(InteractionEvent.makePreference(MonitorCorePlugin.ID_PLUGIN, SYSTEM_INFO_PREFIX
				+ "os-arch", Platform.getOSArch()));
		log.interactionObserved(InteractionEvent.makePreference(MonitorCorePlugin.ID_PLUGIN, SYSTEM_INFO_PREFIX + "os",
				Platform.getOS()));
		log.interactionObserved(InteractionEvent.makePreference(MonitorCorePlugin.ID_PLUGIN, SYSTEM_INFO_PREFIX
				+ Platform.PI_RUNTIME, Platform.getBundle(Platform.PI_RUNTIME).getHeaders().get(
				Constants.BUNDLE_VERSION).toString()));
		log.interactionObserved(InteractionEvent.makePreference(MonitorCorePlugin.ID_PLUGIN, SYSTEM_INFO_PREFIX
				+ "connector-version", MonitorCorePlugin.getDefault().getBundle().getHeaders().get(
				Constants.BUNDLE_VERSION).toString()));
	}

	private void logInstalledFeatures(InteractionEventLogger log) {
		log.interactionObserved(InteractionEvent.makePreference(MonitorCorePlugin.ID_PLUGIN, SYSTEM_INFO_PREFIX
				+ "plugins", AtlassianBundlesInfo.getAllInstalledBundles().toString()));
	}

	private void performUpload(IProgressMonitor monitor) {
		InteractionEventLogger interactionLogger = MonitorCorePlugin.getDefault().getInteractionLogger();
		logPlatformDetails(interactionLogger);
		logInstalledFeatures(interactionLogger);

		MonitorCorePlugin.setPerformingUpload(true);
		MonitorCorePlugin.getDefault().getInteractionLogger().stopMonitoring();
		boolean failed = false;
		try {
			final StudyParameters params = MonitorCorePlugin.getDefault().getStudyParameters();

			File zipFile = zipFilesForUpload(monitor);
			if (zipFile == null) {
				return;
			}

			if (!upload(params, zipFile, monitor)) {
				failed = true;
			}

			if (zipFile.exists()) {
				zipFile.delete();
			}
		} finally {
			if (!failed) {
				// clear the log on success (so we don't send duplicates)
				try {
					MonitorCorePlugin.getDefault().getInteractionLogger().clearInteractionHistory();
				} catch (IOException e) {
					StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN,
							"Failed to clear the Usage Data log", e));
				}
			}
			MonitorCorePlugin.getDefault().getInteractionLogger().startMonitoring();
			MonitorCorePlugin.setPerformingUpload(false);
		}
		return;
	}

	private File zipFilesForUpload(IProgressMonitor monitor) {
		List<File> files = new ArrayList<File>();
		File monitorFile = MonitorCorePlugin.getDefault().getMonitorLogFile();
		File fileToUpload;
		try {
			fileToUpload = this.processMonitorFile(monitorFile);
		} catch (IOException e1) {
			StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN,
					Messages.UsageSubmissionWizard_error_uploading, e1));
			return null;
		}
		files.add(fileToUpload);

		try {
			File zipFile = File.createTempFile(MonitorCorePlugin.getDefault().getUserId() + ".", ".zip"); //$NON-NLS-1$ //$NON-NLS-2$
			ZipFileUtil.createZipFile(zipFile, files, monitor);
			return zipFile;
		} catch (Exception e) {
			StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN,
					Messages.UsageSubmissionWizard_error_uploading, e));
			return null;
		}
	}

	private File processMonitorFile(File monitorFile) throws IOException {
		File processedFile = File.createTempFile(String.format("processed-%s%d.", MonitorCorePlugin.MONITOR_LOG_NAME,
				processedFileCount++), ".xml");
		InteractionEventLogger logger = new InteractionEventLogger(processedFile);
		logger.startMonitoring();
		List<InteractionEvent> eventList = logger.getHistoryFromFile(monitorFile);

		if (eventList.size() > 0) {
			for (InteractionEvent event : eventList) {
				logger.interactionObserved(event);
			}
		}

		return processedFile;
	}

	/**
	 * Method to upload a file to a cgi script
	 * 
	 * @param f
	 *            The file to upload
	 * @return true on success
	 */
	private boolean upload(StudyParameters collector, File f, IProgressMonitor monitor) {
		int status = 0;

		try {
			final PostMethod filePost = new PostMethod(collector.getUploadUrl());
			try {
				Part[] parts = { new FilePart("temp.txt", f, "application/zip", FilePart.DEFAULT_CHARSET) }; //$NON-NLS-1$

				filePost.setRequestEntity(new MultipartRequestEntity(parts, filePost.getParams()));

				final HttpClient client = new HttpClient();

				status = client.executeMethod(filePost);
			} finally {
				filePost.releaseConnection();
			}
		} catch (final Exception e) {
			// there was a problem with the file upload so throw up an error
			// dialog to inform the user and log the exception
			if (e instanceof NoRouteToHostException || e instanceof UnknownHostException) {
				StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN,
						Messages.UsageSubmissionWizard_no_network, e));
			} else {
				StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN,
						Messages.UsageSubmissionWizard_unknown_exception, e));
			}
			return false;
		}

		monitor.worked(1);

		if (status == 401) {
			StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN, NLS.bind(
					Messages.UsageSubmissionWizard_invalid_uid, f.getName(), "")));
		} else if (status == 407) {
			StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN,
					Messages.UsageSubmissionWizard_proxy_authentication));
		} else if (status != 200) {
			// there was a problem with the file upload so throw up an error
			// dialog to inform the user
			StatusHandler.log(new Status(IStatus.ERROR, MonitorCorePlugin.ID_PLUGIN, NLS.bind(
					Messages.UsageSubmissionWizard_30, f.getName(), status)));
		} else {
			// the file was uploaded successfully
			return true;
		}
		return false;
	}

}