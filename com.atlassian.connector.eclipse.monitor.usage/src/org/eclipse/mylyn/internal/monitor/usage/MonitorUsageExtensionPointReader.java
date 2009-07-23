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

package org.eclipse.mylyn.internal.monitor.usage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.monitor.usage.AbstractStudyBackgroundPage;
import org.eclipse.mylyn.monitor.usage.AbstractStudyQuestionnairePage;

class MonitorUsageExtensionPointReader {

	private static final long HOUR = 3600 * 1000;

	public static final String EXTENSION_ID_STUDY = "com.atlassian.connector.eclipse.monitor.usage.study"; //$NON-NLS-1$

	public static final String ELEMENT_COLLECTOR = "usageCollector"; //$NON-NLS-1$

	public static final String ELEMENT_COLLECTOR_UPLOAD_URL = "uploadUrl"; //$NON-NLS-1$

	public static final String ELEMENT_COLLECTOR_QUESTIONNAIRE = "questionnaire"; //$NON-NLS-1$

	public static final String ELEMENT_UI = "ui"; //$NON-NLS-1$

	public static final String ELEMENT_UI_TITLE = "title"; //$NON-NLS-1$

	public static final String ELEMENT_UI_DESCRIPTION = "description"; //$NON-NLS-1$

	public static final String ELEMENT_UI_UPLOAD_PROMPT = "daysBetweenUpload"; //$NON-NLS-1$

	public static final String ELEMENT_UI_QUESTIONNAIRE_PAGE = "questionnairePage"; //$NON-NLS-1$

	public static final String ELEMENT_UI_BACKGROUND_PAGE = "backgroundPage"; //$NON-NLS-1$

	public static final String ELEMENT_UI_CONSENT_FORM = "consentForm"; //$NON-NLS-1$

	public static final String ELEMENT_UI_CONTACT_CONSENT_FIELD = "useContactField"; //$NON-NLS-1$

	public static final String ELEMENT_MONITORS = "monitors"; //$NON-NLS-1$

	public static final String ELEMENT_MONITORS_BROWSER_URL = "browserUrlFilter"; //$NON-NLS-1$

	private static final String ELEMENT_COLLECTOR_EVENT_FILTERS = "eventFilters";

	private boolean extensionsRead = false;

	private final Collection<UsageCollector> usageCollectors = new ArrayList<UsageCollector>();

	private final Collection<FormParameters> forms = new ArrayList<FormParameters>();

	private Collection<MonitorParameters> monitors;

	public Collection<UsageCollector> getUsageCollectors() {
		if (!extensionsRead) {
			readExtensions();
		}
		return usageCollectors;
	}

	public void readExtensions() {
		try {
			if (!extensionsRead) {
				IExtensionRegistry registry = Platform.getExtensionRegistry();
				IExtensionPoint extensionPoint = registry.getExtensionPoint(EXTENSION_ID_STUDY);
				if (extensionPoint != null) {
					IExtension[] extensions = extensionPoint.getExtensions();
					for (IExtension extension : extensions) {
						IConfigurationElement[] elements = extension.getConfigurationElements();
						for (IConfigurationElement element : elements) {
							if (element.getName().compareTo(ELEMENT_COLLECTOR) == 0) {
								readUsageCollector(element);
							} else if (element.getName().compareTo(ELEMENT_UI) == 0) {
								readForms(element);
							} else if (element.getName().compareTo(ELEMENT_MONITORS) == 0) {
								readMonitors(element);
							}
						}
					}
					extensionsRead = true;
				}
			}
		} catch (Throwable t) {
			StatusHandler.log(new Status(IStatus.ERROR, UiUsageMonitorPlugin.ID_PLUGIN,
					Messages.UiUsageMonitorPlugin_49, t));
		}
	}

