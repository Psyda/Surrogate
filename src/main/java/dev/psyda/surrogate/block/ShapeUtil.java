package dev.psyda.surrogate.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.EnumMap;
import java.util.Map;

public final class ShapeUtil {
	private ShapeUtil() {
	}

	/** Rotates a shape authored for {@link Direction#NORTH} to face {@code to}. */
	public static VoxelShape rotate(VoxelShape shape, Direction to) {
		VoxelShape[] buffer = {shape, VoxelShapes.empty()};
		int times = (to.getHorizontal() - Direction.NORTH.getHorizontal() + 4) % 4;
		for (int i = 0; i < times; i++) {
			buffer[0].forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
					buffer[1] = VoxelShapes.union(buffer[1], VoxelShapes.cuboid(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
			buffer[0] = buffer[1];
			buffer[1] = VoxelShapes.empty();
		}
		return buffer[0];
	}

	public static Map<Direction, VoxelShape> horizontal(VoxelShape north) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		for (Direction direction : Direction.Type.HORIZONTAL) {
			shapes.put(direction, rotate(north, direction));
		}
		return shapes;
	}
}
