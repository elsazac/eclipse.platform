/*******************************************************************************
 * Copyright (c) 2004, 2025 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Initial Contributors:
 * The following Wind River employees contributed to the Terminal component
 * that contains this file: Chris Thew, Fran Litterio, Stephen Lamb,
 * Helmut Haigermoser and Ted Williams.
 *
 * Contributors:
 * Michael Scharf (Wind River) - split into core, view and connector plugins
 * Martin Oberhuber (Wind River) - fixed copyright headers and beautified
 * Anna Dushistova (MontaVista) - [227537] moved actions from terminal.view to terminal plugin
 * Uwe Stieber (Wind River) - [260372] [terminal] Certain terminal actions are enabled if no target terminal control is available
 * Alexander Fedorov (ArSysOp) - further evolution
 ********************************************************************************/
package org.eclipse.terminal.view.ui.internal.actions;

import org.eclipse.terminal.control.ITerminalViewControl;
import org.eclipse.terminal.view.ui.internal.ImageConsts;

public class TerminalActionClearAll extends AbstractTerminalAction {
	public TerminalActionClearAll() {
		super(TerminalActionClearAll.class.getName());

		setupAction(ActionMessages.CLEARALL, ActionMessages.CLEARALL, null, ImageConsts.ACTION_ClearAll_enabled,
				ImageConsts.ACTION_ClearAll_disabled, false);
	}

	public TerminalActionClearAll(ITerminalViewControl target) {
		super(target, TerminalActionClearAll.class.getName());

		setupAction(ActionMessages.CLEARALL, ActionMessages.CLEARALL, null, ImageConsts.ACTION_ClearAll_enabled,
				ImageConsts.ACTION_ClearAll_disabled, false);
	}

	@Override
	public void run() {
		ITerminalViewControl target = getTarget();
		if (target != null) {
			target.clearTerminal();
		}
	}

	@Override
	public void updateAction(boolean aboutToShow) {
		ITerminalViewControl target = getTarget();
		setEnabled(target != null && !target.isEmpty());
	}
}
