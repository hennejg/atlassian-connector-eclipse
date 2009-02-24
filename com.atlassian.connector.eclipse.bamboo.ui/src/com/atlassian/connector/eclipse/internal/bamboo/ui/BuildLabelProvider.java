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

package com.atlassian.connector.eclipse.internal.bamboo.ui;

import com.atlassian.theplugin.commons.bamboo.BambooBuild;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.mylyn.internal.provisional.commons.ui.CommonImages;
import org.eclipse.swt.graphics.Image;

public class BuildLabelProvider implements ILabelProvider {

	public Image getImage(Object element) {
		if (element instanceof BambooBuild) {
			if (((BambooBuild) element).getEnabled()) {
				if (((BambooBuild) element).getErrorMessage() != null) {
					return CommonImages.getImage(BambooImages.STATUS_DISABLED);
				}
				switch (((BambooBuild) element).getStatus()) {
				case FAILURE:
					return CommonImages.getImage(BambooImages.STATUS_FAILED);
				case SUCCESS:
					return CommonImages.getImage(BambooImages.STATUS_PASSED);
				}
			}
		}
		return CommonImages.getImage(BambooImages.STATUS_DISABLED);
	}

	public String getText(Object element) {
		StringBuilder builder = new StringBuilder();
		BambooBuild bambooBuild = (BambooBuild) element;
		if (bambooBuild.getBuildName() == null) {
			builder.append("N/A");
		} else {
			builder.append(bambooBuild.getBuildName());
			builder.append(" - ");
			builder.append(bambooBuild.getBuildKey());
			builder.append(" - ");
			try {
				builder.append(bambooBuild.getBuildNumber());
			} catch (UnsupportedOperationException e) {
				builder.append("N/A");
			}
		}
		return builder.toString();
	}

	public void addListener(ILabelProviderListener listener) {
		// ignore
	}

	public void dispose() {
		// ignore
	}

	public boolean isLabelProperty(Object element, String property) {
		// ignore
		return false;
	}

	public void removeListener(ILabelProviderListener listener) {
		// ignore
	}
}