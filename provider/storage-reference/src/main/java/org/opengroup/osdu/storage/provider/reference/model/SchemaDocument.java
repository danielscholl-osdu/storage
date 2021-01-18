/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.reference.model;

import java.util.Map;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "StorageSchema")
public class SchemaDocument {

  private String kind;
  private String rev;
  private String user;
  private SchemaItem[] schema;
  private Map<String, Object> extension;


  public SchemaDocument(Schema schema, String user) {
    this.setKind(schema.getKind());
    this.setExtension(schema.getExt());
    this.setSchema(schema.getSchema());
    this.setUser(user);
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getRev() {
    return rev;
  }

  public void setRev(String rev) {
    this.rev = rev;
  }

  public Map<String, Object> getExtension() {
    return extension;
  }

  public void setExtension(Map<String, Object> extension) {
    this.extension = extension;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public SchemaItem[] getSchema() {
    return schema;
  }

  public void setSchema(SchemaItem[] schemaItems) {
    this.schema = schemaItems;
  }

}
