/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm;

import org.opengroup.osdu.storage.provider.interfaces.ISomeBasicInterface;
import org.springframework.stereotype.Component;

@Component
public class SomeBasicInterfaceImpl implements ISomeBasicInterface {
	@Override
	public String hello() {
		return "hello from IBM Cloud";
	}
}
