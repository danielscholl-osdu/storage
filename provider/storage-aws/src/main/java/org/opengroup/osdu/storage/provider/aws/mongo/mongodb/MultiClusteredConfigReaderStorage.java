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
