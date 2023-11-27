// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.storage.provider.aws.mongo.mongodb;

import org.opengroup.osdu.core.aws.mongodb.AbstractMultiClusteredConfigReader;
import org.opengroup.osdu.core.aws.ssm.SSMManagerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component
public class MultiClusteredConfigReaderStorage extends AbstractMultiClusteredConfigReader {

    @Autowired
    public MultiClusteredConfigReaderStorage(SSMManagerUtil ssmManagerUtil) {
        super(ssmManagerUtil);
    }

    @Override
    protected String getDatabaseName(String environment) {
        return environment + "_osdu_storage";
    }
}
