/*******************************************************************************
 * Copyright (c) 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.core.pdom.indexer.fast;

import org.eclipse.cdt.core.dom.IPDOM;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.internal.core.pdom.PDOMDatabase;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * @author Doug Schaefer
 *
 */
public class PDOMFastRemoveTU extends Job {

	private final IPDOM pdom;
	private final ITranslationUnit tu;
	
	public PDOMFastRemoveTU(IPDOM pdom, ITranslationUnit tu) {
		super("PDOM Fast Remove TU");
		this.pdom = pdom;
		this.tu = tu;
	}

	protected IStatus run(IProgressMonitor monitor) {
		try {
			PDOMDatabase mypdom = (PDOMDatabase)pdom;
			mypdom.removeSymbols(tu);
			// TODO delete the file itself from the database
			// the removeSymbols only removes the names in the file
			// TODO Auto-generated method stub
			return Status.OK_STATUS;
		} catch (CoreException e) {
			return e.getStatus();
		}
	}

}
