/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/
package org.opengroup.osdu.storage.provider.ibm.app;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;


@SpringBootApplication
@PropertySource("classpath:swagger.properties")
@ComponentScan({"org.opengroup.osdu"})
public class StorageIBMApplication {

	@PostConstruct
	void f() {

	}
	
	public static void main(String[] args) {
		
		SpringApplication.run(StorageIBMApplication.class, args);
    }
	
}
