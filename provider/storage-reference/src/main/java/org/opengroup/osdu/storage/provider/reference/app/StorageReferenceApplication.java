package org.opengroup.osdu.storage.provider.reference.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"org.opengroup.osdu"})
public class StorageReferenceApplication {

  public static void main(String[] args) {

    SpringApplication.run(StorageReferenceApplication.class, args);
  }

}
