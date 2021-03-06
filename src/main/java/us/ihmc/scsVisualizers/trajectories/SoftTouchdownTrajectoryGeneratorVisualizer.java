package us.ihmc.scsVisualizers.trajectories;

import us.ihmc.commonWalkingControlModules.trajectories.SoftTouchdownPositionTrajectoryGenerator;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.robotics.math.frames.YoFramePoint;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.SimulationConstructionSetParameters;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.yoVariables.registry.YoVariableRegistry;

public class SoftTouchdownTrajectoryGeneratorVisualizer
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final double trajectoryTime = 10.0;
   private final double dt = 0.001;
   private final int recordFrequency = 1;
   private final int bufferSize = (int) (trajectoryTime / dt / recordFrequency + 2);

   private final SoftTouchdownPositionTrajectoryGenerator traj;

   private final YoFramePoint position = new YoFramePoint("position", worldFrame, registry);
   private final YoFrameVector velocity = new YoFrameVector("velocity", worldFrame, registry);
   private final YoFrameVector acceleration = new YoFrameVector("acceleration", worldFrame, registry);

   private final FramePoint3D tempPoint = new FramePoint3D();
   private final FrameVector3D tempVector = new FrameVector3D();

   public SoftTouchdownTrajectoryGeneratorVisualizer()
   {
      traj = new SoftTouchdownPositionTrajectoryGenerator("Traj", worldFrame, registry);
      traj.initialize(0, new FramePoint3D(worldFrame, 1.0, 1.0, 1.0), new FrameVector3D(worldFrame, 0.0, 0.01, -0.1), new FrameVector3D(worldFrame, 0.0001, 0.0, -0.01));

      SimulationConstructionSetParameters parameters = new SimulationConstructionSetParameters();
      parameters.setCreateGUI(true);
      parameters.setDataBufferSize(bufferSize);
      SimulationConstructionSet scs = new SimulationConstructionSet(new Robot("dummy"), parameters);
      scs.addYoVariableRegistry(registry);
      scs.setDT(dt, recordFrequency);

      for (double t = 0.0; t <= trajectoryTime; t += dt)
      {
         traj.compute(t);
         traj.getPosition(tempPoint);
         position.set(tempPoint);
         traj.getVelocity(tempVector);
         velocity.set(tempVector);
         traj.getAcceleration(tempVector);
         acceleration.set(tempVector);

         scs.tickAndUpdate();
      }

      scs.startOnAThread();
      ThreadTools.sleepForever();
   }

   public static void main(String[] args)
   {
      new SoftTouchdownTrajectoryGeneratorVisualizer();
   }
}
