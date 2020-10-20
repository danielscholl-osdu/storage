package org.opengroup.osdu.storage.provider.reference.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "TenantInfo")
public class TenantInfoDocument {

  private String id;
  private List<String> groups;
}
