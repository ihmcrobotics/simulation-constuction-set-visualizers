package us.ihmc.robotics.math.trajectories;

import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.robotics.geometry.SpiralBasedAlgorithm;
import us.ihmc.robotics.lists.GenericTypeBuilder;
import us.ihmc.robotics.lists.RecyclingArrayList;
import us.ihmc.robotics.math.frames.YoFramePoint;
import us.ihmc.robotics.math.trajectories.waypoints.EuclideanTrajectoryPointCalculator;
import us.ihmc.robotics.math.trajectories.waypoints.MultipleWaypointsPositionTrajectoryGenerator;
import us.ihmc.robotics.math.trajectories.waypoints.YoFrameEuclideanTrajectoryPoint;
import us.ihmc.robotics.math.trajectories.waypoints.interfaces.EuclideanTrajectoryPointInterface;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.SimulationConstructionSetParameters;
import us.ihmc.commons.thread.ThreadTools;

public class EuclideanTrajectoryPointCalculatorVisualizer
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private enum WaypointGenerator {SPHERE, LINE};
   private final WaypointGenerator waypointGenerator = WaypointGenerator.LINE;

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final double trajectoryTime = 10.0;
   private final double dt = 0.001;
   private final int recordFrequency = 1;
   private final int bufferSize = (int) (trajectoryTime / dt / recordFrequency + 2);

   private final EuclideanTrajectoryPointCalculator calculator = new EuclideanTrajectoryPointCalculator();
   private final MultipleWaypointsPositionTrajectoryGenerator traj;

   private final YoFramePoint currentPositionViz = new YoFramePoint("currentPositionViz", worldFrame, registry);
   private final RecyclingArrayList<YoFrameEuclideanTrajectoryPoint> trajectoryPointsViz;
   private final YoDouble[] relativeWaypointTimes;

   public EuclideanTrajectoryPointCalculatorVisualizer()
   {
      final YoGraphicsListRegistry yoGraphicsListRegistry = new YoGraphicsListRegistry();
      int numberOfTrajectoryPoints = 60;

      GenericTypeBuilder<YoFrameEuclideanTrajectoryPoint> builder = new GenericTypeBuilder<YoFrameEuclideanTrajectoryPoint>()
      {
         private int i = 0;
         @Override
         public YoFrameEuclideanTrajectoryPoint newInstance()
         {
            String indexAsString = Integer.toString(i++);
            YoFrameEuclideanTrajectoryPoint ret = new YoFrameEuclideanTrajectoryPoint("waypointViz", indexAsString, registry, worldFrame);
            yoGraphicsListRegistry.registerYoGraphic("viz", new YoGraphicPosition("waypointPosition" + indexAsString, ret.getPosition(), 0.025, YoAppearance.AliceBlue()));
            return ret;
         }
      };
      trajectoryPointsViz = new RecyclingArrayList<>(numberOfTrajectoryPoints, builder);

      relativeWaypointTimes = new YoDouble[numberOfTrajectoryPoints];
      for (int i = 0; i < relativeWaypointTimes.length; i++)
         relativeWaypointTimes[i] = new YoDouble("relativeWaypointTime" + i, registry);


      yoGraphicsListRegistry.registerYoGraphic("viz", new YoGraphicPosition("currentPositionViz", currentPositionViz, 0.05, YoAppearance.Red()));

      Point3D[] waypointPositions = null;

      switch (waypointGenerator)
      {
      case SPHERE:
         waypointPositions = generateSphereWaypoints(numberOfTrajectoryPoints);
         break;
      case LINE:
         waypointPositions = generateLineWaypoints(numberOfTrajectoryPoints);
         break;
      default:
         throw new RuntimeException("Should not get there: " + waypointGenerator);
      }

      calculator.enableWeightMethod(2.0, 1.0);

      for (int i = 0; i < numberOfTrajectoryPoints; i++)
      {
         calculator.appendTrajectoryPoint(waypointPositions[i]);
      }

      calculator.computeTrajectoryPointTimes(0.0, trajectoryTime);
      calculator.computeTrajectoryPointVelocities(true);

      RecyclingArrayList<? extends EuclideanTrajectoryPointInterface<?>> waypoints = calculator.getTrajectoryPoints();

      traj = new MultipleWaypointsPositionTrajectoryGenerator("traj", calculator.getNumberOfTrajectoryPoints(), ReferenceFrame.getWorldFrame(), registry);
      traj.appendWaypoints(waypoints);
      traj.initialize();

      double previousWaypointTime = 0.0;

      for (int i = 0; i < numberOfTrajectoryPoints; i++)
      {
         Point3D position3d = new Point3D();
         Vector3D linearVelocity3d = new Vector3D();
         EuclideanTrajectoryPointInterface<?> waypoint = waypoints.get(i);
         waypoint.getPosition(position3d);
         waypoint.getLinearVelocity(linearVelocity3d);
         trajectoryPointsViz.get(i).set(waypoint.getTime(), position3d, linearVelocity3d);
         relativeWaypointTimes[i].set(waypoint.getTime() - previousWaypointTime);
         previousWaypointTime = waypoint.getTime();
      }

      SimulationConstructionSetParameters parameters = new SimulationConstructionSetParameters();
      parameters.setCreateGUI(true);
      parameters.setDataBufferSize(bufferSize);
      SimulationConstructionSet scs = new SimulationConstructionSet(new Robot("dummy"), parameters);
      scs.addYoVariableRegistry(registry);
      scs.setDT(dt, recordFrequency);
      Graphics3DObject linkGraphics = new Graphics3DObject();
      linkGraphics.addCoordinateSystem(0.3);
      scs.addStaticLinkGraphics(linkGraphics);
      
      scs.addYoGraphicsListRegistry(yoGraphicsListRegistry, true);

      for (double t = 0.0; t <= trajectoryTime; t += dt)
      {
         traj.compute(t);

         FramePoint3D currentPosition = new FramePoint3D();
         traj.getPosition(currentPosition);
         currentPositionViz.set(currentPosition);

         scs.tickAndUpdate();
      }

      scs.startOnAThread();
      ThreadTools.sleepForever();
   }

   private Point3D[] generateSphereWaypoints(int numberOfTrajectoryPoints)
   {
      Point3D sphereOrigin = new Point3D(0.0, 0.0, 0.7);
      double sphereRadius = 0.5;
      int numberOfPointsToGenerate = numberOfTrajectoryPoints;
      Point3D[] waypointPositions = SpiralBasedAlgorithm.generatePointsOnSphere(sphereOrigin, sphereRadius, numberOfPointsToGenerate);
      return waypointPositions;
   }

   private Point3D[] generateLineWaypoints(int numberOfTrajectoryPoints)
   {
      Point3D startPoint = new Point3D(-1.0, 0.0, 0.7);
      Point3D endPoint = new Point3D(1.0, 0.0, 0.7);
      Point3D[] waypoints = new Point3D[numberOfTrajectoryPoints];

      for (int i = 0; i < numberOfTrajectoryPoints; i++)
      {
         waypoints[i] = new Point3D();
         waypoints[i].interpolate(startPoint, endPoint, i / (double)(numberOfTrajectoryPoints - 1.0));
      }
      return waypoints;
   }

   public static void main(String[] args)
   {
      new EuclideanTrajectoryPointCalculatorVisualizer();
   }
}
