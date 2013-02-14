/*
 * Copyright (c) 2011-2012 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE file for licensing information.
 */
package eu.emi.security.authn.x509.proxy;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;

import eu.emi.security.authn.x509.helpers.proxy.ProxyAddressRestrictionData;
import eu.emi.security.authn.x509.helpers.proxy.ProxyCSRImpl;
import eu.emi.security.authn.x509.helpers.proxy.ProxyCertInfoExtension;
import eu.emi.security.authn.x509.helpers.proxy.ProxyGeneratorHelper;
import eu.emi.security.authn.x509.helpers.proxy.ProxySAMLExtension;
import eu.emi.security.authn.x509.helpers.proxy.ProxyTracingExtension;
import eu.emi.security.authn.x509.impl.CertificateUtils;

/**
 * Generates a proxy certificate signing request. The request parameters may contain
 * extensions which are passed in the generated Certificate Signing Request. 
 * Of course the peer issuing the proxy certificate may ignore them.
 * <p>
 * The following rules are applied basing on the parameters object:
 * <ul>
 * <li> [RFC proxy only] If the serial number is set then it is used as requested CN part of the proxy. 
 * Otherwise the CN part is set to the serial number of the issuing certificate. Note that 
 * this value most probably will be ignored anyway by the part which issues the actual proxy,
 * as this is this party responsibility to guarantee uniqueness of serial numbers across all proxies
 * issued.
 * <li> All additional extensions, SAML, tracing and address restrictions are added as Attributes
 * of extensionRequest type (PKCS 9) if are set.
 * <li> Proxy path limit and policy (if set) are wrapped into the proxy extension and then included in
 * the Attributes list (as above). If only one of the values is set then the second receives the default
 * value. If the type is set to the legacy proxy then those parameters are ignored.
 * <li>  There is no way to request a validity time of the generated proxy, therefore the lifetime
 * parameter is ignored.
 * <li> Setting proxy to be limited is used only for legacy proxies CSR (to form a special DN). 
 * To create limited RFC/RFC draft proxy CSR use a proper policy. 
 * </ul>
 * 
 * @author K. Benedyczak
 */
public class ProxyCSRGenerator
{
	static 
	{
		CertificateUtils.configureSecProvider();
	}

	/**
	 * Generate the proxy certificate object. Use this method if you want to sign 
	 * the proxy with the key which will be autogenerated together with the public part
	 * which will be included in the proxy itself. This method will throw an exception
	 * if used with parameter which has public key manually set.
	 * 
	 * @param param request creation parameters
	 * @return Proxy certificate signing request
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateEncodingException
	 * @throws IllegalArgumentException when signingKey is null and public key was manully set
	 */
	public static ProxyCSR generate(ProxyCertificateOptions param) 
			throws InvalidKeyException, SignatureException, NoSuchAlgorithmException,
			CertificateEncodingException
	{
		return generate(param, null);
	}

	
	/**
	 * Generate the proxy certificate object. Use this method if you want to manually 
	 * specify the CSR signing key. This is normally the case when 
	 * the {@link ProxyCertificateOptions} parameter contains a manually set public key.
	 * 
	 * @param param request creation parameters
	 * @return Proxy certificate signing request
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateEncodingException
	 * @throws IllegalArgumentException when signingKey is null and public key was manully set
	 */
	public static ProxyCSR generate(ProxyCertificateOptions param, PrivateKey signingKey) 
			throws InvalidKeyException, SignatureException, NoSuchAlgorithmException,
			CertificateEncodingException
	{
		PublicKey pubKey = param.getPublicKey();
		KeyPair keyPair;
		if (pubKey == null)
			keyPair = ProxyGeneratorHelper.generateKeyPair(param.getKeyLength());
		else
			keyPair = new KeyPair(pubKey, null);
		if (signingKey == null)
			signingKey = keyPair.getPrivate();
		if (signingKey == null)
			throw new IllegalArgumentException("Signing (private) key can not be null " +
					"when using a manually set public key");
			
		X509Certificate []chain = param.getParentCertChain();
		ProxyType type = param.getType();
		BigInteger serial = ProxyGeneratorHelper.establishSerial(param);
		X500Name proxySubjectName = ProxyGeneratorHelper.generateDN(chain[0].getSubjectX500Principal(), type, 
				param.isLimited(), serial);
		X500Principal proxySubject = new X500Principal(proxySubjectName.getDEREncoded());
		ASN1Set attributes = generateAttributes(param);
		
		PKCS10CertificationRequest req;
		try
		{
			req = new PKCS10CertificationRequest(
					"SHA1WITHRSA", 
					proxySubject, 
					keyPair.getPublic(), 
					attributes, 
					signingKey);
		} catch (NoSuchProviderException e)
		{
			throw new RuntimeException("Default provider not installed?", e);
		}
		return new ProxyCSRImpl(req, keyPair.getPrivate());
	}
	
	
	
