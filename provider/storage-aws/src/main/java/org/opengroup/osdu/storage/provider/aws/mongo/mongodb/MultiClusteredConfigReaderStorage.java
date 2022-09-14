package org.opengroup.osdu.storage.provider.aws.mongo.mongodb;

import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import org.opengroup.osdu.core.aws.mongodb.MultiClusteredConfigReader;
import org.opengroup.osdu.core.aws.mongodb.config.MongoDBCredentialsSecret;
import org.opengroup.osdu.core.aws.mongodb.config.MongoProperties;
import org.opengroup.osdu.core.aws.partition.PartitionInfoAws;
import org.opengroup.osdu.core.aws.secrets.SecretsManager;
import org.opengroup.osdu.core.aws.ssm.SSMManagerUtil;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Lazy
@Component
public class MultiClusteredConfigReaderStorage implements MultiClusteredConfigReader {
    @Value("${aws.environment}")
    private String environment;
    @Value("${aws.region}")
    private String amazonRegion;
    private final SSMManagerUtil ssmUtil;
    private final SecretsManager secretsManager;

    @Autowired
    public MultiClusteredConfigReaderStorage(SSMManagerUtil ssmManagerUtil) {
        this.ssmUtil = ssmManagerUtil;
        secretsManager = new SecretsManager();
    }

    @Override
    public MongoProperties readProperties(PartitionInfoAws partitionInfoAws) {

        String ssmPathPattern = "/osdu/%s/tenants/%s/storage/mongoDB";
        String secretManagerPathPattern = "/osdu/%s/tenants/%s/storage/mongoDB/credentials-arn";

        String smSecretId = String.format(secretManagerPathPattern,
                environment,
                partitionInfoAws.getTenantId());
        MongoDBCredentialsSecret dbCredentials = secretsManager.getSecret(smSecretId, amazonRegion, MongoDBCredentialsSecret.class);

        String ssmPath = String.format(ssmPathPattern,
                environment,
                partitionInfoAws.getTenantId());
        List<Parameter> tableParams = this.ssmUtil.getSsmParamsUnderPath(ssmPath);
        if (tableParams.size() <= 0) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "table info not found in ssm");
        } else {


            Map<String, String> stringStringMap = tableParams.stream()
                    .collect(Collectors.toMap(Parameter::getName, Parameter::getValue));
            String endpoint = stringStringMap.get(ssmPath + "/endpoint");
            String port = stringStringMap.get(ssmPath + "/port");
            String retryWrites = stringStringMap.get(ssmPath + "/retryWrites");
            String writeMode = stringStringMap.get(ssmPath + "/writeMode");
            String useSrvEndpoint = stringStringMap.get(ssmPath + "/useSrvEndpoint");
            String enableTLS = stringStringMap.get(ssmPath + "/enableTLS");
            String maxPoolSize = stringStringMap.get(ssmPath + "/maxPoolSize");
            String readPreference = stringStringMap.get(ssmPath + "/readPreference");
            String maxIdleTimeMS = stringStringMap.get(ssmPath + "/MaxIdleTimeMS");

            return MongoProperties.builder()
                    .endpoint(endpoint)
                    .port(port)
                    .username(dbCredentials.getUsername())
                    .password(dbCredentials.getPassword())
                    .authDatabase(dbCredentials.getAuthDB())
                    .retryWrites(retryWrites)
                    .writeMode(writeMode)
                    .useSrvEndpointStr(useSrvEndpoint)
                    .enableTLS(enableTLS)
                    .databaseName(this.environment + "_osdu_storage")
                    .maxPoolSize(maxPoolSize)
                    .readPreference(readPreference)
                    .maxIdleTimeMS(maxIdleTimeMS)
                    .build();
        }
    }
}
