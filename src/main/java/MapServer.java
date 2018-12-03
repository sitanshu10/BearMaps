import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

/* Maven is used to pull in these dependencies. */
import com.google.gson.Gson;
import sun.awt.image.ImageWatched;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import static spark.Spark.*;

/**
 * This MapServer class is the entry point for running the JavaSpark web server for the BearMaps
 * application project, receiving API calls, handling the API call processing, and generating
 * requested images and routes.
 * @author Alan Yao
 */
public class MapServer {
    /**
     * The root upper left/lower right longitudes and latitudes represent the bounding box of
     * the root tile, as the images in the img/ folder are scraped.
     * Longitude == x-axis; latitude == y-axis.
     */
    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;
    /** Each tile is 256x256 pixels. */
    public static final int TILE_SIZE = 256;
    /** HTTP failed response. */
    private static final int HALT_RESPONSE = 403;
    /** Route stroke information: typically roads are not more than 5px wide. */
    public static final float ROUTE_STROKE_WIDTH_PX = 5.0f;
    /** Route stroke information: Cyan with half transparency. */
    public static final Color ROUTE_STROKE_COLOR = new Color(108, 181, 230, 200);
    /** The tile images are in the IMG_ROOT folder. */
    private static final String IMG_ROOT = "img/";
    private static HashMap<Integer, BufferedImage> imgSeen = new HashMap<>();


    /**
     * The OSM XML file path. Downloaded from <a href="http://download.bbbike.org/osm/">here</a>
     * using custom region selection.
     **/
    private static final String OSM_DB_PATH = "berkeley.osm";
    /**
     * Each raster request to the server will have the following parameters
     * as keys in the params map accessible by,
     * i.e., params.get("ullat") inside getMapRaster(). <br>
     * ullat -> upper left corner latitude,<br> ullon -> upper left corner longitude, <br>
     * lrlat -> lower right corner latitude,<br> lrlon -> lower right corner longitude <br>
     * w -> user viewport window width in pixels,<br> h -> user viewport height in pixels.
     **/
    private static final String[] REQUIRED_RASTER_REQUEST_PARAMS = {"ullat", "ullon", "lrlat",
            "lrlon", "w", "h"};
    /**
     * Each route request to the server will have the following parameters
     * as keys in the params map.<br>
     * start_lat -> start point latitude,<br> start_lon -> start point longitude,<br>
     * end_lat -> end point latitude, <br>end_lon -> end point longitude.
     **/
    private static final String[] REQUIRED_ROUTE_REQUEST_PARAMS = {"start_lat", "start_lon",
            "end_lat", "end_lon"};
    /* Define any static variables here. Do not define any instance variables of MapServer. */
    private static GraphDB g;

    /**
     * Place any initialization statements that will be run before the server main loop here.
     * Do not place it in the main function. Do not place initialization code anywhere else.
     * This is for testing purposes, and you may fail tests otherwise.
     **/
    public static void initialize() {
        g = new GraphDB(OSM_DB_PATH);
    }