	private static ASN1Set generateAttributes(ProxyCertificateOptions param)
	{
		List<Attribute> attributes = new ArrayList<Attribute>();

		List<CertificateExtension> additionalExts = param.getExtensions();
		for (CertificateExtension ext: additionalExts)
			addAttribute(attributes, ext);

		ProxyPolicy policy = param.getPolicy();
		int pathLimit = param.getProxyPathLimit();
		if (param.getType() != ProxyType.LEGACY && (policy != null || pathLimit != -1))
		{
			if (policy == null)
				policy = new ProxyPolicy(ProxyPolicy.INHERITALL_POLICY_OID);
			
			String oid = param.getType() == ProxyType.DRAFT_RFC ? ProxyCertInfoExtension.DRAFT_EXTENSION_OID 
					: ProxyCertInfoExtension.RFC_EXTENSION_OID;
			ProxyCertInfoExtension extValue = new ProxyCertInfoExtension(pathLimit, policy);
			CertificateExtension ext = new CertificateExtension(oid, extValue, true);
			addAttribute(attributes, ext);
		}
		
		if (param.getProxyTracingIssuer() != null)
		{
			ProxyTracingExtension extValue = new ProxyTracingExtension(param.getProxyTracingIssuer());
			CertificateExtension ext = new CertificateExtension(
					ProxyTracingExtension.PROXY_TRACING_ISSUER_EXTENSION_OID, 
					extValue, false);
			addAttribute(attributes, ext);
		}
		if (param.getProxyTracingSubject() != null)
		{
			ProxyTracingExtension extValue = new ProxyTracingExtension(param.getProxyTracingSubject());
			CertificateExtension ext = new CertificateExtension(
					ProxyTracingExtension.PROXY_TRACING_SUBJECT_EXTENSION_OID, 
					extValue, false);
			addAttribute(attributes, ext);
		}
		
		if (param.getSAMLAssertion() != null)
		{
			ProxySAMLExtension extValue = new ProxySAMLExtension(param.getSAMLAssertion());
			CertificateExtension ext = new CertificateExtension(
					ProxySAMLExtension.SAML_OID, extValue, false);
			addAttribute(attributes, ext);
		}
		
		String[] srcExcl = param.getSourceRestrictionExcludedAddresses();
		String[] srcPerm = param.getSourceRestrictionPermittedAddresses();
		if (srcExcl != null || srcPerm != null)
		{
			ProxyAddressRestrictionData extValue = new ProxyAddressRestrictionData();
			if (srcExcl != null)
			{
				for (String addr: srcExcl)
					extValue.addExcludedIPAddressWithNetmask(addr);
			}
			if (srcPerm != null)
			{
				for (String addr: srcPerm)
					extValue.addPermittedIPAddressWithNetmask(addr);
			}
			CertificateExtension ext = new CertificateExtension(
					ProxyAddressRestrictionData.SOURCE_RESTRICTION_OID, extValue, false);
			addAttribute(attributes, ext);
		}

		String[] tgtExcl = param.getTargetRestrictionExcludedAddresses();
		String[] tgtPerm = param.getTargetRestrictionPermittedAddresses();
		if (tgtExcl != null || tgtPerm != null)
		{
			ProxyAddressRestrictionData extValue = new ProxyAddressRestrictionData();
			if (tgtExcl != null)
			{
				for (String addr: tgtExcl)
					extValue.addExcludedIPAddressWithNetmask(addr);
			}
			if (tgtPerm != null)
			{
				for (String addr: tgtPerm)
					extValue.addPermittedIPAddressWithNetmask(addr);
			}
			CertificateExtension ext = new CertificateExtension(
					ProxyAddressRestrictionData.TARGET_RESTRICTION_OID, extValue, false);
			addAttribute(attributes, ext);
		}
		
		DERSet ret = new DERSet(attributes.toArray(new Attribute[attributes.size()]));
		return ret;
	}

	private static void addAttribute(List<Attribute> attributes, DEREncodable ext)
	{
		Attribute a = new Attribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, 
				new DERSet(ext));
		attributes.add(a);
	}
}








