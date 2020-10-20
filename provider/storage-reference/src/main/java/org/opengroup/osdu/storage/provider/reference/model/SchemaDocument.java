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
