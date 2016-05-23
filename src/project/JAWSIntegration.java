package project;

import java.util.Scanner;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.WordNetDatabase;

public class JAWSIntegration {
	/**
	 * Main entry point. The command-line arguments are concatenated together (separated by spaces) and used as the word form
	 * to look up.
	 */
	public static void main(String[] args) {
		if (args.length > 0) {
			//  Concatenate the command-line arguments
			StringBuffer buffer = new StringBuffer();
			for (int i = 0; i < args.length; i++) {
				buffer.append((i > 0 ? " " : "") + args[i]);
			}
			String wordForm = buffer.toString();
			//  Get the synsets containing the wrod form
			WordNetDatabase database = WordNetDatabase.getFileInstance();
			Synset[] synsets = database.getSynsets(wordForm);
			//  Display the word forms and definitions for synsets retrieved
			if (synsets.length > 0) {
				System.out.println("The following synsets contain '" +
						wordForm + "' or a possible base form " +
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
						"the word form '" + wordForm + "'");
			}
		}
		else {
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
	}
}
