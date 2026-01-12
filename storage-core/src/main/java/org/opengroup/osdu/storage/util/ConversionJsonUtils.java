package org.opengroup.osdu.storage.util;

import static org.opengroup.osdu.storage.conversion.CrsConversionServiceErrorMessages.UNEXPECTED_DATA_FORMAT_JSON_ARRAY;
import static org.opengroup.osdu.storage.conversion.CrsConversionServiceErrorMessages.UNEXPECTED_DATA_FORMAT_JSON_OBJECT;

import org.opengroup.osdu.core.common.model.storage.ConversionStatus;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;

@Component
public class ConversionJsonUtils {

    public boolean isJsonObjectContainsProperty(JsonObject jsonObject, String propertyName) {
        return jsonObject != null && jsonObject.has(propertyName) && !jsonObject.get(propertyName).isJsonNull();
    }

    public boolean isPropertyJsonObject(JsonObject jsonObject, String propertyName, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        if (jsonObject == null || jsonObject.get(propertyName).isJsonNull() || !jsonObject.get(propertyName).isJsonObject()) {
            statusBuilder.addError(String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, propertyName));
            return false;
        }
        return true;
    }

    public boolean isPropertyJsonArray(JsonObject jsonObject, String propertyName, ConversionStatus.ConversionStatusBuilder statusBuilder) {
        if (jsonObject == null || jsonObject.get(propertyName).isJsonNull() || !jsonObject.get(propertyName).isJsonArray()) {
            statusBuilder.addError(String.format(UNEXPECTED_DATA_FORMAT_JSON_ARRAY, propertyName));
            return false;
        }
        return true;
    }
}
