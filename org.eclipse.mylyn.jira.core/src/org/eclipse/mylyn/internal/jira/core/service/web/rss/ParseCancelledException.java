/*******************************************************************************
 * Copyright (c) 2007 Mylar committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Brock Janiczak - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.internal.jira.core.service.web.rss;

import org.xml.sax.SAXException;

/**
 * @author	Brock Janiczak
 */
class ParseCancelledException extends SAXException {

	private static final long serialVersionUID = 1L;

	public ParseCancelledException(String message) {
		super(message);
	}
}