package uniko.iwvi.fgbas.magoetz.sbo.services;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import uniko.iwvi.fgbas.magoetz.sbo.objects.ConfigurationObject;
import uniko.iwvi.fgbas.magoetz.sbo.util.Utilities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ConfigService implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private QueryService queryService = new QueryService();
	
	public ConfigurationObject getConfigurationObject(String objectName) {
		
		// TODO: check if object type exists 
		// TODO: change 3 to objectJSON (why not working?)
		JsonObject jsonConfigObject = queryService.getJsonObject("objects", objectName, "4");
		// log json
		Utilities utilities = new Utilities();
		utilities.printJson(jsonConfigObject, "Parsed object object json");
		
		// get config information
		ConfigurationObject configObject = new ConfigurationObject();
		// object type
		JsonElement jsonFirstConfigElement = jsonConfigObject.get(objectName);
		JsonObject jsonFirstConfigObject = jsonFirstConfigElement.getAsJsonObject();
		//object title
		String objectTitle = jsonFirstConfigObject.get("objectTitle").getAsString();
		configObject.setObjectTitle(objectTitle);
		// object class
		String objectClass = jsonFirstConfigObject.get("objectClass").toString();
		configObject.setObjectClass(objectName);
		// peers
		JsonElement firstLevelConfigElementPeers = jsonFirstConfigObject.get("peers");
		String[] peers = firstLevelConfigElementPeers.getAsString().split(",");
		List<String> peerList = Arrays.asList(peers);
		configObject.setPeers(peerList);
		// attributes
		// TODO: change 6 to attributeJSON
		ArrayList<JsonObject> jsonAttributeObjectList = queryService.getJsonObjects("attributes", objectName, "6");
		
		for(JsonObject jsonAttributeObject : jsonAttributeObjectList) {
			
			utilities.printJson(jsonAttributeObject, "Parsed attribute json");
			
			JsonElement jsonFirstAttrElement = jsonAttributeObject.get("attribute");
			JsonObject jsonFirstAttrObject = jsonFirstAttrElement.getAsJsonObject();
			
			String name = jsonFirstAttrObject.get("name").getAsString();
			String datasource = jsonFirstAttrObject.get("datasource").getAsString();
			String query = jsonFirstAttrObject.get("query").getAsString();
			String fieldname = jsonFirstAttrObject.get("fieldname").getAsString();
			int displayfield = jsonFirstAttrObject.get("displayfield").getAsInt();
			
			configObject.addConfigurationObjectAttribute(name, datasource, query, fieldname, displayfield);
		}
		
		return configObject;
	}
}
