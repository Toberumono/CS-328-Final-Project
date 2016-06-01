package project;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;

public class SentenceEvaluator {
	
	public static void main(String[] args) throws IOException {
		String modelPath = DependencyParser.DEFAULT_MODEL;
		String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
		MaxentTagger tagger = new MaxentTagger(taggerPath);
		DependencyParser parser = DependencyParser.loadFromModelFile(modelPath);
		
		Model model = new PMIModel(Paths.get(args[0]), false);
		model.convertToProbs();
		model.smooth();
		try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("Please enter a sentence to evaluate. (type sys:exit to quit):");
			String input;
			
			while (!(input = scanner.nextLine()).equals("sys:exit")) {
				DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(input));
				
				for (List<HasWord> sentence : tokenizer) {
					List<TaggedWord> tagged = tagger.tagSentence(sentence);
					GrammaticalStructure gs = parser.predict(tagged);
					double probability = 0.0;
					int count = 0;
					for (TypedDependency td : gs.allTypedDependencies()) {
						if (model.containsBigram(td)) {
							probability += model.probabilityForBigram(td);
							count++;
						}
					}
					if (count == 0)
						System.err.println("\"" + sentenceToString(sentence) + "\" does not contain any recognized related words.");
					else
						System.out.println("\"" + sentenceToString(sentence) + "\" has a probability of " + (probability / count) + ".");
				}
				System.out.println("Please enter a sentence to evaluate. (type sys:exit to quit):");
			}
		}
	}
	
	private static String sentenceToString(List<? extends HasWord> sentence) {
		StringBuilder sb = new StringBuilder();
		for (HasWord hw : sentence)
			sb.append(" ").append(hw.word());
		return sb.substring(1);
	}
}
