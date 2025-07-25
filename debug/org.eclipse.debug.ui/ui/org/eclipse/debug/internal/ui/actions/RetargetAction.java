/*******************************************************************************
 *  Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Wind River Systems - added support for IToggleBreakpointsTargetFactory
 *******************************************************************************/
package org.eclipse.debug.internal.ui.actions;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IActionDelegate2;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

/**
 * Global retargettable debug action.
 *
 * @since 3.0
 */
public abstract class RetargetAction implements IWorkbenchWindowActionDelegate, IPartListener, IActionDelegate2 {

	protected IWorkbenchWindow fWindow = null;
	private IWorkbenchPart fActivePart = null;
	private Object fTargetAdapter = null;
	private IAction fAction = null;
	private static final ISelection EMPTY_SELECTION = new EmptySelection();

	static class EmptySelection implements ISelection {

		@Override
		public boolean isEmpty() {
			return true;
		}
	}

	/**
	 * Returns the current selection in the active part, possibly
	 * and empty selection, but never <code>null</code>.
	 *
	 * @return the selection in the active part, possibly empty
	 */
	protected ISelection getTargetSelection() {
		if (fActivePart != null) {
			ISelectionProvider selectionProvider = fActivePart.getSite().getSelectionProvider();
			if (selectionProvider != null) {
				return selectionProvider.getSelection();
			}
		}
		return EMPTY_SELECTION;
	}

	protected IWorkbenchPart getActivePart() {
		return fActivePart;
	}

	@Override
	public void dispose() {
		fWindow.getPartService().removePartListener(this);
		fActivePart = null;
		fTargetAdapter = null;

	}

	@Override
	public void init(IWorkbenchWindow window) {
		this.fWindow = window;
		IPartService partService = window.getPartService();
		partService.addPartListener(this);
		IWorkbenchPart part = partService.getActivePart();
		if (part != null) {
			partActivated(part);
		}
	}

	@Override
	public void run(IAction action) {
		if (fTargetAdapter != null) {
			try {
				if (isTargetEnabled()) {
					performAction(fTargetAdapter, getTargetSelection(), fActivePart);
				} else {
					String message = getOperationUnavailableMessage();
					IStatus status = new Status(IStatus.INFO, DebugUIPlugin.getUniqueIdentifier(), message);
					DebugUIPlugin.errorDialog(DebugUIPlugin.getShell(), DebugUIPlugin.removeAccelerators(action.getText()), message, status);
				}
			} catch (CoreException e) {
				DebugUIPlugin.errorDialog(fWindow.getShell(), ActionMessages.RetargetAction_2, ActionMessages.RetargetAction_3, e.getStatus()); //
			}
		}
	}

	/**
	 * Returns a message to display when we find that the operation is not enabled
	 * when invoked in an editor (we check enabled state before running in this case,
	 * rather than updating on each selection change - see bug 180441).
	 *
	 * @return information message when unavailable
	 */
	protected abstract String getOperationUnavailableMessage();

	/**
	 * Performs the specific breakpoint toggling.
	 *
	 * @param selection selection in the active part
	 * @param part active part
	 * @throws CoreException if an exception occurs
	 */
	protected abstract void performAction(Object target, ISelection selection, IWorkbenchPart part) throws CoreException;

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// if the active part did not provide an adapter, see if the selection does
		if (fTargetAdapter == null && selection instanceof IStructuredSelection ss) {
			if (!ss.isEmpty()) {
				Object object = ss.getFirstElement();
				if (object instanceof IAdaptable) {
					fTargetAdapter = getAdapter((IAdaptable) object);
				}
			}
			action.setEnabled(fTargetAdapter != null && isTargetEnabled());
		}
	}

	@Override
	public void partActivated(IWorkbenchPart part) {
		fActivePart = part;
		IResource resource = part.getAdapter(IResource.class);
		if (resource == null && part instanceof IEditorPart) {
			resource = ((IEditorPart)part).getEditorInput().getAdapter(IResource.class);
		}
		if (resource != null) {
			fTargetAdapter = getAdapter(resource);
		}
		if (fTargetAdapter == null) {
			fTargetAdapter = getAdapter(part);
		}
		if (fAction != null) {
			fAction.setEnabled(fTargetAdapter != null && isTargetEnabled());
		}
	}

	protected Object getAdapter(IAdaptable adaptable) {
		Object adapter  = adaptable.getAdapter(getAdapterClass());
		if (adapter == null) {
			IAdapterManager adapterManager = Platform.getAdapterManager();
			if (adapterManager.hasAdapter(adaptable, getAdapterClass().getName())) {
				adapter = adapterManager.loadAdapter(adaptable, getAdapterClass().getName());
			}
		}
		return adapter;
	}

	/**
	 * Returns the type of adapter (target) this action works on.
	 *
	 * @return the type of adapter this action works on
	 */
	protected abstract Class<?> getAdapterClass();

	@Override
	public void partBroughtToTop(IWorkbenchPart part) {
	}

	@Override
	public void partClosed(IWorkbenchPart part) {
		clearPart(part);
	}

	/**
	 * Clears reference to active part and adapter when a relevant part
	 * is closed or no longer active.
	 *
	 * @param part workbench part that has been closed or no longer active
	 */
	protected void clearPart(IWorkbenchPart part) {
		if (part.equals(fActivePart)) {
			fActivePart = null;
			fTargetAdapter = null;
		}
	}

	@Override
	public void partDeactivated(IWorkbenchPart part) {
		clearPart(part);
	}

	@Override
	public void partOpened(IWorkbenchPart part) {
	}

	/**
	 * Returns whether the target adapter is enabled
	 *
	 * @return whether target adapter is enabled
	 */
	protected boolean isTargetEnabled() {
		if (fTargetAdapter != null) {
			if (fActivePart != null) {
				return canPerformAction(fTargetAdapter, getTargetSelection(), fActivePart);
			}
		}
		return false;
	}

	/**
	 * Returns whether the specific operation is supported.
	 *
	 * @param target the target adapter
	 * @param selection the selection to verify the operation on
	 * @param part the part the operation has been requested on
	 * @return whether the operation can be performed
	 */
	protected abstract boolean canPerformAction(Object target, ISelection selection, IWorkbenchPart part);

	@Override
	public void init(IAction action) {
		fAction = action;
	}

	@Override
	public void runWithEvent(IAction action, Event event) {
		run(action);
	}

	/**
	 * Returns the proxy to this action delegate or <code>null</code>
	 *
	 * @return action proxy or <code>null</code>
	 */
	protected IAction getAction() {
		return fAction;
	}

	/**
	 * Returns whether there is currently a target adapter for this action.
	 *
	 * @return whether the action has a target adapter.
	 */
	protected boolean hasTargetAdapter() {
		return fTargetAdapter != null;
	}

}
