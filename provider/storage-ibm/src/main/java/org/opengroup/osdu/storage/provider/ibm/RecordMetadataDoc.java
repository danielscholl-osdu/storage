/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

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
		super.setTags(recordMetadata.getTags());
    	
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
		rm.setTags(this.getTags());
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
