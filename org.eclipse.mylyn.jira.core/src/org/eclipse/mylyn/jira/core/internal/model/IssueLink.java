/*******************************************************************************
 * Copyright (c) 2005 Jira Dashboard project.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Brock Janiczak - initial API and implementation
 *******************************************************************************/
package org.eclipse.mylar.jira.core.internal.model;

import java.io.Serializable;

public class IssueLink implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String issueKey;

	private final String linkTypeId;

	public IssueLink(String issueKey, String linkTypeId) {
		this.issueKey = issueKey;
		this.linkTypeId = linkTypeId;
	}

	public String getIssueKey() {
		return this.issueKey;
	}

	public String getLinkTypeId() {
		return this.linkTypeId;
	}

}
