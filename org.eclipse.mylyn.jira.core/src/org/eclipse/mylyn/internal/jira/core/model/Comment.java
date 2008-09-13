/*******************************************************************************
 * Copyright (c) 2004, 2008 Brock Janiczak and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Brock Janiczak - initial API and implementation
 *     Tasktop Technologies - improvements
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.core.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Represents an immutable comment that will be attached to an issue. Comments can not be changed once created.
 * 
 * @author Brock Janiczak
 */
public final class Comment implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String level;

	private final String comment;

	private final String author;

	private final Date created;

	private boolean markupDetected;

	public Comment(String comment, String author) {
		this(comment, author, "", new Date()); //$NON-NLS-1$
	}

	public Comment(String comment, String author, String level) {
		this(comment, author, level, new Date());
	}

	public Comment(String comment, String author, String level, Date created) {
		this.comment = comment;
		this.author = author;
		this.level = level;
		this.created = created;
	}

	public String getAuthor() {
		return this.author;
	}

	public String getComment() {
		return this.comment;
	}

	public Date getCreated() {
		return this.created;
	}

	public String getLevel() {
		return this.level;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return this.author + ": " + this.comment;
	}

	public boolean isMarkupDetected() {
		return markupDetected;
	}

	public void setMarkupDetected(boolean markupDetected) {
		this.markupDetected = markupDetected;
	}

}
