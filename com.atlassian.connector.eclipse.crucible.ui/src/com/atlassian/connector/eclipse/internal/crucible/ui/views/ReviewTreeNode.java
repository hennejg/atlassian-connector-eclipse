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

import com.atlassian.theplugin.commons.crucible.api.model.CrucibleFileInfo;
import com.atlassian.theplugin.commons.util.MiscUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class ReviewTreeNode {
	@NotNull
	private List<ReviewTreeNode> children = MiscUtil.buildArrayList();

	@Nullable
	private CrucibleFileInfo cfi;

	@Nullable
	private String pathToken;

	@Nullable
	private final ReviewTreeNode parent;

	public ReviewTreeNode(@Nullable ReviewTreeNode parent, @Nullable String pathToken) {
		this.parent = parent;
		this.pathToken = pathToken;
	}

	@Nullable
	public CrucibleFileInfo getCrucibleFileInfo() {
		return cfi;
	}

	@NotNull
	public List<ReviewTreeNode> getChildren() {
		return children;
	}

	void add(String[] path, CrucibleFileInfo aCfi) {
		if (path.length == 0) {
			cfi = aCfi;
			return;
		}
		for (ReviewTreeNode child : children) {
			if (child.pathToken.equals(path[0])) {
				child.add(Arrays.copyOfRange(path, 1, path.length), aCfi);
				return;
			}
		}
		ReviewTreeNode newChild = new ReviewTreeNode(this, path[0]);
		children.add(newChild);
		newChild.add(Arrays.copyOfRange(path, 1, path.length), aCfi);
		return;
	}

	public void compact() {
		while (children.size() == 1) {
			ReviewTreeNode onlyChild = children.get(0);
			if (onlyChild.getCrucibleFileInfo() == null) {
				if (pathToken == null) {
					pathToken = onlyChild.pathToken;
				} else {
					pathToken = pathToken + "/" + onlyChild.pathToken;
				}
				children = onlyChild.children;
			} else {
				break;
			}
		}
		for (ReviewTreeNode child : children) {
			child.compact();
		}
	}

	public String getPathToken() {
		return pathToken;
	}

	@Override
	public String toString() {
		return pathToken;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cfi == null) ? 0 : cfi.hashCode());
		result = prime * result + ((pathToken == null) ? 0 : pathToken.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ReviewTreeNode other = (ReviewTreeNode) obj;
		if (!MiscUtil.isEqual(parent, other.parent)) {
			return false;
		}
		if (cfi == null) {
			if (other.cfi != null) {
				return false;
			}
		} else if (!cfi.equals(other.cfi)) {
			return false;
		}
		if (pathToken == null) {
			if (other.pathToken != null) {
				return false;
			}
		} else if (!pathToken.equals(other.pathToken)) {
			return false;
		}
		return true;
	}

//	@Override
//	public int hashCode() {
//		if (cfi != null) {
//			return cfi.hashCode();
//		}
//		if (pathToken != null) {
//			return pathToken.hashCode();
//		}
//		return super.hashCode();
//	}

}