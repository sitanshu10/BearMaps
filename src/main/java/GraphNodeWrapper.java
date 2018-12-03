import java.util.ArrayList;

/**
 * Created by davidvakshlyak on 8/3/16.
 */
public class GraphNodeWrapper {
    private GraphNode gN;
    private Double distanceSoFar;



    public GraphNodeWrapper(GraphNode gN, Double distanceSoFar){
        this.gN = gN;
        this.distanceSoFar = distanceSoFar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphNodeWrapper that = (GraphNodeWrapper) o;

        return gN != null ? gN.equals(that.gN) : that.gN == null;

    }

    @Override
    public int hashCode() {
        return gN != null ? gN.hashCode() : 0;
    }

    public GraphNode gN() {
        return gN;
    }

    public Double distanceSoFar() {
        return distanceSoFar;
    }
}
