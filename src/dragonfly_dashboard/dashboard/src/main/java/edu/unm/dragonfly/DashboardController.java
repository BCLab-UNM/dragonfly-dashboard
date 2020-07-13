package edu.unm.dragonfly;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.*;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.ArcGISTiledElevationSource;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Surface;
import com.esri.arcgisruntime.mapping.view.*;
import com.esri.arcgisruntime.symbology.*;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import org.ros.exception.ServiceNotFoundException;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Subscriber;

import javax.inject.Inject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class DashboardController {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("hh:mm:ss");
    private static final String ELEVATION_IMAGE_SERVICE =
            "https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer";
    private static final int ALPHA_RED = 0x33FF0000;
    private static final int RED = 0xFFFF0000;
    private static final Random RAND = new Random(System.currentTimeMillis());

    @FXML
    private SceneView sceneView;
    @FXML
    private Button add;
    @FXML
    private Button delete;
    @FXML
    private Button center;
    @FXML
    private ListView<Drone> drones;
    @FXML
    private ListView<String> log;
    @FXML
    private TextField coordinates;
    @FXML
    private Button select;
    @FXML
    private Button lawnmower;
    @FXML
    private Button ddsa;
    @FXML
    private Button random;
    @FXML
    private Button cancel;

    private final ObservableList<Drone> droneList = FXCollections.observableArrayList();
    private final ObservableList<String> logList = FXCollections.observableArrayList();
    private final GraphicsOverlay droneOverlay = new GraphicsOverlay();
    private final GraphicsOverlay droneShadowOverlay = new GraphicsOverlay();
    private final GraphicsOverlay boundaryOverlay = new GraphicsOverlay();
    private final GraphicsOverlay pathOverlay = new GraphicsOverlay();
    private final List<Point> boundaryPoints = new ArrayList<Point>();
    private CoordianteSelectionMode mode = CoordianteSelectionMode.CLEAR;

    private enum CoordianteSelectionMode {
        SELECT("Finished"),
        FINISHED("Clear"),
        CLEAR("Select Boundary");

        private final String buttonLabel;

        CoordianteSelectionMode(String buttonLabel) {
            this.buttonLabel = buttonLabel;
        }
    }

    @Inject
    private ConnectedNode node;

    public void initialize() {
        // create a scene and add a basemap to it
        ArcGISScene scene = new ArcGISScene();
        scene.setBasemap(Basemap.createImagery());

        sceneView.setArcGISScene(scene);

        sceneView.getGraphicsOverlays().add(droneOverlay);
        droneOverlay.getSceneProperties().setSurfacePlacement(LayerSceneProperties.SurfacePlacement.RELATIVE_TO_SCENE);
        sceneView.getGraphicsOverlays().add(droneShadowOverlay);
        droneShadowOverlay.getSceneProperties().setSurfacePlacement(LayerSceneProperties.SurfacePlacement.DRAPED_FLAT);
        sceneView.getGraphicsOverlays().add(boundaryOverlay);
        boundaryOverlay.getSceneProperties().setSurfacePlacement(LayerSceneProperties.SurfacePlacement.DRAPED_FLAT);
        sceneView.getGraphicsOverlays().add(pathOverlay);
        pathOverlay.getSceneProperties().setSurfacePlacement(LayerSceneProperties.SurfacePlacement.DRAPED_FLAT);

        sceneView.setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                Point2D point2D = new Point2D(event.getX(), event.getY());

                // get the scene location from the screen position
                ListenableFuture<Point> pointFuture = sceneView.screenToLocationAsync(point2D);
                pointFuture.addDoneListener(() -> {
                    try {
                        Point point = pointFuture.get();
                        coordinates.setText("Lat: " + point.getY() + " Lon: " + point.getX());

                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        sceneView.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if(mode == CoordianteSelectionMode.SELECT) {
                    Point2D point2D = new Point2D(event.getX(), event.getY());

                    // get the scene location from the screen position
                    ListenableFuture<Point> pointFuture = sceneView.screenToLocationAsync(point2D);
                    pointFuture.addDoneListener(() -> {
                        try {
                            Point point = pointFuture.get();

                            boundaryPoints.add(point);

                            boundaryOverlay.getGraphics().clear();

                            Graphic boudaryGraphic;
                            if(boundaryPoints.size() == 1) {
                                SimpleMarkerSymbol markerSymbol = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, RED, 2);
                                boudaryGraphic = new Graphic(new Point(boundaryPoints.get(0).getX(), boundaryPoints.get(0).getY()), markerSymbol);
                            } else if(boundaryPoints.size() == 2) {
                                PolylineBuilder lineBuilder = new PolylineBuilder(SpatialReferences.getWgs84());
                                lineBuilder.addPoint(boundaryPoints.get(0).getX(), boundaryPoints.get(0).getY());
                                lineBuilder.addPoint(boundaryPoints.get(1).getX(), boundaryPoints.get(1).getY());
                                SimpleLineSymbol lineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, RED, 2);
                                boudaryGraphic = new Graphic(lineBuilder.toGeometry(), lineSymbol);
                            } else {
                                PointCollection polygonPoints = new PointCollection(boundaryPoints);
                                SimpleFillSymbol polygonSymbol = new SimpleFillSymbol(SimpleFillSymbol.Style.SOLID, ALPHA_RED, new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, RED, .3f));
                                boudaryGraphic = new Graphic(new Polygon(polygonPoints), polygonSymbol);
                            }
                            boundaryOverlay.getGraphics().add(boudaryGraphic);


                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        });

        select.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(mode == CoordianteSelectionMode.SELECT) {
                    mode = CoordianteSelectionMode.FINISHED;
                    lawnmower.setDisable(!(!drones.getSelectionModel().isEmpty() && !boundaryPoints.isEmpty()));
                    random.setDisable(!(!drones.getSelectionModel().isEmpty() && !boundaryPoints.isEmpty()));
                } else if (mode == CoordianteSelectionMode.FINISHED) {
                    mode = CoordianteSelectionMode.CLEAR;
                    boundaryOverlay.getGraphics().clear();
                    boundaryPoints.clear();
                } else if (mode == CoordianteSelectionMode.CLEAR) {
                    mode = CoordianteSelectionMode.SELECT;
                }
                select.setText(mode.buttonLabel);
            }
        });

        // add base surface for elevation data
        Surface surface = new Surface();
        surface.getElevationSources().add(new ArcGISTiledElevationSource(ELEVATION_IMAGE_SERVICE));
        scene.setBaseSurface(surface);

        drones.setItems(droneList);
        log.setItems(logList);

        delete.setDisable(true);
        center.setDisable(true);
        lawnmower.setDisable(true);
        ddsa.setDisable(true);
        random.setDisable(true);
        cancel.setDisable(true);

        add.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                TextInputDialog dialog = new TextInputDialog("Add Drone");
                dialog.setHeaderText("Add Drone");
                Optional<String> output = dialog.showAndWait();

                if(output.isPresent()) {
                    addDrone(output.get());
                    drones.getSelectionModel().clearSelection();
                }
            }
        });

        delete.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                deleteDrone(drones.getSelectionModel().getSelectedItem());
                drones.getSelectionModel().clearSelection();
            }
        });

        center.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                centerDrone(drones.getSelectionModel().getSelectedItem());
                drones.getSelectionModel().clearSelection();
            }
        });

        lawnmower.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(!boundaryPoints.isEmpty()) {
                        LawnmowerDialogFactory.create((stepLength, altitude, stacks, walkBoundary, walk, waittime) -> {
                            try {
                                drones.getSelectionModel().getSelectedItem().lawnmower(boundaryPoints, stepLength, altitude, stacks, walkBoundary, walk.id, waittime);
                            } catch (ServiceNotFoundException e) {
                                e.printStackTrace();
                            }
                        });
                }
                drones.getSelectionModel().clearSelection();
            }
        });

        ddsa.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                DDSADialogFactory.create((radius, stepLength, altitude, loops, stacks, walk, waittime) -> {
                    try {
                        drones.getSelectionModel().getSelectedItem().ddsa(radius, stepLength, altitude, loops, stacks, walk.id, waittime);
                    } catch (ServiceNotFoundException e) {
                        e.printStackTrace();
                    }
                });
                drones.getSelectionModel().clearSelection();
            }
        });

        random.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if(!boundaryPoints.isEmpty()) {
                    drones.getSelectionModel().clearSelection();
                    Observable.fromCallable(new Callable<GeneticTSP.Tour>() {
                        @Override
                        public GeneticTSP.Tour call() throws Exception {
                            double xmax = -10000;
                            double xmin = 10000;
                            double ymax = -10000;
                            double ymin = 10000;

                            for (Point point : boundaryPoints) {
                                if(xmax < point.getX()) {
                                    xmax = point.getX();
                                }
                                if(xmin > point.getX()) {
                                    xmin = point.getX();
                                }
                                if(ymax < point.getY()) {
                                    ymax = point.getY();
                                }
                                if(ymin > point.getY()) {
                                    ymin = point.getY();
                                }
                            }

                            List<GeneticTSP.PointImpl> points = new ArrayList<>();
                            for(int i = 0; i < 100; i++) {
                                points.add(new GeneticTSP.PointImpl((RAND.nextDouble() * (xmax - xmin)) + xmin, (RAND.nextDouble() * (ymax - ymin) + ymin) ));
                            }

                            GeneticTSP.Population population = GeneticTSP.Population.generate(points, 500);

                            for(int i = 0; i < 10000; i++) {
                                long start = System.currentTimeMillis();
                                population = GeneticTSP.evolve(population);

                                System.out.println("Evolution " + i + " " +
                                        "took: " + (System.currentTimeMillis() - start) + "ms, " +
                                        "distance: " + population.getMostFit().getDistance());
                            }

                            return population.getMostFit();
                        }
                    })
                            .subscribeOn(Schedulers.computation())
                            .observeOn(JavaFxScheduler.platform())
                            .subscribe(tour -> draw(tour));


                }
            }
        });

        cancel.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    drones.getSelectionModel().getSelectedItem().cancel();
                } catch (ServiceNotFoundException e) {
                    e.printStackTrace();
                }
                drones.getSelectionModel().clearSelection();
            }
        });

        drones.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Drone>() {
            @Override
            public void changed(ObservableValue observable, Drone oldValue, Drone newValue) {
                boolean selected = newValue != null;
                delete.setDisable(!selected);
                center.setDisable(!selected);
                lawnmower.setDisable(!(selected && !boundaryPoints.isEmpty() && mode == CoordianteSelectionMode.FINISHED));
                ddsa.setDisable(!selected);
                random.setDisable(!(selected && !boundaryPoints.isEmpty() && mode == CoordianteSelectionMode.FINISHED));
                cancel.setDisable(!selected);
            }
        });

        Subscriber<std_msgs.String> nameBroadcastSubscriber = node.newSubscriber("/dragonfly/announce", std_msgs.String._TYPE);
        PublishSubject<String> nameSubject = PublishSubject.create();
        nameBroadcastSubscriber.addMessageListener(name -> nameSubject.onNext(name.getData()));
    
        nameSubject
                .observeOn(JavaFxScheduler.platform())
                .subscribe(name -> addDrone(name));

        log("Dashboard Startup");
    }

    private void draw(GeneticTSP.Tour tour) {
        pathOverlay.getGraphics().clear();

        PolylineBuilder lineBuilder = new PolylineBuilder(SpatialReferences.getWgs84());

        for(GeneticTSP.PointImpl point : tour.getPoints()) {
            lineBuilder.addPoint(point.getX(), point.getY());
        }

        SimpleLineSymbol lineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.DASH, 0xFF800080, 2);
        Graphic graphic = new Graphic(lineBuilder.toGeometry(), lineSymbol);
        pathOverlay.getGraphics().add(graphic);
    }

    private void centerDrone(Drone drone) {
         drone.getLatestPosition()
                 .observeOn(JavaFxScheduler.platform())
                 .subscribe(new Consumer<Drone.LatLonRelativeAltitude>() {
                     @Override
                     public void accept(Drone.LatLonRelativeAltitude position) {
                         Camera camera = new Camera(position.getLatitude(), position.getLongitude(), 10, 0, 0, 0);
                         sceneView.setViewpointCameraAsync(camera);
                     }
                 });
    }

    private void deleteDrone(Drone name) {
        droneList.remove(name);
        name.shutdown();
        log("Removed " + name);
    }

    private void addDrone(String name) {

        if(!exists(name)) {
            Drone drone = new Drone(node, name);
            drone.init();

            drone.getLog()
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(message -> log(name + ": " + message));

            drone.getPositions()
                    .observeOn(JavaFxScheduler.platform())
                    .subscribe(new Observer<Drone.LatLonRelativeAltitude>() {
                        private Graphic droneGraphic;
                        private Graphic droneShadowGraphic;

                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(Drone.LatLonRelativeAltitude navSatFix) {
                            Point point = new Point(navSatFix.getLongitude(), navSatFix.getLatitude(), navSatFix.getRelativeAltitude());
                            if (droneGraphic == null) {
                                SimpleMarkerSceneSymbol symbol = new SimpleMarkerSceneSymbol(SimpleMarkerSceneSymbol.Style.CYLINDER, 0xFFFF0000, 1, 1, 1, SceneSymbol.AnchorPosition.CENTER);
                                TextSymbol nameText = new TextSymbol(10, name, 0xFFFFFFFF, TextSymbol.HorizontalAlignment.LEFT, TextSymbol.VerticalAlignment.MIDDLE);
                                nameText.setOffsetX(25);
                                droneGraphic = new Graphic(point, new CompositeSymbol(Arrays.asList(symbol, nameText)));
                                droneOverlay.getGraphics().add(droneGraphic);

                                SimpleMarkerSymbol shadowSymbol = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, 0x99000000, 2.5f);
                                droneShadowGraphic = new Graphic(point, shadowSymbol);
                                droneShadowOverlay.getGraphics().add(droneShadowGraphic);
                            } else {
                                droneGraphic.setGeometry(point);
                                droneShadowGraphic.setGeometry(point);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {
                            if (droneGraphic != null) {
                                droneOverlay.getGraphics().remove(droneGraphic);
                                droneShadowOverlay.getGraphics().remove(droneShadowGraphic);
                            }
                        }
                    });

            droneList.add(drone);

            log("Added " + name);

            if(droneList.size() == 1) {
                centerDrone(drone);
            }
        }
    }

    private boolean exists(String name) {
        for(Drone drone : droneList) {
            if(name.equals(drone.getName())) {
                return true;
            }
        }
        return false;
    }

    private void log(String message) {
        logList.add("[" + DATE_FORMAT.format(new Date()) + "]" + message);
    }

    void terminate() {
        if (sceneView != null) {
            sceneView.dispose();
        }
    }
}
