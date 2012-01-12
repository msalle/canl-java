/*
 * Copyright (c) 2011-2012 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licensing information.
 */
package eu.emi.security.authn.x509.helpers.pkipath;

import java.security.InvalidAlgorithmParameterException;
import java.util.Collection;
import java.util.List;
import java.util.Timer;

import eu.emi.security.authn.x509.CrlCheckingMode;
import eu.emi.security.authn.x509.StoreUpdateListener;
import eu.emi.security.authn.x509.X509CertChainValidator;
import eu.emi.security.authn.x509.helpers.crl.PlainCRLStoreSpi;
import eu.emi.security.authn.x509.helpers.pkipath.AbstractValidator;
import eu.emi.security.authn.x509.impl.CRLParameters;
import eu.emi.security.authn.x509.impl.KeystoreCertChainValidator;
import eu.emi.security.authn.x509.impl.RevocationParameters;

/**
 * An abstract validator which provides a CRL support common for validators
 * using {@link PlainCRLStoreSpi}. Additionally it also defines a timer useful for 
 * CA or CRL updates.
 * <p>
 * The CRLs (Certificate Revocation Lists, if their handling is turned on) can be obtained
 * from two sources: CA certificate extension defining CRL URL and additional list
 * of URLs manually set by the class user. As an additional feature one may 
 * provide a simple paths to a local files, using wildcards. All files matching a 
 * wildcard are used.
 * <p>
 * This class is thread-safe.
 * 
 * @author K. Benedyczak
 * @see X509CertChainValidator
 * @see KeystoreCertChainValidator
 */
public abstract class PlainCRLValidator extends AbstractValidator
{
	protected PlainCRLStoreSpi crlStoreImpl;
	protected RevocationParameters parametersCopy; //only for CRL store recreation
	protected Timer timer;
	//TODO add get for the parameters 
	/**
	 * FIXME - arguments not used... ugly design with init
	 * Constructs a new validator instance. CRLs (Certificate Revocation Lists) 
	 * are taken from the trusted CAs certificate extension and downloaded, 
	 * unless CRL checking is disabled. Additional CRLs may be provided explicitly
	 * using the constructor argument. Such additional CRLs are preferred to the
	 * ones defined by the CA extensions.
	 * 
	 * @param revocationParams configuration of CRL sources
	 * @param crlMode defines overall CRL handling mode
	 * @param listeners initial listeners to be notified about CRL background updates
	 */
	public PlainCRLValidator(RevocationParameters revocationParams, CrlCheckingMode crlMode,
			Collection<? extends StoreUpdateListener> listeners) 
	{
		if (revocationParams == null)
			throw new IllegalArgumentException("CRLParameters argument can not be null");
		parametersCopy = revocationParams.clone();
		timer = new Timer();
		crlStoreImpl = createCRLStore(revocationParams.getCrlParameters(), timer, listeners);
	}
	
	/**
	 * This method can be overridden if a different implementation of the 
	 * {@link PlainCRLStoreSpi} (its subclass) should be used.
	 * @param crlParams source definition
	 * @param t timer to be used for scheduling updates
	 * @return initialized CRL store SPI
	 */
	protected PlainCRLStoreSpi createCRLStore(CRLParameters crlParams, Timer t, 
			Collection<? extends StoreUpdateListener> listeners)
	{
		try
		{
			return new PlainCRLStoreSpi(crlParams, t, listeners);
		} catch (InvalidAlgorithmParameterException e)
		{
			throw new RuntimeException("BUG: PlainCRLStoreSpi " +
					"can not be initialized with CRLParameters", e);
		}
	}
	
	/**
	 * Returns the interval between subsequent reloads of CRLs.
	 * This setting is used for all CRLs (those defined in CA certificates and
	 * manually configured). Implementation does not
	 * guarantees that the CRL is updated <i>exactly</i> after this interval.
	 * 
	 * @return the current refresh interval in milliseconds
	 */
	public long getCRLUpdateInterval()
	{
		return crlStoreImpl.getUpdateInterval();
	}

	/**
	 * Sets a new interval between subsequent of CRLs. 
	 * This setting is used for all CRLs (those defined in CA certificates and
	 * manually configured). Implementation does not
	 * guarantees that the CRL is updated <i>exactly</i> after this interval.
	 *
	 * @param updateInterval the new interval to be set in milliseconds
	 */
	public void setCRLUpdateInterval(long updateInterval)
	{
		crlStoreImpl.setUpdateInterval(updateInterval);
	}

	/**
	 * Returns the current list of additional CRL locations. 
	 * See class description for details.
	 * @return The current list of additional CRLs. The returned list is 
	 * a copy of the list actually used so its modifications does not influence
	 * the validator.
	 */
	public List<String> getCrls()
	{
		return crlStoreImpl.getLocations();
	}

	/**
	 * Sets a new list of additional CRL locations. See class description for details.
	 * @param crls the new list.
	 */
	public synchronized void setCrls(List<String> crls)
	{
		crlStoreImpl.dispose();
		parametersCopy.getCrlParameters().setCrls(crls);
		crlStoreImpl = createCRLStore(parametersCopy.getCrlParameters(), timer, observers);
		init(null, crlStoreImpl, isProxyAllowed(), getRevocationCheckingMode());
	}

	@Override
	public void dispose()
	{
		super.dispose();
		crlStoreImpl.dispose();
		if (timer != null)
			timer.cancel();
	}
}