	private void readUsageCollector(IConfigurationElement element) {
		String uploadUrl = element.getAttribute(ELEMENT_COLLECTOR_UPLOAD_URL);
		String eventFilters = element.getAttribute(ELEMENT_COLLECTOR_EVENT_FILTERS);
		Collection<String> filters = new ArrayList<String>();

		if (eventFilters != null) {
			filters.addAll(Arrays.asList(eventFilters.split(",")));
		}

		usageCollectors.add(new UsageCollector(uploadUrl, filters));
	}

	private void readForms(IConfigurationElement element) throws CoreException {
		FormParameters form = new FormParameters();

		form.setCustomizingPlugin(element.getContributor().getName());
		form.setTitle(element.getAttribute(ELEMENT_UI_TITLE));
		form.setDescription(element.getAttribute(ELEMENT_UI_DESCRIPTION));

		if (element.getAttribute(ELEMENT_UI_UPLOAD_PROMPT) != null) {
			Integer uploadInt = new Integer(element.getAttribute(ELEMENT_UI_UPLOAD_PROMPT));
			form.setTransmitPromptPeriod(HOUR * 24 * uploadInt);
		}

		form.setUseContactField(Boolean.parseBoolean(element.getAttribute(ELEMENT_UI_CONTACT_CONSENT_FIELD)));

		try {
			if (element.getAttribute(ELEMENT_UI_QUESTIONNAIRE_PAGE) != null) {
				Object questionnaireObject = element.createExecutableExtension(ELEMENT_UI_QUESTIONNAIRE_PAGE);
				if (questionnaireObject instanceof AbstractStudyQuestionnairePage) {
					AbstractStudyQuestionnairePage page = (AbstractStudyQuestionnairePage) questionnaireObject;
					form.setQuestionnairePage(page);
				}
			} else {
				UiUsageMonitorPlugin.getDefault().setQuestionnaireEnabled(false);
			}
		} catch (Throwable e) {
			StatusHandler.log(new Status(IStatus.ERROR, UiUsageMonitorPlugin.ID_PLUGIN,
					Messages.UiUsageMonitorPlugin_50, e));
			UiUsageMonitorPlugin.getDefault().setQuestionnaireEnabled(false);
		}

		try {
			if (element.getAttribute(ELEMENT_UI_BACKGROUND_PAGE) != null) {
				Object backgroundObject = element.createExecutableExtension(ELEMENT_UI_BACKGROUND_PAGE);
				if (backgroundObject instanceof AbstractStudyBackgroundPage) {
					AbstractStudyBackgroundPage page = (AbstractStudyBackgroundPage) backgroundObject;
					form.setBackgroundPage(page);
					UiUsageMonitorPlugin.getDefault().setBackgroundEnabled(true);
				}
			} else {
				UiUsageMonitorPlugin.getDefault().setBackgroundEnabled(false);
			}
		} catch (Throwable e) {
			StatusHandler.log(new Status(IStatus.ERROR, UiUsageMonitorPlugin.ID_PLUGIN,
					Messages.UiUsageMonitorPlugin_51, e));
			UiUsageMonitorPlugin.getDefault().setBackgroundEnabled(false);
		}

		form.setFormsConsent("/" + element.getAttribute(ELEMENT_UI_CONSENT_FORM)); //$NON-NLS-1$

		forms.add(form);
	}

	private void readMonitors(IConfigurationElement element) throws CoreException {
		MonitorParameters monitor = new MonitorParameters();
		// TODO: This should parse a list of filters but right now it takes
		// the
		// entire string as a single filter.
		// ArrayList<String> urlList = new ArrayList<String>();
		Collection<String> urls = new ArrayList<String>();
		urls.add(element.getAttribute(ELEMENT_MONITORS_BROWSER_URL));
		monitor.setAcceptedUrlList(urls);

		monitors.add(monitor);
	}

	public Collection<MonitorParameters> getMonitors() {
		return monitors;
	}

	public Collection<FormParameters> getForms() {
		return forms;
	}
}
