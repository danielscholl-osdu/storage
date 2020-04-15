package org.opengroup.osdu.storage.util;

import java.io.IOException;

public class IdentityClient {
		
    public static String getTokenForUserWithAccess(){
        try {
        	String user = System.getProperty("AUTH_USER_ACCESS");
        	String pass = System.getProperty("AUTH_USER_ACCESS_PASSWORD");
			return KeyCloakProvider.getToken(user, pass);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
    }

    public static String getTokenForUserWithNoAccess(){
    	try {
        	String user = System.getProperty("AUTH_USER_NO_ACCESS");
        	String pass = System.getProperty("AUTH_USER_NO_ACCESS_PASSWORD");
			return KeyCloakProvider.getToken(user, pass);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
    }
}
