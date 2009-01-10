/*******************************************************************************
 * Copyright (c) 2008 Atlassian and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Atlassian - initial API and implementation
 ******************************************************************************/

package com.atlassian.connector.eclipse.internal.bamboo.core.client;

import com.atlassian.theplugin.commons.cfg.Server;
import com.atlassian.theplugin.commons.cfg.ServerCfg;
import com.atlassian.theplugin.commons.exception.HttpProxySettingsException;
import com.atlassian.theplugin.commons.remoteapi.rest.AbstractHttpSession;
import com.atlassian.theplugin.commons.remoteapi.rest.HttpSessionCallback;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.mylyn.commons.net.AbstractWebLocation;
import org.eclipse.mylyn.commons.net.WebUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of HttpSessionCallback that can handle setting the HttpClient information on a per-server basis
 * 
 * @author Shawn Minto
 */
public class BambooHttpSessionCallback implements HttpSessionCallback {

	private final Map<ServerCfg, HttpClient> httpClients;

	private final MultiThreadedHttpConnectionManager connectionManager;

	public BambooHttpSessionCallback() {
		this.httpClients = new HashMap<ServerCfg, HttpClient>();
		this.connectionManager = new MultiThreadedHttpConnectionManager();
		WebUtil.addConnectionManager(connectionManager);
	}

	public synchronized HttpClient getHttpClient(Server server) throws HttpProxySettingsException {
		HttpClient httpClient = httpClients.get(server);

		// TODO handle the case where we dont have a client initialized
		assert (httpClient != null);

		return httpClient;
	}

	public void configureHttpMethod(AbstractHttpSession session, HttpMethod method) {
		// we don't need to do anything here right now	
	}

	public synchronized void removeClient(ServerCfg serverCfg) {
		httpClients.remove(serverCfg);
	}

	public synchronized HttpClient initialize(AbstractWebLocation location, ServerCfg serverCfg) {
		HttpClient httpClient = httpClients.get(serverCfg);
		if (httpClient == null) {
			httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
			httpClients.put(serverCfg, httpClient);
		}
		initializeHttpClient(location, httpClient);
		return httpClient;
	}

	private void initializeHttpClient(AbstractWebLocation location, HttpClient httpClient) {
		HostConfiguration hostConfiguration = WebUtil.createHostConfiguration(httpClient, location,
				new NullProgressMonitor());
		httpClient.setHostConfiguration(hostConfiguration);
		httpClient.getParams().setAuthenticationPreemptive(true);
	}

	@Override
	protected void finalize() throws Throwable {
		WebUtil.removeConnectionManager(connectionManager);
	}

}
