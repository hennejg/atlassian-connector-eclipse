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

package com.atlassian.connector.eclipse.internal.crucible.core;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.JDomDriver;

import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskMapper;

import java.util.List;

/**
 * Utility class for mapping between TaskData and Task
 * 
 * @author Shawn Minto
 */
public class CrucibleTaskMapper extends TaskMapper {

	public CrucibleTaskMapper(TaskData taskData, boolean createNonExistingAttributes) {
		super(taskData, createNonExistingAttributes);
	}

	public CrucibleTaskMapper(TaskData taskData) {
		super(taskData);
	}

	@Override
	public void setTaskKey(String key) {
		setValue(TaskAttribute.TASK_KEY, key);
	}

	@Override
	public boolean applyTo(ITask task) {
		boolean changed = super.applyTo(task);

		List<String> cc = getCc();
		if (cc != null && cc.size() > 0) {
			XStream xs = new XStream(new JDomDriver());
			task.setAttribute(TaskAttribute.USER_CC, xs.toXML(cc));
		}

		return changed;
	}
}
