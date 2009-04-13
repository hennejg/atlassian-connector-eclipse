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

package com.atlassian.connector.eclipse.internal.subclipse.ui;

import com.atlassian.connector.eclipse.internal.subclipse.ui.compare.CrucibleSubclipseCompareEditorInput;
import com.atlassian.connector.eclipse.ui.team.CrucibleFile;
import com.atlassian.connector.eclipse.ui.team.CustomChangeSetLogEntry;
import com.atlassian.connector.eclipse.ui.team.CustomRepository;
import com.atlassian.connector.eclipse.ui.team.ICompareAnnotationModel;
import com.atlassian.connector.eclipse.ui.team.ITeamResourceConnector;
import com.atlassian.connector.eclipse.ui.team.TeamUiUtils;
import com.atlassian.theplugin.commons.VersionedVirtualFile;
import com.atlassian.theplugin.commons.crucible.ValueNotYetInitialized;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.Review;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.tigris.subversion.subclipse.core.ISVNLocalFile;
import org.tigris.subversion.subclipse.core.ISVNLocalResource;
import org.tigris.subversion.subclipse.core.ISVNRemoteFile;
import org.tigris.subversion.subclipse.core.ISVNRemoteFolder;
import org.tigris.subversion.subclipse.core.ISVNRemoteResource;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.ISVNResource;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.commands.GetLogsCommand;
import org.tigris.subversion.subclipse.core.history.ILogEntry;
import org.tigris.subversion.subclipse.core.history.LogEntryChangePath;
import org.tigris.subversion.subclipse.core.resources.RemoteFile;
import org.tigris.subversion.subclipse.core.resources.SVNWorkspaceRoot;
import org.tigris.subversion.subclipse.ui.Policy;
import org.tigris.subversion.subclipse.ui.SVNUIPlugin;
import org.tigris.subversion.subclipse.ui.compare.ResourceEditionNode;
import org.tigris.subversion.subclipse.ui.editor.RemoteFileEditorInput;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Connector to handle connecting to a subclipse repository
 * 
 * @author Shawn Minto
 */
public class SubclipseTeamResourceConnector implements ITeamResourceConnector {

	public SubclipseTeamResourceConnector() {
	}

	public boolean isEnabled() {
		return true;
	}

	public boolean canHandleFile(String repoUrl, String filePath, IProgressMonitor monitor) {
		return true;
	}

