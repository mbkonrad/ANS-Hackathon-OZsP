import java.io.*;
import java.util.*;

import ConvexHull.Point3d;
import ConvexHull.ConvexHull3D;
import Octree.AABB;
import Octree.Cube3d;
import Octree.Octree;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point3D;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Shape3D;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.morbz.minecraft.blocks.*;
import net.morbz.minecraft.blocks.states.Facing4State;
import net.morbz.minecraft.level.FlatGenerator;
import net.morbz.minecraft.level.GameType;
import net.morbz.minecraft.level.IGenerator;
import net.morbz.minecraft.level.Level;
import net.morbz.minecraft.world.DefaultLayers;
import net.morbz.minecraft.world.World;

public class ExportService extends Service<Void> {

    private Boolean currentState;
    private ObjectProperty<String> currentWork = new SimpleObjectProperty<>();
    private List<File> files;
    private long pointsCount;
    private long pointsExportCount;

    private List<Point3d> points3dList;
    private List<Cube3d> cube3dList;
    private List<Cube3d> cube3dListNew;
    //    private List<Cube3d> cube3dListLocalTemp;
//    private List<Cube3d> cube3dListLocal;
    private Point3d[] vertices;
    private Octree octree;
    private int counter = 0;
    private long max = 0;

    private final Stage cubesStage = new Stage();

    Group root = new Group();
    XformBox cameraXform = new XformBox();
    XformBox allXForm = new XformBox();
    PhongMaterial redMaterial, greenMaterial, blueMaterial;

    PerspectiveCamera camera = new PerspectiveCamera(true);

    private static double CAMERA_INITIAL_DISTANCE = -450;
    private static double CAMERA_INITIAL_X_ANGLE = -10.0;
    private static double CAMERA_INITIAL_Y_ANGLE = 0.0;
    private static double CAMERA_NEAR_CLIP = 0.1;
    private static double CAMERA_FAR_CLIP = 10000.0;
    private static double AXIS_LENGTH = 1000.0;
    private static double MOUSE_SPEED = 0.1;
    private static double ROTATION_SPEED = 2.0;

    double mouseStartPosX, mouseStartPosY;
    double mousePosX, mousePosY;
    double mouseOldX, mouseOldY;
    double mouseDeltaX, mouseDeltaY;

