// Copyright 2017-2026, Schlumberger
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

package org.opengroup.osdu.storage.di;

import static java.time.Clock.systemDefaultZone;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class BeanConfig {

  @Bean
  public Clock clock() {
    return systemDefaultZone();
  }

  @Bean
  public Gson gson() {
    return new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL).create();
  }

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer recordNumbersCustomizer() {
    return builder -> builder
        .featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .featuresToDisable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES);
  }

  // Restores Boot's customizable mapper, which os-core-common's jsonObjectMapper bean otherwise suppresses
  @Bean
  @Primary
  public ObjectMapper objectMapper(ObjectProvider<Jackson2ObjectMapperBuilder> builderProvider) {
    return builderProvider.getIfAvailable(() -> {
      Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
      recordNumbersCustomizer().customize(builder);
      return builder;
    }).build();
  }

  @Bean
  public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
    return new MappingJackson2HttpMessageConverter(
        objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
  }

}
