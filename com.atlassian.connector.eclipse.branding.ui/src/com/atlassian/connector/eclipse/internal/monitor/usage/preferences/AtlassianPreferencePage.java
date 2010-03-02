package com.atlassian.connector.eclipse.internal.monitor.usage.preferences;

import java.net.URL;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import com.atlassian.connector.eclipse.internal.monitor.usage.UiUsageMonitorPlugin;
import com.atlassian.connector.eclipse.internal.ui.AtlassianLogo;

public class AtlassianPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private static final String TEXT_MAIN = "The Atlassian Eclipse Connector is an Eclipse plugin that lets you "
			+ "work with the Atlassian products within your IDE. Now you don't "
			+ "have to switch between websites, email messages and news feeds to "
			+ "see what's happening to your project and your code. Instead, you "
			+ "can see the relevant <a href=\"http://www.atlassian.com/software/jira\">JIRA</a> issues, "
			+ "<a href=\"http://www.atlassian.com/software/crucible\">Crucible</a> reviews "
			+ "and <a href=\"http://www.atlassian.com/software/bamboo\">Bamboo</a> build "
			+ "information right there in your development environment. Viewing your "
			+ "code in <a href=\"http://www.atlassian.com/software/fisheye\">FishEye</a> is just a click away.";

	@Override
	protected Control createContents(Composite parent) {
		final Composite composite = new Composite(parent, SWT.NULL);
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER).grab(false, false).applyTo(composite);
		GridLayoutFactory.fillDefaults().numColumns(1).margins(5, 0).spacing(0, 0).applyTo(composite);
		final Label label = new Label(composite, SWT.CENTER);
		final Image image = AtlassianLogo.getImage(AtlassianLogo.ATLASSIAN_LOGO);
		label.setImage(image);

		final int logoWidth = image.getBounds().width + 100;
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.FILL).hint(logoWidth, image.getBounds().height).applyTo(
				label);

		label.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));

		// OK, when we cannot just embed the browser, then
		// we can always build something decent manually:

		final Composite nestedComposite = new Composite(composite, SWT.NONE);
		nestedComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		GridLayoutFactory.fillDefaults().numColumns(1).margins(4, 0).applyTo(nestedComposite);
		GridDataFactory.fillDefaults()
				.align(SWT.CENTER, SWT.FILL)
				.hint(logoWidth, SWT.DEFAULT)
				.applyTo(nestedComposite);

		Link link = new Link(nestedComposite, SWT.NONE);
		link.setText(TEXT_MAIN);

		link.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		link.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				openUrl(e.text);
			}
		});
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.TOP).hint(logoWidth, SWT.DEFAULT).applyTo(link);

		final FontRegistry fontRegistry = new FontRegistry();
		fontRegistry.put("big", new FontData[] { new FontData("Arial", 18, SWT.BOLD) });
		final Font bigFont = fontRegistry.get("big");

		// a spacer
		new Label(nestedComposite, SWT.NONE).setVisible(false);

		final Link link2 = createLink(nestedComposite, "Developed by Atlassian for you to lust after", -1);
		link2.setFont(bigFont);

		link2.setText("Developed by Atlassian for you to lust after");
		final Link link3 = createLink(nestedComposite,
				"<a href=\"http://www.atlassian.com/\">http://www.atlassian.com/</a>", -1);
		link3.setFont(bigFont);

		// a spacer
		new Label(nestedComposite, SWT.NONE).setVisible(false);

		createLink(nestedComposite, "Licensed under the Eclipse Public License Version 1.0 (\"EPL\").", -1);
		createLink(nestedComposite, "Copyright (c) Atlassian 2009", -1);

		// a spacer
		new Label(nestedComposite, SWT.NONE).setVisible(false);

		final Composite buttonBar = new Composite(composite, SWT.NONE);
		buttonBar.setLayout(new RowLayout());

		createButton(buttonBar, "On-line Help",
				"http://confluence.atlassian.com/display/IDEPLUGIN/Atlassian+Eclipse+Connector");
		createButton(buttonBar, "Visit Forum", "http://forums.atlassian.com/forum.jspa?forumID=124&start=0");
		createButton(buttonBar, "Request Support", "https://support.atlassian.com/browse/ECSP");
		createButton(buttonBar, "Report Bug", "https://studio.atlassian.com/browse/PLE");

		return composite;
	}

	private Link createLink(Composite parent, String text, int width) {
		Link link = new Link(parent, SWT.NONE);
		link.setText(text);

		link.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.TOP).hint(width, SWT.DEFAULT).applyTo(link);
		link.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				openUrl(e.text);
			}

		});
		return link;
	}

	private void createButton(final Composite parent, String text, final String url) {
		Button helpButton = new Button(parent, SWT.PUSH);
		helpButton.setText(text);
		helpButton.setToolTipText("Open " + url);
		helpButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				openUrl(url);
			}
		});
	}

	private void openUrl(String url) {
		try {
			IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
			support.getExternalBrowser().openURL(new URL(url));
		} catch (Exception e) {
			StatusHandler.fail(new Status(IStatus.ERROR, UiUsageMonitorPlugin.ID_PLUGIN,
					"Could not open URL in an external browser [" + url + "]", e)); //$NON-NLS-1$
		}
	}

	public AtlassianPreferencePage() {
		noDefaultAndApplyButton();
		setPreferenceStore(UiUsageMonitorPlugin.getDefault().getPreferenceStore());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}

}