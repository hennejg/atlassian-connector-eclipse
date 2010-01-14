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

package com.atlassian.connector.eclipse.internal.crucible.ui.editor.parts;

import com.atlassian.connector.commons.misc.IntRanges;
import com.atlassian.connector.eclipse.internal.crucible.IReviewChangeListenerAction;
import com.atlassian.connector.eclipse.internal.crucible.core.CrucibleUtil;
import com.atlassian.connector.eclipse.internal.crucible.ui.CrucibleUiPlugin;
import com.atlassian.connector.eclipse.internal.crucible.ui.IReviewAction;
import com.atlassian.connector.eclipse.team.ui.CrucibleFile;
import com.atlassian.theplugin.commons.VersionedVirtualFile;
import com.atlassian.theplugin.commons.crucible.ValueNotYetInitialized;
import com.atlassian.theplugin.commons.crucible.api.model.Comment;
import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.crucible.api.model.Review;
import com.atlassian.theplugin.commons.crucible.api.model.VersionedComment;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.forms.widgets.Section;
import org.jetbrains.annotations.Nullable;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A UI part to represent a general comment in a review
 * 
 * @author Shawn Minto
 */
public class VersionedCommentPart extends CommentPart<Comment, VersionedCommentPart> {

	private VersionedComment versionedComment;

	private CrucibleFileInfo crucibleFileInfo;

	private final List<IReviewAction> customActions;

	private Composite composite;

	private final List<IReviewChangeListenerAction> reviewActions = new ArrayList<IReviewChangeListenerAction>();

	public VersionedCommentPart(VersionedComment comment, Review review, CrucibleFileInfo crucibleFileInfo) {
		super(comment, review, new CrucibleFile(crucibleFileInfo, false));
		this.versionedComment = comment;
		this.crucibleFileInfo = crucibleFileInfo;
		customActions = new ArrayList<IReviewAction>();
	}

	@Override
	protected String getSectionHeaderText() {
		String headerText = versionedComment.getAuthor().getDisplayName() + "   ";
		headerText += DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(
				versionedComment.getCreateDate());
		return headerText;
	}

	@Override
	protected Composite createSectionContents(Section section, FormToolkit toolkit) {
		composite = super.createSectionContents(section, toolkit);

		updateChildren(composite, toolkit, false, versionedComment.getReplies());
		return composite;
	}

	@Override
	protected String getAnnotationText() {

		String text = super.getAnnotationText();
		if ((comment.isDefectRaised() || comment.isDefectApproved()) && !comment.isReply()) {
			text += "DEFECT ";
		}

		if (!comment.isReply()) {
			text += getLineNumberText(crucibleFileInfo);
		}
		return text;
	}

	private String getLineInfo(IntRanges intRanges, @Nullable String revision) {
		String revStr = (revision != null) ? ("Rev: " + revision + ", ") : "";
		if (intRanges.getTotalMin() == intRanges.getTotalMax()) {
			return revStr + "Line: " + intRanges.getTotalMin();
		} else {
			return revStr + "Lines: " + intRanges.toNiceString();
		}
	}

	/**
	 * We base here on the fact that <code>lineRanges</code> is in fact {@link LinkedHashMap} and preserve ordering. We
	 * cannot reasonably sort revisions, as they e.g. for CVS can be whatever.
	 * 
	 * @param lineRanges
	 * @return
	 */
	private IntRanges getLastLineRange(Map<String, IntRanges> lineRanges) {
		final Iterator<String> it = lineRanges.keySet().iterator();
		String candidate = null;
		while (it.hasNext()) {
			candidate = it.next();
		}
		return lineRanges.get(candidate);
	}

	private String getLineNumberText(CrucibleFileInfo crucibleFileInfo) {
		Set<String> displayedRevisions = new HashSet<String>();
		final VersionedVirtualFile toFile = crucibleFileInfo.getFileDescriptor();
		if (toFile != null && toFile.getRevision() != null) {
			displayedRevisions.add(toFile.getRevision());
		}
		final VersionedVirtualFile fromFile = crucibleFileInfo.getOldFileDescriptor();
		if (fromFile != null && fromFile.getRevision() != null) {
			displayedRevisions.add(fromFile.getRevision());
		}

		// new Crucible 2.1 API for iterative reviews - let's handle it at least partially
		final Map<String, IntRanges> lineRanges = versionedComment.getLineRanges();
		if (lineRanges != null && !lineRanges.isEmpty()) {
			final boolean omitRevisions = displayedRevisions.containsAll(lineRanges.keySet());
			if (omitRevisions) {
				// if all line ranges are identical in each revision - just display one
				final Set<IntRanges> uniqueSet = MiscUtil.buildHashSet(lineRanges.values());
				if (uniqueSet.size() == 1) {
					return "[" + getLineInfo(uniqueSet.iterator().next(), null) + "]";
				}
			}

			final StringBuilder builder = new StringBuilder("[");
			final Iterator<String> it = lineRanges.keySet().iterator();
			while (it.hasNext()) {
				final String revision = it.next();
				final IntRanges intRanges = lineRanges.get(revision);
				builder.append(getLineInfo(intRanges, omitRevisions ? null : revision));

				if (it.hasNext()) {
					builder.append("; ");
				}
			}
			builder.append("]");
			return builder.toString();
		}
		if (versionedComment.isToLineInfo()) {
			return "[" + getLineInfo(versionedComment.getToLineRanges(), null) + "]";
		} else if (versionedComment.isFromLineInfo()) {
			return "[" + getLineInfo(versionedComment.getFromLineRanges(), null) + "]";
		} else {
			return "[General File]";
		}
	}

