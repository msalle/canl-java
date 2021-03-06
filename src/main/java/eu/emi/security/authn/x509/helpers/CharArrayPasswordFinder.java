/*
 * Copyright (c) 2011 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licensing information.
 */
package eu.emi.security.authn.x509.helpers;

import org.bouncycastle.openssl.PasswordFinder;

/**
 * Trivial implementation of {@link PasswordFinder} which uses a password
 * provided to the constructor. 
 *
 * @author K. Benedyczak
 */
public class CharArrayPasswordFinder implements PasswordFinder
{
	private transient char []password;
	
	public CharArrayPasswordFinder(char []password)
	{
		this.password = password;
	}

	@Override
	public char[] getPassword()
	{
		return password;
	}
}
