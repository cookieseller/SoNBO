package uniko.iwvi.fgbas.magoetz.sbo.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import uniko.iwvi.fgbas.magoetz.sbo.SoNBOSession;
import uniko.iwvi.fgbas.magoetz.sbo.database.IQueryService;
import uniko.iwvi.fgbas.magoetz.sbo.database.NotesDB;
import uniko.iwvi.fgbas.magoetz.sbo.objects.ActivityEntry;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Datasource;
import uniko.iwvi.fgbas.magoetz.sbo.objects.Query;
import uniko.iwvi.fgbas.magoetz.sbo.util.Utilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.sbt.services.client.ClientServicesException;
import com.ibm.sbt.services.client.connections.profiles.Profile;
import com.ibm.sbt.services.client.connections.profiles.ProfileService;
import com.ibm.xml.crypto.util.Base64;
import com.ibm.xsp.model.domino.DominoUtils;

public class ActivityStreamUpdateService {

	private final NotesDB configQueryService = new NotesDB();

	/**
	 * Called by the agent to periodically check if a new post has to be made
	 *
	 * @throws MalformedURLException 
	 * @throws ClientServicesException 
	 */
	public void updateActivityStream() throws ClientServicesException, MalformedURLException {

		NotesDB notesDB = new NotesDB();
		FacesContext ctx = FacesContext.getCurrentInstance(); 
        SoNBOSession session = (SoNBOSession) ctx.getApplication().getVariableResolver().resolveVariable(ctx, "soNBOSession");
        session.updateCredentials("mriedle", "Ogilubime859");

		try {
			ViewEntryCollection activities    = getActivities();
			ViewEntryCollection postedEntries = getPostedActivtyEntries();
			List<ActivityEntry> unpostedEntries = new ArrayList<ActivityEntry>();
			try {
				unpostedEntries = getUnpostedActivityEntries(activities, postedEntries);	
			} catch (Exception e) {
				e.printStackTrace();
			}

			for (ActivityEntry post : unpostedEntries) {
				if (postActivityEntry(post)) {
					Map<String, String> values = new HashMap<String, String>();
					values.put("postedActivityObjectID", post.getId());
					values.put("postedActivityMessage", post.getMessage());
					values.put("postedActivityReceiver", post.getReceiver());
					values.put("postedActivityDate", post.getDate());
					values.put("postedActivityType", post.getType());

					notesDB.insertEntry(values, "configPostedActivityEntries");
				}
			}
		} catch (NotesException e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Return all entries in the activities view
	 *
	 * @return
	 * @throws NotesException
	 */
	private ViewEntryCollection getActivities() throws NotesException {
		Database db = DominoUtils.getCurrentDatabase();

		return db.getView("nodeTypesActivities").getAllEntries();
	}
	
	/**
	 * Return all entries that have already been posted to an activity stream of a user.
	 * The database does not contain all entries that have been posted, as old entries will be deleted.
	 *
	 * @return
	 * @throws NotesException
	 */
	private ViewEntryCollection getPostedActivtyEntries() throws NotesException {
		Database db = DominoUtils.getCurrentDatabase();

		return db.getView("postedActivityEntries").getAllEntries();
	}

	/**
	 * Return a list of all activity entries that have to be posted. This list is generated by taking all activities into account
	 * and subtracting all entries that have already been posted. The remainder is a list of all entries that have to be posted.
	 *
	 * @param activities
	 * @param postedActivityEntries
	 * @return
	 * @throws NotesException 
	 */
	private List<ActivityEntry> getUnpostedActivityEntries(ViewEntryCollection activities, ViewEntryCollection postedActivityEntries) throws NotesException {

		ArrayList<ActivityEntry> unpostedEntries = new ArrayList<ActivityEntry>();

		Document document = activities.getFirstEntry().getDocument();
		while (document != null) {
			String queryName   = document.getItemValueString("activityEntryQuery");

			Query query 		  = configQueryService.getQueryObject(queryName);
			Datasource datasource = configQueryService.getDatasourceObject(query.getType());
			query.setSkip(getSkiptoken());

			IQueryService service = QueryServiceFactory.getQueryServiceByDatasource(datasource.getType());
			Utilities.remotePrint(query.getString());
			JsonArray result 	  = service.executeQuery(datasource, query);
			for (JsonElement el : result) {
				if (!el.getAsJsonObject().has("Entry_No"))
					continue;

				String entryNo = el.getAsJsonObject().get("Entry_No").getAsString();
				if (viewEntryCollectionContainsValue(postedActivityEntries, "postedActivityObjectID", entryNo)) {
					continue;
				}
				
				try {
					unpostedEntries.add(getActivityEntry(document, el.getAsJsonObject()));	
				} catch (NullPointerException e) {
					e.printStackTrace();
				}
			}

			ViewEntry entry = activities.getNextEntry();
			if (entry == null)
				break;
			document = entry.getDocument();
		}
		return unpostedEntries;
	}

	/**
	 * Post the given entry and return whether the action was successful
	 *
	 * @param post
	 * @return
	 * @throws ClientServicesException 
	 * @throws MalformedURLException 
	 */
	private boolean postActivityEntry(ActivityEntry activityEntry) throws ClientServicesException, MalformedURLException {
		ProfileService profileService 			  = new ProfileService("connectionsSSO");
		GeneralConfigService generalConfigService = new GeneralConfigService();

		Profile profile 	 = profileService.getProfile("mriedle@uni-koblenz.de");
		String postText 	 = generalConfigService.getConfigEntryByName("MentionsPostData").get("configEntryValue").getAsString();
		String postGenerator = generalConfigService.getConfigEntryByName("PostGenerator").get("configEntryValue").getAsString();
		String postTarget	 = generalConfigService.getConfigEntryByName("MentionsTargetUrl").get("configEntryValue").getAsString();

		Map<String, String> replacementMap = new HashMap<String, String>();
		replacementMap.put("Post_Generator", postGenerator);
		replacementMap.put("Post_Title", activityEntry.getTitle());
		replacementMap.put("Post_Text", activityEntry.getMessage());
		replacementMap.put("Post_To_ID", profile.getUserid());
		replacementMap.put("Post_To_Name", profile.getName());

		String postContent = Utilities.replaceTokens(postText, replacementMap);
		URL url = new URL(postTarget);

		try {
			HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
			SSLContext ctx 				  = SSLContext.getInstance("SSL");

			// TODO implement certificate check
			ctx.init(new KeyManager[0], new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
		    connection.setHostnameVerifier(new HostnameVerifier() {
				public boolean verify(String arg0, SSLSession arg1) { return true; }
		    });
		    connection.setSSLSocketFactory(ctx.getSocketFactory());
		    connection.setRequestMethod("POST");
		    connection.setRequestProperty("Accept-Charset", "UTF-8");
		    connection.setRequestProperty("Authorization", "Basic " + Base64.encode("mriedle:ConNeXt".getBytes("UTF-8")));
		    connection.setRequestProperty("Content-Type", "application/json");
		    connection.setDoOutput(true);

	        OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), Charset.forName("UTF-8").newEncoder());
	    	out.write(postContent);	
	        out.close();

	        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	        String decodedString;
	        while ((decodedString = in.readLine()) != null) {
	            System.out.println(decodedString);
	        }
	        in.close();

	        return connection.getResponseCode() == 200;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Generate a activity entry from the given configuration document and the json data
	 *
	 * @param asJsonObject
	 * @param document
	 * @return
	 * @throws NotesException 
	 */
	private ActivityEntry getActivityEntry(final Document document, final JsonObject changelogEntry) throws NotesException {
		String userField 	  		 = document.getItemValueString("activityEntryUserField");
		String dateField	  		 = document.getItemValueString("activityEntryDateField");
		String displayTitle  		 = document.getItemValueString("activityEntryDisplayTitle");
		String displayText	  		 = document.getItemValueString("activityEntryDisplayText");
		boolean loadAssociatedObject = !document.getItemValueString("activityEntryLoadObject").isEmpty();

		// Maybe a bit inefficient, but we are using small objects and its an easy way to deep copy 
		JsonObject activityObject = new JsonParser().parse(changelogEntry.toString()).getAsJsonObject();
		String changelogEntryID   = changelogEntry.get("Entry_No").getAsString();
		if (loadAssociatedObject) {
			String associatedObjectQueryName = document.getItemValueString("activityEntryLoadObjectQuery");
			Query associatedObjectQuery 	 = configQueryService.getQueryObject(associatedObjectQueryName);
			JsonObject associatedObject 	 = loadAssociatedObject(changelogEntry, associatedObjectQuery);

			if (associatedObject == null) {
				Utilities.remotePrint(associatedObjectQuery.getView() + "/" + associatedObjectQuery.getCommand());
				throw new NullPointerException("Unable to load the associated object for entry: " + changelogEntryID);
			}

			for (String attr : associatedObject.keySet()) {
				if (!activityObject.has(attr))
					activityObject.add(attr, associatedObject.get(attr));
			}
		}

		ActivityEntry entry = new ActivityEntry();
		entry.setId(changelogEntryID);
	    entry.setTitle(displayTitle);
	    entry.setMessage(Utilities.replaceTokens(displayText, activityObject));
	    if (activityObject.has(dateField)) entry.setDate(activityObject.get(dateField).getAsString());
	    if (activityObject.has(userField)) entry.setReceiver(activityObject.get(userField).getAsString());

		return entry;
	}

	/**
	 * Check if the given ViewEntryCollection collects an entry with the given value
	 *
	 * @param collection
	 * @param key
	 * @param value
	 * @return
	 */
	private boolean viewEntryCollectionContainsValue(final ViewEntryCollection collection, final String key, final String value) {
		try {
			Document document = collection.getFirstEntry().getDocument();
			while (document != null) {
				String documentValue = document.getItemValueString(key);
				if (documentValue.equals(value)) {
					return true;
				}

				ViewEntry entry = collection.getNextEntry();
				if (entry == null)
					break;
				document = entry.getDocument();
			}
		} catch (NotesException e1) {
			e1.printStackTrace();
		} catch (NullPointerException e) {
		}

		return false;
	}

	/**
	 * Workaround because of the bug in Dynamics NAV OData, where filters would not work on > 1000 entries.
	 * We just skip all entries of which we know that they are not relevant, this is what the skiptoken is for.
	 * It works similar to an id, where 
	 * @return
	 */
	private String getSkiptoken() {
		GeneralConfigService generalConfigService = new GeneralConfigService();
		return generalConfigService.getConfigEntryByName("GeneralLogOffset").get("configEntryValue").getAsString();
	}

	/**
	 * Returns the Profile of the user who should receive the post
	 * 
	 * @param user
	 * @return
	 * @throws ClientServicesException
	 */
	private Profile getReceivingUser(String user) throws ClientServicesException {
		ProfileService profileService = new ProfileService("connectionsSSO");

		return profileService.getProfile("mriedle@uni-koblenz.de");
	}

	/**
	 * Load the object that is described in the changelog, this is to get all information about the object
	 * and potentially use its field in the output or to determine the user who should receive a activity post
	 *
	 * @return
	 */
	private JsonObject loadAssociatedObject(JsonObject changelogEntry, Query associatedObjectQuery) {
		Datasource datasource = configQueryService.getDatasourceObject(associatedObjectQuery.getType());
		IQueryService service = QueryServiceFactory.getQueryServiceByDatasource(associatedObjectQuery.getType());

		ArrayList<String> tokenList = Utilities.getTokenList(associatedObjectQuery.getString());
	    Map<String, String> replaceAttributesMap = new HashMap<String, String>();
        for (String replaceAttributeKey : tokenList) {
        	if (changelogEntry.has(replaceAttributeKey)) {
        		String replaceAttributeValue = changelogEntry.get(replaceAttributeKey).getAsString();
        		replaceAttributesMap.put(replaceAttributeKey, replaceAttributeValue);
        	} else {
        		replaceAttributesMap.put(replaceAttributeKey, "No value found");
        	}
        }
        String replacedQuery = Utilities.replaceTokens(associatedObjectQuery.getString(), replaceAttributesMap);
        associatedObjectQuery.setString(replacedQuery);

		//TODO load datasource by query type
		JsonArray associatedObject = service.executeQuery(datasource, associatedObjectQuery);
		Utilities.remotePrint(associatedObjectQuery.getView() + "/" + associatedObjectQuery.getString());
		if (associatedObject.size() != 1) {
			System.out.println("Failed loading the original object, " + associatedObject.size() + " objects found but expected 1.");
			Utilities.remotePrint("Failed loading the original object, " + associatedObject.size() + " objects found but expected 1.");
			Utilities.remotePrint(associatedObjectQuery.getView() + " " + replacedQuery + " should be " + changelogEntry.get("Table_Caption").getAsString());

			return null;
		}

		return associatedObject.get(0).getAsJsonObject();
	}

    private static class DefaultTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

        public X509Certificate[] getAcceptedIssuers() {
        	return new X509Certificate[0];
        }
    }
}