	@Override
	protected void createCustomAnnotations(Composite toolbarComposite, FormToolkit toolkit) {

		reviewActions.clear();

		for (IReviewAction customAction : customActions) {
			ImageHyperlink textHyperlink = toolkit.createImageHyperlink(toolbarComposite, SWT.NONE);
			textHyperlink.setText(" ");
			textHyperlink.setEnabled(false);
			textHyperlink.setUnderlined(false);

			createActionHyperlink(toolbarComposite, toolkit, customAction);
		}
	}

	@Override
	protected List<IReviewAction> getToolbarActions(boolean isExpanded) {
		List<IReviewAction> actions = new ArrayList<IReviewAction>();
		actions.addAll(super.getToolbarActions(isExpanded));

		return actions;
	}

	public void addCustomAction(IReviewAction action) {
		customActions.add(action);
	}

	@Override
	protected Control update(Composite parentComposite, FormToolkit toolkit, Comment newComment, Review newReview) {
		this.crucibleReview = newReview;
		if (reviewActions != null) {

			try {
				Set<CrucibleFileInfo> files;
				files = crucibleReview.getFiles();
				// FIXME we need new file here with refreshed comments collection (we have to find it as we do not get as param)
				// workaround for PLE-727 (expandable part generic is an obstacle to solve that in the right way)
				for (CrucibleFileInfo file : files) {
					if (file.equals(crucibleFileInfo)) {
						this.crucibleFileInfo = file;
						break;
					}
				}
			} catch (ValueNotYetInitialized e) {
				// we can do nothing here but there should be at least one file as we are inside VersionedCommentPart)
				StatusHandler.log(new Status(IStatus.ERROR, CrucibleUiPlugin.PLUGIN_ID,
						"Missing files in the processed review"));
			}

			for (IReviewChangeListenerAction reviewAction : reviewActions) {
				reviewAction.updateReview(newReview, crucibleFileInfo, (VersionedComment) newComment);
			}
		}
		// TODO update the text 
		if (newComment instanceof VersionedComment
				&& !CrucibleUtil.areVersionedCommentsDeepEquals((VersionedComment) newComment, versionedComment)) {
			if (newComment instanceof VersionedComment) {
				this.versionedComment = (VersionedComment) newComment;
			}
			this.comment = newComment;

			Control createControl = createOrUpdateControl(parentComposite, toolkit);

			return createControl;

		}
		return getSection();
	}

	// TODO handle changed highlighting properly

	protected final Control createOrUpdateControl(Composite parentComposite, FormToolkit toolkit) {
		Control createdControl = null;
		if (getSection() == null) {

			Control newControl = createControl(parentComposite, toolkit);

			setIncomming(true);

			createdControl = newControl;
		} else {

			if (commentTextComposite != null && !commentTextComposite.isDisposed()) {
				Composite parent = commentTextComposite.getParent();
				commentTextComposite.dispose();
				createCommentArea(toolkit, composite);
				if (parent.getChildren().length > 0) {
					commentTextComposite.moveAbove(parent.getChildren()[0]);
				}

			}
			updateChildren(composite, toolkit, true, versionedComment.getReplies());

			createdControl = getSection();
		}

		if (sectionClient != null && !sectionClient.isDisposed()) {
			sectionClient.clearCache();
		}
		getSection().layout(true, true);

		update();

		return createdControl;

	}

	@Override
	protected VersionedCommentPart createChildPart(Comment comment, Review crucibleReview2) {
		return new VersionedCommentPart((VersionedComment) comment, crucibleReview2, crucibleFileInfo);
	}

	@Override
	protected Comparator<Comment> getComparator() {
		return new Comparator<Comment>() {

			public int compare(Comment o1, Comment o2) {
				if (o1 != null && o2 != null) {
					return o1.getCreateDate().compareTo(o2.getCreateDate());
				}
				return 0;
			}

		};
	}

	@Override
	protected boolean represents(Comment comment) {
		return versionedComment.getPermId().equals(comment.getPermId());
	}

}
