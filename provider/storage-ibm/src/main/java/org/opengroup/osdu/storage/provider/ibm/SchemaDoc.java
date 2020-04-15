/**
 * Copyright 2020 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