    public static void main(String[] args) {

        initialize();
        staticFileLocation("/page");
        /* Allow for all origin requests (since this is not an authenticated server, we do not
         * care about CSRF).  */
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });


        /* Define the raster endpoint for HTTP GET requests. I use anonymous functions to define
         * the request handlers. */
        get("/raster", (req, res) -> {
            HashMap<String, Double> rasterParams =
                    getRequestParams(req, REQUIRED_RASTER_REQUEST_PARAMS);
            /* Required to have valid raster params */
            validateRequestParameters(rasterParams, REQUIRED_RASTER_REQUEST_PARAMS);
            /* Create the Map for return parameters. */
            Map<String, Object> rasteredImgParams = new HashMap<>();
            /* getMapRaster() does almost all the work for this API call */
            BufferedImage im = getMapRaster(rasterParams, rasteredImgParams);
            /* Check if we have routing parameters. */
            HashMap<String, Double> routeParams =
                    getRequestParams(req, REQUIRED_ROUTE_REQUEST_PARAMS);
            /* If we do, draw the route too. */
            if (hasRequestParameters(routeParams, REQUIRED_ROUTE_REQUEST_PARAMS)) {
                findAndDrawRoute(routeParams, rasteredImgParams, im);
            }
            /* On an image query success, add the image data to the response */
            if (rasteredImgParams.containsKey("query_success")
                    && (Boolean) rasteredImgParams.get("query_success")) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                writeJpgToStream(im, os);
                String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
                rasteredImgParams.put("b64_encoded_image_data", encodedImage);
                os.flush();
                os.close();
            }
            /* Encode response to Json */
            Gson gson = new Gson();
            return gson.toJson(rasteredImgParams);
        });

        /* Define the API endpoint for search */
        get("/search", (req, res) -> {
            Set<String> reqParams = req.queryParams();
            String term = req.queryParams("term");
            Gson gson = new Gson();
            /* Search for actual location data. */
            if (reqParams.contains("full")) {
                List<Map<String, Object>> data = getLocations(term);
                return gson.toJson(data);
            } else {
                /* Search for prefix matching strings. */
                List<String> matches = getLocationsByPrefix(term);
                return gson.toJson(matches);
            }
        });

        /* Define map application redirect */
        get("/", (request, response) -> {
            response.redirect("/map.html", 301);
            return true;
        });



    }

    /**
     * Check if the computed parameter map matches the required parameters on length.
     */
    private static boolean hasRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        return params.size() == requiredParams.length;
    }

    /**
     * Validate that the computed parameters matches the required parameters.
     * If the parameters do not match, halt.
     */
    private static void validateRequestParameters(
            HashMap<String, Double> params, String[] requiredParams) {
        if (params.size() != requiredParams.length) {
            halt(HALT_RESPONSE, "Request failed - parameters missing.");
        }
    }

    /**
     * Return a parameter map of the required request parameters.
     * Requires that all input parameters are doubles.
     * @param req HTTP Request
     * @param requiredParams TestParams to validate
     * @return A populated map of input parameter to it's numerical value.
     */
    private static HashMap<String, Double> getRequestParams(
            spark.Request req, String[] requiredParams) {
        Set<String> reqParams = req.queryParams();
        HashMap<String, Double> params = new HashMap<>();
        for (String param : requiredParams) {
            if (reqParams.contains(param)) {
                try {
                    params.put(param, Double.parseDouble(req.queryParams(param)));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    halt(HALT_RESPONSE, "Incorrect parameters - provide numbers.");
                }
            }
        }
        return params;
    }

    /**
     * Write a <code>BufferedImage</code> to an <code>OutputStream</code>. The image is written as
     * a lossy JPG, but with the highest quality possible.
     * @param im Image to be written.
     * @param os Stream to be written to.
     */
    static void writeJpgToStream(BufferedImage im, OutputStream os) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(1.0F); // Highest quality of jpg possible
        writer.setOutput(new MemoryCacheImageOutputStream(os));
        try {
            writer.write(im);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Handles raster API calls, queries for tiles and rasters the full image. <br>
     * <p>
     *     The rastered photo must have the following properties:
     *     <ul>
     *         <li>Has dimensions of at least w by h, where w and h are the user viewport width
     *         and height.</li>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *     Additional image about the raster is returned and is to be included in the Json response.
     * </p>
     * @param inputParams Map of the HTTP GET request's query parameters - the query bounding box
     *                    and the user viewport width and height.
     * @param rasteredImageParams A map of parameters for the Json response as specified:
     * "raster_ul_lon" -> Double, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Double, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Double, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Double, the bounding lower right latitude of the rastered image <br>
     * "raster_width"  -> Integer, the width of the rastered image <br>
     * "raster_height" -> Integer, the height of the rastered image <br>
     * "depth"         -> Integer, the 1-indexed quadtree depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image string. <br>
     * "query_success" -> Boolean, whether an image was successfully rastered. <br>
     * @return a <code>BufferedImage</code>, which is the rastered result.
     * @see #REQUIRED_RASTER_REQUEST_PARAMS  (ul/lr, w/h)
     */
    public static BufferedImage getMapRaster(Map<String, Double> inputParams,
                                             Map<String, Object> rasteredImageParams) {

        QTreeNode query = new QTreeNode(-1, -1, inputParams.get("ullon"), inputParams.get("ullat"),
                inputParams.get("lrlon"), inputParams.get("lrlat"));
        Double ldpGoal = (query.lonLR() - query.lonUL()) / inputParams.get("w");
        ArrayList<QTreeNode> goodResolution = goodRes(query, ldpGoal);
        ArrayList<Object> order = order(goodResolution);
        ArrayList<QTreeNode> orderedTiles = (ArrayList<QTreeNode>) order.remove(0);

        rasteredImageParams.put("depth", orderedTiles.get(0).depth());
        rasteredImageParams.put("raster_ul_lon",
                orderedTiles.get(0).lonUL());
        rasteredImageParams.put("raster_ul_lat",
                orderedTiles.get(0).latUL());
        rasteredImageParams.put("raster_lr_lon",
                orderedTiles.get(orderedTiles.size() - 1).lonLR());
        rasteredImageParams.put("raster_lr_lat",
                orderedTiles.get(orderedTiles.size() - 1).latLR());
        rasteredImageParams.put("raster_width",
                (Integer) order.remove(0) * 256);
        rasteredImageParams.put("raster_height",
                (Integer) order.remove(0) * 256);
        rasteredImageParams.put("query_success", true);

        BufferedImage img = new BufferedImage((Integer) rasteredImageParams.get("raster_width"),
                (Integer) rasteredImageParams.get("raster_height"),
                BufferedImage.TYPE_INT_RGB);
        int x = 0;
        int y = 0;
        for (QTreeNode n: orderedTiles) {
            if (imgSeen.containsKey(n.id())) {
                img.createGraphics().drawImage(imgSeen.get(n.id()), x, y, null);
            } else {
                n.generateBufferedImage();
                img.createGraphics().drawImage(n.bi(), x, y, null);
                imgSeen.put(n.id(), n.bi());
            }
            x += 256;
            if (x >= img.getWidth()) {
                x = 0;
                y += 256;
            }
        }
        return img;
    }

    /** Orders tiles in tilesToOrder so they are ready to be put in a BufferedImage.**/
    private static ArrayList<Object> order(ArrayList<QTreeNode> tilesToOrder) {
        ArrayList<QTreeNode> orderedTiles = new ArrayList<>();
        Double rastUllat = tilesToOrder.get(0).latUL();
        Double rastUllon = tilesToOrder.get(0).lonUL();
        int numTilesVert = 0;
        int numTilesHori = 0;
        for (QTreeNode n : tilesToOrder) {
            if (n.lonUL().equals(rastUllon)) {
                numTilesVert++;
            }
            if (n.latUL().equals(rastUllat)) {
                numTilesHori++;
            }
        }
        Double height = tilesToOrder.get(0).latUL() - tilesToOrder.get(0).latLR();
        Double currULlat = rastUllat;
        for (int i = 0; i < numTilesVert; i++) {
            for (Iterator<QTreeNode> iterator = tilesToOrder.iterator(); iterator.hasNext();) {
                QTreeNode n = iterator.next();
                if (nearlyEqual(n.latUL(), currULlat)) {
                    orderedTiles.add(n);
                    iterator.remove();
                }
            }
            currULlat -= height;
        }
        ArrayList<Object> rtn = new ArrayList<>();
        rtn.add(orderedTiles);
        rtn.add(numTilesHori);
        rtn.add(numTilesVert);

        return rtn;
    }
    private static boolean nearlyEqual(Double a, Double b) {
        return Math.abs(a - b) < 0.0000000001;
    }

    /** Traverses down QTree hierarchy,
     * returns arrayList of ALL tiles with good enough resolution,
     * that is all tiles with LDP <= queryLDP. **/
    public static ArrayList<QTreeNode> goodRes(QTreeNode query, Double ldpGoal) {
        QuadTree world =
                new QuadTree(
                        new QTreeNode(0, 0, ROOT_ULLON, ROOT_ULLAT, ROOT_LRLON, ROOT_LRLAT));
        ArrayList<QTreeNode> rtn = new ArrayList<>();
        world.getRoot().checkLDP(rtn, query, ldpGoal);
        return rtn;
    }



    /**
     * Searches for the shortest route satisfying the input request parameters, and returns a
     * <code>List</code> of the route's node ids. <br>
     * The route should start from the closest node to the start point and end at the closest node
     * to the endpoint. Distance is defined as the euclidean distance between two points
     * (lon1, lat1) and (lon2, lat2).
     * If <code>im</code> is not null, draw the route onto the image by drawing lines in between
     * adjacent points in the route. The lines should be drawn using ROUTE_STROKE_COLOR,
     * ROUTE_STROKE_WIDTH_PX, BasicStroke.CAP_ROUND and BasicStroke.JOIN_ROUND.
     * @param routeParams Params collected from the API call. Members are as
     *                    described in REQUIRED_ROUTE_REQUEST_PARAMS.
     * @param rasterImageParams parameters returned from the image rastering.
     * @param im The rastered map image to be drawn on.
     * @return A List of node ids from the start of the route to the end.
     */
    public static List<Long> findAndDrawRoute(Map<String, Double> routeParams,
                                              Map<String, Object> rasterImageParams,
                                              BufferedImage im) {


        GraphNode origin = g.findClosest(routeParams.get("start_lon"), routeParams.get("start_lat"));
        GraphNode destination = g.findClosest(routeParams.get("end_lon"), routeParams.get("end_lat"));

        LinkedList<GraphNode> route = star(origin, destination);







        if (im != null) {
            Graphics graphics = im.getGraphics();
            ((Graphics2D) graphics).setStroke(new BasicStroke(MapServer.ROUTE_STROKE_WIDTH_PX,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(ROUTE_STROKE_COLOR);
            for(int i = 0; i < route.size() - 1; i++){
                GraphNode first = route.get(i);
                GraphNode next = route.get(i+1);

                int x_pos_curr = getXpos(first, rasterImageParams);
                int y_pos_curr = getYpos(first, rasterImageParams);
                int x_pos_next = getXpos(next, rasterImageParams);
                int y_pos_next = getYpos(next, rasterImageParams);
                graphics.drawLine(x_pos_curr, y_pos_curr, x_pos_next, y_pos_next);
            }
            try{
                File f = new File("test.png");
                ImageIO.write(im, "PNG", f);

            } catch (IOException e){
                System.out.println("FUCK");

            }
        }



        List<Long> routeIDs = new ArrayList<>();

        for(GraphNode g: route){
            routeIDs.add(g.getId());
        }

        return routeIDs;








    }

    private static int getXpos(GraphNode n, Map<String, Object> rasterImageParams){
        int rast_width = (int) rasterImageParams.get("raster_width");
        Double lr_lon = (Double) rasterImageParams.get("raster_lr_lon");
        Double ul_lon = (Double) rasterImageParams.get("raster_ul_lon");
        Double pixelPerDegree = rast_width / (lr_lon - ul_lon);
        Double rtn = (n.lon() - ul_lon) * pixelPerDegree;
        return rtn.intValue();
    }


    private static int getYpos(GraphNode n, Map<String, Object> rasterImageParams){
        int rast_height = (int) rasterImageParams.get("raster_height");
        Double ul_lat = (Double) rasterImageParams.get("raster_ul_lat");
        Double lr_lat = (Double) rasterImageParams.get("raster_lr_lat");
        Double pixelPerDegree = rast_height / (ul_lat - lr_lat);
        Double rtn = n.lat() - ul_lat> 0? -1*(n.lat() - ul_lat) * pixelPerDegree : Math.abs(n.lat() - ul_lat) * pixelPerDegree;
        return rtn.intValue();

    }

    /** Uses A* algorithm to return ArrayList of GraphNodes that will connect origin to destination.
     * Start node in fringe h = 0
     * pop, calcc new dist (3 terms), update if less, ow keep going*/
    private static LinkedList<GraphNode> star(GraphNode origin, GraphNode destination) {
        PriorityQueue<GraphNode> fringe = new PriorityQueue<>(new Comparator<GraphNode>() {
            @Override
            public int compare(GraphNode o1, GraphNode o2) {
                Double f1 = o1.dist() + o1.euclidDistance(destination);
                Double f2 = o2.dist() + o2.euclidDistance(destination);

                if (f1 > f2){
                    return 1;
                } else if (f2 > f1){
                    return -1;
                } else {
                    return 0;
                }
            }
        });
        HashSet<GraphNode> corrupted = new HashSet<>();
        origin.setDist(0.0);
        corrupted.add(origin);
        fringe.add(origin);
        while (!fringe.isEmpty()) {
            GraphNode current = fringe.poll();
            for (GraphNode neighbor : current.neighbors()){
                if (neighbor.equals(destination)){
                    neighbor.setParent(current);
                    LinkedList<GraphNode> route = new LinkedList<>();

                    GraphNode curr = neighbor;

                    while(curr != null) {
                        route.addFirst(curr);
                        curr = curr.getParent();
                    }

                    for(GraphNode n: corrupted){
                        n.reset();
                    }
                    return route;
                } else {
                    Double cost = current.dist() + current.euclidDistance(neighbor);
                    if (neighbor.dist() > cost){
                        neighbor.setDist(cost);
                        neighbor.setParent(current);
                        fringe.add(neighbor);
                        corrupted.add(neighbor);
                    }


                }
            }

        }
        return null;

    }







    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public static List<String> getLocationsByPrefix(String prefix) {
        return new LinkedList<>();
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */
    public static List<Map<String, Object>> getLocations(String locationName) {
        return new LinkedList<>();
    }
}