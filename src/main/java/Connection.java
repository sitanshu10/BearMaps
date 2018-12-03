import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by davidvakshlyak on 8/1/16.
 */
public class Connection {
    String name;
    Long id;
    ArrayList<GraphNode> nodes;
    int size;

    public Connection( String name, Long id, ArrayList<GraphNode> nodes) {
        this.name = name;
        this.id = id;
        this.nodes = nodes;
        connectAll(this.nodes);
        size = this.nodes.size();
    }

    public Connection( Long id, ArrayList<GraphNode> nodes) {
        this.name = null;
        this.id = id;
        this.nodes = nodes;
        connectAll(this.nodes);
        size = this.nodes.size();
    }

    public ArrayList<GraphNode> nodes() {
        return nodes;
    }

    /** Connects all nodes**/
    private void connectAll(ArrayList<GraphNode> nodes){
        for(int i = 0; i < nodes.size() - 1; i++){
            GraphNode curr = nodes.get(i);
            GraphNode next = nodes.get(i+1);
            curr.addNeighbor(next);
            next.addNeighbor(curr);
        }
    }

}

