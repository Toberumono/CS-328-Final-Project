package project;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.morph.WordnetStemmer;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.WordNetDatabase;

public class JWIIntegration {
	
	public static void main(String[] args) throws IOException {
		String wnhome = "/usr/local/Cellar/wordnet/3.1";
		String path = wnhome + File.separator + "dict";
		URL url = new URL("file", null, path);
		IDictionary dict = new Dictionary(url);
		dict.open();
		WordnetStemmer stemmer = new WordnetStemmer(dict);
		stemmer.findStems("replacements", POS.NOUN).forEach(System.out::println);
		IIndexWord idxWord = dict.getIndexWord("dog", POS.NOUN);
		IWordID wordID = idxWord.getWordIDs().get(0);
		IWord word = dict.getWord(wordID);
		System.out.println("Id = " + wordID);
		System.out.println("Lemma = " + word.getLemma());
		System.out.println("Gloss = " + word.getSynset().getGloss());
		try (Scanner inp = new Scanner(System.in)) {
			String input;
			System.out.println("Please enter a word to stem. (type sys:exit to quit):");
			while (!(input = inp.nextLine()).equals("sys:exit")) {
				//  Get the synsets containing the wrod form
				WordNetDatabase database = WordNetDatabase.getFileInstance();
				Synset[] synsets = database.getSynsets(input);
				//  Display the word forms and definitions for synsets retrieved
				if (synsets.length > 0) {
					System.out.println("The following synsets contain '" +
							input + "' or a possible base form " +
							"of that text:");
					for (int i = 0; i < synsets.length; i++) {
						System.out.println("");
						String[] wordForms = synsets[i].getWordForms();
						for (int j = 0; j < wordForms.length; j++) {
							System.out.print((j > 0 ? ", " : "") +
									wordForms[j]);
						}
						System.out.println(": " + synsets[i].getDefinition());
					}
				}
				else {
					System.err.println("No synsets exist that contain " +
							"the word form '" + input + "'");
				}
				System.out.println("Please enter a word to stem. (type sys:exit to quit):");
			}
		}
	}
	
	public static POS posAdapter(String pos) {
		if (pos.startsWith("J") || pos.startsWith("j"))
			return POS.ADJECTIVE;
		if (pos.startsWith("N") || pos.startsWith("n"))
			return POS.NOUN;
		if (pos.startsWith("R") || pos.startsWith("r"))
			return POS.ADVERB;
		if (pos.startsWith("V") || pos.startsWith("v"))
			return POS.VERB;
		return null;
	}
}
