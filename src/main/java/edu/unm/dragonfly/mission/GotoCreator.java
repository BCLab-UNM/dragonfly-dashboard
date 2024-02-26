package edu.unm.dragonfly.mission;

import edu.unm.dragonfly.mission.step.MissionStep;
import edu.unm.dragonfly.mission.step.MissionStepGoto;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.*;

/**
 * @author John Ericksen
 */
public class GotoCreator implements MissionStepCreator {

    private final ComboBox<String> droneSelection;
    private final ComboBox<String> waypointSelection;
    private ComboBox<Integer> pumpNumber;
    private CheckBox runPump;
    private TextField pumpThreshold;

    public GotoCreator(List<String> drones, List<String> waypoints) {
        this.droneSelection = new ComboBox<>(FXCollections.observableList(drones));
        this.waypointSelection = new ComboBox<>(FXCollections.observableList(waypoints));
    }

    @Override
    public void create(GridPane grid) {
        pumpNumber = new ComboBox<>(FXCollections.observableList(Arrays.asList(0, 1, 2, 3)));
        pumpNumber.getSelectionModel().select(0);
        runPump = new CheckBox();
        pumpThreshold = new TextField("500");

        GridUtil.builder(grid).increment()
                .add("Drone:", droneSelection)
                .add("Waypoint:", waypointSelection)
                .add("Pump CO2:", runPump)
                .add("Pump number:", pumpNumber)
                .add("Pump Threshold:", pumpThreshold);
    }

    @Override
    public MissionStep build() {
        return new MissionStepGoto(droneSelection.getSelectionModel().getSelectedItem(),
                waypointSelection.getSelectionModel().getSelectedItem(),
                runPump.isSelected(),
                pumpNumber.getSelectionModel().getSelectedItem(),
                Double.parseDouble(pumpThreshold.getText()));
    }
}