	public boolean openCompareEditor(String repoUrl, String newFilePath, String oldFilePath, String oldRevisionString,
			String newRevisionString, ICompareAnnotationModel annotationModel, final IProgressMonitor monitor)
			throws CoreException {
		ISVNRemoteResource oldRemoteFile = getSvnRemoteFile(repoUrl, oldFilePath, newFilePath, oldRevisionString,
				newRevisionString, monitor);
		ISVNRemoteResource newRemoteFile = getSvnRemoteFile(repoUrl, newFilePath, oldFilePath, newRevisionString,
				oldRevisionString, monitor);

		if (oldRemoteFile != null && newRemoteFile != null) {
			ResourceEditionNode left = new ResourceEditionNode(newRemoteFile);
			ResourceEditionNode right = new ResourceEditionNode(oldRemoteFile);
			CompareEditorInput compareEditorInput = new CrucibleSubclipseCompareEditorInput(left, right,
					annotationModel);
			TeamUiUtils.openCompareEditorForInput(compareEditorInput);
			return true;
		}
		throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, NLS.bind(
				"Could not get revisions for {0}.", newFilePath)));
	}

	public SortedSet<Long> getRevisionsForFile(IFile file, IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(file);
		ISVNLocalResource local = SVNWorkspaceRoot.getSVNResourceFor(file);
		try {
			monitor.beginTask("Getting Revisions for " + file.getName(), IProgressMonitor.UNKNOWN);
			SVNRevision revision = SVNRevision.HEAD;
			ISVNRemoteResource remoteResource = local.getRemoteResource(revision);
			GetLogsCommand getLogsCommand = new GetLogsCommand(remoteResource, revision, new SVNRevision.Number(0),
					SVNRevision.HEAD, false, 0, null, true);
			getLogsCommand.run(monitor);
			ILogEntry[] logEntries = getLogsCommand.getLogEntries();
			SortedSet<Long> revisions = new TreeSet<Long>();
			for (ILogEntry logEntrie : logEntries) {
				revisions.add(new Long(logEntrie.getRevision().getNumber()));
			}
			return revisions;
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
					"Error while retrieving Revisions for file " + file.getName() + ".", e));
		}
	}

	public Map<CustomRepository, SortedSet<CustomChangeSetLogEntry>> getLatestChangesets(String repositoryUrl,
			int limit, IProgressMonitor monitor) {
		ISVNRepositoryLocation[] repos = SVNUIPlugin.getPlugin().getRepositoryManager().getKnownRepositoryLocations(
				monitor);
		monitor.beginTask("Retrieving changeset for SVN (subclipse) repositories", repos.length);
		Map<CustomRepository, SortedSet<CustomChangeSetLogEntry>> map = new HashMap<CustomRepository, SortedSet<CustomChangeSetLogEntry>>();
		for (ISVNRepositoryLocation repo : repos) {
			//if a repository is given and the repo does not match the given repository, skip it
			if (repositoryUrl != null && !repositoryUrl.equals(repo.getUrl().toString())) {
				continue;
			}
			IProgressMonitor subMonitor = org.eclipse.mylyn.commons.net.Policy.subMonitorFor(monitor, 1);
			CustomRepository customRepository = new CustomRepository(repo.getUrl().toString());
			SortedSet<CustomChangeSetLogEntry> changesets = new TreeSet<CustomChangeSetLogEntry>();
			ISVNRemoteFolder rootFolder = repo.getRootFolder();

			if (limit > 0) { //do not retrieve unlimited revisions
				subMonitor.beginTask("Retrieving changesets for " + repo.getLabel(), 101);
				GetLogsCommand getLogsCommand = new GetLogsCommand(rootFolder, SVNRevision.HEAD, SVNRevision.HEAD,
						new SVNRevision.Number(0), false, limit, null, true);
				try {
					getLogsCommand.run(subMonitor);
					ILogEntry[] logEntries = getLogsCommand.getLogEntries();
					for (ILogEntry logEntry : logEntries) {
						LogEntryChangePath[] logEntryChangePaths = logEntry.getLogEntryChangePaths();
						String[] changed = new String[logEntryChangePaths.length];
						for (int i = 0; i < logEntryChangePaths.length; i++) {
							changed[i] = logEntryChangePaths[i].getPath();

						}
						CustomChangeSetLogEntry customEntry = new CustomChangeSetLogEntry(logEntry.getComment(),
								logEntry.getAuthor(), logEntry.getRevision().toString(), logEntry.getDate(), changed,
								customRepository);
						changesets.add(customEntry);
					}
				} catch (SVNException e) {
					// ignore
				}
			}
			map.put(customRepository, changesets);
			subMonitor.done();
		}
		return map;

	}

	public Map<IFile, SortedSet<Long>> getRevisionsForFile(List<IFile> files, IProgressMonitor monitor)
			throws CoreException {
		Assert.isNotNull(files);

		Map<IFile, SortedSet<Long>> map = new HashMap<IFile, SortedSet<Long>>();

		monitor.beginTask("Getting Revisions", files.size());

		for (IFile file : files) {
			ISVNLocalResource local = SVNWorkspaceRoot.getSVNResourceFor(file);
			IProgressMonitor submonitor = Policy.subMonitorFor(monitor, 1);
			try {
				submonitor.beginTask("Getting revisions for " + file.getName(), IProgressMonitor.UNKNOWN);
				SVNRevision revision = SVNRevision.HEAD;
				GetLogsCommand getLogsCommand = new GetLogsCommand(local.getRemoteResource(revision), revision,
						new SVNRevision.Number(0), SVNRevision.HEAD, false, 0, null, true);
				getLogsCommand.run(submonitor);
				ILogEntry[] logEntries = getLogsCommand.getLogEntries();
				SortedSet<Long> revisions = new TreeSet<Long>();
				for (ILogEntry logEntrie : logEntries) {
					revisions.add(new Long(logEntrie.getRevision().getNumber()));
				}
				map.put(file, revisions);
			} catch (Exception e) {
				throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
						"Error while retrieving Revisions for file " + file.getName() + ".", e));
			} finally {
				submonitor.done();
			}
		}
		return map;
	}

	public ISVNRemoteFile getSvnRemoteFile(String repoUrl, String filePath, String otherRevisionFilePath,
			String revisionString, String otherRevisionString, final IProgressMonitor monitor) {
		if (repoUrl == null) {
			return null;
		}
		try {

			if (filePath.startsWith("/")) {
				filePath = filePath.substring(1);
			}

			IResource localResource = getLocalResourceFromFilePath(filePath);

			boolean localFileNotFound = localResource == null;

			if (localFileNotFound) {
				localResource = getLocalResourceFromFilePath(otherRevisionFilePath);
			}

			if (localResource != null) {
				SVNRevision svnRevision = SVNRevision.getRevision(revisionString);
				SVNRevision otherSvnRevision = SVNRevision.getRevision(otherRevisionString);
				return getRemoteFile(localResource, filePath, svnRevision, otherSvnRevision, localFileNotFound);
			}
		} catch (SVNException e) {
			StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e));
		} catch (ParseException e) {
			StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e));
		}
		return null;
	}

	public IEditorPart openFile(String repoUrl, String filePath, String otherRevisionFilePath, String revisionString,
			String otherRevisionString, final IProgressMonitor monitor) throws CoreException {
		if (repoUrl == null) {
			throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
					"No repository URL given.."));
		}
		try {

			IResource localResource = getLocalResourceFromFilePath(filePath);

			boolean localFileNotFound = localResource == null;

			if (localFileNotFound) {
				localResource = getLocalResourceFromFilePath(otherRevisionFilePath);
			}

			if (localResource != null) {
				SVNRevision svnRevision = SVNRevision.getRevision(revisionString);
				SVNRevision otherSvnRevision = SVNRevision.getRevision(otherRevisionString);
				ISVNLocalFile localFile = getLocalFile(localResource);

				if (localFile.getStatus().getLastChangedRevision().equals(svnRevision) && !localFile.isDirty()) {
					// the file is not dirty and we have the right local copy
					IEditorPart editorPart = TeamUiUtils.openLocalResource(localResource);
					if (editorPart == null) {
						throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
								NLS.bind("Could not open editor for {0}.", localFile.getName())));
					}
					return editorPart;

				} else {
					final ISVNRemoteFile remoteFile = getRemoteFile(localResource, filePath, svnRevision,
							otherSvnRevision, localFileNotFound);
					if (remoteFile != null) {
						// we need to open the remote resource since the file is either dirty or the wrong revision

						IEditorPart editorPart = null;
						if (Display.getCurrent() != null) {
							editorPart = openRemoteSvnFile(remoteFile, monitor);
						} else {
							final IEditorPart[] part = new IEditorPart[1];
							PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
								public void run() {
									part[0] = openRemoteSvnFile(remoteFile, monitor);
								}
							});
							editorPart = part[0];
						}
						if (editorPart == null) {
							throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
									NLS.bind("Could not open editor for {0}.", remoteFile.getName())));
						}
						return editorPart;
					} else {
						throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
								NLS.bind("Could not get remote file for {0}.", filePath)));
					}
				}
			} else {
				throw new CoreException(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, NLS.bind(
						"Could not find local resource for {0}.", filePath)));
			}
		} catch (SVNException e) {
			Status status = new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e);
			StatusHandler.log(status);
			throw new CoreException(status);
		} catch (ParseException e) {
			Status status = new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e);
			StatusHandler.log(status);
			throw new CoreException(status);
		}
	}

	public boolean canHandleEditorInput(IEditorInput editorInput) {
		if (editorInput instanceof FileEditorInput) {
			try {
				IFile file = ((FileEditorInput) editorInput).getFile();
				ISVNLocalFile localFile = getLocalFile(file);
				if (localFile != null && !localFile.isDirty()) {
					return true;
				}
			} catch (SVNException e) {
				StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
						"Unable to get svn information for local file.", e));
			}
		} else if (editorInput instanceof RemoteFileEditorInput) {
			return true;
		}
		return false;
	}

	public CrucibleFile getCorrespondingCrucibleFileFromEditorInput(IEditorInput editorInput, Review activeReview) {
		SVNUrl fileUrl = null;
		String revision = null;
		if (editorInput instanceof FileEditorInput) {
			// this is a local file that we know how to deal with
			try {
				IFile file = ((FileEditorInput) editorInput).getFile();
				ISVNLocalFile localFile = getLocalFile(file);
				if (localFile != null && !localFile.isDirty()) {
					fileUrl = localFile.getUrl();
					revision = localFile.getStatus().getLastChangedRevision().toString();
				}
			} catch (SVNException e) {
				StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
						"Unable to get svn information for local file.", e));
			}
		} else if (editorInput instanceof RemoteFileEditorInput) {
			// this is a remote file that we know how to deal with
			RemoteFileEditorInput input = (RemoteFileEditorInput) editorInput;
			ISVNRemoteFile remoteFile = input.getSVNRemoteFile();
			fileUrl = remoteFile.getUrl();
			revision = remoteFile.getRevision().toString();
		}

		if (fileUrl != null && revision != null) {
			try {
				for (CrucibleFileInfo file : activeReview.getFiles()) {
					VersionedVirtualFile fileDescriptor = file.getFileDescriptor();
					VersionedVirtualFile oldFileDescriptor = file.getOldFileDescriptor();
					SVNUrl newFileUrl = null;
					String newAbsoluteUrl = getAbsoluteUrl(fileDescriptor);
					if (newAbsoluteUrl != null) {
						newFileUrl = new SVNUrl(newAbsoluteUrl);
					}

					SVNUrl oldFileUrl = null;
					String oldAbsoluteUrl = getAbsoluteUrl(oldFileDescriptor);
					if (oldAbsoluteUrl != null) {
						oldFileUrl = new SVNUrl(oldAbsoluteUrl);
					}
					if ((newFileUrl != null && newFileUrl.equals(fileUrl))
							|| (oldFileUrl != null && oldFileUrl.equals(fileUrl))) {
						if (revision.equals(fileDescriptor.getRevision())) {
							return new CrucibleFile(file, false);
						}
						if (revision.equals(oldFileDescriptor.getRevision())) {
							return new CrucibleFile(file, true);
						}
						return null;
					}
				}
			} catch (ValueNotYetInitialized e) {
				StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID,
						"Review is not fully initialized.  Unable to get file from review.", e));
			} catch (MalformedURLException e) {
				// ignore
			}
		}
		return null;
	}

	private String getAbsoluteUrl(VersionedVirtualFile fileDescriptor) {
		//TODO might need some performance tweak, but works for now for M2
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {

			if (SVNWorkspaceRoot.isManagedBySubclipse(project)) {
				try {
					IPath fileIPath = new Path(fileDescriptor.getUrl());
					IResource resource = project.findMember(fileIPath);
					while (!fileIPath.isEmpty() && resource == null) {
						fileIPath = fileIPath.removeFirstSegments(1);
						resource = project.findMember(fileIPath);
					}
					if (resource == null) {
						continue;
					}

					ISVNResource projectResource = SVNWorkspaceRoot.getSVNResourceFor(resource);

					if (projectResource.getUrl().toString().endsWith(fileDescriptor.getUrl())) {
						return projectResource.getUrl().toString();
					}
				} catch (Exception e) {
					StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e));
				}
			}
		}
		return null;
	}

	private IEditorPart openRemoteSvnFile(ISVNRemoteFile remoteFile, IProgressMonitor monitor) {
		try {
			IWorkbench workbench = AtlassianSubclipseUiPlugin.getDefault().getWorkbench();
			IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();

			RemoteFileEditorInput editorInput = new CustomRemoteFileEditorInput(remoteFile, monitor);
			String editorId = getEditorId(workbench, remoteFile);
			return page.openEditor(editorInput, editorId);
		} catch (PartInitException e) {
			StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e));
		}
		return null;
	}

	private ISVNLocalFile getLocalFile(IResource localResource) throws SVNException {
		ISVNLocalResource local = SVNWorkspaceRoot.getSVNResourceFor(localResource);

		if (local.isManaged()) {
			return (ISVNLocalFile) local;
		}
		return null;
	}

	private ISVNRemoteFile getRemoteFile(IResource localResource, String filePath, SVNRevision svnRevision,
			SVNRevision otherSvnRevision, boolean localFileNotFound) throws ParseException, SVNException {

		ISVNLocalResource local = SVNWorkspaceRoot.getSVNResourceFor(localResource);

		if (local.isManaged()) {
			if (localFileNotFound) {
				//file has been moved, so we have to do some funky file retrieval
				ISVNRepositoryLocation location = local.getRepository();

				SVNUrl svnUrl = local.getUrl();

				if (otherSvnRevision instanceof SVNRevision.Number) {
					return new RemoteFile(null, location, svnUrl, svnRevision, (SVNRevision.Number) svnRevision,
							new Date(), "");
				} else {
					return new RemoteFile(null, location, svnUrl, svnRevision, SVNRevision.INVALID_REVISION,
							new Date(), "");
				}
			} else {
				return (ISVNRemoteFile) local.getRemoteResource(svnRevision);
			}
		}

		return null;
	}

	private IResource getLocalResourceFromFilePath(String filePath) {
		if (filePath == null || filePath.length() <= 0) {
			return null;
		}
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {

			if (SVNWorkspaceRoot.isManagedBySubclipse(project)) {
				try {
					IPath fileIPath = new Path(filePath);
					IResource resource = project.findMember(fileIPath);
					while (!fileIPath.isEmpty() && resource == null) {
						fileIPath = fileIPath.removeFirstSegments(1);
						resource = project.findMember(fileIPath);
					}
					if (resource == null) {
						continue;
					}

					ISVNResource projectResource = SVNWorkspaceRoot.getSVNResourceFor(resource);
					String url = projectResource.getUrl().toString();

					if (url.endsWith(filePath)) {
						return resource;
					}
				} catch (Exception e) {
					StatusHandler.log(new Status(IStatus.ERROR, AtlassianSubclipseUiPlugin.PLUGIN_ID, e.getMessage(), e));
				}
			}
		}
		return null;
	}

	private String getEditorId(IWorkbench workbench, ISVNRemoteFile file) {
		IEditorRegistry registry = workbench.getEditorRegistry();
		String filename = file.getName();
		IEditorDescriptor descriptor = registry.getDefaultEditor(filename);
		String id;
		if (descriptor == null) {
			descriptor = registry.findEditor(IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID);
		}
		if (descriptor == null) {
			id = "org.eclipse.ui.DefaultTextEditor"; //$NON-NLS-1$
		} else {
			id = descriptor.getId();
		}
		return id;
	}

// Code that can work if there is no file in the local workspace 
//	private RemoteFile getRemoteFile(String repoUrl, String filePath, String revisionString,
//	ISVNRepositoryLocation location) throws MalformedURLException, ParseException, SVNException {
//RemoteFile file;
//SVNUrl svnUrl = new SVNUrl(repoUrl).appendPath(filePath);
//SVNRevision svnRevision = SVNRevision.getRevision(revisionString);
//
//ISVNClientAdapter svnClient = location.getSVNClient();
//ISVNInfo info = null;
//try {
//	if (location.getRepositoryRoot().equals(svnUrl)) {
//		file = new RemoteFile(location, svnUrl, svnRevision);
//	} else {
//		info = svnClient.getInfo(svnUrl, svnRevision, svnRevision);
//	}
//} catch (SVNClientException e) {
//	throw new SVNException("Can't get latest remote resource for " + svnUrl);
//}
//
//if (info == null) {
//	file = null;//new RemoteFile(location, svnUrl, svnRevision);
//} else {
//	file = new RemoteFile(
//			null, // we don't know its parent
//			location, svnUrl, svnRevision, info.getLastChangedRevision(), info.getLastChangedDate(),
//			info.getLastCommitAuthor());
//}
//return file;
//}
}
