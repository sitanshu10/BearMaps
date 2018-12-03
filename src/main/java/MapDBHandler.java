import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.LocatorImpl;

import java.awt.geom.Point2D;
import java.util.*;

/**
 *  Parses OSM XML files using an XML SAX parser. Used to construct the graph of roads for
 *  pathfinding, under some constraints.
 *  See OSM documentation on
 *  <a href="http://wiki.openstreetmap.org/wiki/Key:highway">the highway tag</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Way">the way XML element</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Node">the node XML element</a>,
 *  and the java
 *  <a href="https://docs.oracle.com/javase/tutorial/jaxp/sax/parsing.html">SAX parser tutorial</a>.
 *  @author Alan Yao
 */
public class MapDBHandler extends DefaultHandler {
    /**
     * Only allow for non-service roads; this prevents going on pedestrian streets as much as
     * possible. Note that in Berkeley, many of the campus roads are tagged as motor vehicle
     * roads, but in practice we walk all over them with such impunity that we forget cars can
     * actually drive on them.
     */
    private static final Set<String> ALLOWED_HIGHWAY_TYPES = new HashSet<>(Arrays.asList
            ("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
                    "residential", "living_street", "motorway_link", "trunk_link", "primary_link",
                    "secondary_link", "tertiary_link"));
    private final GraphDB g;
    private String activeState = "";

    //Used at startElement/
    private ArrayList<GraphNode> referredNodes;
    private String wayName;
    private Long wayId;
    private GraphNode lastNode;



    //Used at endElement.
    private ArrayList<Connection> berkeleyConnections;
    private HashMap<Long, GraphNode> berkeleyNodes;

    public MapDBHandler(GraphDB g) {
        this.g = g;
        wayName = null;
        lastNode = null;
        referredNodes = new ArrayList<>();
        berkeleyConnections = new ArrayList<>();
        berkeleyNodes = new HashMap<>();

    }

    /**
     * Called at the beginning of an element. Typically, you will want to handle each element in
     * here, and you may want to track the parent element.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available. This tells us which element we're looking at.
     * @param attributes The attributes attached to the element. If there are no attributes, it
     *                   shall be an empty Attributes object.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see Attributes
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {

        //Node construction begins.
        if (qName.equals("node")) {
            activeState = "node";
            GraphNode gN = new GraphNode(Long.parseLong(attributes.getValue("id")), Double.parseDouble(attributes.getValue("lon")),
                    Double.parseDouble(attributes.getValue("lat")));
            berkeleyNodes.put(Long.parseLong(attributes.getValue("id")), gN);
            lastNode = gN;
        }

        //Way Construction begins.
        else if (qName.equals("way")) {
            activeState = "way";
            wayId = Long.parseLong(attributes.getValue("id"));
        }

        //Node has name.
        else if (activeState.equals("node") && qName.equals("tag")
                && attributes.getValue("k").equals("name")) {
            lastNode.setName(cleanString(attributes.getValue("v")));
        }

        //Way has name.
        else if (activeState.equals("way") && qName.equals("tag")
                && attributes.getValue("k").equals("name")) {
            wayName = attributes.getValue("v");
        }

        // Fetch Nodes to be connected in way.
        else if (qName.equals("nd")) {
            referredNodes.add(berkeleyNodes.get(Long.parseLong(attributes.getValue("ref"))));
        }

        // See if way is allowed.
        else if ( qName.equals("tag")
                && attributes.getValue("k").equals("highway")) {
            if (ALLOWED_HIGHWAY_TYPES.contains(attributes.getValue("v")) && referredNodes.size() > 1) {
                Connection c = (wayName == null) ? new Connection(wayId, referredNodes)
                        : new Connection(wayName, wayId, referredNodes);
                berkeleyConnections.add(c);
            }
        }


    }



    /**
     * Receive notification of the end of an element. You may want to take specific terminating
     * actions here, like finalizing vertices or edges found.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available.
     * @throws SAXException  Any SAX exception, possibly wrapping another exception.
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if(qName.equals("way")){
            wayName = null;
            wayId = null;
            referredNodes.clear();

        }


    }

    /**
     *  Remove nodes with no connections from the graph.
     *  While this does not guarantee that any two nodes in the remaining graph are connected,
     *  we can reasonably assume this since typically roads are connected.
     */
    public void clean(){
        Iterator nodeIter = berkeleyNodes.entrySet().iterator();
        while (nodeIter.hasNext()) {
            Map.Entry pair = (Map.Entry) nodeIter.next();
            GraphNode gN = (GraphNode) pair.getValue();

            if (!gN.isConnected()) {
                nodeIter.remove();
            }
        }
    }

    public GraphNode findClosest(Double lon, Double lat){
        Iterator iter = berkeleyNodes.entrySet().iterator();
        Map.Entry pair = (Map.Entry) iter.next();
        GraphNode best = (GraphNode) pair.getValue();
        while (iter.hasNext()){
            Map.Entry somePair = (Map.Entry) iter.next();
            GraphNode someNode = (GraphNode) somePair.getValue();
            if(best.getId().equals(661844282) || someNode.getId().equals(661844282)){
                System.out.println("Found");
            }
            Double bestDist = best.euclidDistance(lon, lat);
            Double someDist = someNode.euclidDistance(lon, lat);
            best = Math.min(bestDist, someDist) == someDist ? someNode : best;
        }
        return best;
    }





    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }
}
