/*******************************************************************************
 * Copyright (c) 2011, 2025 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 * Max Weninger (Wind River) - [361352] [TERMINALS][SSH] Add SSH terminal support
 * Alexander Fedorov (ArSysOp) - further evolution
 *******************************************************************************/
package org.eclipse.terminal.connector.ssh.launcher;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.terminal.connector.ISettingsStore;
import org.eclipse.terminal.connector.ITerminalConnector;
import org.eclipse.terminal.connector.InMemorySettingsStore;
import org.eclipse.terminal.connector.TerminalConnectorExtension;
import org.eclipse.terminal.connector.ssh.connector.ISshSettings;
import org.eclipse.terminal.connector.ssh.connector.SshSettings;
import org.eclipse.terminal.connector.ssh.controls.SshWizardConfigurationPanel;
import org.eclipse.terminal.connector.ssh.nls.Messages;
import org.eclipse.terminal.view.core.ITerminalsConnectorConstants;
import org.eclipse.terminal.view.ui.IMementoHandler;
import org.eclipse.terminal.view.ui.launcher.AbstractLauncherDelegate;
import org.eclipse.terminal.view.ui.launcher.IConfigurationPanel;
import org.eclipse.terminal.view.ui.launcher.IConfigurationPanelContainer;

/**
 * SSH launcher delegate implementation.
 */
public class SshLauncherDelegate extends AbstractLauncherDelegate {
	// The SSH terminal connection memento handler
	private final IMementoHandler mementoHandler = new SshMementoHandler();

	@Override
	public boolean needsUserConfiguration() {
		return true;
	}

	@Override
	public IConfigurationPanel getPanel(IConfigurationPanelContainer container) {
		return new SshWizardConfigurationPanel(container);
	}

	@Override
	public CompletableFuture<?> execute(Map<String, Object> properties) {
		Assert.isNotNull(properties);

		// Set the terminal tab title
		String terminalTitle = getTerminalTitle(properties);
		if (terminalTitle != null) {
			properties.put(ITerminalsConnectorConstants.PROP_TITLE, terminalTitle);
		}

		// For SSH terminals, force a new terminal tab each time it is launched,
		// if not set otherwise from outside
		if (!properties.containsKey(ITerminalsConnectorConstants.PROP_FORCE_NEW)) {
			properties.put(ITerminalsConnectorConstants.PROP_FORCE_NEW, Boolean.TRUE);
		}
		try {
			return getTerminalService().openConsole(properties);
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(e);
		}
	}

	/**
	 * Returns the terminal title string.
	 * <p>
	 * The default implementation constructs a title like &quot;SSH @ host (Start time) &quot;.
	 *
	 * @return The terminal title string or <code>null</code>.
	 */
	private String getTerminalTitle(Map<String, Object> properties) {
		// Try to see if the user set a title explicitly via the properties map.
		String title = getDefaultTerminalTitle(properties);
		if (title != null) {
			return title;
		}

		//No title,try to calculate the title
		String host = (String) properties.get(ITerminalsConnectorConstants.PROP_IP_HOST);
		String user = (String) properties.get(ITerminalsConnectorConstants.PROP_SSH_USER);
		Object value = properties.get(ITerminalsConnectorConstants.PROP_IP_PORT);
		String port = value != null ? value.toString() : null;

		if (host != null && user != null) {
			DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
			String date = format.format(new Date(System.currentTimeMillis()));
			if (port != null && Integer.valueOf(port).intValue() != ISshSettings.DEFAULT_SSH_PORT) {
				return NLS.bind(Messages.SshLauncherDelegate_terminalTitle_port, user, host, port, date);
			}
			return NLS.bind(Messages.SshLauncherDelegate_terminalTitle, user, host, date);
		}

		return Messages.SshLauncherDelegate_terminalTitle_default;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.PlatformObject#getAdapter(java.lang.Class)
	 */
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (IMementoHandler.class.equals(adapter)) {
			return adapter.cast(mementoHandler);
		}
		return super.getAdapter(adapter);
	}

	@Override
	public ITerminalConnector createTerminalConnector(Map<String, Object> properties) throws CoreException {
		Assert.isNotNull(properties);

		// Check for the terminal connector id
		String connectorId = (String) properties.get(ITerminalsConnectorConstants.PROP_TERMINAL_CONNECTOR_ID);
		if (connectorId == null) {
			connectorId = "org.eclipse.terminal.connector.ssh.SshConnector"; //$NON-NLS-1$
		}

		// Extract the ssh properties
		String host = (String) properties.get(ITerminalsConnectorConstants.PROP_IP_HOST);
		Object value = properties.get(ITerminalsConnectorConstants.PROP_IP_PORT);
		String port = value != null ? value.toString() : null;
		value = properties.get(ITerminalsConnectorConstants.PROP_TIMEOUT);
		String timeout = value != null ? value.toString() : null;
		value = properties.get(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE);
		String keepAlive = value != null ? value.toString() : null;
		String password = (String) properties.get(ITerminalsConnectorConstants.PROP_SSH_PASSWORD);
		String user = (String) properties.get(ITerminalsConnectorConstants.PROP_SSH_USER);

		int portOffset = 0;
		if (properties.get(ITerminalsConnectorConstants.PROP_IP_PORT_OFFSET) instanceof Integer) {
			portOffset = ((Integer) properties.get(ITerminalsConnectorConstants.PROP_IP_PORT_OFFSET)).intValue();
			if (portOffset < 0) {
				portOffset = 0;
			}
		}

		// The real port to connect to is port + portOffset
		if (port != null) {
			port = Integer.toString(Integer.decode(port).intValue() + portOffset);
		}

		// Construct the ssh settings store
		ISettingsStore store = new InMemorySettingsStore();

		// Construct the telnet settings
		SshSettings sshSettings = new SshSettings();
		sshSettings.setHost(host);
		sshSettings.setPort(port);
		sshSettings.setTimeout(timeout);
		sshSettings.setKeepalive(keepAlive);
		sshSettings.setPassword(password);
		sshSettings.setUser(user);

		// And save the settings to the store
		sshSettings.save(store);

		// MWE TODO make sure this is NOT passed outside as this is plain text
		store.put("Password", password); //$NON-NLS-1$

		// Construct the terminal connector instance
		ITerminalConnector connector = TerminalConnectorExtension.makeTerminalConnector(connectorId);
		// Apply default settings
		connector.setDefaultSettings();
		// And load the real settings
		connector.load(store);
		return connector;
	}
}
