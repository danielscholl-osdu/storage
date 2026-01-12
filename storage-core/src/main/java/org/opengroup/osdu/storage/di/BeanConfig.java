// Copyright 2017-2021, Schlumberger
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
import com.google.gson.Gson;
import java.time.Clock;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class BeanConfig {

  @Bean
  public Clock clock() {
    return systemDefaultZone();
  }

  @Bean
  public Gson gson() {
    return new Gson();
  }

  @Bean
  public MappingJackson2HttpMessageConverter strictConverter() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    return new MappingJackson2HttpMessageConverter(objectMapper) {
      @Override
      public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return ReplayRequest.class.isAssignableFrom(clazz) && MediaType.APPLICATION_JSON.includes(mediaType);
      }

      @Override
      public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return ReplayRequest.class.isAssignableFrom(clazz) && MediaType.APPLICATION_JSON.includes(mediaType);
      }
    };
  }

}
