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

package com.atlassian.connector.eclipse.internal.cvs.ui;

import com.atlassian.connector.eclipse.team.ui.CrucibleFile;
import com.atlassian.connector.eclipse.team.ui.ICompareAnnotationModel;
import com.atlassian.connector.eclipse.team.ui.ICustomChangesetLogEntry;
import com.atlassian.connector.eclipse.team.ui.ITeamUiResourceConnector;
import com.atlassian.connector.eclipse.team.ui.LocalStatus;
import com.atlassian.connector.eclipse.team.ui.ScmRepository;
import com.atlassian.connector.eclipse.team.ui.TeamConnectorType;
import com.atlassian.theplugin.commons.crucible.api.UploadItem;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.CVSProviderPlugin;
import org.eclipse.team.internal.ccvs.core.ICVSFolder;
import org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.ICVSResource;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryRoot;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Connector to handle connecting to a CVS repository
 * 
 * @author Pawel Niewiadomiski
 * @author Wojciech Seliga
 */
@SuppressWarnings("restriction")
public class CvsTeamResourceConnector implements ITeamUiResourceConnector {

	public boolean isEnabled() {
		return true;
	}

	public boolean canHandleFile(String repoUrl, String filePath, IProgressMonitor monitor) {
		// @todo implement it
		return false;
	}

	public boolean openCompareEditor(String repoUrl, String newFilePath, String oldFilePath, String oldRevisionString,
			String newRevisionString, ICompareAnnotationModel annotationModel, final IProgressMonitor monitor)
			throws CoreException {
		// @todo implement it
		throw new CoreException(new Status(IStatus.ERROR, AtlassianCvsUiPlugin.PLUGIN_ID, NLS.bind(
				"Could not get revisions for {0}.", newFilePath)));
	}

	public SortedSet<Long> getRevisionsForFile(IFile file, IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(file);
		// @todo implement it
		throw new CoreException(new Status(IStatus.WARNING, AtlassianCvsUiPlugin.PLUGIN_ID,
				"Not implemented yet for CVS."));
	}

	public Collection<ScmRepository> getRepositories(IProgressMonitor monitor) {
		ICVSRepositoryLocation[] repositories = CVSProviderPlugin.getPlugin().getKnownRepositories();
		if (repositories == null) {
			return MiscUtil.buildArrayList();
		}

		List<ScmRepository> res = MiscUtil.buildArrayList(repositories.length);
		final RepositoryManager repositoryManager = CVSUIPlugin.getPlugin().getRepositoryManager();

		for (ICVSRepositoryLocation repo : repositories) {
			final RepositoryRoot root = repositoryManager.getRepositoryRootFor(repo);
			final String name = (root != null && root.getName() != null) ? root.getName() : null;
			res.add(new ScmRepository(repo.getLocation(true), name, this));
		}
		return res;
	}

	@NotNull
	public SortedSet<ICustomChangesetLogEntry> getLatestChangesets(@NotNull String repositoryUrl, int limit,
			IProgressMonitor monitor) throws CoreException {
		// @todo implement it
		throw new CoreException(new Status(IStatus.WARNING, AtlassianCvsUiPlugin.PLUGIN_ID,
				"Not implemented yet for CVS."));
	}

	public Map<IFile, SortedSet<Long>> getRevisionsForFiles(Collection<IFile> files, IProgressMonitor monitor)
			throws CoreException {
		// @todo implement it
		Assert.isNotNull(files);
		throw new CoreException(new Status(IStatus.WARNING, AtlassianCvsUiPlugin.PLUGIN_ID,
				"Not implemented yet for CVS."));
	}

	public IEditorPart openFile(String repoUrl, String filePath, String otherRevisionFilePath, String revisionString,
			String otherRevisionString, final IProgressMonitor monitor) throws CoreException {
		if (repoUrl == null) {
			throw new CoreException(new Status(IStatus.ERROR, AtlassianCvsUiPlugin.PLUGIN_ID,
					"No repository URL given.."));
		}
		// @todo implement it
		return null;
	}

	public boolean canHandleEditorInput(IEditorInput editorInput) {
		// @todo implement it
		return false;
	}

	public CrucibleFile getCorrespondingCrucibleFileFromEditorInput(IEditorInput editorInput, Review activeReview) {
		// @todo implement it
		return null;
	}

	public LocalStatus getLocalRevision(IResource resource) throws CoreException {
		final IProject project = resource.getProject();
		if (project == null) {
			return null;
		}

		if (CVSWorkspaceRoot.isSharedWithCVS(project)) {
			final ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
			final ResourceSyncInfo syncInfo = cvsResource.getSyncInfo();
			// syncInfo is null for projects (directly checked-out)
			final boolean isBinary = syncInfo != null && syncInfo.getKeywordMode().isBinary();

			final ICVSFolder folder = (ICVSFolder) CVSWorkspaceRoot.getCVSResourceFor(project);
			final FolderSyncInfo folderInfo = folder.getFolderSyncInfo();
			final String revision = syncInfo != null ? "".equals(syncInfo.getRevision()) ? null
					: syncInfo.getRevision() : null;

			return new LocalStatus(folderInfo.getRoot() + '/' + cvsResource.getRepositoryRelativePath(), revision,
					syncInfo == null || syncInfo.isAdded(), false, isBinary, false);
		}
		return null;
	}

	public ScmRepository getApplicableRepository(IResource resource) throws CoreException {
		final IProject project = resource.getProject();
		if (project == null) {
			return null;
		}
		if (CVSWorkspaceRoot.isSharedWithCVS(project)) {
			final ICVSFolder folder = (ICVSFolder) CVSWorkspaceRoot.getCVSResourceFor(project);
			final FolderSyncInfo folderInfo = folder.getFolderSyncInfo();
			return folderInfo != null ? new ScmRepository(folderInfo.getRoot(), null, this) : null;
		}
		return null;
	}

	public String getName() {
		return "CVS (FishEye only)";
	}

	public TeamConnectorType getType() {
		return TeamConnectorType.CVS;
	}

	public boolean haveMatchingResourcesRecursive(IResource[] roots, State filter) {
		return false;
	}

	public Collection<UploadItem> getUploadItemsForResources(IResource[] resources, @NotNull IProgressMonitor monitor)
			throws CoreException {
		return MiscUtil.buildArrayList();
	}

	@NotNull
	public IResource[] getMembersForContainer(@NotNull IContainer element) throws CoreException {
		return new IResource[0];
	}

	public List<IResource> getResourcesByFilterRecursive(IResource[] roots, State filter) {
		return MiscUtil.buildArrayList();
	}

	public boolean isResourceManagedBy(IResource resource) {
		if (!isEnabled()) {
			return false;
		}
		try {
			return CVSWorkspaceRoot.isSharedWithCVS(resource);
		} catch (CVSException e) {
			return false;
		}
	}

}
