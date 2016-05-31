package project;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import toberumono.lexer.errors.LexerException;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.parser.nndep.demo.DependencyParserDemo;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.logging.Redwood;

public class CorpusParser {
	
	/** A logger for this class */
	private static Redwood.RedwoodChannels log = Redwood.channels(DependencyParserDemo.class);
	
	public static void main(String[] args) throws IOException {
		String modelPath = DependencyParser.DEFAULT_MODEL;
		String taggerPath = "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger";
		
		for (int argIndex = 0; argIndex < args.length - 2;) {
			switch (args[argIndex]) {
				case "-tagger":
					taggerPath = args[argIndex + 1];
					argIndex += 2;
					break;
				case "-model":
					modelPath = args[argIndex + 1];
					argIndex += 2;
					break;
				default:
					throw new RuntimeException("Unknown argument " + args[argIndex]);
			}
		}
		
		String text = "I can almost always tell when movies use fake dinosaurs.";//BNCScraper.scrapeBNCFile(Paths.get("/Users/joshualipstone/Downloads/2554/2554/download/Texts/A/A0/A00.xml"));
		
		Model model = new RawCountsModel(false);
		MaxentTagger tagger = new MaxentTagger(taggerPath);
		DependencyParser parser = DependencyParser.loadFromModelFile(modelPath);
		
		SimpleFileVisitor<Path> corpusVisitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!file.getFileName().toString().endsWith("xml"))
					return FileVisitResult.CONTINUE;
				System.out.println(file);
				try {
					DocumentPreprocessor tokenizer = new DocumentPreprocessor(new StringReader(BNCScraper.scrapeBNCFile(file)));
					for (List<HasWord> sentence : tokenizer) {
						List<TaggedWord> tagged = tagger.tagSentence(sentence);
						GrammaticalStructure gs = parser.predict(tagged);
						for (TypedDependency td : gs.allTypedDependencies())
							model.addBigram(td);
						// Print typed dependencies
						//log.info(gs);
					}
				}
				catch (LexerException e) {
					System.err.println(file + " FAILED");
				}
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(Paths.get(args[args.length - 2]), corpusVisitor);
		Path modelLoc = Paths.get(args[args.length - 1]);
		model.storeModel(modelLoc);
	}
}
