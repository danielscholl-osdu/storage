package org.opengroup.osdu.storage.provider.reference.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "StorageRecord")
public class RecordMetadataDocument {

  private String id;
  private String kind;
  private Acl acl;
  private Legal legal;
  private RecordAncestry ancestry;
  private List<String> gcsVersionPaths = new ArrayList();
  private RecordState status;
  private String user;
  private Long createTime;
  private String modifyUser;
  private Long modifyTime;
}