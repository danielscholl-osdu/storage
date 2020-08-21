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

import java.util.List;

import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

public class RecordMetadataDoc extends RecordMetadata {
	
	private String _id;
    private String _rev;
    
    public RecordMetadataDoc(String id, String rev) {
		this._id = id;
		this._rev = rev;
	}
    
    public RecordMetadataDoc(RecordMetadata recordMetadata) {
    	this.setId(recordMetadata.getId());
    	
    	super.setKind(recordMetadata.getKind());
    	super.setAcl(recordMetadata.getAcl());
    	super.setLegal(recordMetadata.getLegal());
    	super.setAncestry(recordMetadata.getAncestry());
    	
    	super.setModifyUser(recordMetadata.getModifyUser());
    	super.setModifyTime(recordMetadata.getModifyTime());
    	super.setCreateTime(recordMetadata.getCreateTime());
    	super.setStatus(recordMetadata.getStatus());
    	super.setUser(recordMetadata.getUser());
    	super.setGcsVersionPaths(recordMetadata.getGcsVersionPaths());
	}
    
    public RecordMetadata getRecordMetadata() {
    	RecordMetadata rm = new RecordMetadata();
    	rm.setId(this.getId());
    	rm.setKind(this.getKind());
    	rm.setAcl(this.getAcl());
    	rm.setLegal(this.getLegal());
    	rm.setAncestry(this.getAncestry());
    	rm.setGcsVersionPaths(this.getGcsVersionPaths());
    	rm.setStatus(this.getStatus());
    	rm.setUser(this.getUser());
    	rm.setCreateTime(this.getCreateTime());
    	rm.setModifyUser(this.getModifyUser());
    	rm.setModifyTime(this.getModifyTime());
    	return rm;
    }
	
    public String getId() {
		return _id;
	}
	public void setId(String id) {
		this._id = id;
	}
	
	public String getRev() {
		return _rev;
	}
	public void setRev(String rev) {
		this._rev = rev;
	}
	
	public void addGcsPath(long version) {
		List<String> temp = super.getGcsVersionPaths();
		temp.add(String.format("%s/%s/%s", super.getKind(), this._id, version));
		super.setGcsVersionPaths(temp);
	}

}
