package project;

import java.io.IOException;
import java.nio.file.Path;

import edu.stanford.nlp.trees.TypedDependency;

public class RawCountsModel extends Model {

	public RawCountsModel(boolean requireSynchronized) {
		super(requireSynchronized);
	}

	public RawCountsModel(Path root, boolean requireSynchronized) throws IOException {
		super(root, requireSynchronized);
	}

	@Override
	protected void smooth() {
		
	}

	@Override
	public Double probabilityForBigram(TypedDependency td) {
		stem(td);
		String name = td.reln().getLongName(), gov = generateKey(td.gov()), dep = generateKey(td.dep()), key = gov + " ~ " + dep;
		return probs.get(name).get(key);
	}
	
	
}
