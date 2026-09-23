package org.opengroup.osdu.storage.provider.azure.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.slf4j.Logger;
/* This mixin is used to fix https://github.com/microsoft/spring-data-cosmosdb/issues/423
Upgrading cosmosdb spring library will also solve this. The fix is in a major release which needs lot of changes
This mixin is injected in QueryRepositoryImpl constructor. After upgrade these can be deleted.
*/
public interface QueryItemMixIn {
    @JsonIgnore
    abstract Logger getLogger();
}
