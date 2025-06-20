/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm;

import java.util.Map;

import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;


public class SchemaDoc {
	
    private String _id;
    private String _rev;
    private String user;
    private SchemaItem[] schema;
    private Map<String,Object> extension;

    
	public SchemaDoc(Schema schema, String user) {
		this.setId(schema.getKind());
		this.setExtension(schema.getExt());
		this.setSchema(schema.getSchema());
		this.setUser(user);		
	}
	
	public String getId() {
		return _id;
	}
	public String getKind() {
		return _id;
	}
	public void setId(String _id) {
		this._id = _id;
	}
	public String getRev() {
		return _rev;
	}
	public void setRev(String _rev) {
		this._rev = _rev;
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