    private void handleMouse(Scene scene) {

        scene.setOnScroll(me -> {
            camera.setTranslateZ(camera.getTranslateZ() + me.getDeltaY());
        });

        scene.setOnMousePressed(me -> {
            mouseStartPosX = me.getSceneX();
            mouseStartPosY = me.getSceneY();
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });

        scene.setOnMouseDragged(me -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            mouseDeltaX = (mousePosX - mouseOldX);
            mouseDeltaY = (mousePosY - mouseOldY);

            if (me.isPrimaryButtonDown()) {
                allXForm.addRotation(-mouseDeltaX * MOUSE_SPEED * ROTATION_SPEED, Rotate.Y_AXIS);
                allXForm.addRotation(mouseDeltaY * MOUSE_SPEED * ROTATION_SPEED, Rotate.X_AXIS);
            }
        });
    }

    private void handleKeyboard(Scene scene) {
        scene.setOnKeyPressed(event -> allXForm.reset());
    }

    PhongMaterial createMaterial(Color diffuseColor, Color specularColor) {
        PhongMaterial material = new PhongMaterial(diffuseColor);
        material.setSpecularColor(specularColor);
        return material;
    }

    class XformBox extends Group {
        XformBox() {
            super();
            getTransforms().add(new Affine());
        }

        public void addRotation(double angle, Point3D axis) {
            Rotate r = new Rotate(angle, axis);
            getTransforms().set(0, r.createConcatenation(getTransforms().get(0)));
        }

        public void reset() {
            getTransforms().set(0, new Affine());
        }
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                double xmax=0,xmin=0,ymax=0,ymin=0,zmax=0,zmin=0;
                Platform.runLater(
                        () -> {
                            setCurrentWork("Start");
                        }
                );

                points3dList = new ArrayList<Point3d>();
                counter = 0;
                currentState = true;

                // ilosc operacji do progressu
                max = pointsCount;

                try {

                    for (File file : files) {

                        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                        String row;

                        Platform.runLater(
                                () -> {
                                    setCurrentWork("1 z 4: Dekodowanie plikow...");
                                }
                        );

                        // pomijamy naglowek
                        int count=0;
                        bufferedReader.readLine();
                        while ((row = bufferedReader.readLine()) != null) {
                            if (!currentState) return null;

                            try {

                                String[] pointArray = new String[10];
                                pointArray = row.split(",");

                                Point3d point3d = new Point3d();
                                point3d.x = Double.parseDouble(pointArray[0]);  // x
                                point3d.y = Double.parseDouble(pointArray[1]);  // y
                                point3d.z = Double.parseDouble(pointArray[2]);  // z
                                point3d.r = Integer.parseInt(pointArray[3]);    // Red
                                point3d.g = Integer.parseInt(pointArray[4]);    // Green
                                point3d.b = Integer.parseInt(pointArray[5]);    // Blue
                                point3d.c = Double.parseDouble(pointArray[9]);// classification
                                if(point3d.c==7)
                                {
                                    continue;
                                }
                                if(point3d.x<xmin) xmin=point3d.x;
                                if(point3d.x>xmax) xmax=point3d.x;
                                if(point3d.y<ymin) ymin=point3d.y;
                                if(point3d.y>ymax) ymax=point3d.y;
                                if(point3d.z<zmin) zmin=point3d.z;
                                if(point3d.z>zmax) zmax=point3d.z;

                                points3dList.add(point3d);

                                if(count==0)
                                {
                                    ymin=point3d.y;
                                    ymax=point3d.y;
                                    xmin=point3d.x;
                                    xmax=point3d.x;
                                    zmin=point3d.z;
                                    zmax=point3d.z;
                                    count++;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            counter++;
                            updateProgress(counter, max);
                        }

                    }
                    for(Point3d pkt:points3dList)
                    {
                        pkt.x-=xmin;
                        pkt.x=Math.round(pkt.x);
                        pkt.y-=ymin;
                        pkt.y=Math.round(pkt.y);
                        pkt.z=pkt.z-zmin+21;
                        pkt.z=Math.round(pkt.z);
                    }
                    System.out.println("Prawidlowych pkt: " + points3dList.size());

                } catch (Exception e) {
                    e.printStackTrace();
                }



                try {
                    Platform.runLater(
                            () -> {
                                setCurrentWork("2 z 4: zadanie");
                            }
                    );
                    for(Point3d pkt: points3dList)
                    {
                        List<Double> classif = new ArrayList<Double>();
                        classif.add(pkt.c);
                        for(int i=points3dList.indexOf(pkt)+1;i<points3dList.size();i++)
                        {
                            Point3d temp = points3dList.get(i);
                            if(temp.x==pkt.x && temp.y==pkt.y && temp.z==pkt.z)
                            {
                                classif.add(temp.c);
                                points3dList.remove(i);
                                i--;
                            }
                        }

                    }
                    System.out.println(xmin);
                    System.out.println(xmax);
                    System.out.println(ymin);
                    System.out.println(ymax);
                    System.out.println(zmin);
                    System.out.println(zmax);

                    /// zadanie


                } catch (Exception e) {
                    e.printStackTrace();
                }


                try {
                    Platform.runLater(
                            () -> {
                                setCurrentWork("3 z 4: zadanie ...");
                            }
                    );

                    //zadanie






                } catch (Exception e) {
                    e.printStackTrace();
                }





                try {
                    Platform.runLater(
                            () -> {
                                setCurrentWork("4 z 4: rozklad chmury do mapy...");
                            }
                    );



                    DefaultLayers layers = new DefaultLayers();
                    layers.setLayer(0, Material.BEDROCK);
                    IGenerator generator = new FlatGenerator(layers);
                    Level level = new Level("HackathonMap", generator);
                    level.setGameType(GameType.CREATIVE);

                    World world = new World(level, layers);

                    // Create a huge structure of glass that has an area of 100x100 blocks and is 50 blocks height.
                    // On top of the glass structure we have a layer of grass.
                    level.setSpawnPoint(50, 20 + 1, 50);
                    for (int x = 0; x < 400; x++) {
                        for (int z = 0; z < 400; z++) {
                            // Set glass
                            for (int y = 1; y < 20; y++) {
                                world.setBlock(x, y, z, SimpleBlock.GLASS);
                            }
                            // Set grass
                            world.setBlock(x, 20, z, SimpleBlock.GLASS_PANE);
                        }
                    }
                    for(Point3d pkt:points3dList)
                    {
                        if(pkt.c==9) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, new StainedBlock(StainedBlock.StainedMaterial.WOOL, StainedBlock.StainedColor.LIGHT_BLUE));
                        else if(pkt.c==8) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, new StainedBlock(StainedBlock.StainedMaterial.WOOL, StainedBlock.StainedColor.RED));
                        else if(pkt.c==7) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, new StainedBlock(StainedBlock.StainedMaterial.WOOL, StainedBlock.StainedColor.BLACK));
                        else if(pkt.c==6) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, new StainedBlock(StainedBlock.StainedMaterial.WOOL, StainedBlock.StainedColor.LIGHT_GRAY));
                        else if(pkt.c==5) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, new StainedBlock(StainedBlock.StainedMaterial.WOOL, StainedBlock.StainedColor.GREEN));
                        else if(pkt.c==4) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, new StainedBlock(StainedBlock.StainedMaterial.WOOL, StainedBlock.StainedColor.LIME));
                        else if(pkt.c==3) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, SimpleBlock.GRASS);
                        else if(pkt.c==2) world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, DirtBlock.DIRT);
                        else world.setBlock((int)pkt.x,(int)pkt.z,(int)pkt.y, SimpleBlock.BEDROCK);
                    }

                    // przyk??ad tworzenia drzwi
                        world.setBlock(50, 21, 50, DoorBlock.makeLower(DoorBlock.DoorMaterial.OAK, Facing4State.EAST, false));
                        world.setBlock(50, 22, 50, DoorBlock.makeUpper(DoorBlock.DoorMaterial.OAK, DoorBlock.HingeSide.LEFT));

                    //  save the world
                    world.save();

                } catch (Throwable e) {
                    e.printStackTrace();
                }

                Platform.runLater(
                        () -> {
                            setCurrentWork(" READY :)");
                        }
                );
                return null;
            }



        };
    }


    public Boolean getCurrentState() {
        return currentState;
    }

    public void setCurrentState(Boolean stateNew) {
        this.currentState = stateNew;
    }

    public void setFiles(List<File> files) {
        this.files = files;
    }

    public void setPointsCount(long pointsCount) {
        this.pointsCount = pointsCount;
    }


    public ObjectProperty<String> currentWorkProperty() {
        return currentWork;
    }

    public final String getCurrentWork() {
        return currentWorkProperty().get();
    }

    public final void setCurrentWork(String currentWork) {
        currentWorkProperty().set(currentWork);
    }

    public long getPointsExportCount() {
        return pointsExportCount;
    }

    public void setPointsExportCount(long pointsExportCount) {
        this.pointsExportCount = pointsExportCount;
    }


    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public long getMax() {
        return max;
    }

    public void setMax(long max) {
        this.max = max;
    }
}
