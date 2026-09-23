package org.opengroup.osdu.storage.util;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.info.ConnectedOuterServicesBuilder;
import org.opengroup.osdu.core.common.model.info.ConnectedOuterService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(type = "ConnectedOuterServicesBuilder")
public class CloudConnectedOuterServicesBuilder implements ConnectedOuterServicesBuilder {

  private static final String REDIS_PREFIX = "Redis-";

  private List<RedisCache> redisCaches;

  public CloudConnectedOuterServicesBuilder(List<RedisCache> redisCaches) {
    this.redisCaches = redisCaches;
  }

  @Override
  public List<ConnectedOuterService> buildConnectedOuterServices() {
    return redisCaches.stream().map(this::fetchRedisInfo).collect(Collectors.toList());
  }

  private ConnectedOuterService fetchRedisInfo(RedisCache cache) {
    String redisVersion = StringUtils.substringBetween(cache.info(), ":", "\r");
    return ConnectedOuterService.builder()
        .name(REDIS_PREFIX + StringUtils.substringAfterLast(cache.getClass().getName(), "."))
        .version(redisVersion)
        .build();
  }
}
