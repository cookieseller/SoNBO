package uniko.iwvi.fgbas.magoetz.sbo.services;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Attribute;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Datasource;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Node;
import uniko.iwvi.fgbas.magoetz.sbo.objects.NodeTypeCategory;
import uniko.iwvi.fgbas.magoetz.sbo.objects.NodeType;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Query;
import uniko.iwvi.fgbas.magoetz.sbo.objects.AdjacencyQuery;
import uniko.iwvi.fgbas.magoetz.sbo.objects.QueryResult;
import uniko.iwvi.fgbas.magoetz.sbo.util.Utilities;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ObjectService implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private ConfigService configService = new ConfigService();
	
	private QueryService queryService = new QueryService();

	public Node getNode(String id, boolean nodePreview) {
		
		// 1. CREATE NEW BUSINESS OBJECT
		Node node = new Node();
	
		// set object id and name
		node.setId(id);
		//businessObject.setObjectName(objectName);
		
		// 2. GET CONFIGURATION DOCUMENT FOR OBJECT TYPE

		//NodeType configObject = configService.getConfigurationObject(objectName);
		NodeType configObject = configService.getNodeTypeById(id);
		node.setNodeType(configObject.getNodeTypeName());
		
		// 3. RETRIEVE ATTRIBUTES OF BUSINESS OBJECT
		
		// set object class
		node.setNodeTypeCategory(configObject.getNodeTypeCategory());
		
		// set object image
		// TODO: set individual image if available
		NodeTypeCategory nodeTypeCategory = configService.getNodeTypeCategory(node.getNodeTypeCategory());
		node.setNodeImage(nodeTypeCategory.getDefaultImage());
		
		// get business object attributes
		ArrayList<QueryResult> queryResultList = this.getNodeAttributes(configObject, id, nodePreview);
		
		// load attribute key and value into business object
		node = loadAttributes(node, configObject, queryResultList, nodePreview);
	
		return node;
	}
	
	/*
	 * returns list of business object attributes
	 */
	private ArrayList<QueryResult> getNodeAttributes(NodeType config, String id, boolean nodePreview) {
		
		// cache result to prevent redundant queries
		ArrayList<QueryResult> queryResultList = new ArrayList<QueryResult>();
		
		// if it is an object preview only get preview attributes otherwise all defined
		List<Attribute> nodeTypeAttributes =  new ArrayList<Attribute>();
		if(nodePreview){
			nodeTypeAttributes =  config.getPreviewConfigurationNodeAttributes();
		}else {
			nodeTypeAttributes =  config.getNodeTypeAttributes();
		}
		
		// get value for each object attribute
		for(Attribute nodeTypeAttribute : nodeTypeAttributes) {

			String datasource = nodeTypeAttribute.getDatasource();
			String query = nodeTypeAttribute.getQuery();
			System.out.println("Query: " + query);
			QueryResult queryResult = new QueryResult(datasource, query);
			
			// check if query result is already cached
			JsonObject jsonQueryResultObject = queryService.getQueryResult(queryResultList, queryResult);
			
			if(jsonQueryResultObject == null) {
				// get datasource configuration
				JsonObject jsonDatasourceObject = queryService.getJsonObject("datasources", datasource, "datasourceJSON");
				Gson gson = new Gson();
				Datasource datasourceObject = gson.fromJson(jsonDatasourceObject, Datasource.class);
				// log json
				//Utilities utilities = new Utilities();
				//utilities.printJson(jsonDatasourceObject, "json datasource object");
				// get query				
				JsonObject jsonQueryObject = queryService.getJsonObject("queries", query, "queryJSON");
				Query queryObject = gson.fromJson(jsonQueryObject, Query.class);
				// log json
				//utilities.printJson(jsonQueryObject, "json query object");
				jsonQueryResultObject = queryService.executeQuery(datasourceObject, queryObject, id);
				queryResult.setJsonObject(jsonQueryResultObject);
				queryResultList.add(queryResult);
				// log json
				//utilities.printJson(jsonQueryResultObject, "Parsed queryResult json");
			}
		}	
		
		return queryResultList;
	}
	
	private Node loadAttributes(Node businessObject, NodeType configuration, ArrayList<QueryResult> queryResultList, boolean nodePreview) {

		// TODO
		// if it is an object preview only process preview attributes otherwise all defined
		List<Attribute> configurationNodeAttributes =  new ArrayList<Attribute>();
		if(nodePreview){
			configurationNodeAttributes =  configuration.getPreviewConfigurationNodeAttributes();
		}else {
			configurationNodeAttributes =  configuration.getNodeTypeAttributes();
		}
		
		for(Attribute nodeTypeAttribute : configuration.getNodeTypeAttributes()) {
			// get name and fieldname of attribute
			String name = nodeTypeAttribute.getName();
			String fieldname = nodeTypeAttribute.getFieldname();
			// get query result for config object attribute
			String datasource = nodeTypeAttribute.getDatasource();
			String query = nodeTypeAttribute.getQuery();
			QueryResult queryResult = new QueryResult(datasource, query);
			JsonObject jsonQueryResultObject = queryService.getQueryResult(queryResultList, queryResult);
			// extract value from query result
			JsonElement jsonFirstQueryResultElement = jsonQueryResultObject.get(businessObject.getId());
			JsonObject jsonFirstQueryResultObject = jsonFirstQueryResultElement.getAsJsonObject();
			String value = jsonFirstQueryResultObject.get(fieldname).getAsString();
			int displayfield = nodeTypeAttribute.getDisplayfield();
			
			businessObject.addKeyValuePair(name, value, displayfield);
			//set business object title if attribute is configured as title
			String titleAttribute = configuration.getNodeTypeTitle();
			if(titleAttribute.equals(name)) {
				businessObject.setNodeTitle(value);
			}
		}
		
		return businessObject;
	}

	public List<Node> getAdjacentNodes(Node businessObject, String nodeTypeCategoryName) {
		
		// TODO: refactor method (too big!)
		
		System.out.println("Adjacent nodes");
		System.out.println("==============");
		// TODO: Only implemented for object person (main source / query)
		// TODO: Define queries person -> teaching, teaching -> person.... // only one def needed because bidirectional -> Class
		
		List<Node> adjacentNodesList = new ArrayList<Node>();
		// get peer query for object type
		// execute peer query and retrieve peer object ids
		String queryString = "";
		
		// determine peer query by source and target object type
		String sourceNodeType = businessObject.getNodeType();
				System.out.println("sourceNodeType: " + sourceNodeType);
		String sourceNodeId = businessObject.getId();
				System.out.println("sourceNodeId: " + sourceNodeId);
	
		// Get children of class objectPeers ( = target object names) e.g. person -> employee, student etc.
		ArrayList<String> adjacentNodeTypes = this.getChildrenNodeTypes(nodeTypeCategoryName);
		
		// get peer queries for source object <-> target objects
		// build query string for getting object relationships
		String adjacencyQueryString = "";
		for(int i=0; i<adjacentNodeTypes.size(); i++) {
			System.out.println("Result string adjacent node name: " + adjacentNodeTypes.get(i));
			adjacencyQueryString += "[adjacencySourceObject] = " + sourceNodeType + " AND [adjacencyTargetObject] = " + adjacentNodeTypes.get(i);
			if(i < adjacentNodeTypes.size() - 1) {
				adjacencyQueryString += " OR ";
			}
		}
		System.out.println("adjacencyQueryString: " + adjacencyQueryString);
		DocumentCollection resultCollectionAdjacenyQueryList = queryService.ftSearchView("", adjacencyQueryString, "nodeTypeAdjacencies");
		
		//execute query for getting object relationships
		ArrayList<AdjacencyQuery> adjacencyQueryList = new ArrayList<AdjacencyQuery>();
		try {
			if(resultCollectionAdjacenyQueryList != null) {
				Gson gson = new Gson();
				for(int i=1; i<=resultCollectionAdjacenyQueryList.getCount(); i++) {
					Document doc = resultCollectionAdjacenyQueryList.getNthDocument(i);
					String adjacencyName = doc.getItemValueString("adjacencyName");
					String adjacencyQueryJSON = queryService.getFieldValue("nodeTypeAdjacencies", adjacencyName, "adjacencyQueryJSON");					
					// retrieve query and database
					AdjacencyQuery adjacencyQuery = gson.fromJson(adjacencyQueryJSON, AdjacencyQuery.class);
					adjacencyQueryList.add(adjacencyQuery);
					// test print out
					System.out.println("adjacencyQuery: " + adjacencyQuery.getQuery());
				}
			}else {
				System.out.println("Result of query " + adjacencyQueryString + " is null.");
			}
		} catch (NotesException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// execute queries for getting peer object IDs
		ArrayList<String> adjacentNodeIDs = new ArrayList<String>();
		for(AdjacencyQuery adjacencyQuery : adjacencyQueryList) {
		
			// get datasource and query for peer query
			JsonObject jsonDatasourceObject = queryService.getJsonObject("datasources", adjacencyQuery.getDatasource(), "datasourceJSON");				
			JsonObject jsonQueryObject = queryService.getJsonObject("queries", adjacencyQuery.getQuery(), "queryJSON");
			//replace attributes in query string with variable values
			Gson gson = new Gson();
			Datasource datasourceObject = gson.fromJson(jsonDatasourceObject, Datasource.class);
			Query queryObject = gson.fromJson(jsonQueryObject, Query.class);
			String string = queryObject.getString();
			Utilities utilities = new Utilities();
			// create map with replacements
			ArrayList<String> replaceAttributesList = utilities.getTokens(string);
			Map<String, String> replaceAttributesMap = new HashMap<String, String>();
			for(String replaceAttributeKey : replaceAttributesList) {
				// get attribute value from business object
				String replaceAttributeValue = businessObject.getAttributeValue(replaceAttributeKey);
				replaceAttributesMap.put(replaceAttributeKey, replaceAttributeValue);
			}
			// replace [key] in string with variable values
			string = utilities.replaceTokens(string, replaceAttributesMap);
			System.out.println("QueryString after replacements: " + string);
			// replace query string
			queryObject.setString(string);
			
			String targetNodeName = adjacencyQuery.getTargetNode();
			String targetNodeIdKey = queryService.getFieldValue("nodeTypes", targetNodeName, "nodeTypeId");
			
				System.out.println("targetNodeIdKey: " + targetNodeIdKey);
				System.out.println("targetNodeIdKey: " + sourceNodeId);
			
			// TODO: 2. change paramter to query object, 3. in method extract keys as targetObjectIDs
			ArrayList<String> resultAdjacentNodeIDs = this.getAdjacentNodeIDs(datasourceObject, queryObject, sourceNodeId);
			// test
			for(String nodeId : resultAdjacentNodeIDs) {
				System.out.println("AdjacentNodeID: " + nodeId);
			}
			adjacentNodeIDs.addAll(resultAdjacentNodeIDs);
		}
		
		for(String adjacentNodeId : adjacentNodeIDs) {
			adjacentNodesList.add(this.getNode(adjacentNodeId, true));
		}
		return adjacentNodesList;
	}
	
	private ArrayList<String> getChildrenNodeTypes(String nodeTypeCategoryName) {
		ArrayList<String> adjacentNodeTypes = new ArrayList<String>();
		String queryStringNodeTypes = "FIELD nodeTypeCategory = " + nodeTypeCategoryName;
		DocumentCollection resultCollectionNodeTypes = queryService.ftSearchView("", queryStringNodeTypes, "nodeTypes");
		try {
			if(resultCollectionNodeTypes != null) {
				for(int i=1; i<=resultCollectionNodeTypes.getCount(); i++) {
					Document doc = resultCollectionNodeTypes.getNthDocument(i);
					String nodeType = doc.getItemValueString("nodeTypeName");
							System.out.println("childObjectName: " + nodeType);
					adjacentNodeTypes.add(nodeType);
				}
			}else {
				System.out.println("Result of query " + queryStringNodeTypes + " is null.");
			}
		} catch (NotesException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return adjacentNodeTypes;
	}
	
	private ArrayList<String> getAdjacentNodeIDs(Datasource datasourceObject, Query queryObject, String sourceNodeId) {
		
		DocumentCollection resultCollectionAdjacentNodesIDs = queryService.executeQueryFTSearch(datasourceObject, queryObject); 
		// get targetObjectIdKeys
		List<String> targetNodeIdKeys = queryObject.getKey();
		// extract object IDs from resultCollection
		ArrayList<String> adjacentNodeIds = new ArrayList<String>();
		if(resultCollectionAdjacentNodesIDs != null) {
			try {
				for(int i=1; i<=resultCollectionAdjacentNodesIDs.getCount(); i++) {
					Document doc = resultCollectionAdjacentNodesIDs.getNthDocument(i);
					System.out.println(doc.generateXML());
					for(String targetNodeIdKey : targetNodeIdKeys) {
						String nodeId = doc.getItemValueString(targetNodeIdKey);
						// add to peer object id list if it is not the object id itself
						if(!nodeId.equals(sourceNodeId)) {
							adjacentNodeIds.add(nodeId);
						}
					}
				}
			} catch (NotesException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			System.out.println("Result of query to get adjacentNodeIDs of is null.");
		}
		return adjacentNodeIds;
	}

	public List<Node> getFilteredResultList(Node businessObject, String peerNodeType, String nodeTypeCategoryName) {
		
		List<Node> filteredAdjacentNodeList = new ArrayList<Node>();
		if(peerNodeType != null && !peerNodeType.equals("all")) {
			for(Node adjacentNode : businessObject.getAdjacentNodeList()) {	
				if(adjacentNode.getNodeType().equals(peerNodeType)) {
					filteredAdjacentNodeList.add(adjacentNode);
				}
			}
		}else {
			filteredAdjacentNodeList = businessObject.getAdjacentNodeList();
		}
		return filteredAdjacentNodeList;
	}

	public List<String> getAdjacentNodeTypes(String nodeTypeCategoryName) {	
		// get class object -> relationship attribute
		NodeTypeCategory nodeTypeCategory = configService.getNodeTypeCategory(nodeTypeCategoryName);
		// TODO remove class property (not needed anymore)
		//String nodeAdjacency = nodeTypeCategory.getAdjacencies();
		// get all children node types
		ArrayList<String> adjacentNodeTypes = getChildrenNodeTypes(nodeTypeCategoryName);
		
		return adjacentNodeTypes;
	}
}
