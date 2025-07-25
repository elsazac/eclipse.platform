/*******************************************************************************
 * Copyright (c) 2015, 2018 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.terminal.view.core.internal;

import org.eclipse.osgi.util.NLS;

/**
 * Externalized strings management.
 */
public class Messages extends NLS {

	static {
		// Load message values from bundle file
		NLS.initializeMessages(Messages.class.getName(), Messages.class);
	}

	// **** Declare externalized string id's down here *****

	public static String TerminalServiceFactory_error_serviceImplLoadFailed;

	public static String Extension_error_missingRequiredAttribute;
}
