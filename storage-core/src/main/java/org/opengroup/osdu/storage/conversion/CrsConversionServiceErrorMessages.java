// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.conversion;

public class CrsConversionServiceErrorMessages {
    /**
     * Please do not change these error messages as these are included in public interface.
     * Or if you do need to change, just be aware those changes can be breaking changes to our consumers.
     */
    public static final String MISSING_DATA_BLOCK = "CRS Conversion: DataBlock is missing or empty in this record, no conversion applied.";
    public static final String ILLEGAL_METAITEM_ARRAY = "CRS conversion: error when extract metaItem array, error message: %s, no conversion applied.";
    public static final String MISSING_META_KIND = "CRS conversion: Required property 'kind' in meta block is missing or empty, no conversion applied.";
    public static final String MISSING_PROPERTY_NAMES = "CRS conversion: 'propertyNames' in meta block is missing or empty, no conversion applied";
    public static final String UNSUPPORTED_PROPERTY_NUMBER = "CRS conversion: Inappropriate number of properties for point conversion, unsupported property set, no conversion applied.";
    public static final String ILLEGAL_PROPERTY_NAMES = "CRS conversion: 'propertyNames' illegal, no conversion applied.";
    public static final String MISSING_REFERENCE = "CRS conversion: 'persistableReference' missing";
    public static final String MISSING_PROPERTY = "CRS conversion: property '%s' is missing in datablock, no conversion applied.";
    public static final String PROPERTY_VALUE_CAST_ERROR = "CRS conversion: cannot cast the value of property '%s' to double, error message: %s, no conversion applied.";
    public static final String ILLEGAL_PROPERTY_VALUE = "CRS conversion: illegal value for property '%s', error message: %s, no conversion applied.";
    public static final String MISSING_PROPERTY_FOR_POINT_CONVERSION = "CRS conversion: required properties for point conversion not sufficient, no conversion applied.";
    public static final String INVALID_NESTED_PROPERTY_NAME = "CRS conversion: invalid nested property name: '%s', no conversion applied.";
    public static final String MISSING_POINTS_IN_NESTED_PROPERTY = "CRS conversion: missing the property 'points' in nested property, no conversion applied.";
    public static final String BAD_REQUEST_FROM_CRS = "CRS conversion: bad request from crs converter, illegal persistable reference, no conversion applied.";
    public static final String CRS_OTHER_ERROR = "Response from CRS converter: %s";
    public static final String ILLEGAL_DATA_IN_NESTED_PROPERTY = "CRS conversion: illegal value in nested property '%s', error message: %s, no conversion applied.";
    public static final String PAIR_FAILURE = "CRS conversion: Unknown coordinate pair '%s'.";
}
