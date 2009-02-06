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

package com.atlassian.connector.eclipse.internal.bamboo.core;

import com.atlassian.theplugin.commons.SubscribedPlan;
import com.atlassian.theplugin.commons.bamboo.BambooBuild;

import org.eclipse.mylyn.tasks.core.TaskRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

/**
 * Provides utility methods for Bamboo.
 * 
 * @author Shawn Minto
 */
public final class BambooUtil {

	private static final String KEY_SUBSCRIBED_PLANS = "com.atlassian.connector.eclipse.bamboo.subscribedPlans";

	private BambooUtil() {
	}

	public static void setSubcribedPlans(TaskRepository repository, Collection<SubscribedPlan> plans) {
		StringBuffer sb = new StringBuffer();
		for (SubscribedPlan plan : plans) {
			sb.append(plan.getKey());
			sb.append(",");
		}
		repository.setProperty(KEY_SUBSCRIBED_PLANS, sb.toString());
	}

	public static Collection<SubscribedPlan> getSubscribedPlans(TaskRepository repository) {
		Collection<SubscribedPlan> plans = new ArrayList<SubscribedPlan>();
		String value = repository.getProperty(KEY_SUBSCRIBED_PLANS);
		if (value != null) {
			StringTokenizer t = new StringTokenizer(value, ",");
			while (t.hasMoreTokens()) {
				plans.add(new SubscribedPlan(t.nextToken()));
			}
		}
		return plans;
	}

	public static boolean isSameBuildPlan(BambooBuild buildOne, BambooBuild buildTwo) {
		if (buildOne.getBuildUrl().equals(buildTwo.getServerUrl())) {
			return false;
		}
		//check if same planKey
		return buildOne.getBuildKey().equals(buildTwo.getBuildKey());

//		//check if from same server
//		String[] keyElementsOne = buildOne.getBuildKey().split("-");
//		String[] keyElementsTwo = buildTwo.getBuildKey().split("-");

//		//check if at least 2 elements
//		if (keyElementsOne.length < 2) {
//			StatusHandler.log(new Status(IStatus.WARNING, BambooCorePlugin.PLUGIN_ID, "Invalid Bamboo Build Key: "
//					+ buildOne.getBuildKey()));
//			return false;
//		}
//		if (keyElementsTwo.length < 2) {
//			StatusHandler.log(new Status(IStatus.WARNING, BambooCorePlugin.PLUGIN_ID, "Invalid Bamboo Build Key: "
//					+ buildTwo.getBuildKey()));
//			return false;
//		}
//		//check if same project and same build plan
//		return keyElementsOne[0].equals(keyElementsTwo[0]) && keyElementsOne[1].equals(keyElementsTwo[1]);
	}

}
