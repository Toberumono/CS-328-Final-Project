package project;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The trivial "count them up" model
 * 
 * @author Joshua Lipstone
 */
public class RawCountsModel extends Model {
	
	public RawCountsModel(boolean requireSynchronized) {
		super(requireSynchronized);
	}
	
	public RawCountsModel(Path root, boolean requireSynchronized) throws IOException {
		super(root, requireSynchronized);
	}
	
	@Override
	protected void doSmoothing() {
		//This prevents the smoothing flag from being set for this Model
	}
	
	@Override
	public void smooth() {
		
	}
}
