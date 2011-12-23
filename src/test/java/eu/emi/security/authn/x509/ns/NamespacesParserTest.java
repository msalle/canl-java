/*
 * Copyright (c) 2011-2012 ICM Uniwersytet Warszawski All rights reserved.
 * See LICENCE.txt file for licensing information.
 */
package eu.emi.security.authn.x509.ns;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static junit.framework.Assert.*;

import org.junit.Test;

import eu.emi.security.authn.x509.helpers.ns.EuGridPmaNamespacesParser;
import eu.emi.security.authn.x509.helpers.ns.EuGridPmaNamespacesStore;
import eu.emi.security.authn.x509.helpers.ns.NamespacePolicy;

public class NamespacesParserTest
{
	public static final String PFX = "src/test/resources/namespaces/";
	
	private static Case[] CORRECT_TEST_CASES = {
		new Case(PFX + "4798da47.namespaces",
				new String[] {
				"CN=HKU Grid CA,DC=GRID,DC=HKU,DC=HK",
				"CN=Test,C=EU",
				"CN=http://www.net.org,C=EU",
				},
				new String[][] {
				{".*,CN=.*,C=EU", "CN=.*,C=EU", ".*,CN=.*,O=t,C=EU", "CN=.*,O=t,C=EU"},
				{".*,CN=.*,O=q,C=EU", "CN=.*,O=q,C=EU", ".*,CN=.*,.*,C=E.", "CN=.*,.*,C=E."},
				{"CN=ha\\,ha \\,ha,EMAILADDRESS=c@d,EMAILADDRESS=a@b,EMAILADDRESS=some@email"},
				})
	};

	private static String[] INCORRECT_TEST_CASES = {
		PFX+"00000001.namespaces",
		PFX+"00000002.namespaces",
		PFX+"00000003.namespaces",
		PFX+"00000004.namespaces",
		PFX+"00000005.namespaces"
	};
	
	
	@Test
	public void testEuGridPMADistro()
	{
		File f = new File(PFX+"eugridpma-namespaces");
		String []files = f.list();
		for (String file: files)
		{
			File toTest = new File(f, file);
			if (toTest.isDirectory())
				continue;
			System.out.println("Testing file " + file);
			EuGridPmaNamespacesParser parser = new EuGridPmaNamespacesParser(
					f.getPath()+File.separator+file);
			EuGridPmaNamespacesStore store = new EuGridPmaNamespacesStore();
			List<NamespacePolicy> result;
			try
			{
				result = parser.parse();
			} catch (IOException e)
			{
				e.printStackTrace();
				fail(e.toString());
				return; //dummy
			}
			store.setPolicies(result);
		}
	}
	
	@Test
	public void testNormalize() throws IOException 
	{
		List<String> pattern = EuGridPmaNamespacesParser.normalize("/C=E./.*/CN=.*");
		assertEquals("CN=.*,.*,C=E.", pattern.get(0));
		assertEquals(".*,CN=.*,.*,C=E.", pattern.get(1));
	}
	
	@Test
	public void testCorrect()
	{
		for (Case testCase: CORRECT_TEST_CASES)
		{
			System.out.println("Testing file " + testCase.file);
			EuGridPmaNamespacesParser parser = new EuGridPmaNamespacesParser(testCase.file);
			EuGridPmaNamespacesStore store = new EuGridPmaNamespacesStore();
			testCase.testCase(store, parser);
		}
	}
	
	@Test
	public void testIncorrect()
	{
		for (String testCase: INCORRECT_TEST_CASES)
		{
			EuGridPmaNamespacesParser parser = new EuGridPmaNamespacesParser(testCase);
			try
			{
				parser.parse();
				fail("Should get an error but parsing was successful, file " + testCase);
			} catch (IOException e)
			{
				//OK
				System.out.println("Got an expected error for file " + testCase + 
						": " + e.getMessage());
			}
		}
	}
}