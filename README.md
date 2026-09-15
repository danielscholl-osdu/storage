## Documentation
Official documentation [https://osdu.pages.opengroup.org/platform/system/storage/](https://osdu.pages.opengroup.org/platform/system/storage/)

## Running the Storage Service locally
The Storage Service is a Maven multi-module project with each cloud implemention placed in its submodule.

## Azure

Instructions for running the Azure implementation locally can be found [here](./provider/storage-azure/README.md).

## Google Cloud Implementation

All documentation for the Google Cloud implementation of Storage service lives [here](./provider/storage-gc/README.md)

## AWS

The AWS provider has been removed from this repository.

### Open API 3.0 - Swagger
- Swagger UI : https://host/context-path/swagger (will redirect to https://host/context-path/swagger-ui/index.html)
- api-docs (JSON) : https://host/context-path/api-docs
- api-docs (YAML) : https://host/context-path/api-docs.yaml

All the Swagger and OpenAPI related common properties are managed here [swagger.properties](./storage-core/src/main/resources/swagger.properties)

_Note: For Collaboration Filter exclusion, refer 'excluded paths' section in  [docs/tutorial/CollaborationContext.md](./docs/tutorial/CollaborationContext.md#excluded-paths-a-nameexcluded-pathsa)_


#### Server Url(full path vs relative path) configuration
- `api.server.fullUrl.enabled=true` It will generate full server url in the OpenAPI swagger
- `api.server.fullUrl.enabled=false` It will generate only the contextPath only
- default value is false (Currently only in Azure it is enabled)
[Reference]:(https://springdoc.org/faq.html#_how_is_server_url_generated) 

### Other platforms

1. Navigate to the module of the cloud of interest, for example, ```storage-azure```. Configure ```application.properties``` and optionally ```logback-spring.xml```. Intead of changing these files in the source, you can also provide external files at run time. 

2. Navigate to the root of the storage project, build and run unit tests in command line:
    ```bash
    mvn clean package
    ```

3. Install Redis

    > Redis is currently not required when running BYOC

    You can follow the [tutorial to install Redis locally](https://koukia.ca/installing-redis-on-windows-using-docker-containers-7737d2ebc25e) or install [docker for windows](https://docs.docker.com/docker-for-windows/install/). Make sure you are running docker with linux containers as Redis does not have a Windows image.

    ```bash
    #Pull redis image on docker
    docker pull redis
    #Run redis on docker
    docker run --name some-redis -d redis
    ```

    Install windows Redis client by following the instructions [here](https://github.com/MicrosoftArchive/redis/releases). Use default port 6379.

4. Set environment variables:
    
The AWS provider has been removed from this repository.

5. Run storage service in command line:
    ```bash
    # Running BYOC (Bring Your Own Cloud): 
    java -jar storage-service-byoc\target\storage-byoc-0.0.1-SNAPSHOT-spring-boot.jar
    
    # Running Google Cloud:
    java -jar  -Dspring.profiles.active=local storage-service-gc\target\storage-gc-0.0.1-SNAPSHOT-spring-boot.jar
    
    # Running Azure:
    java -jar storage-service-azure\target\storage-azure-0.0.1-SNAPSHOT-spring-boot.jar
    ```

6. Access the service:

    The port and path for the service endpoint can be configured in ```application.properties``` in the provider folder as following. If not specified, then  the web container (ex. Tomcat) default is used: 
    ```bash
    server.servlet.contextPath=/api/storage/v2/
    server.port=8080
    ```

    > On Azure, when you access this service endpoint, you'll see a simple html page from which you can log in to Azure AD and get an Open ID Connect JWT token. You can then use this token to access the Storage Service API. The ```/whoami``` controller displays the logged in user info, and ```/swagger``` takes you to the swagger UI. 

7. Build and test in IntelliJ:
    1. Import the maven project from the root of this project. 
    2. Create a ```JAR Application``` in ```Run/Debug Configurations``` with the ```Path to JAR``` set to the target jar file. 
    3. To run unit tests, creat a ```JUnit``` configuration in ```Run/Debug Configurations```, specify, for example:

    ```text
    Test kind: All in a package
    Search for tests: In whole project
    ```
   
## Cloud Deployment
This section describes the deployment process for each cloud provider.

### Azure

Instructions for running the Azure implementation in the cloud can be found [here](https://dev.azure.com/slb-des-ext-collaboration/open-data-ecosystem/_git/infrastructure-templates?path=%2Fdocs%2Fosdu%2FSERVICE_DEPLOYMENTS.md&_a=preview).
(This link may not be reachable for everyone, it points to Azure infrastructure templates and ensuing documentation. We are trying to find a home for that, so please stay tuned, or reach our to Dania.Kodeih@microsoft.com to get early access)

### AWS

The AWS provider has been removed from this repository.


## Running integration tests
Integration tests are located in a separate project for each cloud in the ```testing``` directory under the project root directory. 

### Azure

Instructions for running the Azure integration tests can be found [here](./provider/storage-azure/README.md).


### Google Cloud

Instructions for running the Google Cloud integration tests can be found [here](./provider/storage-gc/README.md).

### AWS

The AWS provider has been removed from this repository.

## License
Copyright 2017-2019, Schlumberger

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at 

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
