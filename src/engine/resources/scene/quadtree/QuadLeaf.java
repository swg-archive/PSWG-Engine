package engine.resources.scene.quadtree;

import java.util.ArrayList;
import java.util.List;

public class QuadLeaf<T> {
	public final float x;
	public final float y;
	public final List<T> values;

	public QuadLeaf(float x, float y, T value) {
		this.x = x;
		this.y = y;
		this.values = new ArrayList<T>(1);
		this.values.add(value);
	}
}