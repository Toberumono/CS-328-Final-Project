package project;

import java.io.IOException;
import java.nio.file.Path;

public class CutoffLexicalHierarchy extends Model {

	public CutoffLexicalHierarchy(boolean requireSynchronized) {
		super(requireSynchronized);
	}

	public CutoffLexicalHierarchy(Path root, boolean requireSynchronized) throws IOException {
		super(root, requireSynchronized);
	}

	@Override
	protected void smooth() {
		
	}
	
}
