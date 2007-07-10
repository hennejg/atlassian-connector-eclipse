/*******************************************************************************
 * Copyright (c) 2004, 2007 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.internal.jira.core.model;

import java.io.Serializable;

/**
 * @author Brock Janiczak
 */
public class Group implements Serializable {
	private static final long serialVersionUID = 1L;

	private String name;

	private User[] users;

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public User[] getUsers() {
		return this.users;
	}

	public void setUsers(User[] users) {
		this.users = users;
	}
}
