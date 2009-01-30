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

package com.atlassian.connector.eclipse.internal.crucible.ui.commons;

import com.atlassian.connector.eclipse.internal.crucible.core.client.model.CrucibleCachedUser;
import com.atlassian.theplugin.commons.crucible.api.model.User;
import com.atlassian.theplugin.commons.crucible.api.model.UserBean;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

/**
 * @author Shawn Minto
 */
public class UserContentProviderTest extends TestCase {

	public void testUserContentProvider() {
		CrucibleUserContentProvider contentProvider = new CrucibleUserContentProvider();

		String userName1 = "username";
		String userName2 = "username2";
		String displayName1 = "displayName";
		User user1 = new UserBean(userName1, displayName1);
		User user2 = new UserBean(userName2);

		CrucibleCachedUser cachedUser1 = new CrucibleCachedUser(user1);
		CrucibleCachedUser cachedUser2 = new CrucibleCachedUser(user2);

		Set<CrucibleCachedUser> cachedUsers = new HashSet<CrucibleCachedUser>();
		cachedUsers.add(cachedUser1);
		cachedUsers.add(cachedUser2);

		assertFalse(contentProvider.hasChildren(cachedUser1));
		assertEquals(0, contentProvider.getChildren(cachedUser1).length);
		assertNull(contentProvider.getParent(cachedUser1));

		Object[] elements = contentProvider.getElements(cachedUsers);
		assertNotNull(elements);
		assertEquals(2, elements.length);

	}

}
