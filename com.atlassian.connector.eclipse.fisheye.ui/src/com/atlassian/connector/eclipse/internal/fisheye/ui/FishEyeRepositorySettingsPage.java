package com.atlassian.connector.eclipse.internal.fisheye.ui;

import com.atlassian.connector.eclipse.internal.commons.ui.dialogs.RemoteApiLockedDialog;
import com.atlassian.connector.eclipse.internal.fisheye.core.FishEyeClientManager;
import com.atlassian.connector.eclipse.internal.fisheye.core.FishEyeCorePlugin;
import com.atlassian.connector.eclipse.internal.fisheye.core.client.FishEyeClient;
import com.atlassian.connector.eclipse.internal.fisheye.core.client.FishEyeClientData;
import com.atlassian.theplugin.commons.remoteapi.CaptchaRequiredException;
import com.atlassian.theplugin.commons.remoteapi.RemoteApiException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.net.Policy;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.mylyn.internal.tasks.core.IRepositoryConstants;
import org.eclipse.mylyn.tasks.core.RepositoryTemplate;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.ui.wizards.AbstractRepositorySettingsPage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class FishEyeRepositorySettingsPage extends AbstractRepositorySettingsPage {

	private class FishEyeValidator extends Validator {

		private final FishEyeClientManager clientManager;

		private final TaskRepository repository;

		public FishEyeValidator(TaskRepository repository, FishEyeClientManager clientManager) {
			this.repository = repository;
			this.clientManager = clientManager;
		}

		@Override
		public void run(IProgressMonitor monitor) throws CoreException {
			FishEyeClient client = null;
			try {
				client = clientManager.createTempClient(repository, new FishEyeClientData());
				client.validate(Policy.backgroundMonitorFor(monitor), repository);
			} catch (CoreException e) {
				if (e.getCause() != null && e.getCause() instanceof RemoteApiException
						&& e.getCause().getCause() != null && e.getCause().getCause() instanceof IOException
						&& e.getCause().getCause().getMessage().contains("HTTP 404")) {
					setStatus(new Status(IStatus.ERROR, FishEyeUiPlugin.PLUGIN_ID,
							"HTTP 404 (Not Found) - Seems like the server you entered isn't FishEye", e));
				} else if (e.getCause() instanceof CaptchaRequiredException) {
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							new RemoteApiLockedDialog(WorkbenchUtil.getShell(), repository.getRepositoryUrl()).open();
						}
					});
					setStatus(new Status(IStatus.ERROR,	FishEyeUiPlugin.PLUGIN_ID, "You've been locked out from remote API.", e));
				} else {
					setStatus(e.getStatus());
				}
				return;
			} finally {
				if (client != null) {
					clientManager.deleteTempClient(client.getServerData());
				}
			}
		}
	}

	public FishEyeRepositorySettingsPage(TaskRepository taskRepository) {
		super("FishEye Repository Settings", "Enter FishEye server information", taskRepository);
		setNeedsHttpAuth(true);
		setNeedsEncoding(false);
		setNeedsAnonymousLogin(false);
		setNeedsAdvanced(false);
	}

	@Override
	public void applyTo(final TaskRepository repository) {
		this.repository = applyToValidate(repository);
		repository.setProperty(IRepositoryConstants.PROPERTY_CATEGORY, IRepositoryConstants.CATEGORY_OTHER);
	}

	/**
	 * Helper method for distinguishing between hitting Finish and Validate (because Validation leads to calling applyTo
	 * in the superclass)
	 */
	public TaskRepository applyToValidate(TaskRepository repository) {
		// PLE-1120 MigrateToSecureStorageJob.migrateToSecureStorage(repository);
		super.applyTo(repository);
		return repository;
	}

	@Override
	public TaskRepository createTaskRepository() {
		TaskRepository repository = new TaskRepository(connector.getConnectorKind(), getRepositoryUrl());
		return applyToValidate(repository);
	}

	@Override
	protected void createAdditionalControls(Composite parent) {
		addRepositoryTemplatesToServerUrlCombo();
	}

	@Override
	protected void createContributionControls(Composite parent) {
	}

	@Override
	public String getConnectorKind() {
		return FishEyeCorePlugin.CONNECTOR_KIND;
	}

	@Override
	protected Validator getValidator(TaskRepository repository) {
		return new FishEyeValidator(repository, FishEyeCorePlugin.getDefault()
				.getRepositoryConnector()
				.getClientManager());
	}

	@Override
	protected boolean isValidUrl(String name) {
		if (name.startsWith(URL_PREFIX_HTTPS) || name.startsWith(URL_PREFIX_HTTP)) {
			try {
				new URL(name);
				return true;
			} catch (MalformedURLException e) {
				// ignore
			}
		}
		return false;
	}

	@Override
	protected void repositoryTemplateSelected(RepositoryTemplate template) {
		repositoryLabelEditor.setStringValue(template.label);
		setUrl(template.repositoryUrl);
		getContainer().updateButtons();
	}

}
