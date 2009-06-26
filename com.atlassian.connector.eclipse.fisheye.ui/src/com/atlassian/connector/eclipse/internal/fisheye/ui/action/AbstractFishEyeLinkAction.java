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

package com.atlassian.connector.eclipse.internal.fisheye.ui.action;

import com.atlassian.connector.eclipse.fisheye.ui.IFishEyeResource;
import com.atlassian.connector.eclipse.fisheye.ui.preferences.FishEyePreferenceContextData;
import com.atlassian.connector.eclipse.fisheye.ui.preferences.FishEyePreferencePage;
import com.atlassian.connector.eclipse.fisheye.ui.preferences.NoMatchingFishEyeConfigurationException;
import com.atlassian.connector.eclipse.internal.fisheye.ui.FishEyeUiPlugin;
import com.atlassian.connector.eclipse.internal.fisheye.ui.dialogs.ErrorDialogWithHyperlink;
import com.atlassian.connector.eclipse.ui.team.RepositoryInfo;
import com.atlassian.connector.eclipse.ui.team.TeamUiUtils;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.text.source.LineRange;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.mylyn.internal.provisional.commons.ui.WorkbenchUtil;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("restriction")
public abstract class AbstractFishEyeLinkAction extends BaseSelectionListenerAction {

	protected static final class ResultBean {
		private final IResource resource;

		private final LineRange lineRange;

		private ResultBean(IResource resource, LineRange lineRange) {
			this.resource = resource;
			this.lineRange = lineRange;
		}
	}

	private IWorkbenchWindow workbenchWindow;

	private ResultBean selectionData;

	public AbstractFishEyeLinkAction(String text) {
		super(text);
	}

	public void dispose() {
	}

	public void init(IWorkbenchWindow window) {
		this.workbenchWindow = window;
	}

	public void run(IAction action) {
		if (selectionData != null) {
			if (selectionData.resource != null) {
				processResource(selectionData.resource, selectionData.lineRange, WorkbenchUtil.getShell());
			}

		}
	}

	private ResultBean getData(ISelection selection) {
		IResource resource = null;
		LineRange lineRange = null;
		if (selection instanceof IStructuredSelection) {
			final IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			if (structuredSelection.getFirstElement() instanceof IFishEyeResource) {
				IFishEyeResource a = (IFishEyeResource) structuredSelection.getFirstElement();
				resource = a.getResource();
				lineRange = a.getLineRange();

			} else if (structuredSelection.getFirstElement() instanceof IAdaptable) {
				resource = (IResource) ((IAdaptable) structuredSelection.getFirstElement()).getAdapter(IResource.class);
				lineRange = getJavaEditorSelection(structuredSelection);
			}
		} else {

			IEditorPart activeEditor = getActiveEditor();
			if (activeEditor != null) {
				IEditorInput editorInput = getEditorInputFromSelection(selection);
				if (editorInput != null) {
					resource = (IResource) editorInput.getAdapter(IResource.class);
				}
				// such call:
				//				lineRange = new LineRange(textSelection.getStartLine(), textSelection.getEndLine()
				//						- textSelection.getStartLine());
				// does not work (i.e. it returns previously selected text region rather than selected now ?!?
				lineRange = TeamUiUtils.getSelectedLineNumberRangeFromEditorInput(activeEditor,
						activeEditor.getEditorInput());
			}
		}
		return new ResultBean(resource, lineRange);
	}

	public void selectionChanged(IAction action, ISelection selection) {
		if (action.isEnabled()) {
			selectionData = getData(selection);
			boolean isEnabled;
			try {
				isEnabled = selectionData.resource != null
						&& TeamUiUtils.getLocalRevision(selectionData.resource) != null;
			} catch (CoreException e) {
				isEnabled = false;
			}
			action.setEnabled(isEnabled);
			setEnabled(selectionData.resource != null);
		} else {
			selectionData = null;
		}

	}

	private IEditorPart getActiveEditor() {
		IWorkbenchWindow window = workbenchWindow;
		if (window == null) {
			window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		}
		if (window != null && window.getActivePage() != null) {
			return window.getActivePage().getActiveEditor();
		}
		return null;
	}

	private IEditorInput getEditorInputFromSelection(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = ((IStructuredSelection) selection);
			if (structuredSelection.getFirstElement() instanceof IEditorInput) {
				return (IEditorInput) structuredSelection.getFirstElement();
			}
		}
		return null;
	}

	@Nullable
	private LineRange getJavaEditorSelection(ISelection selection) {
		IEditorPart editorPart = getActiveEditor();
		IEditorInput editorInput = getEditorInputFromSelection(selection);
		if (editorInput != null && editorPart != null) {
			return TeamUiUtils.getSelectedLineNumberRangeFromEditorInput(editorPart, editorInput);
		}
		return null;
	}

	protected abstract void processUrl(String url);

	private void processResource(IResource resource, LineRange lineRange, final Shell shell) {
		try {
			final String url = FishEyeUiPlugin.getDefault().getFishEyeSettingsManager().buildFishEyeUrl(resource,
					lineRange);
			processUrl(url);
		} catch (final NoMatchingFishEyeConfigurationException e) {
			final RepositoryInfo repoInfo = TeamUiUtils.getApplicableRepository(resource);
			String scmPathToConfigure = (repoInfo != null) ? repoInfo.getScmPath() : null;
			handleException(resource, shell, e.getMessage(), new FishEyePreferenceContextData(scmPathToConfigure));
		} catch (CoreException e) {
			handleException(resource, shell, e.getMessage(), null);
		}
	}

	private void handleException(IResource resource, final Shell shell, String message,
			@Nullable final FishEyePreferenceContextData contextData) {
		new ErrorDialogWithHyperlink(shell, FishEyeUiPlugin.PRODUCT_NAME, "Cannot build FishEye URL for "
				+ resource.getName() + ": " + message, "<a>Configure FishEye Settings</a>", new Runnable() {
			public void run() {
				final PreferenceDialog prefDialog = PreferencesUtil.createPreferenceDialogOn(shell,
						FishEyePreferencePage.ID, null, contextData);
				if (prefDialog != null) {
					prefDialog.open();
				}
			}
		}).open();

	}
}