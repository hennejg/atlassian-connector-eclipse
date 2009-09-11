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
/*******************************************************************************
 * Copyright (c) 2003, 2006 Subclipse project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Subclipse project committers - initial API and implementation
 ******************************************************************************/

package com.atlassian.connector.eclipse.internal.crucible.ui.wizards;

import com.atlassian.connector.eclipse.ui.AtlassianUiPlugin;
import com.atlassian.connector.eclipse.ui.team.ITeamResourceConnector;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Page to select a patch file. Overriding validatePage was necessary to allow entering a file name that already exists.
 */
public class WorkspacePatchSelectionPage extends WizardPage {
	private CheckboxTreeViewer changeViewer;

	private Object[] initialSelection;

	private final List<IResource> roots = new ArrayList<IResource>();

	private Object[] realSelection;

	private final Set<ITeamResourceConnector> teamConnectors;

	private ITeamResourceConnector selectedTeamConnector;

	private final ReviewWizard wizard;

	private ComboViewer scmViewer;

	public WorkspacePatchSelectionPage(@NotNull TaskRepository taskRepository, @NotNull ReviewWizard wizard,
			@NotNull List<IResource> roots) {
		super("Add Workspace Patch to Review");
		setTitle("Add Workspace Patch to Review");
		setDescription("Attach a patch from the workspace to the review.");

		this.wizard = wizard;
		this.roots.addAll(roots);
		this.teamConnectors = AtlassianUiPlugin.getDefault().getTeamResourceManager().getTeamConnectors();
	}

	public IResource[] getSelection() {
		return Arrays.asList(this.realSelection).toArray(new IResource[this.realSelection.length]);
	}

