package com.binhjcao.physicstorches;

import com.binhjcao.physicstorches.entity.EntityRigidBody;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class PhysicsTorchesCommands {
  private static final SimpleCommandExceptionType UNKNOWN_TEST =
      new SimpleCommandExceptionType(Component.literal("Unknown test."));

  private static final Map<String, Consumer<ServerPlayer>> TESTS = new LinkedHashMap<>();

  static {
    registerTest("physicstorches:test_rot", PhysicsTorchesCommands::runTestRot);
    registerTest("physicstorches:test_quaternion", PhysicsTorchesCommands::runTestQuaternion);
    registerTest("physicstorches:test_torque", PhysicsTorchesCommands::runTestTorque);
    registerTest("physicstorches:test_linear", PhysicsTorchesCommands::runTestLinear);
    registerTest("physicstorches:test_collision", PhysicsTorchesCommands::runTestCollision);
  }

  private PhysicsTorchesCommands() {}

  public static void register() {
    CommandRegistrationCallback.EVENT.register(PhysicsTorchesCommands::registerCommands);
  }

  private static void registerTest(String id, Consumer<ServerPlayer> runner) {
    TESTS.put(id, runner);
  }

  private static void registerCommands(
      CommandDispatcher<CommandSourceStack> dispatcher,
      net.minecraft.commands.CommandBuildContext registryAccess,
      Commands.CommandSelection environment) {
    dispatcher.register(
        Commands.literal("test")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(
                Commands.literal("run")
                    .then(
                        Commands.argument("test", StringArgumentType.greedyString())
                            .suggests(
                                (context, builder) -> {
                                  String remaining = builder.getRemaining().toLowerCase();
                                  for (String testId : TESTS.keySet()) {
                                    if (testId.toLowerCase().startsWith(remaining)) {
                                      builder.suggest(testId);
                                    }
                                  }
                                  return builder.buildFuture();
                                })
                            .executes(
                                context ->
                                    runNamedTest(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "test"))))));
  }

  private static int runNamedTest(CommandSourceStack source, String testId)
      throws CommandSyntaxException {
    Consumer<ServerPlayer> runner = TESTS.get(testId);
    if (runner == null) {
      throw UNKNOWN_TEST.create();
    }

    ServerPlayer player = source.getPlayerOrException();
    runner.accept(player);
    source.sendSuccess(() -> Component.literal("Ran " + testId + "."), true);
    return 1;
  }

  private static void runTestRot(ServerPlayer player) {
    spawnCubesInFrontOf(
        player,
        new Vec3(1.0D, 0.0D, 0.0D),
        new Vec3(0.0D, 1.0D, 0.0D),
        new Vec3(0.0D, 0.0D, 1.0D));
  }

  private static void runTestQuaternion(ServerPlayer player) {
    Vec3 tumbleAxis = new Vec3(1.0D, 1.0D, 1.0D);
    spawnCubesInFrontOf(
        player,
        new Quaternionf[] {
          new Quaternionf().rotateX((float) Math.toRadians(45)).rotateY((float) Math.toRadians(45)),
          new Quaternionf().rotateY((float) Math.toRadians(45)),
          new Quaternionf().rotateZ((float) Math.toRadians(45))
        },
        tumbleAxis,
        tumbleAxis,
        tumbleAxis);
  }

  private static void runTestTorque(ServerPlayer player) {
    class EntityTestTorque extends EntityRigidBody {
      public EntityTestTorque(Level level) {
        super(level, cubePositionInFrontOf(player));
        linearLock(true);
        angularDamp(0.3D);
      }

      @Override
      protected void onSurfaceInput(Player player, Vec3 surfacePosition, Vec3 surfaceNormal) {
        Vec3 force = surfaceNormal.scale(-3.0D);
        Vec3 leverArm = surfacePosition.subtract(position());
        applyTorqueImpulse(leverArm.cross(force).scale(0.35D));
      }
    }
    player.level().addFreshEntity(new EntityTestTorque(player.level()));
  }

  private static void runTestLinear(ServerPlayer player) {
    class EntityTestLinear extends EntityRigidBody {
      public EntityTestLinear(Level level) {
        super(level, cubePositionInFrontOf(player));
        gravityScale(0);
      }

      @Override
      protected void onSurfaceInput(Player player, Vec3 surfacePosition, Vec3 surfaceNormal) {
        Vec3 impulse =
          PlayerLookRay.from(player, 1.0F).normalizedDirection().scale(2.5D);
        Vec3 leverArm = surfacePosition.subtract(position());
        applyImpulse(impulse, leverArm);
      }
    }
    player.level().addFreshEntity(new EntityTestLinear(player.level()));
  }

  private static void runTestCollision(ServerPlayer player) {
    EntityRigidBody cube = new EntityRigidBody(
        player.level(), cubePositionInFrontOf(player).add(0, 5, 0));
    cube.gravityScale(0.1);
    cube.linearDamp(0.1);
    cube.angularDamp(0.1);
    cube.bounce(0);
    player.level().addFreshEntity(cube);
  }

  private static Vec3 cubePositionInFrontOf(ServerPlayer player) {
    EntityRigidBody.PlayerLookRay ray = EntityRigidBody.PlayerLookRay.from(player, 1.0F);
    return ray.origin().add(ray.normalizedDirection().scale(2.0D));
  }

  private static void spawnCubesInFrontOf(ServerPlayer player, Vec3... bodyAxes) {
    spawnCubesInFrontOf(player, null, bodyAxes);
  }

  private static void spawnCubesInFrontOf(
      ServerPlayer player, Quaternionf[] initialOrientations, Vec3... bodyAxes) {
    EntityRigidBody.PlayerLookRay ray = EntityRigidBody.PlayerLookRay.from(player, 1.0F);
    Vec3 forward = ray.normalizedDirection();
    Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
    if (right.lengthSqr() < 1.0E-6D) {
      right = new Vec3(1.0D, 0.0D, 0.0D);
    } else {
      right = right.normalize();
    }

    Vec3 center = ray.origin().add(forward.scale(2.0D));
    double spacing = 2.0D;
    Vec3[] offsets = {right.scale(-spacing), Vec3.ZERO, right.scale(spacing)};

    for (int i = 0; i < bodyAxes.length; i++) {
      Vec3 position = center.add(offsets[i]);
      Quaternionf initialOrientation =
          initialOrientations != null ? initialOrientations[i] : null;
      EntityRigidBody body = new EntityRigidBody(player.level(), position, initialOrientation);
      body.gravityScale(0);
      body.angularVelocity(bodyAxes[i].scale(2.5D));
      player.level().addFreshEntity(body);
    }
  }
}
