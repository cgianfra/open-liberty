/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.ws.sib.admin;

/**
 *Exception representing invalid file store configuration
 */
public class InvalidFileStoreConfigurationException extends SIBExceptionBase {

	private static final long serialVersionUID = 2326898619518568826L;

	/**
	 * @param msg
	 */
	public InvalidFileStoreConfigurationException(String msg) {
		super(msg);
	}

}
