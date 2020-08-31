package view;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Java 360 Sphere Image Viewer.
 *
 * Resources: factory image: https://i.ytimg.com/vi/Ifis9sSyPSY/maxresdefault.jpg park image:
 * http://www.mediavr.com/belmorepark1left.jpg
 *
 * @author Leonardo Ono (ono.leo@gmail.com)
 */
public class View extends JPanel implements MouseMotionListener {

  int count = 1;
  private BufferedImage sphereImage;
  private final BufferedImage offscreenImage;
  private int[] sphereImageBuffer;
  private final int[] offscreenImageBuffer;
  private static final double FOV = Math.toRadians(110);
  private final double cameraPlaneDistance;
  private double rayVecs[][][];
  private static final double ACCURACY_FACTOR = 2048;
  private static final int REQUIRED_SIZE = (int) (2 * ACCURACY_FACTOR);
  private double[] asinTable;
  private double[] atan2Table;
  private static final double INV_PI = 1 / Math.PI;
  private static final double INV_2PI = 1 / (2 * Math.PI);
  private double targetRotationX, targetRotationY;
  private double currentRotationX, currentRotationY;
  private int mouseX, mouseY;
  private BufferStrategy bs;

  public View() {
    try {
      BufferedImage sphereTmpImage = ImageIO.read(getClass().getResourceAsStream("/res/cachan mapillary.jpg"));
      sphereImage = new BufferedImage(sphereTmpImage.getWidth(), sphereTmpImage.getHeight(), BufferedImage.TYPE_INT_RGB);
      sphereImage.getGraphics().drawImage(sphereTmpImage, 0, 0, null);
      sphereImageBuffer = ((DataBufferInt) sphereImage.getRaster().getDataBuffer()).getData();
    } catch (IOException ex) {
      Logger.getLogger(View.class.getName()).log(Level.SEVERE, null, ex);
      System.exit(-1);
    }
    offscreenImage = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
    offscreenImageBuffer = ((DataBufferInt) offscreenImage.getRaster().getDataBuffer()).getData();
    cameraPlaneDistance = (offscreenImage.getWidth() / 2) / Math.tan(FOV / 2);
    createRayVecs();
    precalculateAsinAtan2();
    addMouseMotionListener(this);
  }

  private void createRayVecs() {
    rayVecs = new double[3][offscreenImage.getWidth()][offscreenImage.getHeight()]; // x, y, z
    for (int y = 0; y < offscreenImage.getHeight(); y++) {
      for (int x = 0; x < offscreenImage.getWidth(); x++) {
        double vecX = x - offscreenImage.getWidth() / 2;
        double vecY = y - offscreenImage.getHeight() / 2;
        double vecZ = cameraPlaneDistance;
        double invVecLength = 1 / Math.sqrt(vecX * vecX + vecY * vecY + vecZ * vecZ);
        rayVecs[0][x][y] = vecX * invVecLength;
        rayVecs[1][x][y] = vecY * invVecLength;
        rayVecs[2][x][y] = vecZ * invVecLength;
      }
    }
  }

  private void precalculateAsinAtan2() {
    asinTable = new double[REQUIRED_SIZE];
    atan2Table = new double[REQUIRED_SIZE * REQUIRED_SIZE];
    for (int i = 0; i < 2 * ACCURACY_FACTOR; i++) {
      asinTable[i] = Math.asin((i - ACCURACY_FACTOR) * 1 / ACCURACY_FACTOR);
      for (int j = 0; j < 2 * ACCURACY_FACTOR; j++) {
        double y = (i - ACCURACY_FACTOR) / ACCURACY_FACTOR;
        double x = (j - ACCURACY_FACTOR) / ACCURACY_FACTOR;
        atan2Table[i + j * REQUIRED_SIZE] = Math.atan2(y, x);
      }
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    long startTime = System.currentTimeMillis();
    targetRotationX = (mouseY - (offscreenImage.getHeight() / 2)) * 0.025;
    targetRotationY = (mouseX - (offscreenImage.getWidth() / 2)) * 0.025;
    currentRotationX += (targetRotationX - currentRotationX) * 0.1;
    currentRotationY += (targetRotationY - currentRotationY) * 0.1;
    double sinRotationX = Math.sin(currentRotationX);
    double cosRotationX = Math.cos(currentRotationX);
    double sinRotationY = Math.sin(currentRotationY);
    double cosRotationY = Math.cos(currentRotationY);
    IntStream.range(0, offscreenImage.getHeight()).parallel().forEach(y -> {
      IntStream.range(0, offscreenImage.getWidth()).parallel().forEach(x -> {
        double vecX = rayVecs[0][x][y];
        double vecY = rayVecs[1][x][y];
        double vecZ = rayVecs[2][x][y];
        //rotate x
        double tmpVecZ = vecZ * cosRotationX - vecY * sinRotationX;
        double tmpVecY = vecZ * sinRotationX + vecY * cosRotationX;
        vecZ = tmpVecZ;
        vecY = tmpVecY;
        // rotate y
        tmpVecZ = vecZ * cosRotationY - vecX * sinRotationY;
        double tmpVecX = vecZ * sinRotationY + vecX * cosRotationY;
        vecZ = tmpVecZ;
        vecX = tmpVecX;
        int iX = (int) ((vecX + 1) * ACCURACY_FACTOR);
        int iY = (int) ((vecY + 1) * ACCURACY_FACTOR);
        int iZ = (int) ((vecZ + 1) * ACCURACY_FACTOR);
//        // https://en.wikipedia.org/wiki/UV_mapping
        double u = 0.5 + (atan2Table[iZ + iX * REQUIRED_SIZE] * INV_2PI);
        double v = 0.5 - (asinTable[iY] * INV_PI);
        int tx = (int) (sphereImage.getWidth() * u);
        int ty = (int) (sphereImage.getHeight() * (1 - v));
        int color = sphereImageBuffer[ty * sphereImage.getWidth() + tx];
        offscreenImageBuffer[y * offscreenImage.getWidth() + x] = color;
      });
    });
    g.drawImage(offscreenImage, 0, 0, getWidth(), getHeight(), null);
    long endTime = System.currentTimeMillis();

    long durationInMillis = endTime - startTime;
    long millis = durationInMillis % 1000;
    long second = (durationInMillis / 1000) % 60;
    long minute = (durationInMillis / (1000 * 60)) % 60;
    long hour = (durationInMillis / (1000 * 60 * 60)) % 24;

    String time = String.format("%02d:%02d:%02d.%d", hour, minute, second, millis);
    String message = Integer.toString(count);
    message = message + ": ";
    message = message + Integer.toString(offscreenImage.getWidth());
    message = message + " x ";
    message = message + Integer.toString(offscreenImage.getHeight());
    message = message + ": ";
    message = message + time;
    System.out.println(message);
    count++;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    mouseX = e.getX();
    mouseY = e.getY();
    repaint();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    // do nothing
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        View view = new View();
        JFrame frame = new JFrame();
        frame.setTitle("Java 360 Sphere Image Viewer");
        frame.setSize(1000, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.add(view);
        frame.setVisible(true);
        view.requestFocus();
      }
    });
  }

}
