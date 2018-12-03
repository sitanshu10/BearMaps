/**
 * Created by davidvakshlyak on 7/16/16.
 */
public class QuadTree {
    QTreeNode root;

    public QuadTree(QTreeNode n) {
        root = n;
    }

    public QTreeNode getRoot() {
        return root;
    }

    public void print() {
        root.print();
    }


}
