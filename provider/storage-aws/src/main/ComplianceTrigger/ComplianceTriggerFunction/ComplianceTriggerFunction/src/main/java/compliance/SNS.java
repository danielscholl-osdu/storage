// Copyright © 2020 Amazon Web Services
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package compliance;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class SNS {
    @JsonProperty("Test")
    public MessageAttribute test;

    @JsonProperty("Type")
    public String type;

    @JsonProperty("MessageId")
    public String messageId;

    @JsonProperty("TopicArn")
    public String topicArn;

    @JsonProperty("Message")
    public String message;

    @JsonProperty("Timestamp")
    public String timestamp;

    @JsonProperty("SignatureVersion")
    public int signatureVersion;

    @JsonProperty("Signature")
    public String signature;

    @JsonProperty("SigningCertURL")
    public String signingCertUrl;

    @JsonProperty("UnsubscribeURL")
    public String unsubscribeUrl;

    @JsonProperty("MessageAttributes")
    public SNSMessageAttributes messageAttributes;
}
