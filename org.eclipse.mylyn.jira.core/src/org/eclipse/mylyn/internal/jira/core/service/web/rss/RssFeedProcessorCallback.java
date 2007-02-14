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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.mylar.internal.jira.core.DebugManager;
import org.eclipse.mylar.internal.jira.core.model.filter.IssueCollector;
import org.eclipse.mylar.internal.jira.core.service.JiraServer;
import org.eclipse.mylar.internal.jira.core.service.web.JiraWebSessionCallback;

/**
 * @author	Brock Janiczak
 */
public abstract class RssFeedProcessorCallback implements JiraWebSessionCallback {
	private static final boolean RSS_DEBUG_ENABLED = DebugManager.isDebugEnabled()
			&& DebugManager.isDebugOptionEnabled("rss"); //$NON-NLS-1$

	private final boolean useGZipCompression;

	private final IssueCollector collector;

	public RssFeedProcessorCallback(boolean useGZipCompression, IssueCollector collector) {
		this.useGZipCompression = useGZipCompression;
		this.collector = collector;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.mylar.internal.jira.core.service.web.JiraWebSessionCallback#execute(org.apache.commons.httpclient.HttpClient,
	 *      org.eclipse.mylar.internal.jira.core.service.JiraServer)
	 */
	public final void execute(HttpClient client, JiraServer server) {
		String rssUrl = getRssUrl();
		GetMethod rssRequest = new GetMethod(rssUrl);
		// If there is only a single match Jira will redirect to the issue
		// browser
		rssRequest.setFollowRedirects(true);

		// Tell the server we would like the response GZipped. This does not
		// guarantee it will be done
		if (useGZipCompression) {
			rssRequest.setRequestHeader("Accept-Encoding", "gzip"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		try {
			if (collector.isCancelled()) {
				return;
			}
			long start = System.currentTimeMillis();
			client.executeMethod(rssRequest);

			// Jira 3.4 can redirect straight to the issue browser, but not with
			// the RSS view type
			if (!isXMLOrRSS(rssRequest)) {
				rssRequest = new GetMethod(rssRequest.getURI().getURI());
				rssRequest.setQueryString("decorator=none&view=rss"); //$NON-NLS-1$
				client.executeMethod(rssRequest);

				// If it still isn't an XML response, an invalid issue was
				// entered
				if (!isXMLOrRSS(rssRequest)) {
					return;
				}
			}

			boolean isResponseGZipped = isResponseGZipped(rssRequest);

			// Wrap the physical and logical input streams so we can see how
			// much data is being processed
			if (RSS_DEBUG_ENABLED) {
				InputStream firstStream = null;
				try {
					CountingInputStream phyStreamCounter = new CountingInputStream(rssRequest.getResponseBodyAsStream());
					CountingInputStream logStreamCounter = new CountingInputStream(
							isResponseGZipped ? (InputStream) new GZIPInputStream(phyStreamCounter)
									: (InputStream) phyStreamCounter);
					firstStream = logStreamCounter;

					new RssReader(server, collector).readRssFeed(logStreamCounter);
					long end = System.currentTimeMillis();
					long delta = end - start;
					long bytesProcessed = logStreamCounter.getTotalByesRead();
					long bytesRead = phyStreamCounter.getTotalByesRead();

					if (delta == 0) {
						delta = 10L;
					}

					StringBuffer logMessage = new StringBuffer();
					logMessage.append("URL: ").append(rssUrl).append('\n');
					logMessage.append("Processed: ").append(bytesProcessed / 1024L).append(" kb");
					logMessage.append(" at ").append(bytesProcessed / (end - start) * 1000F / 1024F).append(" kb/s");
					logMessage.append(" in ").append((float) (end - start) / 1000F).append(" second(s)").append('\n');

					if (isResponseGZipped) {
						logMessage.append("Compression ratio of ").append(
								(1.0f - ((float) phyStreamCounter.getTotalByesRead() / (float) logStreamCounter
										.getTotalByesRead())) * 100F).append(" percent");
						logMessage.append(" (").append(bytesRead / 1024L).append(" kb read").append(")");
					}
					DebugManager.log(logMessage.toString(), null);
				} finally {
					if (firstStream != null) {
						try {
							firstStream.close();
						} catch (IOException e1) {
							// Do nothing
						}
					}
				}
			} else {
				InputStream rssFeed = null;
				try {
					rssFeed = isResponseGZipped ? new GZIPInputStream(rssRequest.getResponseBodyAsStream())
							: rssRequest.getResponseBodyAsStream();
					new RssReader(server, collector).readRssFeed(rssFeed);
				} finally {
					try {
						if (rssFeed != null) {
							rssFeed.close();
						}
					} catch (IOException e) {
						// Do nothing
					}
				}
			}
		} catch (HttpException e) {
			collector.setException(e);
			DebugManager.error(e.getMessage(), e);
		} catch (IOException e) {
			collector.setException(e);
			DebugManager.error(e.getMessage(), e);
		} finally {
			rssRequest.releaseConnection();
		}
	}

	/**
	 * Determines the URL of the RSS being processed. This URL will typically be
	 * generated from a filter definition
	 * 
	 * @return The URL of the RSS feed to be processed
	 */
	protected abstract String getRssUrl();

	/**
	 * Determines if the response of <code>method</code> was GZip encoded
	 * 
	 * @param method
	 *            Method to determine encoding of
	 * @return <code>true</code> if the resposne was GZip encoded,
	 *         <code>false</code> otherwise.
	 */
	private boolean isResponseGZipped(HttpMethod method) {
		Header contentEncoding = method.getResponseHeader("Content-Encoding"); //$NON-NLS-1$
		return contentEncoding != null && "gzip".equals(contentEncoding.getValue()); //$NON-NLS-1$
	}

	private boolean isXMLOrRSS(HttpMethod method) throws HttpException {
		Header contentType = method.getResponseHeader("Content-Type"); //$NON-NLS-1$
		if (contentType == null) {
			return false;
		}

		HeaderElement[] values = contentType.getElements();
		for (int i = 0; i < values.length; i++) {
			HeaderElement element = values[i];
			if (element.getName().startsWith("text/xml")) { //$NON-NLS-1$
				return true;
			}
		}

		return false;
	}
}
