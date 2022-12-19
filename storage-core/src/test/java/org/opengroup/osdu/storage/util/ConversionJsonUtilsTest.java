package org.opengroup.osdu.storage.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.ConversionStatus;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class ConversionJsonUtilsTest {

    public static final String UNEXPECTED_DATA_FORMAT_JSON_OBJECT = "CRS conversion: unexpected data format. Field %s should be a JsonObject";
    public static final String UNEXPECTED_DATA_FORMAT_JSON_ARRAY = "CRS conversion: unexpected data format. Field %s should be a JsonArray";
    private static final String PROPERTY_NAME = "testProperty";
    private static final String JSON_WITH_PROPERTY = "{\"" + PROPERTY_NAME + "\": \"somevalue\"}";
    private static final String JSON_WITH_NESTED_OBJECT = "{\"" + PROPERTY_NAME + "\":{\"somekey\":\"somevalue\"}}";
    private static final String JSON_WITH_NESTED_ARRAY = "{\"" + PROPERTY_NAME + "\":[\"somevalue\"]}";
    private static final String JSON_WITH_NULL_PROPERTY = "{\"" + PROPERTY_NAME + "\": null}";

    private ConversionJsonUtils conversionJsonUtils = new ConversionJsonUtils();
    private ConversionStatus.ConversionStatusBuilder statusBuilder = ConversionStatus.builder();

    private JsonObject jsonObject;

    /**
     * ---isJsonObjectContainsProperty---
     **/

    @Test
    public void isJsonObjectContainsProperty_shouldReturnTrue_whenPropertyPresentedAndNotNull() {
        jsonObject = JsonParser.parseString(JSON_WITH_PROPERTY).getAsJsonObject();

        assertTrue(conversionJsonUtils.isJsonObjectContainsProperty(jsonObject, PROPERTY_NAME));
    }

    @Test
    public void isJsonObjectContainsProperty_shouldReturnFalse_whenPropertyNotPresented() {
        jsonObject = JsonParser.parseString(JSON_WITH_PROPERTY).getAsJsonObject();

        assertFalse(conversionJsonUtils.isJsonObjectContainsProperty(jsonObject, "wrong_property"));
    }

    @Test
    public void isJsonObjectContainsProperty_shouldReturnFalse_whenPropertyHasNullValue() {
        jsonObject = JsonParser.parseString(JSON_WITH_NULL_PROPERTY).getAsJsonObject();

        assertFalse(conversionJsonUtils.isJsonObjectContainsProperty(jsonObject, PROPERTY_NAME));
    }

    @Test
    public void isJsonObjectContainsProperty_shouldReturnFalse_whenNullASJsonObjectPassed() {
        assertFalse(conversionJsonUtils.isJsonObjectContainsProperty(jsonObject, PROPERTY_NAME));
    }

    /**
     * ---isPropertyJsonObject---
     **/

    @Test
    public void isPropertyJsonObject_shouldReturnTrue_whenProvidedJsonObject() {
        jsonObject = JsonParser.parseString(JSON_WITH_NESTED_OBJECT).getAsJsonObject();

        statusBuilder = ConversionStatus.builder();

        assertTrue(conversionJsonUtils.isPropertyJsonObject(jsonObject, PROPERTY_NAME, statusBuilder));

        assertTrue(statusBuilder.getErrors().isEmpty());
    }

    @Test
    public void isPropertyJsonObject_shouldReturnFalse_whenProvidedJsonElementIsNotJsonObject() {
        jsonObject = JsonParser.parseString(JSON_WITH_PROPERTY).getAsJsonObject();
        statusBuilder = ConversionStatus.builder();

        assertFalse(conversionJsonUtils.isPropertyJsonObject(jsonObject, PROPERTY_NAME, statusBuilder));

        assertEquals(statusBuilder.getErrors().get(0), String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, PROPERTY_NAME));
    }

    @Test
    public void isPropertyJsonObject_shouldReturnFalse_whenProvidedJsonElementIsJsonNull() {
        jsonObject = JsonParser.parseString(JSON_WITH_NULL_PROPERTY).getAsJsonObject();
        statusBuilder = ConversionStatus.builder();

        assertFalse(conversionJsonUtils.isPropertyJsonObject(jsonObject, PROPERTY_NAME, statusBuilder));

        assertEquals(statusBuilder.getErrors().get(0), String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, PROPERTY_NAME));
    }

    @Test
    public void isPropertyJsonObject_shouldReturnFalse_whenProvidedJsonObjectIsNull() {
        statusBuilder = ConversionStatus.builder();

        assertFalse(conversionJsonUtils.isPropertyJsonObject(jsonObject, PROPERTY_NAME, statusBuilder));

        assertEquals(statusBuilder.getErrors().get(0), String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, PROPERTY_NAME));
    }

    /**
     * ---isPropertyJsonArray---
     **/

    @Test
    public void isPropertyJsonArray_shouldReturnTrue_whenProvidedJsonArray() {
        jsonObject = JsonParser.parseString(JSON_WITH_NESTED_ARRAY).getAsJsonObject();

        statusBuilder = ConversionStatus.builder();

        assertTrue(conversionJsonUtils.isPropertyJsonArray(jsonObject, PROPERTY_NAME, statusBuilder));

        assertTrue(statusBuilder.getErrors().isEmpty());
    }

    @Test
    public void isPropertyJsonArray_shouldReturnFalse_whenProvidedJsonElementIsNotJsonArray() {
        jsonObject = JsonParser.parseString(JSON_WITH_PROPERTY).getAsJsonObject();
        statusBuilder = ConversionStatus.builder();

        assertFalse(conversionJsonUtils.isPropertyJsonArray(jsonObject, PROPERTY_NAME, statusBuilder));

        assertEquals(statusBuilder.getErrors().get(0), String.format(UNEXPECTED_DATA_FORMAT_JSON_ARRAY, PROPERTY_NAME));
    }

    @Test
    public void isPropertyJsonArray_shouldReturnFalse_whenProvidedJsonElementIsJsonNull() {
        jsonObject = JsonParser.parseString(JSON_WITH_NULL_PROPERTY).getAsJsonObject();
        statusBuilder = ConversionStatus.builder();

        assertFalse(conversionJsonUtils.isPropertyJsonObject(jsonObject, PROPERTY_NAME, statusBuilder));

        assertEquals(statusBuilder.getErrors().get(0), String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, PROPERTY_NAME));
    }

    @Test
    public void isPropertyJsonArray_shouldReturnFalse_whenProvidedJsonArrayIsNull() {
        statusBuilder = ConversionStatus.builder();

        assertFalse(conversionJsonUtils.isPropertyJsonObject(jsonObject, PROPERTY_NAME, statusBuilder));

        assertEquals(statusBuilder.getErrors().get(0), String.format(UNEXPECTED_DATA_FORMAT_JSON_OBJECT, PROPERTY_NAME));
    }
}
