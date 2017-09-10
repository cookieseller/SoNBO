package uniko.iwvi.fgbas.magoetz.sbo;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.faces.context.FacesContext;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Node;
import uniko.iwvi.fgbas.magoetz.sbo.objects.NodeTypeCategory;
import uniko.iwvi.fgbas.magoetz.sbo.services.ConfigService;
import uniko.iwvi.fgbas.magoetz.sbo.services.ObjectService;
import uniko.iwvi.fgbas.magoetz.sbo.util.Utilities;

/**
 * @author Flemming
 *
 */
public class SoNBOManager implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String objectId = (String) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("id");
	
	private String nodeTypeCategoryName = (String) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("nodeTypeCategory");
	
	private String nodeType = (String) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("nodeType");
	
	private ArrayList<String> notificationCodeList = new ArrayList<String>();
	
	public Node businessObject;
	
	public NodeTypeCategory nodeTypeCategory;
	
	private ObjectService objectService = new ObjectService();
	
	public void init(){
		
		if(objectId != null) {
			
			System.out.println("NEW REQUEST FOR BUSINESS OBJECT");
			System.out.println("===============================");
			// get business objects
			this.businessObject = objectService.getNode(objectId, false);
			// TODO reload peer object list if other objectPeer was chosen
			// get list of peer objects (all)
			ConfigService configService = new ConfigService();
			this.nodeTypeCategory = configService.getNodeTypeCategory(businessObject.getNodeTypeCategory());
			//List<Node> allPeerObjectList = new ArrayList<Node>();
			List<Node> adjacentNodeList = new ArrayList<Node>();
			
			HashSet<String> objectRelationships = new HashSet<String>();
			//List<Node> filteredPeerObjectList = new ArrayList<Node>();
			HashSet<Node> filteredPeerObjectList = new HashSet<Node>();
			
			for(String adjacentNodeTypeCategory : this.nodeTypeCategory.getAdjacentNodeTypeCategories())  {
				System.out.println("AdjacentNodeTypeCategory: " + adjacentNodeTypeCategory);
				List<Node> objects = objectService.getAdjacentNodes(businessObject, adjacentNodeTypeCategory);
				//allPeerObjectList.addAll(objects);
				if(this.nodeTypeCategoryName.equals(adjacentNodeTypeCategory) || this.nodeTypeCategoryName.equals("all")) {
					adjacentNodeList.addAll(objects);
				}
				this.businessObject.setAdjacentNodeList(adjacentNodeList);
				objectRelationships.addAll(objectService.getAdjacentNodeTypes(adjacentNodeTypeCategory));
				filteredPeerObjectList.addAll(objectService.getFilteredResultList(businessObject, nodeType, adjacentNodeTypeCategory));
			}
			List<String> relationshipList = new ArrayList<String>();
			for(String relationship : objectRelationships) {
				relationshipList.add(relationship);
			}
			List<Node> peerList = new ArrayList<Node>();
			for(Node filteredPeerObject : filteredPeerObjectList) {
				peerList.add(filteredPeerObject);
			}
			this.businessObject.setNodeAdjacencies(relationshipList);
			this.businessObject.setFilteredAdjacentNodeList(peerList);

			// TODO: execute tests if necessary
			Test test = new Test();
			//test.javaToJson();
			
		}else {
			this.notificationCodeList.add("E1");
		}
	}

	public ArrayList<String> getNotificationCodeList() {
		return notificationCodeList;
	}
}
