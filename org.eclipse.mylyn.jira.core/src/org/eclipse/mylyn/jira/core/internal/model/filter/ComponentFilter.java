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
package org.eclipse.mylar.jira.core.internal.model.filter;

import java.io.Serializable;

import org.eclipse.mylar.jira.core.internal.model.Component;

/**
 * Restricts to issues that have one of the specified components. This filter
 * can only be used in conjunction with a {@link ProjectFilter}. If no
 * components are specified it is assumed the user is looking for issues wih no
 * assigned components. If you are looking for issues with any component, don't
 * add a component filter.
 * 
 * @see com.gbst.jira.core.model.filter.ProjectFilter
 */
public class ComponentFilter implements Filter, Serializable {
	private static final long serialVersionUID = 1L;

	private final Component[] components;

	public ComponentFilter(Component[] components) {
		assert (components != null);
		this.components = components;
	}

	public Component[] getComponents() {
		return this.components;
	}

	public boolean hasNoComponent() {
		return components.length == 0;
	}

	ComponentFilter copy() {
		return new ComponentFilter(this.components);
	}
}
