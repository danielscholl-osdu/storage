# Copyright © Amazon Web Services
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
FROM amazoncorretto:8

ARG JAR_VERSION
ENV JAR_FILE=storage-aws-${JAR_VERSION}-spring-boot.jar

WORKDIR /
COPY provider/storage-aws/target/$JAR_FILE $JAR_FILE
EXPOSE 8080

CMD java -jar $JAR_FILE