	/**
	 * Allow the user to chose to save the patch to the workspace or outside of the workspace.
	 */
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(false).margins(5, 5).create());
		GridDataFactory.fillDefaults().grab(true, true).applyTo(composite);

		Dialog.applyDialogFont(composite);
		initializeDialogUnits(composite);
		setControl(composite);

		Label label = new Label(composite, SWT.NONE);
		label.setText("Select SCM provider:");
		GridDataFactory.fillDefaults().grab(false, false).applyTo(label);
		scmViewer = new ComboViewer(composite);
		scmViewer.getCombo().setText("Select SCM provider");
		scmViewer.setContentProvider(new ArrayContentProvider());
		scmViewer.setSorter(new ViewerSorter());
		scmViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ITeamResourceConnector) {
					return ((ITeamResourceConnector) element).getName();
				}
				return super.getText(element);
			}
		});
		scmViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection = (IStructuredSelection) scmViewer.getSelection();
				if (selection.isEmpty()) {
					return;
				}

				selectedTeamConnector = (ITeamResourceConnector) selection.getFirstElement();

				changeViewer.resetFilters();
				changeViewer.addFilter(new ViewerFilter() {
					public boolean select(Viewer viewer, Object parentElement, Object element) {
						if (element instanceof IResource && selectedTeamConnector != null) {
							IResource resource = (IResource) element;
							IPath resourcePath = resource.getFullPath();
							for (IResource root : WorkspacePatchSelectionPage.this.roots) {
								IPath rootPath = root.getFullPath();
								if (rootPath.isPrefixOf(resourcePath) || resourcePath.isPrefixOf(rootPath)) {
									if (selectedTeamConnector.checkForResourcesPresenceRecursive(
											new IResource[] { resource }, ITeamResourceConnector.State.SF_ANY_CHANGE)) {
										return true;
									}
								}

							}
						}
						return false;
					}
				});

				changeViewer.expandAll();
				changeViewer.setAllChecked(true);
				realSelection = initialSelection = changeViewer.getCheckedElements();

				validatePage();
			}
		});

		label = new Label(composite, SWT.NONE);
		label.setText("Include changes:");

		changeViewer = new CheckboxTreeViewer(composite, SWT.BORDER);
		GridDataFactory.fillDefaults().span(2, 1).hint(SWT.DEFAULT, 220).grab(true, true).applyTo(
				changeViewer.getControl());
		changeViewer.setContentProvider(new WorkbenchContentProvider() {
			public Object[] getChildren(Object element) {
				if (element instanceof IContainer) {
					try {
						IResource[] children = selectedTeamConnector.getMembersForContainer((IContainer) element);
						if (children != null && children.length != 0) {
							return children;
						}
					} catch (Exception e) {
						// do nothing
					}
				}
				return super.getChildren(element);
			}
		});
		changeViewer.setLabelProvider(new WorkbenchLabelProvider());
		changeViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				if (event.getChecked()) {
					IResource resource = (IResource) event.getElement();
					if (resource.getType() != IResource.FILE) {
						IPath path = resource.getFullPath();
						for (Object current : WorkspacePatchSelectionPage.this.initialSelection) {
							if (path.isPrefixOf(((IResource) current).getFullPath())) {
								WorkspacePatchSelectionPage.this.changeViewer.setChecked(current, true);
								WorkspacePatchSelectionPage.this.changeViewer.setGrayed(current, false);
							}
						}
					}
					while ((resource = resource.getParent()).getType() != IResource.ROOT) {
						boolean hasUnchecked = false;
						IPath path = resource.getFullPath();
						for (Object element : WorkspacePatchSelectionPage.this.initialSelection) {
							IResource current = (IResource) element;
							if (path.isPrefixOf(current.getFullPath()) && current != resource) {
								hasUnchecked |= !WorkspacePatchSelectionPage.this.changeViewer.getChecked(current);
							}
						}
						if (!hasUnchecked) {
							WorkspacePatchSelectionPage.this.changeViewer.setGrayed(resource, false);
							WorkspacePatchSelectionPage.this.changeViewer.setChecked(resource, true);
						}
					}
				} else {
					IResource resource = (IResource) event.getElement();
					if (resource.getType() != IResource.FILE) {
						IPath path = resource.getFullPath();
						for (Object element : WorkspacePatchSelectionPage.this.initialSelection) {
							IResource current = (IResource) element;
							if (path.isPrefixOf(current.getFullPath())) {
								WorkspacePatchSelectionPage.this.changeViewer.setChecked(current, false);
							}
						}
					}
					while ((resource = resource.getParent()).getType() != IResource.ROOT) {
						WorkspacePatchSelectionPage.this.changeViewer.setGrayed(resource, true);
					}
				}
				WorkspacePatchSelectionPage.this.realSelection = WorkspacePatchSelectionPage.this.changeViewer.getCheckedElements();
			}
		});
		changeViewer.setUseHashlookup(true);
		changeViewer.setInput(ResourcesPlugin.getWorkspace().getRoot());

		MenuManager menuMgr = new MenuManager();
		Menu menu = menuMgr.createContextMenu(changeViewer.getTree());
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager menuMgr) {
				fillTreeMenu(menuMgr);
			}
		});
		menuMgr.setRemoveAllWhenShown(true);
		changeViewer.getTree().setMenu(menu);

		Button updateData = new Button(composite, SWT.PUSH);
		updateData.setText("Update Repository Data");
		GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.BEGINNING).applyTo(updateData);
		updateData.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				wizard.updateCache(WorkspacePatchSelectionPage.this);
			}
		});

		// update selection after all wiring has been done
		scmViewer.setInput(teamConnectors);
		scmViewer.setSelection(new StructuredSelection(scmViewer.getElementAt(0)));
	}

	void setAllChecked(boolean state) {
		changeViewer.setAllChecked(state);
	}

	protected void fillTreeMenu(IMenuManager menuMgr) {
		Action selectAllAction = new Action("Select all") {
			public void run() {
				setAllChecked(true);
			}
		};
		menuMgr.add(selectAllAction);
		Action deselectAllAction = new Action("Deselect all") { //$NON-NLS-1$
			public void run() {
				setAllChecked(false);
			}
		};
		menuMgr.add(deselectAllAction);
	}

	private void validatePage() {
		setErrorMessage(null);

		boolean allFine = true;
		String errorMessage = null;

		if (getSelectedTeamResourceConnector() == null) {
			errorMessage = "Please select SCM provider";
			allFine = false;
		}

		/*FIXME: if (patchText.getText().length() < 1) {
			errorMessage = "In order to create a review from a patch,"
					+ " copy the patch to the clipboard before opening this Wizard.";
			allFine = false;
		} else if (selectedRepository == null) {
			errorMessage = "Choose a repository on Crucible this patch relates to.";
			allFine = false;
		}*/

		if (!allFine) {
			setPageComplete(false);
			if (errorMessage != null) {
				setErrorMessage(errorMessage);
			}
		} else {
			setPageComplete(true);
		}

		getContainer().updateButtons();
	}

	public ITeamResourceConnector getSelectedTeamResourceConnector() {
		return selectedTeamConnector;
	}

}