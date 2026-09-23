package org.opengroup.osdu.storage.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class SchemaPayloadUtil {

  private SchemaPayloadUtil() {
  }

  public static JsonObject createSchemaPayload(String kind) {
    JsonObject record = new JsonObject();
    record.addProperty("kind", kind);

    JsonObject innerSchemaObjectOne = new JsonObject();
    innerSchemaObjectOne.addProperty("path", "name");
    innerSchemaObjectOne.addProperty("kind", "string");
    JsonObject innerSchemaObjectTwo = new JsonObject();
    innerSchemaObjectTwo.addProperty("path", "age");
    innerSchemaObjectTwo.addProperty("kind", "int");

    JsonArray schema = new JsonArray();
    schema.add(innerSchemaObjectOne);
    schema.add(innerSchemaObjectTwo);

    record.add("schema", schema);
    return record;
  }

  public static String validPostBody(String kind) {
    JsonObject item1Ext = new JsonObject();
    item1Ext.addProperty("indexerTip", "call911");

    JsonObject item1 = new JsonObject();
    item1.addProperty("path", "name");
    item1.addProperty("kind", "string");
    item1.add("ext", item1Ext);

    JsonObject item2Ext = new JsonObject();
    item2Ext.addProperty("address.city", "this is a weird string");
    item2Ext.addProperty("address.country", "country with two letters");

    JsonObject item2 = new JsonObject();
    item2.addProperty("path", "age");
    item2.addProperty("kind", "int");
    item2.add("ext", item2Ext);

    JsonArray schemaItems = new JsonArray();
    schemaItems.add(item1);
    schemaItems.add(item2);

    JsonObject schema = new JsonObject();
    schema.addProperty("kind", kind);
    schema.add("schema", schemaItems);
    schema.add("ext", item2Ext);

    return schema.toString();
  }
}
