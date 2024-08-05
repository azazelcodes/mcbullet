package me.azazeldev.mcbullet;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.SphereShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;

import org.bukkit.util.Vector;
import org.incendo.cloud.annotations.*;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.meta.SimpleCommandMeta;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.LegacyPaperCommandManager;

import org.joml.AxisAngle4f;
import org.joml.Matrix4f;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

// PLEASE OPTIMIZE THIS! its laggy sometimes + lots of collision errors like clipping in the floor (? maybe also just wrong rotation)
public class Main extends JavaPlugin implements Listener {
    private DiscreteDynamicsWorld dynamicsWorld;
    private final Map<Entity, RigidBody> bodies = new ConcurrentHashMap<>();

    public float physicsTime = 1.0f;
    public int physicsSubsteps = 300;

    Component prefix;
    MiniMessage mini;

    @Override
    public void onEnable() {
        getLogger().info("mcBullet enabled!");

        // Physics World Setup
        DefaultCollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
        CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);
        DbvtBroadphase broadphase = new DbvtBroadphase();
        ConstraintSolver solver = new SequentialImpulseConstraintSolver();
        dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);

        // More exact calc??
        dynamicsWorld.setNumTasks(80); // Increase the number of solver iterations

        // Event Init
        getServer().getPluginManager().registerEvents(this, this);

        // Command Manager Init
        LegacyPaperCommandManager<CommandSender> commandManager = LegacyPaperCommandManager.createNative(
                this,
                ExecutionCoordinator.simpleCoordinator()
        );

        /* IDK IF NEEDED??
        if (commandManager.hasCapability(CloudBukkitCapabilities.BRIGADIER)) {
            commandManager.registerBrigadier();
        }
        */

        if (commandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            commandManager.registerAsynchronousCompletions();
        }

        AnnotationParser<CommandSender> annotationParser = new AnnotationParser<>(commandManager, CommandSender.class, parameters -> SimpleCommandMeta.empty());
        annotationParser.parse(this);

        // Help Command + Prefix
        mini = MiniMessage.miniMessage();

        prefix = mini.deserialize("<yellow>[<gray>mcBullet</gray><yellow>] ");

        MinecraftHelp<CommandSender> help = MinecraftHelp.createNative(
                "/bullethelp",
                commandManager
        );

        commandManager.command(
                commandManager.commandBuilder("bullethelp")
                        .optional("query", greedyStringParser(), DefaultValue.constant(""))
                        .handler(context ->
                            help.queryCommands(context.get("query"), context.sender())
                        )
        );

        // Run physics steps every tick
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePhysics();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @Override
    public void onDisable() {
        getLogger().info("mcBullet disabled!");
    }

    // Events
    @EventHandler
    public void onPhysicsHit(PlayerArmSwingEvent e) {
        Player player = e.getPlayer();
        Vector dir = player.getLocation().getDirection();

        RayTraceResult entityRay = player.getWorld().rayTraceEntities(player.getEyeLocation(), dir, 50f, 1f);

        if (entityRay != null && entityRay.getHitEntity() != null && entityRay.getHitEntity().getType() == EntityType.BLOCK_DISPLAY) {
            BlockDisplay target = (BlockDisplay) entityRay.getHitEntity();
            /* Only needed for relative impulses which mess up rotation
            Location position = player.getEyeLocation();
            Vector3f vecPosition = new Vector3f(
                    (float) position.x(),
                    (float) position.y(),
                    (float) position.z()
            );
            */

            UUID targetID = target.getUniqueId();

            for (Entity physObj : bodies.keySet()) {
                if (physObj.getUniqueId() == targetID) {
                    RigidBody body = bodies.get(physObj);
                    Vector3f centerOfMassPosition = new Vector3f();
                    body.getCenterOfMassPosition(centerOfMassPosition);
                    Vector3f vecDir = new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
                    /* Same thing as before. Relative impulses only
                    vecDir.normalize();

                    Vector3f upVector = new Vector3f(0, 1, 0);

                    Vector3f rightVector = new Vector3f(
                            vecDir.x * upVector.x,
                            vecDir.y * upVector.y,
                            vecDir.z * upVector.z
                    );

                    Vector3f toTarget = new Vector3f();
                    toTarget.sub(vecPosition, centerOfMassPosition);

                    float rightComponent = rightVector.dot(toTarget);
                    float upComponent = upVector.dot(toTarget);
                    float forwardComponent = vecDir.dot(toTarget);

                    Vector3f relativePosition = new Vector3f(rightComponent, upComponent, forwardComponent);
                    */

                    // Apply force at hit pos
                    if (!body.isActive()) {
                        body.activate();
                    }
                    body.applyCentralImpulse(new Vector3f(vecDir.x*5000, vecDir.y*5000, vecDir.z*5000));
                }
            }
        }
    }

    // Commands
    @Command("killall|killphys")
    public void killAllPhysics(
            CommandSender sender
    ) {
        for (Entity physObj : bodies.keySet()) {
            RigidBody body = bodies.get(physObj);
            if (body != null) {
                dynamicsWorld.removeRigidBody(body);
            }
            physObj.remove();
            bodies.remove(physObj, body);
        }

        Component message = Component.empty()
                .append(prefix)
                .append(mini.deserialize("<#D9CFF2>Killed all physics objects in this physics world!"));

        sender.sendMessage(message);
    }

    @Command("howmany|physcount|getbullets")
    public void count(
            CommandSender sender
    ) {
        Component message = Component.empty()
                .append(prefix)
                .append(mini.deserialize(
                        "<#D9CFF2>There are <gray><size></gray> physics objects in this physics world!",
                        Placeholder.component("size",
                                Component.text(bodies.size())
                        ))
                );

        sender.sendMessage(message);
    }

    @Command("time|timemult|timestep|delta set <multiplier>")
    public void setPhysicsTime (
            CommandSender sender,
            @Argument(value = "multiplier", description = "Speed multiplier of the physics time (Default: 1)") float targetMult
    ) {
        physicsTime = targetMult;

        Component message = Component.empty()
                .append(prefix)
                .append(mini.deserialize(
                        "<#D9CFF2>The physics are now running at <gray><speed>x</gray> speed!",
                        Placeholder.component("speed",
                                Component.text(physicsTime)
                        ))
                );

        sender.sendMessage(message);
    }

    @Command("time|timemult|timestep|delta get")
    public void getPhysicsTime (
                              CommandSender sender
    ) {
        Component message = Component.empty()
                .append(prefix)
                .append(mini.deserialize(
                        "<#D9CFF2>The physics are calculating <gray><time></gray> substeps per physics tick at the moment.",
                        Placeholder.component("time",
                                Component.text(physicsTime)
                        ))
                );

        sender.sendMessage(message);
    }

    @Command("calcstep|calc|sub|substeps set <steps>")
    public void setPhysicsSteps ( // This kinda useless?
            CommandSender sender,
            @Argument(value = "steps", description = "How many calculation sub steps should be done for each physics tick (Default: 300)") int targetSteps
    ) {
        physicsSubsteps = targetSteps;

        Component message = Component.empty()
                .append(prefix)
                .append(mini.deserialize(
                        "<#D9CFF2>The physics are now calculating <gray><steps></gray> substeps per physics tick!",
                        Placeholder.component("steps",
                                Component.text(physicsSubsteps)
                        ))
                );

        sender.sendMessage(message);
    }

    @Command("step|calcstep|calc|sub|substeps get")
    public void getPhysicsSteps ( // This kinda useless aswell?
                              CommandSender sender
    ) {
        Component message = Component.empty()
                .append(prefix)
                .append(mini.deserialize(
                        "<#D9CFF2>The physics are calculating <gray><steps></gray> substeps per physics tick at the moment.",
                        Placeholder.component("steps",
                                Component.text(physicsSubsteps)
                        ))
                );

        sender.sendMessage(message);
    }



    /* OLD WAY OF SPAWNING BLOCKS!
    // Suggestion list of all materials that are able to be blocks and are not legacy
    @Suggestions("blockTypes")
    public List<String> blockTypes(CommandContext<CommandSender> context, CommandInput input) { return Arrays.stream(Material.values()).filter(Material::isBlock).filter(material -> !material.isLegacy()).map(Material::toString).collect(Collectors.toList()); };

    @Command("spawnblock|sb [blockType] [mass]")
    public void spawnPhysicsBlock(
            CommandSender sender,
            @Argument(value = "blockType", suggestions = "blockTypes", description = "Material of the physics object") @Default("thisWillDefaultToStoneInTheFunctionDw!!") String blockType,
            @Argument(value = "mass", description = "Weight of the physics object") @Default("1.0f") float mass
    ) {
        if (sender instanceof Player player) {
            Material type = Material.STONE;
            try {
                type = Material.valueOf(blockType.toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Invalid block type specified! Defaulting to Stone.");
            }

            BoxShape boxShape = new BoxShape(new Vector3f(0.5f, 0.5f, 0.5f)); // Ensure size is correct
            Transform startTransform = new Transform();
            startTransform.setIdentity();
            startTransform.origin.set(new Vector3f((float) player.getLocation().getX(), (float) (player.getLocation().getY()), (float) player.getLocation().getZ())); // Spawn above the player

            Vector3f localInertia = new Vector3f();
            boxShape.calculateLocalInertia(mass, localInertia);

            RigidBodyConstructionInfo cInfo = new RigidBodyConstructionInfo(mass, new DefaultMotionState(startTransform), boxShape, localInertia);
            RigidBody body = new RigidBody(cInfo);

            // More exact collisions?
            boxShape.setMargin(0.01f); // Default is usually around 0.04
            body.setCcdMotionThreshold(1e-7f);
            body.setCcdSweptSphereRadius(0.2f);

            dynamicsWorld.addRigidBody(body);

            // Spawn the block display
            Location displayLocation = new Location(player.getWorld(), player.getX() + 0.5, player.getY() + 0.5, player.getZ() + 0.5, player.getYaw(), player.getPitch());
            Material finalType = type;
            BlockDisplay display = displayLocation.getWorld().spawn(displayLocation, BlockDisplay.class, entity -> {
                entity.setBlock(finalType.createBlockData());
                Transformation transformation = entity.getTransformation();
                transformation.getTranslation().set(-0.5F, -0.5F, -0.5F);
                entity.setTransformation(transformation);
            });
            bodies.put(display, body);
        } else {
            sender.sendMessage("This command can only be run by a player.");
        }
    }
     */

    @Suggestions("blockTypes")
    public List<String> blockTypes(CommandContext<CommandSender> context, CommandInput input) { return Arrays.stream(PhysicsMaterial.values()).map(PhysicsMaterial::toString).collect(Collectors.toList()); }

    @Command("spawnblock|sb <blockType>")
    public void spawnPhysicsBlock(
            CommandSender sender,
            @Argument(value = "blockType", suggestions = "blockTypes", description = "Material of the physics object") String blockType
    ) { // TODO: Size specification
        if (sender instanceof Player player) { // TODO: Move to requiredSender
            PhysicsMaterial type;
            try {
                type = PhysicsMaterial.valueOf(blockType.toUpperCase());
            } catch (IllegalArgumentException e) {
                Component message = mini.deserialize(
                        prefix +
                                "<#D9CFF2>Invalid block type specified! Ignoring. Check suggestions for valid types!"
                );
                sender.sendMessage(message);
                return;
            }

            BoxShape boxShape = new BoxShape(new Vector3f(0.5f, 0.5f, 0.5f)); // Ensure size is correct
            Transform startTransform = new Transform();
            startTransform.setIdentity();
            startTransform.origin.set(new Vector3f((float) player.getLocation().getX(), (float) (player.getLocation().getY()), (float) player.getLocation().getZ())); // Spawn above the player

            Vector3f localInertia = new Vector3f();
            boxShape.calculateLocalInertia(type.mass, localInertia);

            RigidBodyConstructionInfo cInfo = new RigidBodyConstructionInfo(type.mass, new DefaultMotionState(startTransform), boxShape, localInertia);
            RigidBody body = new RigidBody(cInfo);
            body.setFriction(type.friction);
            body.setRestitution(type.restitution);

            // More exact collisions?
            boxShape.setMargin(0.02f); // Default is usually around 0.04
            body.setCcdMotionThreshold(1e-7f);
            body.setCcdSweptSphereRadius(0.2f);

            dynamicsWorld.addRigidBody(body);

            // Spawn the block display
            Location displayLocation = new Location(player.getWorld(), player.getX() + 0.5, player.getY() + 0.5, player.getZ() + 0.5, player.getYaw(), player.getPitch());
            Material finalType = Material.valueOf(type.toString());

            BlockDisplay display = displayLocation.getWorld().spawn(displayLocation, BlockDisplay.class, entity -> {
                entity.setBlock(finalType.createBlockData());
                Transformation transformation = entity.getTransformation();
                transformation.getTranslation().set(-0.5F, -0.5F, -0.5F);
                entity.setTransformation(transformation);
            });
            bodies.put(display, body);


            Component message = Component.empty()
                    .append(prefix)
                    .append(mini.deserialize(
                            "<#D9CFF2>Spawned a <gray>1m by 1m block</gray> of <gray><material></gray>, with a mass of <gray><weight>kg</gray>, has a <hover:show_text:'<yellow>friction coefficient'>frico of <gray><frico></gray> and a <hover:show_text:'<yellow>restitution coefficient'>resco of <gray><resco></gray>!",
                            Placeholder.component("material",
                                    Component.text(type.toString())
                            ),
                            Placeholder.component("weight",
                                    Component.text(type.mass)
                            ),
                            Placeholder.component("frico",
                                    Component.text(type.friction)
                            ),
                            Placeholder.component("resco",
                                    Component.text(type.restitution)
                            ))
                    );

            sender.sendMessage(message);
        } else {
            sender.sendMessage("This command can only be run by a player.");
        }
    }



    // Suggestion list of all entities
    @Suggestions("entityTypes")
    public List<String> entityTypes(CommandContext<CommandSender> context, CommandInput input) { return Stream.of(EntityType.values()).map(EntityType::toString).collect(Collectors.toList()); }

    @Command("spawnentity|se [entityType] [mass]")
    public void spawnPhysicsEntity(
            CommandSender sender,
            @Argument(value = "entityType", suggestions = "entityTypes", description = "Entity of the physics object") @Default("thisWillDefaultToPigInTheFunctionDw!!aaa<3") String entityType,
            @Argument(value = "mass", description = "Weight of the physics object") @Default("1.0f") float mass
    ) {
        if (sender instanceof Player player) { // TODO: Move to requiredSender
            EntityType type = EntityType.PIG;
            try {
                type = EntityType.valueOf(entityType.toUpperCase());
            } catch (IllegalArgumentException e) {
                Component message = mini.deserialize(
                        prefix +
                                "<#D9CFF2>Invalid entity type specified! Defaulting to Pig. Check suggestions for valid types!"
                );
                sender.sendMessage(message);
            }

            SphereShape sphereShape = new SphereShape(0.5f);
            Transform startTransform = new Transform();
            startTransform.setIdentity();
            startTransform.origin.set(new Vector3f((float) player.getLocation().getX(), (float) (player.getLocation().getY()), (float) player.getLocation().getZ())); // Spawn above the player

            Vector3f localInertia = new Vector3f(0, 0, 0);
            sphereShape.calculateLocalInertia(mass, localInertia);

            RigidBodyConstructionInfo cInfo = new RigidBodyConstructionInfo(mass, new DefaultMotionState(startTransform), sphereShape, localInertia);
            RigidBody body = new RigidBody(cInfo);

            dynamicsWorld.addRigidBody(body);

            // Spawn the specified entity
            Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);

            // Store the entity and rigid body together for updates
            bodies.put(entity, body);
        } else {
            sender.sendMessage("This command can only be run by a player.");
        }
    }

    @Command("ground|g|sg|gs <loc1> <loc2>")
    public void spawnGround(
            CommandSender sender,
            @Argument(value = "loc1") Location loc1,
            @Argument(value = "loc2") Location loc2
    ) {
        if (sender instanceof Player player) {
            Vector3f pointA;
            Vector3f pointB;
            try {
                pointA = new Vector3f((float) loc1.x(), (float) loc1.y(), (float) loc1.z());
                pointB = new Vector3f((float) loc2.x() + 1.0f, (float) loc2.y() + 1.0f, (float) loc2.z() + 1.0f); // idk why +1 just trust
            } catch (IllegalArgumentException e) {
                Component message = mini.deserialize(
                        prefix +
                                "<#D9CFF2>Invalid positions specified! Ignoring. Enter a valid location (e.g ~ ~ ~)"
                );
                sender.sendMessage(message);
                return;
            }

            Vector3f center = new Vector3f();
            center.interpolate(pointA, pointB, 0.5f);
            Vector3f halfExtents;
            halfExtents = new Vector3f();
            halfExtents.sub(pointB, pointA);
            halfExtents.scale(0.5f);

            float width = Math.abs(pointB.x - pointA.x);
            float height = Math.abs(pointB.y - pointA.y);
            float length = Math.abs(pointB.z - pointA.z);

            CollisionShape boxShape = new BoxShape(new Vector3f(width / 2, height / 2, length / 2));

            Transform boxTransform = new Transform();
            boxTransform.setIdentity();
            boxTransform.origin.set(center);

            DefaultMotionState boxMotionState = new DefaultMotionState(boxTransform);

            Vector3f localInertia = new Vector3f(0, 0, 0);

            // Set the mass to 0 so no gravity!!
            RigidBodyConstructionInfo boxRigidBodyCI = new RigidBodyConstructionInfo(0, boxMotionState, boxShape, localInertia);

            RigidBody boxRigidBody = new RigidBody(boxRigidBodyCI);

            dynamicsWorld.addRigidBody(boxRigidBody);
            BlockDisplay display = player.getLocation().getWorld().spawn(new Location(player.getWorld(), pointA.x, pointA.y, pointA.z), BlockDisplay.class, entity -> {  // Sumting wrrong here witt trans!! -0.5 would shift half a block which would center corner block. we don't want this, so we just kinda keep translation in #updatePhysics
                entity.setBlock(Material.BLACK_CONCRETE_POWDER.createBlockData());
                entity.setTransformation(
                        new Transformation(
                                new org.joml.Vector3f(-(width / 2), -(height / 2),  -(length / 2)),
                                new AxisAngle4f(),
                                new org.joml.Vector3f(pointB.x - pointA.x, pointB.y - pointA.y, pointB.z - pointA.z),
                                new AxisAngle4f()
                        )
                );
            });
            bodies.put(display, boxRigidBody);
        } else {
            sender.sendMessage("This command can only be run by a player.");
        }
    }


    private void updatePhysics() {
        if (dynamicsWorld != null) {
            float deltaTime = physicsTime / (float) Bukkit.getServer().getTPS()[0];
            dynamicsWorld.stepSimulation(deltaTime, physicsSubsteps);

            // Sync entities with physics positions
            bodies.forEach((entity, body) -> {
                Transform transform = new Transform();
                body.getMotionState().getWorldTransform(transform);

                Vector3f position = transform.origin;
                Quat4f rotation = new Quat4f();
                transform.getRotation(rotation);

                Location location = new Location(entity.getWorld(), position.x, position.y, position.z);
                if (entity.getType().equals(EntityType.BLOCK_DISPLAY)) {
                    BlockDisplay blockDisplay = (BlockDisplay) entity;
                    Transformation transformation = blockDisplay.getTransformation();
                    blockDisplay.setTransformationMatrix(
                            new Matrix4f()
                                    .translate(transformation.getTranslation()) // This just because ground translation is frogged up!!
                                    .scale(transformation.getScale())
                                    .rotateXYZ(rotation.x, rotation.y, rotation.z)
                    );

                    if (body.isActive() && !blockDisplay.isGlowing()) { // Technically some debug stuff for hit detection but mmm idk it looks cool
                        blockDisplay.setGlowing(true);
                    } else if (!body.isActive() && blockDisplay.isGlowing()) {
                        blockDisplay.setGlowing(false);
                    }
                } else {
                    LivingEntity living = (LivingEntity) entity;
                    if (body.isActive()) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 1));
                    } else {
                        living.removePotionEffect(PotionEffectType.GLOWING);
                    }
                }
                entity.teleport(location);
            });
        }
    }
}