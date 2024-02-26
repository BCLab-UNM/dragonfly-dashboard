package edu.unm.dragonfly.mission;

import edu.unm.dragonfly.mission.step.MissionStep;
import edu.unm.dragonfly.mission.step.MissionStepCurtain;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;

/**
 * @author John Ericksen
 */
public class CurtainCreator implements MissionStepCreator {

    private final ComboBox<String> droneSelection;
    private final ComboBox<String> waypointStartSelection;
    private final ComboBox<String> waypointEndSelection;
    private TextField altitudeField;
    private TextField stacksField;
    private TextField distanceThreshold;
    private TextField stackHeight;
    private CheckBox co2Limit;
    private TextField co2Threshold;
    private TextField co2LimitMargin;

    public CurtainCreator(List<String> drones, List<String> waypoints) {
        this.droneSelection = new ComboBox<>(FXCollections.observableList(drones));
        this.waypointStartSelection = new ComboBox<>(FXCollections.observableList(waypoints));
        this.waypointEndSelection = new ComboBox<>(FXCollections.observableList(waypoints));
    }

    @Override
    public void create(GridPane grid) {

        stacksField = new TextField();
        altitudeField = new TextField();
        distanceThreshold = new TextField();
        stackHeight = new TextField();
        co2Limit = new CheckBox();
        co2Threshold = new TextField();
        co2LimitMargin = new TextField();

        // Set Defaults
        stacksField.setText("1");
        altitudeField.setText("10");
        distanceThreshold.setText("1");
        stackHeight.setText("5");
        co2Limit.setSelected(false);
        co2Threshold.setText("425");
        co2LimitMargin.setText("5");

        GridUtil.builder(grid).increment()
                .add("Drone:", droneSelection)
                .add("Start:", waypointStartSelection)
                .add("End:", waypointEndSelection)
                .add("Altitude:", altitudeField)
                .add("Stacks:", stacksField)
                .add("Stack Height:", stackHeight)
                .add("Distance Threshold:", distanceThreshold)
                .add("CO2 Limit:", co2Limit)
                .add("CO2 Threshold:", co2Threshold)
                .add("CO2 Limit Margin:", co2LimitMargin);
    }

    @Override
    public MissionStep build() {
        return new MissionStepCurtain(
                droneSelection.getSelectionModel().getSelectedItem(),
                waypointStartSelection.getSelectionModel().getSelectedItem(),
                waypointEndSelection.getSelectionModel().getSelectedItem(),
                Double.parseDouble(altitudeField.getText()),
                Integer.parseInt(stacksField.getText()),
                Double.parseDouble(distanceThreshold.getText()),
                Double.parseDouble(stackHeight.getText()),
                co2Limit.isSelected(),
                Double.parseDouble(co2Threshold.getText()),
                Double.parseDouble(co2LimitMargin.getText()));
    }
}
