package org.opengroup.osdu.storage.provider.reference.cache;

import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroupCache extends RedisCache<String, Groups> {

	public GroupCache(
		@Value("${gcp.redis.host}") final String redisHost,
		@Value("${gcp.redis.port}") final Integer redisPort,
		@Value("${gcp.redis.exp.time}") final Integer expTimeSec) {
		super(redisHost, redisPort, expTimeSec, String.class, Groups.class);
	}
}