package project;

import java.io.IOException;
import java.nio.file.Path;

public class CutoffLexicalHierarchy extends Model {
	public DigestWornet(Path file) {
		
	}
	
	NounSynset nounSynset; 
	NounSynset[] hyponyms; 

	WordNetDatabase database = WordNetDatabase.getFileInstance(); 
	Synset[] synsets = database.getSynsets("fly", SynsetType.NOUN); 
	for (int i = 0; i < synsets.length; i++) { 
	    nounSynset = (NounSynset)(synsets[i]); 
	    hyponyms = nounSynset.getHyponyms(); 
	    System.err.println(nounSynset.getWordForms()[0] + 
	            ": " + nounSynset.getDefinition() + ") has " + hyponyms.length + " hyponyms"); 
	}

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
