package org.opengroup.osdu.storage.provider.aws.mongo.mongodb;

import org.opengroup.osdu.core.aws.mongodb.MultiClusteredConfigReader;
import org.opengroup.osdu.core.aws.mongodb.config.MongoProperties;
import org.opengroup.osdu.core.aws.mongodb.config.MongoPropertiesReader;
import org.opengroup.osdu.core.aws.partition.PartitionInfoAws;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Lazy
@Component
public class MultiClusteredConfigReaderStorage implements MultiClusteredConfigReader {

    private final MongoPropertiesReader propertiesReader;

    @Autowired
    public MultiClusteredConfigReaderStorage(MongoPropertiesReader propertiesReader) {
        this.propertiesReader = propertiesReader;
    }

    @Override
    public MongoProperties readProperties(PartitionInfoAws partitionInfoAws) {
        return propertiesReader.getProperties();
    }

}
