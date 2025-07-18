# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.0.0 (2025-07-15)


### ✨ Features

* Adding resume functionality allowing another storage pod to resume on last cursor in cases where an existing pod gets killed while in the middle of processing a replay message. Also, fixed an issue when retrieving records where the no matching records exist in the batch but not at the end of the table causing the processor to exit early before viewing all records ([295e84c](https://github.com/danielscholl-osdu/storage/commit/295e84cfaa9ed8f6cc34750b07e204eefb3032e9))
* Aws implementation of replay feature ([decd639](https://github.com/danielscholl-osdu/storage/commit/decd6392e60f454edae565b03b81994c8eccca16))
* Creating system record to report on status before background process is incomplete ([89f6083](https://github.com/danielscholl-osdu/storage/commit/89f60839dbf27e63f4f049a266d40213b7091c32))
* Handling replay requests in an async parallel batch process to avoid request timeouts ([e9a0610](https://github.com/danielscholl-osdu/storage/commit/e9a06103f19eec4fc8aeed00c292207404688145))
* Using core-lib-aws library for replay implementation ([c7492fe](https://github.com/danielscholl-osdu/storage/commit/c7492feb34694267205927487c2d665b1371022b))
* Using sns-sqs pub-sub pattern instead ([6afc232](https://github.com/danielscholl-osdu/storage/commit/6afc232b6ca47ad2bc833d47060f814f58391b23))


### 🐛 Bug Fixes

* Adding content to replay message ([d0aaa35](https://github.com/danielscholl-osdu/storage/commit/d0aaa35c54653d4c57fb8f63975015089b7eac80))
* Adding record metadata to SNS record to fix indexer not knowing kind; adding all metadata that would be included with a normal storage update request ([679ddea](https://github.com/danielscholl-osdu/storage/commit/679ddea003facd640d3921c8d9e56f8c2d1a5055))
* Better handling of duplicate messages - exit early if state is already completed and extending timeout and allowing to be configurable in cases of large record counts ([eb9c1e7](https://github.com/danielscholl-osdu/storage/commit/eb9c1e7d7cf0a4196d4888d3a1c9ecffe07d3966))
* Changing implementation to use Schema service to retrieve unique kinds since there is no efficient way of querying the record metadata table to retrieve unique kinds ([cebba84](https://github.com/danielscholl-osdu/storage/commit/cebba84c1aa49ef5d46c0e1f3d4cbb198adabaff))
* Correct test assertion in OsmRecordsMetadataRepositoryTest ([d49103f](https://github.com/danielscholl-osdu/storage/commit/d49103f0bb5dfdddced273d0dbdf7aba09528e23))
* Correct test assertion in OsmRecordsMetadataRepositoryTest ([2718f28](https://github.com/danielscholl-osdu/storage/commit/2718f2857ef9d9ca79b538a1b7af34f234daa1cc))
* Creating GSI for kind and replayId for status lookup and fixing GET status endpoint ([cad9fec](https://github.com/danielscholl-osdu/storage/commit/cad9fec0cb5b9f77de33f88693463b0ed969696f))
* Enabling scheduling annotation by creating AWS main application ([7081ffb](https://github.com/danielscholl-osdu/storage/commit/7081ffb5ffbf97b2692f85c8fc19f193de9227ac))
* Failing replay unit tests ([4de64df](https://github.com/danielscholl-osdu/storage/commit/4de64df1513a1ce70167263068f578dc0e50bacc))
* Fix assertion not matching response structure ([cec3028](https://github.com/danielscholl-osdu/storage/commit/cec3028bbae5555d9761bf07c0af39fcc1500c3d))
* Fixing unit tests after pub-sub update ([97f580b](https://github.com/danielscholl-osdu/storage/commit/97f580b2969aec6dd59dbc75b5da2d7bb6891bad))
* Fixing validation and errors to match expected integration test behavior ([6984692](https://github.com/danielscholl-osdu/storage/commit/6984692dbdfe2fa045e07a4f2e5c52f86cd44aea))
* Implementing batch retrieval method to improve processing performance ([6d1cb28](https://github.com/danielscholl-osdu/storage/commit/6d1cb284723460a2090fae7d45f2d923c43fce07))
* Issue where all records for all partitions regardless of status were being counted in totalRecords. Using existing getAllRecordsFromKind class to handle totalRecords count to ensure consistency ([2c7ebc6](https://github.com/danielscholl-osdu/storage/commit/2c7ebc6884863133cc67492eee292082e0c51d0c))
* Issue where replay status is QUEUED for fully processed kinds ([207add1](https://github.com/danielscholl-osdu/storage/commit/207add152643a410b9fcd17b225e9a12499e221a))
* Logger scoping issue and removing unused records changed topic ([85fb20a](https://github.com/danielscholl-osdu/storage/commit/85fb20a6ae2b30441bf303bbb7458201bd0fe265))
* Logging auth token ([31d7d6b](https://github.com/danielscholl-osdu/storage/commit/31d7d6b89ccfc4543fe508de29d415e84fed102d))
* Passing headers to replay queue message attributes so they are consumable by replay message processor ([2552ddf](https://github.com/danielscholl-osdu/storage/commit/2552ddfa0721fd60a8271b6ca127be2290a24fe7))
* Reducing default replay batch size and allowing setting to be configured from deployment ([a407a21](https://github.com/danielscholl-osdu/storage/commit/a407a21179a2c59518ba3a726df9a28510eca0f8))
* Removing circular dependency with replay service and replay message handler after replay service split. Now overriding default replay service behavior so individual items for each kind are created in the dynamo table for better traceability and performance ([6267a5c](https://github.com/danielscholl-osdu/storage/commit/6267a5c7380b5dbb5609ac582cce9b4e74c4d28f))
* Replay all to use actual kind record since records created with invalid kind will not be replayed ([771469d](https://github.com/danielscholl-osdu/storage/commit/771469d2aab43f5dbb65b611cd49c419c7c8f762))
* Returning id instead of entity type as kind ([f5127e9](https://github.com/danielscholl-osdu/storage/commit/f5127e947f7948d7e8c016d92670053251b66ade))
* Reverting batch retrieve since it adds complexity and also slower ([8250795](https://github.com/danielscholl-osdu/storage/commit/8250795e80619e667a189dce4b4a9168476a442d))
* Several issues with handling of collaboration context, publishing record change messages, and handling of messages from SQS queue. Still having an issue with REPLAY ALL operation always trying to retrieve all kinds despite each SQS message containing the kind; this is controlled in common code and need to find an elegant solution to this ([7a9a146](https://github.com/danielscholl-osdu/storage/commit/7a9a14609a05e4f4a3fa2a87a4a567339ec037d6))
* Sns topic arn naming convention ([fa14162](https://github.com/danielscholl-osdu/storage/commit/fa141629dc7eab01873f6900d56c9cbd487568b1))
* Sonar finding .toList() ([68b0e15](https://github.com/danielscholl-osdu/storage/commit/68b0e154422e29777624ca30f72d21dcb58592cb))
* Spring cves ([2ac867c](https://github.com/danielscholl-osdu/storage/commit/2ac867c3d2981797676ab96952c7cd58997df8e3))
* Spring cves ([24c3287](https://github.com/danielscholl-osdu/storage/commit/24c3287115793191cf1e39d98af66b09f089d764))
* Start time and elapsed time calculation. Replaced all instances with AWS DTO as common DTO does not support elapsed time calculations. ([a8093db](https://github.com/danielscholl-osdu/storage/commit/a8093dbda4a050ccba80867e5fd2098c903c92c8))
* Tomcat-core netty-common json-smart cve ([909c298](https://github.com/danielscholl-osdu/storage/commit/909c298b4002f44ff784d230b51c052da78b83cd))
* Tomcat-core netty-common json-smart cve ([728a296](https://github.com/danielscholl-osdu/storage/commit/728a296d52f3e03dd0998d22c2518dc3130e8483))
* Too many headers are added to SNS message attributes causing the publishing to silently fail ([a47fec7](https://github.com/danielscholl-osdu/storage/commit/a47fec70ad7f5d2c1d4abd3be25a97985b3bc0d1))
* Typo in search url resulting in failed request ([0d116ad](https://github.com/danielscholl-osdu/storage/commit/0d116ad63715aa1420a516f152c47f66965f267d))
* Unable to reolve unused routing propety variables ([332032d](https://github.com/danielscholl-osdu/storage/commit/332032d697ed3cd2bc710904f3ccd30d4b2bee7a))
* Unit tests after removing records changed topic and replay config ([35c80a2](https://github.com/danielscholl-osdu/storage/commit/35c80a21a04d9a1786173a31aafb4e316a355402))
* Using batch save on update of replay metadata to reduce calls to Dynamo ([ea3c35a](https://github.com/danielscholl-osdu/storage/commit/ea3c35ad1074419553bb273b2d853d973b869f27))
* Using existing record sns topic ([a9ed277](https://github.com/danielscholl-osdu/storage/commit/a9ed277a2971b0d5211e5dd47ba687244168bb23))
* Using getActiveRecordsCountForKinds which is more performant than getActiveRecords ([1801c63](https://github.com/danielscholl-osdu/storage/commit/1801c632ead90d13c229946bd0ecc5eb02ea9a85))
* Using record metadata to retrieve all kinds instead of empty schema repository table which has long been deprecated ([8a54d98](https://github.com/danielscholl-osdu/storage/commit/8a54d98faf65f871f2505f055a8ded1ec0a46982))
* Wrapping scheduled poll in a request scope to handle replay messages from SQS ([2267b1a](https://github.com/danielscholl-osdu/storage/commit/2267b1a3864f31d6a1e1f21be7bcffe5c1c6c87e))


### 📚 Documentation

* Adding aws implementation plan ([70363c8](https://github.com/danielscholl-osdu/storage/commit/70363c8a1b04fde8c53ce5d9e569cefdcbe8a457))
* Adding comments of schema model that should be moved to core common after M25 ([e4a0367](https://github.com/danielscholl-osdu/storage/commit/e4a0367328ffa165e314b2364d72de7460000543))
* Adding comments to indicate conflicting schema service implementations and planned deprecation of old schema service code after M25 release ([b864203](https://github.com/danielscholl-osdu/storage/commit/b864203779964a9d759ddc507dcdfeacdefd473d))
* Adding replay sequence diagram ([26fb292](https://github.com/danielscholl-osdu/storage/commit/26fb2921d45d987716cfeedecc7506398f786ca0))
* Adding replay sequence diagram ([115c597](https://github.com/danielscholl-osdu/storage/commit/115c5971b4b52dd0103117992949695455c2b3b2))
* Removing AWS impl working doc ([08c2ffa](https://github.com/danielscholl-osdu/storage/commit/08c2ffa877f957ed2e242d86c528277c6ec99128))
* Removing SNS - simplifying architecture by using SQS only ([cb0cec9](https://github.com/danielscholl-osdu/storage/commit/cb0cec925212ac7a6de5cdd47e179283353a1719))
* Saving replay docs locally ([bccc129](https://github.com/danielscholl-osdu/storage/commit/bccc129c55f87f962d9f2690283547f99ecc9fb8))
* Updating AWS implementation guide with latest changes ([0831557](https://github.com/danielscholl-osdu/storage/commit/083155789a3a67f9850e410bf997ce76345e69c6))
* Updating AWS README and implementation guide with latest replay changes ([f49ea4d](https://github.com/danielscholl-osdu/storage/commit/f49ea4d1941ecf69d5c314c34ceb94e687988a80))
* Updating AWS README with replay feature features and considerations ([f2c81a7](https://github.com/danielscholl-osdu/storage/commit/f2c81a712e74ad8d7a0ed6e68f976c23884f8bac))
* Updating README with resume-on-failure summary ([1dcc03a](https://github.com/danielscholl-osdu/storage/commit/1dcc03ac7d0c786816d083a3f6d74df6da6f3c2b))
* Updating README with resume-on-failure summary ([05f189a](https://github.com/danielscholl-osdu/storage/commit/05f189acc54fc8fca41b91cd1cbd474ee609efe6))


### 🔧 Miscellaneous

* Adding and fixing unit tests for aws schema service and replay service ([9aa017c](https://github.com/danielscholl-osdu/storage/commit/9aa017c8b2532ab2e809630db94bb92e9c9596f5))
* Adding missing unit tests to ReplayMessageHandlerTest ([ef4d0b4](https://github.com/danielscholl-osdu/storage/commit/ef4d0b4149cfefc4f967969d95e69c846250657c))
* Adding unit test for collaboration feature for message bus impl class ([dca95be](https://github.com/danielscholl-osdu/storage/commit/dca95be93d61a2207a549bb6b5cfcaa2dbbf463d))
* Adding unit tests ([a2543f2](https://github.com/danielscholl-osdu/storage/commit/a2543f29adfafab193e0982e4ba709653a2df075))
* Adding unit tests for request scope util ([0f9004e](https://github.com/danielscholl-osdu/storage/commit/0f9004efe3b27258225259ca3fc0ceaed0957285))
* Adding unit tests to configuration and exception classes ([fcdf591](https://github.com/danielscholl-osdu/storage/commit/fcdf591c4caad338b8d07d1fa4b1e189d4e321f4))
* Addressing sonar findings ([f505c2f](https://github.com/danielscholl-osdu/storage/commit/f505c2f72af0bd9a675873d41a8fd2b09d05433d))
* Addressing sonar findings on ReplayMessageHandler; creating dedicated exception ([89ba172](https://github.com/danielscholl-osdu/storage/commit/89ba172bcf76a7f35066e725f0d2d800f91f663a))
* Cleaning up aws schema service class to be more maintainable ([bee82ca](https://github.com/danielscholl-osdu/storage/commit/bee82cab371c2e77986a639ea5433113849f8bed))
* Cleaning up folder structure ([1209418](https://github.com/danielscholl-osdu/storage/commit/120941824c1113f380c273e5d065e54e9672ef80))
* Cleaning up imports and collectors ([81acc11](https://github.com/danielscholl-osdu/storage/commit/81acc11e5d690091dfe0f04e20ee4926e2becc70))
* Cleaning up unused class fields and imports and creating constructors ([83ae87a](https://github.com/danielscholl-osdu/storage/commit/83ae87a98ac9d0d040c70b680f805699ec489717))
* Complete repository initialization ([bee9850](https://github.com/danielscholl-osdu/storage/commit/bee98501733d31c6aff4ffa3551a5f6a2a079062))
* Copy configuration and workflows from main branch ([dc88f6c](https://github.com/danielscholl-osdu/storage/commit/dc88f6cb0e82e2b4e84355adb1270b5345b68510))
* Correcting sonar findings in QueryRepo ([ea2985e](https://github.com/danielscholl-osdu/storage/commit/ea2985e5a4270aee3242bf15d878308a733f4742))
* Deleting aws helm chart ([4c15bc3](https://github.com/danielscholl-osdu/storage/commit/4c15bc3a9078612a069cd380313f5ca552809034))
* Deleting aws helm chart ([621ad7d](https://github.com/danielscholl-osdu/storage/commit/621ad7d96c6ffb2a42e00abaa5d738d9d8d03417))
* Enabling and adding integration tests for replay feature ([a962139](https://github.com/danielscholl-osdu/storage/commit/a9621397273b0b2dbd82ff39d7ebc4d244c9d929))
* Fixing sonar findings ([0117563](https://github.com/danielscholl-osdu/storage/commit/0117563063167f5121f3b6277c65ba26e30d4d5b))
* Fixing sonar findings - remove useless eq() ([d5c0dfa](https://github.com/danielscholl-osdu/storage/commit/d5c0dfaee0004c6d63632640c6b5402b7d75d39e))
* Fixing sonar findings in testing classes ([fb7dabc](https://github.com/danielscholl-osdu/storage/commit/fb7dabc8130dabec8fb2ff9438372cab97e5ce2a))
* Fixing sonar issues and making ReplayMessageProcessorAwsImpl more maintainable ([f2b13e6](https://github.com/danielscholl-osdu/storage/commit/f2b13e672c9baf664b2226011b7a73aa338534aa))
* Fixing sonar issues and reducing cognitive complexity on ParallelReplayProcessor ([1571748](https://github.com/danielscholl-osdu/storage/commit/15717489bc88b3193a30854ce540acce02c2cf82))
* Fixing sonar issues and reducing cognitive complexity on ReplaySubscriptionMessageHandler ([8dc38cc](https://github.com/danielscholl-osdu/storage/commit/8dc38cc7656e22c5aceb29eb20b9f36d323f191b))
* Fixing sonar issues and removing unecessary logging assertions from unit tests ([44e8914](https://github.com/danielscholl-osdu/storage/commit/44e89140d1d2d20305578fc7a4fe4ee06aae51ae))
* Fixing sonar issues on MessageBusImpl ([01e943f](https://github.com/danielscholl-osdu/storage/commit/01e943f04ba18e2e5874f3ca22acf8167046cfd4))
* Fixing various sonar findings ([7bc0319](https://github.com/danielscholl-osdu/storage/commit/7bc0319755289ac4ba23c931fd5c857da79359fa))
* Integration test fix for replay all - indexing was longer than wait time, so created a helper class to poll search until records are indexed ([790cc45](https://github.com/danielscholl-osdu/storage/commit/790cc454d0d09eb160ad341605a947e2cc9a0998))
* Merging application properties since relay properties are not being detected ([ee90b14](https://github.com/danielscholl-osdu/storage/commit/ee90b14ee312de23a6dc912ab102684a3a811616))
* Merging nested if statements to reduce cognitive complexity ([bf537f6](https://github.com/danielscholl-osdu/storage/commit/bf537f670b8b5a419f0b37dc9aecaca0b3510bf4))
* Merging similar query statements into private method ([7dda472](https://github.com/danielscholl-osdu/storage/commit/7dda47281c0dcc95659951aac51ded0bad95c620))
* Merging updateBatchStatus methods to single method to avoid duplications ([4c1db0a](https://github.com/danielscholl-osdu/storage/commit/4c1db0a948aaadc7e102a68e2b7bcd8b97d758e8))
* Moving all configuration options to env variables ([a4af6d7](https://github.com/danielscholl-osdu/storage/commit/a4af6d7a9d628aacbf208113e533b46136960b7a))
* Overriding 200 - success integration tests on AWS due to incompatability with AWS's implementation ([ebf63a1](https://github.com/danielscholl-osdu/storage/commit/ebf63a1944c9856e0ea3d64fa2aa69d1c7587275))
* Passing values directly instead of with eq() ([a6562a2](https://github.com/danielscholl-osdu/storage/commit/a6562a240f8d8cfed6e1b899312c35b785f4d288))
* Re-eanbling all integration tests now that replay integration tests are passing ([e464c5c](https://github.com/danielscholl-osdu/storage/commit/e464c5cb481203da30fe76edbe5df8ea33ff9158))
* Reducing cognitive complexity of ReplayServiceAwsImpl ([0b08844](https://github.com/danielscholl-osdu/storage/commit/0b088442ae377e2a93a4df9b219fe296ea58e65e))
* Refactoring state calculating logic to be O(n) ([baa2828](https://github.com/danielscholl-osdu/storage/commit/baa2828e708917f76c7b14d7fba6f5badbda655b))
* Removing helm copy from aws buildspec ([16ed8dd](https://github.com/danielscholl-osdu/storage/commit/16ed8ddbbdf7a304899b50b1111ebd1dd1a7286d))
* Removing routing properties from class fields as they are no longer needed ([39fe8f6](https://github.com/danielscholl-osdu/storage/commit/39fe8f61a6a738dd7b7a94bd3f34a124a6db8d74))
* Removing unneeded logging statements to reduce verbosity ([7728804](https://github.com/danielscholl-osdu/storage/commit/7728804d2b250bc63472b8b1ecbfa7a7fe231d0a))
* Removing unused import ([177fa1f](https://github.com/danielscholl-osdu/storage/commit/177fa1fc93ea8c87ab02ef9632dedd9ad726547c))
* Removing unused logger ([abf04aa](https://github.com/danielscholl-osdu/storage/commit/abf04aa51dce868fc657c3751946aae00693c9a1))
* Removing unused parameter ([2831832](https://github.com/danielscholl-osdu/storage/commit/283183245d72a46473669c5821941e5611b4a0fd))
* Renaming env variables to be more semantic. Reducing message size to help resolve relaibility issues caused by large batch sizes. Increasing visibility timeout to 15min to avoid duplicate messages ([684da0f](https://github.com/danielscholl-osdu/storage/commit/684da0fc02ce70945e93698ed2d24a4e08a635bf))
* Renaming LOGGER to logger; not final ([973d29b](https://github.com/danielscholl-osdu/storage/commit/973d29bbac65834d61c30669009ebdc07968a13d))
* Resolving sonar issues introduced by resume feature ([345a37b](https://github.com/danielscholl-osdu/storage/commit/345a37ba6a9abfed4683b42b5eaf151adaeedcf5))
* Reverting back to 50 batch size as indexer problem is unrelated to large batch size ([6be5f64](https://github.com/danielscholl-osdu/storage/commit/6be5f6405ff11317a09eb6ea8ad0353e0513dbb7))
* Simplifying pub-sub pattern to use a single replay topic (removing reindex topic) since there is no significant difference in functionality that is worth the added complexity ([4a282e1](https://github.com/danielscholl-osdu/storage/commit/4a282e1be3faf708a380e14ef18dd9f9780b89b7))
* Simplifying RequestScopeUtil by requiring headers ([f740f26](https://github.com/danielscholl-osdu/storage/commit/f740f268af7b9bbed49c45e58ee123be02575f4c))
* Simplifying topic param retrieval, replacing dummy account ids with dummy topics, and removing useless null check ([0f6ae0f](https://github.com/danielscholl-osdu/storage/commit/0f6ae0f3f35c8b86ee97d48cdbda01619a916403))
* Splitting AWS replay service into two separate classes; one to handle HTTP requests and one to handle SQS messages to follow single responsibility principal and to avoid circular dependencies in future refactoring to override parent replay service. Also fixed a bug where user id is not set but required when publishing storage SNS messages ([49c0a95](https://github.com/danielscholl-osdu/storage/commit/49c0a958d1a8958996bfa34fbe4915a04f980e6b))
* Updating aws core lib version ([9f1d6b6](https://github.com/danielscholl-osdu/storage/commit/9f1d6b638031e2630bdbb6ce7c8e28fef261c4b3))
* Updating aws core lib version ([db076ae](https://github.com/danielscholl-osdu/storage/commit/db076ae4a88d1d582fe1ea79061bc3a1aa164f57))
* Updating cor lib aws dependency with latest retrieve by composite key feature ([30f1019](https://github.com/danielscholl-osdu/storage/commit/30f1019acf55d71ddb45597206503f8792498b49))
* Updating NOTICE ([a361af1](https://github.com/danielscholl-osdu/storage/commit/a361af13816ec89235294d6c6c6ee3e19e520b1b))
* Updating ParallelReplayProcessor unit tests ([7f9e11c](https://github.com/danielscholl-osdu/storage/commit/7f9e11c299e57877b1d629df202dc91f99761781))
* Updating tests for ParallelReplayProcessor ([38bd070](https://github.com/danielscholl-osdu/storage/commit/38bd070c8dd03079305d18bf8ceec5fb67261b04))
* Updating tests for ReplayMessageProcessorAWSImpl ([be83232](https://github.com/danielscholl-osdu/storage/commit/be832321cfe670e4fc74603387720f5d3d522229))
* Updating unit tests for QueryRepo ([a95aa7b](https://github.com/danielscholl-osdu/storage/commit/a95aa7b6463614803ad3894116383c165d78b850))
* Updating unit tests for replay message handler ([96d4d39](https://github.com/danielscholl-osdu/storage/commit/96d4d394cad48041d171ac362b3fa01c8996f121))
* Updating unit tests to accomodate new query by GSI changes ([a9a7e3f](https://github.com/danielscholl-osdu/storage/commit/a9a7e3fbffece8af600ae8af8e0c7600e137fef8))
* Updating unit tests to reflect latest changes ([3d20768](https://github.com/danielscholl-osdu/storage/commit/3d20768dc575e444a15fe630ccc7d1641a48bd45))
* Updating unit tests to reflect latest core-lib-aws lib update changes ([ca03481](https://github.com/danielscholl-osdu/storage/commit/ca03481a6c357567d7cf3e0332daff4db47dc6f5))
* Updating unit tests with async batch processing changes ([c5dca37](https://github.com/danielscholl-osdu/storage/commit/c5dca374dc84413277a8a89eadee586efe7d612a))
* Using DpsHeaders collaboration string ([8f2cc7a](https://github.com/danielscholl-osdu/storage/commit/8f2cc7adba74b2b831ab79272b0109f44200a7d8))
* Using stream to iterate over each kind ([d3eea26](https://github.com/danielscholl-osdu/storage/commit/d3eea26b99eca94e74158c17a4b61ffc1c11242c))


### ⚙️ Continuous Integration

* Move opa config to partition level ([98f8d1e](https://github.com/danielscholl-osdu/storage/commit/98f8d1e07bb8f6272aa2656c15b24f337f6ed88f))
* Move opa config to partition level ([aa97be1](https://github.com/danielscholl-osdu/storage/commit/aa97be1ce872c779a3d413e5ab21887b93e10237))

## [2.0.0] - Major Workflow Enhancement & Documentation Release

### ✨ Features
- **Comprehensive MkDocs Documentation Site**: Complete documentation overhaul with GitHub Pages deployment
- **Automated Cascade Failure Recovery**: System automatically recovers from cascade workflow failures
- **Human-Centric Cascade Pattern**: Issue lifecycle tracking with human notifications for critical decisions
- **Integration Validation**: Comprehensive validation system for cascade workflows
- **Claude Workflow Integration**: Full Claude Code CLI support with Maven MCP server integration
- **GitHub Copilot Enhancement**: Java development environment setup and firewall configuration
- **Fork Resources Staging Pattern**: Template-based staging for fork-specific configurations
- **Conventional Commits Validation**: Complete validation system with all supported commit types
- **Enhanced PR Label Management**: Simplified production PR labels with automated issue closure
- **Meta Commit Strategy**: Advanced release-please integration for better version management
- **Push Protection Handling**: Sophisticated upstream secrets detection and resolution workflows

### 🔨 Build System
- **Workflow Separation Pattern**: Template development vs. fork instance workflow isolation
- **Template Workflow Management**: 9 comprehensive template workflows for fork management
- **Enhanced Action Reliability**: Improved cascade workflow trigger reliability with PR event filtering
- **Base64 Support**: Enhanced create-enhanced-pr action with encoding capabilities

### 📚 Documentation
- **Structured MkDocs Site**: Complete documentation architecture with GitHub Pages
- **AI-First Development Docs**: Comprehensive guides for AI-enhanced development
- **ADR Documentation**: 20+ Architectural Decision Records covering all major decisions
- **Workflow Specifications**: Detailed documentation for all 9 template workflows
- **Streamlined README**: Focused quick-start guide directing to comprehensive documentation

### 🛡️ Security & Reliability
- **Advanced Push Protection**: Intelligent handling of upstream repositories with secrets
- **Branch Protection Integration**: Automated branch protection rule management
- **Security Pattern Recognition**: Enhanced security scanning and pattern detection
- **MCP Configuration**: Secure Model Context Protocol integration for AI development

### 🔧 Workflow Enhancements
- **Cascade Monitoring**: Advanced cascade workflow monitoring and SLA management
- **Dependabot Integration**: Enhanced dependabot validation and automation
- **Template Synchronization**: Sophisticated template update propagation system
- **Issue State Tracking**: Advanced issue lifecycle management and tracking
- **GITHUB_TOKEN Standardization**: Improved token handling across all workflows

### ♻️ Code Refactoring
- **Removed AI_EVOLUTION.md**: Migrated to structured ADR approach for better maintainability
- **Simplified README Structure**: Eliminated redundancy between README and documentation site
- **Enhanced Initialization Cleanup**: Improved fork repository cleanup and setup process
- **Standardized Error Handling**: Consistent error handling patterns across all workflows

### 🐛 Bug Fixes
- **YAML Syntax Issues**: Resolved multiline string handling in workflow configurations
- **Release Workflow Compatibility**: Updated to googleapis/release-please-action@v4
- **MCP Server Configuration**: Fixed Maven MCP server connection and configuration issues
- **Cascade Trigger Reliability**: Implemented pull_request_target pattern for better triggering
- **Git Diff Syntax**: Corrected git command syntax in sync-template workflow
- **Label Management**: Standardized label usage across all workflows and templates

## [1.0.0] - Initial Release

### ✨ Features
- Initial release of OSDU Fork Management Template
- Automated fork initialization workflow
- Daily upstream synchronization with AI-enhanced PR descriptions
- Three-branch management strategy (main, fork_upstream, fork_integration)
- Automated conflict detection and resolution guidance
- Semantic versioning and release management
- Template development workflows separation

### 📚 Documentation
- Complete architectural decision records (ADRs)
- Product requirements documentation
- Development and usage guides
- GitHub Actions workflow documentation
