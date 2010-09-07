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

package com.atlassian.connector.eclipse.internal.crucible.ui.views;

import com.atlassian.connector.commons.misc.IntRanges;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.AvatarImages.AvatarSize;
import com.atlassian.connector.eclipse.internal.crucible.ui.util.CommentUiUtil;
import com.atlassian.connector.eclipse.ui.commons.CustomToolTip;
import com.atlassian.connector.eclipse.ui.viewers.ICustomToolTipInfo;
import com.atlassian.connector.eclipse.ui.viewers.ICustomToolTipInfoProvider;
import com.atlassian.theplugin.commons.VersionedVirtualFile;
import com.atlassian.theplugin.commons.crucible.api.model.Comment;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.RepositoryType;
import com.atlassian.theplugin.commons.crucible.api.model.VersionedComment;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;
import java.util.Map;

public class ReviewExplorerInfoProvider implements ICustomToolTipInfoProvider {

	public ICustomToolTipInfo getToolTipInfo(Object hoverObject) {
		if (hoverObject instanceof Widget) {
			final Object data = ((Widget) hoverObject).getData();
			if (data != null) {
				if (data instanceof ReviewTreeNode) {
					final CrucibleFileInfo fileInfo = ((ReviewTreeNode) data).getCrucibleFileInfo();
					if (fileInfo != null) {
						return new ICustomToolTipInfo() {
							public boolean isContainer() {
								return false;
							}

							public void createToolTipArea(CustomToolTip tooltip, Composite composite) {

								VersionedVirtualFile newFile = fileInfo.getFileDescriptor();
								VersionedVirtualFile oldFile = fileInfo.getOldFileDescriptor();

								String details = newFile.getRevision();
								if (oldFile != null && oldFile.getRevision().length() != 0) {
									details += '-' + oldFile.getRevision();
								}

								if (fileInfo.getRepositoryType() == RepositoryType.SCM) {
									tooltip.addIconAndLabel(composite, null, "Post-commit review item for revisions:",
											true);
									tooltip.addIconAndLabel(composite, null, details);
								} else if (fileInfo.getRepositoryType() == RepositoryType.UPLOAD) {
									tooltip.addIconAndLabel(composite, null, "Pre-commit review item (" + details + ")");
								}
							}
						};
					}
				} else if (data instanceof Comment) {
					assert Display.getCurrent() != null;
					final Comment comment = (Comment) data;
					final Image avatarImage = CrucibleUiPlugin.getDefault().getAvatarsCache().getAvatarOrDefaultImage(
							comment.getAuthor(), AvatarSize.LARGE);

					return new ICustomToolTipInfo() {
						public boolean isContainer() {
							return false;
						}

						public void createToolTipArea(CustomToolTip tooltip, Composite composite) {
							tooltip.addIconAndLabel(composite, avatarImage, CommentUiUtil.getCommentInfoHeaderText(comment),
									true);

							if (data instanceof VersionedComment) {
								VersionedComment versionedComment = (VersionedComment) data;
								Map<String, IntRanges> ranges = versionedComment.getLineRanges();
								if (ranges == null || ranges.keySet() == null) {
									tooltip.addIconAndLabel(composite, null, "General File Comment", false);
								} else {
									final String infoText = CommentUiUtil.getCompactedLineInfoText(ranges);
									tooltip.addIconAndLabel(composite, null, infoText, false);
								}
							} else {
								tooltip.addIconAndLabel(composite, null,
										comment.getParentComment() != null ? "Comment Reply" : "General Comment",
										false);
							}

							ScrolledComposite scrolledComposite = createScrolledWikiTextComment(comment, composite);
							GridDataFactory.fillDefaults().span(2, 1).applyTo(scrolledComposite);

						}
					};

				}
			}
		}
		return null;
	}

	private ScrolledComposite createScrolledWikiTextComment(final Comment comment, Composite parent) {
		final int maxWidth = 600;
		final int maxHeight = 500;
		// scroll pane respecting maximum size
		final ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL) {
			public org.eclipse.swt.graphics.Point computeSize(int wHint, int hHint, boolean changed) {
				final Point size = super.computeSize(wHint, hHint, changed);
				if (size.x > maxWidth) {
					final Point size2 = super.computeSize(maxWidth, SWT.DEFAULT, changed);
					if (size2.y > maxHeight) {
						return new Point(maxWidth, maxHeight);
					}
					return size2;
				}
				return size;
			}
		};

		final Composite scrolledContent = new Composite(scrolledComposite, SWT.NONE);
		scrolledComposite.setContent(scrolledContent);
		scrolledContent.setLayout(new FillLayout());
		CommentUiUtil.createWikiTextControl(null, scrolledContent, comment);

		scrolledComposite.setExpandVertical(true);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				Rectangle r = scrolledComposite.getClientArea();
				scrolledComposite.setMinSize(scrolledContent.computeSize(r.width,
						SWT.DEFAULT));
			}
		});

		return scrolledComposite;
	}

}
