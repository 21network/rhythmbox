package com.drips.rhythmbox.services;

import android.os.Environment;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class DataStreamBus {

    // DATA STREAM BUS ---  Non-Volatile Data System Manager

    // Identifier in logcat
    public static final String TAG = "DSBL - DATA BUS LOG";

    // region XML Definitions

    // XML Definition - Playlist
    private static final String ROOT_TAG_P = "playlist";
    private static final String CHILD_TAG_P = "track";
    private static final String ATTRIBUTE_ONE_P = "title";
    private static final String ATTRIBUTE_TWO_P = "artist";
    private static final String ATTRIBUTE_THREE_P = "loc";
    private static final String ATTRIBUTE_FOUR_P = "playcount";

    // XML Definition - Track Record
    private static final String ROOT_TAG_T = "recorder";
    private static final String CHILD_TAG_T = "record";
    private static final String ATTRIBUTE_ONE_T = "count";
    private static final String ATTRIBUTE_TWO_T = "stamp";
    private static final String ATTRIBUTE_THREE_T = "month";
    private static final String ATTRIBUTE_FOUR_T = "year";

    // endregion XML Definitions

    // graphing supports 10 years
    private final int MONTH_DISPLAY_LIMIT = 120;

    private String appDir;

    public DataStreamBus(String appDir){
        this.appDir = appDir;
    }

    // region XML IO

    // creates an empty XML with defined root @ROOT_TAG
    public void createXml(String fileName, boolean isPlaylist, boolean addedToPlaylist){
        try {

            String ROOT_TAG_LOCAL = constructRoot(isPlaylist);
            String outputLocation = constructLocation(isPlaylist, fileName);

            if (isPlaylist && !addedToPlaylist){
                addToPlaylistDisplay(fileName, outputLocation);
            }

            initRecorder();

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement(ROOT_TAG_LOCAL);
            doc.appendChild(rootElement);

            Transformer docTransformer = generateTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult((new FileOutputStream(outputLocation)));
            docTransformer.transform(source, result);

        } catch (TransformerConfigurationException e){
            Log.d(TAG, "createXml: TransformerConfigurationError");
        } catch (IOException e) {
            Log.d(TAG, "createXml: File IO exception " + e.getMessage() );
        } catch (TransformerException e) {
            Log.d(TAG, "createXml: Transformer Exception");
        }
    }
    // appends received arguments to existing XML
    public void writeXml(String fileName, boolean isRecord, String title, String artist, String location, String playCount){
        try {
            Log.d(TAG, "writeXml: WROTE XML!");
            initRecorder();
            String outputLocation = constructLocation(!isRecord, fileName);
            Log.d(TAG, "writeXml: *" + outputLocation.toString() + "*");
            String[] config = constructNonRoot(isRecord);
            String CHILD_TAG_LOCAL = config[0];
            String ATTRIBUTE_ONE_LOCAL = config[1];
            String ATTRIBUTE_TWO_LOCAL = config[2];
            String ATTRIBUTE_THREE_LOCAL = config[3];
            String ATTRIBUTE_FOUR_LOCAL = config[4];

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(outputLocation));
            Element rootElement = doc.getDocumentElement();

            Element track = doc.createElement(CHILD_TAG_LOCAL);
            track.setAttribute(ATTRIBUTE_ONE_LOCAL, stripper(title));
            track.setAttribute(ATTRIBUTE_TWO_LOCAL, stripper(artist));
            track.setAttribute(ATTRIBUTE_THREE_LOCAL, location);
            track.setAttribute(ATTRIBUTE_FOUR_LOCAL, playCount);
            rootElement.appendChild(track);

            Transformer docTransformer = generateTransformer();
            docTransformer.setOutputProperty(OutputKeys.INDENT, "yes");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(outputLocation).getAbsolutePath());
            docTransformer.transform(source, result);

        } catch (TransformerConfigurationException e){
            Log.d(TAG, "writeXml: TransformerConfigurationError");
        } catch (IOException e) {
            Log.d(TAG, "writeXml: File IO exception " + e.getMessage());
        } catch (TransformerException e) {
            Log.d(TAG, "writeXml: Transformer Exception");
        } catch (SAXException e) {
            Log.d(TAG, "writeXml: SAX Exception");
        }

    }
    // returns String matrix from XML
    public String[][] readXml(String fileName){
        try {

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(appDir + File.separator + "playlists" + File.separator + stripper(fileName)));

            NodeList tracks = doc.getElementsByTagName("track");
            int nodeSize = tracks.getLength();
            String[] titles = new String[nodeSize];
            String[] artists = new String[nodeSize];
            String[] locs = new String[nodeSize];
            String[] playcounts = new String[nodeSize];
            for (int i = 0; i < nodeSize; i++){
                Element track = (Element) tracks.item(i);
                titles[i] = track.getAttribute("title");
                artists[i] = track.getAttribute("artist");
                locs[i] = track.getAttribute("loc");
                playcounts[i] = track.getAttribute("playcount");
            }
            String[][] xmlContent = {titles, artists, locs, playcounts};
            return xmlContent;

        } catch (IOException e) {
            Log.d(TAG, "readXml: File IO exception " + e.getMessage());
            return null;
        } catch (SAXException e) {
            Log.d(TAG, "readXml: SAX Exception");
            return null;
        }
    }
    // reads playcount and stamp into string
    public String readRecord(String playlistName, String trackName){
        try {
            Log.d(TAG, "readRecord: " + appDir);
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(appDir + File.separator + "recorder" + File.separator + playlistName + File.separator + trackName));
            NodeList tracks = doc.getElementsByTagName("record");
            int nodeSize = tracks.getLength();
            String records = "";
            for (int i = 0; i < nodeSize; i++){
                Element track = (Element) tracks.item(i);
                records += String.format(Locale.getDefault(), "%03d", Integer.parseInt(track.getAttribute("count"))) + "\t";
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy @ HH:mm:ss");
                Date datea = new Date((long) (Integer.parseInt(track.getAttribute("stamp")))*1000);

                Log.d(TAG, "readRecord: " + datea.toString());
                records += sdf.format(datea)  + "\n";
            }
            return records;

        } catch (ParserConfigurationException e) {
            Log.d(TAG, "readRecord: ParserConfigurationError");
            return "Records failed.";
        } catch (IOException e) {
            Log.d(TAG, "readXml: File IO exception " + e.getMessage());
            return "No records.";
        } catch (SAXException e) {
            Log.d(TAG, "readXml: SAX Exception");
            return "readRecord failed.";
        }
    }
    // adds playlist to comprehensive playlist xml
    public void addToPlaylistDisplay(String playlistName, String location){

        //@@@FLAG improve efficiency
        // to tired to integrate with createXML() and writeXML();

        String outputLocation = appDir + File.separator + "playlists" + File.separator + "displayunit";
        String ROOT_TAG_DIS = "playlists";
        String CHILD_TAG_DIS = "playlist";
        String ATTRIBUTE_ONE_LOCAL = "name";
        String ATTRIBUTE_TWO_LOCAL = "location";

        if (!new File(appDir + File.separator + "playlists").isFile()){
            new File(appDir + File.separator + "playlists" ).mkdirs();
        }

        if (!new File(outputLocation).isFile()){

            try {

                initRecorder();

                DocumentBuilder docBuilder = generateDocument();
                Document doc = docBuilder.newDocument();
                Element rootElement = doc.createElement(ROOT_TAG_DIS);
                doc.appendChild(rootElement);

                Transformer docTransformer = generateTransformer();
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult((new FileOutputStream(outputLocation)));
                docTransformer.transform(source, result);

            } catch (TransformerConfigurationException e){
                Log.d(TAG, "addToPlaylistDisplayCreateXML: TransformerConfigurationError");
            } catch (IOException e) {
                Log.d(TAG, "addToPlaylistDisplayCreateXml: File IO exception " + e.getMessage() );
            } catch (TransformerException e) {
                Log.d(TAG, "addToPlaylistDisplayCreateXml: Transformer Exception");
            }
        }

        try {

            initRecorder();

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(outputLocation));
            Element rootElement = doc.getDocumentElement();

            Element track = doc.createElement(CHILD_TAG_DIS);
            track.setAttribute(ATTRIBUTE_ONE_LOCAL, stripper(playlistName));
            track.setAttribute(ATTRIBUTE_TWO_LOCAL, location);
            rootElement.appendChild(track);

            Transformer docTransformer = generateTransformer();
            docTransformer.setOutputProperty(OutputKeys.INDENT, "yes");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(outputLocation).getAbsolutePath());
            docTransformer.transform(source, result);

        } catch (TransformerConfigurationException e){
            Log.d(TAG, "writeXml: TransformerConfigurationError");
        } catch (IOException e) {
            Log.d(TAG, "writeXml: File IO exception " + e.getMessage());
        } catch (TransformerException e) {
            Log.d(TAG, "writeXml: Transformer Exception");
        } catch (SAXException e) {
            Log.d(TAG, "writeXml: SAX Exception");
        }

    }

    // endregion XML IO

    // region XML Byter Functions

    public String constructRoot(boolean isPlaylist){
        if (isPlaylist){
            return ROOT_TAG_P;
        } else {
            return ROOT_TAG_T;
        }
    }
    public String[] constructNonRoot(boolean isRecord){
        if (!isRecord){
            return new String[] {CHILD_TAG_P, ATTRIBUTE_ONE_P, ATTRIBUTE_TWO_P, ATTRIBUTE_THREE_P, ATTRIBUTE_FOUR_P};
        } else {
            return new String[] {CHILD_TAG_T, ATTRIBUTE_ONE_T, ATTRIBUTE_TWO_T, ATTRIBUTE_THREE_T, ATTRIBUTE_FOUR_T};
        }
    }
    public String constructLocation(boolean isPlaylist, String fileName){
        if (isPlaylist){
            return appDir + File.separator + "playlists" + File.separator + stripperNoSpace(fileName);
        } else {
            return fileName;
        }
    }
    public void initRecorder(){
        // creates recorder directory
        File recorderDir = new File(appDir + File.separator + "recorder");
        Log.d(TAG, "initRecorder: " + recorderDir.isDirectory());
        if (!recorderDir.exists()){
            recorderDir.mkdir();
        }
    }

    // endregion XML Byter Functions

    // region Helper Functions
    public String stripper(String unstrip){
        // removes special characters; negative lookup alphanumeric and whitespace [regex]
        Pattern pattern = Pattern.compile("[^a-zA-Z0-9\\s]+");
        String stripped = (pattern.matcher(unstrip).replaceAll("")).trim();
        return stripped;
    }
    public String stripperNoSpace(String unstrip){
        Pattern pattern = Pattern.compile("[^a-zA-Z0-9]+");
        String stripped = (pattern.matcher(unstrip).replaceAll("")).trim();
        return stripped;
    }
    public Transformer generateTransformer(){
        try {
            TransformerFactory docTransformerFactory = TransformerFactory.newInstance();
            Transformer docTransformer = null;
            docTransformer = docTransformerFactory.newTransformer();
            docTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            return docTransformer;
        } catch (TransformerConfigurationException e) {
            Log.d(TAG, "generateTransformer: TRANSFORMER CONFIGURATION ERROR");
            return null;
        }
    }
    public DocumentBuilder generateDocument(){
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            return docBuilder; }
        catch (ParserConfigurationException e){
            Log.d(TAG, "generateDocument: PARSER CONFIGURATION ERROR");
            return null;
        }
    }
    // endregion Helper Functions

    // region Count Managers

    public int getTrackPlayCount(String playlist, String trackName){
        try {

            XPath xpath = XPathFactory.newInstance().newXPath();

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(appDir + File.separator + "playlists" + File.separator + playlist));

            String evalutor = "/playlist/track[@title=\"" + trackName + "\"]/@playcount";
            int x =  Integer.parseInt(xpath.evaluate(evalutor, doc));

            Log.d(TAG, "getTrackPlayCount: x is " + x);
            return x;

        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        } catch (SAXException e) {
            e.printStackTrace();
            return 0;
        } catch (XPathExpressionException e) {
            e.printStackTrace();
            return 0;
        }
    }
    public void updatePlayCount(String playlist, String track){
        int oldCount = getTrackPlayCount(playlist, track);
        int newCount = oldCount + 1;
        String evalutor = "/playlist/track[@title=\"" + track + "\"]";
        String pathOutput = appDir + File.separator + "playlists" + File.separator + playlist;
        try {

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(pathOutput));

            XPath xpath = XPathFactory.newInstance().newXPath();
            Element node = (Element) ((NodeList) xpath.evaluate(evalutor, doc, XPathConstants.NODESET)).item(0);
            node.setAttribute("playcount", newCount + "");

            Transformer docTransformer = generateTransformer();
            docTransformer.setOutputProperty(OutputKeys.INDENT, "yes");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(pathOutput));
            docTransformer.transform(source, result);

        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }


    }

    // endregion Count Managers

    // region Playlist Extended Manager
    public String[] getPlaylists(){

        String outputLocation = appDir + File.separator + "playlists" + File.separator + "displayunit";
        String ROOT_TAG_DIS = "playlists";
        String CHILD_TAG_DIS = "playlist";
        String ATTRIBUTE_ONE_LOCAL = "name";
        String ATTRIBUTE_TWO_LOCAL = "location";

        try {

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(outputLocation));

            NodeList tracks = doc.getElementsByTagName(CHILD_TAG_DIS);
            int nodeSize = tracks.getLength();
            String[] names = new String[nodeSize];
            String[] locs = new String[nodeSize];
            for (int i = 0; i < nodeSize; i++){
                Element track = (Element) tracks.item(i);
                names[i] = stripper(track.getAttribute(ATTRIBUTE_ONE_LOCAL));
                //locs[i] = track.getAttribute(ATTRIBUTE_TWO_LOCAL);
            }
            // String[][] xmlContent = {names, locs};
            return names;

        } catch (IOException e) {
            Log.d(TAG, "readXml: File IO exception " + e.getMessage());
            return null;
        } catch (SAXException e) {
            Log.d(TAG, "readXml: SAX Exception");
            return null;
        }
    }
    public void updatePlaylist(String playlistName, String[][] newPlaylist){
        createXml(playlistName, true, true);
        String[] titles = newPlaylist[0];
        String[] artists = newPlaylist[1];
        String[] locs = newPlaylist[2];
        String[] playCounts = newPlaylist[3];
        String[] previousTitles = newPlaylist[4];
        String[] previousArtists = newPlaylist[5];

        for (int i = 0; i < newPlaylist[0].length; i++){
            Log.d(TAG, "updatePlaylist: UPDATING " + i);
            int code = updateRecord(playlistName, previousTitles[i], titles[i], previousArtists[i], artists[i]);
            if (code == 0){
                writeXml(playlistName, false, titles[i], artists[i], locs[i], playCounts[i]);
                Log.d(TAG, "updatePlaylist: RENAME APPLIED");
            } else {
                writeXml(playlistName, false, previousTitles[i], previousArtists[i], locs[i], playCounts[i]);
            }
        }
    }
    public int updateRecord(String playlistName, String previousTitle, String newTitle, String previousArtists, String newArtist){
        String oldTrackName = stripperNoSpace(previousTitle + "x" + previousArtists);
        String newTrackName = stripperNoSpace(newTitle + "x" + newArtist);
        String preamble = appDir + File.separator + "recorder" + File.separator + playlistName + File.separator;
        Log.d(TAG, "updateRecord: UPDATE FROM " + oldTrackName + " to " + newTrackName);
        File oldF = new File(preamble + stripperNoSpace(oldTrackName));
        File newF = new File(preamble + stripperNoSpace(newTrackName));
        if (newF.exists()){
            Log.d(TAG, "updateRecord: ALREADY EXISTS");
        } else {
            if (new File(appDir + File.separator + "recorder" + File.separator + playlistName).exists()){
                if (oldF.exists()){
                    oldF.renameTo(newF);
                    Log.d(TAG, "updateRecord: RENAMED");
                    return 0;
                } else {
                    Log.d(TAG, "updateRecord: Record does not exist! Pathed at *" + oldF.getPath() + "*");
                    return 0;
                }
            } else {
                Log.d(TAG, "updateRecord: Directory does not exist");
                return 0;
            }
        }
        return 1;
    }
    public int[][] getMonthMatrix(String playlistName, String trackName){
        try {

            String x_year_eval = "/recorder/record/@year[not(. = ../preceding-sibling:: record/@year)]";
            String x_month_eval = "/recorder/record/@month[not(. = ../preceding-sibling:: record/@month)]";

            XPath xpath = XPathFactory.newInstance().newXPath();
            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(appDir + File.separator + "recorder" + File.separator + playlistName + File.separator + trackName));
            NodeList hits = (NodeList) (xpath.compile(x_year_eval).evaluate(doc, XPathConstants.NODESET));

            int[] xArr = new int[MONTH_DISPLAY_LIMIT];
            int[] yArr = new int[MONTH_DISPLAY_LIMIT];
            int arrCountHold = 0;

            for (int i = 0; i < hits.getLength(); i++){

                // XML with nodes containing specified year attribute
                Node currentNode = hits.item(i);
                String year = currentNode.getNodeValue();
                String all_year_eval = "/recorder/record [@year=\"" + year + "\"]";
                NodeList hitAgain = (NodeList) (xpath.compile(all_year_eval).evaluate(doc, XPathConstants.NODESET));
                DocumentBuilder tempDocBuilder = generateDocument();
                Document tempDoc = tempDocBuilder.newDocument();
                Element root = tempDoc.createElement(ROOT_TAG_T);
                tempDoc.appendChild(root);
                for (int k = 0; k < hitAgain.getLength(); k++){
                    Node n = (Node) hitAgain.item(k);
                    Node newNodeK = tempDoc.importNode(n, true);
                    tempDoc.adoptNode(newNodeK);
                    root.appendChild(newNodeK);
                }

                NodeList hitsAgainMonth = (NodeList) (xpath.compile(x_month_eval).evaluate(tempDoc, XPathConstants.NODESET));
                for (int m = 0; m < hitsAgainMonth.getLength(); m++){
                    String month = hitsAgainMonth.item(m).getNodeValue();

                    String y_axis_eval = "count(/recorder/record[@month='" + month + "']/@month)";
                    int count = Integer.parseInt(xpath.compile(y_axis_eval).evaluate(tempDoc));
                    String newYear = (Integer.parseInt(year)) + "";
                    xArr[arrCountHold] = Integer.parseInt(newYear + "" + month);
                    yArr[arrCountHold] = count;
                    Log.d(TAG, "getMonthMatrix: COORD_x" + xArr[arrCountHold] + "_y_" + yArr[arrCountHold]);
                    arrCountHold++;
                }
            }

            return new int[][] {Arrays.copyOfRange(xArr, 0, arrCountHold), Arrays.copyOfRange(yArr, 0, arrCountHold)};

        } catch (XPathExpressionException e){

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return null;
    }
    public void updateParentPlaylist(String[][] up){
        String outputLocation = appDir + File.separator + "playlists" + File.separator + "displayunit";
        String ROOT_TAG_DIS = "playlists";
        String CHILD_TAG_DIS = "playlist";
        String ATTRIBUTE_ONE_LOCAL = "name";
        String ATTRIBUTE_TWO_LOCAL = "location";
        boolean direSucces = false;
        boolean fireSuccess = false;
        //@@@FLAG improve efficiency
        try {

            initRecorder();

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement(ROOT_TAG_DIS);
            doc.appendChild(rootElement);

            Transformer docTransformer = generateTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult((new FileOutputStream(outputLocation)));
            docTransformer.transform(source, result);

        } catch (TransformerConfigurationException e){
            Log.d(TAG, "addToPlaylistDisplayCreateXML: TransformerConfigurationError");
        } catch (IOException e) {
            Log.d(TAG, "addToPlaylistDisplayCreateXml: File IO exception " + e.getMessage() );
        } catch (TransformerException e) {
            Log.d(TAG, "addToPlaylistDisplayCreateXml: Transformer Exception");
        }

        String[] newPlaylists = up[0];
        String[] previousPlaylists = up[1];
        Log.d(TAG, "updatePlaylists: new palyslits " + newPlaylists.length);
        for (int i = 0; i < newPlaylists.length; i++){
            String newPlaylistName = newPlaylists[i];
            String oldPlaylistName = previousPlaylists[i];

            String rPreamble = appDir + File.separator + "recorder" + File.separator;
            String pPreamble = appDir + File.separator + "playlists" + File.separator;

            File oldDir = new File(rPreamble + oldPlaylistName);
            File newDir = new File(rPreamble + newPlaylistName);
            File oldFil = new File(pPreamble + oldPlaylistName);
            File newFil = new File(pPreamble + newPlaylistName);
            Log.d(TAG, "updatePlaylists: FROM " + oldDir.getAbsolutePath() + " to " + newDir.getAbsolutePath());
            if (newPlaylistName != oldPlaylistName){

                if (oldDir.isDirectory()){
                    direSucces = oldDir.renameTo(newDir);
                    Log.d(TAG, "updatePlaylists: Renamed directory");
                }

                if(oldFil.isFile()){
                    fireSuccess = oldFil.renameTo(newFil);
                    Log.d(TAG, "updatePlaylists: Renamed file");
                }
                Log.d(TAG, "updatePlaylists: " + direSucces + " " + fireSuccess);
                if (fireSuccess){
                    addToPlaylistDisplay(newPlaylistName, pPreamble + newPlaylistName);
                }
            } else {
                addToPlaylistDisplay(newPlaylistName, pPreamble + newPlaylistName);
            }
        }
    }
    public void deleteParentPlaylists(ArrayList<String> dp){
        //@@@FLAG improve efficiency
        for (int i = 1; i <= dp.size(); i++){
            Log.d(TAG, "deletePlaylists: " + dp);
            try {
                File deletedPlaylistDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + File.separator + "rhythmdrip" + File.separator + "trash" + File.separator + dp.get(i-1));
                File deadPlaylistF = new File(appDir + File.separator + "playlists" + File.separator + dp.get(i-1));
                File deadPlaylistD = new File(appDir + File.separator + "records" + File.separator + dp.get(i-1));

                if (!deletedPlaylistDir.isDirectory()){
                    deletedPlaylistDir.mkdir();
                }

                if (deadPlaylistD.isDirectory()) {
                    FileUtils.moveDirectoryToDirectory(deadPlaylistD, deletedPlaylistDir, true);
                    Log.d(TAG, "updatePlaylists: Trashed directory");
                } else {
                    Log.d(TAG, "deletePlaylists: Trashed failed; directory doesn't exist");
                }

                if (deadPlaylistF.isFile()) {
                    FileUtils.moveFileToDirectory(deadPlaylistF, deletedPlaylistDir, true);
                    Log.d(TAG, "updatePlaylists: Trashed file");
                } else {
                    Log.d(TAG, "deletePlaylists: Trashed failed; file doesn't exist");
                }
            } catch (IOException e) {
                Log.d(TAG, "deletePlaylist: IO Excpetion " + e.getMessage());
            }
        }
    }
    // endregion Playlist Extended Manager

    // region Unused Functions
    // @@@FLAG future implementations
    public int[][] getYearMatrix(String playlistName, String trackName){
        try {

            // String x_axis_eval = "distinct-values(/recorder/record/@year)";  // xpath v2
            String x_year_eval = "/recorder/record/@year[not(. = ../preceding-sibling:: record/@year)]";

            XPath xpath = XPathFactory.newInstance().newXPath();

            DocumentBuilder docBuilder = generateDocument();
            Document doc = docBuilder.parse(new File(appDir + File.separator + "recorder" + File.separator + playlistName + File.separator + trackName));
            NodeList hits = (NodeList) (xpath.compile(x_year_eval).evaluate(doc, XPathConstants.NODESET));
            int nodeSize = hits.getLength();

            int[] x_year_axis = new int[nodeSize];
            int[] y_year_axis = new int[nodeSize];

            for (int i = 0; i < nodeSize; i++){
                Node currentNode = hits.item(i);
                String y_axis_eval = "count(/recorder/record[@year='" + currentNode.getNodeValue() + "']/@year)";
                int count = Integer.parseInt(xpath.compile(y_axis_eval).evaluate(doc));
                x_year_axis[i] = Integer.parseInt(currentNode.getNodeValue());
                y_year_axis[i] = count;
            }

            return new int[][] {x_year_axis, y_year_axis};

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (XPathExpressionException e) {
            Log.d(TAG, "getMatrix: XPATH EXPRESSION EXCEPTION" + e.getMessage());
        }

        return new int[0][];
    }
    public static void printDocument(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            transformer.transform(new DOMSource(doc),
                    new StreamResult(new OutputStreamWriter(System.out, "UTF-8")));
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }
    // endregion Unused Functions

}
