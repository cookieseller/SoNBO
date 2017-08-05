package uniko.iwvi.fgbas.magoetz.sbo.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class BusinessObject implements Serializable {
	
	private static final long serialVersionUID = 805731509510272843L;
	
	private String objectId;
	
	// TODO: create database mapping for object type <-> configFormId
	private String configFormId;
	
	private List<String> peers;
	
	// String arrays containing attribute names for displaying in respective field	
	public HashMap<String, String> displayField1 = new HashMap<String, String>();
	public HashMap<String, String> displayField2 = new HashMap<String, String>();
	public HashMap<String, String> displayField3 = new HashMap<String, String>();
	public HashMap<String, String> displayField4 = new HashMap<String, String>();
	
	public void addKeyValuePair(String key, String value, int displayfield) {
		
		switch(displayfield) {
			case 1:
				this.displayField1.put(key, value);
			break;
			case 2:
				this.displayField2.put(key, value);
			break;
			case 3:
				this.displayField3.put(key, value);
			break;
			case 4:
				this.displayField4.put(key, value);
			break;
		}
	}

	public void setObjectId(String objectId) {
		this.objectId = objectId;
	}

	public String getObjectId() {
		return objectId;
	}

	public void setConfigFormId(String configFormId) {
		this.configFormId = configFormId;
	}

	public String getConfigFormId() {
		return configFormId;
	}

}
