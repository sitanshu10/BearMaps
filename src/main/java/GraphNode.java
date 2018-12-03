import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * GraphNode is the node representation in the Graph of Berkeley.
 * Every GraphNode has an id, and a pair of Coordinates (lon, lat).
 * GraphNodes can also be named (point features),
 * or have parents/successors (nodes in ways).
 *
 * Created by davidvakshlyak on 8/1/16.
 */
public class GraphNode {
    Double lat, lon;
    Long id;
    String name;
    GraphNode parent;
    ArrayList<GraphNode> neighbors;
    Double dist;





    public GraphNode(Long id, Double lon, Double lat) {
        this.id = id;
        this.lon = lon;
        this.lat = lat;
        this.name = null;
        this.neighbors = new ArrayList<>();
        this.parent = null;
        this.dist = Double.MAX_VALUE;
    }

    public void reset(){
        this.dist = Double.MAX_VALUE;
        this.parent = null;
    }

    public Double dist(){
        return this.dist;
    }

    public void setDist(Double dist){
        this.dist = dist;
    }

    public void setParent(GraphNode parent) {
        this.parent = parent;
    }

    public GraphNode getParent() {
        return parent;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<GraphNode> neighbors() {
        return neighbors;
    }

    public void addNeighbor(GraphNode n) {
        if (!neighbors.contains(n)) neighbors.add(n);
    }

    public Long getId() {
        return id;
    }

    public boolean isConnected() {
        return neighbors.size() > 0;
    }


    public Double lon() {
        return lon;
    }

    public Double lat() {
        return lat;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphNode graphNode = (GraphNode) o;

        if (!lat.equals(graphNode.lat)) return false;
        if (!lon.equals(graphNode.lon)) return false;
        return id.equals(graphNode.id);

    }

    @Override
    public int hashCode() {
        int result = lat.hashCode();
        result = 31 * result + lon.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }

    //Manhattan Distance between this Node and a a Node/pair of coords.
    public Double manhattanDistance(Double lon, Double lat){
        return Math.abs(this.lon-lon) + Math.abs(this.lat-lat);
    }
    public Double manhattanDistance(GraphNode n){
        if (this.equals(n)) return 0.0;
        return manhattanDistance(n.lon(), n.lat);
    }
    public Double euclidDistance(Double lon, Double lat) {
        return Math.sqrt(Math.pow(lon - this.lon, 2) + Math.pow(lat - this.lat, 2));

    }

    public Double euclidDistance(GraphNode n) {
        if (this.equals(n)) return 0.0;
        return euclidDistance(n.lon(), n.lat);
    }

}
