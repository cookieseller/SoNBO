package uniko.iwvi.fgbas.magoetz.sbo;


import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.faces.context.FacesContext;

import org.apache.olingo.client.core.http.BasicAuthHttpClientFactory;

import uniko.iwvi.fgbas.magoetz.sbo.services.ConnectionsService;
import uniko.iwvi.fgbas.magoetz.sbo.util.Utilities;

/**
 * SoNBOSession is the session bean of the SoNBO app.
 * Any session relevant actions, like a chronic of navigation actions, has to be put here, rather than in the SoNBOManager.
 * 
 * @author Mathias
 *
 */
public class SoNBOSession {

    private String objectId;

    private ConnectionsService connectionsService;

    private List<Vector<String>> chronicList = new ArrayList<Vector<String>>();

    private BasicAuthHttpClientFactory clientFactory;
    
    public SoNBOSession() {
        this.connectionsService = new ConnectionsService("connectionsSSO");
        this.objectId = connectionsService.getUserEmail();
        
        Utilities.remotePrint("SoNBOSession");
        clientFactory = new BasicAuthHttpClientFactory("", "");
        //clientFactory = new BasicAuthHttpClientFactory("mriedle", "Ogilubime859");
    }

    public String getObjectId() {
        return objectId;
    }

    public void clearChronic() {
        this.chronicList.clear();
    }

    public void addChronicEntry(String nodeTitle, String nodeId) {
		Vector<String> newChronicEntry = new Vector<String>();
		newChronicEntry.add(nodeTitle);
		newChronicEntry.add(nodeId);
		this.chronicList.add(newChronicEntry);
    }

    public List<Vector<String>> getChronicEntries(int maxEntries) {
        List<Vector<String>> chronicEntries = new ArrayList<Vector<String>>();
        maxEntries = maxEntries == 0 ? chronicList.size() : maxEntries;
        if (chronicList.size() > 1) {
            for (int i = chronicList.size(); i > 1; i--) {
                chronicEntries.add(chronicList.get(i - 1));
                // max number to return
                if (i <= chronicList.size() - maxEntries) {
                    break;
                }
            }
        }
        return chronicEntries;
    }

    public void updateCredentials(String username, String password) {
    	Utilities.remotePrint("User: " + username + " Password: " + password);
    	String user = (String) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("user");
    	String passwd = (String) FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("password");
    	Utilities.remotePrint("User: " + user + " Password: " + passwd);
    	this.clientFactory = new BasicAuthHttpClientFactory(username, password);
    }
    
    public BasicAuthHttpClientFactory getClientFactory() {
    	return this.clientFactory;
    }
}
