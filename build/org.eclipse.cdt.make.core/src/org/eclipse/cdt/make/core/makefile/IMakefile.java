/**********************************************************************
 * Copyright (c) 2002,2003 QNX Software Systems and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
***********************************************************************/
package org.eclipse.cdt.make.core.makefile;

import java.io.IOException;
import java.io.Reader;


/**
 * IMakefile
 */
public interface IMakefile extends IParent {
	IRule[] getRules();
	IRule[] getRule(String target);
	IInferenceRule[] getInferenceRules();
	IInferenceRule[] getInferenceRule(String target);
	ITargetRule[] getTargetRules();
	ITargetRule[] getTargetRule(String target);
	IMacroDefinition[] getMacroDefinitions();
	IMacroDefinition[] getMacroDefinition(String name);
	IDirective[] getBuiltins();
	void parse(Reader makefile) throws IOException;
}
