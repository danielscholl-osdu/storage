## Running the Storage Service locally
The Storage Service is a Maven multi-module project with each cloud implemention placed in its submodule.

### Azure

Instructions for running the Azure implementation locally can be found [here](./provider/storage-azure/README.md).

## Google Cloud Implementation

All documentation for the Google Cloud implementation of Storage service lives [here](./provider/storage-gcp/README.md)

## AWS

Instructions for running and testing this service can be found [here](./provider/storage-aws/README.md)



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
    
**AWS**: AWS service account credentials are read from the environment variables in order to 
authenticate AWS requests. The following variables can be set as either system environment 
variables or user environment variables. User values will take precedence if both are set.
1. `AWS_ACCESS_KEY_ID=<YOURAWSACCESSKEYID>`
2. `AWS_SECRET_KEY=<YOURAWSSECRETKEY>`

Note that these values can be found in the IAM stack's export values in the AWS console. To 
deploy resources to the AWS console, see the deployment section below.

5. Run storage service in command line:
    ```bash
    # Running BYOC (Bring Your Own Cloud): 
    java -jar storage-service-byoc\target\storage-byoc-0.0.1-SNAPSHOT-spring-boot.jar
    
    # Running Google Cloud:
    java -jar  -Dspring.profiles.active=local storage-service-gc\target\storage-gc-0.0.1-SNAPSHOT-spring-boot.jar
    
    # Running Azure:
    java -jar storage-service-azure\target\storage-azure-0.0.1-SNAPSHOT-spring-boot.jar
    
    # Running AWS:
    java -jar provider\storage-aws\target\storage-aws-0.0.1-SNAPSHOT-spring-boot.jar
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
This guide assumes that you already have an AWS account and have admin access to it in order to 
configure the environment.
1. **CodeCommit setup:** The automated deployment pipeline will require a CodeCommit repo to be 
    set up for storage service. Push the current Storage Service codebase to this repo in the 
    branch that you'd like to deploy from.
    1. NOTE: After the move the GitLab, we will revisit and see if deployment straight from GitLab 
    is possible.
2. **Pipeline setup:** In the AWS Console, navigate the the CloudFormation service. Create a new 
    stack, and upload the template found in 
    `provider/storage-aws/CloudFormation/Manual/01-CreateCodePipeline.yml`. You can upload via S3 
    or copy/paste into the Designer, it doesn't make a difference. Click "next" and upload any 
    of the default parameter values as-needed. For example, the notification email, environment, 
    and the CodeCommit repository and branch names are likely to change, as well as the region if 
    you are deploying somewhere other than us-east-1.
3. **Application deployment:** Allow the stack to finish deploying, then navigate to CodePipeline 
    in the AWS console and locate the new pipeline you just deployed. It should be automatically 
    be performing an initial run. Allow it to complete and validate that there weren't any errors.
    1. The pipeline is subscribed to the CodeCommit branch you specified in the CodePipeline stack, 
    and the pipeline will automatically be kicked off and update and changed resources automatically 
    when code is committed to the subscribed branch.
    2. When the deployment is complete, you can return the the CloudFormation console and locate 
    the stack with the name starting with `<env>-os-storage-master-stack-IAMCredentialsStack`. 
    Select it, click on the 'Outputs' tab, and you will find the AWS access key and secret you 
    need for the environment variables (required for running integration tests against AWS 
    resources).
        1. `StorageServiceIamUserAccessKeyId` contains the value you need for `AWS_ACCESS_KEY_ID`
        2. `StorageServiceIamUserSecretAccessKey` contains the value you need for `AWS_SECRET_KEY`


## Running integration tests
Integration tests are located in a separate project for each cloud in the ```testing``` directory under the project root directory. 

### Azure

Instructions for running the Azure integration tests can be found [here](./provider/storage-azure/README.md).


### Google Cloud

Instructions for running the Google Cloud integration tests can be found [here](./provider/storage-gc/README.md).

### AWS

Instructions for running the AWS integration tests can be found [here](./provider/storage-aws/README.md).

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
