import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by davidvakshlyak on 7/16/16.
 */
public class QTreeNode {

    private int id, depth;
    private BufferedImage bi;
    /** Upper Left, Lower Right
     * cartesian points of a QTreeNode **/
    private Point2D.Double ul, lr;
    private QTreeNode[] children;

    public QTreeNode(int id, int depth, Double ullon, Double ullat, Double lrlon, Double lrlat) {
        this.id = id;
        this.depth = depth;
        ul = new Point2D.Double(ullon, ullat);
        lr = new Point2D.Double(lrlon, lrlat);
        children = new QTreeNode[4];
    }

    public QTreeNode(Double ullon, Double ullat, Double lrlon, Double lrlat) {
        ul = new Point2D.Double(ullon, ullat);
        lr = new Point2D.Double(lrlon, lrlat);
        children = new QTreeNode[4];
    }

    public BufferedImage bi() {
        return bi;
    }

    public int id() {
        return id;
    }

    public int depth() {
        return depth;
    }

    public QTreeNode[] children() {
        return children;
    }

    public Double lonUL() {
        return ul.getX();
    }

    public Double latUL() {
        return ul.getY();
    }

    public Double lonLR() {
        return lr.getX();
    }

    public Double latLR() {
        return lr.getY();
    }

    /** Generates and sets children for a particular QTreeNode. **/
    public void generateChildren() {

        this.children[0] = new QTreeNode((id * 10) + 1, depth + 1, ul.getX(), ul.getY(),
                ul.getX() + ((lr.getX() - ul.getX()) / 2),
                lr.getY() + (ul.getY() - lr.getY()) / 2);
        this.children[1] = new QTreeNode((id * 10) + 2, depth + 1,
                ul.getX() + ((lr.getX() - ul.getX()) / 2), ul.getY(),
                lr.getX(), lr.getY() + (ul.getY() - lr.getY()) / 2);
        this.children[2] = new QTreeNode((id * 10) + 3, depth + 1, ul.getX(),
                lr.getY() + (ul.getY() - lr.getY()) / 2,
                ul.getX() + ((lr.getX() - ul.getX()) / 2), lr.getY());
        this.children[3] = new QTreeNode((id * 10) + 4, depth + 1,
                ul.getX() + ((lr.getX() - ul.getX()) / 2),
                lr.getY() + (ul.getY() - lr.getY()) / 2,
                lr.getX(), lr.getY());

    }

    public void generateBufferedImage() {
        try {
            bi = ImageIO.read(new File("img/" + id + ".png"));
        } catch (IOException e) {
            bi = null;
        }
    }


    /** Fills arrList with goodRes tiles that are closest to the root of the QuadTree
     * and intersect query.**/
    public void checkLDP(ArrayList<QTreeNode> arrTOfill, QTreeNode query, Double ldpGoal) {
        Double ldpView = (lonLR() - lonUL()) / 256;
        if (this.intersects(query) && (ldpView <= ldpGoal || depth == 7)) {
            arrTOfill.add(this);
        } else if (depth < 7) {
            generateChildren();
            for (QTreeNode child : children) {
                child.checkLDP(arrTOfill, query, ldpGoal);
            }
        }
    }

    /** Prints ID/Depth of QTN n and does same for ALL of its children. (Preorder Traversal) **/
    public void print() {
        String tab = indent(depth);
        System.out.println(tab + "ID: "
                + Integer.toString(id)
                + " Depth: "
                + Integer.toString(depth));
        if (children != null) {
            for (QTreeNode kid : children) {
                kid.print();
            }
        }
    }
    private static String indent(int i) {
        String tab = "";
        for (int j = 0; j < i; j++) {
            tab = tab + "    ";
        }
        return tab;

    }

    /** Checks if this QTreeNode intersects q.
     * That is either ul or lr of this is within q. **/
    public boolean intersects(QTreeNode q) {

        Point2D.Double ll = new Point2D.Double(lonUL(), latLR());
        Point2D.Double ur = new Point2D.Double(lonLR(), latUL());
        return  q.contains(ul) || q.contains(lr) || q.contains(ll) || q.contains(ur);

    }
    private boolean contains(Point2D.Double p) {
        return (lonUL() <= p.getX() && p.getX() <= lonLR())
                && (latLR() <= p.getY() && p.getY() <= latUL());
    }



}
